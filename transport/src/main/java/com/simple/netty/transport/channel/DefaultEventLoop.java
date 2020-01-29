package com.simple.netty.transport.channel;

import com.simple.netty.common.concurrent.DefaultThreadFactory;
import com.simple.netty.common.concurrent.ThreadPerTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Date: 2020-01-28
 * Time: 21:27
 *
 * @author yrw
 */
public class DefaultEventLoop extends SingleThreadEventLoop {

    public DefaultEventLoop() {
        this(new ThreadPerTaskExecutor(new DefaultThreadFactory(DefaultEventLoop.class)));
    }

    public DefaultEventLoop(Executor executor) {
        super(null, executor);
    }

    public DefaultEventLoop(EventLoopGroup parent, Executor executor) {
        super(parent, executor);
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
