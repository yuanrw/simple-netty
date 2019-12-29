package com.simple.netty.buffer;

import com.simple.netty.common.internal.NettyRuntime;
import com.simple.netty.common.internal.PlatformDependent;

import java.nio.ByteBuffer;

/**
 * 池化分配器
 * Date: 2019-12-14
 * Time: 19:36
 *
 * @author yrw
 */
public class PooledByteBufAllocator extends AbstractByteBufAllocator {

    private static final int DEFAULT_NUM_HEAP_ARENA;
    private static final int DEFAULT_NUM_DIRECT_ARENA;

    private static final int DEFAULT_PAGE_SIZE;

    // 8192 << 11 = 16 MiB per chunk
    private static final int DEFAULT_MAX_ORDER;
    private static final int DEFAULT_TINY_CACHE_SIZE = 512;
    private static final int DEFAULT_SMALL_CACHE_SIZE = 256;
    private static final int DEFAULT_NORMAL_CACHE_SIZE = 64;
    private static final int DEFAULT_MAX_CACHED_BUFFER_CAPACITY = 32 * 1024;
    private static final int DEFAULT_DIRECT_MEMORY_CACHE_ALIGNMENT = 0;

    private static final int MIN_PAGE_SIZE = 4096;
    private static final int MAX_CHUNK_SIZE = (int) (((long) Integer.MAX_VALUE + 1) / 2);

    private PoolThreadLocalCache threadCache;

    static {
        DEFAULT_PAGE_SIZE = 8192;
        DEFAULT_MAX_ORDER = 11;

        final int defaultMinNumArena = NettyRuntime.availableProcessors() * 2;
        final int defaultChunkSize = DEFAULT_PAGE_SIZE << DEFAULT_MAX_ORDER;

        final Runtime runtime = Runtime.getRuntime();

        DEFAULT_NUM_HEAP_ARENA = Math.max(0, (int) Math.min(defaultMinNumArena,
            runtime.maxMemory() / defaultChunkSize / 2 / 3));
        DEFAULT_NUM_DIRECT_ARENA = Math.max(0, (int) Math.min(defaultMinNumArena,
            PlatformDependent.maxDirectMemory() / defaultChunkSize / 2 / 3));
    }

    private final PoolArena<byte[]>[] heapArenas;
    private final PoolArena<ByteBuffer>[] directArenas;
    private final int chunkSize;
    private final int tinyCacheSize;
    private final int smallCacheSize;
    private final int normalCacheSize;

    public PooledByteBufAllocator(boolean preferDirect) {
        this(preferDirect, DEFAULT_NUM_HEAP_ARENA, DEFAULT_NUM_DIRECT_ARENA, DEFAULT_PAGE_SIZE, DEFAULT_MAX_ORDER,
            DEFAULT_TINY_CACHE_SIZE, DEFAULT_SMALL_CACHE_SIZE, DEFAULT_NORMAL_CACHE_SIZE, DEFAULT_DIRECT_MEMORY_CACHE_ALIGNMENT);
    }

    public PooledByteBufAllocator(boolean preferDirect, int nHeapArena, int nDirectArena, int pageSize, int maxOrder,
                                  int tinyCacheSize, int smallCacheSize, int normalCacheSize, int directMemoryCacheAlignment) {
        super(preferDirect);
        this.threadCache = new PoolThreadLocalCache();
        this.tinyCacheSize = tinyCacheSize;
        this.smallCacheSize = smallCacheSize;
        this.normalCacheSize = normalCacheSize;
        this.chunkSize = validateAndCalculateChunkSize(pageSize, maxOrder);

        int pageShifts = validateAndCalculatePageShifts(pageSize);

        //初始化arena

        if (nHeapArena > 0) {
            heapArenas = newArenaArray(nHeapArena);
            for (int i = 0; i < heapArenas.length; i++) {
                PoolArena.HeapArena arena = new PoolArena.HeapArena(this,
                    pageSize, maxOrder, pageShifts, chunkSize,
                    directMemoryCacheAlignment);
                heapArenas[i] = arena;
            }
        } else {
            heapArenas = null;
        }

        if (nDirectArena > 0) {
            directArenas = newArenaArray(nDirectArena);
            for (int i = 0; i < directArenas.length; i++) {
                PoolArena.DirectArena arena = new PoolArena.DirectArena(
                    this, pageSize, maxOrder, pageShifts, chunkSize, directMemoryCacheAlignment);
                directArenas[i] = arena;
            }
        } else {
            directArenas = null;
        }
    }

    private static int validateAndCalculatePageShifts(int pageSize) {
        if (pageSize < MIN_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize: " + pageSize + " (expected: " + MIN_PAGE_SIZE + ")");
        }

        if ((pageSize & pageSize - 1) != 0) {
            throw new IllegalArgumentException("pageSize: " + pageSize + " (expected: power of 2)");
        }

        // Logarithm base 2. At this point we know that pageSize is a power of two.
        return Integer.SIZE - 1 - Integer.numberOfLeadingZeros(pageSize);
    }


    private static int validateAndCalculateChunkSize(int pageSize, int maxOrder) {
        if (maxOrder > 14) {
            throw new IllegalArgumentException("maxOrder: " + maxOrder + " (expected: 0-14)");
        }

        // Ensure the resulting chunkSize does not overflow.
        int chunkSize = pageSize;
        for (int i = maxOrder; i > 0; i--) {
            if (chunkSize > MAX_CHUNK_SIZE / 2) {
                throw new IllegalArgumentException(String.format(
                    "pageSize (%d) << maxOrder (%d) must not exceed %d", pageSize, maxOrder, MAX_CHUNK_SIZE));
            }
            chunkSize <<= 1;
        }
        return chunkSize;
    }

    @SuppressWarnings("unchecked")
    private static <T> PoolArena<T>[] newArenaArray(int size) {
        return new PoolArena[size];
    }

    @Override
    protected ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
        PoolThreadCache cache = threadCache.get();
        PoolArena<byte[]> heapArena = cache.heapArena;

        final ByteBuf buf;
        if (heapArena != null) {
            buf = heapArena.allocate(cache, initialCapacity, maxCapacity);
        } else {
            buf = new UnpooledHeapByteBuf(this, initialCapacity, maxCapacity);
        }

        return buf;
    }

    @Override
    protected ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
        PoolThreadCache cache = threadCache.get();
        PoolArena<ByteBuffer> directArena = cache.directArena;

        final ByteBuf buf;
        if (directArena != null) {
            buf = directArena.allocate(cache, initialCapacity, maxCapacity);
        } else {
            buf = new UnpooledDirectByteBuf(this, initialCapacity, maxCapacity);
        }

        return buf;
    }

    final PoolThreadCache threadCache() {
        PoolThreadCache cache = threadCache.get();
        assert cache != null;
        return cache;
    }

    @Override
    public boolean isDirectBufferPooled() {
        return false;
    }

    public final int chunkSize() {
        return chunkSize;
    }

    @Override
    public long usedHeapMemory() {
        return usedMemory(heapArenas);
    }

    @Override
    public long usedDirectMemory() {
        return usedMemory(directArenas);
    }

    private static long usedMemory(PoolArena<?>[] arenas) {
        if (arenas == null) {
            return -1;
        }
        long used = 0;
        for (PoolArena<?> arena : arenas) {
            used += arena.numActiveBytes();
            if (used < 0) {
                return Long.MAX_VALUE;
            }
        }
        return used;
    }

    public PoolArena<byte[]>[] getHeapArenas() {
        return heapArenas;
    }

    public PoolArena<ByteBuffer>[] getDirectArenas() {
        return directArenas;
    }

    /**
     * 把线程和PoolThreadCache绑定在一起，全局唯一
     * 任何线程分配内存，都会调用同一个PoolThreadLocalCache.get()获取PoolThreadCache
     */
    final class PoolThreadLocalCache extends ThreadLocal<PoolThreadCache> {
        @Override
        protected synchronized PoolThreadCache initialValue() {
            final PoolArena<byte[]> heapArena = leastUsedArena(heapArenas);
            final PoolArena<ByteBuffer> directArena = leastUsedArena(directArenas);
            return new PoolThreadCache(
                heapArena, directArena, tinyCacheSize, smallCacheSize, normalCacheSize,
                DEFAULT_MAX_CACHED_BUFFER_CAPACITY);
        }

        private <T> PoolArena<T> leastUsedArena(PoolArena<T>[] arenas) {
            if (arenas == null || arenas.length == 0) {
                return null;
            }

            PoolArena<T> minArena = arenas[0];
            for (int i = 1; i < arenas.length; i++) {
                PoolArena<T> arena = arenas[i];
                if (arena.numThreadCaches.get() < minArena.numThreadCaches.get()) {
                    minArena = arena;
                }
            }

            return minArena;
        }
    }
}
