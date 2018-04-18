package io.iovo.node.snapshot.model;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class Account {
    private final String address;
    private final long amount;
}
