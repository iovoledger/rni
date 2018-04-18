package io.iovo.node.service.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class GetInfoResponse extends Response {

    private final String hallmark;
    private final String application;
    private final String version;
    private final String platform;
    private final boolean shareAddress;
}
