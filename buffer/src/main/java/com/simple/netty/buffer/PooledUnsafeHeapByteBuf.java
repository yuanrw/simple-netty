package com.simple.netty.buffer;

import com.simple.netty.common.internal.ObjectPool;

/**
 * Date: 2019-12-20
 * Time: 20:04
 *
 * @author yrw
 */
public class PooledUnsafeHeapByteBuf extends PooledHeapByteBuf {

    private static final ObjectPool<PooledUnsafeHeapByteBuf> RECYCLER = ObjectPool.newPool(
        () -> new PooledUnsafeHeapByteBuf(0));

    protected PooledUnsafeHeapByteBuf(int maxCapacity) {
        super(maxCapacity);
    }

    static PooledUnsafeHeapByteBuf newUnsafeInstance(int maxCapacity) {
        PooledUnsafeHeapByteBuf buf = RECYCLER.get();
        buf.reuse(maxCapacity);
        return buf;
    }
}
