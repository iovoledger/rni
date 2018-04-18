package io.iovo.node.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URL;

public class NetworkUtils {

    public static String getMyIp() throws IOException {
        return InetAddress.getLocalHost().getHostAddress();
//
//        URL url = new URL("http://checkip.amazonaws.com/");
//        BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
//        return br.readLine();
    }
}
