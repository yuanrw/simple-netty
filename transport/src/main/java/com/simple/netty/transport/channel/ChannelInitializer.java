package com.simple.netty.transport.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用来初始化的handler，初始化成功后会从ChannelPipeline中移除掉
 * Date: 2020-01-21
 * Time: 16:19
 *
 * @author yrw
 */
public abstract class ChannelInitializer<C extends Channel> extends ChannelInboundHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(ChannelInitializer.class);

    private final Set<ChannelHandlerContext> initMap = Collections.newSetFromMap(
        new ConcurrentHashMap<>());

    /**
     * 一旦{@link Channel}被register，会调用这个方法
     * 方法返回后将自己从pipeline中移除
     *
     * @param ch 注册的{@link Channel}
     * @throws Exception 如果出现异常会被{@link #exceptionCaught(ChannelHandlerContext, Throwable)}
     *                   处理，并且关闭{@link Channel}.
     */
    protected abstract void initChannel(C ch) throws Exception;

    /**
     * 处理{@link Throwable}并打日志，关闭{@link Channel}.
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (logger.isWarnEnabled()) {
            logger.warn("Failed to initialize a channel. Closing: " + ctx.channel(), cause);
        }
        ctx.close();
    }

    /**
     * 初始化的主要逻辑
     * 调用用户的初始化方法
     * 将ctx从pipeline中移除
     * 将ctx从initMap中移除
     * <p>
     * {@inheritDoc} 重写这个方法，必须调用super
     */
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        if (ctx.channel().isRegistered()) {
            // This should always be true with our current DefaultChannelPipeline implementation.
            // The good thing about calling initChannel(...) in handlerAdded(...) is that there will be no ordering
            // surprises if a ChannelInitializer will add another ChannelInitializer. This is as all handlers
            // will be added in the expected order.
            if (initChannel(ctx)) {
                //channel初始化完成，把自己移除
                removeState(ctx);
            }
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        initMap.remove(ctx);
    }

    @SuppressWarnings("unchecked")
    private boolean initChannel(ChannelHandlerContext ctx) throws Exception {
        //防止重复调用initChannel
        if (initMap.add(ctx)) {
            try {
                initChannel((C) ctx.channel());
            } catch (Throwable cause) {
                exceptionCaught(ctx, cause);
            } finally {
                //将自己从channel中移除
                ChannelPipeline pipeline = ctx.pipeline();
                if (pipeline.context(this) != null) {
                    pipeline.remove(this);
                }
            }
            return true;
        }
        return false;
    }

    private void removeState(final ChannelHandlerContext ctx) {
        if (ctx.isRemoved()) {
            initMap.remove(ctx);
        } else {
            //通常来说这个方法在ctx.remove()后被调用，但也有可能用户使用自定义的EventExecutor导致还没remove
            //因此放到executor中后续调用
            ctx.executor().execute(() -> initMap.remove(ctx));
        }
    }
}
