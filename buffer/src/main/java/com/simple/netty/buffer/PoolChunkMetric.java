package com.simple.netty.buffer;

/**
 * Date: 2019-12-18
 * Time: 19:58
 *
 * @author yrw
 */
public interface PoolChunkMetric {

    /**
     * 返回这个chunk使用的百分比
     *
     * @return
     */
    int usage();

    /**
     * 返回这个chunk的最大byte数量
     *
     * @return
     */
    int chunkSize();

    /**
     * 返回这个chunk空闲的byte数量
     *
     * @return
     */
    int freeBytes();
}
