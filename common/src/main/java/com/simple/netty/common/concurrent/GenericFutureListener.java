package com.simple.netty.common.concurrent;

import java.util.EventListener;

/**
 * Date: 2020-01-05
 * Time: 14:08
 *
 * @author yrw
 */
public interface GenericFutureListener<F extends Future<?>> extends EventListener {

    void operationComplete(F future) throws Exception;
}
