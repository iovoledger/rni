package io.iovo.node.network;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class UDPReceiver {
    private static final Logger logger = LogManager.getLogger();

    private int port;
    private DatagramSocket socket;
    private Thread receivingThread;
    private Node node;
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    private final int PROCESSOR_THREADS = Math.max(1, Runtime.getRuntime().availableProcessors() * 4);
    private final ExecutorService processor = new ThreadPoolExecutor(PROCESSOR_THREADS, PROCESSOR_THREADS, 5000L,
            TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(PROCESSOR_THREADS, true),
            new ThreadPoolExecutor.AbortPolicy());

    private final DatagramPacket receivingPacket = new DatagramPacket(new byte[TRANSACTION_PACKET_SIZE], TRANSACTION_PACKET_SIZE);
    private static final int TRANSACTION_PACKET_SIZE = 1650;

    public UDPReceiver(int port, Node node) {
        this.port = port;
        this.node = node;
    }

    public void init() throws Exception {
//        socket = new DatagramSocket(port);
//        node.setUdpSocket(socket);
//        logger.info("UDP replicator is accepting connections on udp port " + port);
//        receivingThread = new Thread(spawnReceiverThread(), "UDP receiving thread");
//        receivingThread.start();
    }

    private Runnable spawnReceiverThread() {
        return () -> {
            logger.info("Spawning Receiver Thread");
            int processed = 0, dropped = 0;

            while (!shuttingDown.get()) {
                if (((processed + dropped) % 50000 == 0)) {
                    logger.info("Receiver thread processed/dropped ratio: " + processed + "/" + dropped);
                    processed = 0;
                    dropped = 0;
                }

                try {
                    socket.receive(receivingPacket);
                    //if (receivingPacket.getLength() == TRANSACTION_PACKET_SIZE) {
                        byte[] bytes = Arrays.copyOf(receivingPacket.getData(), receivingPacket.getLength());
                        SocketAddress address = receivingPacket.getSocketAddress();
                      //  processor.submit(() -> node.preProcessReceivedData(bytes, address));
                        processed++;
                        Thread.yield();
                   // } else {
                   //     receivingPacket.setLength(TRANSACTION_PACKET_SIZE);
                   // }
                } catch (final RejectedExecutionException e) {
                    //no free thread, packet dropped
                    dropped++;

                } catch (final Exception e) {
                    logger.error("Receiver Thread Exception:" + e);
                }
            }
            logger.info("Shutting down spawning Receiver Thread");
        };
    }

    public void send(final DatagramPacket packet) {
        try {
            if (socket != null) {
                socket.send(packet);
            }
        } catch (IOException e) {
            // TODO: handle exceptions
        }
    }

    public void shutdown() throws InterruptedException {
        shuttingDown.set(true);
        processor.shutdown();
        processor.awaitTermination(6, TimeUnit.SECONDS);
        try {
            receivingThread.join(6000L);
        } catch (Exception e) {
            // TODO: handle exceptions
        }
    }
}
