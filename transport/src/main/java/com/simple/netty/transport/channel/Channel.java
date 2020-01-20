package com.simple.netty.transport.channel;

import com.simple.netty.buffer.ByteBuf;
import com.simple.netty.buffer.ByteBufAllocator;

import java.net.SocketAddress;

/**
 * 管道，通过socket进行异步的I/O操作比如 读，写，连接，绑定等
 * 采用聚合的方式封装各种功能
 * <p>
 * Date: 2019-12-30
 * Time: 20:53
 *
 * @author yrw
 */
public interface Channel extends ChannelOutboundInvoker, Comparable<Channel> {

    /**
     * 返回全局唯一的管道id
     *
     * @return
     */
    String id();

    /**
     * 返回这个channel所注册到的EventLoop
     *
     * @return
     */
    EventLoop eventLoop();

    /**
     * 返回父管道
     *
     * @return
     */
    Channel parent();

    /**
     * 返回管道的配置
     *
     * @return
     */
    ChannelConfig config();

    /**
     * 返回管道是否开启
     *
     * @return
     */
    boolean isOpen();

    /**
     * 返回管道是否注册了一个EventLoop
     *
     * @return
     */
    boolean isRegistered();

    /**
     * 返回管道是否活跃
     *
     * @return
     */
    boolean isActive();

    /**
     * 返回管道绑定的本地地址
     *
     * @return
     */
    SocketAddress localAddress();

    /**
     * 返回管道绑定的远程地址
     *
     * @return
     */
    SocketAddress remoteAddress();

    /**
     * 一旦管道关闭会得到通知
     *
     * @return
     */
    ChannelFuture closeFuture();

    /**
     * 返回Unsafe类
     *
     * @return
     */
    Unsafe unsafe();

    /**
     * I/O线程可以处理写请求返回true
     *
     * @return
     */
    boolean isWritable();

    /**
     * 返回分配给管道的ChannelPipeline
     *
     * @return
     */
    ChannelPipeline pipeline();

    /**
     * 返回分配给管道的ByteBufAllocator
     *
     * @return
     */
    ByteBufAllocator alloc();

    /**
     * 从管道里读数据
     *
     * @return
     */
    @Override
    Channel read();

    /**
     * flush数据
     *
     * @return
     */
    @Override
    Channel flush();

    /**
     * 实际的IO读写操作都是Unsafe操作的，取名叫Unsafe是因为不该被用户调用
     */
    interface Unsafe {

        /**
         * 返回绑定的本地地址
         */
        SocketAddress localAddress();

        /**
         * 返回绑定的远程地址
         */
        SocketAddress remoteAddress();

        /**
         * 把Channel注册到EventLoop上，注册完成后通知ChannelPromise
         */
        void register(EventLoop eventLoop, ChannelPromise promise);

        /**
         * 绑定指定的端口，对于服务端，绑定指定端口，对于客户端，绑定本地Socket地址
         */
        void bind(SocketAddress localAddress, ChannelPromise promise);

        /**
         * 把Channel连接到远程地址
         */
        void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise);

        /**
         * 服务端或客户端主动关闭连接
         */
        void disconnect(ChannelPromise promise);

        /**
         * 关闭Channel
         */
        void close(ChannelPromise promise);

        /**
         * 不fire任何事件，直接把Channel关掉
         */
        void closeForcibly();

        /**
         * 在EventLoop里取消注册Channel
         */
        void deregister(ChannelPromise promise);

        /**
         * Schedules a read operation that fills the inbound buffer of the first {@link ChannelInboundHandler} in the
         * {@link ChannelPipeline}.  If there's already a pending read operation, this method does nothing.
         */
        void beginRead();

        /**
         * 将消息加到发送缓冲区中，并不是真正的写Channel
         */
        void write(ByteBuf msg, ChannelPromise promise);

        /**
         * 将发送缓冲区中所有的消息全部写入Channel中，发送给通信的对方
         */
        void flush();

        /**
         * 一个特殊的ChannelPromise 可以被复用，可以传入{@link Unsafe}
         * 仅仅作为一个容器被使用
         */
        ChannelPromise voidPromise();

        /**
         * 返回消息发送缓冲区
         */
        ChannelOutboundBuffer outboundBuffer();
    }
}
