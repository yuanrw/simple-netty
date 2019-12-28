package com.simple.netty.buffer;

import com.simple.netty.common.internal.LongCounter;
import com.simple.netty.common.internal.PlatformDependent;

import java.nio.ByteBuffer;

/**
 * Date: 2019-12-14
 * Time: 12:37
 *
 * @author yrw
 */
public class UnpooledByteBufAllocator extends AbstractByteBufAllocator {

    final LongCounter directCounter = PlatformDependent.newLongCounter();
    final LongCounter heapCounter = PlatformDependent.newLongCounter();

    public UnpooledByteBufAllocator(boolean preferDirect) {
        super(preferDirect);
    }

    @Override
    protected ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
        return new InstrumentedUnpooledHeapByteBuf(this, initialCapacity, maxCapacity);
    }

    @Override
    protected ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
        return new InstrumentedUnpooledDirectByteBuf(this, initialCapacity, maxCapacity);
    }

    @Override
    public boolean isDirectBufferPooled() {
        return false;
    }

    void incrementDirect(int amount) {
        directCounter.add(amount);
    }

    void decrementDirect(int amount) {
        directCounter.add(-amount);
    }

    void incrementHeap(int amount) {
        heapCounter.add(amount);
    }

    void decrementHeap(int amount) {
        heapCounter.add(-amount);
    }

    @Override
    public long usedHeapMemory() {
        return heapCounter.value();
    }

    @Override
    public long usedDirectMemory() {
        return directCounter.value();
    }

    private static final class InstrumentedUnpooledHeapByteBuf extends UnpooledHeapByteBuf {
        InstrumentedUnpooledHeapByteBuf(UnpooledByteBufAllocator alloc, int initialCapacity, int maxCapacity) {
            super(alloc, initialCapacity, maxCapacity);
        }

        @Override
        protected byte[] allocateArray(int initialCapacity) {
            byte[] bytes = super.allocateArray(initialCapacity);
            ((UnpooledByteBufAllocator) alloc()).incrementHeap(bytes.length);
            return bytes;
        }

        @Override
        protected void freeArray(byte[] array) {
            int length = array.length;
            super.freeArray(array);
            ((UnpooledByteBufAllocator) alloc()).decrementHeap(length);
        }
    }

    private static final class InstrumentedUnpooledDirectByteBuf extends UnpooledDirectByteBuf {
        InstrumentedUnpooledDirectByteBuf(
            UnpooledByteBufAllocator alloc, int initialCapacity, int maxCapacity) {
            super(alloc, initialCapacity, maxCapacity);
        }

        @Override
        protected ByteBuffer allocateDirect(int initialCapacity) {
            ByteBuffer buffer = super.allocateDirect(initialCapacity);
            ((UnpooledByteBufAllocator) alloc()).incrementDirect(buffer.capacity());
            return buffer;
        }

        @Override
        protected void freeDirect(ByteBuffer buffer) {
            int capacity = buffer.capacity();
            super.freeDirect(buffer);
            ((UnpooledByteBufAllocator) alloc()).decrementDirect(capacity);
        }
    }
}
