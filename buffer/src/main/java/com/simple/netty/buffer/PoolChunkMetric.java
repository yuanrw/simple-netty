package com.simple.netty.buffer;

/**
 * Date: 2019-12-18
 * Time: 19:58
 *
 * @author yrw
 */
public interface PoolChunkMetric {

    /**
     * Return the percentage of the current usage of the chunk.
     */
    int usage();

    /**
     * Return the size of the chunk in bytes, this is the maximum of bytes that can be served out of the chunk.
     */
    int chunkSize();

    /**
     * Return the number of free bytes in the chunk.
     */
    int freeBytes();
}
