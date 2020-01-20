package com.simple.netty.transport.channel;

import com.simple.netty.common.concurrent.EventExecutor;
import com.simple.netty.common.internal.ObjectUtil;
import com.simple.netty.common.internal.PlatformDependent;

/**
 * Date: 2020-01-19
 * Time: 21:48
 *
 * @author yrw
 */
public class FailedChannelFuture extends CompleteChannelFuture {

    private final Throwable cause;

    FailedChannelFuture(Channel channel, EventExecutor executor, Throwable cause) {
        super(channel, executor);
        this.cause = ObjectUtil.checkNotNull(cause, "cause");
    }

    @Override
    public Throwable cause() {
        return cause;
    }

    @Override
    public boolean isSuccess() {
        return false;
    }

    @Override
    public ChannelFuture sync() {
        PlatformDependent.throwException(cause);
        return this;
    }
}
