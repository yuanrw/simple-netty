package com.simple.netty.common.concurrent;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.Assert.fail;

/**
 * Date: 2020-01-27
 * Time: 16:19
 *
 * @author yrw
 */
public class SingleThreadEventExecutorTest {

    @Test
    public void testWrappedExecutorIsShutdown() {
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        SingleThreadEventExecutor executor = new SingleThreadEventExecutor(null, executorService) {
            @Override
            protected void run() {
                while (!confirmShutdown()) {
                    Runnable task = takeTask();
                    if (task != null) {
                        task.run();
                    }
                }
            }
        };

        executorService.shutdownNow();
        executeShouldFail(executor);
        executeShouldFail(executor);
        try {
            executor.shutdownGracefully().sync();
            Assert.fail();
        } catch (RejectedExecutionException expected) {
            // 已经shutdown，不能再shutdown
        } catch (InterruptedException e) {
            fail();
        }
        Assert.assertTrue(executor.isShutdown());
    }

    private static void executeShouldFail(Executor executor) {
        try {
            executor.execute(() -> {});
            Assert.fail();
        } catch (RejectedExecutionException expected) {
            // 无法提交任务
        }
    }
}
