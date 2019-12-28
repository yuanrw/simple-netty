package com.simple.netty.buffer;

import com.simple.netty.common.internal.IllegalReferenceCountException;
import com.simple.netty.common.internal.PlatformDependent;
import com.simple.netty.common.internal.ReferenceCounted;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * 实现ByteBuf的引用计数
 * 跟踪某个特定对象的活动引用的数量，refCnf>0就不会被释放
 * <p>
 * Abstract base class for {@link ByteBuf} implementations that count references.
 * Date: 2019-12-14
 * Time: 12:37
 *
 * @author yrw
 */
public abstract class AbstractReferenceCountedByteBuf extends AbstractByteBuf {

    /**
     * 计数域的偏移地址
     */
    private static final long REFCNT_FIELD_OFFSET = getUnsafeOffset();

    /**
     * volatile可以保证属性可见，但不能保证原子性
     */
    private volatile int refCnt = 0;

    private static final AtomicIntegerFieldUpdater<AbstractReferenceCountedByteBuf> UPDATER =
        AtomicIntegerFieldUpdater.newUpdater(AbstractReferenceCountedByteBuf.class, "refCnt");

    protected AbstractReferenceCountedByteBuf(int maxCapacity) {
        super(maxCapacity);
    }

    @Override
    public int refCnt() {
        return 0;
    }

    @Override
    public ByteBuf touch() {
        return touch(null);
    }

    @Override
    public ByteBuf touch(Object hint) {
        return this;
    }

    @Override
    public ReferenceCounted retain() {
        UPDATER.getAndAdd(this, 1);
        return this;
    }

    @Override
    public ReferenceCounted retain(int increment) {
        UPDATER.getAndAdd(this, increment);
        return this;
    }

    @Override
    public boolean release() {
        int cnt = nonVolatileRawCnt();
        return cnt == 1 ? tryFinalRelease0(1) : nonFinalRelease0(1, cnt);
    }

    @Override
    public boolean release(int decrement) {
        int cnt = nonVolatileRawCnt();
        return decrement == cnt ? tryFinalRelease0(cnt) || retryRelease0(decrement)
            : nonFinalRelease0(decrement, cnt);
    }

    private boolean tryFinalRelease0(int expectRawCnt) {
        return UPDATER.compareAndSet(this, expectRawCnt, 0);
    }

    private boolean nonFinalRelease0(int decrement, int expectRawCnt) {
        if (decrement < expectRawCnt
            && UPDATER.compareAndSet(this, expectRawCnt, expectRawCnt - decrement)) {
            return false;
        }
        return retryRelease0(decrement);
    }

    private boolean retryRelease0(int decrement) {
        for (; ; ) {
            int cnt = UPDATER.get(this);
            if (decrement == cnt) {
                if (tryFinalRelease0(cnt)) {
                    return true;
                }
            } else if (decrement < cnt) {
                if (UPDATER.compareAndSet(this, cnt, cnt - decrement)) {
                    return false;
                }
            } else {
                throw new IllegalReferenceCountException(cnt, -decrement);
            }
            Thread.yield();
        }
    }

    private int nonVolatileRawCnt() {
        return REFCNT_FIELD_OFFSET != -1 ? PlatformDependent.getInt(this, REFCNT_FIELD_OFFSET)
            : UPDATER.get(this);
    }

    protected final void resetRefCnt() {
        UPDATER.set(this, 0);
    }

    private static long getUnsafeOffset() {
        try {
            if (PlatformDependent.hasUnsafe()) {
                return PlatformDependent.objectFieldOffset(AbstractReferenceCountedByteBuf.class.getDeclaredField("refCnt"));
            }
        } catch (Throwable ignore) {
            // fall-back
        }
        return -1;
    }
}
