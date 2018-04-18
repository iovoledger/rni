package io.iovo.node.network;

import io.iovo.node.network.state.NetworkState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Node {
    private static final Logger logger = LogManager.getLogger();

//    private final List<Neighbor> neighbors = new CopyOnWriteArrayList<>();
//    @Getter
//    private final List<Neighbor> connectedNeighbors = new ArrayList<>();
//    private final HashMap<Neighbor, Integer> heights = new HashMap<>();
//    private NetworkState networkState;
//    @Setter
//    private DatagramSocket udpSocket;
//    private ConnectingThread[] connectingThread = new ConnectingThread[Configuration.CONNECTING_THREADS];
//    private int currentConnectingThread = 0;

    public Node(NetworkState networkState) {
        //this.networkState = networkState;
    }
/*
    public void connectToNode(URI uri) {
        if (uri.getScheme().equals("udp")) {
            UDPNeighbor udpNeighbor = new UDPNeighbor(new InetSocketAddress(uri.getHost(), uri.getPort()), udpSocket);
            neighbors.add(udpNeighbor);
            if (connectedNeighbors.size() < Configuration.MAX_NEIGHBOURS) {
                runConnectThread(udpNeighbor, currentConnectingThread);
                currentConnectingThread = (currentConnectingThread + 1) % Configuration.CONNECTING_THREADS;
            }
        }
    }

    private void runConnectThread(UDPNeighbor udpNeighbor, int index) {
        if (connectingThread[index] != null && connectingThread[index].isAlive()) {
            try {
                connectingThread[index].join();
            } catch (InterruptedException e) {
                logger.error("ConnectingThread was stopped!");
            }
        }
        if (connectedNeighbors.size() < Configuration.MAX_NEIGHBOURS) {
            connectingThread[index] = new ConnectingThread(udpNeighbor);
            connectingThread[index].start();
        }
    }

    public void preProcessReceivedData(byte[] receivedData, SocketAddress senderAddress) {
        boolean addressMatch;
        UDPPacket udpPacket = new UDPPacket(receivedData);
        for (Neighbor neighbor : neighbors) {
            addressMatch = neighbor.matches(senderAddress);
            if (addressMatch) {
                if (udpPacket.getCommand() == Command.SYNC_TRANSACTION) {
                    onSyncTransaction(udpPacket);
                    return;
                }
                if (udpPacket.getCommand() == Command.NEW_TRANSACTION) {
                    onNewTransaction(udpPacket);
                    return;
                }
                if (udpPacket.getCommand() == Command.CONNECT) {
                    onConnectReceived(udpPacket, neighbor, senderAddress);
                    return;
                }
                if (udpPacket.getCommand() == Command.CONNECTED) {
                    onConnectedReceived(udpPacket, neighbor, senderAddress);
                    return;
                }
                if (udpPacket.getCommand() == Command.CONNECTED_CONFIRMATION) {
                    onConnectedConfirmationReceived(udpPacket, neighbor, senderAddress);
                    return;
                }
            }
        }
        if (udpPacket.getCommand() == Command.CONNECT) {
            try {
                URI uri = new URI("udp:/" + senderAddress.toString());
                Neighbor neighbor = new UDPNeighbor(new InetSocketAddress(uri.getHost(), uri.getPort()), udpSocket);
                neighbors.add(neighbor);
                onConnectReceived(udpPacket, neighbor, senderAddress);
            } catch (URISyntaxException e) {
                logger.error("Problem with address IP " + senderAddress);
            }
        }
    }

    private void onConnectReceived(UDPPacket udpPacket, Neighbor neighbor, SocketAddress senderAddress) {
        logger.info("New request from : " + senderAddress);
        if (connectedNeighbors.size() < Configuration.MAX_NEIGHBOURS) {
            neighbor.sendConnected();
        }
    }

    private void onConnectedReceived(UDPPacket udpPacket, Neighbor neighbor, SocketAddress senderAddress) {
        if (connectedNeighbors.size() < Configuration.MAX_NEIGHBOURS) {
            TransactionOld transaction = new TransactionOld(udpPacket.getMessage());
            heights.put(neighbor, transaction.getHeight());
            checkSync(neighbor, transaction.getHeight());
            neighbor.sendConnectedConfirmation();
        }
    }

    private void onConnectedConfirmationReceived(UDPPacket udpPacket, Neighbor neighbor, SocketAddress senderAddress) {
        TransactionOld transaction = new TransactionOld(udpPacket.getMessage());
        heights.put(neighbor, transaction.getHeight());
        checkSync(neighbor, transaction.getHeight());
        connectedNeighbors.add(neighbor);
    }

    private void onNewTransaction(UDPPacket udpPacket) {
        TransactionOld transaction = new TransactionOld(udpPacket.getMessage());
        // TODO: IOVO-20 CHECK IF TRANSACTION EXISTS IN BASE
        // TODO: IOVO-20 SAVE TRANSACTION TO BASE
        // TODO: IOVO-20 UPDATE HEIGHT OF NEIGHBOUR
        // TODO: IOVO-20 SEND TO REST OF NEIGHBOURS WITH HEIGHT - 1
    }

    private void onSyncTransaction(UDPPacket udpPacket) {
        TransactionOld transaction = new TransactionOld(udpPacket.getMessage());
        // TODO: IOVO-20 CHECK IF TRANSACTION EXISTS IN BASE
        // TODO: IOVO-20 SAVE TRANSACTION TO BASE
        // TODO: IOVO-19 SEND CONFIRMATION
    }

    private void checkSync(Neighbor neighbor, int height) {
        int myHeight = 0;
        if (height < myHeight) {
            //TODO: IOVO-20 sendTransaction(height+1);
        }
    }

    public void sendTransactionToAll(TransactionOld transaction, boolean sync) {
        for (Neighbor neighbor : neighbors) {
            UDPPacket udpPacket = new UDPPacket(sync ? Command.SYNC_TRANSACTION : Command.NEW_TRANSACTION, transaction.serialize());
            byte[] buffer = udpPacket.serialize();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, neighbor.getAddress().getAddress(), neighbor.getPort());
            neighbor.send(packet);
        }
    }*/
}
