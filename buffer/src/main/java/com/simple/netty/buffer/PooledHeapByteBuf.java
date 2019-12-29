package com.simple.netty.buffer;

import com.simple.netty.common.internal.ObjectPool;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

/**
 * Date: 2019-12-14
 * Time: 12:39
 *
 * @author yrw
 */
public class PooledHeapByteBuf extends PooledByteBuf<byte[]> {
    private static final ObjectPool<PooledHeapByteBuf> RECYCLER = new ObjectPool<>(
        handler -> new PooledHeapByteBuf(handler, 0));

    protected PooledHeapByteBuf(Consumer<PooledHeapByteBuf> recycleHandler, int maxCapacity) {
        super(recycleHandler, maxCapacity);
    }

    static PooledHeapByteBuf newInstance(int maxCapacity) {
        PooledHeapByteBuf buf = RECYCLER.get();
        buf.reuse(maxCapacity);
        return buf;
    }

    @Override
    protected byte _getByte(int index) {
        return HeapByteBufUtil.getByte(memory, idx(index));
    }

    @Override
    protected short _getShort(int index) {
        return HeapByteBufUtil.getShort(memory, idx(index));
    }

    @Override
    protected int _getInt(int index) {
        return HeapByteBufUtil.getInt(memory, index);
    }

    @Override
    protected long _getLong(int index) {
        return HeapByteBufUtil.getLong(memory, index);
    }

    @Override
    protected void _setByte(int index, int value) {
        HeapByteBufUtil.setByte(memory, idx(index), value);
    }

    @Override
    protected void _setInt(int index, int value) {
        HeapByteBufUtil.setInt(memory, idx(index), value);
    }

    @Override
    protected void _setShort(int index, int value) {
        HeapByteBufUtil.setShort(memory, idx(index), value);
    }

    @Override
    public boolean isDirect() {
        return false;
    }

    @Override
    public final ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        checkDstIndex(index, length, dstIndex, dst.capacity());
        if (dst.hasArray()) {
            getBytes(index, dst.array(), dstIndex, length);
        } else {
            dst.setBytes(dstIndex, memory, idx(index), length);
        }
        return this;
    }

    @Override
    public final ByteBuf getBytes(int index, ByteBuffer dst) {
        int length = dst.remaining();
        checkIndex(index, length);
        dst.put(memory, idx(index), length);
        return this;
    }

    @Override
    public final ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        checkDstIndex(index, length, dstIndex, dst.length);
        System.arraycopy(memory, idx(index), dst, dstIndex, length);
        return this;
    }

    @Override
    public final ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
        checkSrcIndex(index, length, srcIndex, src.length);
        System.arraycopy(src, srcIndex, memory, idx(index), length);
        return this;
    }

    @Override
    public final ByteBuf setBytes(int index, ByteBuffer src) {
        int length = src.remaining();
        checkIndex(index, length);
        src.get(memory, idx(index), length);
        return this;
    }

    @Override
    public final ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
        checkSrcIndex(index, length, srcIndex, src.capacity());
        if (src.hasArray()) {
            setBytes(index, src.array(), srcIndex, length);
        } else {
            src.getBytes(srcIndex, memory, idx(index), length);
        }
        return this;
    }

    @Override
    public boolean hasArray() {
        return true;
    }

    @Override
    public byte[] array() {
        ensureAccessible();
        return memory;
    }

    @Override
    protected ByteBuffer internalNioBuffer(byte[] memory) {
        return ByteBuffer.wrap(memory);
    }
}
