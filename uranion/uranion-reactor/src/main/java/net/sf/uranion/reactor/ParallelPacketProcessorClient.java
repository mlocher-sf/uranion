// $Id:$
package net.sf.uranion.reactor;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import net.sf.uranion.packet.Packet;
import EDU.oswego.cs.dl.util.concurrent.BoundedBuffer;
import EDU.oswego.cs.dl.util.concurrent.Callable;
import EDU.oswego.cs.dl.util.concurrent.FutureResult;
import EDU.oswego.cs.dl.util.concurrent.PooledExecutor;


public class ParallelPacketProcessorClient implements PacketProcessor {

    private ThreadLocal client = new ThreadLocal() {
        protected synchronized Object initialValue() {
            try {
                return new PacketProcessorClient(remoteAddress);
            } catch (IOException e) {
                throw new Error(e);
            }
        }
    };

    private SocketAddress remoteAddress;

    private PooledExecutor executor;

    public ParallelPacketProcessorClient(int numClients, SocketAddress remoteAddress) throws IOException {
        super();
        this.executor = new PooledExecutor(new BoundedBuffer(numClients), numClients);
        this.executor.setMinimumPoolSize(numClients);
        this.executor.runWhenBlocked();
        this.remoteAddress = remoteAddress;
    }

    public ParallelPacketProcessorClient(int numClients, String host, int port) throws IOException {
        this(numClients, new InetSocketAddress(host, port));
    }

    protected PacketProcessorClient getClient() {
        return (PacketProcessorClient) this.client.get();
    }

    public void shutdown() throws InterruptedException {
        this.executor.shutdownAfterProcessingCurrentlyQueuedTasks();
        this.executor.awaitTerminationAfterShutdown();
    }

    public Packet process(final Packet packet) throws IOException {

        try {
            FutureResult result = new FutureResult();
            this.executor.execute(result.setter(new Callable() {
                public Object call() throws Exception {
                    return getClient().process(packet);
                }
            }));
            return (Packet) result.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws Exception {
        final ParallelPacketProcessorClient client = new ParallelPacketProcessorClient(4, "localhost", 7777);

        System.out.println("start");
        for (int i = 0; i < 1000; i++) {
            client.process(Packet.EMPTY_PACKET);
        }
        System.out.println("done");
        client.shutdown();
    }

}
