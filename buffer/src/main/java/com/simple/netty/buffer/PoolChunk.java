package com.simple.netty.buffer;

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
    public final boolean unpooled;

    /**
     * 记录内存二叉树的可用状态，会更新
     */
    private final byte[] memoryMap;

    /**
     * 记录节点的深度，这个不会变
     */
    private final byte[] depthMap;

    /**
     * 叶子节点，默认2048个，一个8k
     */
    private final PoolSubPage<T>[] subPages;

    /**
     * 每个叶子节点的大小
     */
    private final int pageSize;

    /**
     * 树的深度
     */
    private final int maxOrder;

    /**
     * 这个chunk的内存大小，默认16mb
     */
    private final int chunkSize;
    private final int log2ChunkSize;

    /**
     * 用于判断内存时否小于pageSize
     */
    private final int subPageOverflowMask;

    /**
     * 标记不可用
     */
    private final byte unusable;

    private int freeBytes;

    /**
     * PoolChunk由maxSubPageAllocs个PoolSubPage组成, 默认2048个。
     */
    private final int maxSubPageAllocs;

    PoolChunkList<T> parent;
    PoolChunk<T> prev;
    PoolChunk<T> next;

    public PoolChunk(PoolArena<T> arena, T memory, int pageSize, int maxOrder, int chunkSize) {
        this.arena = arena;
        this.memory = memory;
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
    }

    /**
     * 创建非池化的chunk
     */
    PoolChunk(PoolArena<T> arena, T memory, int size) {
        unpooled = true;
        this.arena = arena;
        this.memory = memory;
        memoryMap = null;
        depthMap = null;
        subPages = null;
        subPageOverflowMask = 0;
        pageSize = 0;
        maxOrder = 0;
        unusable = (byte) (maxOrder + 1);
        chunkSize = size;
        log2ChunkSize = log2(chunkSize);
        maxSubPageAllocs = 0;
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

        if ((normCapacity & subPageOverflowMask) != 0) {
            // 申请大小 >= pageSize
            handle = allocateRun(normCapacity);
        } else {
            // < pageSize
            handle = allocateSubPage(normCapacity);
        }

        //获取bitmap index
        if (handle < 0) {
            return false;
        }

        //申请内存
        initBuf(buf, handle, reqCapacity);
        return true;
    }

    /**
     * 获取一个可用节点（一个chunk）
     * 中序遍历树找到第一个memory(id)=h的节点，根节点id=1
     *
     * @param d 深度
     * @return 节点id
     */
    private int allocateNode(int d) {
        int id = 1;
        // d位1
        int initial = -(1 << d);
        byte val = value(id);
        // 节点值大于当前深度，代表没这么大的连续内存了
        if (val > d) {
            return -1;
        }
        // d层的节点 id & initial == 1 << d，小于d层的节点，结果=0
        // 小于d层的节点，或者val(id)<d
        while (val < d || (id & initial) == 0) {
            //乘2，左子树
            id <<= 1;
            val = value(id);
            if (val > d) {
                //右子树
                id ^= 1;
                val = value(id);
            }
        }

        //找到d层，且val(id)=d的节点
        byte value = value(id);
        assert value == d && (id & initial) == 1 << d : String.format("val = %d, id & initial = %d, d = %d",
            value, id & initial, d);

        //把节点标记为不可用
        setValue(id, unusable);
        //把它的父节点全部更新
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
        int d = log2(chunkSize / normCapacity);

        //取一个可用节点
        int id = allocateNode(d);
        if (id < 0) {
            return id;
        }

        freeBytes -= runLength(id);
        return id;
    }

    /**
     * 分配小于pageSize的内存，需要SubPage来做
     *
     * @param normCapacity
     * @return
     */
    private long allocateSubPage(int normCapacity) {
        // 获取subPage的头节点
        // This is need as we may add it back and so alter the linked-list structure.
        PoolSubPage<T> head = arena.findSubPagePoolHead(normCapacity);

        synchronized (head) {
            // subPages只会在叶子节点被分配
            int id = allocateNode(maxOrder);
            if (id < 0) {
                return id;
            }

            final PoolSubPage<T>[] subPages = this.subPages;
            final int pageSize = this.pageSize;

            freeBytes -= pageSize;

            //求出这是第几个叶子节点
            int subPageIdx = subPageIdx(id);
            PoolSubPage<T> subPage = subPages[subPageIdx];
            if (subPage == null) {
                //创建一个新的叶子节点，依旧会init
                subPage = new PoolSubPage<>(head, this, id, pageSize, normCapacity);
                subPages[subPageIdx] = subPage;
            } else {
                subPage.init(head, normCapacity);
            }
            return subPage.allocate();
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
     * 父节点memoryIdx=左右子树小的memoryIdx
     *
     * @param id id
     */
    private void updateParentsAlloc(int id) {
        while (id > 1) {
            //获取父节点id
            int parentId = id >>> 1;
            byte val1 = value(id);
            //todo: ?
            byte val2 = value(id ^ 1);
            byte val = val1 < val2 ? val1 : val2;
            setValue(parentId, val);
            id = parentId;
        }
    }

    private void updateParentsFree(int id) {
        int logChild = depth(id) + 1;
        while (id > 1) {
            int parentId = id >>> 1;
            byte val1 = value(id);
            byte val2 = value(id ^ 1);
            // in first iteration equals log, subsequently reduce 1 from logChild as we traverse up
            logChild -= 1;

            if (val1 == logChild && val2 == logChild) {
                setValue(parentId, (byte) (logChild - 1));
            } else {
                byte val = val1 < val2 ? val1 : val2;
                setValue(parentId, val);
            }

            id = parentId;
        }
    }

    /**
     * 分配内存
     *
     * @param buf         终点
     * @param handle      bitmap
     * @param reqCapacity 申请大小
     */
    void initBuf(PooledByteBuf<T> buf, long handle, int reqCapacity) {
        //低32位，节点id
        int memoryMapIdx = memoryMapIdx(handle);
        //高32位，subPage id
        int bitmapIdx = bitmapIdx(handle);
        if (bitmapIdx == 0) {
            //申请内存>=pageSize，直接取出节点
            byte val = value(memoryMapIdx);
            assert val == unusable : String.valueOf(val);
            //分配内存
            buf.init(this, handle, runOffset(memoryMapIdx), reqCapacity, runLength(memoryMapIdx),
                arena.parent.threadCache());
        } else {
            initBufWithSubPage(buf, handle, bitmapIdx, reqCapacity);
        }
    }

    /**
     * 获取bitmap的低32位，得到page id
     *
     * @param handle bitmap
     * @return
     */
    private static int memoryMapIdx(long handle) {
        return (int) handle;
    }

    /**
     * 获取高32位，subPage id
     *
     * @param handle bitmap
     * @return
     */
    private static int bitmapIdx(long handle) {
        return (int) (handle >>> Integer.SIZE);
    }

    /**
     * 节点大小
     * 从叶子节点往上数 层数 m，size=2^m
     *
     * @param id 节点
     * @return byte数量
     */
    private int runLength(int id) {
        return 1 << (log2ChunkSize - depth(id));
    }

    private byte depth(int id) {
        return depthMap[id];
    }

    /**
     * @param id 申请节点id
     * @return 该节点在这个深度的偏移
     */
    private int runOffset(int id) {
        //把这个深度的第一个节点id和 申请节点id 异或，得到的就是相对节点id的偏移量
        int shift = id ^ (1 << depth(id));
        return shift * runLength(id);
    }

    void initBufWithSubPage(PooledByteBuf<T> buf, long handle, int reqCapacity) {
        initBufWithSubPage(buf, handle, bitmapIdx(handle), reqCapacity);
    }

    private void initBufWithSubPage(PooledByteBuf<T> buf, long handle, int bitmapIdx, int reqCapacity) {
        assert bitmapIdx != 0;

        int memoryMapIdx = memoryMapIdx(handle);

        PoolSubPage<T> subPage = subPages[subPageIdx(memoryMapIdx)];
        assert subPage.doNotDestroy;
        assert reqCapacity <= subPage.elemSize;

        buf.init(this, handle, runOffset(memoryMapIdx) + (bitmapIdx & 0x3FFFFFFF) * subPage.elemSize,
            reqCapacity, subPage.elemSize, arena.parent.threadCache());
    }

    public void free(long handle) {
        int memoryMapIdx = memoryMapIdx(handle);
        int bitmapIdx = bitmapIdx(handle);

        if (bitmapIdx != 0) {
            // 释放subPage
            PoolSubPage<T> subPage = subPages[subPageIdx(memoryMapIdx)];
            assert subPage != null && subPage.doNotDestroy;

            PoolSubPage<T> head = arena.findSubPagePoolHead(subPage.elemSize);
            synchronized (head) {
                if (subPage.free(head, bitmapIdx & 0x3FFFFFFF)) {
                    return;
                }
            }
        }
        freeBytes += runLength(memoryMapIdx);
        setValue(memoryMapIdx, depth(memoryMapIdx));
        updateParentsFree(memoryMapIdx);
    }

    void destroy() {
        arena.destroyChunk(this);
    }
}
