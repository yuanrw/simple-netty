package com.simple.netty.buffer;

import com.simple.netty.buffer.allocator.ByteBufAllocator;

import java.nio.ByteBuffer;

/**
 * Date: 2019-12-14
 * Time: 12:38
 *
 * @author yrw
 */
public class PooledDirectByteBuf extends PooledByteBuf<ByteBuffer> {
    @Override
    public int capacity() {
        return 0;
    }

    @Override
    public int maxCapacity() {
        return 0;
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
        return null;
    }

    @Override
    public ByteBuf markReaderIndex() {
        return null;
    }

    @Override
    public ByteBuf resetReaderIndex() {
        return null;
    }

    @Override
    public ByteBuf markWriterIndex() {
        return null;
    }

    @Override
    public ByteBuf resetWriterIndex() {
        return null;
    }

    @Override
    public boolean getBoolean(int index) {
        return false;
    }

    @Override
    public byte getByte(int index) {
        return 0;
    }

    @Override
    public int getInt(int index) {
        return 0;
    }

    @Override
    public char getChar(int index) {
        return 0;
    }

    @Override
    public double getDouble(int index) {
        return 0;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst) {
        return null;
    }

    @Override
    public ByteBuf getBytes(int index, byte[] dst) {
        return null;
    }

    @Override
    public boolean readBoolean() {
        return false;
    }

    @Override
    public byte readByte() {
        return 0;
    }

    @Override
    public int readInt() {
        return 0;
    }

    @Override
    public char readChar() {
        return 0;
    }

    @Override
    public double readDouble() {
        return 0;
    }

    @Override
    public ByteBuf readBytes(int length) {
        return null;
    }

    @Override
    public ByteBuf readSlice(int length) {
        return null;
    }

    @Override
    public ByteBuf readRetainedSlice(int length) {
        return null;
    }

    @Override
    public ByteBuf readBytes(ByteBuf dst) {
        return null;
    }

    @Override
    public ByteBuf readBytes(byte[] dst) {
        return null;
    }

    @Override
    public ByteBuf readBytes(ByteBuffer dst) {
        return null;
    }

    @Override
    public ByteBuf writeBoolean(boolean value) {
        return null;
    }

    @Override
    public ByteBuf writeByte(int value) {
        return null;
    }

    @Override
    public ByteBuf writeShortLE(int value) {
        return null;
    }

    @Override
    public ByteBuf writeInt(int value) {
        return null;
    }

    @Override
    public ByteBuf writeChar(int value) {
        return null;
    }

    @Override
    public ByteBuf writeBytes(ByteBuf src) {
        return null;
    }

    @Override
    public ByteBuf writeBytes(byte[] src) {
        return null;
    }

    @Override
    public ByteBuf writeBytes(ByteBuffer src) {
        return null;
    }
}
