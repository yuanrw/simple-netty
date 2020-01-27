package com.simple.netty.common.concurrent;

import com.simple.netty.common.internal.ObjectUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 一个单线程实现的EventExecutor，单例的
 * 这个自动启动和结束线程，不需要调用shutdown等方法
 * Date: 2020-01-21
 * Time: 11:53
 *
 * @author yrw
 */
public final class GlobalEventExecutor extends AbstractScheduledEventExecutor {
    private static final Logger logger = LoggerFactory.getLogger(GlobalEventExecutor.class);

    //此处变量顺序要注意，interval要先初始化

    private static final long SCHEDULE_QUIET_PERIOD_INTERVAL = TimeUnit.SECONDS.toNanos(1);

    public static final GlobalEventExecutor INSTANCE = new GlobalEventExecutor();

    final BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<>();

    final ScheduledFutureTask<Void> quietPeriodTask = new ScheduledFutureTask<>(
        this, () -> {}, ScheduledFutureTask.deadlineNanos(SCHEDULE_QUIET_PERIOD_INTERVAL), -SCHEDULE_QUIET_PERIOD_INTERVAL);

    /**
     * 线程是否启动
     */
    private final AtomicBoolean started = new AtomicBoolean();

    volatile Thread thread;
    ThreadFactory threadFactory;

    private final Future<?> terminationFuture = new FailedFuture<>(this, new UnsupportedOperationException());

    private GlobalEventExecutor() {
        scheduledTaskQueue().add(quietPeriodTask);
        threadFactory = new DefaultThreadFactory(DefaultThreadFactory.toPoolName(getClass()));
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
    public void shutdown() {
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

                    if (task != quietPeriodTask) {
                        continue;
                    }

                    // 跑完quietPeriodTask之后，任务会自动入scheduledTaskQueue
                    // 如果没有别的任务，就形成taskQueue=empty，scheduledTaskQueue.size = 1的状态
                    // 此时自动关闭

                    Queue<ScheduledFutureTask<?>> scheduledTaskQueue = GlobalEventExecutor.this.scheduledTaskQueue;
                    if (taskQueue.isEmpty() && (scheduledTaskQueue == null || scheduledTaskQueue.size() == 1)) {
                        //把线程标记为stop，一定会成功因为只有一个线程
                        boolean stopped = started.compareAndSet(true, false);
                        assert stopped;

                        // 再次判断是否有新任务提交进来了
                        if (taskQueue.isEmpty() && (scheduledTaskQueue == null || scheduledTaskQueue.size() == 1)) {
                            break;
                        }

                        // 有新任务，把线程取消stop
                        if (!started.compareAndSet(false, true)) {
                            //修改没成功，说明startThread()已被调用，新的线程已经将start改为true
                            //这个线程就可以退休了
                            break;
                        }

                        //修改成功，说明新的线程还没来得及启动，那就继续用这个线程
                        //新的线程调用startThread()不会创新新的线程
                    }
                }
            };

            //创建一个线程开始工作
            final Thread t = threadFactory.newThread(taskRunner);

            // 在启动线程前set，否则inEventLoop() 可能会返回false
            thread = t;
            t.start();
        }
    }

    /**
     * 获取下一个任务，没有会阻塞，除非添加了WAKEUP_TASK
     * 和SingleThreadEventExecutor逻辑基本一致
     *
     * @return
     */
    private Runnable takeTask() {
        BlockingQueue<Runnable> taskQueue = this.taskQueue;
        for (; ; ) {
            ScheduledFutureTask<?> scheduledTask = peekScheduledTask();
            if (scheduledTask == null) {
                Runnable task = null;
                try {
                    //此处block，但是scheduledTask为空，taskQueue就一定不为空
                    task = taskQueue.take();
                } catch (Exception e) {
                    // 打断忽略
                }
                return task;
            } else {
                long delayNanos = scheduledTask.delayNanos();
                Runnable task = null;
                if (delayNanos > 0) {
                    try {
                        task = taskQueue.poll(delayNanos, TimeUnit.NANOSECONDS);
                    } catch (InterruptedException e) {
                        // Waken up.
                        return null;
                    }
                }

                //如果delayNanos=0，代表定时任务立即要执行，此时一定要fetch
                if (task == null) {
                    fetchFromScheduledTaskQueue();
                    task = taskQueue.poll();
                }

                if (task != null) {
                    return task;
                }
            }
        }
    }

    private void fetchFromScheduledTaskQueue() {
        long nanoTime = AbstractScheduledEventExecutor.nanoTime();
        Runnable scheduledTask = pollScheduledTask(nanoTime);
        while (scheduledTask != null) {
            taskQueue.add(scheduledTask);
            scheduledTask = pollScheduledTask(nanoTime);
        }
    }
}
