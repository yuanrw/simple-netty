package com.simple.netty.buffer;

import com.simple.netty.common.internal.PlatformDependent;

/**
 * Date: 2019-12-14
 * Time: 12:37
 *
 * @author yrw
 */
public class UnpooledByteBufAllocator extends AbstractByteBufAllocator {

    public static final UnpooledByteBufAllocator DEFAULT =
        new UnpooledByteBufAllocator(PlatformDependent.directBufferPreferred());

    private final boolean noCleaner;

    public UnpooledByteBufAllocator(boolean preferDirect) {
        this(preferDirect, false);
    }

    @Override
    protected ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
        return null;
    }

    @Override
    protected ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
        return null;
    }

    public UnpooledByteBufAllocator(boolean preferDirect, boolean disableLeakDetector) {
        this(preferDirect, disableLeakDetector, PlatformDependent.useDirectBufferNoCleaner());
    }

    public UnpooledByteBufAllocator(boolean preferDirect, boolean disableLeakDetector, boolean tryNoCleaner) {
        super(preferDirect);
        noCleaner = tryNoCleaner && PlatformDependent.hasUnsafe()
            && PlatformDependent.hasDirectBufferNoCleanerConstructor();
    }

    @Override
    public boolean isDirectBufferPooled() {
        return false;
    }
}
