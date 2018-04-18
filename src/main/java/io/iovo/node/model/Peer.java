package io.iovo.node.model;

import io.iovo.node.Iovo;
import io.iovo.node.crypto.Crypto;
import lombok.Getter;
import org.apache.commons.io.input.CountingInputStream;
import org.apache.commons.io.output.CountingOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static io.iovo.node.Iovo.*;
import static io.iovo.node.utils.ConvertUtils.convert;

public class Peer implements Comparable<Peer> {
    private static final Logger logger = LogManager.getLogger();
    public static final int STATE_NONCONNECTED = 0;
    public static final int STATE_CONNECTED = 1;
    public static final int STATE_DISCONNECTED = 2;

    private final int index;
    public String platform;
    @Getter
    public String announcedAddress;
    public boolean shareAddress;
    private String hallmark;
    public long accountId;
    private int weight, date;
    public long adjustedWeight;
    public String application;
    public String version;

    private long blacklistingTime;
    public int state;
    private long downloadedVolume, uploadedVolume;

    private Peer(String announcedAddress, int index) {
        this.announcedAddress = announcedAddress;
        this.index = index;
    }

    public static Peer addPeer(String address, String announcedAddress) {
        try {
            new URL("http://" + address);
        } catch (Exception e) {
            return null;
        }
        try {
            new URL("http://" + announcedAddress);
        } catch (Exception e) {
            announcedAddress = "";
        }

        if (address.equals("localhost") || address.equals("127.0.0.1") || address.equals("0:0:0:0:0:0:0:1")) {
            return null;
        }

        if (myAddress != null && myAddress.length() > 0 && myAddress.equals(announcedAddress)) {
            return null;
        }

        Peer peer = peers.get(announcedAddress.length() > 0 ? announcedAddress : address);
        if (peer == null) {
            //TODO: Check addresses
            peer = new Peer(announcedAddress, peerCounter.incrementAndGet());
            peers.put(announcedAddress.length() > 0 ? announcedAddress : address, peer);
        }
        return peer;
    }

    public boolean analyzeHallmark(String realHost, String hallmark) {
        if (hallmark == null) {
            return true;
        }

        try {
            byte[] hallmarkBytes = convert(hallmark);

            ByteBuffer buffer = ByteBuffer.wrap(hallmarkBytes);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            byte[] publicKey = new byte[32];
            buffer.get(publicKey);
            int hostLength = buffer.getShort();
            byte[] hostBytes = new byte[hostLength];
            buffer.get(hostBytes);
            String host = new String(hostBytes, "UTF-8");
            if (host.length() > 100 || !host.equals(realHost)) {
                return false;
            }
            int weight = buffer.getInt();
            if (weight <= 0 || weight > MAX_BALANCE) {
                return false;
            }
            int date = buffer.getInt();
            buffer.get();
            byte[] signature = new byte[64];
            buffer.get(signature);

            byte[] data = new byte[hallmarkBytes.length - 64];
            System.arraycopy(hallmarkBytes, 0, data, 0, data.length);

            if (Crypto.verify(signature, data, publicKey)) {
                this.hallmark = hallmark;

                long accountId = Account.getId(publicKey);
                Account account = accounts.get(accountId);
                if (account == null) {
                    return false;
                }
                LinkedList<Peer> groupedPeers = new LinkedList<>();
                int validDate = 0;

                this.accountId = accountId;
                this.weight = weight;
                this.date = date;

                for (Peer peer : peers.values()) {
                    if (peer.accountId == accountId) {
                        groupedPeers.add(peer);
                        if (peer.date > validDate) {
                            validDate = peer.date;
                        }
                    }
                }

                long totalWeight = 0;
                for (Peer peer : groupedPeers) {
                    if (peer.date == validDate) {
                        totalWeight += peer.weight;
                    } else {
                        peer.adjustedWeight = 0;
                        peer.updateWeight();
                    }
                }

                for (Peer peer : groupedPeers) {
                    peer.adjustedWeight = MAX_BALANCE * peer.weight / totalWeight;
                    peer.updateWeight();
                }
                return true;
            }
        } catch (Exception e) { }
        return false;
    }

    public void blacklist() {
        blacklistingTime = System.currentTimeMillis();

        JSONObject response = new JSONObject();
        response.put("response", "processNewData");

        JSONArray removedKnownPeers = new JSONArray();
        JSONObject removedKnownPeer = new JSONObject();
        removedKnownPeer.put("index", index);
        removedKnownPeers.add(removedKnownPeer);
        response.put("removedKnownPeers", removedKnownPeers);

        JSONArray addedBlacklistedPeers = new JSONArray();
        JSONObject addedBlacklistedPeer = new JSONObject();
        addedBlacklistedPeer.put("index", index);
        addedBlacklistedPeer.put("announcedAddress", announcedAddress.length() > 30 ? (announcedAddress.substring(0, 30) + "...") : announcedAddress);
        for (String wellKnownPeer : wellKnownPeers) {
            if (announcedAddress.equals(wellKnownPeer)) {
                addedBlacklistedPeer.put("wellKnown", true);
                break;
            }
        }
        addedBlacklistedPeers.add(addedBlacklistedPeer);
        response.put("addedBlacklistedPeers", addedBlacklistedPeers);

        for (User user : users.values()) {
            user.send(response);
        }
    }

    @Override
    public int compareTo(Peer o) {
        long weight = getWeight(), weight2 = o.getWeight();
        if (weight > weight2) {
            return -1;
        } else if (weight < weight2) {
            return 1;
        } else {
            return index - o.index;
        }
    }

    public void connect() {
        JSONObject request = new JSONObject();
        request.put("command", "getInfo");
        if (myAddress != null && myAddress.length() > 0) {
            request.put("announcedAddress", myAddress);
        }
        if (myHallmark != null && myHallmark.length() > 0) {
            request.put("hallmark", myHallmark);
        }
        request.put("application", "NRS");
        request.put("version", VERSION);
        request.put("platform", "MacOS");
        request.put("scheme", myScheme);
        request.put("port", myPort);
        request.put("shareAddress", shareMyAddress);
        JSONObject response = send(request);
        if (response != null) {

            application = (String)response.get("application");
            version = (String)response.get("version");
            platform = (String)response.get("platform");
            try {
                shareAddress = Boolean.parseBoolean((String)response.get("shareAddress"));
            } catch (Exception e) {
                /**/shareAddress = true;
            }

            if (analyzeHallmark(announcedAddress, (String)response.get("hallmark"))) {
                setState(STATE_CONNECTED);
            } else {
                blacklist();
            }
        }
    }

    private void deactivate() {
        if (state == STATE_CONNECTED) {
            disconnect();
        }
        setState(STATE_NONCONNECTED);

        JSONObject response = new JSONObject();
        response.put("response", "processNewData");

        JSONArray removedActivePeers = new JSONArray();
        JSONObject removedActivePeer = new JSONObject();
        removedActivePeer.put("index", index);
        removedActivePeers.add(removedActivePeer);
        response.put("removedActivePeers", removedActivePeers);

        if (announcedAddress.length() > 0) {
            JSONArray addedKnownPeers = new JSONArray();
            JSONObject addedKnownPeer = new JSONObject();
            addedKnownPeer.put("index", index);
            addedKnownPeer.put("announcedAddress", announcedAddress.length() > 30 ? (announcedAddress.substring(0, 30) + "...") : announcedAddress);
            for (String wellKnownPeer : wellKnownPeers) {
                if (announcedAddress.equals(wellKnownPeer)) {
                    addedKnownPeer.put("wellKnown", true);
                    break;
                }
            }
            addedKnownPeers.add(addedKnownPeer);
            response.put("addedKnownPeers", addedKnownPeers);
        }
        for (User user : users.values()) {
            user.send(response);
        }
    }

    private void disconnect() {
        setState(STATE_DISCONNECTED);
    }

    public static Peer getAnyPeer(int state, boolean applyPullThreshold) {
        List<Peer> selectedPeers = new ArrayList<Peer>();
        for (Peer peer : Iovo.peers.values()) {
            if (peer.blacklistingTime <= 0 && peer.state == state && peer.announcedAddress.length() > 0
                    && (!applyPullThreshold || !enableHallmarkProtection || peer.getWeight() >= pullThreshold)) {
                selectedPeers.add(peer);
            }
        }

        if (selectedPeers.size() > 0) {
            long totalWeight = 0;
            for (Peer peer : selectedPeers) {
                long weight = peer.getWeight();
                if (weight == 0) {
                    weight = 1;
                }
                totalWeight += weight;
            }

            long hit = ThreadLocalRandom.current().nextLong(totalWeight);
            for (Peer peer : selectedPeers) {
                long weight = peer.getWeight();
                if (weight == 0) {
                    weight = 1;
                }
                if ((hit -= weight) < 0) {
                    return peer;
                }
            }
        }
        return null;
    }

    public static int getNumberOfConnectedPublicPeers() {
        int numberOfConnectedPeers = 0;
        for (Peer peer : peers.values()) {
            if (peer.state == STATE_CONNECTED && peer.announcedAddress.length() > 0) {
                numberOfConnectedPeers++;
            }
        }
        return numberOfConnectedPeers;
    }

    private int getWeight() {
        if (accountId == 0) {
            return 0;
        }
        Account account = accounts.get(accountId);
        if (account == null) {
            return 0;
        }
        return (int)(adjustedWeight * (account.getBalance() / 100) / MAX_BALANCE);
    }

    public void removeBlacklistedStatus() {
        setState(STATE_NONCONNECTED);
        blacklistingTime = 0;

        JSONObject response = new JSONObject();
        response.put("response", "processNewData");

        JSONArray removedBlacklistedPeers = new JSONArray();
        JSONObject removedBlacklistedPeer = new JSONObject();
        removedBlacklistedPeer.put("index", index);
        removedBlacklistedPeers.add(removedBlacklistedPeer);
        response.put("removedBlacklistedPeers", removedBlacklistedPeers);

        JSONArray addedKnownPeers = new JSONArray();
        JSONObject addedKnownPeer = new JSONObject();
        addedKnownPeer.put("index", index);
        addedKnownPeer.put("announcedAddress", announcedAddress.length() > 30 ? (announcedAddress.substring(0, 30) + "...") : announcedAddress);
        for (String wellKnownPeer : wellKnownPeers) {
            if (announcedAddress.equals(wellKnownPeer)) {
                addedKnownPeer.put("wellKnown", true);
                break;
            }
        }
        addedKnownPeers.add(addedKnownPeer);
        response.put("addedKnownPeers", addedKnownPeers);

        for (User user : users.values()) {
            user.send(response);
        }
    }

    private void removePeer() {
        peers.values().remove(this);
        JSONObject response = new JSONObject();
        response.put("response", "processNewData");
        JSONArray removedKnownPeers = new JSONArray();
        JSONObject removedKnownPeer = new JSONObject();
        removedKnownPeer.put("index", index);
        removedKnownPeers.add(removedKnownPeer);
        response.put("removedKnownPeers", removedKnownPeers);
        for (User user : users.values()) {
            user.send(response);
        }
    }

    //TODO: send in parallel using an executor service or NIO
    public static void sendToAllPeers(JSONObject request) {
        for (Peer peer : Iovo.peers.values()) {
            if (enableHallmarkProtection && peer.getWeight() < pushThreshold) {
                continue;
            }
//            if (peer.blacklistingTime == 0 && peer.state == Peer.STATE_CONNECTED && peer.announcedAddress.length() > 0) {
                peer.sendNew(request);
//            }
        }
    }

    public JSONObject send(JSONObject request) {
        JSONObject response;
        String log = null;
        boolean showLog = false;
        HttpURLConnection connection = null;
        try {
            if (communicationLoggingMask != 0) {
                log = "\"" + announcedAddress + "\": " + request.toString();
            }

            request.put("protocol", 1);

            /**/URL url = new URL("http://" + announcedAddress + ((new URL("http://" + announcedAddress)).getPort() < 0 ? ":7874" : "") + "/iovo");
            /**///URL url = new URL("http://" + announcedAddress + ":6874" + "/iovo");
            connection = (HttpURLConnection)url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(connectTimeout);
            connection.setReadTimeout(readTimeout);

            CountingOutputStream cos = new CountingOutputStream(connection.getOutputStream());
            try (Writer writer = new BufferedWriter(new OutputStreamWriter(cos, "UTF-8"))) {
                request.writeJSONString(writer);
            }
            updateUploadedVolume(cos.getCount());

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                if ((communicationLoggingMask & LOGGING_MASK_200_RESPONSES) != 0) {
                    // inefficient
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    byte[] buffer = new byte[65536];
                    int numberOfBytes;
                    try (InputStream inputStream = connection.getInputStream()) {
                        while ((numberOfBytes = inputStream.read(buffer)) > 0) {
                            byteArrayOutputStream.write(buffer, 0, numberOfBytes);
                        }
                    }
                    String responseValue = byteArrayOutputStream.toString("UTF-8");
                    log += " >>> " + responseValue;
                    showLog = true;
                    updateDownloadedVolume(responseValue.getBytes("UTF-8").length);
                    response = (JSONObject)JSONValue.parse(responseValue);
                } else {
                    CountingInputStream cis = new CountingInputStream(connection.getInputStream());

                    try (Reader reader = new BufferedReader(new InputStreamReader(cis, "UTF-8"))) {
                        response = (JSONObject)JSONValue.parse(reader);
                    }
                    updateDownloadedVolume(cis.getCount());
                }
            } else {
                if ((communicationLoggingMask & LOGGING_MASK_NON200_RESPONSES) != 0) {
                    log += " >>> Peer responded with HTTP " + connection.getResponseCode() + " code!";
                    showLog = true;
                }
                disconnect();
                response = null;
            }
        } catch (Exception e) {
            if ((communicationLoggingMask & LOGGING_MASK_EXCEPTIONS) != 0) {
                log += " >>> " + e.toString();
                showLog = true;
            }
            if (state == STATE_NONCONNECTED) {
                blacklist();
            } else {
                disconnect();
            }
            response = null;
        }
        if (showLog) {
            logger.info(log + "\n");
        }
        if (connection != null) {
            connection.disconnect();
        }
        return response;
    }

    public JSONObject sendNew(JSONObject request) {
        JSONObject response;
        String log = null;
        boolean showLog = false;
        HttpURLConnection connection = null;
        try {
            if (communicationLoggingMask != 0) {
                log = "\"" + announcedAddress + "\": " + request.toString();
            }

            URL url = new URL("http://localhost:12345");
            connection = (HttpURLConnection)url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(connectTimeout);
            connection.setReadTimeout(readTimeout);

            CountingOutputStream cos = new CountingOutputStream(connection.getOutputStream());
            try (Writer writer = new BufferedWriter(new OutputStreamWriter(cos, "UTF-8"))) {
                request.writeJSONString(writer);
            }
            updateUploadedVolume(cos.getCount());

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                if ((communicationLoggingMask & LOGGING_MASK_200_RESPONSES) != 0) {
                    // inefficient
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    byte[] buffer = new byte[65536];
                    int numberOfBytes;
                    try (InputStream inputStream = connection.getInputStream()) {
                        while ((numberOfBytes = inputStream.read(buffer)) > 0) {
                            byteArrayOutputStream.write(buffer, 0, numberOfBytes);
                        }
                    }
                    String responseValue = byteArrayOutputStream.toString("UTF-8");
                    log += " >>> " + responseValue;
                    showLog = true;
                    updateDownloadedVolume(responseValue.getBytes("UTF-8").length);
                    response = (JSONObject)JSONValue.parse(responseValue);
                } else {
                    CountingInputStream cis = new CountingInputStream(connection.getInputStream());

                    try (Reader reader = new BufferedReader(new InputStreamReader(cis, "UTF-8"))) {
                        response = (JSONObject)JSONValue.parse(reader);
                    }
                    updateDownloadedVolume(cis.getCount());
                }
            } else {
                if ((communicationLoggingMask & LOGGING_MASK_NON200_RESPONSES) != 0) {
                    log += " >>> Peer responded with HTTP " + connection.getResponseCode() + " code!";
                    showLog = true;
                }
                disconnect();
                response = null;
            }
        } catch (Exception e) {
            if ((communicationLoggingMask & LOGGING_MASK_EXCEPTIONS) != 0) {
                log += " >>> " + e.toString();
                showLog = true;
            }
            if (state == STATE_NONCONNECTED) {
                blacklist();
            } else {
                disconnect();
            }
            response = null;
        }
        if (showLog) {
            logger.info(log + "\n");
        }
        if (connection != null) {
            connection.disconnect();
        }
        return response;
    }

    public void setState(int state) {
        if (this.state == STATE_NONCONNECTED && state != STATE_NONCONNECTED) {
            JSONObject response = new JSONObject();
            response.put("response", "processNewData");
            if (announcedAddress.length() > 0) {
                JSONArray removedKnownPeers = new JSONArray();
                JSONObject removedKnownPeer = new JSONObject();
                removedKnownPeer.put("index", index);
                removedKnownPeers.add(removedKnownPeer);
                response.put("removedKnownPeers", removedKnownPeers);
            }
            JSONArray addedActivePeers = new JSONArray();
            JSONObject addedActivePeer = new JSONObject();
            addedActivePeer.put("index", index);
            if (state == STATE_DISCONNECTED) {
                addedActivePeer.put("disconnected", true);
            }

            //TODO: there must be a better way
            for (Map.Entry<String, Peer> peerEntry : peers.entrySet()) {
                if (peerEntry.getValue() == this) {
                    addedActivePeer.put("address", peerEntry.getKey().length() > 30 ? (peerEntry.getKey().substring(0, 30) + "...") : peerEntry.getKey());
                    break;
                }
            }
            addedActivePeer.put("announcedAddress", announcedAddress.length() > 30 ? (announcedAddress.substring(0, 30) + "...") : announcedAddress);
            addedActivePeer.put("weight", getWeight());
            addedActivePeer.put("downloaded", downloadedVolume);
            addedActivePeer.put("uploaded", uploadedVolume);
            addedActivePeer.put("software", (application == null ? "?" : application) + " (" + (version == null ? "?" : version) + ")" + " @ " + (platform == null ? "?" : platform));
            for (String wellKnownPeer : wellKnownPeers) {
                if (announcedAddress.equals(wellKnownPeer)) {
                    addedActivePeer.put("wellKnown", true);
                    break;
                }
            }
            addedActivePeers.add(addedActivePeer);
            response.put("addedActivePeers", addedActivePeers);

            for (User user : users.values()) {
                user.send(response);
            }
        } else if (this.state != STATE_NONCONNECTED && state != STATE_NONCONNECTED) {
            JSONObject response = new JSONObject();
            response.put("response", "processNewData");

            JSONArray changedActivePeers = new JSONArray();
            JSONObject changedActivePeer = new JSONObject();
            changedActivePeer.put("index", index);
            changedActivePeer.put(state == STATE_CONNECTED ? "connected" : "disconnected", true);
            changedActivePeers.add(changedActivePeer);
            response.put("changedActivePeers", changedActivePeers);

            for (User user : users.values()) {
                user.send(response);
            }
        }
        this.state = state;
    }

    private void updateDownloadedVolume(long volume) {
        downloadedVolume += volume;

        JSONObject response = new JSONObject();
        response.put("response", "processNewData");

        JSONArray changedActivePeers = new JSONArray();
        JSONObject changedActivePeer = new JSONObject();
        changedActivePeer.put("index", index);
        changedActivePeer.put("downloaded", downloadedVolume);
        changedActivePeers.add(changedActivePeer);
        response.put("changedActivePeers", changedActivePeers);

        for (User user : users.values()) {
            user.send(response);
        }
    }

    private void updateUploadedVolume(long volume) {
        uploadedVolume += volume;
        JSONObject response = new JSONObject();
        response.put("response", "processNewData");

        JSONArray changedActivePeers = new JSONArray();
        JSONObject changedActivePeer = new JSONObject();
        changedActivePeer.put("index", index);
        changedActivePeer.put("uploaded", uploadedVolume);
        changedActivePeers.add(changedActivePeer);
        response.put("changedActivePeers", changedActivePeers);

        for (User user : users.values()) {
            user.send(response);
        }
    }

    public void updateWeight() {
        JSONObject response = new JSONObject();
        response.put("response", "processNewData");

        JSONArray changedActivePeers = new JSONArray();
        JSONObject changedActivePeer = new JSONObject();
        changedActivePeer.put("index", index);
        changedActivePeer.put("weight", getWeight());
        changedActivePeers.add(changedActivePeer);
        response.put("changedActivePeers", changedActivePeers);

        for (User user : users.values()) {
            user.send(response);
        }
    }
}