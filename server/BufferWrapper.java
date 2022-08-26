package server;

import java.nio.ByteBuffer;

public class BufferWrapper {
    private ByteBuffer buffer;
    private final int id;

    public BufferWrapper(int id, int size){
        this.id = id;
        buffer = ByteBuffer.allocate(size);
    }

    public ByteBuffer getBuffer() {
        return buffer;
    }

    public ByteBuffer newBuffer(int size) {
        this.buffer = ByteBuffer.allocate(size);
        return getBuffer();
    }

    public int getId() {
        return id;
    }

}
