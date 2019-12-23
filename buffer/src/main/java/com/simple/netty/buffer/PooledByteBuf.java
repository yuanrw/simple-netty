package com.simple.netty.buffer;

import com.simple.netty.common.IllegalReferenceCountException;

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
    long handle;
    T memory;
    int offset;

    /**
     * 字节长度
     */
    protected int length;

    /**
     * 最大长度
     */
    int maxLength;

    PoolThreadCache cache;
    ByteBuffer tmpNioBuf;

    private ByteBufAllocator allocator;

    PooledByteBuf(int maxCapacity) {
        super(maxCapacity);
    }

    void init(PoolChunk<T> chunk, ByteBuffer nioBuffer,
              long handle, int offset, int length, int maxLength, PoolThreadCache cache) {
        init0(chunk, nioBuffer, handle, offset, length, maxLength, cache);
    }

    private void init0(PoolChunk<T> chunk, ByteBuffer nioBuffer,
                       long handle, int offset, int length, int maxLength, PoolThreadCache cache) {
        assert handle >= 0;
        assert chunk != null;

        this.chunk = chunk;
        memory = chunk.memory;
        tmpNioBuf = nioBuffer;
        allocator = chunk.arena.parent;
        this.cache = cache;
        this.handle = handle;
        this.offset = offset;
        this.length = length;
        this.maxLength = maxLength;
    }

    void initUnpooled(PoolChunk<T> chunk, int length) {
        init0(chunk, null, 0, chunk.offset, length, length, null);
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
        return null;
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
        int readBytes = out.write(_internalNioBuffer(readerIndex, length, false));
        readerIndex += readBytes;
        return readBytes;
    }

    @Override
    public final int readBytes(FileChannel out, long position, int length) throws IOException {
        checkReadableBytes(length);
        int readBytes = out.write(_internalNioBuffer(readerIndex, length, false), position);
        readerIndex += readBytes;
        return readBytes;
    }

    /**
     * 获取需要的ByteBuffer
     *
     * @param index     开始位置
     * @param length    长度
     * @param duplicate 是否复制
     * @return
     */
    final ByteBuffer _internalNioBuffer(int index, int length, boolean duplicate) {
        index = idx(index);
        ByteBuffer buffer = duplicate ? newInternalNioBuffer(memory) : internalNioBuffer();
        buffer.limit(index + length).position(index);
        return buffer;
    }

    protected final int idx(int index) {
        return offset + index;
    }

    /**
     * 转成ByteBuffer
     *
     * @return
     */
    public final ByteBuffer internalNioBuffer() {
        ByteBuffer tmpNioBuf = this.tmpNioBuf;
        if (tmpNioBuf == null) {
            this.tmpNioBuf = tmpNioBuf = newInternalNioBuffer(memory);
        }
        return tmpNioBuf;
    }

    protected abstract ByteBuffer newInternalNioBuffer(T memory);

    /**
     * 判断buffer是否已经被释放
     * 每次获取buffer内容之前都要调用
     */
    protected final void ensureAccessible() {
        if (!isAccessible()) {
            throw new IllegalReferenceCountException(0);
        }
    }
}
