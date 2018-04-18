package io.iovo.node.snapshot.model;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Data
@RequiredArgsConstructor
public class Snapshot {
    private final List<Account> balances;
}
