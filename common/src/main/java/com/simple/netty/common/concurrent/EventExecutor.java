package com.simple.netty.common.concurrent;

import java.util.concurrent.Future;

/**
 * reactorw线程
 * Date: 2020-01-05
 * Time: 10:41
 *
 * @author yrw
 */
public interface EventExecutor extends EventExecutorGroup {

    @Override
    EventExecutor next();

    EventExecutorGroup parent();

    /**
     * 判断当前线程是否在这个线程池里
     *
     * @return
     */
    boolean inEventLoop();

    /**
     * 判断线程是否在这个线程池里
     *
     * @param thread
     * @return
     */
    boolean inEventLoop(Thread thread);

    <V> Promise<V> newPromise();

    <V> Future<V> newSucceededFuture(V result);

    <V> Future<V> newFailedFuture(Throwable cause);
}
