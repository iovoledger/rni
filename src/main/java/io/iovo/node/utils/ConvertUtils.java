package io.iovo.node.utils;

import java.math.BigInteger;

public class ConvertUtils {
    public static final String alphabet = "0123456789abcdefghijklmnopqrstuvwxyz";
    public static final BigInteger two64 = new BigInteger("18446744073709551616");

    public static byte[] convert(String string) {
        byte[] bytes = new byte[string.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte)Integer.parseInt(string.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }

    public static String convert(byte[] bytes) {
        StringBuilder string = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            int number;
            string.append(alphabet.charAt((number = bytes[i] & 0xFF) >> 4)).append(alphabet.charAt(number & 0xF));
        }
        return string.toString();
    }

    public static String convert(long objectId) {
        BigInteger id = BigInteger.valueOf(objectId);
        if (objectId < 0) {
            id = id.add(two64);
        }
        return id.toString();
    }
}
