package com.simple.netty.common.concurrent;

import com.simple.netty.common.internal.ObjectUtil;
import com.simple.netty.common.internal.ThreadExecutorMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 一个单线程实现的EventExecutor，单例的
 * 自动启动线程
 * 不要去扩展它的功能
 * Date: 2020-01-21
 * Time: 11:53
 *
 * @author yrw
 */
public final class GlobalEventExecutor extends AbstractEventExecutor {
    private static final Logger logger = LoggerFactory.getLogger(GlobalEventExecutor.class);

    public static final GlobalEventExecutor INSTANCE = new GlobalEventExecutor();

    final BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<>();

    /**
     * 线程是否启动
     */
    private final AtomicBoolean started = new AtomicBoolean();

    volatile Thread thread;
    private ThreadFactory threadFactory;

    private final Future<?> terminationFuture = new FailedFuture<>(this, new UnsupportedOperationException());

    private GlobalEventExecutor() {
        threadFactory = ThreadExecutorMap.apply(new DefaultThreadFactory(
            DefaultThreadFactory.toPoolName(getClass())), this);
    }

    @Override
    public boolean inEventLoop(Thread thread) {
        return thread == this.thread;
    }

    @Override
    public boolean isShuttingDown() {
        return false;
    }

    @Override
    public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
        return terminationFuture();
    }

    @Override
    public Future<?> terminationFuture() {
        return terminationFuture;
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void shutdown() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Runnable> shutdownNow() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isShutdown() {
        return false;
    }

    @Override
    public boolean isTerminated() {
        return false;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return false;
    }

    @Override
    public void execute(Runnable task) {
        addTask(ObjectUtil.checkNotNull(task, "task"));
        if (!inEventLoop()) {
            startThread();
        }
    }

    /**
     * Add a task to the task queue, or throws a {@link RejectedExecutionException} if this instance was shutdown
     * before.
     */
    private void addTask(Runnable task) {
        taskQueue.add(ObjectUtil.checkNotNull(task, "task"));
    }

    private void startThread() {
        if (started.compareAndSet(false, true)) {
            Runnable taskRunner = () -> {
                for (; ; ) {
                    Runnable task = takeTask();
                    if (task != null) {
                        try {
                            task.run();
                        } catch (Throwable t) {
                            logger.warn("Unexpected exception from the global event executor: ", t);
                        }
                    }
                }
            };

            //启动工作线程
            final Thread t = threadFactory.newThread(taskRunner);

            // Set the thread before starting it as otherwise inEventLoop() may return false and so produce
            // an assert error.
            thread = t;
            t.start();
        }
    }

    Runnable takeTask() {
        BlockingQueue<Runnable> taskQueue = this.taskQueue;
        for (; ; ) {
            Runnable task = taskQueue.poll();
            if (task != null) {
                return task;
            }
        }
    }
}
