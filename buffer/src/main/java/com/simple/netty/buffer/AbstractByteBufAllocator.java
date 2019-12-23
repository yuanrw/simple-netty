package com.simple.netty.buffer;

import com.simple.netty.common.internal.PlatformDependent;

/**
 * Date: 2019-12-15
 * Time: 11:47
 *
 * @author yrw
 */
public class AbstractByteBufAllocator implements ByteBufAllocator {

    private final boolean directByDefault;

    protected AbstractByteBufAllocator(boolean preferDirect) {
        directByDefault = preferDirect && PlatformDependent.hasUnsafe();
    }

    @Override
    public ByteBuf buffer() {
        return null;
    }

    @Override
    public ByteBuf buffer(int initialCapacity) {
        return null;
    }

    @Override
    public ByteBuf buffer(int initialCapacity, int maxCapacity) {
        return null;
    }

    @Override
    public ByteBuf heapBuffer() {
        return null;
    }

    @Override
    public ByteBuf heapBuffer(int initialCapacity) {
        return null;
    }

    @Override
    public ByteBuf heapBuffer(int initialCapacity, int maxCapacity) {
        return null;
    }

    @Override
    public ByteBuf directBuffer() {
        return null;
    }

    @Override
    public ByteBuf directBuffer(int initialCapacity) {
        return null;
    }

    @Override
    public ByteBuf directBuffer(int initialCapacity, int maxCapacity) {
        return null;
    }

    @Override
    public CompositeByteBuf compositeBuffer() {
        return null;
    }

    @Override
    public CompositeByteBuf compositeBuffer(int maxNumComponents) {
        return null;
    }

    @Override
    public CompositeByteBuf compositeHeapBuffer() {
        return null;
    }

    @Override
    public CompositeByteBuf compositeHeapBuffer(int maxNumComponents) {
        return null;
    }

    @Override
    public CompositeByteBuf compositeDirectBuffer() {
        return null;
    }

    @Override
    public CompositeByteBuf compositeDirectBuffer(int maxNumComponents) {
        return null;
    }

    @Override
    public boolean isDirectBufferPooled() {
        return false;
    }

    @Override
    public int calculateNewCapacity(int minNewCapacity, int maxCapacity) {
        return 0;
    }
}
