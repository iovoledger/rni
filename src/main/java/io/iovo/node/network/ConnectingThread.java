package io.iovo.node.network;

import io.iovo.node.conf.Configuration;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@RequiredArgsConstructor
public class ConnectingThread extends Thread {
    private static final Logger logger = LogManager.getLogger();

    private final Neighbor neighbor;

    public void run() {
        neighbor.sendConnect();
        try {
            sleep(Configuration.getLong(Configuration.TIME_BETWEEN_NEIGHBOURS_INVITE));
        } catch (InterruptedException e) {
            logger.error("ConnectingThread interrupted while waiting");
        }
    }
}
