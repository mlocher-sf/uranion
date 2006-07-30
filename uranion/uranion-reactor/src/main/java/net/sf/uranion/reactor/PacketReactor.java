package net.sf.uranion.reactor;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

import net.sf.uranion.packet.Packet;
import net.sf.uranion.packet.PacketCallback;

public class PacketReactor extends AbstractServer {

    private Selector selector;

    private SelectionKey acceptorKey;

    private Thread reactorThread;

    public PacketReactor(int port, int numProcessors, PacketProcessor processor) throws IOException {
        super(port, numProcessors, processor);
    }

    protected void prepareNetwork() throws IOException {
        this.selector = Selector.open();
        this.startListen(false);
        this.registerAcceptor();
    }

    private void registerAcceptor() throws ClosedChannelException {
        this.acceptorKey = this.getServerSocket().register(this.selector, SelectionKey.OP_ACCEPT);
        this.acceptorKey.attach(new PacketAcceptor());
    }

    public boolean hasActiveConnections() {
        return this.selector.isOpen() ? !this.selector.keys().isEmpty() : false;
    }

    public void stopListen() throws IOException {
        if (this.acceptorKey != null && this.acceptorKey.isValid()) {
            this.acceptorKey.cancel();
            this.acceptorKey = null;
        }
        super.stopListen();
    }

    public void shutdown() throws IOException, InterruptedException {
        super.shutdown();
        if (this.reactorThread != null) {
            this.reactorThread.interrupt();
        }
        this.selector.close();
    }

    public void run() {
        try {
            this.reactorThread = Thread.currentThread();
            while (!Thread.interrupted()) {
                this.selector.select();
                Set selected = selector.selectedKeys();
                Iterator it = selected.iterator();
                while (it.hasNext()) {
                    SelectionKey sk = (SelectionKey) it.next();
                    if (sk.isValid()) {
                        this.dispatch(sk);
                    } else {
                        sk.cancel();
                    }
                    it.remove();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            this.reactorThread = null;
        }
    }

    private void dispatch(SelectionKey key) {
        Runnable task = (Runnable) key.attachment();
        if (task != null) {
            task.run();
        }
    }

    private class PacketAcceptor implements Runnable {
        public void run() {
            try {
                PacketReactor.this.acceptConnection();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    } // inner-class

    private void acceptConnection() throws IOException {
        SocketChannel client = this.getServerSocket().accept();
        if (client != null) {
            client.configureBlocking(false);
            this.adaptClientConnection(client);
            new Receiving(client.register(selector, 0)).activate();
        }
    }

    private abstract class ReactorStage implements PacketCallback {

        private SelectionKey sk;

        public ReactorStage(SelectionKey sk) {
            this.sk = sk;
        }

        public ReactorStage(ReactorStage predecessor) {
            this(predecessor.sk);
        }

        protected SelectionKey getKey() {
            return sk;
        }

        public void changeInterest(int interest, Runnable handler) {
            sk.interestOps(interest);
            sk.attach(handler);
            selector.wakeup();
        }

    } // inner-class

    private final class Receiving extends ReactorStage {

        public Receiving(SelectionKey sk) {
            super(sk);
        }

        public Receiving(Finished predecessor) {
            super(predecessor);
        }

        public void activate() {
            Runnable handler = new Packet.AsyncTOCReader(new Processing(this), this.getKey());
            this.changeInterest(SelectionKey.OP_READ, handler);
        }

        public void handlePacketCallback(final Packet packet) {
            // never-called, no because this is the first stage
        }

    } // inner-class

    private final class Processing extends ReactorStage {

        public Processing(Receiving predecessor) {
            super(predecessor);
        }

        public void handlePacketCallback(final Packet packet) {
            process(new Sending(this), packet);
        }

    } // inner-class

    private final class Sending extends ReactorStage {

        public Sending(Processing predecessor) {
            super(predecessor);
        }

        public void handlePacketCallback(Packet packet) {
            Packet result = (packet == null) ? Packet.EMPTY_PACKET : packet;
            Runnable handler = new Packet.AsyncWriter(result, new Finished(this), this.getKey());
            this.changeInterest(SelectionKey.OP_WRITE, handler);
        }

    } // inner-class

    private final class Finished extends ReactorStage {

        public Finished(Sending predecessor) {
            super(predecessor);
        }

        public void handlePacketCallback(Packet packet) {
            packet.dispose();
            new Receiving(this).activate();
        }
    } // inner-class

    public static void main(String[] args) throws IOException {
        new PacketReactor(7777, 4, PacketProcessor.ECHO).run();
    }

}
