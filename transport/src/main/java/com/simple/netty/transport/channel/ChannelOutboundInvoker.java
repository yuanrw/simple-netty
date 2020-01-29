package com.simple.netty.transport.channel;

import com.simple.netty.buffer.ByteBuf;

import java.net.SocketAddress;

/**
 * Outbound代表由用户线程发起的操作
 * Date: 2019-12-31
 * Time: 18:31
 *
 * @author yrw
 */
public interface ChannelOutboundInvoker {

    ChannelFuture bind(SocketAddress localAddress);

    ChannelFuture connect(SocketAddress remoteAddress);

    ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress);

    ChannelFuture disconnect();

    ChannelFuture close();

    ChannelFuture deregister();

    ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise);

    ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise);

    ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise);

    ChannelFuture disconnect(ChannelPromise promise);

    ChannelFuture close(ChannelPromise promise);

    ChannelFuture deregister(ChannelPromise promise);

    ChannelOutboundInvoker read();

    ChannelFuture write(ByteBuf msg);

    ChannelFuture write(ByteBuf msg, ChannelPromise promise);

    ChannelOutboundInvoker flush();

    ChannelFuture writeAndFlush(ByteBuf msg, ChannelPromise promise);

    ChannelFuture writeAndFlush(ByteBuf msg);

    ChannelPromise newPromise();

    ChannelFuture newSucceededFuture();

    ChannelFuture newFailedFuture(Throwable cause);

    ChannelPromise voidPromise();
}
