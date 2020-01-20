package com.simple.netty.common.concurrent;

import com.simple.netty.common.internal.ObjectUtil;
import com.simple.netty.common.internal.PlatformDependent;

/**
 * 失败的CompleteFuture
 * Date: 2020-01-05
 * Time: 14:07
 *
 * @author yrw
 */
public final class FailedFuture<V> extends CompleteFuture<V> {

    private final Throwable cause;

    /**
     * Creates a new instance.
     *
     * @param executor the {@link EventExecutor} associated with this future
     * @param cause    the cause of failure
     */
    public FailedFuture(EventExecutor executor, Throwable cause) {
        super(executor);
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
    public Future<V> sync() {
        PlatformDependent.throwException(cause);
        return this;
    }

    @Override
    public V getNow() {
        return null;
    }
}
