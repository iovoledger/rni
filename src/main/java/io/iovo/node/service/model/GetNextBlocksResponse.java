package io.iovo.node.service.model;

import io.iovo.node.model.Block;
import io.iovo.node.utils.ConvertUtils;
import io.iovo.node.model.Transaction;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static io.iovo.node.utils.ConvertUtils.convert;

@RequiredArgsConstructor
@Getter
public class GetNextBlocksResponse extends Response {
    private final List<BlockObject> nextBlocks;

    public static class BlockObject {
        private final int version;
        private final int timestamp;
        private final long previousBlock;
        private final int numberOfTransactions;
        private final int totalAmount;
        private final int totalFee;
        private final int payloadLength;
        private final String payloadHash;
        private final String generatorPublicKey;
        private final String generationSignature;
        private final String blockSignature;
        private final List<TransactionObject> transactions;

        public BlockObject(Block block, HashMap<Long, Transaction> transactions) {
            this.version = block.version;
            this.timestamp = block.timestamp;
            this.previousBlock = block.previousBlock;
            this.numberOfTransactions = block.numberOfTransactions;
            this.totalAmount = block.totalAmount;
            this.totalFee = block.totalFee;
            this.payloadLength = block.payloadLength;
            this.payloadHash = ConvertUtils.convert(block.payloadHash);
            this.generatorPublicKey = ConvertUtils.convert(block.generatorPublicKey);
            this.generationSignature = ConvertUtils.convert(block.generationSignature);
            this.blockSignature = ConvertUtils.convert(block.blockSignature);
            this.transactions = new ArrayList<>();
            for (int i = 0; i < numberOfTransactions; i++) {
                this.transactions.add(new TransactionObject(transactions.get(block.transactions[i])));
            }
        }
    }

    public static class TransactionObject {
        private final byte type;
        private final byte subtype;
        private final int timestamp;
        private final int deadline;
        private final String senderPublicKey;
        private final String recipient;
        private final long amount;
        private final long fee;
        private final String referencedTransaction;
        private final String signature;

        public TransactionObject(Transaction transaction) {
            this.type = transaction.type;
            this.subtype = transaction.subtype;
            this.timestamp = transaction.timestamp;
            this.deadline = transaction.deadline;
            this.senderPublicKey = convert(transaction.senderPublicKey);
            this.recipient = convert(transaction.recipient);
            this.amount = transaction.amount;
            this.fee = transaction.fee;
            this.referencedTransaction = convert(transaction.referencedTransaction);
            this.signature = convert(transaction.signature);
//            if (transaction.attachment != null) {
//                transaction.put("attachment", transaction.attachment.getJSONObject());
//            }
        }
    }
}
