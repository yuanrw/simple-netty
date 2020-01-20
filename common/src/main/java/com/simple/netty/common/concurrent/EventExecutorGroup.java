package com.simple.netty.common.concurrent;

import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 线程池
 * 可以调度定时任务
 * 负责管理他们的生命周期
 * Date: 2020-01-05
 * Time: 10:41
 *
 * @author yrw
 */
public interface EventExecutorGroup extends ScheduledExecutorService, Iterable<EventExecutor> {

    boolean isShuttingDown();

    Future<?> shutdownGracefully();

    Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit);

    Future<?> terminationFuture();

    /**
     * 获取可使用线程
     *
     * @return
     */
    EventExecutor next();

    @Override
    Iterator<EventExecutor> iterator();

    @Override
    Future<?> submit(Runnable task);

    @Override
    <T> Future<T> submit(Runnable task, T result);

    @Override
    <T> Future<T> submit(Callable<T> task);
}
