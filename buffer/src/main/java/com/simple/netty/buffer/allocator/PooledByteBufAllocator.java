package com.simple.netty.buffer.allocator;

import com.simple.netty.buffer.ByteBuf;
import com.simple.netty.buffer.CompositeByteBuf;

/**
 * Date: 2019-12-14
 * Time: 12:36
 *
 * @author yrw
 */
public class PooledByteBufAllocator implements ByteBufAllocator {
    public ByteBuf buffer() {
        return null;
    }

    public ByteBuf buffer(int initialCapacity) {
        return null;
    }

    public ByteBuf buffer(int initialCapacity, int maxCapacity) {
        return null;
    }

    public ByteBuf heapBuffer() {
        return null;
    }

    public ByteBuf heapBuffer(int initialCapacity) {
        return null;
    }

    public ByteBuf heapBuffer(int initialCapacity, int maxCapacity) {
        return null;
    }

    public ByteBuf directBuffer() {
        return null;
    }

    public ByteBuf directBuffer(int initialCapacity) {
        return null;
    }

    public ByteBuf directBuffer(int initialCapacity, int maxCapacity) {
        return null;
    }

    public CompositeByteBuf compositeBuffer() {
        return null;
    }

    public CompositeByteBuf compositeBuffer(int maxNumComponents) {
        return null;
    }

    public CompositeByteBuf compositeHeapBuffer() {
        return null;
    }

    public CompositeByteBuf compositeHeapBuffer(int maxNumComponents) {
        return null;
    }

    public CompositeByteBuf compositeDirectBuffer() {
        return null;
    }

    public CompositeByteBuf compositeDirectBuffer(int maxNumComponents) {
        return null;
    }

    public boolean isDirectBufferPooled() {
        return false;
    }

    public int calculateNewCapacity(int minNewCapacity, int maxCapacity) {
        return 0;
    }
}
