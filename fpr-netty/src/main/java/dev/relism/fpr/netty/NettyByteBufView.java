package dev.relism.fpr.netty;

import dev.relism.fpr.core.ByteView;
import io.netty.buffer.ByteBuf;

public final class NettyByteBufView implements ByteView {
    private final ByteBuf buffer;
    private final int offset;
    private final int length;

    public NettyByteBufView(ByteBuf buffer, int offset, int length) {
        if (buffer == null) {
            throw new IllegalArgumentException("buffer must not be null");
        }
        if (offset < 0 || length < 0 || offset + length > buffer.capacity()) {
            throw new IllegalArgumentException("invalid offset/length");
        }
        this.buffer = buffer;
        this.offset = offset;
        this.length = length;
    }

    public ByteBuf buffer() {
        return buffer;
    }

    public int offset() {
        return offset;
    }

    @Override
    public int length() {
        return length;
    }

    @Override
    public byte byteAt(int index) {
        if (index < 0 || index >= length) {
            throw new IndexOutOfBoundsException("index: " + index);
        }
        return buffer.getByte(offset + index);
    }

    @Override
    public boolean supportsLong() {
        return true;
    }

    @Override
    public long longAt(int index) {
        if (index < 0 || index + 8 > length) {
            throw new IndexOutOfBoundsException("index: " + index);
        }
        return buffer.getLongLE(offset + index);
    }
}
