package com.simple.netty.transport.channel.nio;

import com.simple.netty.transport.channel.ChannelConfig;

/**
 * 服务端Channel配置
 * Date: 2020-01-12
 * Time: 22:01
 *
 * @author yrw
 */
public interface ServerSocketChannelConfig extends ChannelConfig {

    /**
     * Gets the backlog value to specify when the channel binds to a local
     * address.
     */
    int getBacklog();

    /**
     * Sets the backlog value to specify when the channel binds to a local
     * address.
     */
    ServerSocketChannelConfig setBacklog(int backlog);

    /**
     * Gets the {@link StandardSocketOptions#SO_RCVBUF} option.
     */
    int getReceiveBufferSize();

    /**
     * Gets the {@link StandardSocketOptions#SO_SNDBUF} option.
     */
    ServerSocketChannelConfig setReceiveBufferSize(int receiveBufferSize);
}
