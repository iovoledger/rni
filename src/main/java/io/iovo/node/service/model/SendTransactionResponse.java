package io.iovo.node.service.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class SendTransactionResponse extends Response {

    private final String sender;
    private final String receiver;
    private final long value;
    private final String hash;

}
