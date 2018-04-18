package io.iovo.node.tracker.model;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RegisterNodeRequest {
    private final String ip;
    private final String version;
}
