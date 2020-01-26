package com.simple.netty.common.concurrent;

import com.simple.netty.common.internal.ObjectUtil;
import com.simple.netty.common.internal.ThreadExecutorMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * 用单个线程实现的reactor线程
 * Date: 2020-01-13
 * Time: 20:07
 *
 * @author yrw
 */
public abstract class SingleThreadEventExecutor extends AbstractScheduledEventExecutor {
    private static Logger logger = LoggerFactory.getLogger(SingleThreadEventExecutor.class);

    /**
     * 存放任务的队列
     */
    private final Queue<Runnable> taskQueue;
    private final CountDownLatch threadLock = new CountDownLatch(1);

    /**
     * 线程的状态变量
     */
    private static final int ST_NOT_STARTED = 1;
    private static final int ST_STARTED = 2;
    private static final int ST_SHUTTING_DOWN = 3;
    private static final int ST_SHUTDOWN = 4;
    private static final int ST_TERMINATED = 5;

    /**
     * 当前状态
     */
    private volatile int state = ST_NOT_STARTED;
    private static final AtomicIntegerFieldUpdater<SingleThreadEventExecutor> STATE_UPDATER =
        AtomicIntegerFieldUpdater.newUpdater(SingleThreadEventExecutor.class, "state");


    private volatile Thread thread;
    private final Executor executor;

    /**
     * 等待任务数量上限
     */
    private final int maxPendingTasks;

    /**
     * 上次执行时间
     */
    private long lastExecutionTime;

    /**
     * 标记是否要打断
     */
    private volatile boolean interrupted;

    /**
     * 终止future
     */
    private final Promise<?> terminationFuture = new DefaultPromise<Void>(GlobalEventExecutor.INSTANCE);

    /**
     * shutdown参数
     */
    private volatile long gracefulShutdownQuietPeriod;
    private volatile long gracefulShutdownTimeout;

    /**
     * gracefulShutdown()被调用的时间
     */
    private long gracefulShutdownStartTime;

    protected SingleThreadEventExecutor(EventExecutorGroup parent, Executor executor) {
        this(parent, executor, 16);
    }

    protected SingleThreadEventExecutor(EventExecutorGroup parent, Executor executor, int maxPendingTasks) {
        super(parent);
        this.maxPendingTasks = maxPendingTasks;
        //todo:
        this.executor = ThreadExecutorMap.apply(executor, this);
        taskQueue = newTaskQueue(this.maxPendingTasks);
    }

    /**
     * 任务队列默认是LinkedBlockingQueue，子类可以重写
     *
     * @param maxPendingTasks 队列最大长度
     * @return
     */
    protected Queue<Runnable> newTaskQueue(int maxPendingTasks) {
        return new LinkedBlockingQueue<>(maxPendingTasks);
    }

    /**
     * 打断线程
     */
    protected void interruptThread() {
        Thread currentThread = thread;
        if (currentThread == null) {
            //标记打断
            interrupted = true;
        } else {
            currentThread.interrupt();
        }
    }

    /**
     * 从队列中取出一个任务
     *
     * @return
     */
    protected Runnable pollTask() {
        assert inEventLoop();
        return pollTaskFrom(taskQueue);
    }

    protected static Runnable pollTaskFrom(Queue<Runnable> taskQueue) {
        return taskQueue.poll();
    }

    /**
     * 获取下一个任务，没有会阻塞，除非添加了WAKEUP_TASK
     *
     * @return
     */
    protected Runnable takeTask() {
        assert inEventLoop();
        //如果被子类重写，任务队列不支持block，抛异常
        if (!(taskQueue instanceof BlockingQueue)) {
            throw new UnsupportedOperationException();
        }

        BlockingQueue<Runnable> taskQueue = (BlockingQueue<Runnable>) this.taskQueue;
        for (; ; ) {
            //获取第一个定时任务（不取出）
            ScheduledFutureTask<?> scheduledTask = peekScheduledTask();
            if (scheduledTask == null) {
                //没有定时任务
                Runnable task = null;
                try {
                    //取出普通任务
                    task = taskQueue.take();
                    if (task == WAKEUP_TASK) {
                        task = null;
                    }
                } catch (InterruptedException e) {
                    // Ignore
                }
                return task;
            } else {
                //有定时任务，获取距离下一次执行的时间
                long delayNanos = scheduledTask.delayNanos();
                Runnable task = null;
                if (delayNanos > 0) {
                    try {
                        //取出定时任务（会有另一个线程把任务放进队列）
                        task = taskQueue.poll(delayNanos, TimeUnit.NANOSECONDS);
                    } catch (InterruptedException e) {
                        // Waken up.
                        return null;
                    }
                }

                //没有任务
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

    /**
     * 把任务从scheduledTaskQueue取出来，放入taskQueue中
     * 如果taskQueue放不下了，放回scheduledTaskQueue，等待下次处理
     *
     * @return
     */
    private boolean fetchFromScheduledTaskQueue() {
        if (scheduledTaskQueue == null || scheduledTaskQueue.isEmpty()) {
            return true;
        }
        long nanoTime = AbstractScheduledEventExecutor.nanoTime();
        for (; ; ) {
            Runnable scheduledTask = pollScheduledTask(nanoTime);
            if (scheduledTask == null) {
                return true;
            }
            if (!taskQueue.offer(scheduledTask)) {
                // No space left in the task queue add it back to the scheduledTaskQueue so we pick it up again.
                scheduledTaskQueue.add((ScheduledFutureTask<?>) scheduledTask);
                return false;
            }
        }
    }

    protected boolean hasTasks() {
        assert inEventLoop();
        return !taskQueue.isEmpty();
    }

    public int pendingTasks() {
        return taskQueue.size();
    }

    protected void addTask(Runnable task) {
        ObjectUtil.checkNotNull(task, "task");
        if (!offerTask(task)) {
            logger.warn("abandon task");
            //todo：拒绝任务
        }
    }

    final boolean offerTask(Runnable task) {
        if (isShutdown()) {
            logger.warn("abandon task");
            //todo：拒绝任务
        }
        return taskQueue.offer(task);
    }

    /**
     * nio线程的核心逻辑（通常是死循环，除非被打断）
     */
    protected abstract void run();

    @Override
    public boolean inEventLoop(Thread thread) {
        return thread == this.thread;
    }

    @Override
    public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
        ObjectUtil.checkPositiveOrZero(quietPeriod, "quietPeriod");
        if (timeout < quietPeriod) {
            throw new IllegalArgumentException(
                "timeout: " + timeout + " (expected >= quietPeriod (" + quietPeriod + "))");
        }

        ObjectUtil.checkNotNull(unit, "unit");

        if (isShuttingDown()) {
            return terminationFuture();
        }

        boolean inEventLoop = inEventLoop();
        int oldState;

        //把状态切换成 >= ST_SHUTTING_DOWN
        for (; ; ) {
            if (isShuttingDown()) {
                return terminationFuture();
            }
            int newState;
            //获取当前状态
            oldState = state;
            if (inEventLoop) {
                newState = ST_SHUTTING_DOWN;
            } else {
                switch (oldState) {
                    case ST_NOT_STARTED:
                    case ST_STARTED:
                        newState = ST_SHUTTING_DOWN;
                        break;
                    default:
                        newState = oldState;
                }
            }

            //修改状态
            if (STATE_UPDATER.compareAndSet(this, oldState, newState)) {
                break;
            }
        }

        //状态切换完毕
        //确保线程启动，才能做shutdown
        if (ensureThreadStarted(oldState)) {
            return terminationFuture;
        }

        //run方法可能是block的，唤醒它
        taskQueue.offer(WAKEUP_TASK);

        //老状态是启动的
        return terminationFuture();
    }

    @Override
    @Deprecated
    public void shutdown() {
        throw new UnsupportedOperationException();
    }

    private void startThread() {
        if (state == ST_NOT_STARTED) {
            //修改状态
            if (STATE_UPDATER.compareAndSet(this, ST_NOT_STARTED, ST_STARTED)) {
                boolean success = false;
                try {
                    //启动一个新的线程
                    doStartThread();
                    success = true;
                } finally {
                    if (!success) {
                        STATE_UPDATER.compareAndSet(this, ST_STARTED, ST_NOT_STARTED);
                    }
                }
            }
        }

        //已经启动，直接返回
    }

    private boolean ensureThreadStarted(int oldState) {
        if (oldState == ST_NOT_STARTED) {
            try {
                doStartThread();
            } catch (Throwable cause) {
                STATE_UPDATER.set(this, ST_TERMINATED);
                terminationFuture.tryFailure(cause);
                return true;
            }
        }
        return false;
    }

    protected void updateLastExecutionTime() {
        lastExecutionTime = ScheduledFutureTask.nanoTime();
    }

    private void doStartThread() {
        assert thread == null;
        executor.execute(() -> {
            thread = Thread.currentThread();
            if (interrupted) {
                thread.interrupt();
            }

            boolean success = false;
            updateLastExecutionTime();
            try {
                //核心逻辑
                SingleThreadEventExecutor.this.run();
                success = true;
            } catch (Throwable t) {
                logger.warn("Unexpected exception from an event executor: ", t);
            } finally {
                //关闭event loop
                for (; ; ) {
                    int oldState = state;
                    if (oldState >= ST_SHUTTING_DOWN || STATE_UPDATER.compareAndSet(
                        SingleThreadEventExecutor.this, oldState, ST_SHUTTING_DOWN)) {
                        break;
                    }
                }

                // 如果confirmShutdown()没有被调用过，打日志
                if (success && gracefulShutdownStartTime == 0) {
                    if (logger.isErrorEnabled()) {
                        logger.error("Buggy " + EventExecutor.class.getSimpleName() + " implementation; " +
                            SingleThreadEventExecutor.class.getSimpleName() + ".confirmShutdown() must " +
                            "be called before run() implementation terminates.");
                    }
                }

                //shutdown流程
                try {
                    // 把剩下的所有remaining tasks 和shutdown hooks跑完，当前状态是ST_SHUTTING_DOWN
                    // 可以接受新任务，直到有长达quietTime都没有新任务了
                    for (; ; ) {
                        if (confirmShutdown()) {
                            break;
                        }
                    }

                    //修改状态，此时不能接受新任务了
                    for (; ; ) {
                        int oldState = state;
                        if (oldState >= ST_SHUTDOWN || STATE_UPDATER.compareAndSet(
                            SingleThreadEventExecutor.this, oldState, ST_SHUTDOWN)) {
                            break;
                        }
                    }

                    // 把最后一波任务跑完
                    confirmShutdown();
                } finally {
                    STATE_UPDATER.set(SingleThreadEventExecutor.this, ST_TERMINATED);
                    threadLock.countDown();
                    int numUserTasks = drainTasks();
                    if (numUserTasks > 0 && logger.isWarnEnabled()) {
                        logger.warn("An event executor terminated with " +
                            "non-empty task queue (" + numUserTasks + ')');
                    }

                    //通知future
                    terminationFuture.setSuccess(null);
                }
            }
        });
    }

    protected boolean confirmShutdown() {
        if (!isShuttingDown()) {
            return false;
        }

        if (!inEventLoop()) {
            throw new IllegalStateException("must be invoked from an event loop");
        }

        //取消定时任务，如果不取消，scheduledTaskQueue不会清空
        cancelScheduledTasks();

        //记录调用gracefulShutdown()的时间
        if (gracefulShutdownStartTime == 0) {
            gracefulShutdownStartTime = ScheduledFutureTask.nanoTime();
        }

        //跑完所有任务（阻塞的）
        if (runAllTasks()) {
            if (isShutdown()) {
                // 这个状态不会再有新的任务了
                return true;
            }

            //队列里还有任务，需要再等待
            //如果gracefulShutdownQuietPeriod = 0，立刻终止
            //todo:
            if (gracefulShutdownQuietPeriod == 0) {
                return true;
            }

            //让taskTask()返回null
            taskQueue.offer(WAKEUP_TASK);
            return false;
        }

        final long nanoTime = ScheduledFutureTask.nanoTime();

        if (isShutdown() || nanoTime - gracefulShutdownStartTime > gracefulShutdownTimeout) {
            return true;
        }

        if (nanoTime - lastExecutionTime <= gracefulShutdownQuietPeriod) {
            // Check if any tasks were added to the queue every 100ms.
            // TODO: Change the behavior of takeTask() so that it returns on timeout.
            taskQueue.offer(WAKEUP_TASK);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // Ignore
            }

            return false;
        }

        // No tasks were added for last quiet period - hopefully safe to shut down.
        // (Hopefully because we really cannot make a guarantee that there will be no execute() calls by a user.)
        return true;
    }

    /**
     * 计算队列中的任务
     *
     * @return
     */
    final int drainTasks() {
        int numTasks = 0;
        for (; ; ) {
            Runnable runnable = taskQueue.poll();
            if (runnable == null) {
                break;
            }
            // WAKEUP_TASK should be just discarded as these are added internally.
            // The important bit is that we not have any user tasks left.
            if (WAKEUP_TASK != runnable) {
                numTasks++;
            }
        }
        return numTasks;
    }

    protected boolean removeTask(Runnable task) {
        return taskQueue.remove(ObjectUtil.checkNotNull(task, "task"));
    }

    /**
     * 执行队列里的所有任务
     *
     * @return 没有任务返回false，执行了至少一个任务返回true
     */
    protected boolean runAllTasks() {
        assert inEventLoop();
        boolean fetchedAll;
        boolean ranAtLeastOne = false;

        do {
            fetchedAll = fetchFromScheduledTaskQueue();
            if (runAllTasksFrom(taskQueue)) {
                ranAtLeastOne = true;
            }
        } while (!fetchedAll);
        // keep on processing until we fetched all scheduled tasks.

        if (ranAtLeastOne) {
            lastExecutionTime = ScheduledFutureTask.nanoTime();
        }
        return ranAtLeastOne;
    }

    protected final boolean runAllTasksFrom(Queue<Runnable> taskQueue) {
        Runnable task = pollTaskFrom(taskQueue);
        if (task == null) {
            return false;
        }
        for (; ; ) {
            safeExecute(task);
            task = pollTaskFrom(taskQueue);
            if (task == null) {
                return true;
            }
        }
    }

    @Override
    public boolean isShuttingDown() {
        return state >= ST_SHUTTING_DOWN;
    }

    @Override
    public boolean isShutdown() {
        return state >= ST_SHUTDOWN;
    }

    @Override
    public boolean isTerminated() {
        return state == ST_TERMINATED;
    }

    @Override
    public void execute(Runnable task) {
        ObjectUtil.checkNotNull(task, "task");

        boolean inEventLoop = inEventLoop();

        //加入队列
        addTask(task);

        if (!inEventLoop) {
            //启动线程，这个方法不阻塞，会创建新线程去做核心逻辑
            startThread();

            //已经shutdown需要从队列中移除任务
            if (isShutdown()) {
                boolean reject = false;
                try {
                    if (removeTask(task)) {
                        reject = true;
                    }
                } catch (UnsupportedOperationException e) {
                    // The task queue does not support removal so the best thing we can do is to just move on and
                    // hope we will be able to pick-up the task before its completely terminated.
                    // In worst case we will log on termination.
                }
                if (reject) {
                    logger.warn("abandon task");
                }
            }
        }
    }

    @Override
    public Future<?> terminationFuture() {
        return terminationFuture;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        ObjectUtil.checkNotNull(unit, "unit");
        if (inEventLoop()) {
            throw new IllegalStateException("cannot await termination of the current thread");
        }

        threadLock.await(timeout, unit);

        return isTerminated();
    }
}
