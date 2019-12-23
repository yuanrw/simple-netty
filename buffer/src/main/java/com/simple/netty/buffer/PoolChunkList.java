package com.simple.netty.buffer;

import com.simple.netty.common.StringUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * 一系列PoolChunk组成的链条
 * Date: 2019-12-18
 * Time: 20:21
 *
 * @author yrw
 */
public class PoolChunkList<T> implements PoolChunkListMetric {

    private static final Iterator<PoolChunkMetric> EMPTY_METRICS = Collections.<PoolChunkMetric>emptyList().iterator();

    /**
     * 归属的分配器
     */
    private final PoolArena<T> arena;
    /**
     * 头部，第一个元素
     */
    private PoolChunk<T> head;
    private PoolChunkList<T> prevList;
    private final PoolChunkList<T> nextList;

    private final int minUsage;
    private final int maxUsage;
    private final int maxCapacity;

    PoolChunkList(PoolArena<T> arena, PoolChunkList<T> nextList, int minUsage, int maxUsage, int chunkSize) {
        assert minUsage <= maxUsage;
        this.arena = arena;
        this.nextList = nextList;
        this.minUsage = minUsage;
        this.maxUsage = maxUsage;
        maxCapacity = calculateMaxCapacity(minUsage, chunkSize);
    }

    /**
     * 计算这个list里所有PoolChunk的最大容量
     *
     * @param minUsage  最小使用率（百分比）
     * @param chunkSize 块大小
     */
    private static int calculateMaxCapacity(int minUsage, int chunkSize) {
        minUsage = minUsage0(minUsage);
        if (minUsage == 100) {
            return 0;
        }
        return (int) (chunkSize * (100L - minUsage) / 100L);
    }

    void prevList(PoolChunkList<T> prevList) {
        assert this.prevList == null;
        this.prevList = prevList;
    }

    /**
     * 分配内存，遍历PoolChunk，分配成功后自动调整PoolChunk的位置
     *
     * @param buf          目的地
     * @param reqCapacity  申请内存大小
     * @param normCapacity 格式化后的申请内存大小，一定是 < poolChunkSize的
     * @return
     */
    boolean allocate(PooledByteBuf<T> buf, int reqCapacity, int normCapacity) {
        if (normCapacity > maxCapacity) {
            return false;
        }

        for (PoolChunk<T> cur = head; cur != null; cur = cur.next) {
            //遍历list
            if (cur.allocate(buf, reqCapacity, normCapacity)) {
                if (cur.usage() >= maxUsage) {
                    //如果使用比例已经超了，就把poolChunk放到下一个list里面
                    remove(cur);
                    nextList.add(cur);
                }
                return true;
            }
        }
        return false;
    }

    boolean free(PoolChunk<T> chunk, long handle, ByteBuffer nioBuffer) {
        chunk.free(handle, nioBuffer);
        if (chunk.usage() < minUsage) {
            remove(chunk);
            return move0(chunk);
        }
        return true;
    }

    private boolean move(PoolChunk<T> chunk) {
        assert chunk.usage() < maxUsage;

        if (chunk.usage() < minUsage) {
            //往前移动
            return move0(chunk);
        }

        //放到当前list里
        add0(chunk);
        return true;
    }

    /**
     * Moves the {@link PoolChunk} down the {@link PoolChunkList} linked-list so it will end up in the right
     * {@link PoolChunkList} that has the correct minUsage / maxUsage in respect to {@link PoolChunk#usage()}.
     */
    private boolean move0(PoolChunk<T> chunk) {
        if (prevList == null) {
            //前面没有了，返回false，会触发PoolChunk的销毁，并且释放内存
            assert chunk.usage() == 0;
            return false;
        }
        //继续往前移动
        return prevList.move(chunk);
    }

    void add(PoolChunk<T> chunk) {
        if (chunk.usage() >= maxUsage) {
            nextList.add(chunk);
            return;
        }
        add0(chunk);
    }

    /**
     * Adds the {@link PoolChunk} to this {@link PoolChunkList}.
     */
    void add0(PoolChunk<T> chunk) {
        chunk.parent = this;
        if (head == null) {
            head = chunk;
            chunk.prev = null;
            chunk.next = null;
        } else {
            chunk.prev = null;
            chunk.next = head;
            head.prev = chunk;
            head = chunk;
        }
    }

    private void remove(PoolChunk<T> cur) {
        if (cur == head) {
            head = cur.next;
            if (head != null) {
                head.prev = null;
            }
        } else {
            PoolChunk<T> next = cur.next;
            cur.prev.next = next;
            if (next != null) {
                next.prev = cur.prev;
            }
        }
    }

    @Override
    public int minUsage() {
        return minUsage0(minUsage);
    }

    @Override
    public int maxUsage() {
        return min(maxUsage, 100);
    }

    private static int minUsage0(int value) {
        return max(1, value);
    }

    @Override
    public Iterator<PoolChunkMetric> iterator() {
        synchronized (arena) {
            if (head == null) {
                return EMPTY_METRICS;
            }
            List<PoolChunkMetric> metrics = new ArrayList<PoolChunkMetric>();
            for (PoolChunk<T> cur = head; ; ) {
                metrics.add(cur);
                cur = cur.next;
                if (cur == null) {
                    break;
                }
            }
            return metrics.iterator();
        }
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        synchronized (arena) {
            if (head == null) {
                return "none";
            }

            for (PoolChunk<T> cur = head; ; ) {
                buf.append(cur);
                cur = cur.next;
                if (cur == null) {
                    break;
                }
                buf.append(StringUtil.NEWLINE);
            }
        }
        return buf.toString();
    }

    void destroy(PoolArena<T> arena) {
        PoolChunk<T> chunk = head;
        while (chunk != null) {
            arena.destroyChunk(chunk);
            chunk = chunk.next;
        }
        head = null;
    }
}
