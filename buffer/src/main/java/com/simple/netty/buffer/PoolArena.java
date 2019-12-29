package com.simple.netty.buffer;

import com.simple.netty.common.internal.LongCounter;
import com.simple.netty.common.internal.PlatformDependent;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.simple.netty.common.internal.ObjectUtil.checkPositiveOrZero;
import static java.lang.Math.max;

/**
 * 内存分配器
 * Date: 2019-12-18
 * Time: 20:19
 *
 * @author yrw
 */
public abstract class PoolArena<T> implements PoolArenaMetric {
    static final boolean HAS_UNSAFE = PlatformDependent.hasUnsafe();

    enum SizeClass {
        /**
         * 内存大小类型
         */

        Tiny,
        Small,
        Normal
    }

    /**
     * tiny的个数 32个
     */
    static final int numTinySubPagePools = 512 >>> 4;
    /**
     * small类型的内存等级: pageShifts - log(512) = 4,分别为[512, 1k, 2k, 4k]
     */
    final int numSmallSubPagePools;

    public final PooledByteBufAllocator parent;

    private final int maxOrder;
    final int pageSize;
    final int pageShifts;
    final int chunkSize;
    final int subPageOverflowMask;

    final int directMemoryCacheAlignment;
    final int directMemoryCacheAlignmentMask;

    /**
     * tiny类型分31个等级[16, 32, ..., 512], 每个等级都可以存放一个链(元素为PoolSubPage), 可存放未分配的该范围的内存块
     */
    private final PoolSubPage<T>[] tinySubPagePools;

    /**
     * small类型分31个等级[512, 1k, 2k, 4k], 每个等级都可以存放一个链(元素为PoolSubPage), 可存放未分配的该范围的内存块
     */
    private final PoolSubPage<T>[] smallSubPagePools;

    /**
     * normal，按照不同使用率来划分
     */
    private final PoolChunkList<T> q050;
    private final PoolChunkList<T> q025;
    private final PoolChunkList<T> q000;
    private final PoolChunkList<T> qInit;
    private final PoolChunkList<T> q075;
    private final PoolChunkList<T> q100;

    private final List<PoolChunkListMetric> chunkListMetrics;

    private final LongCounter allocationsTiny = PlatformDependent.newLongCounter();
    private final LongCounter allocationsSmall = PlatformDependent.newLongCounter();
    private final LongCounter allocationsHuge = PlatformDependent.newLongCounter();
    private final LongCounter activeBytesHuge = PlatformDependent.newLongCounter();

    private long allocationsNormal;
    private long deallocationsTiny;
    private long deallocationsSmall;
    private long deallocationsNormal;

    private final LongCounter deallocationsHuge = PlatformDependent.newLongCounter();

    /**
     * 该PoolArea被多少线程引用。
     */
    final AtomicInteger numThreadCaches = new AtomicInteger();

    protected PoolArena(PooledByteBufAllocator parent, int pageSize,
                        int maxOrder, int pageShifts, int chunkSize, int cacheAlignment) {
        this.parent = parent;
        this.pageSize = pageSize;
        this.maxOrder = maxOrder;
        this.pageShifts = pageShifts;
        this.chunkSize = chunkSize;
        directMemoryCacheAlignment = cacheAlignment;
        directMemoryCacheAlignmentMask = cacheAlignment - 1;
        subPageOverflowMask = -pageSize;
        tinySubPagePools = newSubPagePoolArray(numTinySubPagePools);
        for (int i = 0; i < tinySubPagePools.length; i++) {
            tinySubPagePools[i] = newSubPagePoolHead(pageSize);
        }

        numSmallSubPagePools = pageShifts - 9;
        smallSubPagePools = newSubPagePoolArray(numSmallSubPagePools);
        for (int i = 0; i < smallSubPagePools.length; i++) {
            smallSubPagePools[i] = newSubPagePoolHead(pageSize);
        }

        q100 = new PoolChunkList<>(this, null, 100, Integer.MAX_VALUE, chunkSize);
        q075 = new PoolChunkList<>(this, q100, 75, 100, chunkSize);
        q050 = new PoolChunkList<>(this, q075, 50, 100, chunkSize);
        q025 = new PoolChunkList<>(this, q050, 25, 75, chunkSize);
        q000 = new PoolChunkList<>(this, q025, 1, 50, chunkSize);
        qInit = new PoolChunkList<>(this, q000, Integer.MIN_VALUE, 25, chunkSize);

        q100.prevList(q075);
        q075.prevList(q050);
        q050.prevList(q025);
        q025.prevList(q000);
        q000.prevList(null);
        qInit.prevList(qInit);

        List<PoolChunkListMetric> metrics = new ArrayList<>(6);
        metrics.add(qInit);
        metrics.add(q000);
        metrics.add(q025);
        metrics.add(q050);
        metrics.add(q075);
        metrics.add(q100);
        chunkListMetrics = Collections.unmodifiableList(metrics);
    }

    private PoolSubPage<T> newSubPagePoolHead(int pageSize) {
        PoolSubPage<T> head = new PoolSubPage<>(pageSize);
        head.prev = head;
        head.next = head;
        return head;
    }

    @SuppressWarnings("unchecked")
    private PoolSubPage<T>[] newSubPagePoolArray(int size) {
        return new PoolSubPage[size];
    }

    abstract boolean isDirect();


    @Override
    public int numThreadCaches() {
        return numThreadCaches.get();
    }

    @Override
    public int numChunkLists() {
        return chunkListMetrics.size();
    }

    /**
     * 申请内存
     *
     * @param cache
     * @param reqCapacity
     * @param maxCapacity
     * @return
     */
    public PooledByteBuf<T> allocate(PoolThreadCache cache, int reqCapacity, int maxCapacity) {
        PooledByteBuf<T> buf = newByteBuf(maxCapacity);
        allocate(cache, buf, reqCapacity);
        return buf;
    }

    static int tinyIdx(int normCapacity) {
        //在tiny维护的链中找到合适自己位置的下标, 除以16，就是下标了
        return normCapacity >>> 4;
    }

    static int smallIdx(int normCapacity) {
        int tableIdx = 0;
        int i = normCapacity >>> 10;
        while (i != 0) {
            i >>>= 1;
            tableIdx++;
        }
        return tableIdx;
    }

    private boolean isTinyOrSmall(int normCapacity) {
        return (normCapacity & subPageOverflowMask) == 0;
    }

    private static boolean isTiny(int normCapacity) {
        return (normCapacity & 0xFFFFFE00) == 0;
    }

    /**
     * 首先尝试从cache中申请，若在cache中申请不到的话，接着会尝试从SubPagePools中申请
     *
     * @param cache
     * @param buf
     * @param reqCapacity
     */
    private void allocate(PoolThreadCache cache, PooledByteBuf<T> buf, final int reqCapacity) {
        final int normCapacity = normalizeCapacity(reqCapacity);

        if (isTinyOrSmall(normCapacity)) {
            // capacity < pageSize
            int tableIdx;
            PoolSubPage<T>[] table;
            boolean tiny = isTiny(normCapacity);
            if (tiny) {
                // < 512
                if (cache.allocateTiny(this, buf, reqCapacity, normCapacity)) {
                    //申请成功，结束
                    return;
                }
                //计算该内存在tinySubPagePool里的下标
                tableIdx = tinyIdx(normCapacity);
                table = tinySubPagePools;
            } else {
                if (cache.allocateSmall(this, buf, reqCapacity, normCapacity)) {
                    //申请成功，结束
                    return;
                }
                tableIdx = smallIdx(normCapacity);
                table = smallSubPagePools;
            }

            //根据下标，获取相应的节点
            final PoolSubPage<T> head = table[tableIdx];

            //分配的时候需要加锁
            synchronized (head) {
                final PoolSubPage<T> s = head.next;
                if (s != head) {
                    //head不是最后一个
                    assert s.doNotDestroy && s.elemSize == normCapacity;
                    long handle = s.allocate();
                    assert handle >= 0;
                    s.chunk.initBufWithSubPage(buf, handle, reqCapacity);

                    //增加计数
                    incTinySmallAllocation(tiny);
                    return;
                }
            }

            //如果subPage没有可用的，就从normal里分配，一个Arena有多个线程使用，需要加锁
            synchronized (this) {
                allocateNormal(buf, reqCapacity, normCapacity);
            }

            incTinySmallAllocation(tiny);
            return;
        }

        // > pageSize
        if (normCapacity <= chunkSize) {
            if (cache.allocateNormal(this, buf, reqCapacity, normCapacity)) {
                //分配成功，结束
                return;
            }
            synchronized (this) {
                allocateNormal(buf, reqCapacity, normCapacity);
                ++allocationsNormal;
            }
        } else {
            // > chunkSize，没有缓存，直接分配堆外内存
            allocateHuge(buf, reqCapacity);
        }
    }


    private void allocateNormal(PooledByteBuf<T> buf, int reqCapacity, int normCapacity) {
        if (q050.allocate(buf, reqCapacity, normCapacity) || q025.allocate(buf, reqCapacity, normCapacity) ||
            q000.allocate(buf, reqCapacity, normCapacity) || qInit.allocate(buf, reqCapacity, normCapacity) ||
            q075.allocate(buf, reqCapacity, normCapacity)) {
            return;
        }

        // 增加一个新的内存块
        PoolChunk<T> c = newChunk(pageSize, maxOrder, chunkSize);
        boolean success = c.allocate(buf, reqCapacity, normCapacity);
        assert success;

        //新内存块都放到qInit里面
        qInit.add(c);
    }

    private void incTinySmallAllocation(boolean tiny) {
        if (tiny) {
            allocationsTiny.increment();
        } else {
            allocationsSmall.increment();
        }
    }

    private void allocateHuge(PooledByteBuf<T> buf, int reqCapacity) {
        PoolChunk<T> chunk = newUnpooledChunk(reqCapacity);
        activeBytesHuge.add(chunk.chunkSize());
        buf.initUnpooled(chunk, reqCapacity);
        allocationsHuge.increment();
    }

    void free(PoolChunk<T> chunk, long handle, int normCapacity, PoolThreadCache cache) {
        if (chunk.unpooled) {
            //非池化
            int size = chunk.chunkSize();
            destroyChunk(chunk);
            activeBytesHuge.add(-size);
            deallocationsHuge.increment();
        } else {
            SizeClass sizeClass = sizeClass(normCapacity);
            if (cache != null && cache.add(this, chunk, handle, normCapacity, sizeClass)) {
                // cached so not free it.
                return;
            }

            freeChunk(chunk, handle, sizeClass, false);
        }
    }

    private SizeClass sizeClass(int normCapacity) {
        if (!isTinyOrSmall(normCapacity)) {
            return SizeClass.Normal;
        }
        return isTiny(normCapacity) ? SizeClass.Tiny : SizeClass.Small;
    }

    void freeChunk(PoolChunk<T> chunk, long handle, SizeClass sizeClass, boolean finalizer) {
        final boolean destroyChunk;
        synchronized (this) {
            // We only call this if freeChunk is not called because of the PoolThreadCache finalizer as otherwise this
            // may fail due lazy class-loading in for example tomcat.
            if (!finalizer) {
                switch (sizeClass) {
                    case Normal:
                        ++deallocationsNormal;
                        break;
                    case Small:
                        ++deallocationsSmall;
                        break;
                    case Tiny:
                        ++deallocationsTiny;
                        break;
                    default:
                        throw new Error();
                }
            }
            destroyChunk = !chunk.parent.free(chunk, handle);
        }
        if (destroyChunk) {
            // destroyChunk not need to be called while holding the synchronized lock.
            destroyChunk(chunk);
        }
    }

    /**
     * 把数字格式化成2的次幂，方便申请内存
     *
     * @param reqCapacity
     * @return
     */
    int normalizeCapacity(int reqCapacity) {
        checkPositiveOrZero(reqCapacity, "reqCapacity");

        if (reqCapacity >= chunkSize) {
            return directMemoryCacheAlignment == 0 ? reqCapacity : alignCapacity(reqCapacity);
        }

        if (!isTiny(reqCapacity)) {
            // >= 512
            // Doubled

            int normalizedCapacity = reqCapacity;
            normalizedCapacity--;
            normalizedCapacity |= normalizedCapacity >>> 1;
            normalizedCapacity |= normalizedCapacity >>> 2;
            normalizedCapacity |= normalizedCapacity >>> 4;
            normalizedCapacity |= normalizedCapacity >>> 8;
            normalizedCapacity |= normalizedCapacity >>> 16;
            normalizedCapacity++;

            if (normalizedCapacity < 0) {
                normalizedCapacity >>>= 1;
            }
            assert directMemoryCacheAlignment == 0 || (normalizedCapacity & directMemoryCacheAlignmentMask) == 0;

            return normalizedCapacity;
        }

        if (directMemoryCacheAlignment > 0) {
            return alignCapacity(reqCapacity);
        }

        // Quantum-spaced
        if ((reqCapacity & 15) == 0) {
            return reqCapacity;
        }

        return (reqCapacity & ~15) + 16;
    }

    int alignCapacity(int reqCapacity) {
        int delta = reqCapacity & directMemoryCacheAlignmentMask;
        return delta == 0 ? reqCapacity : reqCapacity + directMemoryCacheAlignment - delta;
    }

    @Override
    public int numTinySubPages() {
        return tinySubPagePools.length;
    }

    @Override
    public int numSmallSubPages() {
        return smallSubPagePools.length;
    }

    @Override
    public List<PoolSubPageMetric> tinySubPages() {
        return subPageMetricList(tinySubPagePools);
    }

    @Override
    public List<PoolSubPageMetric> smallSubPages() {
        return subPageMetricList(smallSubPagePools);
    }

    @Override
    public List<PoolChunkListMetric> chunkLists() {
        return chunkListMetrics;
    }

    private static List<PoolSubPageMetric> subPageMetricList(PoolSubPage<?>[] pages) {
        List<PoolSubPageMetric> metrics = new ArrayList<PoolSubPageMetric>();
        for (PoolSubPage<?> head : pages) {
            if (head.next == head) {
                continue;
            }
            PoolSubPage<?> s = head.next;
            for (; ; ) {
                metrics.add(s);
                s = s.next;
                if (s == head) {
                    break;
                }
            }
        }
        return metrics;
    }

    @Override
    public long numAllocations() {
        final long allocsNormal;
        synchronized (this) {
            allocsNormal = allocationsNormal;
        }
        return allocationsTiny.value() + allocationsSmall.value() + allocsNormal + allocationsHuge.value();
    }

    @Override
    public long numTinyAllocations() {
        return allocationsTiny.value();
    }

    @Override
    public long numSmallAllocations() {
        return allocationsSmall.value();
    }

    @Override
    public synchronized long numNormalAllocations() {
        return allocationsNormal;
    }

    @Override
    public long numDeallocations() {
        final long deallocs;
        synchronized (this) {
            deallocs = deallocationsTiny + deallocationsSmall + deallocationsNormal;
        }
        return deallocs + deallocationsHuge.value();
    }

    @Override
    public synchronized long numTinyDeallocations() {
        return deallocationsTiny;
    }

    @Override
    public synchronized long numSmallDeallocations() {
        return deallocationsSmall;
    }

    @Override
    public synchronized long numNormalDeallocations() {
        return deallocationsNormal;
    }

    @Override
    public long numHugeAllocations() {
        return allocationsHuge.value();
    }

    @Override
    public long numHugeDeallocations() {
        return deallocationsHuge.value();
    }

    @Override
    public long numActiveAllocations() {
        long val = allocationsTiny.value() + allocationsSmall.value() + allocationsHuge.value()
            - deallocationsHuge.value();
        synchronized (this) {
            val += allocationsNormal - (deallocationsTiny + deallocationsSmall + deallocationsNormal);
        }
        return max(val, 0);
    }

    @Override
    public long numActiveTinyAllocations() {
        return max(numTinyAllocations() - numTinyDeallocations(), 0);
    }

    @Override
    public long numActiveSmallAllocations() {
        return max(numSmallAllocations() - numSmallDeallocations(), 0);
    }

    @Override
    public long numActiveNormalAllocations() {
        final long val;
        synchronized (this) {
            val = allocationsNormal - deallocationsNormal;
        }
        return max(val, 0);
    }

    @Override
    public long numActiveHugeAllocations() {
        return max(numHugeAllocations() - numHugeDeallocations(), 0);
    }

    @Override
    public long numActiveBytes() {
        long val = activeBytesHuge.value();
        synchronized (this) {
            for (int i = 0; i < chunkListMetrics.size(); i++) {
                for (PoolChunkMetric m : chunkListMetrics.get(i)) {
                    val += m.chunkSize();
                }
            }
        }
        return max(0, val);
    }

    protected abstract PoolChunk<T> newChunk(int pageSize, int maxOrder, int chunkSize);
    protected abstract PoolChunk<T> newUnpooledChunk(int capacity);
    protected abstract PooledByteBuf<T> newByteBuf(int maxCapacity);
    protected abstract void destroyChunk(PoolChunk<T> chunk);

    @Override
    protected final void finalize() throws Throwable {
        try {
            super.finalize();
        } finally {
            destroyPoolSubPages(smallSubPagePools);
            destroyPoolSubPages(tinySubPagePools);
            destroyPoolChunkLists(qInit, q000, q025, q050, q075, q100);
        }
    }

    private static void destroyPoolSubPages(PoolSubPage<?>[] pages) {
        for (PoolSubPage<?> page : pages) {
            page.destroy();
        }
    }

    private void destroyPoolChunkLists(PoolChunkList<T>... chunkLists) {
        for (PoolChunkList<T> chunkList : chunkLists) {
            chunkList.destroy(this);
        }
    }

    PoolSubPage<T> findSubPagePoolHead(int elemSize) {
        int tableIdx;
        PoolSubPage<T>[] table;
        // < 512
        if (isTiny(elemSize)) {
            tableIdx = elemSize >>> 4;
            table = tinySubPagePools;
        } else {
            tableIdx = 0;
            elemSize >>>= 10;
            while (elemSize != 0) {
                elemSize >>>= 1;
                tableIdx++;
            }
            table = smallSubPagePools;
        }

        return table[tableIdx];
    }

    /**
     * 堆内存分配器
     */
    static final class HeapArena extends PoolArena<byte[]> {

        HeapArena(PooledByteBufAllocator parent, int pageSize, int maxOrder,
                  int pageShifts, int chunkSize, int directMemoryCacheAlignment) {
            super(parent, pageSize, maxOrder, pageShifts, chunkSize,
                directMemoryCacheAlignment);
        }

        private static byte[] newByteArray(int size) {
            return PlatformDependent.allocateUninitializedArray(size);
        }

        @Override
        boolean isDirect() {
            return false;
        }

        @Override
        protected PoolChunk<byte[]> newChunk(int pageSize, int maxOrder, int chunkSize) {
            return new PoolChunk<>(this, newByteArray(chunkSize), pageSize, maxOrder, chunkSize);
        }

        @Override
        protected PoolChunk<byte[]> newUnpooledChunk(int capacity) {
            return new PoolChunk<>(this, newByteArray(capacity), capacity);
        }

        @Override
        protected void destroyChunk(PoolChunk<byte[]> chunk) {
            // Rely on GC.
        }

        @Override
        protected PooledByteBuf<byte[]> newByteBuf(int maxCapacity) {
            return PooledHeapByteBuf.newInstance(maxCapacity);
        }

    }

    /**
     * 堆外内存分配器
     */
    static final class DirectArena extends PoolArena<ByteBuffer> {

        DirectArena(PooledByteBufAllocator parent, int pageSize, int maxOrder,
                    int pageShifts, int chunkSize, int directMemoryCacheAlignment) {
            super(parent, pageSize, maxOrder, pageShifts, chunkSize,
                directMemoryCacheAlignment);
        }

        @Override
        boolean isDirect() {
            return true;
        }

        private int offsetCacheLine(ByteBuffer memory) {
            int remainder = HAS_UNSAFE
                ? (int) (PlatformDependent.directBufferAddress(memory) & directMemoryCacheAlignmentMask)
                : 0;

            // offset = alignment - address & (alignment - 1)
            return directMemoryCacheAlignment - remainder;
        }

        @Override
        protected PoolChunk<ByteBuffer> newChunk(int pageSize, int maxOrder, int chunkSize) {
            if (directMemoryCacheAlignment == 0) {
                return new PoolChunk<>(this,
                    allocateDirect(chunkSize), pageSize, maxOrder,
                    chunkSize);
            }
            final ByteBuffer memory = allocateDirect(chunkSize
                + directMemoryCacheAlignment);
            return new PoolChunk<>(this, memory, pageSize,
                maxOrder, chunkSize);
        }

        @Override
        protected PoolChunk<ByteBuffer> newUnpooledChunk(int capacity) {
            if (directMemoryCacheAlignment == 0) {
                return new PoolChunk<>(this,
                    allocateDirect(capacity), capacity);
            }
            final ByteBuffer memory = allocateDirect(capacity
                + directMemoryCacheAlignment);
            return new PoolChunk<>(this, memory, capacity);
        }

        private static ByteBuffer allocateDirect(int capacity) {
            return PlatformDependent.useDirectBufferNoCleaner() ?
                PlatformDependent.allocateDirectNoCleaner(capacity) : ByteBuffer.allocateDirect(capacity);
        }

        @Override
        protected void destroyChunk(PoolChunk<ByteBuffer> chunk) {
            if (PlatformDependent.useDirectBufferNoCleaner()) {
                PlatformDependent.freeDirectNoCleaner(chunk.memory);
            } else {
                PlatformDependent.freeDirectBuffer(chunk.memory);
            }
        }

        @Override
        protected PooledByteBuf<ByteBuffer> newByteBuf(int maxCapacity) {
            return PooledDirectByteBuf.newInstance(maxCapacity);
        }
    }
}
