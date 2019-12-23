package com.simple.netty.buffer;

import com.simple.netty.common.internal.ObjectPool;

/**
 * Date: 2019-12-20
 * Time: 20:04
 *
 * @author yrw
 */
public class PooledUnsafeDirectByteBuf extends PooledDirectByteBuf {

    protected PooledUnsafeDirectByteBuf(int maxCapacity) {
        super(maxCapacity);
    }

    private static final ObjectPool<PooledUnsafeDirectByteBuf> RECYCLER = ObjectPool.newPool(
        () -> new PooledUnsafeDirectByteBuf(0));

    static PooledUnsafeDirectByteBuf newInstance(int maxCapacity) {
        PooledUnsafeDirectByteBuf buf = RECYCLER.get();
        buf.reuse(maxCapacity);
        return buf;
    }

}
