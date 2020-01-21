package com.simple.netty.transport.bootstrap;

import com.simple.netty.transport.channel.Channel;

import java.net.SocketAddress;

/**
 * Date: 2020-01-21
 * Time: 15:43
 *
 * @author yrw
 */
public final class BootstrapConfig extends AbstractBootstrapConfig<Bootstrap, Channel> {

    BootstrapConfig(Bootstrap bootstrap) {
        super(bootstrap);
    }

    /**
     * Returns the configured remote address or {@code null} if non is configured yet.
     */
    public SocketAddress remoteAddress() {
        return bootstrap.remoteAddress();
    }
}
