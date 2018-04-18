package io.iovo.node.model;

import io.iovo.node.crypto.Crypto;
import io.iovo.node.utils.TimeUtils;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static io.iovo.node.Iovo.*;
import static io.iovo.node.model.Block.MAX_PAYLOAD_LENGTH;
import static io.iovo.node.utils.ConvertUtils.convert;

public class Account {
    private static final Logger logger = LogManager.getLogger();
    public long id;
    @Getter
    private long balance;
    public int height;
    public byte[] publicKey;
    public HashMap<Long, Integer> assetBalances;

    @Getter
    private long unconfirmedBalance;
    private HashMap<Long, Integer> unconfirmedAssetBalances;

    public Account(long id) {
        this.id = id;
        this.height = Block.getLastBlock().height;
        this.assetBalances = new HashMap<>();
        this.unconfirmedAssetBalances = new HashMap<>();
    }

    public static Account addAccount(long id) {
        synchronized (accounts) {
            Account account = new Account(id);
            accounts.put(id, account);
            return account;
        }
    }

    public void generateBlock(String secretPhrase) throws Exception {
        Transaction[] sortedTransactions;
        synchronized (transactions) {
            sortedTransactions = unconfirmedTransactions.values().toArray(new Transaction[0]);
            while (sortedTransactions.length > 0) {
                int i;
                for (i = 0; i < sortedTransactions.length; i++) {
                    Transaction transaction = sortedTransactions[i];
                    if (transaction.referencedTransaction != 0 && transactions.get(transaction.referencedTransaction) == null) {
                        sortedTransactions[i] = sortedTransactions[sortedTransactions.length - 1];
                        Transaction[] tmp = new Transaction[sortedTransactions.length - 1];
                        System.arraycopy(sortedTransactions, 0, tmp, 0, tmp.length);
                        sortedTransactions = tmp;
                        break;
                    }
                }
                if (i == sortedTransactions.length) {
                    break;
                }
            }
        }
        Arrays.sort(sortedTransactions);
        HashMap<Long, Transaction> newTransactions = new HashMap<>();
        HashSet<String> newAliases = new HashSet<>();
        HashMap<Long, Long> accumulatedAmounts = new HashMap<>();
        int payloadLength = 0;
        while (payloadLength <= MAX_PAYLOAD_LENGTH) {
            int prevNumberOfNewTransactions = newTransactions.size();
            for (int i = 0; i < sortedTransactions.length; i++) {
                Transaction transaction = sortedTransactions[i];
                int transactionLength = transaction.getBytes().length;
                if (newTransactions.get(transaction.getId()) == null && payloadLength + transactionLength <= MAX_PAYLOAD_LENGTH) {
                    long sender = Account.getId(transaction.senderPublicKey);
                    Long accumulatedAmount = accumulatedAmounts.get(sender);
                    if (accumulatedAmount == null) {
                        accumulatedAmount = new Long(0);
                    }

                    long amount = (transaction.amount + transaction.fee) * 100L;
                    if (accumulatedAmount + amount <= accounts.get(sender).balance && transaction.validateAttachment()) {
                        switch (transaction.type) {
                            case Transaction.TYPE_MESSAGING:
                            {
                                switch (transaction.subtype) {
                                    case Transaction.SUBTYPE_MESSAGING_ALIAS_ASSIGNMENT:
                                    {
                                        if (!newAliases.add(((Transaction.MessagingAliasAssignmentAttachment)transaction.attachment).alias.toLowerCase())) {
                                            continue;
                                        }
                                    }
                                    break;
                                }
                            }
                            break;
                        }
                        accumulatedAmounts.put(sender, accumulatedAmount + amount);
                        newTransactions.put(transaction.getId(), transaction);
                        payloadLength += transactionLength;
                    }
                }
            }

            if (newTransactions.size() == prevNumberOfNewTransactions) {
                break;
            }
        }

        Block block = new Block(1, TimeUtils.getEpochTime(System.currentTimeMillis()), lastBlock, newTransactions.size(), 0, 0, 0, null, Crypto.getPublicKey(secretPhrase), null, new byte[64]);
        block.transactions = new long[block.numberOfTransactions];
        int i = 0;
        for (Map.Entry<Long, Transaction> transactionEntry : newTransactions.entrySet()) {
            Transaction transaction = transactionEntry.getValue();
            block.totalAmount += transaction.amount;
            block.totalFee += transaction.fee;
            block.payloadLength += transaction.getBytes().length;
            block.transactions[i++] = transactionEntry.getKey();
        }

        Arrays.sort(block.transactions);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (i = 0; i < block.numberOfTransactions; i++) {
            digest.update(newTransactions.get(block.transactions[i]).getBytes());
        }
        block.payloadHash = digest.digest();
        block.generationSignature = Crypto.sign(Block.getLastBlock().generationSignature, secretPhrase);

        byte[] data = block.getBytes();
        byte[] data2 = new byte[data.length - 64];
        System.arraycopy(data, 0, data2, 0, data2.length);
        block.blockSignature = Crypto.sign(data2, secretPhrase);

        JSONObject request = block.getJSONObject(newTransactions);
        request.put("command", "processBlock");
        if (block.verifyBlockSignature() && block.verifyGenerationSignature()) {
            Peer.sendToAllPeers(request);
            logger.info("New block generated {}. Now sending to peers...", convert(block.getId()));
        } else {
            logger.info("Generated an incorrect block. Waiting for the next one...");
        }
    }

    public int getEffectiveBalance() {
        if (height == 0) {
            return (int)(balance / 100);
        }
        if (Block.getLastBlock().height - height < 1440) {
            return 0;
        }
        int amount = 0;
        for (long transactionId : Block.getLastBlock().transactions) {
            Transaction transaction = transactions.get(transactionId);
            if (transaction.recipient == id) {
                amount += transaction.amount;
            }
        }
        return (int)(balance / 100) - amount;
    }

    public static long getId(byte[] publicKey) throws Exception {
        byte[] publicKeyHash = MessageDigest.getInstance("SHA-256").digest(publicKey);
        BigInteger bigInteger = new BigInteger(1, new byte[] {publicKeyHash[7], publicKeyHash[6], publicKeyHash[5], publicKeyHash[4], publicKeyHash[3], publicKeyHash[2], publicKeyHash[1], publicKeyHash[0]});
        return bigInteger.longValue();
    }

    public void setBalance(long balance) {
        this.balance = balance;
        for (Peer peer : peers.values()) {
            try {
                if (peer.accountId == id && peer.adjustedWeight > 0) {
                    peer.updateWeight();
                }
            } catch (NullPointerException e) {
                logger.error("TODO");
            }
        }
    }

    public void setUnconfirmedBalance(long unconfirmedBalance) throws Exception {
        this.unconfirmedBalance = unconfirmedBalance;
        JSONObject response = new JSONObject();
        response.put("response", "setBalance");
        response.put("balance", unconfirmedBalance);
        for (User user : users.values()) {
            if (user.secretPhrase != null && Account.getId(Crypto.getPublicKey(user.secretPhrase)) == id) {
                user.send(response);
            }
        }
    }
}