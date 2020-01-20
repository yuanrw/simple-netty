package com.simple.netty.transport.channel.socket;

import com.simple.netty.common.internal.ObjectUtil;
import com.simple.netty.transport.channel.DefaultChannelConfig;

import java.net.Socket;
import java.net.SocketException;

/**
 * Date: 2020-01-12
 * Time: 22:28
 *
 * @author yrw
 */
public class DefaultSocketChannelConfig extends DefaultChannelConfig implements SocketChannelConfig {

    protected final Socket javaSocket;

    public DefaultSocketChannelConfig(SocketChannel channel, Socket javaSocket) {
        super(channel);
        this.javaSocket = ObjectUtil.checkNotNull(javaSocket, "javaSocket");
    }

    @Override
    public int getSendBufferSize() {
        try {
            return javaSocket.getSendBufferSize();
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public SocketChannelConfig setSendBufferSize(int sendBufferSize) {
        try {
            javaSocket.setSendBufferSize(sendBufferSize);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    @Override
    public int getReceiveBufferSize() {
        try {
            return javaSocket.getReceiveBufferSize();
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public SocketChannelConfig setReceiveBufferSize(int receiveBufferSize) {
        try {
            javaSocket.setReceiveBufferSize(receiveBufferSize);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
        return this;
    }
}
