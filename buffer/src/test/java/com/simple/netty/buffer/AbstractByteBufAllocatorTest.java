package com.simple.netty.buffer;

import com.simple.netty.common.internal.PlatformDependent;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Date: 2019-12-28
 * Time: 12:47
 *
 * @author yrw
 */
public abstract class AbstractByteBufAllocatorTest<T extends AbstractByteBufAllocator> extends ByteBufAllocatorTest {

    @Override
    protected abstract T newAllocator(boolean preferDirect);

    protected abstract T newUnpooledAllocator();

    @Override
    protected boolean isDirectExpected(boolean preferDirect) {
        return preferDirect && PlatformDependent.hasUnsafe();
    }

    @Override
    protected final int defaultMaxCapacity() {
        return AbstractByteBufAllocator.DEFAULT_MAX_CAPACITY;
    }

    @Test
    public void testCalculateNewCapacity() {
        testCalculateNewCapacity(true);
        testCalculateNewCapacity(false);
    }

    private void testCalculateNewCapacity(boolean preferDirect) {
        T allocator = newAllocator(preferDirect);
        assertEquals(8, allocator.calculateNewCapacity(1, 8));
        assertEquals(7, allocator.calculateNewCapacity(1, 7));
        assertEquals(64, allocator.calculateNewCapacity(1, 129));
        assertEquals(AbstractByteBufAllocator.CALCULATE_THRESHOLD,
            allocator.calculateNewCapacity(AbstractByteBufAllocator.CALCULATE_THRESHOLD,
                AbstractByteBufAllocator.CALCULATE_THRESHOLD + 1));
        assertEquals(AbstractByteBufAllocator.CALCULATE_THRESHOLD * 2,
            allocator.calculateNewCapacity(AbstractByteBufAllocator.CALCULATE_THRESHOLD + 1,
                AbstractByteBufAllocator.CALCULATE_THRESHOLD * 4));
        try {
            allocator.calculateNewCapacity(8, 7);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            allocator.calculateNewCapacity(-1, 8);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testUsedDirectMemory() {
        T allocator = newAllocator(true);
        assertEquals(0, allocator.usedDirectMemory());
        ByteBuf buffer = allocator.directBuffer(1024, 4096);
        int capacity = buffer.capacity();
        assertEquals(expectedUsedMemory(allocator, capacity), allocator.usedDirectMemory());

        // Double the size of the buffer
        buffer.capacity(capacity << 1);
        capacity = buffer.capacity();
        assertEquals(expectedUsedMemory(allocator, capacity), allocator.usedDirectMemory());

        buffer.release();
        assertEquals(expectedUsedMemoryAfterRelease(allocator, capacity), allocator.usedDirectMemory());
    }

    @Test
    public void testUsedHeapMemory() {
        T allocator = newAllocator(true);
        assertEquals(0, allocator.usedHeapMemory());
        ByteBuf buffer = allocator.heapBuffer(1024, 4096);
        int capacity = buffer.capacity();
        assertEquals(expectedUsedMemory(allocator, capacity), allocator.usedHeapMemory());

        // Double the size of the buffer
        buffer.capacity(capacity << 1);
        capacity = buffer.capacity();
        assertEquals(expectedUsedMemory(allocator, capacity), allocator.usedHeapMemory());

        buffer.release();
        assertEquals(expectedUsedMemoryAfterRelease(allocator, capacity), allocator.usedHeapMemory());
    }

    protected long expectedUsedMemory(T allocator, int capacity) {
        return capacity;
    }

    protected long expectedUsedMemoryAfterRelease(T allocator, int capacity) {
        return 0;
    }
}
