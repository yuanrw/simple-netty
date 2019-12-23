package com.simple.netty.buffer;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 内存块
 * page - 最小的内存单位，根据不同的操作系统而不同，一般是4kb
 * chunk - 一堆page的集合
 * chunkSize = 2^{maxOrder} * pageSize
 * 一次申请16m内存，通过二叉树管理
 * <p>
 * memoryMap[id] = x => 表示以id为跟的子树里， 第一个可以分配的空闲节点的深度是x
 * （深度从0开始），每次分配或者释放节点都会更新这个值。
 * 初始化：memoryMap[id] = depth_of_id
 * <p>
 * Date: 2019-12-18
 * Time: 20:19
 *
 * @author yrw
 */
public class PoolChunk<T> implements PoolChunkMetric {

    public final PoolArena<T> arena;
    public final T memory;
    public final int offset;
    public final boolean unpooled;

    /**
     * 记录二叉树的可用状态，会更新
     */
    private final byte[] memoryMap;

    /**
     * 记录节点的深度，这个不会变
     */
    private final byte[] depthMap;
    private final PoolSubPage<T>[] subPages;

    private final int pageSize;
    private final int maxOrder;
    private final int chunkSize;
    private final int log2ChunkSize;
    private final int subPageOverflowMask;

    private final int pageShifts;

    /**
     * 标记不可用
     */
    private final byte unusable;

    /**
     * 缓存
     */
    private final Deque<ByteBuffer> cachedNioBuffers;
    private int freeBytes;
    private final int maxSubPageAllocs;

    PoolChunkList<T> parent;
    PoolChunk<T> prev;
    PoolChunk<T> next;

    public PoolChunk(PoolArena<T> arena, T memory, int offset, int pageShifts, int pageSize, int maxOrder, int chunkSize) {
        this.arena = arena;
        this.memory = memory;
        this.offset = offset;
        this.pageShifts = pageShifts;
        this.pageSize = pageSize;
        this.maxOrder = maxOrder;
        this.chunkSize = chunkSize;
        this.log2ChunkSize = log2(chunkSize);
        this.unpooled = false;

        assert maxOrder < 30 : "maxOrder should be < 30, but is: " + maxOrder;
        maxSubPageAllocs = 1 << maxOrder;
        unusable = (byte) (maxOrder + 1);
        subPageOverflowMask = -pageSize;

        //初始化树
        memoryMap = new byte[maxSubPageAllocs << 1];
        depthMap = new byte[memoryMap.length];
        //节点下标
        int memoryMapIndex = 1;
        //遍历树的层次
        for (int d = 0; d <= maxOrder; d++) {
            //当前层的节点数
            int number = 1 << d;
            for (int p = 0; p < number; p++) {
                memoryMap[memoryMapIndex] = (byte) d;
                depthMap[memoryMapIndex] = (byte) d;
                memoryMapIndex++;
            }
        }

        subPages = new PoolSubPage[maxSubPageAllocs];
        this.cachedNioBuffers = new ArrayDeque<>(8);
    }

    /**
     * Creates a special chunk that is not pooled.
     */
    PoolChunk(PoolArena<T> arena, T memory, int size, int offset) {
        unpooled = true;
        this.arena = arena;
        this.memory = memory;
        this.offset = offset;
        memoryMap = null;
        depthMap = null;
        subPages = null;
        subPageOverflowMask = 0;
        pageSize = 0;
        pageShifts = 0;
        maxOrder = 0;
        unusable = (byte) (maxOrder + 1);
        chunkSize = size;
        log2ChunkSize = log2(chunkSize);
        maxSubPageAllocs = 0;
        cachedNioBuffers = null;
    }

    @Override
    public int usage() {
        final int freeBytes;
        synchronized (arena) {
            freeBytes = this.freeBytes;
        }
        return usage(freeBytes);
    }

    private int usage(int freeBytes) {
        if (freeBytes == 0) {
            return 100;
        }

        int freePercentage = (int) (freeBytes * 100L / chunkSize);
        if (freePercentage == 0) {
            return 99;
        }
        return 100 - freePercentage;
    }

    @Override
    public int chunkSize() {
        return chunkSize;
    }

    @Override
    public int freeBytes() {
        synchronized (arena) {
            return freeBytes;
        }
    }

    /**
     * 分配内存
     *
     * @param buf          终点
     * @param reqCapacity  请求大小
     * @param normCapacity 格式化后的大小
     * @return
     */
    boolean allocate(PooledByteBuf<T> buf, int reqCapacity, int normCapacity) {
        final long handle;

        // >= pageSize
        if ((normCapacity & subPageOverflowMask) != 0) {
            handle = allocateRun(normCapacity);
        } else {
            handle = allocateSubPage(normCapacity);
        }

        //获取bitmap index
        if (handle < 0) {
            return false;
        }

        ByteBuffer nioBuffer = cachedNioBuffers != null ? cachedNioBuffers.pollLast() : null;
        initBuf(buf, nioBuffer, handle, reqCapacity);
        return true;
    }

    /**
     * 获取一个可用节点（一个chunk）
     * 中序遍历树找到第一个memory(id)=h的节点
     *
     * @param d 深度
     * @return 索引
     */
    private int allocateNode(int d) {
        int id = 1;
        // has last d bits = 0 and rest all = 1
        int initial = -(1 << d);
        byte val = value(id);
        // unusable
        if (val > d) {
            return -1;
        }
        // id & initial == 1 << d for all ids at depth d, for < d it is 0
        while (val < d || (id & initial) == 0) {
            id <<= 1;
            val = value(id);
            if (val > d) {
                id ^= 1;
                val = value(id);
            }
        }
        byte value = value(id);
        assert value == d && (id & initial) == 1 << d : String.format("val = %d, id & initial = %d, d = %d",
            value, id & initial, d);
        // mark as unusable
        setValue(id, unusable);
        updateParentsAlloc(id);
        return id;
    }

    /**
     * 分配一些pages(>=1)，少于一个chunk
     * 找到第一个大于normCapacity的chunk
     *
     * @param normCapacity 请求大小
     * @return 节点索引
     */
    private long allocateRun(int normCapacity) {
        //根据请求大小计算出节点深度
        int d = maxOrder - (log2(normCapacity) - pageShifts);

        //取一个可用节点
        int id = allocateNode(d);
        if (id < 0) {
            return id;
        }

        freeBytes -= runLength(id);
        return id;
    }

    /**
     * 分配小于pageSize的内存
     *
     * @param normCapacity
     * @return
     */
    private long allocateSubPage(int normCapacity) {
        // Obtain the head of the PoolSubPage pool that is owned by the PoolArena and synchronize on it.
        // This is need as we may add it back and so alter the linked-list structure.
        PoolSubPage<T> head = arena.findSubPagePoolHead(normCapacity);
        // subpages are only be allocated from pages i.e., leaves
        int d = maxOrder;
        synchronized (head) {
            int id = allocateNode(d);
            if (id < 0) {
                return id;
            }

            final PoolSubPage<T>[] subpages = this.subPages;
            final int pageSize = this.pageSize;

            freeBytes -= pageSize;

            int subPageIdx = subPageIdx(id);
            PoolSubPage<T> subpage = subpages[subPageIdx];
            if (subpage == null) {
                subpage = new PoolSubPage<T>(head, this, id, runOffset(id), pageSize, normCapacity);
                subpages[subPageIdx] = subpage;
            } else {
                subpage.init(head, normCapacity);
            }
            return subpage.allocate();
        }
    }

    private static int log2(int val) {
        return Integer.SIZE - 1 - Integer.numberOfLeadingZeros(val);
    }

    private byte value(int id) {
        return memoryMap[id];
    }

    private void setValue(int id, byte val) {
        memoryMap[id] = val;
    }

    private int subPageIdx(int memoryMapIdx) {
        // remove highest set bit, to get offset
        return memoryMapIdx ^ maxSubPageAllocs;
    }

    /**
     * Update method used by allocate
     * This is triggered only when a successor is allocated and all its predecessors
     * need to update their state
     * The minimal depth at which subtree rooted at id has some free space
     *
     * @param id id
     */
    private void updateParentsAlloc(int id) {
        while (id > 1) {
            int parentId = id >>> 1;
            byte val1 = value(id);
            byte val2 = value(id ^ 1);
            byte val = val1 < val2 ? val1 : val2;
            setValue(parentId, val);
            id = parentId;
        }
    }

    void initBuf(PooledByteBuf<T> buf, ByteBuffer nioBuffer, long handle, int reqCapacity) {
        int memoryMapIdx = memoryMapIdx(handle);
        int bitmapIdx = bitmapIdx(handle);
        if (bitmapIdx == 0) {
            byte val = value(memoryMapIdx);
            assert val == unusable : String.valueOf(val);
            buf.init(this, nioBuffer, handle, runOffset(memoryMapIdx) + offset,
                reqCapacity, runLength(memoryMapIdx), arena.parent.threadCache());
        } else {
            initBufWithSubPage(buf, nioBuffer, handle, bitmapIdx, reqCapacity);
        }
    }

    /**
     * 获取低32位，属于哪个page
     *
     * @param handle page索引
     * @return
     */
    private static int memoryMapIdx(long handle) {
        return (int) handle;
    }

    /**
     * 获取高32位，属于PoolSubPage里的哪个子内存块
     *
     * @param handle 内存索引
     * @return
     */
    private static int bitmapIdx(long handle) {
        return (int) (handle >>> Integer.SIZE);
    }

    private int runLength(int id) {
        // represents the size in #bytes supported by node 'id' in the tree
        return 1 << log2ChunkSize - depth(id);
    }

    private byte depth(int id) {
        return depthMap[id];
    }

    private int runOffset(int id) {
        // represents the 0-based offset in #bytes from start of the byte-array chunk
        int shift = id ^ 1 << depth(id);
        return shift * runLength(id);
    }

    void initBufWithSubPage(PooledByteBuf<T> buf, ByteBuffer nioBuffer, long handle, int reqCapacity) {
        initBufWithSubPage(buf, nioBuffer, handle, bitmapIdx(handle), reqCapacity);
    }

    private void initBufWithSubPage(PooledByteBuf<T> buf, ByteBuffer nioBuffer,
                                    long handle, int bitmapIdx, int reqCapacity) {
        assert bitmapIdx != 0;

        int memoryMapIdx = memoryMapIdx(handle);

        PoolSubPage<T> subpage = subPages[subPageIdx(memoryMapIdx)];
        assert subpage.doNotDestroy;
        assert reqCapacity <= subpage.elemSize;

        buf.init(
            this, nioBuffer, handle,
            runOffset(memoryMapIdx) + (bitmapIdx & 0x3FFFFFFF) * subpage.elemSize + offset,
            reqCapacity, subpage.elemSize, arena.parent.threadCache());
    }

    public void free(long handle, ByteBuffer nioBuffer) {

    }

    void destroy() {
        arena.destroyChunk(this);
    }
}
