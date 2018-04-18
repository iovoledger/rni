package io.iovo.node.service.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class TransactionToProcess {
    private final String senderPublicKey;
    private final String amount;
    private final String referencedTransaction;
    private final String subtype;
    private final String signature;
    private final String fee;
    private final String recipient;
    private final String type;
    private final String deadline;
    private final String timestamp;
}
