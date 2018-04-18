package io.iovo.node.snapshot.util;

import io.iovo.node.snapshot.model.Account;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SnapshotUtils {
    private final static String SEPARATOR = ":";
    private static SnapshotUtils ourInstance = new SnapshotUtils();

    public static SnapshotUtils getInstance() {
        return ourInstance;
    }

    public Account convertLineToAccount(String line) {
        return new Account(line.split(SEPARATOR)[0], Long.parseLong(line.split(SEPARATOR)[1]));
    }
}
