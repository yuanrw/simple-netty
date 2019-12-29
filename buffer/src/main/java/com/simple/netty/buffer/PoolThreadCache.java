package com.simple.netty.buffer;

import com.simple.netty.common.internal.MathUtil;
import com.simple.netty.common.internal.ObjectPool;
import com.simple.netty.common.internal.PlatformDependent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * NioEventLoop在为数据分配存放的内存时，首先尝试从线程本地缓存申请，失败才从全局内存中申请，
 * 本地缓存的管理者就是PoolThreadCache对象
 * Date: 2019-12-18
 * Time: 20:22
 *
 * @author yrw
 */
public class PoolThreadCache {

    private static final Logger logger = LoggerFactory.getLogger(PoolThreadCache.class);

    final PoolArena<byte[]> heapArena;
    final PoolArena<ByteBuffer> directArena;

    /**
     * 各个区的缓存
     */
    private final MemoryRegionCache<byte[]>[] tinySubPageHeapCaches;
    private final MemoryRegionCache<byte[]>[] smallSubPageHeapCaches;
    private final MemoryRegionCache<ByteBuffer>[] tinySubPageDirectCaches;
    private final MemoryRegionCache<ByteBuffer>[] smallSubPageDirectCaches;
    private final MemoryRegionCache<byte[]>[] normalHeapCaches;
    private final MemoryRegionCache<ByteBuffer>[] normalDirectCaches;

    private final int numShiftsNormalDirect;
    private final int numShiftsNormalHeap;
    private final AtomicBoolean freed = new AtomicBoolean();

    PoolThreadCache(PoolArena<byte[]> heapArena, PoolArena<ByteBuffer> directArena,
                    int tinyCacheSize, int smallCacheSize, int normalCacheSize,
                    int maxCachedBufferCapacity) {
        this.heapArena = heapArena;
        this.directArena = directArena;
        if (directArena != null) {
            tinySubPageDirectCaches = createSubPageCaches(
                tinyCacheSize, PoolArena.numTinySubPagePools, PoolArena.SizeClass.Tiny);
            smallSubPageDirectCaches = createSubPageCaches(
                smallCacheSize, directArena.numSmallSubPagePools, PoolArena.SizeClass.Small);

            numShiftsNormalDirect = log2(directArena.pageSize);
            normalDirectCaches = createNormalCaches(
                normalCacheSize, maxCachedBufferCapacity, directArena);

            directArena.numThreadCaches.getAndIncrement();
        } else {
            // 没有directArea
            tinySubPageDirectCaches = null;
            smallSubPageDirectCaches = null;
            normalDirectCaches = null;
            numShiftsNormalDirect = -1;
        }
        if (heapArena != null) {
            tinySubPageHeapCaches = createSubPageCaches(
                tinyCacheSize, PoolArena.numTinySubPagePools, PoolArena.SizeClass.Tiny);
            smallSubPageHeapCaches = createSubPageCaches(
                smallCacheSize, heapArena.numSmallSubPagePools, PoolArena.SizeClass.Small);

            numShiftsNormalHeap = log2(heapArena.pageSize);
            normalHeapCaches = createNormalCaches(
                normalCacheSize, maxCachedBufferCapacity, heapArena);

            heapArena.numThreadCaches.getAndIncrement();
        } else {
            // No heapArea is configured so just null out all caches
            tinySubPageHeapCaches = null;
            smallSubPageHeapCaches = null;
            normalHeapCaches = null;
            numShiftsNormalHeap = -1;
        }
    }

    private static <T> MemoryRegionCache<T>[] createSubPageCaches(
        int cacheSize, int numCaches, PoolArena.SizeClass sizeClass) {
        if (cacheSize > 0 && numCaches > 0) {
            @SuppressWarnings("unchecked")
            MemoryRegionCache<T>[] cache = new MemoryRegionCache[numCaches];
            for (int i = 0; i < cache.length; i++) {
                cache[i] = new SubPageMemoryRegionCache<T>(cacheSize, sizeClass);
            }
            return cache;
        } else {
            return null;
        }
    }

    private static <T> MemoryRegionCache<T>[] createNormalCaches(
        int cacheSize, int maxCachedBufferCapacity, PoolArena<T> area) {
        if (cacheSize > 0 && maxCachedBufferCapacity > 0) {
            int max = Math.min(area.chunkSize, maxCachedBufferCapacity);
            int arraySize = Math.max(1, log2(max / area.pageSize) + 1);

            @SuppressWarnings("unchecked")
            MemoryRegionCache<T>[] cache = new MemoryRegionCache[arraySize];
            for (int i = 0; i < cache.length; i++) {
                cache[i] = new NormalMemoryRegionCache<T>(cacheSize);
            }
            return cache;
        } else {
            return null;
        }
    }

    private static int log2(int val) {
        int res = 0;
        while (val > 1) {
            val >>= 1;
            res++;
        }
        return res;
    }

    /**
     * 分配一个tiny级别的内存
     */
    boolean allocateTiny(PoolArena<?> area, PooledByteBuf<?> buf, int reqCapacity, int normCapacity) {
        return allocate(cacheForTiny(area, normCapacity), buf, reqCapacity);
    }

    /**
     * 分配一个small级别的内存
     */
    boolean allocateSmall(PoolArena<?> area, PooledByteBuf<?> buf, int reqCapacity, int normCapacity) {
        return allocate(cacheForSmall(area, normCapacity), buf, reqCapacity);
    }

    /**
     * 分配一个normal级别的内存
     */
    boolean allocateNormal(PoolArena<?> area, PooledByteBuf<?> buf, int reqCapacity, int normCapacity) {
        return allocate(cacheForNormal(area, normCapacity), buf, reqCapacity);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean allocate(MemoryRegionCache<?> cache, PooledByteBuf buf, int reqCapacity) {
        if (cache == null) {
            // no cache found so just return false here
            return false;
        }
        return cache.allocate(buf, reqCapacity);
    }

    /**
     * Add {@link PoolChunk} and {@code handle} to the cache if there is enough room.
     * Returns {@code true} if it fit into the cache {@code false} otherwise.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    boolean add(PoolArena<?> area, PoolChunk chunk,
                long handle, int normCapacity, PoolArena.SizeClass sizeClass) {
        MemoryRegionCache<?> cache = cache(area, normCapacity, sizeClass);
        if (cache == null) {
            return false;
        }
        return cache.add(chunk, handle);
    }

    private MemoryRegionCache<?> cache(PoolArena<?> area, int normCapacity, PoolArena.SizeClass sizeClass) {
        switch (sizeClass) {
            case Normal:
                return cacheForNormal(area, normCapacity);
            case Small:
                return cacheForSmall(area, normCapacity);
            case Tiny:
                return cacheForTiny(area, normCapacity);
            default:
                throw new Error();
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            super.finalize();
        } finally {
            free(true);
        }
    }

    /**
     * Should be called if the Thread that uses this cache is about to exist to release resources out of the cache
     */
    void free(boolean finalizer) {
        // As free() may be called either by the finalizer or by FastThreadLocal.onRemoval(...) we need to ensure
        // we only call this one time.
        if (freed.compareAndSet(false, true)) {
            int numFreed = free(tinySubPageDirectCaches, finalizer) +
                free(smallSubPageDirectCaches, finalizer) +
                free(normalDirectCaches, finalizer) +
                free(tinySubPageHeapCaches, finalizer) +
                free(smallSubPageHeapCaches, finalizer) +
                free(normalHeapCaches, finalizer);

            if (numFreed > 0 && logger.isDebugEnabled()) {
                logger.debug("Freed {} thread-local buffer(s) from thread: {}", numFreed,
                    Thread.currentThread().getName());
            }

            if (directArena != null) {
                directArena.numThreadCaches.getAndDecrement();
            }

            if (heapArena != null) {
                heapArena.numThreadCaches.getAndDecrement();
            }
        }
    }

    private static int free(MemoryRegionCache<?>[] caches, boolean finalizer) {
        if (caches == null) {
            return 0;
        }

        int numFreed = 0;
        for (MemoryRegionCache<?> c : caches) {
            numFreed += free(c, finalizer);
        }
        return numFreed;
    }

    private static int free(MemoryRegionCache<?> cache, boolean finalizer) {
        if (cache == null) {
            return 0;
        }
        return cache.free(finalizer);
    }

    private MemoryRegionCache<?> cacheForTiny(PoolArena<?> area, int normCapacity) {
        int idx = PoolArena.tinyIdx(normCapacity);
        if (area.isDirect()) {
            return cache(tinySubPageDirectCaches, idx);
        }
        return cache(tinySubPageHeapCaches, idx);
    }

    private MemoryRegionCache<?> cacheForSmall(PoolArena<?> area, int normCapacity) {
        int idx = PoolArena.smallIdx(normCapacity);
        if (area.isDirect()) {
            return cache(smallSubPageDirectCaches, idx);
        }
        return cache(smallSubPageHeapCaches, idx);
    }

    private MemoryRegionCache<?> cacheForNormal(PoolArena<?> area, int normCapacity) {
        if (area.isDirect()) {
            int idx = log2(normCapacity >> numShiftsNormalDirect);
            return cache(normalDirectCaches, idx);
        }
        int idx = log2(normCapacity >> numShiftsNormalHeap);
        return cache(normalHeapCaches, idx);
    }

    private static <T> MemoryRegionCache<T> cache(MemoryRegionCache<T>[] cache, int idx) {
        if (cache == null || idx > cache.length - 1) {
            return null;
        }
        return cache[idx];
    }

    /**
     * Cache used for buffers which are backed by TINY or SMALL size.
     */
    private static final class SubPageMemoryRegionCache<T> extends MemoryRegionCache<T> {
        SubPageMemoryRegionCache(int size, PoolArena.SizeClass sizeClass) {
            super(size, sizeClass);
        }

        @Override
        protected void initBuf(
            PoolChunk<T> chunk, long handle, PooledByteBuf<T> buf, int reqCapacity) {
            chunk.initBufWithSubPage(buf, handle, reqCapacity);
        }
    }

    /**
     * Cache used for buffers which are backed by NORMAL size.
     */
    private static final class NormalMemoryRegionCache<T> extends MemoryRegionCache<T> {
        NormalMemoryRegionCache(int size) {
            super(size, PoolArena.SizeClass.Normal);
        }

        @Override
        protected void initBuf(
            PoolChunk<T> chunk, long handle, PooledByteBuf<T> buf, int reqCapacity) {
            chunk.initBuf(buf, handle, reqCapacity);
        }
    }

    /**
     * 缓存的实现
     *
     * @param <T>
     */
    private abstract static class MemoryRegionCache<T> {
        /**
         * 内部用一个队列实现
         */
        private final int size;
        private final Queue<Entry<T>> queue;
        private final PoolArena.SizeClass sizeClass;

        MemoryRegionCache(int size, PoolArena.SizeClass sizeClass) {
            this.size = MathUtil.safeFindNextPositivePowerOfTwo(size);
            queue = PlatformDependent.newFixedMpscQueue(this.size);
            this.sizeClass = sizeClass;
        }

        /**
         * 初始化{@link PooledByteBuf}
         */
        protected abstract void initBuf(PoolChunk<T> chunk, long handle, PooledByteBuf<T> buf, int reqCapacity);

        @SuppressWarnings("unchecked")
        public final boolean add(PoolChunk<T> chunk, long handle) {
            Entry<T> entry = newEntry(chunk, handle);
            boolean queued = queue.offer(entry);
            if (!queued) {
                //如果放不进去了，就回收掉
                entry.recycle();
            }
            return true;
        }

        /**
         * 取出一个ByteBuf
         */
        public final boolean allocate(PooledByteBuf<T> buf, int reqCapacity) {
            Entry<T> entry = queue.poll();
            if (entry == null) {
                return false;
            }
            initBuf(entry.chunk, entry.handle, buf, reqCapacity);
            entry.recycle();

            return true;
        }

        /**
         * 清空缓存
         */
        public final int free(boolean finalizer) {
            return free(Integer.MAX_VALUE, finalizer);
        }

        private int free(int max, boolean finalizer) {
            int numFreed = 0;
            for (; numFreed < max; numFreed++) {
                Entry<T> entry = queue.poll();
                if (entry != null) {
                    freeEntry(entry, finalizer);
                } else {
                    // all cleared
                    return numFreed;
                }
            }
            return numFreed;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private void freeEntry(Entry entry, boolean finalizer) {
            PoolChunk chunk = entry.chunk;
            long handle = entry.handle;

            if (!finalizer) {
                //此方法不是在finalizer中被调用的时候才执行，否则就代表马上要被gc了
                entry.recycle();
            }

            chunk.arena.freeChunk(chunk, handle, sizeClass, finalizer);
        }

        static final class Entry<T> {
            final Consumer<Entry<?>> recycleHandler;
            PoolChunk<T> chunk;
            long handle = -1;

            void recycle() {
                chunk = null;
                handle = -1;
            }

            Entry(Consumer<Entry<?>> recycleHandler) {
                this.recycleHandler = recycleHandler;
            }
        }

        private static Entry newEntry(PoolChunk<?> chunk, long handle) {
            Entry entry = RECYCLER.get();
            entry.chunk = chunk;
            entry.handle = handle;
            return entry;
        }

        @SuppressWarnings("unchecked")
        private static final ObjectPool<Entry> RECYCLER = new ObjectPool<>(handler -> new Entry(handler));
    }
}
