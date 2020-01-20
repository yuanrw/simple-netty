package com.simple.netty.transport.channel;

import com.simple.netty.common.concurrent.EventExecutor;

/**
 * reactor线程
 * 可以注册Channel
 * Date: 2019-12-30
 * Time: 20:56
 *
 * @author yrw
 */
public interface EventLoop extends EventExecutor, EventLoopGroup {
}
