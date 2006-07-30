package net.sf.uranion.reactor;

import java.io.IOException;

import net.sf.uranion.packet.Packet;

public interface PacketProcessor {

    public final static PacketProcessor ECHO = new PacketProcessor() {
        public Packet process(Packet packet) {
            //System.out.print('.');
            return packet;
        }
    };

    public Packet process(Packet packet) throws IOException;
}