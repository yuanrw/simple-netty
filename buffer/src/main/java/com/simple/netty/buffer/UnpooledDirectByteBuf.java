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

    /**
     * 用于分配内存
     */
    private final ByteBufAllocator alloc;

    /**
     * 缓冲区，通过unsafe直接获取
     */
    ByteBuffer buffer;
    private int capacity;
    private boolean doNotFree;

    public UnpooledDirectByteBuf(ByteBufAllocator alloc, int initialCapacity, int maxCapacity) {
        super(maxCapacity);
        this.alloc = alloc;

        //初始化buffer
        setByteBuffer(allocateDirect(initialCapacity), false);
    }

    /**
     * 创建一个新的DirectBuffer
     *
     * @param maxCapacity DirectBuffer的最大容量
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

    @Override
    public boolean hasArray() {
        return false;
    }

    @Override
    public byte[] array() {
        throw new UnsupportedOperationException("direct buffer");
    }

    @Override
    public ByteBuffer internalNioBuffer(int index, int length) {
        checkIndex(index, length);
        return (ByteBuffer) buffer.duplicate().clear().position(index).limit(index + length);
    }

    void setByteBuffer(ByteBuffer buffer, boolean tryFree) {
        //释放buffer
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

    protected void freeDirect(ByteBuffer buffer) {
        PlatformDependent.freeDirectBuffer(buffer);
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
    public ByteBuffer[] nioBuffers(int index, int length) {
        return new ByteBuffer[]{nioBuffer(index, length)};
    }

    @Override
    public ByteBuffer nioBuffer(int index, int length) {
        checkIndex(index, length);
        return ((ByteBuffer) buffer.duplicate().position(index).limit(index + length)).slice();
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
    public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        checkDstIndex(index, length, dstIndex, dst.capacity());
        if (dst.hasArray()) {
            //用array数组实现的
            getBytes(index, dst.array(), dstIndex, length);
        } else {
            //用DirectBuffer实现的
            for (ByteBuffer bb : dst.nioBuffers(dstIndex, length)) {
                int bbLen = bb.remaining();
                getBytes(index, bb);
                index += bbLen;
            }
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
        //复制一份，重置各种index
        ByteBuffer tmpBuf = buffer.duplicate();
        tmpBuf.clear().position(index).limit(index + length);
        //复制到dest
        tmpBuf.get(dst, dstIndex, length);
        return this;
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

    @Override
    public ByteBuf setByte(int index, int value) {
        ensureAccessible();
        _setByte(index, value);
        return this;
    }

    @Override
    protected void _setByte(int index, int value) {
        buffer.put(index, (byte) value);
    }

    @Override
    public ByteBuf setShort(int index, int value) {
        ensureAccessible();
        _setShort(index, value);
        return this;
    }

    @Override
    protected void _setShort(int index, int value) {
        buffer.putShort(index, (short) value);
    }

    @Override
    public ByteBuf setInt(int index, int value) {
        ensureAccessible();
        _setInt(index, value);
        return this;
    }

    @Override
    protected void _setInt(int index, int value) {
        buffer.putInt(index, value);
    }

    @Override
    public ByteBuf setLong(int index, long value) {
        ensureAccessible();
        _setLong(index, value);
        return this;
    }

    @Override
    protected void _setLong(int index, long value) {
        buffer.putLong(index, value);
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
        ensureAccessible();
        ByteBuffer tmpBuf = buffer.duplicate();
        if (src == tmpBuf) {
            src = src.duplicate();
        }

        tmpBuf.clear().position(index).limit(index + src.remaining());
        tmpBuf.put(src);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
        checkSrcIndex(index, length, srcIndex, src.capacity());
        src.getBytes(srcIndex, this, index, length);
        return this;
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
        //创建新的ByteBuffer
        ByteBuffer newBuffer = allocateDirect(newCapacity);
        //设置position和limit
        oldBuffer.position(0).limit(bytesToCopy);
        newBuffer.position(0).limit(bytesToCopy);

        //复制到dest并且重置各种index
        newBuffer.put(oldBuffer).clear();

        //替换
        setByteBuffer(newBuffer, true);
        return this;
    }

    @Override
    protected void deallocate() {
        ByteBuffer buffer = this.buffer;
        if (buffer == null) {
            return;
        }

        this.buffer = null;

        if (!doNotFree) {
            freeDirect(buffer);
        }
    }

    protected ByteBuffer allocateDirect(int initialCapacity) {
        return ByteBuffer.allocateDirect(initialCapacity);
    }
}
