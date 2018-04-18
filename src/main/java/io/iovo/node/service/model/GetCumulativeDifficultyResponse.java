package io.iovo.node.service.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class GetCumulativeDifficultyResponse extends Response {
    private final String cumulativeDifficulty;
}
