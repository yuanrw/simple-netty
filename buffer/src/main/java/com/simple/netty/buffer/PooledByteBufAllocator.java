package com.simple.netty.buffer;

import com.simple.netty.common.internal.NettyRuntime;
import com.simple.netty.common.internal.PlatformDependent;
import com.simple.netty.common.internal.SystemPropertyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

/**
 * 池化分配器
 * Date: 2019-12-14
 * Time: 19:36
 *
 * @author yrw
 */
public class PooledByteBufAllocator extends AbstractByteBufAllocator {
    private static final Logger logger = LoggerFactory.getLogger(PooledByteBufAllocator.class);

    private static final int DEFAULT_NUM_HEAP_ARENA;
    private static final int DEFAULT_NUM_DIRECT_ARENA;

    private static final int DEFAULT_PAGE_SIZE;

    // 8192 << 11 = 16 MiB per chunk
    private static final int DEFAULT_MAX_ORDER;
    private static final int DEFAULT_TINY_CACHE_SIZE = 512;
    private static final int DEFAULT_SMALL_CACHE_SIZE = 256;
    private static final int DEFAULT_NORMAL_CACHE_SIZE = 64;
    private static final int DEFAULT_MAX_CACHED_BUFFER_CAPACITY = 32 * 1024;
    private static final int DEFAULT_CACHE_TRIM_INTERVAL = 8192;
    private static final long DEFAULT_CACHE_TRIM_INTERVAL_MILLIS = 0;
    private static final boolean DEFAULT_USE_CACHE_FOR_ALL_THREADS = true;
    private static final int DEFAULT_DIRECT_MEMORY_CACHE_ALIGNMENT = 0;
    static final int DEFAULT_MAX_CACHED_BYTEBUFFERS_PER_CHUNK = 1023;

    private static final int MIN_PAGE_SIZE = 4096;
    private static final int MAX_CHUNK_SIZE = (int) (((long) Integer.MAX_VALUE + 1) / 2);

    private PoolThreadLocalCache threadCache;

    static {
        int defaultPageSize = SystemPropertyUtil.getInt("io.netty.allocator.pageSize", 8192);
        Throwable pageSizeFallbackCause = null;
        try {
            validateAndCalculatePageShifts(defaultPageSize);
        } catch (Throwable t) {
            pageSizeFallbackCause = t;
            defaultPageSize = 8192;
        }
        DEFAULT_PAGE_SIZE = defaultPageSize;

        int defaultMaxOrder = SystemPropertyUtil.getInt("io.netty.allocator.maxOrder", 11);
        Throwable maxOrderFallbackCause = null;
        try {
            validateAndCalculateChunkSize(DEFAULT_PAGE_SIZE, defaultMaxOrder);
        } catch (Throwable t) {
            maxOrderFallbackCause = t;
            defaultMaxOrder = 11;
        }
        DEFAULT_MAX_ORDER = defaultMaxOrder;

        final int defaultMinNumArena = NettyRuntime.availableProcessors() * 2;
        final int defaultChunkSize = DEFAULT_PAGE_SIZE << DEFAULT_MAX_ORDER;
        final Runtime runtime = Runtime.getRuntime();

        DEFAULT_NUM_HEAP_ARENA = Math.max(0, (int) Math.min(defaultMinNumArena,
            runtime.maxMemory() / defaultChunkSize / 2 / 3));
        DEFAULT_NUM_DIRECT_ARENA = Math.max(0, (int) Math.min(defaultMinNumArena,
            PlatformDependent.maxDirectMemory() / defaultChunkSize / 2 / 3));
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

    protected PooledByteBufAllocator(boolean preferDirect) {
        super(preferDirect);
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

    /**
     * 把线程和PoolThreadCache绑定在一起，全局唯一
     * 任何线程分配内存，都会调用同一个PoolThreadLocalCache.get()获取PoolThreadCache
     */
    final class PoolThreadLocalCache extends ThreadLocal<PoolThreadCache> {
        @Override
        protected synchronized PoolThreadCache initialValue() {
            return null;
        }
    }
}
