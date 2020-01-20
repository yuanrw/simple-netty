package com.simple.netty.transport.channel;

import com.simple.netty.buffer.ByteBufAllocator;
import com.simple.netty.common.internal.ObjectUtil;

import static com.simple.netty.common.internal.ObjectUtil.checkPositiveOrZero;

/**
 * 默认配置
 * Date: 2019-12-30
 * Time: 20:57
 *
 * @author yrw
 */
public class DefaultChannelConfig implements ChannelConfig {

    private static final int DEFAULT_CONNECT_TIMEOUT = 30000;

    protected final Channel channel;
    private volatile ByteBufAllocator allocator = ByteBufAllocator.DEFAULT;
    private volatile int connectTimeoutMillis = DEFAULT_CONNECT_TIMEOUT;

    private static final int DEFAULT_LOW_WATER_MARK = 32 * 1024;
    private static final int DEFAULT_HIGH_WATER_MARK = 64 * 1024;

    public DefaultChannelConfig(Channel channel) {
        this.channel = channel;
    }

    @Override
    public DefaultChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis) {
        checkPositiveOrZero(connectTimeoutMillis, "connectTimeoutMillis");
        this.connectTimeoutMillis = connectTimeoutMillis;
        return this;
    }

    @Override
    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    @Override
    public ByteBufAllocator getAllocator() {
        return allocator;
    }


    @Override
    public DefaultChannelConfig setAllocator(ByteBufAllocator allocator) {
        this.allocator = ObjectUtil.checkNotNull(allocator, "allocator");
        return this;
    }

    @Override
    public int getWriteSpinCount() {
        return 16;
    }

    @Override
    public int getWriteBufferHighWaterMark() {
        return DEFAULT_HIGH_WATER_MARK;
    }

    @Override
    public int getWriteBufferLowWaterMark() {
        return DEFAULT_LOW_WATER_MARK;
    }
}
