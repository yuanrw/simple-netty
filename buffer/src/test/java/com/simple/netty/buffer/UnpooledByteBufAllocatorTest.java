package com.simple.netty.buffer;

/**
 * Date: 2019-12-28
 * Time: 12:47
 *
 * @author yrw
 */
public class UnpooledByteBufAllocatorTest extends AbstractByteBufAllocatorTest {

    @Override
    protected AbstractByteBufAllocator newAllocator(boolean preferDirect) {
        return new UnpooledByteBufAllocator(preferDirect);
    }
}
