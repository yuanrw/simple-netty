package com.simple.netty.transport.channel;

/**
 * 全部透传，用户继承这个类方便开发
 * Date: 2020-01-21
 * Time: 16:00
 *
 * @author yrw
 */
public class ChannelInboundHandlerAdapter implements ChannelInboundHandler {

    /**
     * 调用{@link ChannelHandlerContext#fireChannelRegistered()}激活
     * {@link ChannelPipeline}中的下一个{@link ChannelInboundHandler}
     */
    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelRegistered();
    }

    /**
     * 调用{@link ChannelHandlerContext#fireChannelUnregistered()}激活
     * {@link ChannelPipeline}中的下一个{@link ChannelInboundHandler}
     */
    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelUnregistered();
    }

    /**
     * 调用{@link ChannelHandlerContext#fireChannelActive()}激活
     * {@link ChannelPipeline}中的下一个{@link ChannelInboundHandler}
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelActive();
    }

    /**
     * 调用{@link ChannelHandlerContext#fireChannelInactive()}激活
     * {@link ChannelPipeline}中的下一个{@link ChannelInboundHandler}
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelInactive();
    }

    /**
     * 调用{@link ChannelHandlerContext#fireChannelRead(Object)}激活
     * {@link ChannelPipeline}中的下一个{@link ChannelInboundHandler}
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ctx.fireChannelRead(msg);
    }

    /**
     * 调用{@link ChannelHandlerContext#fireChannelReadComplete()}激活
     * {@link ChannelPipeline}中的下一个{@link ChannelInboundHandler}
     */
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelReadComplete();
    }

    /**
     * 调用{@link ChannelHandlerContext#fireUserEventTriggered(Object)}激活
     * {@link ChannelPipeline}中的下一个{@link ChannelInboundHandler}
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        ctx.fireUserEventTriggered(evt);
    }

    /**
     * 调用{@link ChannelHandlerContext#fireChannelWritabilityChanged()}激活
     * {@link ChannelPipeline}中的下一个{@link ChannelInboundHandler}
     */
    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelWritabilityChanged();
    }

    /**
     * 调用{@link ChannelHandlerContext#fireExceptionCaught(Throwable)}激活
     * {@link ChannelPipeline}中的下一个{@link ChannelInboundHandler}
     */
    @Override
    @SuppressWarnings("deprecation")
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
        throws Exception {
        ctx.fireExceptionCaught(cause);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {

    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {

    }
}
