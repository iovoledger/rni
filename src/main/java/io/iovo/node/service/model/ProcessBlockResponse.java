package io.iovo.node.service.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class ProcessBlockResponse extends Response {
    private final boolean accepted;
}
