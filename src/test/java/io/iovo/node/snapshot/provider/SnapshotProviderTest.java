package io.iovo.node.snapshot.provider;

import io.iovo.node.snapshot.model.Account;
import io.iovo.node.snapshot.model.Snapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SnapshotProviderTest {

    private SnapshotProvider snapshotProvider;

    @BeforeEach
    void setup() {
        this.snapshotProvider = new SnapshotProvider();
    }

    @Test
    void shouldReturnSnapshotWhenCorrectDataProvided() {
        // given
        String file = "src/test/resources/snapshot-test.dat";
        Snapshot expected = new Snapshot(createExpectedData());

        // when
        Snapshot snapshotFromFile = snapshotProvider.getSnapshotFromFile(file);

        // then
        assertEquals(expected, snapshotFromFile);
    }

    private List<Account> createExpectedData() {
        List<Account> list = new ArrayList<>();
        list.add(new Account("f8f29d7cd79a564c6dcb07e07aed49bd8a0a5a6da9c9c08d79134b05e9bd108d", 21000000));
        list.add(new Account("eb6703ecffc0225e1d6533d28a615f28c62a6600e917b616781461330c0a730c", 321321));
        list.add(new Account("f47759941904a9bf6f89736c4541d850107c9be6ec619e7e65cf80a14ff7e8e4", 2311));
        return list;
    }


}