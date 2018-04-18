package io.iovo.node.storage.rocksdb.util;

import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.RocksDB;

import java.util.ArrayList;
import java.util.List;

public class ColumnsGenerator {

    private final StringConverter stringConverter;
    private final ColumnFamilyOptions options;
    private final List<ColumnFamilyDescriptor> columns;

    public ColumnsGenerator(StringConverter stringConverter, ColumnFamilyOptions options) {
        this.stringConverter = stringConverter;
        this.options = options;
        this.columns = new ArrayList<>();
        this.columns.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, options));
    }

    public ColumnsGenerator column(String name) {
        columns.add(new ColumnFamilyDescriptor(stringConverter.stringToByteArray(name), options));
        return this;
    }

    public List<ColumnFamilyDescriptor> generate() {
        return columns;
    }
}
