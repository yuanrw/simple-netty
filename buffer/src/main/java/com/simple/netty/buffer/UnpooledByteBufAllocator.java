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

    public UnpooledByteBufAllocator(boolean preferDirect, boolean disableLeakDetector) {
        this(preferDirect, disableLeakDetector, PlatformDependent.useDirectBufferNoCleaner());
    }

    public UnpooledByteBufAllocator(boolean preferDirect, boolean disableLeakDetector, boolean tryNoCleaner) {
        super(preferDirect);
        noCleaner = tryNoCleaner && PlatformDependent.hasUnsafe()
            && PlatformDependent.hasDirectBufferNoCleanerConstructor();
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
}
