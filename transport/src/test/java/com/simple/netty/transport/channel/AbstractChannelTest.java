package com.simple.netty.transport.channel;

import com.simple.netty.buffer.ByteBuf;
import com.simple.netty.buffer.Unpooled;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Date: 2020-01-28
 * Time: 19:14
 *
 * @author yrw
 */
public class AbstractChannelTest {

    @Test
    public void ensureInitialRegistrationFiresActive() throws Throwable {
        EventLoop eventLoop = mock(EventLoop.class);
        // This allows us to have a single-threaded test
        when(eventLoop.inEventLoop()).thenReturn(true);

        TestChannel channel = new TestChannel();
        ChannelInboundHandler handler = mock(ChannelInboundHandler.class);
        channel.pipeline().addLast(handler);

        registerChannel(eventLoop, channel);

        //检查方法的调用次数
        verify(handler).handlerAdded(any(ChannelHandlerContext.class));
        verify(handler).channelRegistered(any(ChannelHandlerContext.class));
        verify(handler).channelActive(any(ChannelHandlerContext.class));
    }

    @Test
    public void ensureSubsequentRegistrationDoesNotFireActive() throws Throwable {
        final EventLoop eventLoop = mock(EventLoop.class);
        // This allows us to have a single-threaded test
        when(eventLoop.inEventLoop()).thenReturn(true);

        doAnswer(invocationOnMock -> {
            ((Runnable) invocationOnMock.getArgument(0)).run();
            return null;
        }).when(eventLoop).execute(any(Runnable.class));

        final TestChannel channel = new TestChannel();
        ChannelInboundHandler handler = mock(ChannelInboundHandler.class);

        channel.pipeline().addLast(handler);

        registerChannel(eventLoop, channel);
        channel.unsafe().deregister(new DefaultChannelPromise(channel));

        registerChannel(eventLoop, channel);

        verify(handler).handlerAdded(any(ChannelHandlerContext.class));

        // Should register twice
        verify(handler, times(2)).channelRegistered(any(ChannelHandlerContext.class));
        verify(handler).channelActive(any(ChannelHandlerContext.class));
        verify(handler).channelUnregistered(any(ChannelHandlerContext.class));
    }

    @Test
    public void testClosedChannelExceptionCarryIOException() throws Exception {
        final IOException ioException = new IOException();
        final Channel channel = new TestChannel() {
            private boolean open = true;
            private boolean active;

            @Override
            protected AbstractUnsafe newUnsafe() {
                return new AbstractUnsafe() {
                    @Override
                    public void connect(
                        SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
                        active = true;
                        promise.setSuccess();
                    }
                };
            }

            @Override
            protected void doClose() {
                active = false;
                open = false;
            }

            @Override
            protected void doWrite(ChannelOutboundBuffer in) throws Exception {
                throw ioException;
            }

            @Override
            public boolean isOpen() {
                return open;
            }

            @Override
            public boolean isActive() {
                return active;
            }
        };

        EventLoop loop = new DefaultEventLoop();

        ByteBuf buf = Unpooled.wrappedBuffer("".getBytes());
        try {
            registerChannel(loop, channel);
            channel.connect(new InetSocketAddress("localhost", 8888)).sync();
            assertSame(ioException, channel.writeAndFlush(buf).await().cause());

            assertClosedChannelException(channel.writeAndFlush(buf), ioException);
            assertClosedChannelException(channel.write(buf), ioException);
            assertClosedChannelException(channel.bind(new InetSocketAddress("localhost", 8888)), ioException);
        } finally {
            channel.close();
            loop.shutdownGracefully();
        }
    }

    private static void assertClosedChannelException(ChannelFuture future, IOException expected)
        throws InterruptedException {
        Throwable cause = future.await().cause();
        assertTrue(cause instanceof ClosedChannelException);
        assertSame(expected, cause.getCause());
    }

    private static void registerChannel(EventLoop eventLoop, Channel channel) throws Exception {
        DefaultChannelPromise future = new DefaultChannelPromise(channel);
        channel.unsafe().register(eventLoop, future);
        future.sync(); // Cause any exceptions to be thrown
    }

    private static class TestChannel extends AbstractChannel {
        private final ChannelConfig config = new DefaultChannelConfig(this);

        TestChannel() {
            super(null);
        }

        @Override
        public ChannelConfig config() {
            return config;
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        protected AbstractUnsafe newUnsafe() {
            return new AbstractUnsafe() {
                @Override
                public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
                    promise.setFailure(new UnsupportedOperationException());
                }
            };
        }

        @Override
        protected SocketAddress localAddress0() {
            return null;
        }

        @Override
        protected SocketAddress remoteAddress0() {
            return null;
        }

        @Override
        protected void doBind(SocketAddress localAddress) {
        }

        @Override
        protected void doDisconnect() {
        }

        @Override
        protected void doClose() {
        }

        @Override
        protected void doBeginRead() {
        }

        @Override
        protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        }
    }
}
