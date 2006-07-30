package net.sf.uranion.reactor;

import java.net.InetSocketAddress;

import junit.framework.TestCase;
import net.sf.uranion.packet.Packet;


public class PacketReactorTest extends TestCase {

    private static final int PORT = 7777;

    private PacketReactor reactor;

    public PacketReactorTest(String name) {
        super(name);
    }

    protected void setUp() throws Exception {
        this.reactor = new PacketReactor(PORT, 2, PacketProcessor.ECHO);
        new Thread(this.reactor).start();
    }

    protected void tearDown() throws Exception {
        this.reactor.stopListen();
        super.tearDown();
    }

    public void testRoundtrip() throws Exception {
        PacketProcessorClient client = new PacketProcessorClient(new InetSocketAddress("localhost", PORT));

        Packet dummy = this.createDummyRequest("hello world");
        Packet processed = client.process(dummy);
        Packet processed2 = client.process(dummy);
        
        client.shutdown();
        
        assertProcessed(dummy, processed);
        assertProcessed(dummy, processed2);
    }

    private void assertProcessed(Packet dummy, Packet result) {
        assertEquals(dummy.getCharacters(0), result.getCharacters(0));
        assertEquals(dummy.getNumber(1), result.getNumber(1));
    }

    private Packet createDummyRequest(String content) {
        Packet result = new Packet(2);
        result.setCharacters(0, content);
        result.setNumber(1, 42);
        return result;
    }

}

