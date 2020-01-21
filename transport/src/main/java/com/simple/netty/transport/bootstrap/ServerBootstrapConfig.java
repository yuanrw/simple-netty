package com.simple.netty.transport.bootstrap;

import com.simple.netty.transport.channel.ServerChannel;

/**
 * Date: 2020-01-21
 * Time: 15:43
 *
 * @author yrw
 */
public class ServerBootstrapConfig extends AbstractBootstrapConfig<ServerBootstrap, ServerChannel> {

    ServerBootstrapConfig(ServerBootstrap bootstrap) {
        super(bootstrap);
    }
}
