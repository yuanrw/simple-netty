package com.simple.netty.transport.channel;

/**
 * Date: 2020-01-02
 * Time: 20:38
 *
 * @author yrw
 */
public interface ChannelHandler {

    void handlerAdded(ChannelHandlerContext ctx) throws Exception;

    void handlerRemoved(ChannelHandlerContext ctx) throws Exception;
}
