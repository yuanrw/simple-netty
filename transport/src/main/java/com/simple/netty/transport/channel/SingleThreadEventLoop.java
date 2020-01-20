package com.simple.netty.transport.channel;

import com.simple.netty.common.concurrent.SingleThreadEventExecutor;
import com.simple.netty.common.internal.ObjectUtil;

import java.util.Queue;
import java.util.concurrent.Executor;

/**
 * 用单个线程实现的reactor线程
 * Date: 2020-01-13
 * Time: 20:07
 *
 * @author yrw
 */
public abstract class SingleThreadEventLoop extends SingleThreadEventExecutor implements EventLoop {

    private final Queue<Runnable> tailTasks;

    protected SingleThreadEventLoop(EventLoopGroup parent, Executor executor) {
        super(parent, executor);
        tailTasks = newTaskQueue(16);
    }

    @Override
    public EventLoopGroup parent() {
        return (EventLoopGroup) super.parent();
    }

    @Override
    public EventLoop next() {
        return (EventLoop) super.next();
    }

    @Override
    public ChannelFuture register(Channel channel) {
        return register(new DefaultChannelPromise(channel, this));
    }

    @Override
    public ChannelFuture register(final ChannelPromise promise) {
        ObjectUtil.checkNotNull(promise, "promise");
        promise.channel().unsafe().register(this, promise);
        return promise;
    }

    @Override
    protected boolean hasTasks() {
        return super.hasTasks() || !tailTasks.isEmpty();
    }

    @Override
    public int pendingTasks() {
        return super.pendingTasks() + tailTasks.size();
    }
}
