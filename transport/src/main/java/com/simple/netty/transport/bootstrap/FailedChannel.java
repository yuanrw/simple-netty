package com.simple.netty.transport.bootstrap;

import com.simple.netty.transport.channel.*;

import java.net.SocketAddress;

/**
 * Date: 2020-01-21
 * Time: 15:38
 *
 * @author yrw
 */
final class FailedChannel extends AbstractChannel {
    private final ChannelConfig config = new DefaultChannelConfig(this);

    FailedChannel() {
        super(null);
    }

    @Override
    protected AbstractUnsafe newUnsafe() {
        return new FailedChannelUnsafe();
    }

    @Override
    protected SocketAddress localAddress0() {
        return null;
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return null;
    }

    @Override
    protected void doBind(SocketAddress localAddress) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void doDisconnect() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void doClose() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void doBeginRead() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelConfig config() {
        return config;
    }

    @Override
    public boolean isOpen() {
        return false;
    }

    @Override
    public boolean isActive() {
        return false;
    }

    private final class FailedChannelUnsafe extends AbstractUnsafe {
        @Override
        public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
            promise.setFailure(new UnsupportedOperationException());
        }
    }
}
