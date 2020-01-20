package com.simple.netty.transport.channel.nio;

import com.simple.netty.transport.channel.AbstractChannel;
import com.simple.netty.transport.channel.Channel;
import com.simple.netty.transport.channel.ChannelFutureListener;
import com.simple.netty.transport.channel.ChannelPromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ConnectionPendingException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.concurrent.Future;

/**
 * Date: 2020-01-02
 * Time: 19:32
 *
 * @author yrw
 */
public abstract class AbstractNioChannel extends AbstractChannel {
    private static final Logger logger = LoggerFactory.getLogger(AbstractNioChannel.class);

    /**
     * 核心Channel，用于注册事件
     */
    private final SelectableChannel ch;

    /**
     * 感兴趣的事件标记
     */
    protected final int readInterestOp;

    /**
     * Channel注册到EventLoop之后返回的选择键
     */
    volatile SelectionKey selectionKey;

    /**
     * 标记半包读
     */
    boolean readPending;

    /**
     * The future of the current connection attempt.  If not null, subsequent
     * connection attempts will fail.
     */
    private ChannelPromise connectPromise;
    private Future<?> connectTimeoutFuture;

    /**
     * Create a new instance
     *
     * @param parent         the parent {@link Channel} by which this instance was created. May be {@code null}
     * @param ch             the underlying {@link SelectableChannel} on which it operates
     * @param readInterestOp the ops to set to receive data from the {@link SelectableChannel}
     */
    protected AbstractNioChannel(Channel parent, SelectableChannel ch, int readInterestOp) {
        super(parent);
        this.ch = ch;
        this.readInterestOp = readInterestOp;
        try {
            ch.configureBlocking(false);
        } catch (IOException e) {
            try {
                ch.close();
            } catch (IOException e2) {
                logger.warn(
                    "Failed to close a partially initialized socket.", e2);
            }

            throw new RuntimeException("Failed to enter non-blocking mode.", e);
        }
    }

    @Override
    public NioEventLoop eventLoop() {
        return (NioEventLoop) super.eventLoop();
    }

    @Override
    protected void doRegister() throws Exception {
        boolean selected = false;
        for (; ; ) {
            try {
                //注册0，代表不对任何事件感兴趣，只是为了获取selectionKey，后续可以从多路复用器中获取Channel对象
                selectionKey = javaChannel().register(eventLoop().unwrappedSelector(), 0, this);
                return;
            } catch (CancelledKeyException e) {
                if (!selected) {
                    // Force the Selector to select now as the "canceled" SelectionKey may still be
                    // cached and not removed because no Select.select(..) operation was called yet.
                    eventLoop().selectNow();
                    selected = true;
                } else {
                    // We forced a select operation on the selector before but the SelectionKey is still cached
                    // for whatever reason. JDK bug ?
                    throw e;
                }
            }
        }
    }

    @Override
    protected void doDeregister() throws Exception {
        eventLoop().cancel(selectionKey());
    }

    @Override
    protected void doBeginRead() throws Exception {
        // 在进行读操作Channel.read() or ChannelHandlerContext.read()之前
        // 需要设置网络操作位为读
        final SelectionKey selectionKey = this.selectionKey;
        if (!selectionKey.isValid()) {
            return;
        }

        readPending = true;

        final int interestOps = selectionKey.interestOps();
        if ((interestOps & readInterestOp) == 0) {
            //说明还没设置读，设置操作位
            selectionKey.interestOps(interestOps | readInterestOp);
        }
    }

    @Override
    public NioUnsafe unsafe() {
        return (NioUnsafe) super.unsafe();
    }

    protected SelectableChannel javaChannel() {
        return ch;
    }

    protected SelectionKey selectionKey() {
        assert selectionKey != null;
        return selectionKey;
    }

    @Override
    public boolean isOpen() {
        return ch.isOpen();
    }

    /**
     * 可以访问内部 {@link SelectableChannel}
     */
    public interface NioUnsafe extends Unsafe {

        /**
         * 返回SelectableChannel
         */
        SelectableChannel ch();

        /**
         * 处理服务端的tcp握手应答
         */
        void finishConnect();

        /**
         * 从SelectableChannel中读取数据
         */
        void read();

        /**
         * flush数据
         */
        void forceFlush();
    }

    /**
     * NioUnsafe的骨架实现
     */
    protected abstract class AbstractNioUnsafe extends AbstractUnsafe implements NioUnsafe {

        /**
         * 清除read标志
         */
        protected final void removeReadOp() {
            SelectionKey key = selectionKey();
            // Check first if the key is still valid as it may be canceled as part of the deregistration
            // from the EventLoop
            // See https://github.com/netty/netty/issues/2104
            if (!key.isValid()) {
                return;
            }
            int interestOps = key.interestOps();
            if ((interestOps & readInterestOp) != 0) {
                // only remove readInterestOp if needed
                key.interestOps(interestOps & ~readInterestOp);
            }
        }

        @Override
        public final SelectableChannel ch() {
            return javaChannel();
        }

        @Override
        public final void connect(
            final SocketAddress remoteAddress, final SocketAddress localAddress, final ChannelPromise promise) {
            if (!ensureOpen(promise)) {
                return;
            }

            try {
                if (connectPromise != null) {
                    //已经连接了
                    throw new ConnectionPendingException();
                }

                boolean wasActive = isActive();
                if (doConnect(remoteAddress, localAddress)) {
                    //连接成功
                    fulfillConnectPromise(promise, wasActive);
                } else {
                    //服务端暂时没有应答，需要等待
                    connectPromise = promise;

                    //提交一个定时任务，设定超时时间
                    final long now = System.nanoTime();
                    int connectTimeoutMillis = config().getConnectTimeoutMillis();
                    if (connectTimeoutMillis > 0) {
                        connectTimeoutFuture = eventLoop().submit(() -> {
                            if (System.nanoTime() - now > connectTimeoutMillis) {
                                ChannelPromise connectPromise = AbstractNioChannel.this.connectPromise;
                                RuntimeException runtimeException = new RuntimeException("connection timed out: " + remoteAddress);
                                if (connectPromise != null && connectPromise.tryFailure(runtimeException)) {
                                    close(voidPromise());
                                }
                            }
                        });
                    }

                    //添加连接成功的监听器
                    promise.addListener((ChannelFutureListener) future -> {
                        if (future.isCancelled()) {
                            if (connectTimeoutFuture != null) {
                                connectTimeoutFuture.cancel(false);
                            }
                            connectPromise = null;
                            close(voidPromise());
                        }
                    });
                }
            } catch (Throwable t) {
                //连接失败，直接抛出异常
                promise.tryFailure(t);
                closeIfClosed();
            }
        }

        private void fulfillConnectPromise(ChannelPromise promise, boolean wasActive) {
            if (promise == null) {
                // Closed via cancellation and the promise has been notified already.
                return;
            }

            boolean active = isActive();

            //如果用户取消connection，会返回false
            boolean promiseSet = promise.trySuccess();

            //判断是否需要fire active（即使取消connection也要fire）
            if (!wasActive && active) {
                pipeline().fireChannelActive();
            }

            // 如果用户取消connection，则关闭Channel
            if (!promiseSet) {
                close(voidPromise());
            }
        }

        private void fulfillConnectPromise(ChannelPromise promise, Throwable cause) {
            if (promise == null) {
                return;
            }

            // Use tryFailure() instead of setFailure() to avoid the race against cancel().
            promise.tryFailure(cause);
            closeIfClosed();
        }

        @Override
        public final void finishConnect() {
            //收到服务端tcp 握手应答消息时调用的方法（取消或超时不会调用）
            assert eventLoop().inEventLoop();

            try {
                boolean wasActive = isActive();
                //判断连接结果
                doFinishConnect();
                //连接成功，将SocketChannel修改为监听读操作位
                fulfillConnectPromise(connectPromise, wasActive);
            } catch (Throwable t) {
                //连接失败
                fulfillConnectPromise(connectPromise, t);
            } finally {
                //取消connectTimeoutFuture的等待
                if (connectTimeoutFuture != null) {
                    connectTimeoutFuture.cancel(false);
                }
                connectPromise = null;
            }
        }

        @Override
        protected final void flush0() {
            //这个方法在没有半包的时候调用，有半包的时候event loop会调用forceFlush()
            if (!isFlushPending()) {
                super.flush0();
            }
        }

        @Override
        public final void forceFlush() {
            //强行flush
            super.flush0();
        }

        /**
         * 判断是否有半包数据
         *
         * @return
         */
        private boolean isFlushPending() {
            SelectionKey selectionKey = selectionKey();
            return selectionKey.isValid() && (selectionKey.interestOps() & SelectionKey.OP_WRITE) != 0;
        }
    }

    /**
     * 连接到远程地址
     *
     * @param localAddress  本地地址
     * @param remoteAddress 远程地址
     * @return
     * @throws Exception
     */
    protected abstract boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception;

    /**
     * 完成连接
     *
     * @throws Exception
     */
    protected abstract void doFinishConnect() throws Exception;
}
