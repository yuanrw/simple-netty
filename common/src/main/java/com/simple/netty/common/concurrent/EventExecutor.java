package com.simple.netty.common.concurrent;

/**
 * nio线程池里的线程
 * Date: 2020-01-05
 * Time: 10:41
 *
 * @author yrw
 */
public interface EventExecutor extends EventExecutorGroup {

    /**
     * 返回自己
     *
     * @return
     */
    @Override
    EventExecutor next();

    /**
     * 返回管理当前线程的{@link EventExecutorGroup}
     *
     * @return
     */
    EventExecutorGroup parent();

    /**
     * 判断当前线程是否在线程池里
     *
     * @return
     */
    boolean inEventLoop();

    /**
     * 判断线程是否在线程池里
     *
     * @param thread
     * @return
     */
    boolean inEventLoop(Thread thread);
}
