package com.simple.netty.transport.bootstrap;

import com.simple.netty.common.internal.ObjectUtil;
import com.simple.netty.transport.channel.Channel;
import com.simple.netty.transport.channel.ChannelHandler;
import com.simple.netty.transport.channel.EventLoopGroup;

import java.net.SocketAddress;

/**
 * Date: 2020-01-21
 * Time: 15:41
 *
 * @author yrw
 */
public abstract class AbstractBootstrapConfig<B extends AbstractBootstrap<B, C>, C extends Channel> {

    protected final B bootstrap;

    protected AbstractBootstrapConfig(B bootstrap) {
        this.bootstrap = ObjectUtil.checkNotNull(bootstrap, "bootstrap");
    }

    /**
     * Returns the configured local address or {@code null} if non is configured yet.
     */
    public final SocketAddress localAddress() {
        return bootstrap.localAddress();
    }

    /**
     * Returns the configured {@link ChannelHandler} or {@code null} if non is configured yet.
     */
    public final ChannelHandler handler() {
        return bootstrap.handler();
    }

    public final EventLoopGroup group() {
        return bootstrap.group();
    }
}
