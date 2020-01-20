package com.simple.netty.transport.channel;

import com.simple.netty.common.concurrent.EventExecutor;

/**
 * Date: 2020-01-20
 * Time: 19:14
 *
 * @author yrw
 */
final class DefaultChannelHandlerContext extends AbstractChannelHandlerContext {
    private final ChannelHandler handler;

    DefaultChannelHandlerContext(
        DefaultChannelPipeline pipeline, EventExecutor executor, String name, ChannelHandler handler) {
        super(pipeline, executor, name, handler.getClass());
        this.handler = handler;
    }

    @Override
    public ChannelHandler handler() {
        return handler;
    }
}
