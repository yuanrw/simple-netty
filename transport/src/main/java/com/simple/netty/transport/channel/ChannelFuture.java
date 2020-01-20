package com.simple.netty.transport.channel;

import com.simple.netty.common.concurrent.Future;
import com.simple.netty.common.concurrent.GenericFutureListener;

/**
 * 用于表示IO操作的结果
 * 共有三种状态：completed, uncompleted, failed
 * 通过添加监听器进行后续操作
 * Date: 2020-01-05
 * Time: 14:59
 *
 * @author yrw
 */
public interface ChannelFuture extends Future<Void> {

    Channel channel();

    @Override
    ChannelFuture addListener(GenericFutureListener<? extends Future<? super Void>> listener);

    @Override
    ChannelFuture addListeners(GenericFutureListener<? extends Future<? super Void>>... listeners);

    @Override
    ChannelFuture removeListener(GenericFutureListener<? extends Future<? super Void>> listener);

    @Override
    ChannelFuture removeListeners(GenericFutureListener<? extends Future<? super Void>>... listeners);

    @Override
    ChannelFuture sync() throws InterruptedException;

    @Override
    ChannelFuture await() throws InterruptedException;
}
