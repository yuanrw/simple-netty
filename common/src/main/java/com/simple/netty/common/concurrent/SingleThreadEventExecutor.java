package com.simple.netty.common.concurrent;

import com.simple.netty.common.internal.ObjectUtil;
import com.simple.netty.common.internal.ThreadExecutorMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * 用单个线程实现的reactor线程
 * Date: 2020-01-13
 * Time: 20:07
 *
 * @author yrw
 */
public abstract class SingleThreadEventExecutor extends AbstractEventExecutor {
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
     * 终止future
     */
    private final Promise<?> terminationFuture = new DefaultPromise<Void>(GlobalEventExecutor.INSTANCE);

    protected SingleThreadEventExecutor(EventExecutorGroup parent, Executor executor) {
        super(parent);
        this.maxPendingTasks = 16;
        this.executor = ThreadExecutorMap.apply(executor, this);
        taskQueue = newTaskQueue(this.maxPendingTasks);
    }

    /**
     * 默认是LinkedBlockingQueue，子类可以重写
     *
     * @param maxPendingTasks
     * @return
     */
    protected Queue<Runnable> newTaskQueue(int maxPendingTasks) {
        return new LinkedBlockingQueue<>(maxPendingTasks);
    }

    /**
     * 从队列中取出一个任务（不会阻塞）
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

    protected Runnable peekTask() {
        assert inEventLoop();
        return taskQueue.peek();
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
     * nio线程的核心逻辑
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
        //如果老状态是未启动，需要启动线程，把任务跑完
        if (ensureThreadStarted(oldState)) {
            return terminationFuture;
        }

        //老状态是启动的
        return terminationFuture();
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

    private void doStartThread() {
        assert thread == null;
        executor.execute(() -> {
            thread = Thread.currentThread();

            try {
                //执行工作
                SingleThreadEventExecutor.this.run();
            } catch (Throwable t) {
                logger.warn("Unexpected exception from an event executor: ", t);
            } finally {
                //最后需要把它shut down
                for (; ; ) {
                    int oldState = state;
                    if (oldState >= ST_SHUTTING_DOWN || STATE_UPDATER.compareAndSet(
                        SingleThreadEventExecutor.this, oldState, ST_SHUTTING_DOWN)) {
                        break;
                    }
                }

                try {
                    //此时线程池状态是ST_SHUTTING_DOWN，此时还可以接受新的任务
                    for (; ; ) {
                        if (runAllTasks()) {
                            break;
                        }
                    }

                    //把状态修改为ST_SHUTDOWN，修改后就不能接受新的任务了
                    for (; ; ) {
                        int oldState = state;
                        if (oldState >= ST_SHUTDOWN || STATE_UPDATER.compareAndSet(
                            SingleThreadEventExecutor.this, oldState, ST_SHUTDOWN)) {
                            break;
                        }
                    }

                    //修改完成，当前状态为ST_SHUTDOWN
                    //现在还有最后一波任务还运行，等这些运行完
                    runAllTasks();
                } finally {
                    //修改状态
                    STATE_UPDATER.set(SingleThreadEventExecutor.this, ST_TERMINATED);

                    threadLock.countDown();

                    //通知future，线程池完成TERMINATE
                    terminationFuture.setSuccess(null);
                }
            }
        });
    }

    protected boolean confirmShutdown() {
        //状态必须是ST_SHUTTING_DOWN
        if (!isShuttingDown()) {
            return false;
        }

        //shutdown必须由自己组内的线程去执行
        if (!inEventLoop()) {
            throw new IllegalStateException("must be invoked from an event loop");
        }

        //跑完所有任务
        if (runAllTasks()) {
            //状态是ST_SHUTDOWN则不会再有新的任务了
            return isShutdown();
        }

        //没有任务了
        return true;
    }

    /**
     * 执行队列里的所有任务
     *
     * @return 没有任务返回false，执行了至少一个任务返回true
     */
    protected boolean runAllTasks() {
        return runAllTasksFrom(taskQueue);
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
        addTask(task);
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
