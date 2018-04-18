package io.iovo.node.tracker;

import io.iovo.node.Iovo;
import io.iovo.node.conf.Configuration;
import io.iovo.node.model.Peer;
import io.iovo.node.tracker.listener.GetNodeListener;
import io.iovo.node.tracker.listener.PostNodeListener;
import io.iovo.node.tracker.model.RegisterNodeRequest;
import io.iovo.node.tracker.service.TrackerService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;

public class TrackerConnector {
    private static final Logger logger = LogManager.getLogger();

    private List<String> trackers;
    private String myIp;
    private final Iovo iovo;

    public TrackerConnector(Iovo iovo) {
        this.iovo = iovo;
        this.trackers = Configuration.getList(String.class, Configuration.TRACKERS);
        try {
           // myIp = NetworkUtils.getMyIp();  // external
            myIp = InetAddress.getLocalHost().getHostAddress().toString();  // internal
            logger.info("My IP: " + myIp);
        } catch (IOException e) {
            logger.error("Problem while getting my ip");
        }
        updateNodes();
    }

    private void updateNodes() {
        trackers.stream()
                .map(tracker -> RetrofitProvider.provide(tracker).create(TrackerService.class))
                .forEach(service ->{
                    registerAsNode(service);
                    getNodes(service);
                });
    }

    private void registerAsNode(TrackerService trackerService) {
        trackerService.addNode(new RegisterNodeRequest(myIp, Configuration.getString(Configuration.NODE_VERSION)))
                .enqueue(new PostNodeListener());
    }

    private void getNodes(TrackerService trackerService) {
        trackerService.getNodes()
                .enqueue(new GetNodeListener(this, myIp));
    }

    public void addNode(String ip) {
        logger.info("Peer added to list: " + ip);
        iovo.peers.put(String.valueOf(iovo.peers.size()), Peer.addPeer(ip, ip));
    }
}
