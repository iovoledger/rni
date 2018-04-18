package io.iovo.node.service.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class SendMoneyResponse extends Response {

    private final String transaction;
    private final String bytes;
}
