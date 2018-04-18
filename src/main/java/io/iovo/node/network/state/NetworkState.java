package io.iovo.node.network.state;

import io.iovo.node.snapshot.model.Account;
import io.iovo.node.snapshot.provider.SnapshotProvider;
import io.iovo.node.storage.rocksdb.RocksDBProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class NetworkState {
    private final Logger logger = LogManager.getLogger();
    private SnapshotProvider snapshotProvider;
    private RocksDBProvider rocksDBProvider;
    private List<Account> balances;

    public NetworkState(SnapshotProvider snapshotProvider, RocksDBProvider rocksDBProvider) {
      //  this.snapshotProvider = snapshotProvider;
      //  this.rocksDBProvider = rocksDBProvider;
      //  loadLocalSnapshots();
    }

    private void loadLocalSnapshots() {
        balances = snapshotProvider.getSnapshotFromFile("snapshots/snapshot-0.dat").getBalances();
        balances.forEach(this::updateDatabase);
    }

    private void updateDatabase(Account account) {
//        try {
//            rocksDBProvider.addTransaction(createTransaction(account));
//        } catch (RocksDBException e) {
//            logger.error("Error while adding transaction to database");
//            logger.debug("Details: ", e);
//        }
    }

}
