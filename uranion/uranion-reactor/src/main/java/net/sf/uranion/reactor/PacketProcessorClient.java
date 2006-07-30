package net.sf.uranion.reactor;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import net.sf.uranion.packet.Packet;

public class PacketProcessorClient implements PacketProcessor {

    private SocketAddress remoteAddress;

    private SocketChannel clientSocket;

    public PacketProcessorClient(SocketAddress remoteAddress) throws IOException {
        super();
        this.remoteAddress = remoteAddress;
        this.connect();
    }

    public PacketProcessorClient(String host, int port) throws IOException {
        this(new InetSocketAddress(host, port));
    }

    private void connect() throws IOException {
        this.clientSocket = SocketChannel.open(this.remoteAddress);
        Socket socket = this.clientSocket.socket();
        socket.setTcpNoDelay(true);
        socket.setKeepAlive(true);
    }

    public synchronized void shutdown() throws IOException {
        this.clientSocket.close();
    }

    public synchronized Packet process(Packet packet) throws IOException {
        packet.write(this.clientSocket);
        return new Packet(this.clientSocket);
    }

    public static void main(String[] args) throws Exception {
        PacketProcessorClient client = new PacketProcessorClient("localhost", 7777);
        ByteBuffer testData = ByteBuffer.allocateDirect(7*1024*1024);        
        for (int i = 0; i < 1000; i++) {
            Packet packet = new Packet(1);
            packet.setRaw(0, testData, true);
            client.process(packet);
        }
        client.shutdown();
    }

}
