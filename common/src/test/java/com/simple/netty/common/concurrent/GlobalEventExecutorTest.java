package com.simple.netty.common.concurrent;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * Date: 2020-01-26
 * Time: 14:21
 *
 * @author yrw
 */
public class GlobalEventExecutorTest {

    private static final GlobalEventExecutor e = GlobalEventExecutor.INSTANCE;

    @Before
    public void setUp() throws Exception {
        // 确保GlobalEventExecutor停止了
        for (; ; ) {
            if (e.thread == null || !e.thread.isAlive()) {
                break;
            }

            Thread.sleep(50);
        }
    }

    @Test(timeout = 5000)
    public void testAutomaticStartStop() throws Exception {
        final TestRunnable task = new TestRunnable(500);
        e.execute(task);

        // 检测线程启动
        Thread thread = e.thread;
        assertThat(thread, is(not(nullValue())));
        assertThat(thread.isAlive(), is(true));

        //跑完任务后自动关闭
        thread.join();
        assertThat(task.ran.get(), is(true));

        // 检测启动新线程
        task.ran.set(false);
        e.execute(task);
        assertThat(e.thread, not(sameInstance(thread)));
        thread = e.thread;

        //关闭
        thread.join();
        assertThat(task.ran.get(), is(true));
    }

    @Test(timeout = 5000)
    public void testScheduledTasks() throws Exception {
        TestRunnable task = new TestRunnable(0);
        ScheduledFuture<?> f = e.schedule(task, 1500, TimeUnit.MILLISECONDS);
        f.sync();
        // check跑了任务
        assertThat(task.ran.get(), is(true));

        // 线程还在
        Thread thread = e.thread;
        assertThat(thread, is(not(nullValue())));
        assertThat(thread.isAlive(), is(true));

        //后面会正常关闭
        thread.join();
    }

    @Test(timeout = 2000)
    public void testThreadGroup() throws InterruptedException {
        // 如果一个任务的提交导致创建了新的线程, 新线程必须在thread group里面
        final ThreadGroup group = new ThreadGroup("group");
        final AtomicReference<ThreadGroup> capturedGroup = new AtomicReference<>();
        final Thread thread = new Thread(group, () -> {
            final Thread t = e.threadFactory.newThread(() -> {
            });
            capturedGroup.set(t.getThreadGroup());
        });
        thread.start();
        thread.join();

        assertEquals(group, capturedGroup.get());
    }

    private static final class TestRunnable implements Runnable {
        final AtomicBoolean ran = new AtomicBoolean();
        final long delay;

        TestRunnable(long delay) {
            this.delay = delay;
        }

        @Override
        public void run() {
            try {
                Thread.sleep(delay);
                ran.set(true);
            } catch (InterruptedException ignored) {
                // Ignore
            }
        }
    }
}
