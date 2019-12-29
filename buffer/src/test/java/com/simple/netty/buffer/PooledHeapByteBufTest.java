package com.simple.netty.buffer;

/**
 * Date: 2019-12-29
 * Time: 16:06
 *
 * @author yrw
 */
public class PooledHeapByteBufTest extends AbstractByteBufTest {

    @Override
    protected ByteBuf newBuffer(int length, int maxCapacity) {
        return PooledByteBufAllocator.DEFAULT.heapBuffer(length, maxCapacity);
    }
}
