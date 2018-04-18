package io.iovo.node.network;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.SerializationUtils;

import java.io.Serializable;

@RequiredArgsConstructor
@Getter
public class UDPPacket implements Serializable {

    private final Command command;
    private final byte[] message;

    public UDPPacket(byte[] receivedData) {
        UDPPacket udpPacket = SerializationUtils.deserialize(receivedData);
        this.command = udpPacket.command;
        this.message = udpPacket.message;
    }

    public byte[] serialize() {
        return SerializationUtils.serialize(this);
    }
}
