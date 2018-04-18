package io.iovo.node.service.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
@Getter
public class GetUnconfirmedTransactionIdsResponse extends Response {
    private final List<String> unconfirmedTransactionIds;
}
