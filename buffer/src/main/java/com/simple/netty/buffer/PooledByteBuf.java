package com.simple.netty.buffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;

/**
 * Date: 2019-12-14
 * Time: 12:38
 *
 * @author yrw
 */
public abstract class PooledByteBuf<T> extends AbstractReferenceCountedByteBuf {

    PoolChunk<T> chunk;

    /**
     * bitmap
     */
    long handle;

    T memory;

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

    PooledByteBuf(int maxCapacity) {
        super(maxCapacity);
    }

    void init(PoolChunk<T> chunk, long handle, int length, int maxLength, PoolThreadCache cache) {
        init0(chunk, handle, length, maxLength, cache);
    }

    private void init0(PoolChunk<T> chunk, long handle, int length, int maxLength, PoolThreadCache cache) {
        assert handle >= 0;
        assert chunk != null;

        this.chunk = chunk;
        memory = chunk.memory;
        allocator = chunk.arena.parent;
        this.cache = cache;
        this.handle = handle;
        this.length = length;
        this.maxLength = maxLength;
    }

    void initUnpooled(PoolChunk<T> chunk, int length) {
        init0(chunk, 0, length, length, null);
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
        return 0;
    }

    @Override
    public final int getBytes(int index, FileChannel out, long position, int length) throws IOException {
        return 0;
    }

    @Override
    public final int readBytes(GatheringByteChannel out, int length) throws IOException {
        checkReadableBytes(length);
        int readBytes = out.write(_internalNioBuffer(readerIndex, length));
        readerIndex += readBytes;
        return readBytes;
    }

    @Override
    public final int readBytes(FileChannel out, long position, int length) throws IOException {
        checkReadableBytes(length);
        int readBytes = out.write(_internalNioBuffer(readerIndex, length), position);
        readerIndex += readBytes;
        return readBytes;
    }

    /**
     * 获取需要的ByteBuffer
     *
     * @param index  开始位置
     * @param length 长度
     * @return
     */
    final ByteBuffer _internalNioBuffer(int index, int length) {
        index = idx(index);
        ByteBuffer buffer = internalNioBuffer(memory);
        buffer.limit(index + length).position(index);
        return buffer;
    }

    protected final int idx(int index) {
        return index;
    }

    protected abstract ByteBuffer internalNioBuffer(T memory);
}
