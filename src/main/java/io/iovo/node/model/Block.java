package io.iovo.node.model;

import io.iovo.node.Iovo;
import io.iovo.node.crypto.Crypto;
import io.iovo.node.utils.ConvertUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.*;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static io.iovo.node.Iovo.*;
import static io.iovo.node.utils.ConvertUtils.two64;
import static io.iovo.node.utils.TimeUtils.getEpochTime;

public class Block implements Serializable {
    private static final Logger logger = LogManager.getLogger();
    private static final long serialVersionUID = 0;
    public static final int MAX_PAYLOAD_LENGTH = 255 * 128;
    public static final int BLOCK_HEADER_LENGTH = 224;
    public static final long CREATOR_ID = 1739068987193023818L;
    public static final long initialBaseTarget = 153722867;
    public static final long maxBaseTarget = 1000000000L * initialBaseTarget;

    public int version;
    public int timestamp;
    public long previousBlock;
    public int numberOfTransactions;
    public int totalAmount, totalFee;
    public int payloadLength;
    public byte[] payloadHash;
    public byte[] generatorPublicKey;
    public byte[] generationSignature;
    public byte[] blockSignature;

    public int index;
    public long[] transactions;
    public long baseTarget;
    public int height;
    public long nextBlock;
    public BigInteger cumulativeDifficulty;
    private long prevBlockPtr;

    public Block(int version, int timestamp, long previousBlock, int numberOfTransactions, int totalAmount, int totalFee, int payloadLength,
                 byte[] payloadHash, byte[] generatorPublicKey, byte[] generationSignature, byte[] blockSignature) {
        this.version = version;
        this.timestamp = timestamp;
        this.previousBlock = previousBlock;
        this.numberOfTransactions = numberOfTransactions;
        this.totalAmount = totalAmount;
        this.totalFee = totalFee;
        this.payloadLength = payloadLength;
        this.payloadHash = payloadHash;
        this.generatorPublicKey = generatorPublicKey;
        this.generationSignature = generationSignature;
        this.blockSignature = blockSignature;
    }

    public void analyze() throws Exception {
        if (previousBlock == 0) {
            lastBlock = Genesis.GENESIS_BLOCK_ID;
            blocks.put(lastBlock, this);
            baseTarget = initialBaseTarget;
            cumulativeDifficulty = BigInteger.ZERO;

            Account.addAccount(CREATOR_ID);
        } else {
            Block.getLastBlock().nextBlock = getId();

            height = Block.getLastBlock().height + 1;
            lastBlock = getId();
            blocks.put(lastBlock, this);
            baseTarget = Block.getBaseTarget();
            cumulativeDifficulty = blocks.get(previousBlock).cumulativeDifficulty.add(two64.divide(BigInteger.valueOf(baseTarget)));

            Account generatorAccount = accounts.get(Account.getId(generatorPublicKey));
            synchronized (generatorAccount) {
                generatorAccount.setBalance(generatorAccount.getBalance() + totalFee * 100L);
                generatorAccount.setUnconfirmedBalance(generatorAccount.getUnconfirmedBalance() + totalFee * 100L);
            }
        }

        synchronized (Iovo.transactions) {
            for (int i = 0; i < numberOfTransactions; i++) {
                Transaction transaction = Iovo.transactions.get(transactions[i]);

                long sender = Account.getId(transaction.senderPublicKey);
                Account senderAccount = accounts.get(sender);
                synchronized (senderAccount) {
                    senderAccount.setBalance(senderAccount.getBalance() - (transaction.amount + transaction.fee) * 100L);
                    senderAccount.setUnconfirmedBalance(senderAccount.getUnconfirmedBalance() - (transaction.amount + transaction.fee) * 100L);

                    if (senderAccount.publicKey == null) {
                        senderAccount.publicKey = transaction.senderPublicKey;
                    }
                }

                Account recipientAccount = accounts.get(transaction.recipient);
                if (recipientAccount == null) {
                    recipientAccount = Account.addAccount(transaction.recipient);
                }

                switch (transaction.type) {
                    case Transaction.TYPE_PAYMENT: {
                        switch (transaction.subtype) {
                            case Transaction.SUBTYPE_PAYMENT_ORDINARY_PAYMENT: {
                                synchronized (recipientAccount) {
                                    recipientAccount.setBalance(recipientAccount.getBalance() + transaction.amount * 100L);
                                    recipientAccount.setUnconfirmedBalance(recipientAccount.getUnconfirmedBalance() + transaction.amount * 100L);
                                }
                            }
                            break;
                        }
                    }
                    break;

                    case Transaction.TYPE_MESSAGING: {
                        switch (transaction.subtype) {
                            case Transaction.SUBTYPE_MESSAGING_ALIAS_ASSIGNMENT: {
                                Transaction.MessagingAliasAssignmentAttachment attachment = (Transaction.MessagingAliasAssignmentAttachment) transaction.attachment;

                                String normalizedAlias = attachment.alias.toLowerCase();
                                synchronized (aliases) {
                                    Alias alias = aliases.get(normalizedAlias);
                                    if (alias == null) {
                                        alias = new Alias(senderAccount, attachment.alias, attachment.uri, timestamp);
                                        aliases.put(normalizedAlias, alias);
                                        aliasIdToAliasMappings.put(transaction.getId(), alias);
                                    } else {
                                        alias.setUri(attachment.uri);
                                        alias.setTimestamp(timestamp);
                                    }
                                }
                            }
                            break;
                        }
                    }
                    break;
                }
            }
        }
    }

    public static long getBaseTarget() throws Exception {
        if (lastBlock == Genesis.GENESIS_BLOCK_ID) {
            return blocks.get(Genesis.GENESIS_BLOCK_ID).baseTarget;
        }

        Block lastBlock = getLastBlock(), previousBlock = blocks.get(lastBlock.previousBlock);
        long curBaseTarget = previousBlock.baseTarget, newBaseTarget = BigInteger.valueOf(curBaseTarget)
                .multiply(BigInteger.valueOf(lastBlock.timestamp - previousBlock.timestamp)).divide(BigInteger.valueOf(60)).longValue();
        if (newBaseTarget < 0 || newBaseTarget > maxBaseTarget) {
            newBaseTarget = maxBaseTarget;
        }

        if (newBaseTarget < curBaseTarget / 2) {
            newBaseTarget = curBaseTarget / 2;
        }
        if (newBaseTarget == 0) {
            newBaseTarget = 1;
        }

        long twofoldCurBaseTarget = curBaseTarget * 2;
        if (twofoldCurBaseTarget < 0) {
            twofoldCurBaseTarget = maxBaseTarget;
        }
        if (newBaseTarget > twofoldCurBaseTarget) {
            newBaseTarget = twofoldCurBaseTarget;
        }
        return newBaseTarget;
    }

    public static Block getBlock(JSONObject blockData) {
        int version = ((Long) blockData.get("version")).intValue();
        int timestamp = ((Long) blockData.get("timestamp")).intValue();
        long previousBlock = (new BigInteger((String) blockData.get("previousBlock"))).longValue();
        int numberOfTransactions = ((Long) blockData.get("numberOfTransactions")).intValue();
        int totalAmount = ((Long) blockData.get("totalAmount")).intValue();
        int totalFee = ((Long) blockData.get("totalFee")).intValue();
        int payloadLength = ((Long) blockData.get("payloadLength")).intValue();
        byte[] payloadHash = ConvertUtils.convert((String) blockData.get("payloadHash"));
        byte[] generatorPublicKey = ConvertUtils.convert((String) blockData.get("generatorPublicKey"));
        byte[] generationSignature = ConvertUtils.convert((String) blockData.get("generationSignature"));
        byte[] blockSignature = ConvertUtils.convert((String) blockData.get("blockSignature"));
        return new Block(version, timestamp, previousBlock, numberOfTransactions, totalAmount, totalFee, payloadLength, payloadHash, generatorPublicKey, generationSignature, blockSignature);
    }

    public static Block getBlockFromRequest(Map<String, Object> blockData) {
        int version = ((Number) blockData.get("version")).intValue();
        int timestamp = ((Number) blockData.get("timestamp")).intValue();
        long previousBlock = (new BigInteger((String) blockData.get("previousBlock"))).longValue();
        int numberOfTransactions = ((Number) blockData.get("numberOfTransactions")).intValue();
        int totalAmount = ((Number) blockData.get("totalAmount")).intValue();
        int totalFee = ((Number) blockData.get("totalFee")).intValue();
        int payloadLength = ((Number) blockData.get("payloadLength")).intValue();
        byte[] payloadHash = ConvertUtils.convert((String) blockData.get("payloadHash"));
        byte[] generatorPublicKey = ConvertUtils.convert((String) blockData.get("generatorPublicKey"));
        byte[] generationSignature = ConvertUtils.convert((String) blockData.get("generationSignature"));
        byte[] blockSignature = ConvertUtils.convert((String) blockData.get("blockSignature"));
        return new Block(version, timestamp, previousBlock, numberOfTransactions, totalAmount, totalFee, payloadLength, payloadHash, generatorPublicKey, generationSignature, blockSignature);
    }

    public byte[] getBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(4 + 4 + 8 + 4 + 4 + 4 + 4 + 32 + 32 + 64 + 64);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(version);
        buffer.putInt(timestamp);
        buffer.putLong(previousBlock);
        buffer.putInt(numberOfTransactions);
        buffer.putInt(totalAmount);
        buffer.putInt(totalFee);
        buffer.putInt(payloadLength);
        buffer.put(payloadHash);
        buffer.put(generatorPublicKey);
        buffer.put(generationSignature);
        buffer.put(blockSignature);
        return buffer.array();
    }

    public long getId() throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(getBytes());
        BigInteger bigInteger = new BigInteger(1, new byte[]{hash[7], hash[6], hash[5], hash[4], hash[3], hash[2], hash[1], hash[0]});
        return bigInteger.longValue();
    }

    public JSONObject getJSONObject(HashMap<Long, Transaction> transactions) {
        JSONObject block = new JSONObject();

        block.put("version", version);
        block.put("timestamp", timestamp);
        block.put("previousBlock", ConvertUtils.convert(previousBlock));
        block.put("numberOfTransactions", numberOfTransactions);
        block.put("totalAmount", totalAmount);
        block.put("totalFee", totalFee);
        block.put("payloadLength", payloadLength);
        block.put("payloadHash", ConvertUtils.convert(payloadHash));
        block.put("generatorPublicKey", ConvertUtils.convert(generatorPublicKey));
        block.put("generationSignature", ConvertUtils.convert(generationSignature));
        block.put("blockSignature", ConvertUtils.convert(blockSignature));

        JSONArray transactionsData = new JSONArray();
        for (int i = 0; i < numberOfTransactions; i++) {
            transactionsData.add(transactions.get(this.transactions[i]).getJSONObject());
        }
        block.put("transactions", transactionsData);
        return block;
    }

    public static Block getLastBlock() {
        return blocks.get(lastBlock);
    }

    public static void loadBlocks(String fileName) throws Exception {
        FileInputStream fileInputStream = new FileInputStream(fileName);
        ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
        blockCounter = objectInputStream.readInt();
        blocks = (HashMap<Long, Block>) objectInputStream.readObject();
        lastBlock = objectInputStream.readLong();
        objectInputStream.close();
        fileInputStream.close();
    }

    public static boolean popLastBlock() {
        if (lastBlock == Genesis.GENESIS_BLOCK_ID) {
            return false;
        }
        try {
            JSONObject response = new JSONObject();
            response.put("response", "processNewData");

            JSONArray addedUnconfirmedTransactions = new JSONArray();
            synchronized (blocks) {
                Block block = Block.getLastBlock();
                Account generatorAccount = accounts.get(Account.getId(block.generatorPublicKey));
                synchronized (generatorAccount) {
                    generatorAccount.setBalance(generatorAccount.getBalance() - block.totalFee * 100L);
                    generatorAccount.setUnconfirmedBalance(generatorAccount.getUnconfirmedBalance() - block.totalFee * 100L);
                }

                synchronized (Iovo.transactions) {
                    for (int i = 0; i < block.numberOfTransactions; i++) {
                        Transaction transaction = Iovo.transactions.remove(block.transactions[i]);
                        unconfirmedTransactions.put(block.transactions[i], transaction);

                        Account senderAccount = accounts.get(Account.getId(transaction.senderPublicKey));
                        synchronized (senderAccount) {
                            senderAccount.setBalance(senderAccount.getBalance() + (transaction.amount + transaction.fee) * 100L);
                        }

                        Account recipientAccount = accounts.get(transaction.recipient);
                        synchronized (recipientAccount) {
                            recipientAccount.setBalance(recipientAccount.getBalance() - transaction.amount * 100L);
                            recipientAccount.setUnconfirmedBalance(recipientAccount.getUnconfirmedBalance() - transaction.amount * 100L);
                        }

                        JSONObject addedUnconfirmedTransaction = new JSONObject();
                        addedUnconfirmedTransaction.put("index", transaction.index);
                        addedUnconfirmedTransaction.put("timestamp", transaction.timestamp);
                        addedUnconfirmedTransaction.put("deadline", transaction.deadline);
                        addedUnconfirmedTransaction.put("recipient", ConvertUtils.convert(transaction.recipient));
                        addedUnconfirmedTransaction.put("amount", transaction.amount);
                        addedUnconfirmedTransaction.put("fee", transaction.fee);
                        addedUnconfirmedTransaction.put("sender", ConvertUtils.convert(Account.getId(transaction.senderPublicKey)));
                        addedUnconfirmedTransaction.put("id", ConvertUtils.convert(transaction.getId()));
                        addedUnconfirmedTransactions.add(addedUnconfirmedTransaction);
                    }
                }

                JSONArray addedOrphanedBlocks = new JSONArray();
                JSONObject addedOrphanedBlock = new JSONObject();
                addedOrphanedBlock.put("index", block.index);
                addedOrphanedBlock.put("timestamp", block.timestamp);
                addedOrphanedBlock.put("numberOfTransactions", block.numberOfTransactions);
                addedOrphanedBlock.put("totalAmount", block.totalAmount);
                addedOrphanedBlock.put("totalFee", block.totalFee);
                addedOrphanedBlock.put("payloadLength", block.payloadLength);
                addedOrphanedBlock.put("generator", ConvertUtils.convert(Account.getId(block.generatorPublicKey)));
                addedOrphanedBlock.put("height", block.height);
                addedOrphanedBlock.put("version", block.version);
                addedOrphanedBlock.put("block", ConvertUtils.convert(block.getId()));
                addedOrphanedBlock.put("baseTarget", BigInteger.valueOf(block.baseTarget).multiply(BigInteger.valueOf(100000)).divide(BigInteger.valueOf(initialBaseTarget)));
                addedOrphanedBlocks.add(addedOrphanedBlock);
                response.put("addedOrphanedBlocks", addedOrphanedBlocks);

                lastBlock = block.previousBlock;
            }

            if (addedUnconfirmedTransactions.size() > 0) {
                response.put("addedUnconfirmedTransactions", addedUnconfirmedTransactions);
            }

            for (User user : users.values()) {
                user.send(response);
            }
        } catch (Exception e) {
            logger.error("19: " + e.toString());
            return false;
        }
        return true;
    }

    public static boolean pushBlock(ByteBuffer buffer, boolean savingFlag) {
        buffer.flip();
        int version = buffer.getInt();
        if (version != 1) {
            return false;
        }

        int blockTimestamp = buffer.getInt();
        long previousBlock = buffer.getLong();
        int numberOfTransactions = buffer.getInt();
        int totalAmount = buffer.getInt();
        int totalFee = buffer.getInt();
        int payloadLength = buffer.getInt();
        byte[] payloadHash = new byte[32];
        buffer.get(payloadHash);
        byte[] generatorPublicKey = new byte[32];
        buffer.get(generatorPublicKey);
        byte[] generationSignature = new byte[64];
        buffer.get(generationSignature);
        byte[] blockSignature = new byte[64];
        buffer.get(blockSignature);

        if (Block.getLastBlock().previousBlock == previousBlock) {
            return false;
        }

        int curTime = getEpochTime(System.currentTimeMillis());
        if (blockTimestamp > curTime + 15 || blockTimestamp <= Block.getLastBlock().timestamp) {
            return false;
        }

        if (payloadLength > MAX_PAYLOAD_LENGTH || BLOCK_HEADER_LENGTH + payloadLength != buffer.capacity()) {
            return false;
        }

        Block block = new Block(version, blockTimestamp, previousBlock, numberOfTransactions, totalAmount, totalFee, payloadLength, payloadHash, generatorPublicKey, generationSignature, blockSignature);
        synchronized (blocks) {
            block.index = ++blockCounter;
        }

        try {
            if (block.previousBlock != lastBlock || blocks.get(block.getId()) != null || !block.verifyGenerationSignature() || !block.verifyBlockSignature()) {
                return false;
            }

            HashMap<Long, Transaction> blockTransactions = new HashMap<>();
            HashSet<String> blockAliases = new HashSet<>();
            block.transactions = new long[block.numberOfTransactions];
            for (int i = 0; i < block.numberOfTransactions; i++) {
                Transaction transaction = Transaction.getTransaction(buffer);
                synchronized (Iovo.transactions) {
                    transaction.index = ++transactionCounter;
                }
                if (blockTransactions.put(block.transactions[i] = transaction.getId(), transaction) != null) {
                    return false;
                }

                switch (transaction.type) {
                    case Transaction.TYPE_MESSAGING: {
                        switch (transaction.subtype) {
                            case Transaction.SUBTYPE_MESSAGING_ALIAS_ASSIGNMENT: {
                                if (!blockAliases.add(((Transaction.MessagingAliasAssignmentAttachment) transaction.attachment).alias.toLowerCase())) {
                                    return false;
                                }
                            }
                            break;
                        }
                    }
                    break;
                }
            }
            Arrays.sort(block.transactions);

            HashMap<Long, Long> accumulatedAmounts = new HashMap<>();
            HashMap<Long, HashMap<Long, Long>> accumulatedAssetQuantities = new HashMap<>();
            int calculatedTotalAmount = 0, calculatedTotalFee = 0;
            int i;
            for (i = 0; i < block.numberOfTransactions; i++) {
                Transaction transaction = blockTransactions.get(block.transactions[i]);
                if (transaction.timestamp > curTime + 15 || transaction.deadline < 1 ||
                        (transaction.timestamp + transaction.deadline * 60 < blockTimestamp && getLastBlock().height > 303) ||
                        transaction.fee <= 0 || !transaction.validateAttachment() ||
                        Iovo.transactions.get(block.transactions[i]) != null ||
                        (transaction.referencedTransaction != 0 &&
                                Iovo.transactions.get(transaction.referencedTransaction) == null &&
                                blockTransactions.get(transaction.referencedTransaction) == null) ||
                        (unconfirmedTransactions.get(block.transactions[i]) == null && !transaction.verify())) {
                    break;
                }

                long sender = Account.getId(transaction.senderPublicKey);
                Long accumulatedAmount = accumulatedAmounts.get(sender);
                if (accumulatedAmount == null) {
                    accumulatedAmount = new Long(0);
                }
                accumulatedAmounts.put(sender, accumulatedAmount + (transaction.amount + transaction.fee) * 100L);
                if (transaction.type == Transaction.TYPE_PAYMENT) {
                    if (transaction.subtype == Transaction.SUBTYPE_PAYMENT_ORDINARY_PAYMENT) {
                        calculatedTotalAmount += transaction.amount;
                    } else {
                        break;
                    }
                } else if (transaction.type == Transaction.TYPE_MESSAGING) {

                    if (transaction.subtype != Transaction.SUBTYPE_MESSAGING_ALIAS_ASSIGNMENT) {
                        break;
                    }
                } else {
                    break;
                }
                calculatedTotalFee += transaction.fee;
            }

            if (i != block.numberOfTransactions || calculatedTotalAmount != block.totalAmount || calculatedTotalFee != block.totalFee) {
                return false;
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (i = 0; i < block.numberOfTransactions; i++) {
                digest.update(blockTransactions.get(block.transactions[i]).getBytes());
            }
            if (!Arrays.equals(digest.digest(), block.payloadHash)) {
                return false;
            }

            synchronized (blocks) {
                for (Map.Entry<Long, Long> accumulatedAmountEntry : accumulatedAmounts.entrySet()) {
                    Account senderAccount = accounts.get(accumulatedAmountEntry.getKey());
                    if (senderAccount.getBalance() < accumulatedAmountEntry.getValue()) {
                        return false;
                    }
                }

                for (Map.Entry<Long, HashMap<Long, Long>> accumulatedAssetQuantitiesEntry : accumulatedAssetQuantities.entrySet()) {
                    Account senderAccount = accounts.get(accumulatedAssetQuantitiesEntry.getKey());
                    for (Map.Entry<Long, Long> accountAccumulatedAssetQuantitiesEntry : accumulatedAssetQuantitiesEntry.getValue().entrySet()) {
                        long asset = accountAccumulatedAssetQuantitiesEntry.getKey();
                        long quantity = accountAccumulatedAssetQuantitiesEntry.getValue();
                        if (senderAccount.assetBalances.get(asset) < quantity) {
                            return false;
                        }
                    }
                }

                if (block.previousBlock != lastBlock) {
                    return false;
                }

                synchronized (Iovo.transactions) {
                    for (Map.Entry<Long, Transaction> transactionEntry : blockTransactions.entrySet()) {
                        Transaction transaction = transactionEntry.getValue();
                        transaction.height = block.height;
                        Iovo.transactions.put(transactionEntry.getKey(), transaction);
                    }
                }

                block.analyze();

                JSONArray addedConfirmedTransactions = new JSONArray();
                JSONArray removedUnconfirmedTransactions = new JSONArray();

                for (Map.Entry<Long, Transaction> transactionEntry : blockTransactions.entrySet()) {
                    Transaction transaction = transactionEntry.getValue();
                    JSONObject addedConfirmedTransaction = new JSONObject();
                    addedConfirmedTransaction.put("index", transaction.index);
                    addedConfirmedTransaction.put("blockTimestamp", block.timestamp);
                    addedConfirmedTransaction.put("transactionTimestamp", transaction.timestamp);
                    addedConfirmedTransaction.put("sender", ConvertUtils.convert(Account.getId(transaction.senderPublicKey)));
                    addedConfirmedTransaction.put("recipient", ConvertUtils.convert(transaction.recipient));
                    addedConfirmedTransaction.put("amount", transaction.amount);
                    addedConfirmedTransaction.put("fee", transaction.fee);
                    addedConfirmedTransaction.put("id", ConvertUtils.convert(transaction.getId()));
                    addedConfirmedTransactions.add(addedConfirmedTransaction);

                    Transaction removedTransaction = unconfirmedTransactions.remove(transactionEntry.getKey());
                    if (removedTransaction != null) {
                        JSONObject removedUnconfirmedTransaction = new JSONObject();
                        removedUnconfirmedTransaction.put("index", removedTransaction.index);
                        removedUnconfirmedTransactions.add(removedUnconfirmedTransaction);

                        Account senderAccount = accounts.get(Account.getId(removedTransaction.senderPublicKey));
                        synchronized (senderAccount) {
                            senderAccount.setUnconfirmedBalance(senderAccount.getUnconfirmedBalance() + (removedTransaction.amount + removedTransaction.fee) * 100L);
                        }
                    }
                    // TODO: Remove from double-spending transactions
                }

                long blockId = block.getId();
                for (i = 0; i < block.transactions.length; i++) {
                    Iovo.transactions.get(block.transactions[i]).block = blockId;
                }
                if (savingFlag) {
                    Transaction.saveTransactions("transactions.iovo");
                    Block.saveBlocks("blocks.iovo", false);
                }
                if (block.timestamp >= curTime - 15) {
                    JSONObject request = block.getJSONObject(Iovo.transactions);
                    request.put("requestType", "processBlock");

                    Peer.sendToAllPeers(request);
                }

                JSONArray addedRecentBlocks = new JSONArray();
                JSONObject addedRecentBlock = new JSONObject();
                addedRecentBlock.put("index", block.index);
                addedRecentBlock.put("timestamp", block.timestamp);
                addedRecentBlock.put("numberOfTransactions", block.numberOfTransactions);
                addedRecentBlock.put("totalAmount", block.totalAmount);
                addedRecentBlock.put("totalFee", block.totalFee);
                addedRecentBlock.put("payloadLength", block.payloadLength);
                addedRecentBlock.put("generator", ConvertUtils.convert(Account.getId(block.generatorPublicKey)));
                addedRecentBlock.put("height", Block.getLastBlock().height);
                addedRecentBlock.put("version", block.version);
                addedRecentBlock.put("block", ConvertUtils.convert(block.getId()));
                addedRecentBlock.put("baseTarget", BigInteger.valueOf(block.baseTarget).multiply(BigInteger.valueOf(100000)).divide(BigInteger.valueOf(initialBaseTarget)));
                addedRecentBlocks.add(addedRecentBlock);

                JSONObject response = new JSONObject();
                response.put("response", "processNewData");
                response.put("addedConfirmedTransactions", addedConfirmedTransactions);
                if (removedUnconfirmedTransactions.size() > 0) {
                    response.put("removedUnconfirmedTransactions", removedUnconfirmedTransactions);
                }
                response.put("addedRecentBlocks", addedRecentBlocks);

                for (User user : users.values()) {
                    user.send(response);
                }
            }
            return true;
        } catch (Exception e) {
            logger.error("11: " + e.toString());
            return false;
        }
    }

    public static void saveBlocks(String fileName, boolean flag) throws Exception {
        synchronized (blocks) {
            FileOutputStream fileOutputStream = new FileOutputStream(fileName);
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);
            objectOutputStream.writeInt(blockCounter);
            objectOutputStream.writeObject(blocks);
            objectOutputStream.writeLong(lastBlock);
            objectOutputStream.close();
            fileOutputStream.close();
        }
    }

    public boolean verifyBlockSignature() throws Exception {
        Account account = accounts.get(Account.getId(generatorPublicKey));
        if (account == null) {
            return false;
        } else if (account.publicKey == null) {
            account.publicKey = generatorPublicKey;
        } else if (!Arrays.equals(generatorPublicKey, account.publicKey)) {
            return false;
        }
        byte[] data = getBytes();
        byte[] data2 = new byte[data.length - 64];
        System.arraycopy(data, 0, data2, 0, data2.length);
        if (!Crypto.verify(blockSignature, data2, generatorPublicKey)) {
            return false;
        }
        return true;
    }

    public boolean verifyGenerationSignature() {
        try {
            if (getLastBlock().height <= 20000) {
                Block previousBlock = blocks.get(this.previousBlock);
                if (previousBlock == null) {
                    return false;
                }
                if (!Crypto.verify(generationSignature, previousBlock.generationSignature, generatorPublicKey)) {
                    return false;
                }
                Account account = accounts.get(Account.getId(generatorPublicKey));
                if (account == null || account.getEffectiveBalance() == 0) {
                    return false;
                }
                int elapsedTime = timestamp - previousBlock.timestamp;
                BigInteger target = BigInteger.valueOf(Block.getBaseTarget()).multiply(BigInteger.valueOf(account.getEffectiveBalance())).multiply(BigInteger.valueOf(elapsedTime));
                byte[] generationSignatureHash = MessageDigest.getInstance("SHA-256").digest(generationSignature);
                BigInteger hit = new BigInteger(1, new byte[]{generationSignatureHash[7], generationSignatureHash[6], generationSignatureHash[5],
                        generationSignatureHash[4], generationSignatureHash[3], generationSignatureHash[2], generationSignatureHash[1], generationSignatureHash[0]});
                if (hit.compareTo(target) >= 0) {
                    return false;
                }
            } else {
                Block previousBlock = blocks.get(this.previousBlock);
                if (previousBlock == null) {
                    return false;
                }
                if (!Crypto.verify(generationSignature, previousBlock.generationSignature, generatorPublicKey)) {
                    return false;
                }
                Account account = accounts.get(Account.getId(generatorPublicKey));
                if (account == null || account.getEffectiveBalance() == 0) {
                    return false;
                }
                int elapsedTime = timestamp - previousBlock.timestamp;
                BigInteger target = BigInteger.valueOf(Block.getBaseTarget()).multiply(BigInteger.valueOf(account.getEffectiveBalance())).multiply(BigInteger.valueOf(elapsedTime));
                byte[] generationSignatureHash = MessageDigest.getInstance("SHA-256").digest(generationSignature);
                BigInteger hit = new BigInteger(1, new byte[]{generationSignatureHash[7], generationSignatureHash[6], generationSignatureHash[5],
                        generationSignatureHash[4], generationSignatureHash[3], generationSignatureHash[2], generationSignatureHash[1], generationSignatureHash[0]});
                if (hit.compareTo(target) >= 0) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}