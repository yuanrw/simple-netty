package com.simple.netty.common.concurrent;

/**
 * 定时任务的异步返回结果
 * Date: 2020-01-22
 * Time: 15:33
 *
 * @author yrw
 */
@SuppressWarnings("ClassNameSameAsAncestorName")
public interface ScheduledFuture<V> extends Future<V>, java.util.concurrent.ScheduledFuture<V> {
}
