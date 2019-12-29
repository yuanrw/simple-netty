package com.simple.netty.buffer;

/**
 * Date: 2019-12-29
 * Time: 16:05
 *
 * @author yrw
 */
public class PooledDirectByteBufTest extends AbstractByteBufTest {

    @Override
    protected ByteBuf newBuffer(int length, int maxCapacity) {
        return PooledByteBufAllocator.DEFAULT.directBuffer(length, maxCapacity);
    }
}
