package com.simple.netty.buffer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Date: 2019-12-28
 * Time: 12:47
 *
 * @author yrw
 */
public abstract class ByteBufAllocatorTest {

    protected abstract int defaultMaxCapacity();

    protected abstract ByteBufAllocator newAllocator(boolean preferDirect);

    @Test
    public void testBuffer() {
        testBuffer(true);
        testBuffer(false);
    }

    private void testBuffer(boolean preferDirect) {
        ByteBufAllocator allocator = newAllocator(preferDirect);
        ByteBuf buffer = allocator.buffer(1);
        try {
            assertBuffer(buffer, isDirectExpected(preferDirect), 1, defaultMaxCapacity());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testBufferWithCapacity() {
        testBufferWithCapacity(true, 8);
        testBufferWithCapacity(false, 8);
    }

    private void testBufferWithCapacity(boolean preferDirect, int maxCapacity) {
        ByteBufAllocator allocator = newAllocator(preferDirect);
        ByteBuf buffer = allocator.buffer(1, maxCapacity);
        try {
            assertBuffer(buffer, isDirectExpected(preferDirect), 1, maxCapacity);
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testHeapBuffer() {
        testHeapBuffer(true);
        testHeapBuffer(false);
    }

    private void testHeapBuffer(boolean preferDirect) {
        ByteBufAllocator allocator = newAllocator(preferDirect);
        ByteBuf buffer = allocator.heapBuffer(1);
        try {
            assertBuffer(buffer, false, 1, defaultMaxCapacity());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testHeapBufferMaxCapacity() {
        testHeapBuffer(true, 8);
        testHeapBuffer(false, 8);
    }

    private void testHeapBuffer(boolean preferDirect, int maxCapacity) {
        ByteBufAllocator allocator = newAllocator(preferDirect);
        ByteBuf buffer = allocator.heapBuffer(1, maxCapacity);
        try {
            assertBuffer(buffer, false, 1, maxCapacity);
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testDirectBuffer() {
        testDirectBuffer(true);
        testDirectBuffer(false);
    }

    private void testDirectBuffer(boolean preferDirect) {
        ByteBufAllocator allocator = newAllocator(preferDirect);
        ByteBuf buffer = allocator.directBuffer(1);
        try {
            assertBuffer(buffer, true, 1, defaultMaxCapacity());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testDirectBufferMaxCapacity() {
        testDirectBuffer(true, 8);
        testDirectBuffer(false, 8);
    }

    private void testDirectBuffer(boolean preferDirect, int maxCapacity) {
        ByteBufAllocator allocator = newAllocator(preferDirect);
        ByteBuf buffer = allocator.directBuffer(1, maxCapacity);
        try {
            assertBuffer(buffer, true, 1, maxCapacity);
        } finally {
            buffer.release();
        }
    }

    protected abstract boolean isDirectExpected(boolean preferDirect);

    private static void assertBuffer(
        ByteBuf buffer, boolean expectedDirect, int expectedCapacity, int expectedMaxCapacity) {
        assertEquals(expectedDirect, buffer.isDirect());
        assertEquals(expectedCapacity, buffer.capacity());
        assertEquals(expectedMaxCapacity, buffer.maxCapacity());
    }
}
