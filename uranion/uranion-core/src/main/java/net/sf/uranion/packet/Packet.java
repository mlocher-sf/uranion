package net.sf.uranion.packet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class Packet {

    private static final byte[] ZERO_TOC = new byte[0];

    public final static Packet EMPTY_PACKET = new Packet(0);

    private final static ByteBuffer EMPTY = ByteBuffer.allocate(0).asReadOnlyBuffer();

    private final static Charset CHARSET = Charset.forName("UTF-16");

    private static final byte FLAG_DIRECT = 1;

    private static final byte CONTENT_NUMBER = 2;

    private static final byte CONTENT_CHAR = 4;

    private static final byte CONTENT_OBJECT = 8;

    private static final byte CONTENT_RAW = 16;

    private static final byte CONTENT_RAW_DIRECT = CONTENT_RAW + FLAG_DIRECT;

    private static final byte[] TOC_MAGIC = new byte[] { 84, 79, 67 };

    private static final int TOC_SIZE_PER_ENTRY = 5;

    private static final int TOC_HEADER_SIZE = 5;

    // ---------------------------------------------------------------

    private byte[] toc;

    private List contents;

    private Packet() {

    }

    public Packet(int fieldCount) {
        this.toc = new byte[fieldCount];
        this.contents = new ArrayList(Collections.nCopies(fieldCount, EMPTY));
    }

    private Packet(AsyncTOCReader tocReader) throws IOException {
        this.parseTOCAndAllocateBuffers(tocReader);
    }

    public String toString() {
        StringBuffer res = new StringBuffer("Packet [");
        for (int i = 0; i < this.toc.length; i++) {
            res.append(this.getNameOfContent(i));
            res.append("=");
            res.append(((ByteBuffer) this.contents.get(i)).limit());
            res.append("b");
            if (i < this.toc.length - 1) {
                res.append(", ");
            }
        }
        res.append("]");
        return res.toString();
    }

    private String getNameOfContent(int position) {
        byte type = this.toc[position];
        switch (type) {
        case 0:
            return "NUL";
        case CONTENT_NUMBER:
            return "NUM";
        case CONTENT_CHAR:
            return "CHAR";
        case CONTENT_OBJECT:
            return "OBJ";
        case CONTENT_RAW:
            return "RAW";
        case CONTENT_RAW_DIRECT:
            return "RAWDIRECT";
        default:
            throw new PacketException("invalid content type");
        }
    }

    public void dispose() {
        this.toc = ZERO_TOC;
        this.contents.clear();
        this.contents = Collections.EMPTY_LIST;
    }

    // ---------------------------------------------------------------

    private ByteBuffer assembleTOC() {
        int size = TOC_HEADER_SIZE + TOC_SIZE_PER_ENTRY * this.toc.length;
        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.put(TOC_MAGIC);
        buf.putShort((short) this.toc.length);
        for (int i = 0; i < this.toc.length; i++) {
            buf.put(this.toc[i]);
            buf.putInt(((ByteBuffer) this.contents.get(i)).limit());
        }
        buf.rewind();
        return buf;
    }

    private int[] parseTOC(short fieldCount, ByteBuffer toc) throws IOException {
        this.toc = new byte[fieldCount];
        int[] lengths = new int[fieldCount];
        for (int i = 0; i < this.toc.length; i++) {
            this.toc[i] = toc.get();
            lengths[i] = (this.isDirect(i) ? -1 : 1) * toc.getInt();
        }
        return lengths;
    }

    private boolean isDirect(int i) {
        return (this.toc[i] & FLAG_DIRECT) != 0;
    }

    private static short parseTOCHeader(ByteBuffer header) {
        assertTOCMagic(header);
        short fieldCount = header.getShort();
        return fieldCount;
    }

    private static void assertTOCMagic(ByteBuffer header) {
        for (int i = 0; i < TOC_MAGIC.length; i++) {
            if (TOC_MAGIC[i] != header.get()) {
                throw new PacketException("packet header mismatch");
            }
        }
    }

    private void read(ReadableByteChannel src, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            int count = src.read(buffer);
            if (count < 0) {
                throw new PacketException("channel closed");
            }
        }
    }

    // ---------------------------------------------------------------

    private void set(int position, byte type, ByteBuffer data) {
        this.toc[position] = type;
        this.contents.set(position, data);
    }

    public void setCharacters(int position, CharSequence data) {
        this.set(position, CONTENT_CHAR, CHARSET.encode(CharBuffer.wrap(data)));
    }

    public void setNumber(int position, long data) {
        ByteBuffer container = ByteBuffer.allocate(8).putLong(data);
        this.set(position, CONTENT_NUMBER, container);
    }

    public void setRaw(int position, byte[] data, boolean directBuffer) {
        this.setRaw(position, ByteBuffer.wrap(data), directBuffer);
    }

    public void setRaw(int position, ByteBuffer data, boolean directBuffer) {
        this.set(position, directBuffer ? CONTENT_RAW_DIRECT : CONTENT_RAW, data);
    }

    public void setObject(int position, Object data) {
        ByteArrayOutputStream container = new ByteArrayOutputStream(64);
        try {
            ObjectOutputStream oos = new ObjectOutputStream(container);
            oos.writeObject(data);
            oos.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.set(position, CONTENT_OBJECT, ByteBuffer.wrap(container.toByteArray()));
    }

    // ---------------------------------------------------------------

    private ByteBuffer get(int position, byte type) {
        assert (this.toc[position] & type) > 0;
        ByteBuffer container = (ByteBuffer) this.contents.get(position);
        container.rewind();
        return container;
    }

    public CharSequence getCharacters(int position) {
        return CHARSET.decode(this.get(position, CONTENT_CHAR));
    }

    public long getNumber(int position) {
        return this.get(position, CONTENT_NUMBER).getLong();
    }

    public ByteBuffer getRaw(int position) {
        return this.get(position, CONTENT_RAW);
    }

    public Object getObject(int position) throws ClassNotFoundException {
        try {
            final InputStream src = ByteBufferInputStream.wrap(this.get(position, CONTENT_OBJECT));
            return new ObjectInputStream(src).readObject();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // ---------------------------------------------------------------

    private static class ByteBufferInputStream extends InputStream {

        public final static ByteBufferInputStream wrap(ByteBuffer buffer) {
            return new ByteBufferInputStream(buffer);
        }

        private ByteBuffer buf;

        private ByteBufferInputStream(ByteBuffer source) {
            this.buf = source;
        }

        public synchronized int read() throws IOException {
            if (!buf.hasRemaining()) {
                return -1;
            }
            return buf.get();
        }

        public synchronized int read(byte[] bytes, int off, int len) throws IOException {
            int bytesRead = Math.min(len, buf.remaining());
            buf.get(bytes, off, bytesRead);
            return bytesRead;
        }
    }

    public void write(WritableByteChannel channel) throws IOException {
        ByteBuffer tocBuffer = this.assembleTOC();
        while (tocBuffer.hasRemaining()) {
            channel.write(tocBuffer);
        }
        Iterator fields = this.contents.iterator();
        while (fields.hasNext()) {
            ByteBuffer fieldBuffer = (ByteBuffer) fields.next();
            fieldBuffer.rewind();
            while (fieldBuffer.hasRemaining()) {
                channel.write(fieldBuffer);
            }
        }
    }

    public Packet(ByteBuffer packet) throws IOException {
        short numFields = parseTOCHeader(packet);
        int[] lengths = this.parseTOC(numFields, packet);
        this.contents = new ArrayList(numFields);
        for (int i = 0; i < numFields; i++) {
            int fieldLength = lengths[i];
            int end = packet.position() + fieldLength;
            packet.limit(end);
            this.contents.add(packet.slice());
            packet.position(end);
        }
    }

    public Packet(ReadableByteChannel src) throws IOException {
        short numFields = this.readAndParseTOCHeader(src);
        int[] lengths = this.readAndParseTOC(numFields, src);
        this.readFields(lengths, src);
    }

    private short readAndParseTOCHeader(ReadableByteChannel src) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(TOC_HEADER_SIZE);
        this.read(src, header);
        header.rewind();
        return parseTOCHeader(header);
    }

    private int[] readAndParseTOC(short numFields, ReadableByteChannel src) throws IOException {
        ByteBuffer tocData = ByteBuffer.allocate(numFields * TOC_SIZE_PER_ENTRY);
        this.read(src, tocData);
        tocData.rewind();
        return this.parseTOC(numFields, tocData);
    }

    private void readFields(int[] lengths, ReadableByteChannel src) throws IOException {
        this.allocateBuffers(lengths);
        for (int i = 0; i < lengths.length; i++) {
            this.read(src, (ByteBuffer) this.contents.get(i));
        }
    }

    /**
     * Scatter read the fields.
     * 
     * @param src
     *            the source channel
     * @return true if all fields are fully read
     */
    private boolean scatterReadFields(ScatteringByteChannel src) throws IOException {
        ByteBuffer[] buffers = getBuffers(false, false);
        long cnt = src.read(buffers);
        if (cnt == -1) {
            src.close();
        }
        return hasNoRemaining(buffers);
    }

    private static boolean hasNoRemaining(ByteBuffer[] buffers) {
        for (int i = 0; i < buffers.length; i++) {
            if (buffers[i].hasRemaining()) {
                return false;
            }
        }
        return true;
    }

    private ByteBuffer[] getBuffers(boolean rewind, boolean includeTOC) {

        ByteBuffer[] fields = new ByteBuffer[(includeTOC ? 1 + this.toc.length : this.toc.length)];

        int i = 0;
        if (includeTOC) {
            fields[i++] = this.assembleTOC();
        }

        for (int j = 0; j < this.toc.length; i++, j++) {
            ByteBuffer buffer = (ByteBuffer) this.contents.get(j);
            if (rewind) {
                buffer.rewind();
            }
            fields[i] = buffer;
        }
        return fields;
    }

    private void parseTOCAndAllocateBuffers(AsyncTOCReader tocReader) throws IOException {
        ByteBuffer tocData = tocReader.getTOCData();
        tocData.rewind();
        int[] lengths = this.parseTOC(tocReader.getFieldCount(), tocData);
        this.allocateBuffers(lengths);
    }

    private void allocateBuffers(final int[] lengths) {
        this.contents = new ArrayList(lengths.length);

        int i = 0;
        int mergedBufferCount;
        long mergedBufferSize;

        while (i < lengths.length) {
            mergedBufferCount = 1;
            mergedBufferSize = Math.abs(lengths[i]);

            int j = i + 1;
            while (j < lengths.length && lengths[i] * lengths[j] >= 0 && mergedBufferSize + Math.abs(lengths[j]) < Integer.MAX_VALUE) {
                mergedBufferCount++;
                mergedBufferSize += Math.abs(lengths[j]);
                j++;
            }

            final int bufferSize = (int) mergedBufferSize;
            ByteBuffer backingBuffer = (lengths[i] <= 0) ? ByteBuffer.allocateDirect(bufferSize) : ByteBuffer.allocate(bufferSize);

            if (mergedBufferCount > 1) {
                int limit = 0;
                for (int k = 0; k < mergedBufferCount; k++) {
                    limit += lengths[i + k];
                    backingBuffer.limit(limit);
                    this.contents.add(backingBuffer.slice());
                    backingBuffer.position(limit);
                }
            } else {
                this.contents.add(backingBuffer);
            }
            i += j;
        }
    }

    // ---------------------------------------------------------------

    private static abstract class PacketTransfer implements Runnable {

        private PacketCallback callback;

        private SelectionKey sk;

        public PacketTransfer(PacketCallback callback, SelectionKey sk) {
            super();
            this.callback = callback;
            this.sk = sk;
        }

        protected PacketTransfer(PacketTransfer predecessor) {
            this(predecessor.callback, predecessor.sk);
        }

        protected SocketChannel getClient() {
            return (SocketChannel) this.sk.channel();
        }

        protected void setNextReadingPhase(PacketTransfer handler) {
            this.sk.attach(handler);
            if (sk.isReadable()) {
                handler.run();
            }
        }

        protected void transferComplete(Packet packet) {
            this.sk.attach(null);
            this.sk.interestOps(0);
            if (this.callback != null) {
                this.callback.handlePacketCallback(packet);
                this.callback = null;
            }
        }

        public void read(ByteBuffer buffer) {
            try {
                int cnt = this.getClient().read(buffer);
                if (cnt == -1) {
                    this.sk.channel().close();
                }
            } catch (IOException e) {
                handleException(e);
            }
        }

        protected void handleException(Exception e) {
            if (this.sk != null) {
                this.sk.cancel();
            }
            this.dispose();
            throw new RuntimeException(e);
        }
        
        protected void dispose()
        {
            this.sk = null;
            this.callback = null;
        }

    } // inner-class

    public final static class AsyncTOCReader extends PacketTransfer {

        private ByteBuffer header = ByteBuffer.allocate(TOC_HEADER_SIZE);

        private ByteBuffer tocData = null;

        private short fieldCount;

        public AsyncTOCReader(PacketCallback callback, SelectionKey sk) {
            super(callback, sk);
        }

        public short getFieldCount() {
            return fieldCount;
        }

        public ByteBuffer getTOCData() {
            return tocData;
        }

        public void run() {
            if (header.hasRemaining()) {
                this.read(header);
            }
            if (!header.hasRemaining()) {
                if (tocData == null) {
                    header.rewind();
                    this.fieldCount = parseTOCHeader(header);
                    tocData = ByteBuffer.allocate(fieldCount * TOC_SIZE_PER_ENTRY);
                }
                read(tocData);
                if (!tocData.hasRemaining()) {
                    try {
                        AsyncContentReader successor = new AsyncContentReader(this);
                        this.setNextReadingPhase(successor);
                        this.dispose();
                    } catch (IOException e) {
                        this.handleException(e);
                    }
                }
            }
        }

        protected void dispose() {
            super.dispose();
            this.header = null;
            this.tocData = null;
        }

    } // inner-class

    public final static class AsyncContentReader extends PacketTransfer {

        private Packet packet;

        public AsyncContentReader(AsyncTOCReader tocReader) throws IOException {
            super(tocReader);
            this.packet = new Packet(tocReader);
        }

        public void run() {
            try {
                boolean readCompleted = this.packet.scatterReadFields(this.getClient());
                if (readCompleted) {
                    this.transferComplete(this.packet);
                    this.dispose();
                }
            } catch (IOException e) {
                this.handleException(e);
            }
        }

        protected void dispose() {
            super.dispose();
            this.packet = null;
        }

    }// inner-class

    public final static class AsyncWriter extends PacketTransfer {

        private Packet packet;

        private ByteBuffer[] data;

        public AsyncWriter(Packet packet, PacketCallback callback, SelectionKey sk) {
            super(callback, sk);
            this.packet = packet;
            this.data = this.packet.getBuffers(true, true);
        }

        public void run() {
            try {
                this.getClient().write(this.data);
                if (hasNoRemaining(this.data)) {
                    this.transferComplete(this.packet);
                    this.dispose();
                }
            } catch (IOException e) {
                this.handleException(e);
            }
        }

        protected void dispose() {
            super.dispose();
            this.packet = null;
            this.data = null;
        }

    }// inner-class

}