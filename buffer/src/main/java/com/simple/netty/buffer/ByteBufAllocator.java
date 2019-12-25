package com.simple.netty.buffer;

/**
 * Date: 2019-12-14
 * Time: 12:36
 *
 * @author yrw
 */
public interface ByteBufAllocator {

    ByteBuf buffer();

    ByteBuf buffer(int initialCapacity);

    ByteBuf buffer(int initialCapacity, int maxCapacity);

    ByteBuf heapBuffer();

    ByteBuf heapBuffer(int initialCapacity);

    ByteBuf heapBuffer(int initialCapacity, int maxCapacity);

    ByteBuf directBuffer();

    ByteBuf directBuffer(int initialCapacity);

    ByteBuf directBuffer(int initialCapacity, int maxCapacity);

    CompositeByteBuf compositeBuffer();

    CompositeByteBuf compositeBuffer(int maxNumComponents);

    CompositeByteBuf compositeHeapBuffer();

    CompositeByteBuf compositeHeapBuffer(int maxNumComponents);

    CompositeByteBuf compositeDirectBuffer();

    CompositeByteBuf compositeDirectBuffer(int maxNumComponents);

    boolean isDirectBufferPooled();

    int calculateNewCapacity(int minNewCapacity, int maxCapacity);
}
