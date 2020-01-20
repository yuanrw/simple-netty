package com.simple.netty.transport.channel.socket;

import com.simple.netty.transport.channel.ChannelConfig;

/**
 * 客户端配置
 * Date: 2020-01-12
 * Time: 21:47
 *
 * @author yrw
 */
public interface SocketChannelConfig extends ChannelConfig {

    int getSendBufferSize();

    SocketChannelConfig setSendBufferSize(int sendBufferSize);

    int getReceiveBufferSize();

    SocketChannelConfig setReceiveBufferSize(int receiveBufferSize);
}
