package com.simple.netty.transport.channel.socket;

import com.simple.netty.common.internal.ObjectUtil;
import com.simple.netty.transport.channel.DefaultChannelConfig;
import com.simple.netty.transport.channel.nio.ServerSocketChannelConfig;

import java.net.ServerSocket;
import java.net.SocketException;

import static com.simple.netty.common.internal.ObjectUtil.checkPositiveOrZero;

/**
 * Date: 2020-01-12
 * Time: 22:33
 *
 * @author yrw
 */
public class DefaultServerSocketChannelConfig extends DefaultChannelConfig implements ServerSocketChannelConfig {

    protected final ServerSocket javaSocket;
    private volatile int backlog = 128;

    public DefaultServerSocketChannelConfig(ServerSocketChannel channel, ServerSocket javaSocket) {
        super(channel);
        this.javaSocket = ObjectUtil.checkNotNull(javaSocket, "javaSocket");
    }

    @Override
    public int getBacklog() {
        return backlog;
    }

    @Override
    public ServerSocketChannelConfig setBacklog(int backlog) {
        checkPositiveOrZero(backlog, "backlog");
        this.backlog = backlog;
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
    public ServerSocketChannelConfig setReceiveBufferSize(int receiveBufferSize) {
        try {
            javaSocket.setReceiveBufferSize(receiveBufferSize);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
        return this;
    }
}
