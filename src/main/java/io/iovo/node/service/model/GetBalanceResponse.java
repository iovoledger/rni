package io.iovo.node.service.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class GetBalanceResponse extends Response {

    private final long balance;
    private final long unconfirmedBalance;
    private final long effectiveBalance;
    private final String address;
}
