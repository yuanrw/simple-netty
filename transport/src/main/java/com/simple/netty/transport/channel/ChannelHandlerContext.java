package com.simple.netty.transport.channel;

import com.simple.netty.buffer.ByteBufAllocator;
import com.simple.netty.common.concurrent.EventExecutor;

/**
 * Date: 2020-01-02
 * Time: 20:42
 *
 * @author yrw
 */
public interface ChannelHandlerContext extends ChannelInboundInvoker, ChannelOutboundInvoker {

    Channel channel();

    EventExecutor executor();

    String name();

    ChannelHandler handler();

    boolean isRemoved();

    @Override
    ChannelHandlerContext fireChannelRegistered();

    @Override
    ChannelHandlerContext fireChannelUnregistered();

    @Override
    ChannelHandlerContext fireChannelActive();

    @Override
    ChannelHandlerContext fireChannelInactive();

    @Override
    ChannelHandlerContext fireExceptionCaught(Throwable cause);

    @Override
    ChannelHandlerContext fireUserEventTriggered(Object evt);

    @Override
    ChannelHandlerContext fireChannelRead(Object msg);

    @Override
    ChannelHandlerContext fireChannelReadComplete();

    @Override
    ChannelHandlerContext fireChannelWritabilityChanged();

    @Override
    ChannelHandlerContext read();

    @Override
    ChannelHandlerContext flush();

    ChannelPipeline pipeline();

    ByteBufAllocator alloc();
}
