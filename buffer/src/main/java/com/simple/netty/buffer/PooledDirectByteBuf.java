package com.simple.netty.buffer;

import com.simple.netty.common.internal.ObjectPool;

import java.nio.ByteBuffer;

/**
 * Date: 2019-12-14
 * Time: 12:38
 *
 * @author yrw
 */
public class PooledDirectByteBuf extends PooledByteBuf<ByteBuffer> {

    private static final ObjectPool<PooledDirectByteBuf> RECYCLER = ObjectPool.newPool(
        () -> new PooledDirectByteBuf(0));

    static PooledDirectByteBuf newInstance(int maxCapacity) {
        PooledDirectByteBuf buf = RECYCLER.get();
        buf.reuse(maxCapacity);
        return buf;
    }

    protected PooledDirectByteBuf(int maxCapacity) {
        super(maxCapacity);
    }

    @Override
    protected byte _getByte(int index) {
        return 0;
    }

    @Override
    protected short _getShort(int index) {
        return 0;
    }

    @Override
    protected int _getInt(int index) {
        return 0;
    }

    @Override
    protected long _getLong(int index) {
        return 0;
    }

    @Override
    protected void _setByte(int index, int value) {

    }

    @Override
    protected void _setInt(int index, int value) {

    }

    @Override
    protected void _setShort(int index, int value) {

    }

    @Override
    public int capacity() {
        return 0;
    }

    @Override
    public ByteBuf capacity(int newCapacity) {
        return null;
    }

    @Override
    public ByteBufAllocator alloc() {
        return null;
    }

    @Override
    public boolean isDirect() {
        return false;
    }

    @Override
    public ByteBuf clear() {
        return null;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        return null;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuffer dst) {
        return null;
    }

    @Override
    public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        return null;
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
        return null;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuffer src) {
        return null;
    }

    @Override
    public ByteBuf readBytes(ByteBuf dst, int length) {
        return null;
    }

    @Override
    public ByteBuf writeBytes(ByteBuf src, int srcIndex, int length) {
        return null;
    }

    @Override
    public int refCnt() {
        return 0;
    }

    @Override
    protected ByteBuffer newInternalNioBuffer(ByteBuffer memory) {
        return null;
    }
}
