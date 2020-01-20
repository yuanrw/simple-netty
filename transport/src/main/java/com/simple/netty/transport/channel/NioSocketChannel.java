package com.simple.netty.transport.channel;

import com.simple.netty.buffer.ByteBuf;
import com.simple.netty.common.internal.PlatformDependent;
import com.simple.netty.common.internal.SocketUtils;
import com.simple.netty.transport.channel.nio.AbstractNioByteChannel;
import com.simple.netty.transport.channel.socket.DefaultSocketChannelConfig;
import com.simple.netty.transport.channel.socket.ServerSocketChannel;
import com.simple.netty.transport.channel.socket.SocketChannel;
import com.simple.netty.transport.channel.socket.SocketChannelConfig;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.spi.SelectorProvider;

/**
 * nio 客户端
 * Date: 2020-01-03
 * Time: 16:04
 *
 * @author yrw
 */
public class NioSocketChannel extends AbstractNioByteChannel implements SocketChannel {

    private static final SelectorProvider DEFAULT_SELECTOR_PROVIDER = SelectorProvider.provider();

    private static java.nio.channels.SocketChannel newSocket(SelectorProvider provider) {
        try {
            /**
             *  Use the {@link SelectorProvider} to open {@link java.nio.channels.SocketChannel} and so remove condition in
             *  {@link SelectorProvider#provider()} which is called by each SocketChannel.open() otherwise.
             *
             *  See <a href="https://github.com/netty/netty/issues/2308">#2308</a>.
             */
            return provider.openSocketChannel();
        } catch (IOException e) {
            throw new RuntimeException("Failed to open a socket.", e);
        }
    }

    private final SocketChannelConfig config;

    /**
     * Create a new instance
     */
    public NioSocketChannel() {
        this(DEFAULT_SELECTOR_PROVIDER);
    }

    /**
     * Create a new instance using the given {@link SelectorProvider}.
     */
    public NioSocketChannel(SelectorProvider provider) {
        this(newSocket(provider));
    }

    /**
     * Create a new instance using the given {@link java.nio.channels.SocketChannel}.
     */
    public NioSocketChannel(java.nio.channels.SocketChannel socket) {
        this(null, socket);
    }

    /**
     * Create a new instance
     *
     * @param parent the {@link Channel} which created this instance or {@code null} if it was created by the user
     * @param socket the {@link java.nio.channels.SocketChannel} which will be used
     */
    public NioSocketChannel(Channel parent, java.nio.channels.SocketChannel socket) {
        super(parent, socket);
        config = new NioSocketChannelConfig(this, socket.socket());
    }

    @Override
    public ServerSocketChannel parent() {
        return (ServerSocketChannel) super.parent();
    }

    @Override
    protected SocketAddress localAddress0() {
        return javaChannel().socket().getLocalSocketAddress();
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return javaChannel().socket().getRemoteSocketAddress();
    }

    @Override
    public SocketChannelConfig config() {
        return config;
    }

    @Override
    protected java.nio.channels.SocketChannel javaChannel() {
        return (java.nio.channels.SocketChannel) super.javaChannel();
    }

    @Override
    public boolean isActive() {
        java.nio.channels.SocketChannel ch = javaChannel();
        return ch.isOpen() && ch.isConnected();
    }

    @Override
    protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        if (localAddress != null) {
            //绑定本地地址
            doBind0(localAddress);
        }

        boolean success = false;
        try {
            //发起tcp连接
            boolean connected = SocketUtils.connect(javaChannel(), remoteAddress);
            if (!connected) {
                //暂时没有连上，服务端没有ack应答，设置标志位
                selectionKey().interestOps(SelectionKey.OP_CONNECT);
            }
            success = true;
            return connected;
        } finally {
            //连接失败（被拒绝等），直接关闭
            if (!success) {
                doClose();
            }
        }
    }

    @Override
    protected void doFinishConnect() throws Exception {

    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        doBind0(localAddress);
    }

    @Override
    protected void doDisconnect() throws Exception {

    }

    @Override
    protected void doClose() throws Exception {

    }

    private void doBind0(SocketAddress localAddress) throws Exception {
        if (PlatformDependent.javaVersion() >= 7) {
            SocketUtils.bind(javaChannel(), localAddress);
        } else {
            SocketUtils.bind(javaChannel().socket(), localAddress);
        }
    }

    @Override
    protected int doReadBytes(ByteBuf byteBuf) throws Exception {
        return byteBuf.writeBytes(javaChannel(), byteBuf.writableBytes());
    }

    @Override
    protected int doWriteBytes(ByteBuf buf) throws Exception {
        final int expectedWrittenBytes = buf.readableBytes();
        return buf.readBytes(javaChannel(), expectedWrittenBytes);
    }

    /**
     * 客户端的主要逻辑，写数据
     *
     * @param in
     * @throws Exception
     */
    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        java.nio.channels.SocketChannel ch = javaChannel();

        //获取写循环的次数上限
        int writeSpinCount = config().getWriteSpinCount();

        do {
            if (in.isEmpty()) {
                // 写完了，清除OP_WRITE后返回
                clearOpWrite();
                return;
            }

            //一次写的最大byte数
            int maxBytesPerGatheringWrite = ((NioSocketChannelConfig) config).getMaxBytesPerGatheringWrite();

            //从缓冲区里获取需要发送的ByteBuffer数组个数
            ByteBuffer[] nioBuffers = in.nioBuffers(1024, maxBytesPerGatheringWrite);
            int nioBufferCnt = in.nioBufferCount();

            if (nioBufferCnt == 0) {
                // todo: 说明要发送的不是ByteBuffer，可能是POJO等
            } else {
                final long localWrittenBytes = ch.write(nioBuffers, 0, nioBufferCnt);
                //tcp缓冲区已满，写不进去了，设置OP_WRITE后退出
                if (localWrittenBytes <= 0) {
                    incompleteWrite(true);
                    return;
                }
                //从缓冲区中移除发送成功的（可能有半包）
                in.removeBytes(localWrittenBytes);
                --writeSpinCount;
            }
        } while (writeSpinCount > 0);

        //没写完
        incompleteWrite(writeSpinCount < 0);
    }

    private final class NioSocketChannelConfig extends DefaultSocketChannelConfig {
        /**
         * 客户端属性：一次写入Channel的最大byte数量
         */
        private volatile int maxBytesPerGatheringWrite = Integer.MAX_VALUE;

        public NioSocketChannelConfig(SocketChannel channel, Socket javaSocket) {
            super(channel, javaSocket);
        }

        void setMaxBytesPerGatheringWrite(int maxBytesPerGatheringWrite) {
            this.maxBytesPerGatheringWrite = maxBytesPerGatheringWrite;
        }

        int getMaxBytesPerGatheringWrite() {
            return maxBytesPerGatheringWrite;
        }
    }

}
