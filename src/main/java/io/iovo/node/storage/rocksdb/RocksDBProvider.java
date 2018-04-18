package io.iovo.node.storage.rocksdb;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RocksDBProvider {
    private final Logger logger = LogManager.getLogger();
//    private static final String DATABASE_PATH = "mainnetdb";
//    private final StringConverter stringConverter;
//    private final List<ColumnFamilyHandle> columnsHandlers;
//    private RocksDB database;

    public RocksDBProvider() {
//        this.stringConverter = new StringConverter();
//        this.columnsHandlers = new ArrayList<>();
//        initDatabase();
    }
/*
    private void initDatabase() {
        RocksDB.loadLibrary();
        try (ColumnFamilyOptions columnsOptions = new ColumnFamilyOptions().optimizeUniversalStyleCompaction()) {
            List<ColumnFamilyDescriptor> columns = createColumns(columnsOptions);
            database = RocksDB.open(DbSettings.COLUMNS_OPTIONS, DATABASE_PATH, columns, columnsHandlers);
            logger.debug("ok");
        } catch (RocksDBException e) {
            logger.error("Error while adding transaction to database");
            logger.debug("Details: ", e);
        }
    }

    public void closeDatabase() {
        closeHandlers();
        database.close();
    }

    private void write(ColumnFamilyHandle handler, String key, String value) throws RocksDBException {
        database.put(handler, stringConverter.stringToByteArray(key), stringConverter.stringToByteArray(value));
    }

    public void write(String key, String value) throws RocksDBException {
        database.put(columnsHandlers.get(0), stringConverter.stringToByteArray(key), stringConverter.stringToByteArray(value));
    }

    private String read(ColumnFamilyHandle handler, String key) throws RocksDBException, NoDataException {
        byte[] result = database.get(handler, stringConverter.stringToByteArray(key));
        if (result == null) {
            throw new NoDataException();
        }
        return stringConverter.byteArrayToString(result);
    }

    public String read(String key) throws RocksDBException, NoDataException {
        byte[] result = database.get(columnsHandlers.get(0), stringConverter.stringToByteArray(key));
        if (result == null) {
            throw new NoDataException();
        }
        return stringConverter.byteArrayToString(result);
    }

    private void remove(ColumnFamilyHandle handler, String key) throws RocksDBException {
        database.singleDelete(handler, stringConverter.stringToByteArray(key));
    }

    public void remove(String key) throws RocksDBException {
        database.singleDelete(columnsHandlers.get(0), stringConverter.stringToByteArray(key));
    }

    public void addTransaction(TransactionOld transaction) throws RocksDBException {
        write(columnsHandlers.get(1), transaction.getHash().toHexString(), transaction.getSender().getHash().toHexString());
        write(columnsHandlers.get(2), transaction.getHash().toHexString(), transaction.getReceiver().getHash().toHexString());
        write(columnsHandlers.get(3), transaction.getHash().toHexString(), transaction.getParentTransaction().toHexString());
        write(columnsHandlers.get(4), transaction.getHash().toHexString(), String.valueOf(transaction.getValue()));
        write(columnsHandlers.get(5), transaction.getHash().toHexString(), String.valueOf(transaction.getHeight()));
    }

    public Optional<TransactionOld> getTransaction(Hash hash) throws RocksDBException, NoDataException {
        return Optional.of(TransactionOld.builder()
                .hash(hash)
                .sender(Address.fromHexString(read(columnsHandlers.get(1), hash.toHexString())))
                .receiver(Address.fromHexString(read(columnsHandlers.get(2), hash.toHexString())))
                .parentTransaction(new Hash(read(columnsHandlers.get(3), hash.toHexString())))
                .value(Long.parseLong(read(columnsHandlers.get(4), hash.toHexString())))
                .height(Integer.parseInt(read(columnsHandlers.get(5), hash.toHexString())))
                .build());
    }

    public Optional<TransactionOld> getNewestTransaction() throws RocksDBException, NoDataException {
        RocksIterator iterator = database.newIterator(columnsHandlers.get(1));
        iterator.seekToLast();
        if (iterator.isValid()) {
            return getTransaction(new Hash(stringConverter.byteArrayToString(iterator.key())));
        }
        return Optional.empty();
    }

    public void removeTransaction(Hash hash) throws RocksDBException {
        for (ColumnFamilyHandle handler : columnsHandlers.subList(1, columnsHandlers.size())) {
            remove(handler, hash.toHexString());
        }
    }

    private void closeHandlers() {
        columnsHandlers
                .forEach(AbstractImmutableNativeReference::close);
    }

    private List<ColumnFamilyDescriptor> createColumns(ColumnFamilyOptions columnsOptions) {
        return new ColumnsGenerator(stringConverter, columnsOptions)
                .column("sender")
                .column("receiver")
                .column("parentTransaction")
                .column("value")
                .column("height")
                .generate();
    }*/
}
