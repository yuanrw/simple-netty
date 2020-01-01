package com.simple.netty.transport.channel;

import com.simple.netty.buffer.ByteBufAllocator;
import sun.misc.Unsafe;

import java.net.SocketAddress;

/**
 * 管道，通过socket进行异步的I/O操作比如 读，写，连接，绑定等
 * 你可以在这里读取
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
    Channel read();

    /**
     * flush数据
     *
     * @return
     */
    Channel flush();
}
