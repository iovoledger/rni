package io.iovo.node.storage.rocksdb;

class RocksDBProviderTest {

//    private RocksDBProvider rocksDBProvider;
//
//    @BeforeEach
//    void setup() {
//        this.rocksDBProvider = new RocksDBProvider();
//    }
//
//    @AfterEach
//    void after() {
//        this.rocksDBProvider.closeDatabase();
//    }
//
//    @Test
//    void shouldReturnExampleWhenDataProvided() {
//        // given
//        String key = "key";
//        String value = "example";
//
//        // when
//        String result = "";
//        try {
//            rocksDBProvider.write(key, value);
//            result = rocksDBProvider.read(key);
//        } catch (RocksDBException | NoDataException e) {
//            e.printStackTrace();
//        }
//
//        // then
//        assertEquals(value, result);
//    }
//
//    @Test
//    void shouldThrowExceptionWhenDataNoExists() {
//        // given
//        String key = "key1";
//
//        // when
//        Executable functionToTest = () -> rocksDBProvider.read(key);
//
//        // then
//        assertThrows(NoDataException.class, functionToTest);
//    }
//
//    @Test
//    void shouldThrowExceptionWhenDataRemoved() {
//        // given
//        String key = "key2";
//        String value = "value2";
//
//        // when
//        try {
//            rocksDBProvider.write(key, value);
//            rocksDBProvider.remove(key);
//        } catch (RocksDBException e) {
//            e.printStackTrace();
//        }
//        Executable functionToTest = () -> rocksDBProvider.read(key);
//
//        // then
//        assertThrows(NoDataException.class, functionToTest);
//    }
//
//    @Test
//    void shouldReturnTransactionWhenAdded() {
//        // given
//        Hash receiverHash = new Hash("81bae876b70513c9decc608eed549977a81afa1c2b6b4080aec256339e792e0f");
//        Hash senderHash = new Hash("0a367b92cf0b037dfd89960ee832d56f7fc151681bb41e53690e776f5786998a");
//        Hash transactionHash = new Hash("60a5d3e4100fe8afa5ee0103739a45711d50d7f3ba7280d8a95b51f5d04aa4b8");
//        TransactionOld expectedTransaction = TransactionOld.builder()
//                .sender(new Address(senderHash))
//                .receiver(new Address(receiverHash))
//                .hash(transactionHash)
//                .parentTransaction(new Hash(""))
//                .height(0)
//                .value(21000000)
//                .build();
//        Optional<TransactionOld> actualTransaction = Optional.empty();
//
//        // when
//        try {
//            rocksDBProvider.addTransaction(expectedTransaction);
//            actualTransaction = rocksDBProvider.getTransaction(transactionHash);
//        } catch (RocksDBException | NoDataException e) {
//            e.printStackTrace();
//        }
//
//        // then
//        if (actualTransaction.isPresent()) {
//            isTransactionsEqual(expectedTransaction, actualTransaction.get());
//        } else {
//            fail("actualTransaction is null");
//        }
//    }
//
//    @Test
//    void shouldThrowExceptionIfDataRemoved() {
//        // given
//        Hash receiverHash = new Hash("81bae876b70513c9decc608eed549977a81afa1c2b6b4080aec256339e792e0f");
//        Hash senderHash = new Hash("0a367b92cf0b037dfd89960ee832d56f7fc151681bb41e53690e776f5786998a");
//        Hash transactionHash = new Hash("60a5d3e4100fe8afa5ee0103739a45711d50d7f3ba7280d8a95b51f5d04aa4b8");
//        TransactionOld expectedTransaction = TransactionOld.builder()
//                .sender(new Address(senderHash))
//                .receiver(new Address(receiverHash))
//                .hash(transactionHash)
//                .parentTransaction(new Hash(""))
//                .height(0)
//                .value(21000000)
//                .build();
//
//        // when
//        try {
//            rocksDBProvider.addTransaction(expectedTransaction);
//            rocksDBProvider.removeTransaction(transactionHash);
//        } catch (RocksDBException e) {
//            e.printStackTrace();
//        }
//        Executable functionToTest = () -> rocksDBProvider.getTransaction(transactionHash);
//
//        // then
//        assertThrows(NoDataException.class, functionToTest);
//    }
//
//    // Throws Assertion failed: (is_last_reference), function ~ColumnFamilyData, file db/column_family.cc, line 457.
//    // But it is fine
//    @Test
//    void shouldReturnNewestTransactionIfAdded() {
//        // given
//        Hash receiverHash = new Hash("81bae876b70513c9decc608eed549977a81afa1c2b6b4080aec256339e792e0f");
//        Hash senderHash = new Hash("0a367b92cf0b037dfd89960ee832d56f7fc151681bb41e53690e776f5786998a");
//        Hash transactionHash = new Hash("60a5d3e4100fe8afa5ee0103739a45711d50d7f3ba7280d8a95b51f5d04aa4b8");
//        TransactionOld expectedTransaction = TransactionOld.builder()
//                .sender(new Address(senderHash))
//                .receiver(new Address(receiverHash))
//                .hash(transactionHash)
//                .parentTransaction(new Hash(""))
//                .height(0)
//                .value(21000000)
//                .build();
//        Optional<TransactionOld> actualTransaction = Optional.empty();
//
//        // when
//        try {
//            rocksDBProvider.addTransaction(expectedTransaction);
//            actualTransaction = rocksDBProvider.getNewestTransaction();
//        } catch (RocksDBException | NoDataException e) {
//            e.printStackTrace();
//        }
//
//        // then
//        if (actualTransaction.isPresent()) {
//            isTransactionsEqual(expectedTransaction, actualTransaction.get());
//        } else {
//            fail("actualTransaction is null");
//        }
//    }
//
//    private void isTransactionsEqual(TransactionOld expectedTransaction, TransactionOld actualTransaction) {
//        assertEquals(expectedTransaction.getSender().getHash().toHexString(), actualTransaction.getSender().getHash().toHexString());
//        assertEquals(expectedTransaction.getReceiver().getHash().toHexString(), actualTransaction.getReceiver().getHash().toHexString());
//        assertEquals(expectedTransaction.getHash().toHexString(), actualTransaction.getHash().toHexString());
//        assertEquals(expectedTransaction.getHeight(), actualTransaction.getHeight());
//        assertEquals(expectedTransaction.getParentTransaction().toHexString(), actualTransaction.getParentTransaction().toHexString());
//        assertEquals(expectedTransaction.getValue(), actualTransaction.getValue());
//    }

}