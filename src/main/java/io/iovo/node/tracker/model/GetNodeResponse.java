package io.iovo.node.tracker.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Getter
@RequiredArgsConstructor
public class GetNodeResponse {
    private final String ip;
    private final String version;


}
