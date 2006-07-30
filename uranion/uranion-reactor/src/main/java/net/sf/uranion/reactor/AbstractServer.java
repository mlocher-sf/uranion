package net.sf.uranion.reactor;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import net.sf.uranion.packet.Packet;
import net.sf.uranion.packet.PacketCallback;

import EDU.oswego.cs.dl.util.concurrent.LinkedQueue;
import EDU.oswego.cs.dl.util.concurrent.PooledExecutor;

public abstract class AbstractServer implements Runnable {

    private static final int SO_RCVBUF_SIZE = 128 * 1024;

    private static final int SO_SNDBUF_SIZE = 128 * 1024;

    private int port;

    private ServerSocketChannel serverSocket;

    private PacketProcessor processor;

    private PooledExecutor processorPool;

    private int numProcessors;

    protected AbstractServer(int port, int numProcessors, PacketProcessor processor) throws IOException {
        super();
        this.port = port;
        this.processor = processor;
        this.numProcessors = numProcessors;
        this.processorPool = this.createProcessorPool();
        this.prepareNetwork();
    }

    public void shutdown() throws IOException, InterruptedException {
        this.stopListen();
        this.shutdownProcessorPool();
    }

    // -------------------------------------------------------------------------
    // Networking

    public abstract boolean hasActiveConnections();

    protected abstract void prepareNetwork() throws IOException;

    private InetSocketAddress getBindAddress() {
        return new InetSocketAddress(this.port);
    }

    protected void startListen(boolean blockingSocket) throws IOException {
        this.serverSocket = ServerSocketChannel.open();
        ServerSocket socket = this.serverSocket.socket();
        socket.setReceiveBufferSize(SO_RCVBUF_SIZE);
        socket.bind(this.getBindAddress());
        this.serverSocket.configureBlocking(blockingSocket);
    }

    protected ServerSocketChannel getServerSocket() {
        return serverSocket;
    }

    public void stopListen() throws IOException {
        if (this.serverSocket != null && this.serverSocket.isOpen()) {
            this.serverSocket.close();
            this.serverSocket = null;
        }
    }

    protected void adaptClientConnection(SocketChannel client) throws SocketException {
        Socket socket = client.socket();
        socket.setSendBufferSize(SO_SNDBUF_SIZE);
        socket.setTcpNoDelay(true);
        socket.setKeepAlive(true);
    }

    // -------------------------------------------------------------------------
    // Packet processing

    private PooledExecutor createProcessorPool() {
        PooledExecutor executor = new PooledExecutor(new LinkedQueue(), this.numProcessors);
        executor.setMinimumPoolSize(this.numProcessors);
        return executor;
    }

    private void shutdownProcessorPool() throws InterruptedException {
        if (this.processorPool != null) {
            this.processorPool.shutdownAfterProcessingCurrentlyQueuedTasks();
            this.processorPool.awaitTerminationAfterShutdown();
            this.processorPool = null;
        }
    }

    protected void process(final PacketCallback resultCallback, final Packet packet) {
        Runnable job = new Runnable() {
            public void run() {
                try {
                    resultCallback.handlePacketCallback(processor.process(packet));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };

        try {
            this.processorPool.execute(job);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
