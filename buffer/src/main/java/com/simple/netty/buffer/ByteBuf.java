package com.simple.netty.buffer;

import com.simple.netty.common.internal.ReferenceCounted;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;

/**
 * Date: 2019-12-14
 * Time: 12:35
 *
 * @author yrw
 */
public abstract class ByteBuf implements ReferenceCounted {

    public abstract int capacity();

    public abstract ByteBuf capacity(int newCapacity);

    public abstract int maxCapacity();

    public abstract ByteBufAllocator alloc();

    public abstract boolean isDirect();


    public abstract int readerIndex();

    public abstract ByteBuf readerIndex(int readerIndex);

    public abstract int writerIndex();

    public abstract ByteBuf writerIndex(int writerIndex);

    public abstract ByteBuf setIndex(int readerIndex, int writerIndex);


    public abstract int readableBytes();

    public abstract int writableBytes();

    /**
     * 不需要内部重新分配或者copy数据的最大容量
     */
    public int maxFastWritableBytes() {
        return writableBytes();
    }


    public abstract boolean isReadable();

    public abstract boolean isReadable(int size);

    public abstract boolean isWritable();

    public abstract boolean isWritable(int size);

    public abstract ByteBuf clear();


    public abstract ByteBuf markReaderIndex();

    public abstract ByteBuf resetReaderIndex();

    public abstract ByteBuf markWriterIndex();

    public abstract ByteBuf resetWriterIndex();


    public abstract boolean getBoolean(int index);

    public abstract char getChar(int index);

    public abstract byte getByte(int index);

    public abstract short getShort(int index);

    public abstract int getInt(int index);

    public abstract long getLong(int index);

    public abstract float getFloat(int index);

    public abstract double getDouble(int index);


    public abstract ByteBuf getBytes(int index, ByteBuf dst);

    public abstract ByteBuf getBytes(int index, ByteBuf dst, int length);

    public abstract ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length);

    public abstract ByteBuf getBytes(int index, byte[] dst);

    public abstract ByteBuf getBytes(int index, ByteBuffer dst);

    public abstract ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length);

    public abstract int getBytes(int index, GatheringByteChannel out, int length) throws IOException;

    public abstract int getBytes(int index, FileChannel out, long position, int length) throws IOException;


    public abstract ByteBuf setBoolean(int index, boolean value);

    public abstract ByteBuf setChar(int index, int value);

    public abstract ByteBuf setByte(int index, int value);

    public abstract ByteBuf setShort(int index, int value);

    public abstract ByteBuf setInt(int index, int value);

    public abstract ByteBuf setLong(int index, long value);

    public abstract ByteBuf setFloat(int index, float value);

    public abstract ByteBuf setDouble(int index, double value);


    public abstract ByteBuf setBytes(int index, byte[] src, int srcIndex, int length);

    public abstract ByteBuf setBytes(int index, ByteBuffer src);

    public abstract ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length);

    public abstract int setBytes(int index, ScatteringByteChannel in, int length) throws IOException;


    public abstract boolean hasArray();

    public abstract byte[] array();


    public abstract boolean readBoolean();

    public abstract char readChar();

    public abstract byte readByte();

    public abstract short readShort();

    public abstract int readInt();

    public abstract long readLong();

    public abstract float readFloat();

    public abstract double readDouble();


    public abstract ByteBuf readBytes(int length);

    public abstract ByteBuf readBytes(ByteBuf dst);

    public abstract ByteBuf readBytes(ByteBuf dst, int length);

    public abstract ByteBuf readBytes(ByteBuf dst, int dstIndex, int length);

    public abstract ByteBuf readBytes(byte[] dst);

    public abstract ByteBuf readBytes(byte[] dst, int dstIndex, int length);

    public abstract ByteBuf readBytes(ByteBuffer dst);

    public abstract int readBytes(GatheringByteChannel out, int length) throws IOException;


    public abstract ByteBuf writeBoolean(boolean value);

    public abstract ByteBuf writeChar(int value);

    public abstract ByteBuf writeByte(int value);

    public abstract ByteBuf writeShort(int value);

    public abstract ByteBuf writeInt(int value);

    public abstract ByteBuf writeLong(long value);

    public abstract ByteBuf writeFloat(float value);

    public abstract ByteBuf writeDouble(double value);


    public abstract ByteBuf writeBytes(ByteBuf src);

    public abstract ByteBuf writeBytes(ByteBuf src, int length);

    public abstract ByteBuf writeBytes(byte[] src);

    public abstract ByteBuf writeBytes(byte[] src, int srcIndex, int length);

    public abstract ByteBuf writeBytes(ByteBuffer src);

    public abstract ByteBuf writeBytes(ByteBuf src, int srcIndex, int length);

    public abstract int writeBytes(ScatteringByteChannel in, int length) throws IOException;


    public abstract ByteBuffer[] nioBuffers(int index, int length);

    public abstract ByteBuffer nioBuffer(int index, int length);

    public abstract ByteBuffer internalNioBuffer(int index, int length);

    /**
     * 返回ByteBuf里包含的ByteBuffer数量
     *
     * @return
     */
    public abstract int nioBufferCount();

    boolean isAccessible() {
        return refCnt() != 0;
    }
}
