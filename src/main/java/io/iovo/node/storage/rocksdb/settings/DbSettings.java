package io.iovo.node.storage.rocksdb.settings;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.rocksdb.DBOptions;
import org.rocksdb.Options;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DbSettings {

    public static final Options BASIC_SETTINGS = createDbSettings();
    public static final DBOptions COLUMNS_OPTIONS = createColumnsSettings();

    private static Options createDbSettings() {
        return new Options()
                .setCreateIfMissing(true);
    }

    private static DBOptions createColumnsSettings() {
        return new DBOptions()
                .setCreateIfMissing(true)
                .setCreateMissingColumnFamilies(true);
    }
}
