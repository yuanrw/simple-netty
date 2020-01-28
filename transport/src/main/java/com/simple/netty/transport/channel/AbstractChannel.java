package com.simple.netty.transport.channel;

import com.simple.netty.buffer.ByteBuf;
import com.simple.netty.buffer.ByteBufAllocator;
import com.simple.netty.common.internal.ObjectUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NotYetConnectedException;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

/**
 * Channel的骨架实现
 * Date: 2020-01-02
 * Time: 19:32
 *
 * @author yrw
 */
public abstract class AbstractChannel implements Channel {

    private static final Logger logger = LoggerFactory.getLogger(AbstractChannel.class);

    private final Channel parent;
    private final String id;
    private final Unsafe unsafe;
    private final DefaultChannelPipeline pipeline;

    private final VoidChannelPromise unsafeVoidPromise = new VoidChannelPromise(this, false);
    private final CloseFuture closeFuture = new CloseFuture(this);

    private volatile SocketAddress localAddress;
    private volatile SocketAddress remoteAddress;

    /**
     * 一个Channel只能注册到一个EventLoop线程
     */
    private volatile EventLoop eventLoop;

    /**
     * 是否已经注册
     */
    private volatile boolean registered;

    /**
     * close()是否被调用
     */
    private boolean closeInitiated;
    private Throwable initialCloseCause;

    protected AbstractChannel(Channel parent) {
        this.parent = parent;
        id = newId();
        unsafe = newUnsafe();
        pipeline = newChannelPipeline();
    }

    /**
     * Creates a new instance.
     *
     * @param parent the parent of this channel. {@code null} if there's no parent.
     */
    protected AbstractChannel(Channel parent, String id) {
        this.parent = parent;
        this.id = id;
        unsafe = newUnsafe();
        pipeline = newChannelPipeline();
    }

    @Override
    public final String id() {
        return id;
    }

    protected String newId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Returns a new {@link DefaultChannelPipeline} instance.
     */
    protected DefaultChannelPipeline newChannelPipeline() {
        return new DefaultChannelPipeline(this);
    }

    @Override
    public boolean isWritable() {
        ChannelOutboundBuffer buf = unsafe.outboundBuffer();
        return buf != null && buf.isWritable();
    }

    @Override
    public Channel parent() {
        return parent;
    }

    @Override
    public ChannelPipeline pipeline() {
        return pipeline;
    }

    @Override
    public ByteBufAllocator alloc() {
        return config().getAllocator();
    }

    @Override
    public EventLoop eventLoop() {
        EventLoop eventLoop = this.eventLoop;
        if (eventLoop == null) {
            throw new IllegalStateException("channel not registered to an event loop");
        }
        return eventLoop;
    }

    @Override
    public SocketAddress localAddress() {
        //先从缓存获取
        SocketAddress localAddress = this.localAddress;
        if (localAddress == null) {
            try {
                this.localAddress = localAddress = unsafe().localAddress();
            } catch (Error e) {
                throw e;
            } catch (Throwable t) {
                // Sometimes fails on a closed socket in Windows.
                return null;
            }
        }
        return localAddress;
    }

    @Override
    public SocketAddress remoteAddress() {
        SocketAddress remoteAddress = this.remoteAddress;
        if (remoteAddress == null) {
            try {
                this.remoteAddress = remoteAddress = unsafe().remoteAddress();
            } catch (Error e) {
                throw e;
            } catch (Throwable t) {
                // Sometimes fails on a closed socket in Windows.
                return null;
            }
        }
        return remoteAddress;
    }

    @Override
    public boolean isRegistered() {
        return registered;
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress) {
        return pipeline.bind(localAddress);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress) {
        return pipeline.connect(remoteAddress);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        return pipeline.connect(remoteAddress, localAddress);
    }

    @Override
    public ChannelFuture disconnect() {
        return pipeline.disconnect();
    }

    @Override
    public ChannelFuture close() {
        return pipeline.close();
    }

    @Override
    public ChannelFuture deregister() {
        return pipeline.deregister();
    }

    @Override
    public Channel flush() {
        pipeline.flush();
        return this;
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
        return pipeline.bind(localAddress, promise);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
        return pipeline.connect(remoteAddress, promise);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        return pipeline.connect(remoteAddress, localAddress, promise);
    }

    @Override
    public ChannelFuture disconnect(ChannelPromise promise) {
        return pipeline.disconnect(promise);
    }

    @Override
    public ChannelFuture close(ChannelPromise promise) {
        return pipeline.close(promise);
    }

    @Override
    public ChannelFuture deregister(ChannelPromise promise) {
        return pipeline.deregister(promise);
    }

    @Override
    public Channel read() {
        pipeline.read();
        return this;
    }

    @Override
    public ChannelFuture write(Object msg) {
        return pipeline.write(msg);
    }

    @Override
    public ChannelFuture write(Object msg, ChannelPromise promise) {
        return pipeline.write(msg, promise);
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg) {
        return pipeline.writeAndFlush(msg);
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
        return pipeline.writeAndFlush(msg, promise);
    }

    @Override
    public ChannelPromise newPromise() {
        return pipeline.newPromise();
    }

    @Override
    public ChannelFuture newSucceededFuture() {
        return pipeline.newSucceededFuture();
    }

    @Override
    public ChannelFuture newFailedFuture(Throwable cause) {
        return pipeline.newFailedFuture(cause);
    }

    @Override
    public ChannelFuture closeFuture() {
        return closeFuture;
    }

    @Override
    public Unsafe unsafe() {
        return unsafe;
    }

    protected abstract AbstractUnsafe newUnsafe();

    @Override
    public final int hashCode() {
        return id.hashCode();
    }

    @Override
    public final boolean equals(Object o) {
        return this == o;
    }

    @Override
    public final int compareTo(Channel o) {
        if (this == o) {
            return 0;
        }

        return id().compareTo(o.id());
    }

    @Override
    public final ChannelPromise voidPromise() {
        return pipeline.voidPromise();
    }

    /**
     * Unsafe的骨架实现
     * 主要实现方法：
     * register
     * bind
     * disconnect
     * closeForcibly
     * write
     * flush
     */
    protected abstract class AbstractUnsafe implements Unsafe {

        /**
         * 存储写数据的缓冲区
         */
        private volatile ChannelOutboundBuffer outboundBuffer = new ChannelOutboundBuffer(AbstractChannel.this);
        /**
         * 半包标志
         */
        private boolean inFlush0;
        /**
         * 如果Channel从来没被注册过，返回true
         */
        private boolean neverRegistered = true;

        private void assertEventLoop() {
            assert !registered || eventLoop.inEventLoop();
        }

        @Override
        public final ChannelOutboundBuffer outboundBuffer() {
            return outboundBuffer;
        }

        @Override
        public final SocketAddress localAddress() {
            return localAddress0();
        }

        @Override
        public final SocketAddress remoteAddress() {
            return remoteAddress0();
        }

        @Override
        public final void register(EventLoop eventLoop, final ChannelPromise promise) {
            ObjectUtil.checkNotNull(eventLoop, "eventLoop");
            //已经注册了
            if (isRegistered()) {
                promise.setFailure(new IllegalStateException("registered to an event loop already"));
                return;
            }

            AbstractChannel.this.eventLoop = eventLoop;

            //当前线程是否是Channel对应的EventLoop线程
            if (eventLoop.inEventLoop()) {
                //如果是同一个线程，不存在多线程并发问题，直接注册
                register0(promise);
            } else {
                // 用户线程或者其他线程发起的注册操作
                // 放到EventLoop的任务队列中，最终都要由EventLoop串行来操作
                try {
                    eventLoop.execute(() -> register0(promise));
                } catch (Throwable t) {
                    logger.warn(
                        "Force-closing a channel whose registration task was not accepted by an event loop: {}",
                        AbstractChannel.this, t);
                    //关闭channel
                    closeForcibly();
                    closeFuture.setClosed();
                    //promise置为失败
                    safeSetFailure(promise, t);
                }
            }
        }

        private void register0(ChannelPromise promise) {
            try {
                //确保channel打开的
                if (!ensureOpen(promise)) {
                    return;
                }
                //保留初始状态，neverRegistered后面会改动
                boolean firstRegistration = neverRegistered;

                //注册
                doRegister();

                neverRegistered = false;
                registered = true;

                //写结果
                safeSetSuccess(promise);

                //通知注册事件
                pipeline.fireChannelRegistered();
                if (isActive()) {
                    //如果是第一次注册，才fire active，防止channel被反复注册的时候多次fire active
                    if (firstRegistration) {
                        pipeline.fireChannelActive();
                    } else {
                        //channel已经被注册过了，需要beginRead，才能读数据
                        beginRead();
                    }
                }
            } catch (Throwable t) {
                // Close the channel directly to avoid FD leak.
                closeForcibly();
                closeFuture.setClosed();
                safeSetFailure(promise, t);
            }
        }

        @Override
        public final void bind(final SocketAddress localAddress, final ChannelPromise promise) {
            assertEventLoop();

            if (!ensureOpen(promise)) {
                return;
            }

            boolean wasActive = isActive();
            try {
                //抽象方法，服务端和客户端doBind不一样
                doBind(localAddress);
            } catch (Throwable t) {
                safeSetFailure(promise, t);
                closeIfClosed();
                return;
            }

            //判断是否需要fireActive
            if (!wasActive && isActive()) {
                invokeLater(pipeline::fireChannelActive);
            }

            safeSetSuccess(promise);
        }

        @Override
        public final void disconnect(final ChannelPromise promise) {
            assertEventLoop();

            boolean wasActive = isActive();
            try {
                //抽象方法
                doDisconnect();
                // 重置
                remoteAddress = null;
                localAddress = null;
            } catch (Throwable t) {
                safeSetFailure(promise, t);
                closeIfClosed();
                return;
            }

            if (wasActive && !isActive()) {
                invokeLater(pipeline::fireChannelInactive);
            }

            safeSetSuccess(promise);
            //关闭channel
            closeIfClosed();
        }

        @Override
        public final void close(final ChannelPromise promise) {
            assertEventLoop();

            ClosedChannelException closedChannelException = new ClosedChannelException();
            close(promise, closedChannelException, closedChannelException);
        }

        private void close(final ChannelPromise promise, final Throwable cause,
                           final ClosedChannelException closeCause) {

            if (closeInitiated) {
                if (closeFuture.isDone()) {
                    // 已经关闭
                    safeSetSuccess(promise);
                } else if (!(promise instanceof VoidChannelPromise)) {
                    // This means close() was called before so we just register a listener and return
                    closeFuture.addListener((ChannelFutureListener) future -> promise.setSuccess());
                }
                return;
            }

            closeInitiated = true;

            final boolean wasActive = isActive();

            //让jvm回收缓冲区
            final ChannelOutboundBuffer outboundBuffer = this.outboundBuffer;
            this.outboundBuffer = null;

            try {
                doClose0(promise);
            } finally {
                //把message全部失败
                if (outboundBuffer != null) {
                    outboundBuffer.failFlushed(cause);
                    outboundBuffer.close(closeCause);
                }
            }

            //激活inactive，把Channel在EventLoop上取消注册

            //半包待写
            if (inFlush0) {
                //要先把数据flush出去，稍后激活
                invokeLater(() -> fireChannelInactiveAndDeregister(wasActive));
            } else {
                //立刻激活
                fireChannelInactiveAndDeregister(wasActive);
            }
        }

        private void doClose0(ChannelPromise promise) {
            try {
                doClose();
                closeFuture.setClosed();
                safeSetSuccess(promise);
            } catch (Throwable t) {
                closeFuture.setClosed();
                safeSetFailure(promise, t);
            }
        }

        private void fireChannelInactiveAndDeregister(final boolean wasActive) {
            deregister(voidPromise(), wasActive && !isActive());
        }

        @Override
        public final void closeForcibly() {
            assertEventLoop();

            try {
                doClose();
            } catch (Exception e) {
                logger.warn("Failed to close a channel.", e);
            }
        }

        @Override
        public final void deregister(final ChannelPromise promise) {
            assertEventLoop();

            deregister(promise, false);
        }

        private void deregister(final ChannelPromise promise, final boolean fireChannelInactive) {
            if (!registered) {
                safeSetSuccess(promise);
                return;
            }

            //为了防止Deregister的时候Channel还在做别的操作
            //放到队列中执行

            invokeLater(() -> {
                try {
                    doDeregister();
                } catch (Throwable t) {
                    logger.warn("Unexpected exception occurred while deregistering a channel.", t);
                } finally {
                    //判断是否需要fire inactive
                    if (fireChannelInactive) {
                        pipeline.fireChannelInactive();
                    }

                    //判断是否需要fire unregister
                    if (registered) {
                        registered = false;
                        pipeline.fireChannelUnregistered();
                    }
                    safeSetSuccess(promise);
                }
            });
        }

        @Override
        public final void beginRead() {
            assertEventLoop();

            if (!isActive()) {
                return;
            }

            try {
                doBeginRead();
            } catch (final Exception e) {
                invokeLater(() -> pipeline.fireExceptionCaught(e));
                close(voidPromise());
            }
        }

        @Override
        public final void write(ByteBuf msg, ChannelPromise promise) {
            assertEventLoop();

            ChannelOutboundBuffer outboundBuffer = this.outboundBuffer;
            if (outboundBuffer == null) {
                // 说明channel已经关闭，需要手动置为失败
                safeSetFailure(promise, initialCloseCause);
                msg.release();
                return;
            }

            //消息加入数组
            outboundBuffer.addMessage(msg);
        }

        @Override
        public final void flush() {
            assertEventLoop();

            ChannelOutboundBuffer outboundBuffer = this.outboundBuffer;
            if (outboundBuffer == null) {
                return;
            }

            //标记本次要flush的消息范围
            outboundBuffer.addFlush();

            //开始flush
            flush0();
        }

        protected void flush0() {
            if (inFlush0) {
                // Avoid re-entrance
                return;
            }

            final ChannelOutboundBuffer outboundBuffer = this.outboundBuffer;
            if (outboundBuffer == null || outboundBuffer.isEmpty()) {
                return;
            }

            inFlush0 = true;

            // 如果管道不活跃
            if (!isActive()) {
                try {
                    if (isOpen()) {
                        //连接状态
                        outboundBuffer.failFlushed(new NotYetConnectedException());
                    } else {
                        //关闭状态
                        outboundBuffer.failFlushed(initialCloseCause);
                    }
                } finally {
                    inFlush0 = false;
                }
                return;
            }

            try {
                //真正的写操作
                doWrite(outboundBuffer);
            } catch (Throwable t) {
                inFlush0 = false;
            }
        }

        @Override
        public final ChannelPromise voidPromise() {
            assertEventLoop();

            return unsafeVoidPromise;
        }

        protected final boolean ensureOpen(ChannelPromise promise) {
            if (isOpen()) {
                return true;
            }

            safeSetFailure(promise, initialCloseCause);
            return false;
        }

        /**
         * 把{@code promise}标记为成功. 如果{@code promise}已经complete，打日志.
         */
        protected final void safeSetSuccess(ChannelPromise promise) {
            if (!promise.trySuccess()) {
                logger.warn("Failed to mark a promise as success because it is done already: {}", promise);
            }
        }

        /**
         * 把{@code promise}标记为失败. 如果{@code promise}已经complete, 打日志.
         */
        protected final void safeSetFailure(ChannelPromise promise, Throwable cause) {
            if (!promise.tryFailure(cause)) {
                logger.warn("Failed to mark a promise as failure because it's done already: {}", promise, cause);
            }
        }

        protected final void closeIfClosed() {
            if (isOpen()) {
                return;
            }
            close(voidPromise());
        }

        private void invokeLater(Runnable task) {
            try {
                //排队执行
                eventLoop().execute(task);
            } catch (RejectedExecutionException e) {
                logger.warn("Can't invoke task later as EventLoop rejected it", e);
            }
        }
    }

    /**
     * Returns the {@link SocketAddress} which is bound locally.
     */
    protected abstract SocketAddress localAddress0();

    /**
     * Return the {@link SocketAddress} which the {@link Channel} is connected to.
     */
    protected abstract SocketAddress remoteAddress0();

    /**
     * 把channel注册到EventLoop的多路复用器上
     *
     * @throws Exception
     */
    protected void doRegister() throws Exception {
        // NOOP
    }

    /**
     * 把{@link SocketAddress}绑定到{@link Channel}上
     */
    protected abstract void doBind(SocketAddress localAddress) throws Exception;

    /**
     * 将{@link Channel}和远程地址断开连接
     */
    protected abstract void doDisconnect() throws Exception;

    /**
     * 关闭{@link Channel}
     */
    protected abstract void doClose() throws Exception;

    /**
     * 将{@link Channel}从{@link EventLoop}上取消注册.
     */
    protected void doDeregister() throws Exception {
        // NOOP
    }

    /**
     * Schedule a read operation.
     */
    protected abstract void doBeginRead() throws Exception;

    /**
     * flush缓冲区里所有的消息
     *
     * @param in 发送缓冲区
     * @throws Exception
     */
    protected abstract void doWrite(ChannelOutboundBuffer in) throws Exception;

    static final class CloseFuture extends DefaultChannelPromise {

        CloseFuture(AbstractChannel ch) {
            super(ch);
        }

        @Override
        public ChannelPromise setSuccess() {
            throw new IllegalStateException();
        }

        @Override
        public ChannelPromise setFailure(Throwable cause) {
            throw new IllegalStateException();
        }

        @Override
        public boolean trySuccess() {
            throw new IllegalStateException();
        }

        @Override
        public boolean tryFailure(Throwable cause) {
            throw new IllegalStateException();
        }

        boolean setClosed() {
            return super.trySuccess();
        }
    }
}
