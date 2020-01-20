package com.simple.netty.common.concurrent;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Future的骨架实现（不支持取消）
 * Date: 2020-01-05
 * Time: 11:16
 *
 * @author yrw
 */
public abstract class AbstractFuture<V> implements Future<V> {

    @Override
    public V get() throws InterruptedException, ExecutionException {
        //阻塞的
        await();

        Throwable cause = cause();
        if (cause == null) {
            //没有异常，获取结果并返回
            return getNow();
        }
        if (cause instanceof CancellationException) {
            throw (CancellationException) cause;
        }

        //包装异常
        throw new ExecutionException(cause);
    }

    @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        if (await(timeout, unit)) {
            Throwable cause = cause();
            if (cause == null) {
                return getNow();
            }
            if (cause instanceof CancellationException) {
                throw (CancellationException) cause;
            }
            throw new ExecutionException(cause);
        }
        throw new TimeoutException();
    }
}
