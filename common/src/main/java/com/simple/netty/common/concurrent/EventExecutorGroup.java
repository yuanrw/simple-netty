package com.simple.netty.common.concurrent;

import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * nio线程池
 * 可以提交任务、定时任务
 * 并且管理他们的生命周期
 * Date: 2020-01-05
 * Time: 10:41
 *
 * @author yrw
 */
public interface EventExecutorGroup extends ScheduledExecutorService, Iterable<EventExecutor> {

    /**
     * 这个{@link EventExecutorGroup}里所有的{@link EventExecutor}都处于
     * shutting down或者shut down状态
     *
     * @return
     */
    boolean isShuttingDown();

    Future<?> shutdownGracefully();

    /**
     * 关闭线程池，一旦调用这个方法，isShuttingDown()会返回true
     * 线程池在一定时间（quietPeriod）内没有任务提交，会关闭
     * 一旦有任务提交quietPeriod就会重新计时
     *
     * @param quietPeriod
     * @param timeout     等待的超时时间
     * @param unit
     * @return
     */
    Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit);

    /**
     * 当所有的{@link EventExecutor}都结束时会收到通知
     *
     * @return
     */
    Future<?> terminationFuture();

    /**
     * 获取一个可用的{@link EventExecutor}
     *
     * @return
     */
    EventExecutor next();

    @Override
    Iterator<EventExecutor> iterator();

    //提交普通任务

    @Override
    Future<?> submit(Runnable task);

    @Override
    <T> Future<T> submit(Runnable task, T result);

    @Override
    <T> Future<T> submit(Callable<T> task);

    //提交定时任务

    @Override
    ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit);

    @Override
    <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit);

    @Override
    ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit);

    @Override
    ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit);
}
