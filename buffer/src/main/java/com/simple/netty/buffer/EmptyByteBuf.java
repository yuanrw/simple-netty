package com.simple.netty.buffer;

import com.simple.netty.common.internal.ObjectUtil;
import com.simple.netty.common.internal.ReferenceCounted;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;

/**
 * Date: 2019-12-25
 * Time: 19:05
 *
 * @author yrw
 */
public class EmptyByteBuf extends ByteBuf {

    private final ByteBufAllocator alloc;

    EmptyByteBuf(ByteBufAllocator alloc) {
        this.alloc = ObjectUtil.checkNotNull(alloc, "alloc");
    }

    @Override
    public int capacity() {
        return 0;
    }

    @Override
    public ByteBuf capacity(int newCapacity) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public int maxCapacity() {
        return 0;
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
    public int readerIndex() {
        return 0;
    }

    @Override
    public ByteBuf readerIndex(int readerIndex) {
        return null;
    }

    @Override
    public int writerIndex() {
        return 0;
    }

    @Override
    public ByteBuf writerIndex(int writerIndex) {
        return null;
    }

    @Override
    public ByteBuf setIndex(int readerIndex, int writerIndex) {
        return null;
    }

    @Override
    public int readableBytes() {
        return 0;
    }

    @Override
    public int writableBytes() {
        return 0;
    }

    @Override
    public boolean isReadable() {
        return false;
    }

    @Override
    public boolean isReadable(int size) {
        return false;
    }

    @Override
    public boolean isWritable() {
        return false;
    }

    @Override
    public boolean isWritable(int size) {
        return false;
    }

    @Override
    public ByteBuf clear() {
        return this;
    }

    @Override
    public ByteBuf markReaderIndex() {
        return this;
    }

    @Override
    public ByteBuf resetReaderIndex() {
        return this;
    }

    @Override
    public ByteBuf markWriterIndex() {
        return this;
    }

    @Override
    public ByteBuf resetWriterIndex() {
        return this;
    }

    @Override
    public boolean getBoolean(int index) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public byte getByte(int index) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public short getShort(int index) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public int getInt(int index) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public long getLong(int index) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public char getChar(int index) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public double getDouble(int index) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst, int length) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public ByteBuf getBytes(int index, byte[] dst) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuffer dst) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public int getBytes(int index, FileChannel out, long position, int length) throws IOException {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuffer src) {
        throw new IndexOutOfBoundsException();
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
        return new byte[0];
    }

    @Override
    public boolean readBoolean() {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public byte readByte() {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public short readShort() {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public int readInt() {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public long readLong() {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public char readChar() {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public double readDouble() {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public ByteBuf readBytes(int length) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public ByteBuf readBytes(ByteBuf dst) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public ByteBuf readBytes(ByteBuf dst, int length) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public ByteBuf readBytes(ByteBuf dst, int dstIndex, int length) {
        return null;
    }

    @Override
    public ByteBuf readBytes(byte[] dst) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public ByteBuf readBytes(byte[] dst, int dstIndex, int length) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public ByteBuf readBytes(ByteBuffer dst) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public int readBytes(GatheringByteChannel out, int length) throws IOException {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public int readBytes(FileChannel out, long position, int length) throws IOException {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public ByteBuf writeBoolean(boolean value) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public ByteBuf writeByte(int value) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public ByteBuf writeInt(int value) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public ByteBuf writeShort(int value) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public ByteBuf writeChar(int value) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public ByteBuf writeBytes(ByteBuf src) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public ByteBuf writeBytes(ByteBuf src, int length) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public ByteBuf writeBytes(byte[] src) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public ByteBuf writeBytes(byte[] src, int srcIndex, int length) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public ByteBuf writeBytes(ByteBuffer src) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public ByteBuf writeBytes(ByteBuf src, int srcIndex, int length) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public int refCnt() {
        return 1;
    }

    @Override
    public ReferenceCounted retain() {
        return this;
    }

    @Override
    public ReferenceCounted retain(int increment) {
        return this;
    }

    @Override
    public ReferenceCounted touch() {
        return null;
    }

    @Override
    public ReferenceCounted touch(Object hint) {
        return null;
    }

    @Override
    public boolean release() {
        return false;
    }

    @Override
    public boolean release(int decrement) {
        return false;
    }
}
