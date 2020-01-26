package com.simple.netty.transport.channel.nio;

import com.simple.netty.common.internal.ObjectUtil;
import com.simple.netty.transport.channel.EventLoop;
import com.simple.netty.transport.channel.SingleThreadEventLoop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 单线程实现的reactor线程
 * Date: 2020-01-12
 * Time: 12:58
 *
 * @author yrw
 */
public class NioEventLoop extends SingleThreadEventLoop {

    private static final Logger logger = LoggerFactory.getLogger(NioEventLoop.class);

    /**
     * NIO多路复用器
     */
    private Selector selector;

    private final SelectorProvider provider;

    NioEventLoop(NioEventLoop parent, Executor executor, SelectorProvider selectorProvider) {
        super(parent, executor);
        this.provider = ObjectUtil.checkNotNull(selectorProvider, "selectorProvider");
        this.selector = openSelector();
    }

    Selector unwrappedSelector() {
        return selector;
    }

    private Selector openSelector() {
        try {
            return provider.openSelector();
        } catch (IOException e) {
            throw new RuntimeException("failed to open a new selector", e);
        }
    }

    @Override
    protected void run() {
        //死循环，除非接到指令退出
        for (; ; ) {
            try {
                int strategy;
                try {
                    //消息队列中有消息没处理，立刻select（不阻塞，没有就返回0）
                    if (hasTasks()) {
                        strategy = selectNow();
                    } else {
                        //没有任务，执行select（会阻塞直到有就绪的Channel）
                        strategy = selector.select();
                    }
                } catch (IOException e) {
                    //todo: netty此处处理jvm的bug导致的空轮询
                    continue;
                }

                //有就绪的Channel，处理
                try {
                    if (strategy > 0) {
                        processSelectedKeys();
                    }
                } finally {
                    //处理非io任务
                    runAllTasks();
                }
            } catch (Throwable e) {
                logger.error("error", e);
            }

            //判断是否处于shutdown状态
            try {
                if (isShuttingDown()) {
                    closeAll();
                    if (confirmShutdown()) {
                        return;
                    }
                }
            } catch (Throwable t) {
                logger.error("error", t);
            }
        }
    }

    int selectNow() throws IOException {
        return selector.selectNow();
    }

    void cancel(SelectionKey key) {
        key.cancel();
    }

    private void closeAll() {
        try {
            selectNow();
        } catch (Throwable t) {
            logger.warn("Failed to update SelectionKeys.", t);
        }
        Set<SelectionKey> keys = selector.keys();
        Collection<AbstractNioChannel> channels = new ArrayList<>(keys.size());
        for (SelectionKey k : keys) {
            Object a = k.attachment();
            if (a instanceof AbstractNioChannel) {
                channels.add((AbstractNioChannel) a);
            } else {
                //系统任务
            }
        }

        //把所有的channel关闭
        for (AbstractNioChannel ch : channels) {
            ch.unsafe().close(ch.unsafe().voidPromise());
        }
    }

    private void processSelectedKeys() {
        Set<SelectionKey> selectedKeys = selector.selectedKeys();

        //判断空，避免每次都创建Iterator，产生垃圾
        if (selectedKeys.isEmpty()) {
            return;
        }

        //遍历Selector
        Iterator<SelectionKey> i = selectedKeys.iterator();
        do {
            final SelectionKey k = i.next();
            final Object a = k.attachment();
            i.remove();

            //处理就绪的channel
            processSelectedKey(k, (AbstractNioChannel) a);

        } while (i.hasNext());
    }

    /**
     * 处理网络io事件的核心逻辑
     *
     * @param k
     * @param ch
     */
    private void processSelectedKey(SelectionKey k, AbstractNioChannel ch) {
        //获取unsafe
        final AbstractNioChannel.NioUnsafe unsafe = ch.unsafe();
        //key不可用，关闭释放资源
        if (!k.isValid()) {
            final EventLoop eventLoop;
            try {
                eventLoop = ch.eventLoop();
            } catch (Throwable ignored) {
                return;
            }
            if (eventLoop == this) {
                // close the channel if the key is not valid anymore
                unsafe.close(unsafe.voidPromise());
            }
            return;
        }

        //判断key，开始处理
        try {
            int readyOps = k.readyOps();

            // 处理connect事件（客户端处理）
            if ((readyOps & SelectionKey.OP_CONNECT) != 0) {
                int ops = k.interestOps();
                //清空ops
                ops &= ~SelectionKey.OP_CONNECT;
                k.interestOps(ops);

                unsafe.finishConnect();
            }

            // 处理write事件，说明有半包消息没发送完成，继续flush
            if ((readyOps & SelectionKey.OP_WRITE) != 0) {
                // forceFlush()写完之后会清除interestOps
                ch.unsafe().forceFlush();
            }

            // 处理read事件 or accept事件（一般服务端处理）
            if ((readyOps & (SelectionKey.OP_READ | SelectionKey.OP_ACCEPT)) != 0 || readyOps == 0) {
                unsafe.read();
            }
        } catch (CancelledKeyException ignored) {
            unsafe.close(unsafe.voidPromise());
        }
    }

    @Override
    @Deprecated
    public void shutdown() {

    }

    @Override
    @Deprecated
    public List<Runnable> shutdownNow() {
        return null;
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }
}
