package net.sf.uranion.packet;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;

import junit.framework.TestCase;

public class PacketTest extends TestCase {

    private Packet packet;

    public PacketTest(String name) {
        super(name);
    }

    protected void setUp() throws Exception {
        this.packet = new Packet(3);
    }

    public void testNumber() {
        this.packet.setNumber(0, 111);
        this.packet.setNumber(1, 222);
        this.packet.setNumber(2, 333);
        assertEquals(111, this.packet.getNumber(0));
        assertEquals(222, this.packet.getNumber(1));
        assertEquals(333, this.packet.getNumber(2));
        assertEquals(111, this.packet.getNumber(0));
        assertEquals(222, this.packet.getNumber(1));
        assertEquals(333, this.packet.getNumber(2));
    }

    public void testChar() {
        this.packet.setCharacters(0, "foo");
        this.packet.setCharacters(1, new StringBuffer("bar"));
        this.packet.setCharacters(2, "baz");
        assertEquals("foo", this.packet.getCharacters(0).toString());
        assertEquals("bar", this.packet.getCharacters(1).toString());
        assertEquals("baz", this.packet.getCharacters(2).toString());
        assertEquals("foo", this.packet.getCharacters(0).toString());
        assertEquals("bar", this.packet.getCharacters(1).toString());
        assertEquals("baz", this.packet.getCharacters(2).toString());
    }

    public void testRaw() {
        this.packet.setRaw(0, new byte[] { 1, 2, 3 }, false);
        byte[] d1 = this.packet.getRaw(0).array();
        byte[] d2 = this.packet.getRaw(0).array();

        assertEquals(3, d1.length);
        assertEquals(1, d1[0]);
        assertEquals(2, d1[1]);
        assertEquals(3, d1[2]);
        assertEquals(3, d2.length);
        assertEquals(1, d2[0]);
        assertEquals(2, d2[1]);
        assertEquals(3, d2[2]);
    }

    public void testObject() throws ClassNotFoundException {
        this.packet.setObject(0, new Integer(1));
        this.packet.setObject(1, new Long(4));
        this.packet.setObject(2, "hello world");
        assertEquals(new Integer(1), this.packet.getObject(0));
        assertEquals(new Long(4), this.packet.getObject(1));
        assertEquals("hello world", this.packet.getObject(2));
        assertEquals(new Integer(1), this.packet.getObject(0));
        assertEquals(new Long(4), this.packet.getObject(1));
        assertEquals("hello world", this.packet.getObject(2));
    }

    public void testRoundtrip() throws IOException {
        this.packet.setNumber(0, 0x44FF);
        this.packet.setCharacters(1, "hello world");
        this.packet.setRaw(2, new byte[] { 1, 2, 3, 4, 5, 6 }, false);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        this.packet.write(Channels.newChannel(out));
        out.close();
        Packet packet2 = new Packet(ByteBuffer.wrap(out.toByteArray()));
        assertEquals(this.packet.getNumber(0), packet2.getNumber(0));
        assertEquals(this.packet.getCharacters(1), packet2.getCharacters(1));
        assertEquals(this.packet.getRaw(2), packet2.getRaw(2));
    }

    public void testRoundtripChannel() throws IOException {
        this.packet.setNumber(0, 0x44FF);
        this.packet.setCharacters(1, "hello world");
        this.packet.setRaw(2, new byte[] { 1, 2, 3, 4, 5, 6 }, false);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        this.packet.write(Channels.newChannel(out));
        out.close();
        Packet packet2 = new Packet(Channels.newChannel(new ByteArrayInputStream(out.toByteArray())));
        assertEquals(this.packet.getNumber(0), packet2.getNumber(0));
        assertEquals(this.packet.getCharacters(1), packet2.getCharacters(1));
        assertEquals(this.packet.getRaw(2), packet2.getRaw(2));
    }

}
