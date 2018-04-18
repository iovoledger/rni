package io.iovo.node.model;

import io.iovo.node.crypto.Crypto;
import io.iovo.node.service.model.TransactionToProcess;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.*;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import static io.iovo.node.Iovo.*;
import static io.iovo.node.model.Block.CREATOR_ID;
import static io.iovo.node.utils.ConvertUtils.alphabet;
import static io.iovo.node.utils.ConvertUtils.convert;
import static io.iovo.node.utils.TimeUtils.getEpochTime;

public class Transaction implements Comparable<Transaction>, Serializable {
    private static final Logger logger = LogManager.getLogger();
    static final long serialVersionUID = 0;

    public static final byte TYPE_PAYMENT = 0;
    public static final byte TYPE_MESSAGING = 1;

    public static final byte SUBTYPE_PAYMENT_ORDINARY_PAYMENT = 0;

    public static final byte SUBTYPE_MESSAGING_ARBITRARY_MESSAGE = 0;
    public static final byte SUBTYPE_MESSAGING_ALIAS_ASSIGNMENT = 1;

    private static final int ASSET_ISSUANCE_FEE = 1000;

    public byte type, subtype;
    public int timestamp;
    public short deadline;
    public byte[] senderPublicKey;
    public long recipient;
    public int amount, fee;
    public long referencedTransaction;
    public byte[] signature;
    public Attachment attachment;

    public int index;
    public long block;
    public int height;

    public Transaction(byte type, byte subtype, int timestamp, short deadline, byte[] senderPublicKey, long recipient, int amount, int fee, long referencedTransaction, byte[] signature) {
        this.type = type;
        this.subtype = subtype;
        this.timestamp = timestamp;
        this.deadline = deadline;
        this.senderPublicKey = senderPublicKey;
        this.recipient = recipient;
        this.amount = amount;
        this.fee = fee;
        this.referencedTransaction = referencedTransaction;
        this.signature = signature;
        this.height = Integer.MAX_VALUE;
    }

    public transient volatile long id;
    transient volatile String stringId = null;
    transient volatile long senderAccountId;

    public long getId() {
        calculateIds();
        return id;
    }

    public String getStringId() {
        calculateIds();
        return stringId;
    }

    public long getSenderAccountId() {
        calculateIds();
        return senderAccountId;
    }

    private void calculateIds() {
        if (stringId != null) {
            return;
        }
        byte[] hash = getMessageDigest("SHA-256").digest(getBytes());
        BigInteger bigInteger = new BigInteger(1, new byte[] {hash[7], hash[6], hash[5], hash[4], hash[3], hash[2], hash[1], hash[0]});
        id = bigInteger.longValue();
        stringId = bigInteger.toString();
        try {
            senderAccountId = Account.getId(senderPublicKey);
        } catch (Exception e) {
            logger.error("Error while getting IDs");
        }
    }

    static MessageDigest getMessageDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            logger.error("Missing message digest algorithm: " + algorithm);
            System.exit(1);
            return null;
        }
    }

    @Override
    public int compareTo(Transaction o) {
        if (height < o.height) {
            return -1;
        } else if (height > o.height) {
            return 1;
        } else {
            if (fee * 1048576L / getBytes().length > o.fee * 1048576L / o.getBytes().length) {
                return -1;
            } else if (fee * 1048576L / getBytes().length < o.fee * 1048576L / o.getBytes().length) {
                return 1;
            } else {
                if (timestamp < o.timestamp) {
                    return -1;
                } else if (timestamp > o.timestamp) {
                    return 1;
                } else {
                    if (index < o.index) {
                        return -1;
                    } else if (index > o.index) {
                        return 1;
                    } else {
                        return 0;
                    }
                }
            }
        }
    }

    public byte[] getBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(1 + 1 + 4 + 2 + 32 + 8 + 4 + 4 + 8 + 64 + (attachment == null ? 0 : attachment.getBytes().length));
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(type);
        buffer.put(subtype);
        buffer.putInt(timestamp);
        buffer.putShort(deadline);
        buffer.put(senderPublicKey);
        buffer.putLong(recipient);
        buffer.putInt(amount);
        buffer.putInt(fee);
        buffer.putLong(referencedTransaction);
        buffer.put(signature);
        if (attachment != null) {
            buffer.put(attachment.getBytes());
        }
        return buffer.array();
    }

    public JSONObject getJSONObject() {
        JSONObject transaction = new JSONObject();
        transaction.put("type", type);
        transaction.put("subtype", subtype);
        transaction.put("timestamp", timestamp);
        transaction.put("deadline", deadline);
        transaction.put("senderPublicKey", convert(senderPublicKey));
        transaction.put("recipient", convert(recipient));
        transaction.put("amount", amount);
        transaction.put("fee", fee);
        transaction.put("referencedTransaction", convert(referencedTransaction));
        transaction.put("signature", convert(signature));
        if (attachment != null) {
            transaction.put("attachment", attachment.getJSONObject());
        }
        return transaction;
    }

    public static Transaction getTransaction(ByteBuffer buffer) {
        byte type = buffer.get();
        byte subtype = buffer.get();
        int timestamp = buffer.getInt();
        short deadline = buffer.getShort();
        byte[] senderPublicKey = new byte[32];
        buffer.get(senderPublicKey);
        long recipient = buffer.getLong();
        int amount = buffer.getInt();
        int fee = buffer.getInt();
        long referencedTransaction = buffer.getLong();
        byte[] signature = new byte[64];
        buffer.get(signature);

        Transaction transaction = new Transaction(type, subtype, timestamp, deadline, senderPublicKey, recipient, amount, fee, referencedTransaction, signature);

        switch (type) {
            case Transaction.TYPE_MESSAGING:
            {
                switch (subtype) {
                    case Transaction.SUBTYPE_MESSAGING_ALIAS_ASSIGNMENT:
                    {
                        int aliasLength = buffer.get();
                        byte[] alias = new byte[aliasLength];
                        buffer.get(alias);
                        int uriLength = buffer.getShort();
                        byte[] uri = new byte[uriLength];
                        buffer.get(uri);
                        try {
                            transaction.attachment = new Transaction.MessagingAliasAssignmentAttachment(new String(alias, "UTF-8"), new String(uri, "UTF-8"));
                        } catch (Exception e) { }
                    }
                    break;
                }
            }
            break;
        }
        return transaction;
    }

    public static Transaction getTransaction(JSONObject transactionData) {
        byte type = ((Long)transactionData.get("type")).byteValue();
        byte subtype = ((Long)transactionData.get("subtype")).byteValue();
        int timestamp = ((Long)transactionData.get("timestamp")).intValue();
        short deadline = ((Long)transactionData.get("deadline")).shortValue();
        byte[] senderPublicKey = convert((String)transactionData.get("senderPublicKey"));
        long recipient = (new BigInteger((String)transactionData.get("recipient"))).longValue();
        int amount = ((Long)transactionData.get("amount")).intValue();
        int fee = ((Long)transactionData.get("fee")).intValue();
        long referencedTransaction = (new BigInteger((String)transactionData.get("referencedTransaction"))).longValue();
        byte[] signature = convert((String)transactionData.get("signature"));

        Transaction transaction = new Transaction(type, subtype, timestamp, deadline, senderPublicKey, recipient, amount, fee, referencedTransaction, signature);

        JSONObject attachmentData = (JSONObject)transactionData.get("attachment");
        switch (type) {
            case TYPE_MESSAGING:
            {
                switch (subtype) {
                    case SUBTYPE_MESSAGING_ALIAS_ASSIGNMENT:
                    {
                        String alias = (String)attachmentData.get("alias");
                        String uri = (String)attachmentData.get("uri");
                        transaction.attachment = new MessagingAliasAssignmentAttachment(alias.trim(), uri.trim());
                    }
                    break;
                }
            }
            break;
        }
        return transaction;
    }

    public static Transaction getTransaction(TransactionToProcess tx) {
        byte type = Long.valueOf(tx.getType()).byteValue();
        byte subtype = Long.valueOf(tx.getSubtype()).byteValue();
        int timestamp = Integer.parseInt(tx.getTimestamp());
        short deadline = Long.valueOf(tx.getDeadline()).shortValue();
        byte[] senderPublicKey = convert(tx.getSenderPublicKey());
        long recipient = Long.valueOf(tx.getRecipient());
        int amount = Integer.parseInt(tx.getAmount());
        int fee = Integer.parseInt(tx.getFee());
        long referencedTransaction = Long.valueOf(tx.getReferencedTransaction());
        byte[] signature = convert(tx.getSignature());
        return new Transaction(type, subtype, timestamp, deadline, senderPublicKey, recipient, amount, fee, referencedTransaction, signature);
    }

    public static Transaction getTransactionFromRequest(Map<String, Object> transactionData) {
        byte type = ((Long) transactionData.get("type")).byteValue();
        byte subtype = ((Long) transactionData.get("subtype")).byteValue();
        int timestamp = ((Number) transactionData.get("timestamp")).intValue();
        short deadline = ((Long) transactionData.get("deadline")).shortValue();
        byte[] senderPublicKey = convert((String)transactionData.get("senderPublicKey"));
        long recipient = (new BigInteger((String)transactionData.get("recipient"))).longValue();
        int amount = ((Number) transactionData.get("amount")).intValue();
        int fee = ((Number) transactionData.get("fee")).intValue();
        long referencedTransaction = (new BigInteger((String)transactionData.get("referencedTransaction"))).longValue();
        byte[] signature = convert((String) transactionData.get("signature"));

        Transaction transaction = new Transaction(type, subtype, timestamp, deadline, senderPublicKey, recipient, amount, fee, referencedTransaction, signature);

        JSONObject attachmentData = (JSONObject)transactionData.get("attachment");
        switch (type) {
            case TYPE_MESSAGING:
            {
                switch (subtype) {
                    case SUBTYPE_MESSAGING_ALIAS_ASSIGNMENT:
                    {
                        String alias = (String)attachmentData.get("alias");
                        String uri = (String)attachmentData.get("uri");
                        transaction.attachment = new MessagingAliasAssignmentAttachment(alias.trim(), uri.trim());
                    }
                    break;
                }
            }
            break;
        }
        return transaction;
    }

    public static void loadTransactions(String fileName) throws Exception {
        FileInputStream fileInputStream = new FileInputStream(fileName);
        ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
        transactionCounter = objectInputStream.readInt();
        transactions = (HashMap<Long, Transaction>)objectInputStream.readObject();
        objectInputStream.close();
        fileInputStream.close();
    }

    @SuppressWarnings("unchecked")
    public static <T extends List<?>> T cast(Object obj) {
        return (T) obj;
    }

    public static void processTransactions(Map<String,Object> request, String parameterName) {
        List<Map<String, Object>> transactionsData = ((List) request.get(parameterName));
        List<Map<String, Object>> validTransactionsData = new ArrayList<>();

        for (int i = 0; i < transactionsData.size(); i++) {
            Map<String, Object> transactionData = transactionsData.get(i);
            Transaction transaction = Transaction.getTransactionFromRequest(transactionData);
            try {
                int curTime = getEpochTime(System.currentTimeMillis());
                if (transaction.timestamp > curTime + 15 || transaction.deadline < 1 || transaction.timestamp + transaction.deadline * 60 < curTime || transaction.fee <= 0 || !transaction.validateAttachment()) {
                    continue;
                }

                synchronized (transactions) {
                    long id = transaction.getId();
                    if (transactions.get(id) != null || unconfirmedTransactions.get(id) != null || doubleSpendingTransactions.get(id) != null || !transaction.verify()) {
                        continue;
                    }

                    boolean doubleSpendingTransaction;
                    long senderId = Account.getId(transaction.senderPublicKey);
                    Account account = accounts.get(senderId);
                    if (account == null) {
                        doubleSpendingTransaction = true;
                    } else {
                        int amount = transaction.amount + transaction.fee;
                        synchronized (account) {
                            if (account.getUnconfirmedBalance() < amount * 100L) {
                                doubleSpendingTransaction = true;
                            } else {
                                doubleSpendingTransaction = false;
                                account.setUnconfirmedBalance(account.getUnconfirmedBalance() - amount * 100L);
                            }
                        }
                    }
                    transaction.index = ++transactionCounter;

                    if (doubleSpendingTransaction) {
                        doubleSpendingTransactions.put(transaction.getId(), transaction);
                    } else {
                        unconfirmedTransactions.put(transaction.getId(), transaction);
                        if (parameterName.equals("transactions")) {
                            validTransactionsData.add(transactionData);
                        }
                    }

                    JSONObject response = new JSONObject();
                    response.put("response", "processNewData");

                    JSONArray newTransactions = new JSONArray();
                    JSONObject newTransaction = new JSONObject();
                    newTransaction.put("index", transaction.index);
                    newTransaction.put("timestamp", transaction.timestamp);
                    newTransaction.put("deadline", transaction.deadline);
                    newTransaction.put("recipient", convert(transaction.recipient));
                    newTransaction.put("amount", transaction.amount);
                    newTransaction.put("fee", transaction.fee);
                    newTransaction.put("sender", convert(senderId));
                    newTransaction.put("id", convert(transaction.getId()));
                    newTransactions.add(newTransaction);

                    if (doubleSpendingTransaction) {
                        response.put("addedDoubleSpendingTransactions", newTransactions);
                    } else {
                        response.put("addedUnconfirmedTransactions", newTransactions);
                    }

                    for (User user : users.values()) {
                        user.send(response);
                    }
                }
            } catch (Exception e) {
                logger.info("15: " + e.toString());
            }
        }

        if (validTransactionsData.size() > 0) {
            JSONObject peerRequest = new JSONObject();
            peerRequest.put("command", "processTransactions");
            peerRequest.put("transactions", validTransactionsData);

            Peer.sendToAllPeers(peerRequest);
        }
    }

    public static void saveTransactions(String fileName) throws Exception {
        synchronized (transactions) {
            FileOutputStream fileOutputStream = new FileOutputStream(fileName);
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);
            objectOutputStream.writeInt(transactionCounter);
            objectOutputStream.writeObject(transactions);
            objectOutputStream.close();
            fileOutputStream.close();
        }
    }

    public void sign(String secretPhrase) {
        signature = Crypto.sign(getBytes(), secretPhrase);
        try {
            while (!verify()) {
                timestamp++;
                signature = new byte[64];
                signature = Crypto.sign(getBytes(), secretPhrase);
            }
        } catch (Exception e) {
            logger.error("16: " + e.toString());
        }
    }

    public boolean validateAttachment() {
        if (fee > 1000000000) {
            return false;
        }

        switch (type) {
            case TYPE_PAYMENT:
            {
                switch (subtype) {
                    case SUBTYPE_PAYMENT_ORDINARY_PAYMENT:
                    {
                        if (amount <= 0 || amount > 1000000000) {
                            return false;
                        } else {
                            return true;
                        }
                    }
                    default:
                    {
                        return false;
                    }
                }
            }

            case TYPE_MESSAGING:
            {
                switch (subtype) {
                    case SUBTYPE_MESSAGING_ALIAS_ASSIGNMENT:
                    {
                        if (Block.getLastBlock().height < 22000) {
                            return false;
                        }

                        try {
                            MessagingAliasAssignmentAttachment attachment = (MessagingAliasAssignmentAttachment)this.attachment;
                            if (recipient != CREATOR_ID || amount != 0 || attachment.alias.length() == 0 || attachment.alias.length() > 100 || attachment.uri.length() > 1000) {
                                return false;
                            } else {
                                String normalizedAlias = attachment.alias.toLowerCase();
                                for (int i = 0; i < normalizedAlias.length(); i++) {
                                    if (alphabet.indexOf(normalizedAlias.charAt(i)) < 0) {
                                        return false;
                                    }
                                }

                                Alias alias;
                                synchronized (aliases) {
                                    alias = aliases.get(normalizedAlias);
                                }
                                if (alias != null && alias.getAccount().id != Account.getId(senderPublicKey)) {
                                    return false;
                                }
                                return true;
                            }
                        } catch (Exception e) {
                            return false;
                        }
                    }
                    default:
                    {
                        return false;
                    }
                }
            }
            default:
            {
                return false;
            }
        }
    }

    boolean verify() throws Exception {
        Account account = accounts.get(Account.getId(senderPublicKey));
        if (account == null) {
            return false;
        } else if (account.publicKey == null) {
            account.publicKey = senderPublicKey;
        } else if (!Arrays.equals(senderPublicKey, account.publicKey)) {
            return false;
        }

        byte[] data = getBytes();
        for (int i = 64; i < 128; i++) {
            data[i] = 0;
        }
        return Crypto.verify(signature, data, senderPublicKey);
    }

    public interface Attachment {
        byte[] getBytes();
        JSONObject getJSONObject();
    }

    public static class MessagingAliasAssignmentAttachment implements Attachment, Serializable {
        static final long serialVersionUID = 0;

        String alias;
        String uri;

        MessagingAliasAssignmentAttachment(String alias, String uri) {
            this.alias = alias;
            this.uri = uri;
        }

        @Override
        public byte[] getBytes() {
            try {
                byte[] alias = this.alias.getBytes("UTF-8");
                byte[] uri = this.uri.getBytes("UTF-8");

                ByteBuffer buffer = ByteBuffer.allocate(1 + alias.length + 2 + uri.length);
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                buffer.put((byte)alias.length);
                buffer.put(alias);
                buffer.putShort((short)uri.length);
                buffer.put(uri);
                return buffer.array();
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public JSONObject getJSONObject() {
            JSONObject attachment = new JSONObject();
            attachment.put("alias", alias);
            attachment.put("uri", uri);
            return attachment;
        }
    }
}