package com.simple.netty.buffer;

/**
 * Date: 2019-12-18
 * Time: 19:59
 *
 * @author yrw
 */
public interface PoolChunkListMetric extends Iterable<PoolChunkMetric> {

    /**
     * Return the minimum usage of the chunk list before which chunks are promoted to the previous list.
     */
    int minUsage();

    /**
     * Return the maximum usage of the chunk list after which chunks are promoted to the next list.
     */
    int maxUsage();
}
