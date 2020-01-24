package com.simple.netty.common.concurrent;

/**
 * Future的扩展，可以写入IO结果（设置成功失败）
 * 每次发起IO操作会创建一个新的Promise对象
 * Date: 2020-01-05
 * Time: 11:08
 *
 * @author yrw
 */
public interface Promise<V> extends com.simple.netty.common.concurrent.Future<V> {

    /**
     * 标记为成功并通知所有listener
     * <p>
     * 如果Future已经成功或者和失败了会抛出 {@link IllegalStateException}.
     */
    Promise<V> setSuccess(V result);

    /**
     * 标记成功并通知所有listener
     *
     * @return
     */
    boolean trySuccess(V result);

    /**
     * 标记失败并通知所有listeners
     * <p>
     * 如果Future已经成功或者和失败了会抛出 {@link IllegalStateException}.
     */
    Promise<V> setFailure(Throwable cause);

    boolean tryFailure(Throwable cause);

    @Override
    Promise<V> addListener(GenericFutureListener<? extends Future<? super V>> listener);

    @Override
    Promise<V> addListeners(GenericFutureListener<? extends Future<? super V>>... listeners);

    @Override
    Promise<V> removeListener(GenericFutureListener<? extends Future<? super V>> listener);

    @Override
    Promise<V> removeListeners(GenericFutureListener<? extends Future<? super V>>... listeners);

    @Override
    Promise<V> await() throws InterruptedException;

    @Override
    Promise<V> sync() throws InterruptedException;
}
