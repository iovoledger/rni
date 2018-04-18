package io.iovo.node.snapshot.provider;

import io.iovo.node.snapshot.model.Account;
import io.iovo.node.snapshot.model.Snapshot;
import io.iovo.node.snapshot.util.SnapshotUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SnapshotProvider {
    private final Logger logger = LogManager.getLogger();
    private final SnapshotUtils snapshotUtils = SnapshotUtils.getInstance();

    public Snapshot getSnapshotFromFile(String filename) {
        try (Stream<String> fileStream = Files.lines(Paths.get(filename))) {
            return new Snapshot(readFile(fileStream));
        } catch (IOException e) {
            logger.error("Error while importing snapshot file {}", filename);
            logger.debug("Details: ", e);
        }
        return new Snapshot(new ArrayList<>());
    }

    private List<Account> readFile(Stream<String> fileStream) {
        return fileStream
                .map(snapshotUtils::convertLineToAccount)
                .collect(Collectors.toList());
    }
}
