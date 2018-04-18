package io.iovo.node;

import io.iovo.node.model.*;
import io.iovo.node.tracker.TrackerConnector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.FileNotFoundException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static io.iovo.node.model.Block.BLOCK_HEADER_LENGTH;
import static io.iovo.node.model.Block.initialBaseTarget;
import static io.iovo.node.utils.ConvertUtils.convert;

public class Iovo {
    private static final Logger logger = LogManager.getLogger();
    public static final String VERSION = "0.0.1";

    private TrackerConnector trackerConnector;

    public static final int LOGGING_MASK_EXCEPTIONS = 1;
    public static final int LOGGING_MASK_NON200_RESPONSES = 2;
    public static final int LOGGING_MASK_200_RESPONSES = 4;
    public static int communicationLoggingMask;

    public static String myScheme, myAddress, myHallmark;
    public static int myPort;
    public static boolean shareMyAddress;

    private static long epochBeginning;

    public static HashMap<Long, Account> accounts = new HashMap<>();
    public static int blockCounter;
    public static HashMap<Long, Block> blocks;
    public static long lastBlock;
    public static int transactionCounter;
    public static HashMap<Long, Transaction> transactions;
    public static ConcurrentHashMap<Long, Transaction> unconfirmedTransactions = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<Long, Transaction> doubleSpendingTransactions = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<Long, Transaction> nonBroadcastedTransactions = new ConcurrentHashMap<>();

    public static HashSet<String> wellKnownPeers = new HashSet<>();
    private static int maxNumberOfConnectedPublicPeers = 4;
    public static int connectTimeout, readTimeout;
    public static boolean enableHallmarkProtection;
    public static int pushThreshold, pullThreshold;
    public static AtomicInteger peerCounter;
    public static HashMap<String, Peer> peers = new HashMap<>();

    public static final long MAX_BALANCE = 1000000000;

    public static ConcurrentHashMap<String, User> users = new ConcurrentHashMap<>();

    public static HashMap<String, Alias> aliases = new HashMap<>();
    public static HashMap<Long, Alias> aliasIdToAliasMappings = new HashMap<>();
    public static final ScheduledExecutorService scheduledThreadPool = Executors.newScheduledThreadPool(7);
    private static volatile Peer lastBlockchainFeeder;
    private static final Object blocksAndTransactionsLock = new Object();

    public Iovo() {
        setBeginning();
        peerCounter = new AtomicInteger(0);
        this.trackerConnector = new TrackerConnector(this);
        try {
            loadTransactions();
            loadBlocks();
            scanBlockchain();
        } catch (Exception e) {
            logger.error("Error while loading blocks " + e);
        }
        connectPeers();
        askForPeers();
        syncBlocks();
        broadcastTransactions();
        addUser("Default", "kolega");
        addUser("Alternative", "kolezanka");

        Forging.forgeBlocks();
//        this.node = new Node(createNetworkState());
    }

    private void broadcastTransactions() {
        scheduledThreadPool.scheduleWithFixedDelay(() -> {
            try {
                JSONArray transactionsData = new JSONArray();
                for (Transaction transaction : nonBroadcastedTransactions.values()) {
                    if (unconfirmedTransactions.get(transaction.id) == null && transactions.get(transaction.id) == null) {
                        transactionsData.add(transaction.getJSONObject());
                    } else {
                        nonBroadcastedTransactions.remove(transaction.id);
                    }
                }
                if (transactionsData.size() > 0) {
                    JSONObject peerRequest = new JSONObject();
                    peerRequest.put("command", "processTransactions");
                    peerRequest.put("transactions", transactionsData);
                    Peer.sendToAllPeers(peerRequest);
                }
            } catch (Exception e) {
                logger.error("Error in transaction re-broadcasting thread", e);
            } catch (Throwable t) {
                logger.error("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString());
                t.printStackTrace();
                System.exit(1);
            }
        }, 0, 60, TimeUnit.SECONDS);
    }

    private void addUser(String username, String password) {
        User user = new User();
        try {
            BigInteger pubKey = user.initializeKeyPair(password);
            users.put(username, user);
        } catch (Exception e) {
            logger.error("Cannot create user " + username  + " " + e);
        }
    }

    private void askForPeers() {
        scheduledThreadPool.scheduleWithFixedDelay(new Runnable() {
            private final JSONObject getPeersRequest = new JSONObject();
            {
                getPeersRequest.put("command", "getPeers");
            }

            @Override
            public void run() {
                try {
                    Peer peer = Peer.getAnyPeer(Peer.STATE_CONNECTED, true);
                    if (peer != null) {
                        JSONObject response = peer.send(getPeersRequest);
                        if (response != null) {
                            JSONArray peers = (JSONArray)response.get("peers");
                            for (Object peerAddress : peers) {
                                String address = ((String)peerAddress).trim();
                                if (address.length() > 0) {
                                    //TODO: can a rogue peer fill the peer pool with zombie addresses?
                                    //consider an option to trust only highly-hallmarked peers
                                    Peer.addPeer(address, address);
                                }
                            }
                        }
                    }
                } catch (Exception e) { }
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    private void connectPeers() {
        scheduledThreadPool.scheduleWithFixedDelay(() -> {
            try {
                if (Peer.getNumberOfConnectedPublicPeers() < maxNumberOfConnectedPublicPeers) {
                    Peer peer = Peer.getAnyPeer(ThreadLocalRandom.current().nextInt(2) == 0 ? Peer.STATE_NONCONNECTED : Peer.STATE_DISCONNECTED, false);
                    if (peer != null) {
                        peer.connect();
                    }
                }
            } catch (Exception e) { }
        }, 0, 5, TimeUnit.SECONDS);
    }

    private void syncBlocks() {
        scheduledThreadPool.scheduleWithFixedDelay(new Runnable() {
            private final JSONObject getCumulativeDifficultyRequest = new JSONObject();
            private final JSONObject getMilestoneBlockIdsRequest = new JSONObject();
            {
                getCumulativeDifficultyRequest.put("command", "getCumulativeDifficulty");
                getMilestoneBlockIdsRequest.put("command", "getMilestoneBlockIds");
            }

            @Override
            public void run() {
                try {
                    Peer peer = Peer.getAnyPeer(Peer.STATE_CONNECTED, true);
                    if (peer != null) {
                        lastBlockchainFeeder = peer;
                        JSONObject response = peer.sendNew(getCumulativeDifficultyRequest);
                        if (response != null) {
                            BigInteger curCumulativeDifficulty = Block.getLastBlock().cumulativeDifficulty;
                            BigInteger betterCumulativeDifficulty = new BigInteger((String)response.get("cumulativeDifficulty"));
                            if (betterCumulativeDifficulty.compareTo(curCumulativeDifficulty) > 0) {
                                response = peer.sendNew(getMilestoneBlockIdsRequest);
                                if (response != null) {
                                    long commonBlockId = Genesis.GENESIS_BLOCK_ID;
                                    JSONArray milestoneBlockIds = (JSONArray)response.get("milestoneBlockIds");
                                    for (Object milestoneBlockId : milestoneBlockIds) {
                                        long blockId = (new BigInteger((String)milestoneBlockId)).longValue();
                                        Block block = blocks.get(blockId);
                                        if (block != null) {
                                            commonBlockId = blockId;
                                            break;
                                        }
                                    }

                                    int i, numberOfBlocks;
                                    do {
                                        JSONObject request = new JSONObject();
                                        request.put("command", "getNextBlockIds");
                                        request.put("blockId", convert(commonBlockId));
                                        response = peer.sendNew(request);
                                        if (response == null) {
                                            return;
                                        } else {
                                            JSONArray nextBlockIds = (JSONArray)response.get("nextBlockIds");
                                            numberOfBlocks = nextBlockIds.size();
                                            if (numberOfBlocks == 0) {
                                                return;
                                            } else {
                                                long blockId;
                                                for (i = 0; i < numberOfBlocks; i++) {
                                                    blockId = (new BigInteger((String)nextBlockIds.get(i))).longValue();
                                                    if (blocks.get(blockId) == null) {
                                                        break;
                                                    }
                                                    commonBlockId = blockId;
                                                }
                                            }
                                        }
                                    } while (i == numberOfBlocks);

                                    if (Block.getLastBlock().height - blocks.get(commonBlockId).height < 720) {
                                        long curBlockId = commonBlockId;
                                        LinkedList<Block> futureBlocks = new LinkedList<>();
                                        HashMap<Long, Transaction> futureTransactions = new HashMap<>();
                                        do {
                                            JSONObject request = new JSONObject();
                                            request.put("command", "getNextBlocks");
                                            request.put("blockId", convert(curBlockId));
                                            response = peer.sendNew(request);
                                            if (response == null) {
                                                break;
                                            } else {
                                                JSONArray nextBlocks = (JSONArray)response.get("nextBlocks");
                                                numberOfBlocks = nextBlocks.size();
                                                if (numberOfBlocks == 0) {
                                                    break;
                                                } else {
                                                    for (i = 0; i < numberOfBlocks; i++) {
                                                        JSONObject blockData = (JSONObject)nextBlocks.get(i);
                                                        Block block = Block.getBlock(blockData);
                                                        curBlockId = block.getId();

                                                        synchronized (blocksAndTransactionsLock) {
                                                            boolean alreadyPushed = false;
                                                            if (block.previousBlock == lastBlock) {
                                                                ByteBuffer buffer = ByteBuffer.allocate(BLOCK_HEADER_LENGTH + block.payloadLength);
                                                                buffer.order(ByteOrder.LITTLE_ENDIAN);
                                                                buffer.put(block.getBytes());
                                                                JSONArray transactionsData = (JSONArray)blockData.get("transactions");
                                                                for (Object transaction : transactionsData) {
                                                                    buffer.put(Transaction.getTransaction((JSONObject)transaction).getBytes());
                                                                }
                                                                if (Block.pushBlock(buffer, false)) {
                                                                    alreadyPushed = true;
                                                                } else {
                                                                    peer.blacklist();
                                                                    return;
                                                                }
                                                            }
                                                            if (!alreadyPushed && blocks.get(block.getId()) == null) {
                                                                futureBlocks.add(block);
                                                                block.transactions = new long[block.numberOfTransactions];
                                                                JSONArray transactionsData = (JSONArray)blockData.get("transactions");
                                                                for (int j = 0; j < block.numberOfTransactions; j++) {
                                                                    Transaction transaction = Transaction.getTransaction((JSONObject)transactionsData.get(j));
                                                                    block.transactions[j] = transaction.getId();
                                                                    futureTransactions.put(block.transactions[j], transaction);
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        } while (true);

                                        if (!futureBlocks.isEmpty() && Block.getLastBlock().height - blocks.get(commonBlockId).height < 720) {
                                            synchronized (blocksAndTransactionsLock) {
                                                Block.saveBlocks("blocks.iovo.bak", true);
                                                Transaction.saveTransactions("transactions.iovo.bak");
                                                curCumulativeDifficulty = Block.getLastBlock().cumulativeDifficulty;
                                                while (lastBlock != commonBlockId && Block.popLastBlock()) {}

                                                if (lastBlock == commonBlockId) {
                                                    for (Block block : futureBlocks) {
                                                        if (block.previousBlock == lastBlock) {
                                                            ByteBuffer buffer = ByteBuffer.allocate(BLOCK_HEADER_LENGTH + block.payloadLength);
                                                            buffer.order(ByteOrder.LITTLE_ENDIAN);
                                                            buffer.put(block.getBytes());
                                                            for (int j = 0; j < block.transactions.length; j++) {
                                                                buffer.put(futureTransactions.get(block.transactions[j]).getBytes());
                                                            }
                                                            if (!Block.pushBlock(buffer, false)) {
                                                                break;
                                                            }
                                                        }
                                                    }
                                                }
                                                if (Block.getLastBlock().cumulativeDifficulty.compareTo(curCumulativeDifficulty) < 0) {
                                                    Block.loadBlocks("blocks.iovo.bak");
                                                    Transaction.loadTransactions("transactions.iovo.bak");
                                                    peer.blacklist();
                                                }
                                            }
                                        }
                                        synchronized (blocksAndTransactionsLock) {
                                            Block.saveBlocks("blocks.iovo", false);
                                            Transaction.saveTransactions("transactions.iovo");
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) { }
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    private void setBeginning() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.ZONE_OFFSET, 0);
        calendar.set(Calendar.YEAR, 2018);
        calendar.set(Calendar.MONTH, Calendar.MARCH);
        calendar.set(Calendar.DAY_OF_MONTH, 24);
        calendar.set(Calendar.HOUR_OF_DAY, 12);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        epochBeginning = calendar.getTimeInMillis();
    }

    private void scanBlockchain() throws Exception {
        logger.info("Scanning blockchain...");
        HashMap<Long, Block> tmpBlocks = blocks;
        blocks = new HashMap<>();
        lastBlock = Genesis.GENESIS_BLOCK_ID;
        long curBlockId = Genesis.GENESIS_BLOCK_ID;
        do {
            Block curBlock = tmpBlocks.get(curBlockId);
            long nextBlockId = curBlock.nextBlock;
            curBlock.analyze();
            curBlockId = nextBlockId;
        } while (curBlockId != 0);
        logger.info("...Done");
    }


    private void loadTransactions() throws Exception {
        try {
            logger.info("Loading transactions...");
            Transaction.loadTransactions("transactions.iovo");
            logger.info("...Done");
        } catch (FileNotFoundException e) {
            logger.info("...generating transactions...");
            transactions = new HashMap<>();
            for (int i = 0; i < Genesis.recipients.length; i++) {
                Transaction transaction = new Transaction(Transaction.TYPE_PAYMENT,
                        Transaction.SUBTYPE_PAYMENT_ORDINARY_PAYMENT, 0,
                        (short) 0, new byte[]{18, 89, -20, 33, -45, 26, 48, -119, -115, 124, -47, 96, -97, -128, -39, 102, -117, 71, 120, -29, -39, 126, -108, 16, 68, -77, -97, 12, 68, -46, -27, 27},
                        Genesis.recipients[i], Genesis.amounts[i], 0, 0, Genesis.signatures[i]);
                transactions.put(transaction.getId(), transaction);
                logger.info("Genesis TX: " + Genesis.amounts[i] + " IOVO to address " + BigInteger.valueOf(Genesis.recipients[i]));
            }
            for (Transaction transaction : transactions.values()) {
                transaction.index = ++transactionCounter;
                transaction.block = Genesis.GENESIS_BLOCK_ID;
            }
            Transaction.saveTransactions("transactions.iovo");
            logger.info("...Done");
        }
    }

    private void loadBlocks() throws Exception {
        try {
            logger.info("Loading blocks...");
            Block.loadBlocks("blocks.iovo");
            logger.info("...Done");
        } catch (FileNotFoundException e) {
            blocks = new HashMap<>();
            logger.info("...generating genesis block...");
            Block block = new Block(-1, 0, 0, transactions.size(), 1000000000, 0,
                    transactions.size() * 128, null,
                    new byte[]{18, 89, -20, 33, -45, 26, 48, -119, -115, 124, -47, 96, -97, -128, -39, 102, -117, 71, 120, -29, -39, 126, -108, 16, 68, -77, -97, 12, 68, -46, -27, 27},
                    new byte[64], new byte[]{105, -44, 38, -60, -104, -73, 10, -58, -47, 103, -127, -128, 53, 101, 39, -63, -2, -32, 48, -83, 115, 47, -65, 118, 114, -62, 38, 109, 22, 106, 76, 8, -49, -113, -34, -76, 82, 79, -47, -76, -106, -69, -54, -85, 3, -6, 110, 103, 118, 15, 109, -92, 82, 37, 20, 2, 36, -112, 21, 72, 108, 72, 114, 17});
            block.index = ++blockCounter;
            blocks.put(Genesis.GENESIS_BLOCK_ID, block);
            block.transactions = new long[block.numberOfTransactions];
            int i = 0;
            for (long transaction : transactions.keySet()) {
                block.transactions[i++] = transaction;
            }
            Arrays.sort(block.transactions);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (i = 0; i < block.numberOfTransactions; i++) {
                digest.update(transactions.get(block.transactions[i]).getBytes());
            }
            block.payloadHash = digest.digest();
            block.baseTarget = initialBaseTarget;
            lastBlock = Genesis.GENESIS_BLOCK_ID;
            logger.info("Genesis block generated " + convert(lastBlock));
            block.cumulativeDifficulty = BigInteger.ZERO;
            Block.saveBlocks("blocks.iovo", false);
            logger.info("...Done");
        }
    }
}
