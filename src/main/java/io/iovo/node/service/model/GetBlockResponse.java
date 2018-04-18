package io.iovo.node.service.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class GetBlockResponse extends Response {
    private int height;
    private String generator;
    private int timestamp;
    private int numberOfTransactions;
    private int totalAmount;
    private int totalFee;
    private int payloadHeight;
    private int version;
    private String baseTarget;
    private String previousBlock;
    private String nextBlock;
    private String payloadHash;
    private String generationSignature;
    private String previousBlockHash;
    private String blockSignature;
    private List<String> transactions;
}
