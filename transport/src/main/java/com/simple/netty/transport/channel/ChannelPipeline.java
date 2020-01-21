package com.simple.netty.transport.channel;

import com.simple.netty.common.concurrent.EventExecutorGroup;

import java.util.List;
import java.util.Map;

/**
 * 放ChannelHandler的容器
 * 内部维护一个Handler链表
 * Date: 2019-12-30
 * Time: 20:58
 *
 * @author yrw
 */
public interface ChannelPipeline extends
    ChannelInboundInvoker, ChannelOutboundInvoker, Iterable<Map.Entry<String, ChannelHandler>> {

    ChannelPipeline addFirst(ChannelHandler... handlers);

    ChannelPipeline addFirst(String name, ChannelHandler handler);

    ChannelPipeline addFirst(EventExecutorGroup group, String name, ChannelHandler handler);

    ChannelPipeline addLast(ChannelHandler... handlers);

    ChannelPipeline addLast(String name, ChannelHandler handler);

    ChannelPipeline addLast(EventExecutorGroup group, String name, ChannelHandler handler);

    ChannelPipeline remove(ChannelHandler handler);

    ChannelHandler remove(String name);

    ChannelHandler removeFirst();

    ChannelHandler removeLast();

    ChannelHandler first();

    ChannelHandlerContext firstContext();

    ChannelHandler last();

    ChannelHandlerContext lastContext();

    ChannelHandlerContext context(ChannelHandler handler);

    ChannelHandlerContext context(String name);

    Channel channel();

    List<String> names();
}
