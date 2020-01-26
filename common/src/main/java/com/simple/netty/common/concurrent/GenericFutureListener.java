package com.simple.netty.common.concurrent;

import java.util.EventListener;

/**
 * Date: 2020-01-05
 * Time: 14:08
 *
 * @author yrw
 */
public interface GenericFutureListener<F extends Future<?>> extends EventListener {

    /**
     * future完成的时候会调用这个回调方法
     *
     * @param future
     * @throws Exception
     */
    void operationComplete(F future) throws Exception;
}
