package com.simple.netty.buffer;

import com.simple.netty.common.internal.PlatformDependent;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;

/**
 * Date: 2019-12-14
 * Time: 12:40
 *
 * @author yrw
 */
public class UnpooledDirectByteBuf extends AbstractReferenceCountedByteBuf {

    private final ByteBufAllocator alloc;

    /**
     * 通过unsafe直接获取
     */
    ByteBuffer buffer;
    private int capacity;
    private boolean doNotFree;

    public UnpooledDirectByteBuf(ByteBufAllocator alloc, int initialCapacity, int maxCapacity) {
        super(maxCapacity);

        this.alloc = alloc;
        setByteBuffer(allocateDirect(initialCapacity), false);
    }

    /**
     * 创建一个新的direct buffer
     *
     * @param maxCapacity direct buffer的最大容量
     */
    protected UnpooledDirectByteBuf(ByteBufAllocator alloc, ByteBuffer initialBuffer, int maxCapacity) {
        this(alloc, initialBuffer, maxCapacity, false, true);
    }

    UnpooledDirectByteBuf(ByteBufAllocator alloc, ByteBuffer initialBuffer,
                          int maxCapacity, boolean doFree, boolean slice) {
        super(maxCapacity);

        int initialCapacity = initialBuffer.remaining();
        this.alloc = alloc;
        doNotFree = !doFree;
        setByteBuffer((slice ? initialBuffer.slice() : initialBuffer).order(ByteOrder.BIG_ENDIAN), false);
        writerIndex(initialCapacity);
    }

    protected void freeDirect(ByteBuffer buffer) {
        PlatformDependent.freeDirectBuffer(buffer);
    }

    void setByteBuffer(ByteBuffer buffer, boolean tryFree) {
        if (tryFree) {
            ByteBuffer oldBuffer = this.buffer;
            if (oldBuffer != null) {
                if (doNotFree) {
                    doNotFree = false;
                } else {
                    freeDirect(oldBuffer);
                }
            }
        }

        this.buffer = buffer;
        capacity = buffer.remaining();
    }

    @Override
    public byte getByte(int index) {
        ensureAccessible();
        return _getByte(index);
    }

    @Override
    protected byte _getByte(int index) {
        return buffer.get(index);
    }

    @Override
    public short getShort(int index) {
        ensureAccessible();
        return _getShort(index);
    }

    @Override
    protected short _getShort(int index) {
        return buffer.getShort(index);
    }

    @Override
    public int getInt(int index) {
        ensureAccessible();
        return _getInt(index);
    }

    @Override
    protected int _getInt(int index) {
        return buffer.getInt(index);
    }

    @Override
    public long getLong(int index) {
        ensureAccessible();
        return _getLong(index);
    }

    @Override
    protected long _getLong(int index) {
        return buffer.getLong(index);
    }

    @Override
    protected void _setByte(int index, int value) {
        buffer.put(index, (byte) value);
    }

    @Override
    protected void _setShort(int index, int value) {
        buffer.putShort(index, (short) value);
    }

    @Override
    protected void _setInt(int index, int value) {
        buffer.putInt(index, value);
    }

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public ByteBuf capacity(int newCapacity) {
        checkNewCapacity(newCapacity);
        int oldCapacity = capacity;
        if (newCapacity == oldCapacity) {
            return this;
        }
        int bytesToCopy;
        if (newCapacity > oldCapacity) {
            bytesToCopy = oldCapacity;
        } else {
            trimIndicesToCapacity(newCapacity);
            bytesToCopy = newCapacity;
        }
        ByteBuffer oldBuffer = buffer;
        ByteBuffer newBuffer = allocateDirect(newCapacity);
        oldBuffer.position(0).limit(bytesToCopy);
        newBuffer.position(0).limit(bytesToCopy);
        newBuffer.put(oldBuffer).clear();
        setByteBuffer(newBuffer, true);
        return this;
    }

    @Override
    public ByteBufAllocator alloc() {
        return alloc;
    }

    @Override
    public boolean isDirect() {
        return true;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        checkDstIndex(index, length, dstIndex, dst.capacity());
        if (dst.hasArray()) {
            getBytes(index, dst.array(), dstIndex, length);
        } else {
            dst.setBytes(dstIndex, this, index, length);
        }
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuffer dst) {
        checkIndex(index, dst.remaining());

        ByteBuffer tmpBuf = buffer.duplicate();
        tmpBuf.clear().position(index).limit(index + dst.remaining());
        dst.put(tmpBuf);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        checkDstIndex(index, length, dstIndex, dst.length);

        ByteBuffer tmpBuf = buffer.duplicate();
        tmpBuf.clear().position(index).limit(index + length);
        tmpBuf.get(dst, dstIndex, length);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
        checkSrcIndex(index, length, srcIndex, src.length);
        ByteBuffer tmpBuf = buffer.duplicate();
        tmpBuf.clear().position(index).limit(index + length);
        tmpBuf.put(src, srcIndex, length);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuffer src) {
        return null;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
        return null;
    }

    @Override
    public boolean hasArray() {
        return false;
    }

    @Override
    public byte[] array() {
        throw new UnsupportedOperationException("direct buffer");
    }

    @Override
    public int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
        ensureAccessible();
        if (length == 0) {
            return 0;
        }
        ByteBuffer tmpBuf = buffer.duplicate();
        tmpBuf.clear().position(index).limit(index + length);
        return out.write(tmpBuf);
    }

    @Override
    public int getBytes(int index, FileChannel out, long position, int length) throws IOException {
        ensureAccessible();
        if (length == 0) {
            return 0;
        }

        ByteBuffer tmpBuf = buffer.duplicate();
        tmpBuf.clear().position(index).limit(index + length);
        return out.write(tmpBuf, position);
    }

    protected ByteBuffer allocateDirect(int initialCapacity) {
        return ByteBuffer.allocateDirect(initialCapacity);
    }
}
