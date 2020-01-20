package com.simple.netty.transport.channel;

import com.simple.netty.common.concurrent.GenericFutureListener;

/**
 * Date: 2020-01-16
 * Time: 18:36
 *
 * @author yrw
 */
public interface ChannelFutureListener extends GenericFutureListener<ChannelFuture> {

    /**
     * A {@link ChannelFutureListener} that closes the {@link Channel} which is
     * associated with the specified {@link ChannelFuture}.
     */
    ChannelFutureListener CLOSE = future -> future.channel().close();

    /**
     * A {@link ChannelFutureListener} that closes the {@link Channel} when the
     * operation ended up with a failure or cancellation rather than a success.
     */
    ChannelFutureListener CLOSE_ON_FAILURE = future -> {
        if (!future.isSuccess()) {
            future.channel().close();
        }
    };

    /**
     * A {@link ChannelFutureListener} that forwards the {@link Throwable} of the {@link ChannelFuture} into the
     * {@link ChannelPipeline}. This mimics the old behavior of Netty 3.
     */
    ChannelFutureListener FIRE_EXCEPTION_ON_FAILURE = future -> {
        if (!future.isSuccess()) {
            future.channel().pipeline().fireExceptionCaught(future.cause());
        }
    };

}
