package io.iovo.node.utils;

public class TimeUtils {

    public static long epochBeginning;

    public static int getEpochTime(long time) {
        return (int)((time - epochBeginning + 500) / 1000);
    }
}
