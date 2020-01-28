package com.simple.netty.transport.channel.nio;

import com.simple.netty.transport.channel.Channel;
import com.simple.netty.transport.channel.ChannelOutboundBuffer;
import com.simple.netty.transport.channel.ChannelPipeline;

import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.List;

/**
 * Date: 2020-01-02
 * Time: 19:31
 *
 * @author yrw
 */
public abstract class AbstractNioMessageChannel extends AbstractNioChannel {

    boolean inputShutdown;

    /**
     * @see AbstractNioChannel#AbstractNioChannel(Channel, SelectableChannel, int)
     */
    protected AbstractNioMessageChannel(Channel parent, SelectableChannel ch, int readInterestOp) {
        super(parent, ch, readInterestOp);
    }

    @Override
    protected AbstractNioUnsafe newUnsafe() {
        return new NioMessageUnsafe();
    }

    @Override
    protected void doBeginRead() throws Exception {
        if (inputShutdown) {
            return;
        }
        super.doBeginRead();
    }

    /**
     * 可以读取Object的NioUnsafe
     */
    private final class NioMessageUnsafe extends AbstractNioUnsafe {

        private final List<Object> readBuf = new ArrayList<>();

        @Override
        public void read() {
            assert eventLoop().inEventLoop();
            final ChannelPipeline pipeline = pipeline();

            boolean closed = false;
            Throwable exception = null;
            try {
                do {
                    int localRead = doReadMessages(readBuf);
                    if (localRead == 0) {
                        break;
                    }
                    if (localRead < 0) {
                        closed = true;
                        break;
                    }

                } while (true);
            } catch (Throwable t) {
                exception = t;
            }

            int size = readBuf.size();

            //激活read
            for (int i = 0; i < size; i++) {
                readPending = false;
                pipeline.fireChannelRead(readBuf.get(i));
            }

            readBuf.clear();

            //激活read complete
            pipeline.fireChannelReadComplete();

            if (exception != null) {
                closed = true;
                pipeline.fireExceptionCaught(exception);
            }

            if (closed) {
                inputShutdown = true;
                if (isOpen()) {
                    close(voidPromise());
                }
            }
        }
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        final SelectionKey key = selectionKey();
        final int interestOps = key.interestOps();

        for (; ; ) {
            //发送POJO对象
            Object msg = in.current();
            if (msg == null) {
                // Wrote all messages.
                if ((interestOps & SelectionKey.OP_WRITE) != 0) {
                    key.interestOps(interestOps & ~SelectionKey.OP_WRITE);
                }
                break;
            }

            boolean done = false;
            for (int i = config().getWriteSpinCount() - 1; i >= 0; i--) {
                if (doWriteMessage(msg, in)) {
                    done = true;
                    break;
                }
            }

            if (done) {
                in.remove();
            } else {
                // Did not write all messages.
                if ((interestOps & SelectionKey.OP_WRITE) == 0) {
                    key.interestOps(interestOps | SelectionKey.OP_WRITE);
                }
                break;
            }
        }
    }

    /**
     * Read messages into the given array and return the amount which was read.
     */
    protected abstract int doReadMessages(List<Object> buf) throws Exception;

    /**
     * Write a message to the underlying {@link java.nio.channels.Channel}.
     *
     * @return {@code true} if and only if the message has been written
     */
    protected abstract boolean doWriteMessage(Object msg, ChannelOutboundBuffer in) throws Exception;
}
