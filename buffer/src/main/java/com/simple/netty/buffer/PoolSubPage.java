package com.simple.netty.buffer;

/**
 * 用于管理page，申请比page还小的内存的时候使用
 * 用long的每一位来表示每一个byte的使用情况，0表示未使用，1表示使用
 * 一个long可以表示64个byte
 * Date: 2019-12-18
 * Time: 20:21
 *
 * @author yrw
 */
public class PoolSubPage<T> implements PoolSubPageMetric {

    final PoolChunk<T> chunk;

    /**
     * 映射到PoolChunk的叶子节点
     */
    private final int memoryMapIdx;

    /**
     * 叶子节点大小
     */
    private final int pageSize;

    /**
     * bitmap的每一位bit代表一个element的使用情况
     * 1代表使用，0代表未使用
     */
    private final long[] bitmap;

    PoolSubPage<T> prev;
    PoolSubPage<T> next;

    boolean doNotDestroy;
    /**
     * 每个单元大小，pageSize/bitmapLength
     */
    int elemSize;

    /**
     * element数量
     */
    private int maxNumElems;

    private int bitmapLength;

    /**
     * 下一个可用的element索引
     */
    private int nextAvail;

    /**
     * 可用element的数量
     */
    private int numAvail;

    PoolSubPage(int pageSize) {
        chunk = null;
        memoryMapIdx = -1;
        elemSize = -1;
        this.pageSize = pageSize;
        bitmap = null;
    }

    PoolSubPage(PoolSubPage<T> head, PoolChunk<T> chunk, int memoryMapIdx, int pageSize, int elemSize) {
        this.chunk = chunk;
        this.memoryMapIdx = memoryMapIdx;
        this.pageSize = pageSize;
        bitmap = new long[pageSize >>> 10];
        init(head, elemSize);
    }

    /**
     * init根据当前需要分配的内存大小，确定需要多少个bitmap元素
     * elemSize代表此次申请的大小，比如申请64byte，那么这个page被分成了8k/64=2^7=128个
     * >>>6 2^6=64 代表long的长度
     */
    void init(PoolSubPage<T> head, int elemSize) {
        doNotDestroy = true;
        this.elemSize = elemSize;
        if (elemSize != 0) {
            maxNumElems = numAvail = pageSize / elemSize;
            nextAvail = 0;

            //bitmap的个数，总个数/64
            bitmapLength = maxNumElems >>> 6;

            if ((maxNumElems & 63) != 0) {
                bitmapLength++;
            }

            //初始化
            for (int i = 0; i < bitmapLength; i++) {
                bitmap[i] = 0;
            }
        }
        //初始化链表的头结点
        addToPool(head);
    }

    private void addToPool(PoolSubPage<T> head) {
        assert prev == null && next == null;
        prev = head;
        next = head.next;
        next.prev = this;
        head.next = this;
    }

    /**
     * 分配内存
     *
     * @return bitmap
     */
    long allocate() {
        if (elemSize == 0) {
            return toHandle(0);
        }

        if (numAvail == 0 || !doNotDestroy) {
            return -1;
        }

        //获取可用的element index
        final int bitmapIdx = getNextAvail();

        //高26位，第几个long
        int q = bitmapIdx >>> 6;

        //低6位，long的第几位
        int r = bitmapIdx & 63;

        //验证bitmap合法
        assert (bitmap[q] >>> r & 1) == 0;

        //把对应的bit位变为1
        bitmap[q] |= 1L << r;

        //全部分配完，这个subPage从池子中移除
        if (--numAvail == 0) {
            removeFromPool();
        }

        return toHandle(bitmapIdx);
    }

    private void removeFromPool() {
        assert prev != null && next != null;
        prev.next = next;
        next.prev = prev;
        next = null;
        prev = null;
    }

    /**
     * 低32位=memoryMapIdx
     * 高32位=subPageIdx
     *
     * @param bitmapIdx
     * @return bitmap
     */
    private long toHandle(int bitmapIdx) {
        return 0x4000000000000000L | (long) bitmapIdx << 32 | memoryMapIdx;
    }

    private int getNextAvail() {
        int nextAvail = this.nextAvail;
        if (nextAvail >= 0) {
            this.nextAvail = -1;
            return nextAvail;
        }
        return findNextAvail();
    }

    private int findNextAvail() {
        final long[] bitmap = this.bitmap;
        final int bitmapLength = this.bitmapLength;
        for (int i = 0; i < bitmapLength; i++) {
            long bits = bitmap[i];
            if (~bits != 0) {
                //有可使用的bit
                return findNextAvail0(i, bits);
            }
        }
        return -1;
    }

    private int findNextAvail0(int i, long bits) {
        final int maxNumElems = this.maxNumElems;
        //在右边增加6位
        final int baseVal = i << 6;

        //从最低位开始一位一位地遍历
        for (int j = 0; j < 64; j++) {
            //当前位是0
            if ((bits & 1) == 0) {
                //高26位代表是第几个long，低6位代表是long中的第几位
                int val = baseVal | j;
                //不能大于总段数
                if (val < maxNumElems) {
                    return val;
                } else {
                    break;
                }
            }
            bits >>>= 1;
        }
        return -1;
    }

    @Override
    public int maxNumElements() {
        return maxNumElems;
    }

    @Override
    public int numAvailable() {
        return numAvail;
    }

    @Override
    public int elementSize() {
        return elemSize;
    }

    @Override
    public int pageSize() {
        return pageSize;
    }

    /**
     * 判断subPage是否被使用
     *
     * @return true被使用，false不被使用，可以释放
     */
    boolean free(PoolSubPage<T> head, int bitmapIdx) {
        if (elemSize == 0) {
            return true;
        }
        int q = bitmapIdx >>> 6;
        int r = bitmapIdx & 63;
        assert (bitmap[q] >>> r & 1) != 0;
        bitmap[q] ^= 1L << r;

        //设置为可用
        setNextAvail(bitmapIdx);

        if (numAvail++ == 0) {
            //第一个节点
            addToPool(head);
            return true;
        }

        if (numAvail != maxNumElems) {
            return true;
        } else {
            // SubPage 所有element没有被使用 (numAvail == maxNumElems)
            if (prev == next) {
                // 池中唯一一个节点，不删除
                return true;
            }

            //不是唯一一个节点，可以删除
            doNotDestroy = false;
            removeFromPool();
            return false;
        }
    }

    void destroy() {
        if (chunk != null) {
            chunk.destroy();
        }
    }

    public void setNextAvail(int nextAvail) {
        this.nextAvail = nextAvail;
    }
}
