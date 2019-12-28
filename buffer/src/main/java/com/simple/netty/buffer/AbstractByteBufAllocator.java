package com.simple.netty.buffer;

import com.simple.netty.common.internal.PlatformDependent;

import static com.simple.netty.common.internal.ObjectUtil.checkPositiveOrZero;

/**
 * ByteBufAllocator的基本实现
 * Date: 2019-12-15
 * Time: 11:47
 *
 * @author yrw
 */
public abstract class AbstractByteBufAllocator implements ByteBufAllocator {

    /**
     * 默认容量
     */
    static final int DEFAULT_INITIAL_CAPACITY = 256;

    /**
     * 默认最大容量
     */
    static final int DEFAULT_MAX_CAPACITY = Integer.MAX_VALUE;
    static final int DEFAULT_MAX_COMPONENTS = 16;
    // 4 MiB page
    static final int CALCULATE_THRESHOLD = 1048576 * 4;

    private final boolean directByDefault;
    private final ByteBuf emptyBuf;

    /**
     * 默认使用heap buffers
     */
    protected AbstractByteBufAllocator() {
        this(false);
    }

    /**
     * Create new instance
     *
     * @param preferDirect true尝试使用堆外内存，false使用堆内内存
     */
    protected AbstractByteBufAllocator(boolean preferDirect) {
        directByDefault = preferDirect && PlatformDependent.hasUnsafe();
        emptyBuf = new EmptyByteBuf(this);
    }

    @Override
    public ByteBuf buffer() {
        if (directByDefault) {
            return directBuffer();
        }
        return heapBuffer();
    }

    @Override
    public ByteBuf buffer(int initialCapacity) {
        if (directByDefault) {
            return directBuffer(initialCapacity);
        }
        return heapBuffer(initialCapacity);
    }

    @Override
    public ByteBuf buffer(int initialCapacity, int maxCapacity) {
        if (directByDefault) {
            return directBuffer(initialCapacity, maxCapacity);
        }
        return heapBuffer(initialCapacity, maxCapacity);
    }

    @Override
    public ByteBuf heapBuffer() {
        return heapBuffer(DEFAULT_INITIAL_CAPACITY, DEFAULT_MAX_CAPACITY);
    }

    @Override
    public ByteBuf heapBuffer(int initialCapacity) {
        return heapBuffer(initialCapacity, DEFAULT_MAX_CAPACITY);
    }

    @Override
    public ByteBuf heapBuffer(int initialCapacity, int maxCapacity) {
        if (initialCapacity == 0 && maxCapacity == 0) {
            return emptyBuf;
        }
        validate(initialCapacity, maxCapacity);
        return newHeapBuffer(initialCapacity, maxCapacity);
    }

    @Override
    public ByteBuf directBuffer() {
        return directBuffer(DEFAULT_INITIAL_CAPACITY, DEFAULT_MAX_CAPACITY);
    }

    @Override
    public ByteBuf directBuffer(int initialCapacity) {
        return directBuffer(initialCapacity, DEFAULT_MAX_CAPACITY);
    }

    @Override
    public ByteBuf directBuffer(int initialCapacity, int maxCapacity) {
        if (initialCapacity == 0 && maxCapacity == 0) {
            return emptyBuf;
        }
        validate(initialCapacity, maxCapacity);
        return newDirectBuffer(initialCapacity, maxCapacity);
    }

    private static void validate(int initialCapacity, int maxCapacity) {
        checkPositiveOrZero(initialCapacity, "initialCapacity");
        if (initialCapacity > maxCapacity) {
            throw new IllegalArgumentException(String.format(
                "initialCapacity: %d (expected: not greater than maxCapacity(%d)",
                initialCapacity, maxCapacity));
        }
    }

    /**
     * 创建堆{@link ByteBuf}
     */
    protected abstract ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity);

    /**
     * 创建堆外{@link ByteBuf}
     */
    protected abstract ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity);

    @Override
    public int calculateNewCapacity(int minNewCapacity, int maxCapacity) {
        checkPositiveOrZero(minNewCapacity, "minNewCapacity");
        if (minNewCapacity > maxCapacity) {
            throw new IllegalArgumentException(String.format(
                "minNewCapacity: %d (expected: not greater than maxCapacity(%d)",
                minNewCapacity, maxCapacity));
        }

        // 4 MiB page
        final int threshold = CALCULATE_THRESHOLD;

        if (minNewCapacity == threshold) {
            return threshold;
        }

        // 大于threshold，不翻倍，只是增加threshold
        if (minNewCapacity > threshold) {
            int newCapacity = minNewCapacity / threshold * threshold;
            if (newCapacity > maxCapacity - threshold) {
                newCapacity = maxCapacity;
            } else {
                newCapacity += threshold;
            }
            return newCapacity;
        }

        // 从64开始不断翻倍
        int newCapacity = 64;
        while (newCapacity < minNewCapacity) {
            newCapacity <<= 1;
        }

        //不能超过threshold
        return Math.min(newCapacity, maxCapacity);
    }
}
