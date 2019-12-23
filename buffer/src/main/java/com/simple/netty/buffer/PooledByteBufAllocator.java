package com.simple.netty.buffer;

/**
 * 池化分配器
 * Date: 2019-12-14
 * Time: 19:36
 *
 * @author yrw
 */
public class PooledByteBufAllocator implements ByteBufAllocator {

    private PoolThreadLocalCache threadCache;

    final PoolThreadCache threadCache() {
        PoolThreadCache cache = threadCache.get();
        assert cache != null;
        return cache;
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

    /**
     * 把线程和PoolThreadCache绑定在一起，全局唯一
     * 任何线程分配内存，都会调用同一个PoolThreadLocalCache.get()获取PoolThreadCache
     */
    final class PoolThreadLocalCache extends ThreadLocal<PoolThreadCache> {
        @Override
        protected synchronized PoolThreadCache initialValue() {
            return null;
        }
    }
}
