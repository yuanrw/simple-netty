package com.simple.netty.buffer;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Date: 2019-12-28
 * Time: 12:46
 *
 * @author yrw
 */
public class PooledByteBufAllocatorTest extends AbstractByteBufAllocatorTest<PooledByteBufAllocator> {

    @Override
    protected PooledByteBufAllocator newAllocator(boolean preferDirect) {
        return new PooledByteBufAllocator(preferDirect);
    }

    @Override
    protected PooledByteBufAllocator newUnpooledAllocator() {
        return new PooledByteBufAllocator(false);
    }

    @Override
    protected long expectedUsedMemory(PooledByteBufAllocator allocator, int capacity) {
        return allocator.chunkSize();
    }

    @Override
    protected long expectedUsedMemoryAfterRelease(PooledByteBufAllocator allocator, int capacity) {
        return allocator.chunkSize();
    }

    @Test
    public void testAllocNotNull() {
        PooledByteBufAllocator allocator = new PooledByteBufAllocator(true, 1, 1,
            8192, 11, 0, 0, 0, 0);
        // Huge allocation
        testAllocNotNull(allocator, allocator.chunkSize() + 1);
        // Normal allocation
        testAllocNotNull(allocator, 1024);
        // Small allocation
        testAllocNotNull(allocator, 512);
        // Tiny allocation
        testAllocNotNull(allocator, 1);
    }

    private static void testAllocNotNull(PooledByteBufAllocator allocator, int capacity) {
        ByteBuf buffer = allocator.heapBuffer(capacity);
        assertNotNull(buffer.alloc());
        assertTrue(buffer.release());
        assertNotNull(buffer.alloc());
    }
}
