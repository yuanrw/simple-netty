package com.simple.netty.transport.channel;

import com.simple.netty.buffer.ByteBufAllocator;

/**
 * Channel配置
 * Date: 2020-01-12
 * Time: 21:29
 *
 * @author yrw
 */
public interface ChannelConfig {

    /**
     * 返回连接超时时间
     *
     * @return
     */
    int getConnectTimeoutMillis();

    /**
     * 设置连接超时时间
     *
     * @param connectTimeoutMillis
     * @return
     */
    ChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis);

    /**
     * 返回循环写次数上限
     *
     * @return
     */
    int getWriteSpinCount();

    /**
     * 返回这个Channel使用的ByteBufAllocator
     *
     * @return
     */
    ByteBufAllocator getAllocator();

    /**
     * 设置这个Channel使用的ByteBufAllocator
     *
     * @param allocator
     * @return
     */
    ChannelConfig setAllocator(ByteBufAllocator allocator);

    /**
     * 返回write buffer的阈值上限，超过这个上限，Channel的isWritable()会返回false
     *
     * @return
     */
    int getWriteBufferHighWaterMark();

    /**
     * @return
     */
    int getWriteBufferLowWaterMark();
}
