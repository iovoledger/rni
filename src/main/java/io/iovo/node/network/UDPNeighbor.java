package io.iovo.node.network;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class UDPNeighbor extends Neighbor {
    private static final Logger logger = LogManager.getLogger();

    private DatagramSocket socket;

    public UDPNeighbor(final InetSocketAddress address, final DatagramSocket socket) {
        super(address);
        this.socket = socket;
    }

    @Override
    public void send(DatagramPacket packet) {
        try {
            packet.setSocketAddress(getAddress());
            socket.send(packet);
            // incSentTransactions();
        } catch (final Exception e) {
            System.out.println("UDP send error: {}" + e.getMessage());
        }
    }

    @Override
    public int getPort() {
        return getAddress().getPort();
    }

    @Override
    public String connectionType() {
        return "udp";
    }

    @Override
    public boolean matches(SocketAddress address) {
        if (getAddress().toString().contains(address.toString())) {
            if (address.toString().contains(Integer.toString(getPort()))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void sendConnect() {

    }

    @Override
    public void sendConnected() {

    }

    @Override
    public void sendConnectedConfirmation() {

    }
}
