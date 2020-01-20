package com.simple.netty.buffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.util.function.Consumer;

/**
 * Date: 2019-12-14
 * Time: 12:38
 *
 * @author yrw
 */
public abstract class PooledByteBuf<T> extends AbstractReferenceCountedByteBuf {

    Consumer<PooledByteBuf<T>> recycleHandler;

    PoolChunk<T> chunk;

    long handle;

    T memory;

    /**
     * 起始偏移
     */
    protected int offset;

    /**
     * 字节长度
     */
    protected int length;

    /**
     * 最大长度
     */
    int maxLength;

    PoolThreadCache cache;

    private ByteBufAllocator allocator;

    @SuppressWarnings("unchecked")
    PooledByteBuf(Consumer<? extends PooledByteBuf<T>> recycleHandler, int maxCapacity) {
        super(maxCapacity);
        this.recycleHandler = (Consumer<PooledByteBuf<T>>) recycleHandler;
    }

    void init(PoolChunk<T> chunk, long handle, int offset, int length, int maxLength, PoolThreadCache cache) {
        init0(chunk, handle, offset, length, maxLength, cache);
    }

    private void init0(PoolChunk<T> chunk, long handle, int offset, int length, int maxLength, PoolThreadCache cache) {
        assert handle >= 0;
        assert chunk != null;

        this.chunk = chunk;
        memory = chunk.memory;
        allocator = chunk.arena.parent;
        this.cache = cache;
        this.handle = handle;
        this.offset = offset;
        this.length = length;
        this.maxLength = maxLength;
    }

    void initUnpooled(PoolChunk<T> chunk, int length) {
        init0(chunk, 0, 0, length, length, null);
    }

    /**
     * 被回收之后，重新使用之前必须调用这个方法，重置一些变量
     */
    final void reuse(int maxCapacity) {
        //设置容量
        maxCapacity(maxCapacity);
        //重置引用计数
        resetRefCnt();
        //重置读写索引
        setIndex0(0, 0);
        //重置mark索引
        discardMarks();
    }

    @Override
    public int capacity() {
        return length;
    }

    @Override
    public final ByteBuf capacity(int newCapacity) {
        if (newCapacity == length) {
            ensureAccessible();
            return this;
        }
        checkNewCapacity(newCapacity);
        if (!chunk.unpooled) {
            // If the request capacity does not require reallocation, just update the length of the memory.
            if (newCapacity > length) {
                if (newCapacity <= maxLength) {
                    length = newCapacity;
                    return this;
                }
            } else if (newCapacity > maxLength >>> 1 &&
                (maxLength > 512 || newCapacity > maxLength - 16)) {
                // here newCapacity < length
                length = newCapacity;
                trimIndicesToCapacity(newCapacity);
                return this;
            }
        }

        // Reallocation required.
        chunk.arena.reallocate(this, newCapacity, true);
        return this;
    }

    @Override
    public int maxFastWritableBytes() {
        return Math.min(maxLength, maxCapacity()) - writerIndex;
    }

    @Override
    public ByteBufAllocator alloc() {
        return allocator;
    }

    @Override
    public final int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
        return out.write(internalNioBuffer(index, length));
    }

    @Override
    public final int getBytes(int index, FileChannel out, long position, int length) throws IOException {
        return 0;
    }

    @Override
    public final int readBytes(GatheringByteChannel out, int length) throws IOException {
        checkReadableBytes(length);
        int readBytes = out.write(internalNioBuffer(readerIndex, length));
        readerIndex += readBytes;
        return readBytes;
    }

    @Override
    public final int setBytes(int index, ScatteringByteChannel in, int length) throws IOException {
        try {
            return in.read(internalNioBuffer(index, length));
        } catch (ClosedChannelException ignored) {
            return -1;
        }
    }

    @Override
    protected final void deallocate() {
        if (handle >= 0) {
            final long handle = this.handle;
            this.handle = -1;
            memory = null;
            chunk.arena.free(chunk, handle, maxLength, cache);
            chunk = null;
            recycle();
        }
    }

    private void recycle() {
        recycleHandler.accept(this);
    }

    @Override
    public final ByteBuffer nioBuffer(int index, int length) {
        return duplicateInternalNioBuffer(index, length).slice();
    }

    ByteBuffer duplicateInternalNioBuffer(int index, int length) {
        checkIndex(index, length);
        return _internalNioBuffer(index, length);
    }

    @Override
    public final ByteBuffer[] nioBuffers(int index, int length) {
        return new ByteBuffer[]{nioBuffer(index, length)};
    }

    protected final ByteBuffer internalNioBuffer() {
        return newInternalNioBuffer(memory);
    }

    @Override
    public final ByteBuffer internalNioBuffer(int index, int length) {
        checkIndex(index, length);
        return _internalNioBuffer(index, length);
    }

    final ByteBuffer _internalNioBuffer(int index, int length) {
        index = idx(index);
        ByteBuffer buffer = newInternalNioBuffer(memory);
        buffer.limit(index + length).position(index);
        return buffer;
    }

    @Override
    public final int nioBufferCount() {
        return 1;
    }

    protected final int idx(int index) {
        return offset + index;
    }

    protected abstract ByteBuffer newInternalNioBuffer(T memory);
}
