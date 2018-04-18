package io.iovo.node.storage.rocksdb.util;

import java.nio.charset.StandardCharsets;

public class StringConverter {

    public byte[] stringToByteArray(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    public String byteArrayToString(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
