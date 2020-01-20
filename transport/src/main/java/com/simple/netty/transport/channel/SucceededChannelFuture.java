package com.simple.netty.transport.channel;

import com.simple.netty.common.concurrent.EventExecutor;

/**
 * Date: 2020-01-19
 * Time: 21:43
 *
 * @author yrw
 */
public class SucceededChannelFuture extends CompleteChannelFuture {

    SucceededChannelFuture(Channel channel, EventExecutor executor) {
        super(channel, executor);
    }

    @Override
    public Throwable cause() {
        return null;
    }

    @Override
    public boolean isSuccess() {
        return true;
    }
}
