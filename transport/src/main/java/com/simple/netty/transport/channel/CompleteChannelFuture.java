package com.simple.netty.transport.channel;

import com.simple.netty.common.concurrent.CompleteFuture;
import com.simple.netty.common.concurrent.EventExecutor;
import com.simple.netty.common.concurrent.Future;
import com.simple.netty.common.concurrent.GenericFutureListener;
import com.simple.netty.common.internal.ObjectUtil;

/**
 * 已经完成的ChannelFuture
 * Date: 2020-01-19
 * Time: 21:40
 *
 * @author yrw
 */
abstract class CompleteChannelFuture extends CompleteFuture<Void> implements ChannelFuture {

    private final Channel channel;

    protected CompleteChannelFuture(Channel channel, EventExecutor executor) {
        super(executor);
        this.channel = ObjectUtil.checkNotNull(channel, "channel");
    }

    @Override
    protected EventExecutor executor() {
        EventExecutor e = super.executor();
        if (e == null) {
            return channel().eventLoop();
        } else {
            return e;
        }
    }

    @Override
    public ChannelFuture addListener(GenericFutureListener<? extends Future<? super Void>> listener) {
        super.addListener(listener);
        return this;
    }

    @Override
    public ChannelFuture addListeners(GenericFutureListener<? extends Future<? super Void>>... listeners) {
        super.addListeners(listeners);
        return this;
    }

    @Override
    public ChannelFuture removeListener(GenericFutureListener<? extends Future<? super Void>> listener) {
        super.removeListener(listener);
        return this;
    }

    @Override
    public ChannelFuture removeListeners(GenericFutureListener<? extends Future<? super Void>>... listeners) {
        super.removeListeners(listeners);
        return this;
    }

    @Override
    public ChannelFuture sync() throws InterruptedException {
        return this;
    }

    @Override
    public ChannelFuture await() throws InterruptedException {
        return this;
    }

    @Override
    public Channel channel() {
        return channel;
    }

    @Override
    public Void getNow() {
        return null;
    }
}
