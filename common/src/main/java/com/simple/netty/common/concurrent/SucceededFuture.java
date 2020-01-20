package com.simple.netty.common.concurrent;

/**
 * 成功的CompleteFuture
 * Date: 2020-01-05
 * Time: 11:56
 *
 * @author yrw
 */
public final class SucceededFuture<V> extends CompleteFuture<V> {

    /**
     * 执行结果
     */
    private final V result;

    /**
     * Creates a new instance.
     *
     * @param executor the {@link EventExecutor} associated with this future
     */
    public SucceededFuture(EventExecutor executor, V result) {
        super(executor);
        this.result = result;
    }

    @Override
    public Throwable cause() {
        return null;
    }

    @Override
    public boolean isSuccess() {
        return true;
    }

    @Override
    public V getNow() {
        return result;
    }

}
