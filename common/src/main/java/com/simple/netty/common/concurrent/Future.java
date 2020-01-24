package com.simple.netty.common.concurrent;

import java.util.concurrent.TimeUnit;

/**
 * 异步操作的结果
 * Date: 2020-01-05
 * Time: 11:08
 *
 * @author yrw
 */
@SuppressWarnings("ClassNameSameAsAncestorName")
public interface Future<V> extends java.util.concurrent.Future<V> {

    boolean isSuccess();

    Throwable cause();

    Future<V> addListener(GenericFutureListener<? extends Future<? super V>> listener);

    Future<V> addListeners(GenericFutureListener<? extends Future<? super V>>... listeners);

    Future<V> removeListener(GenericFutureListener<? extends Future<? super V>> listener);

    Future<V> removeListeners(GenericFutureListener<? extends Future<? super V>>... listeners);

    Future<V> sync() throws InterruptedException;

    Future<V> await() throws InterruptedException;

    boolean await(long timeout, TimeUnit unit) throws InterruptedException;

    boolean await(long timeoutMillis) throws InterruptedException;

    V getNow();

    @Override
    default boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }
    @Override
    default boolean isCancelled() {
        return false;
    }
}
