package io.iovo.node.service.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class GetStateResponse extends Response {

    private final String version;
    private final long time;
    private final String lastBlock;
    private final int numberOfBlocks;
    private final int numberOfTransactions;
    private final int numberOfAccounts;
}
