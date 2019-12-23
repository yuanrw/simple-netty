package com.simple.netty.buffer;

import com.simple.netty.common.internal.ReferenceCountUpdater;
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
    private static final long REFCNT_FIELD_OFFSET =
        ReferenceCountUpdater.getUnsafeOffset(AbstractReferenceCountedByteBuf.class, "refCnt");

    private static final AtomicIntegerFieldUpdater<AbstractReferenceCountedByteBuf> AIF_UPDATER =
        AtomicIntegerFieldUpdater.newUpdater(AbstractReferenceCountedByteBuf.class, "refCnt");

    private static final ReferenceCountUpdater<AbstractReferenceCountedByteBuf> updater =
        new ReferenceCountUpdater<AbstractReferenceCountedByteBuf>() {
            @Override
            protected AtomicIntegerFieldUpdater<AbstractReferenceCountedByteBuf> updater() {
                return AIF_UPDATER;
            }

            @Override
            protected long unsafeOffset() {
                return REFCNT_FIELD_OFFSET;
            }
        };

    /**
     * volatile可以保证属性可见，但不能保证原子性
     */
    private volatile int refCnt = UPDATER.initialValue();

    /**
     * 计数器
     */
    private static final ReferenceCountUpdater<AbstractReferenceCountedByteBuf> UPDATER =
        new ReferenceCountUpdater<AbstractReferenceCountedByteBuf>() {
            @Override
            protected AtomicIntegerFieldUpdater<AbstractReferenceCountedByteBuf> updater() {
                return AIF_UPDATER;
            }

            @Override
            protected long unsafeOffset() {
                return REFCNT_FIELD_OFFSET;
            }
        };

    protected AbstractReferenceCountedByteBuf(int maxCapacity) {
        super(maxCapacity);
    }

    @Override
    public ReferenceCounted retain() {
        return UPDATER.retain(this);
    }

    @Override
    public ReferenceCounted retain(int increment) {
        return UPDATER.retain(this, increment);
    }

    @Override
    public boolean release() {
        return UPDATER.release(this);
    }

    @Override
    public boolean release(int decrement) {
        return UPDATER.release(this, decrement);
    }

    final void setIndex0(int readerIndex, int writerIndex) {
        this.readerIndex = readerIndex;
        this.writerIndex = writerIndex;
    }

    protected final void resetRefCnt() {
        updater.resetRefCnt(this);
    }
}
