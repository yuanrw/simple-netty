package com.simple.netty.buffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
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

    /**
     * bitmap
     */
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
     * Method must be called before reuse this {@link PooledByteBufAllocator}
     */
    final void reuse(int maxCapacity) {
        maxCapacity(maxCapacity);
        resetRefCnt();
        setIndex0(0, 0);
        discardMarks();
    }

    @Override
    public int capacity() {
        return length;
    }

    @Override
    public ByteBuf capacity(int newCapacity) {
        if (newCapacity == length) {

        }
        return null;
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
    public final int readBytes(FileChannel out, long position, int length) throws IOException {
        checkReadableBytes(length);
        int readBytes = out.write(internalNioBuffer(readerIndex, length), position);
        readerIndex += readBytes;
        return readBytes;
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

    protected final int idx(int index) {
        return offset + index;
    }

    protected abstract ByteBuffer newInternalNioBuffer(T memory);
}
