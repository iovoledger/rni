package io.iovo.node.network;

import lombok.Getter;

import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

public abstract class Neighbor {

    @Getter
    private final InetSocketAddress address;
    @Getter
    private final String hostAddress;

    public Neighbor(final InetSocketAddress address) {
        this.address = address;
        this.hostAddress = address.getAddress().getHostAddress();
    }

    public abstract void send(final DatagramPacket packet);

    public abstract int getPort();

    public abstract String connectionType();

    public abstract boolean matches(SocketAddress address);

    public abstract void sendConnect();

    public abstract void sendConnected();

    public abstract void sendConnectedConfirmation();
}
