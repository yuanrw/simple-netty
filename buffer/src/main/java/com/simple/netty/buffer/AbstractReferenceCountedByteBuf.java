package com.simple.netty.buffer;

import com.simple.netty.common.internal.IllegalReferenceCountException;
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
     * volatile可以保证属性可见，但不能保证原子性
     */
    private volatile int refCnt = 1;

    /**
     * 更新器，保证原子性，所有ByteBuf共享
     */
    private static final AtomicIntegerFieldUpdater<AbstractReferenceCountedByteBuf> UPDATER =
        AtomicIntegerFieldUpdater.newUpdater(AbstractReferenceCountedByteBuf.class, "refCnt");

    protected AbstractReferenceCountedByteBuf(int maxCapacity) {
        super(maxCapacity);
    }

    @Override
    public int refCnt() {
        return UPDATER.get(this);
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
        return retain0(this, 1);
    }

    @Override
    public ReferenceCounted retain(int increment) {
        return retain0(this, increment);
    }

    private AbstractReferenceCountedByteBuf retain0(AbstractReferenceCountedByteBuf instance, final int increment) {
        //内部通过cas实现，修改成功才会返回
        int oldRef = UPDATER.getAndAdd(instance, increment);
        //旧值不合法
        if (oldRef == 0 || oldRef == Integer.MAX_VALUE) {
            throw new IllegalReferenceCountException(oldRef, 1);
        }
        return instance;
    }

    @Override
    public boolean release() {
        //release关心旧值，因为决定了是否释放对象。retain只要旧值合法就行
        int cnt = nonVolatileRawCnt();
        //引用的最小值是1，释放时等于1代表
        return cnt == 1 ? tryFinalRelease0(1) || retryRelease0(1)
            : nonFinalRelease0(1, cnt);
    }

    @Override
    public boolean release(int decrement) {
        int cnt = nonVolatileRawCnt();
        return decrement == cnt ? tryFinalRelease0(cnt) || retryRelease0(decrement)
            : nonFinalRelease0(decrement, cnt);
    }

    private boolean tryFinalRelease0(int expectRawCnt) {
        return handleRelease(UPDATER.compareAndSet(this, expectRawCnt, 0));
    }

    private boolean nonFinalRelease0(int decrement, int expectRawCnt) {
        if (decrement < expectRawCnt
            && UPDATER.compareAndSet(this, expectRawCnt, expectRawCnt - decrement)) {
            return false;
        }
        return handleRelease(retryRelease0(decrement));
    }

    private boolean retryRelease0(int decrement) {
        //自旋保证成功
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
        return UPDATER.get(this);
    }

    private boolean handleRelease(boolean result) {
        if (result) {
            deallocate();
        }
        return result;
    }

    /**
     * 当refCnt()=0时，进行释放
     */
    protected abstract void deallocate();

    protected final void resetRefCnt() {
        UPDATER.set(this, 1);
    }
}
