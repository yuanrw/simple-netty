package com.simple.netty.common.concurrent;

import com.simple.netty.common.internal.ObjectUtil;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.*;

/**
 * Date: 2020-01-24
 * Time: 16:11
 *
 * @author yrw
 */
public class DefaultPromiseTest {
    private static final Logger logger = LoggerFactory.getLogger(DefaultPromiseTest.class);

    @Test
    public void testCancelDoesNotScheduleWhenNoListeners() {
        EventExecutor executor = Mockito.mock(EventExecutor.class);
        Mockito.when(executor.inEventLoop()).thenReturn(false);

        Promise<Void> promise = new DefaultPromise<>(executor);
        assertTrue(promise.cancel(false));
        Mockito.verify(executor, Mockito.never()).execute(Mockito.any(Runnable.class));
        assertTrue(promise.isCancelled());
    }

    @Test
    public void testSuccessDoesNotScheduleWhenNoListeners() {
        EventExecutor executor = Mockito.mock(EventExecutor.class);
        Mockito.when(executor.inEventLoop()).thenReturn(false);

        Object value = new Object();
        Promise<Object> promise = new DefaultPromise<>(executor);
        promise.setSuccess(value);
        Mockito.verify(executor, Mockito.never()).execute(Mockito.any(Runnable.class));
        assertSame(value, promise.getNow());
    }

    @Test
    public void testFailureDoesNotScheduleWhenNoListeners() {
        EventExecutor executor = Mockito.mock(EventExecutor.class);
        Mockito.when(executor.inEventLoop()).thenReturn(false);

        Exception cause = new Exception();
        Promise<Void> promise = new DefaultPromise<>(executor);
        promise.setFailure(cause);
        Mockito.verify(executor, Mockito.never()).execute(Mockito.any(Runnable.class));
        assertSame(cause, promise.cause());
    }

    @Test(expected = CancellationException.class)
    public void testCancellationExceptionIsThrownWhenBlockingGet() throws InterruptedException, ExecutionException {
        final Promise<Void> promise = new DefaultPromise<>(new TestEventExecutor());
        assertTrue(promise.cancel(false));
        promise.get();
    }

    @Test(expected = CancellationException.class)
    public void testCancellationExceptionIsThrownWhenBlockingGetWithTimeout() throws InterruptedException,
        ExecutionException, TimeoutException {
        final Promise<Void> promise = new DefaultPromise<>(new TestEventExecutor());
        assertTrue(promise.cancel(false));
        promise.get(1, TimeUnit.SECONDS);
    }

    @Test
    public void testCancellationExceptionIsReturnedAsCause() {
        final Promise<Void> promise = new DefaultPromise<>(new TestEventExecutor());
        assertTrue(promise.cancel(false));
        assertThat(promise.cause(), instanceOf(CancellationException.class));
    }

    @Test
    public void testListenerNotifyOrder() throws Exception {
        EventExecutor executor = new TestEventExecutor();
        try {
            final BlockingQueue<FutureListener<Void>> listeners = new LinkedBlockingQueue<FutureListener<Void>>();
            int runs = 100000;

            for (int i = 0; i < runs; i++) {
                final Promise<Void> promise = new DefaultPromise<>(executor);
                final FutureListener<Void> listener1 = new FutureListener<Void>() {
                    @Override
                    public void operationComplete(Future<Void> future) throws Exception {
                        listeners.add(this);
                    }
                };

                final FutureListener<Void> listener2 = new FutureListener<Void>() {
                    @Override
                    public void operationComplete(Future<Void> future) throws Exception {
                        listeners.add(this);
                    }
                };
                final FutureListener<Void> listener4 = new FutureListener<Void>() {
                    @Override
                    public void operationComplete(Future<Void> future) throws Exception {
                        listeners.add(this);
                    }
                };
                final FutureListener<Void> listener3 = new FutureListener<Void>() {
                    @Override
                    public void operationComplete(Future<Void> future) throws Exception {
                        listeners.add(this);
                        future.addListener(listener4);
                    }
                };

                GlobalEventExecutor.INSTANCE.execute(() -> promise.setSuccess(null));

                promise.addListener(listener1).addListener(listener2).addListener(listener3);

                assertSame("Fail 1 during run " + i + " / " + runs, listener1, listeners.take());
                assertSame("Fail 2 during run " + i + " / " + runs, listener2, listeners.take());
                assertSame("Fail 3 during run " + i + " / " + runs, listener3, listeners.take());
                assertSame("Fail 4 during run " + i + " / " + runs, listener4, listeners.take());
                assertTrue("Fail during run " + i + " / " + runs, listeners.isEmpty());
            }
        } finally {
            executor.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync();
        }
    }

    @Test
    public void testListenerNotifyLater() throws Exception {
        // Testing first execution path in DefaultPromise
        testListenerNotifyLater(1);

        // Testing second execution path in DefaultPromise
        testListenerNotifyLater(2);
    }

    @Test(timeout = 2000)
    public void testPromiseListenerAddWhenCompleteFailure() throws Exception {
        testPromiseListenerAddWhenComplete(fakeException());
    }

    @Test(timeout = 2000)
    public void testPromiseListenerAddWhenCompleteSuccess() throws Exception {
        testPromiseListenerAddWhenComplete(null);
    }

    @Test
    public void testLateListenerIsOrderedCorrectlySuccess() throws InterruptedException {
        testLateListenerIsOrderedCorrectly(null);
    }

    @Test(timeout = 2000)
    public void testLateListenerIsOrderedCorrectlyFailure() throws InterruptedException {
        testLateListenerIsOrderedCorrectly(fakeException());
    }

    private static void testLateListenerIsOrderedCorrectly(Throwable cause) throws InterruptedException {
        final EventExecutor executor = new TestEventExecutor();
        try {
            final AtomicInteger state = new AtomicInteger();
            final CountDownLatch latch1 = new CountDownLatch(1);
            final CountDownLatch latch2 = new CountDownLatch(2);
            final Promise<Void> promise = new DefaultPromise<>(executor);

            // complete前加入lister
            promise.addListener((FutureListener<Void>) future ->
                assertTrue(state.compareAndSet(0, 1)));

            // complete
            if (cause == null) {
                promise.setSuccess(null);
            } else {
                promise.setFailure(cause);
            }

            // complete后添加listener
            promise.addListener((FutureListener<Void>) future -> {
                assertTrue(state.compareAndSet(1, 2));
                latch1.countDown();
            });

            // 等待所有的listener被调用
            latch1.await();
            assertEquals(2, state.get());

            // This is the important listener. A late listener that is added after all late listeners
            // have completed, and needs to update state before a read operation (on the same executor).
            executor.execute(() -> promise.addListener((FutureListener<Void>) future -> {
                assertTrue(state.compareAndSet(2, 3));
                latch2.countDown();
            }));

            // 模拟一个读操作
            executor.execute(() -> {
                // This is the key, we depend upon the state being set in the next listener.
                assertEquals(3, state.get());
                latch2.countDown();
            });

            latch2.await();
        } finally {
            executor.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync();
        }
    }

    private static void testPromiseListenerAddWhenComplete(Throwable cause) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final Promise<Void> promise = new DefaultPromise<>(new ImmediateEventExecutor());
        promise.addListener((FutureListener<Void>)
            future -> promise.addListener(
                (FutureListener<Void>) future1 -> latch.countDown()));
        if (cause == null) {
            promise.setSuccess(null);
        } else {
            promise.setFailure(cause);
        }
        latch.await();
    }

    private static void testListenerNotifyLater(final int numListenersBefore) throws Exception {
        EventExecutor executor = new TestEventExecutor();
        int expectedCount = numListenersBefore + 2;
        final CountDownLatch latch = new CountDownLatch(expectedCount);
        final FutureListener<Void> listener = future -> {
            System.out.println("1");
            latch.countDown();
        };
        final Promise<Void> promise = new DefaultPromise<>(executor);

        executor.execute(() -> {
            for (int i = 0; i < numListenersBefore; i++) {
                promise.addListener(listener);
            }
            promise.setSuccess(null);

            //多加两个lister
            GlobalEventExecutor.INSTANCE.execute(() -> promise.addListener(listener));
            promise.addListener(listener);
        });

        assertTrue("Should have notified " + expectedCount + " listeners",
            latch.await(5, TimeUnit.SECONDS));

        executor.shutdownGracefully().sync();
    }

    /**
     * 一旦execute(task)会立即执行
     */
    private static final class ImmediateEventExecutor extends AbstractEventExecutor {

        private final Future<?> terminationFuture = new FailedFuture<>(
            GlobalEventExecutor.INSTANCE, new UnsupportedOperationException());

        @Override
        public boolean inEventLoop(Thread thread) {
            return true;
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
        @Deprecated
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
        public void execute(Runnable command) {
            ObjectUtil.checkNotNull(command, "command");
            try {
                command.run();
            } catch (Throwable cause) {
                logger.info("Throwable caught while executing Runnable {}", command, cause);
            }
        }
    }


    private static final class TestEventExecutor extends SingleThreadEventExecutor {

        protected TestEventExecutor() {
            super(null, GlobalEventExecutor.INSTANCE);
        }

        @Override
        protected void run() {
            for (; ; ) {
                Runnable task = takeTask();
                if (task != null) {
                    task.run();
                    updateLastExecutionTime();
                }

                if (confirmShutdown()) {
                    break;
                }
            }
        }
    }

    private static RuntimeException fakeException() {
        return new RuntimeException("fake exception");
    }
}
