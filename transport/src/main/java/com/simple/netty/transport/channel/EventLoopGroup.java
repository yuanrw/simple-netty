package com.simple.netty.transport.channel;

import com.simple.netty.common.concurrent.EventExecutorGroup;

/**
 * 一组reactor线程（类似线程池）
 * Date: 2020-01-05
 * Time: 10:40
 *
 * @author yrw
 */
public interface EventLoopGroup extends EventExecutorGroup {

    @Override
    EventLoop next();

    /**
     * 把channel注册到EventLoopGroup上
     *
     * @param channel
     * @return
     */
    ChannelFuture register(Channel channel);

    ChannelFuture register(ChannelPromise promise);
}
