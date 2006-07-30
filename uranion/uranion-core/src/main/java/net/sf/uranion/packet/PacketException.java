package net.sf.uranion.packet;

public class PacketException extends RuntimeException {

	public PacketException(String msg, Throwable cause) {
		super(msg, cause);
	}

	public PacketException(String msg) {
		super(msg);
	}

	public PacketException(Throwable cause) {
		super(cause);
	}

}
