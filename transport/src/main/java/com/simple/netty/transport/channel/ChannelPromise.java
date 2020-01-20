package com.simple.netty.transport.channel;

import com.simple.netty.common.concurrent.Future;
import com.simple.netty.common.concurrent.GenericFutureListener;
import com.simple.netty.common.concurrent.Promise;

/**
 * Date: 2019-12-31
 * Time: 18:02
 *
 * @author yrw
 */
public interface ChannelPromise extends ChannelFuture, Promise<Void> {

    @Override
    Channel channel();

    @Override
    ChannelPromise setSuccess(Void result);

    ChannelPromise setSuccess();

    boolean trySuccess();

    @Override
    ChannelPromise setFailure(Throwable cause);

    @Override
    ChannelPromise addListener(GenericFutureListener<? extends Future<? super Void>> listener);

    @Override
    ChannelPromise addListeners(GenericFutureListener<? extends Future<? super Void>>... listeners);

    @Override
    ChannelPromise removeListener(GenericFutureListener<? extends Future<? super Void>> listener);

    @Override
    ChannelPromise removeListeners(GenericFutureListener<? extends Future<? super Void>>... listeners);

    @Override
    ChannelPromise sync() throws InterruptedException;

    @Override
    ChannelPromise await() throws InterruptedException;

    ChannelPromise unvoid();
}
