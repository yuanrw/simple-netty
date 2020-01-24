package com.simple.netty.transport.bootstrap;

import com.simple.netty.common.concurrent.EventExecutor;
import com.simple.netty.common.concurrent.GlobalEventExecutor;
import com.simple.netty.common.internal.ObjectUtil;
import com.simple.netty.common.internal.SocketUtils;
import com.simple.netty.transport.channel.*;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * 一个引导，帮助启动服务端和客户端
 * Date: 2020-01-20
 * Time: 18:06
 *
 * @author yrw
 */
public abstract class AbstractBootstrap<B extends AbstractBootstrap<B, C>, C extends Channel> implements Cloneable {

    volatile EventLoopGroup group;

    private volatile ReflectiveChannelFactory<? extends C> channelFactory;
    private volatile SocketAddress localAddress;
    private volatile ChannelHandler handler;

    AbstractBootstrap() {
    }

    AbstractBootstrap(AbstractBootstrap<B, C> bootstrap) {
        group = bootstrap.group;
        channelFactory = bootstrap.channelFactory;
        handler = bootstrap.handler;
        localAddress = bootstrap.localAddress;
    }

    /**
     * 给Channel设置EventLoopGroup，用于处理Channel的事件
     */
    public B group(EventLoopGroup group) {
        ObjectUtil.checkNotNull(group, "group");
        if (this.group != null) {
            throw new IllegalStateException("group set already");
        }
        this.group = group;
        return self();
    }

    @SuppressWarnings("unchecked")
    private B self() {
        return (B) this;
    }

    /**
     * 设置Channel
     */
    public B channel(Class<? extends C> channelClass) {
        //设置ChannelFactory
        ReflectiveChannelFactory<C> channelFactory = new ReflectiveChannelFactory<C>(
            ObjectUtil.checkNotNull(channelClass, "channelClass")
        );

        if (this.channelFactory != null) {
            throw new IllegalStateException("channelFactory set already");
        }

        this.channelFactory = channelFactory;
        return self();
    }

    /**
     * 本地绑定的 {@link SocketAddress}
     */
    public B localAddress(SocketAddress localAddress) {
        this.localAddress = localAddress;
        return self();
    }

    public B localAddress(int inetPort) {
        return localAddress(new InetSocketAddress(inetPort));
    }

    public B localAddress(String inetHost, int inetPort) {
        return localAddress(SocketUtils.socketAddress(inetHost, inetPort));
    }

    public B localAddress(InetAddress inetHost, int inetPort) {
        return localAddress(new InetSocketAddress(inetHost, inetPort));
    }

    /**
     * 验证参数
     */
    public B validate() {
        if (group == null) {
            throw new IllegalStateException("group not set");
        }
        if (channelFactory == null) {
            throw new IllegalStateException("channel or channelFactory not set");
        }
        return self();
    }

    /**
     * 创建一个新的 {@link Channel} 并且把它注册到 {@link EventLoop}.
     */
    public ChannelFuture register() {
        validate();
        return initAndRegister();
    }

    /**
     * 创建新的 {@link Channel} 并且绑定到localAddress
     */
    public ChannelFuture bind() {
        validate();
        SocketAddress localAddress = this.localAddress;
        if (localAddress == null) {
            throw new IllegalStateException("localAddress not set");
        }
        return doBind(localAddress);
    }

    public ChannelFuture bind(int inetPort) {
        return bind(new InetSocketAddress(inetPort));
    }

    public ChannelFuture bind(String inetHost, int inetPort) {
        return bind(SocketUtils.socketAddress(inetHost, inetPort));
    }

    public ChannelFuture bind(InetAddress inetHost, int inetPort) {
        return bind(new InetSocketAddress(inetHost, inetPort));
    }

    public ChannelFuture bind(SocketAddress localAddress) {
        validate();
        return doBind(ObjectUtil.checkNotNull(localAddress, "localAddress"));
    }

    private ChannelFuture doBind(final SocketAddress localAddress) {
        final ChannelFuture regFuture = initAndRegister();
        final Channel channel = regFuture.channel();
        if (regFuture.cause() != null) {
            return regFuture;
        }

        if (regFuture.isDone()) {
            // 注册完成
            ChannelPromise promise = channel.newPromise();
            //绑定
            doBind0(regFuture, channel, localAddress, promise);
            return promise;
        } else {
            // 注册还没完成
            final PendingRegistrationPromise promise = new PendingRegistrationPromise(channel);
            regFuture.addListener((ChannelFutureListener) future -> {
                Throwable cause = future.cause();
                if (cause != null) {
                    promise.setFailure(cause);
                } else {
                    //注册完成，开始绑定
                    promise.registered();
                    doBind0(regFuture, channel, localAddress, promise);
                }
            });
            return promise;
        }
    }

    private static void doBind0(
        final ChannelFuture regFuture, final Channel channel,
        final SocketAddress localAddress, final ChannelPromise promise) {

        // This method is invoked before channelRegistered() is triggered.  Give user handlers a chance to set up
        // the pipeline in its channelRegistered() implementation.
        channel.eventLoop().execute(() -> {
            if (regFuture.isSuccess()) {
                channel.bind(localAddress, promise).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
            } else {
                promise.setFailure(regFuture.cause());
            }
        });
    }

    /**
     * 创建channel并注册到EventLoop上
     *
     * @return
     */
    final ChannelFuture initAndRegister() {
        Channel channel = null;
        try {
            //创建channel对象
            channel = channelFactory.newChannel();
            //初始化
            init(channel);
        } catch (Throwable t) {
            if (channel != null) {
                // 关闭channel
                channel.unsafe().closeForcibly();
                // 因为channel还没有注册，因此使用GlobalEventExecutor.INSTANCE
                return new DefaultChannelPromise(channel, GlobalEventExecutor.INSTANCE).setFailure(t);
            }
            return new DefaultChannelPromise(new FailedChannel(), GlobalEventExecutor.INSTANCE).setFailure(t);
        }

        //注册
        ChannelFuture regFuture = group().register(channel);

        //如果注册异常，关闭channel
        if (regFuture.cause() != null) {
            if (channel.isRegistered()) {
                channel.close();
            } else {
                channel.unsafe().closeForcibly();
            }
        }

        // 注册成功
        // 1)此操作由event loop发起，接下来进行bind()和connect()操作是安全的
        // 2)此操作由其他线程发起，register任务被放入task队列，接下来进行bind()和connect()操作是安全的
        // 因为这些操作会被排到register后面
        return regFuture;
    }

    /**
     * 配置
     *
     * @return
     */
    public abstract AbstractBootstrapConfig<B, C> config();

    /**
     * 初始化channel
     *
     * @param channel
     * @throws Exception
     */
    abstract void init(Channel channel) throws Exception;

    /**
     * the {@link ChannelHandler} to use for serving the requests.
     */
    public B handler(ChannelHandler handler) {
        this.handler = ObjectUtil.checkNotNull(handler, "handler");
        return self();
    }

    public final EventLoopGroup group() {
        return group;
    }

    final SocketAddress localAddress() {
        return localAddress;
    }

    final ChannelHandler handler() {
        return handler;
    }

    static final class PendingRegistrationPromise extends DefaultChannelPromise {

        /**
         * 一旦注册成功就用channel对应的event loop，否则就是用GlobalEventExecutor.INSTANCE
         */
        private volatile boolean registered;

        PendingRegistrationPromise(Channel channel) {
            super(channel);
        }

        void registered() {
            registered = true;
        }

        @Override
        protected EventExecutor executor() {
            if (registered) {
                return super.executor();
            }
            return GlobalEventExecutor.INSTANCE;
        }
    }
}
