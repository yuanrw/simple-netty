package com.simple.netty.transport.channel;

import com.simple.netty.buffer.ByteBuf;
import com.simple.netty.common.internal.ObjectPool;
import com.simple.netty.common.internal.PromiseNotificationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import static java.lang.Math.min;

/**
 * 内部数据结构，AbstractChannel使用
 * 发送缓冲区，存放Object，现在只支持ByteBuf
 * Date: 2019-12-31
 * Time: 18:31
 *
 * @author yrw
 */
public final class ChannelOutboundBuffer {
    private static final Logger logger = LoggerFactory.getLogger(ChannelOutboundBuffer.class);

    private static final ThreadLocal<ByteBuffer[]> NIO_BUFFERS = ThreadLocal.withInitial(() -> new ByteBuffer[1024]);

    private final Channel channel;

    private Entry flushedEntry;

    private Entry unflushedEntry;

    private Entry tailEntry;

    private int flushed;

    private int nioBufferCount;
    private long nioBufferSize;

    private boolean inFail;

    private static final AtomicLongFieldUpdater<ChannelOutboundBuffer> TOTAL_PENDING_SIZE_UPDATER =
        AtomicLongFieldUpdater.newUpdater(ChannelOutboundBuffer.class, "totalPendingSize");

    private volatile long totalPendingSize;

    private static final AtomicIntegerFieldUpdater<ChannelOutboundBuffer> UNWRITABLE_UPDATER =
        AtomicIntegerFieldUpdater.newUpdater(ChannelOutboundBuffer.class, "unwritable");

    private volatile int unwritable;

    ChannelOutboundBuffer(AbstractChannel channel) {
        this.channel = channel;
    }

    public int nioBufferCount() {
        return nioBufferCount;
    }

    public void addMessage(ByteBuf msg, ChannelPromise promise) {
        Entry entry = Entry.newInstance(msg, promise);
        //空链表
        if (tailEntry == null) {
            flushedEntry = null;
        } else {
            //加入新数据
            Entry tail = tailEntry;
            tail.next = entry;
        }
        //更新tail指针
        tailEntry = entry;
        //更新unFlushed指针
        if (unflushedEntry == null) {
            unflushedEntry = entry;
        }

        //更新size，更新writable状态
        incrementPendingOutboundBytes(entry.size);
    }

    /**
     * 把所有消息标记为flushed
     */
    public void addFlush() {
        Entry entry = unflushedEntry;
        //有unFlush的元素
        if (entry != null) {
            if (flushedEntry == null) {
                // 全部unFlush
                flushedEntry = entry;
            }

            //更新flushed数量
            do {
                flushed++;
                entry = entry.next;
            } while (entry != null);

            // 全部flush了
            unflushedEntry = null;
        }
    }

    void close(final Throwable cause, final boolean allowChannelOpen) {
        if (inFail) {
            channel.eventLoop().execute(() -> close(cause, allowChannelOpen));
            return;
        }

        inFail = true;

        if (!allowChannelOpen && channel.isOpen()) {
            throw new IllegalStateException("close() must be invoked after the channel is closed.");
        }

        if (!isEmpty()) {
            throw new IllegalStateException("close() must be invoked after all flushed writes are handled.");
        }

        // Release all unFlushed messages.
        try {
            Entry e = unflushedEntry;
            while (e != null) {
                // Just decrease; do not trigger any events via decrementPendingOutboundBytes()
                int size = e.size;
                TOTAL_PENDING_SIZE_UPDATER.addAndGet(this, -size);

                //释放byteBuf
                e.release();
                //写入promise
                safeFail(e.promise, cause);
                logger.warn("Failed to mark a promise as failure because it has succeeded already: {}", cause);

                e = e.recycleAndGetNext();
            }
        } finally {
            inFail = false;
        }
        clearNioBuffers();
    }

    void close(ClosedChannelException cause) {
        close(cause, false);
    }

    private static void safeSuccess(ChannelPromise promise) {
        PromiseNotificationUtil.trySuccess(promise, null, promise instanceof VoidChannelPromise ? null : logger);
    }

    private static void safeFail(ChannelPromise promise, Throwable cause) {
        PromiseNotificationUtil.tryFailure(promise, cause, promise instanceof VoidChannelPromise ? null : logger);
    }

    /**
     * 移除当前消息，并且将Promise记录为success
     *
     * @return
     */
    public boolean remove() {
        Entry e = flushedEntry;
        if (e == null) {
            clearNioBuffers();
            return false;
        }
        ByteBuf msg = e.buf;

        //需要记录下来，后面会release
        ChannelPromise promise = e.promise;
        int size = e.size;

        removeEntry(e);

        msg.release();
        safeSuccess(promise);
        decrementPendingOutboundBytes(size);

        // recycle the entry
        e.recycle();

        return true;
    }

    /**
     * 移除当前消息，并且将Promise记录为fail
     *
     * @param cause
     * @return
     */
    public boolean remove(Throwable cause) {
        Entry e = flushedEntry;
        //全部未发送
        if (e == null) {
            clearNioBuffers();
            return false;
        }

        //获取第一个unFlushed的消息
        ChannelPromise promise = e.promise;
        int size = e.size;
        removeEntry(e);

        // 释放资源
        e.release();
        if (cause != null) {
            logger.warn("Failed to mark a promise as failure because it has succeeded already: {}", cause);
        }

        safeFail(promise, cause);

        //更新size，更新writable状态
        decrementPendingOutboundBytes(size);

        // 资源回收
        e.recycle();

        return true;
    }

    public void removeBytes(long writtenBytes) {
        for (; ; ) {
            final ByteBuf buf = current();
            assert buf != null;

            final int readerIndex = buf.readerIndex();
            final int readableBytes = buf.writerIndex() - readerIndex;

            // 代表数据都发出去了
            if (readableBytes <= writtenBytes) {
                if (writtenBytes != 0) {
                    writtenBytes -= readableBytes;
                }
                //将已发送的byteBuf删除
                remove();
            } else {
                // readableBytes > writtenBytes
                // 说明出现了写半包，只发出去一半
                if (writtenBytes != 0) {
                    buf.readerIndex(readerIndex + (int) writtenBytes);
                }
                break;
            }
        }
        clearNioBuffers();
    }

    private void clearNioBuffers() {
        int count = nioBufferCount;
        if (count > 0) {
            nioBufferCount = 0;
            Arrays.fill(NIO_BUFFERS.get(), 0, count, null);
        }
    }

    private void removeEntry(Entry e) {
        if (--flushed == 0) {
            // 链表为空
            flushedEntry = null;
            if (e == tailEntry) {
                tailEntry = null;
                unflushedEntry = null;
            }
        } else {
            //移动指针
            flushedEntry = e.next;
        }
    }

    void incrementPendingOutboundBytes(long size) {
        if (size == 0) {
            return;
        }

        long newWriteBufferSize = TOTAL_PENDING_SIZE_UPDATER.addAndGet(this, size);
        if (newWriteBufferSize > channel.config().getWriteBufferHighWaterMark()) {
            setUnwritable();
        }
    }

    private void setUnwritable() {
        for (; ; ) {
            final int oldValue = unwritable;
            final int newValue = oldValue | 1;
            if (UNWRITABLE_UPDATER.compareAndSet(this, oldValue, newValue)) {
                //unWritable的状态发送了变化，需要激活ChannelPipeline
                if (oldValue == 0 && newValue != 0) {
                    fireChannelWritabilityChanged();
                }
                break;
            }
        }
    }

    void decrementPendingOutboundBytes(long size) {
        if (size == 0) {
            return;
        }

        long newWriteBufferSize = TOTAL_PENDING_SIZE_UPDATER.addAndGet(this, -size);
        if (newWriteBufferSize < channel.config().getWriteBufferLowWaterMark()) {
            setWritable();
        }
    }

    private void setWritable() {
        for (; ; ) {
            final int oldValue = unwritable;
            final int newValue = oldValue & ~1;
            if (UNWRITABLE_UPDATER.compareAndSet(this, oldValue, newValue)) {
                if (oldValue != 0 && newValue == 0) {
                    fireChannelWritabilityChanged();
                }
                break;
            }
        }
    }

    private void fireChannelWritabilityChanged() {
        final ChannelPipeline pipeline = channel.pipeline();
        pipeline.fireChannelWritabilityChanged();
    }

    void failFlushed(Throwable cause) {
        // Make sure that this method does not reenter.  A listener added to the current promise can be notified by the
        // current thread in the tryFailure() call of the loop below, and the listener can trigger another fail() call
        // indirectly (usually by closing the channel.)
        //
        // See https://github.com/netty/netty/issues/1501
        if (inFail) {
            return;
        }

        try {
            inFail = true;
            for (; ; ) {
                if (!remove(cause)) {
                    break;
                }
            }
        } finally {
            inFail = false;
        }
    }

    public ByteBuf current() {
        Entry entry = flushedEntry;
        if (entry == null) {
            return null;
        }

        return entry.buf;
    }

    /**
     * 把缓冲区里的内容复制到ByteBuffer数组返回
     *
     * @param maxCount
     * @param maxBytes
     * @return
     */
    public ByteBuffer[] nioBuffers(int maxCount, long maxBytes) {
        assert maxCount > 0;
        assert maxBytes > 0;

        //大小计数
        long nioBufferSize = 0;
        //index
        int nioBufferCount = 0;

        //获取当前线程的 ByteBuffer[]
        ByteBuffer[] nioBuffers = NIO_BUFFERS.get();

        //遍历所有FlushedEntry
        Entry entry = flushedEntry;
        while (isFlushedEntry(entry)) {
            ByteBuf buf = entry.buf;
            final int readerIndex = buf.readerIndex();

            //获取ByteBuf里可读的byte数量
            final int readableBytes = buf.writerIndex() - readerIndex;

            if (readableBytes > 0) {
                // 如果nioBufferSize + readableBytes > maxBytes 并且nioBufferCount > 1，就停止移动ByteBuffer
                if (maxBytes - readableBytes < nioBufferSize && nioBufferCount != 0) {
                    break;
                }

                //累加总size
                nioBufferSize += readableBytes;

                //判断NIO_BUFFERS是否需要扩容
                int neededSpace = min(maxCount, nioBufferCount + buf.nioBufferCount());
                if (neededSpace > nioBuffers.length) {
                    nioBuffers = expandNioBufferArray(nioBuffers, neededSpace, nioBufferCount);
                    NIO_BUFFERS.set(nioBuffers);
                }

                ByteBuffer nioBuf = entry.buffer;
                if (nioBuf == null) {
                    //缓存一下
                    entry.buffer = nioBuf = buf.internalNioBuffer(readerIndex, readableBytes);
                }

                //放进nioBuffers
                nioBuffers[nioBufferCount++] = nioBuf;

                if (nioBufferCount == maxCount) {
                    break;
                }
            }
            entry = entry.next;
        }

        this.nioBufferCount = nioBufferCount;
        this.nioBufferSize = nioBufferSize;

        return nioBuffers;
    }

    private boolean isFlushedEntry(Entry e) {
        return e != null && e != unflushedEntry;
    }

    public boolean isEmpty() {
        return flushed == 0;
    }

    public boolean isWritable() {
        return unwritable == 0;
    }

    public long nioBufferSize() {
        return nioBufferSize;
    }

    private static ByteBuffer[] expandNioBufferArray(ByteBuffer[] array, int neededSpace, int size) {
        int newCapacity = array.length;
        do {
            // 翻倍增长
            newCapacity <<= 1;

            if (newCapacity < 0) {
                throw new IllegalStateException();
            }

        } while (neededSpace > newCapacity);

        ByteBuffer[] newArray = new ByteBuffer[newCapacity];
        System.arraycopy(array, 0, newArray, 0, size);

        return newArray;
    }

    /**
     * 链表节点
     */
    static final class Entry {
        private static final ObjectPool<Entry> RECYCLER = new ObjectPool<>(o -> new Entry());

        Entry next;
        ByteBuf buf;
        ByteBuffer buffer;
        ChannelPromise promise;
        int size;

        static Entry newInstance(ByteBuf msg, ChannelPromise promise) {
            Entry entry = RECYCLER.get();
            entry.buf = msg;
            entry.size = msg.readableBytes();
            entry.promise = promise;
            return entry;
        }

        void recycle() {
            next = null;
            buf = null;
            promise = null;
        }

        void release() {
            buf.release();
        }

        Entry recycleAndGetNext() {
            Entry next = this.next;
            recycle();
            return next;
        }
    }
}
