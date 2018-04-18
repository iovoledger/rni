package io.iovo.node.conf;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.fluent.Configurations;

import java.io.File;
import java.io.InputStream;
import java.util.List;

public class Configuration {

    private static Configuration instance = null;

    public static final String NODE_VERSION = "NODE_VERSION";
    public static final String TESTNET = "TESTNET";
    public static final String MAX_NEIGHBOURS = "MAX_NEIGHBOURS";
    public static final String API_HOST = "API_HOST";
    public static final String API_PORT = "API_PORT";
    public static final String UDP_PORT = "UDP_PORT";

    public static final String MAX_BODY_LENGTH = "MAX_BODY_LENGTH";
    public static final String TRACKERS = "TRACKERS";
    public static final String TIME_BETWEEN_NEIGHBOURS_INVITE = "TIME_BETWEEN_NEIGHBOURS_INVITE";
    public static final String CONNECTING_THREADS = "CONNECTING_THREADS";

    public static String getString(String propertyName) {
        if (instance == null) {
            instance = new Configuration();
        }

        return instance.configuration.getString(propertyName);
    }

    public static int getInt(String propertyName) {
        if (instance == null) {
            instance = new Configuration();
        }

        return instance.configuration.getInt(propertyName);
    }

    public static void setInt(String propertyName, int value) {
        if (instance == null) {
            instance = new Configuration();
        }

        instance.configuration.setProperty(propertyName, String.valueOf(value));
    }

    public static long getLong(String propertyName) {
        if (instance == null) {
            instance = new Configuration();
        }

        return instance.configuration.getLong(propertyName);
    }

    public static <T> List<T> getList(Class<T> cls, String propertyName) {
        if (instance == null) {
            instance = new Configuration();
        }

        return instance.configuration.getList(cls, propertyName);
    }

    private Configuration() {
        try {
            Configurations configurations = new Configurations();
            String propFileName = "config.properties";

            configuration = configurations.properties(new File(propFileName));
        } catch (Exception e) {
            System.out.println("Exception: " + e);
        }
    }

    private PropertiesConfiguration configuration = null;
}
