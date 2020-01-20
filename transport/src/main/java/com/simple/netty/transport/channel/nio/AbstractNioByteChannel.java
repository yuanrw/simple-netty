package com.simple.netty.transport.channel.nio;

import com.simple.netty.buffer.ByteBuf;
import com.simple.netty.buffer.ByteBufAllocator;
import com.simple.netty.transport.channel.Channel;
import com.simple.netty.transport.channel.ChannelConfig;
import com.simple.netty.transport.channel.ChannelOutboundBuffer;
import com.simple.netty.transport.channel.ChannelPipeline;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

/**
 * Date: 2020-01-03
 * Time: 16:06
 *
 * @author yrw
 */
public abstract class AbstractNioByteChannel extends AbstractNioChannel {

    /**
     * 没有设置OP_WRITE操作位的时候，多路复用器不会轮询写事件，用flushTask执行任务
     */
    private final Runnable flushTask = () -> ((AbstractNioUnsafe) unsafe()).flush0();

    private final int WRITE_STATUS_SNDBUF_FULL = Integer.MAX_VALUE;

    protected AbstractNioByteChannel(Channel parent, SelectableChannel ch) {
        super(parent, ch, SelectionKey.OP_READ);
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        //获取循环发送次数，发送次数指当一次发送没有写完时，继续循环发送的次数
        int writeSpinCount = config().getWriteSpinCount();
        do {
            //从环形数组中弹出一条消息
            ByteBuf msg = in.current();
            if (msg == null) {
                // 为空代表全部写完了，清除半包标识，结束写
                clearOpWrite();
                return;
            }
            //减去发送次数
            writeSpinCount -= doWriteInternal(in, msg);
        } while (writeSpinCount > 0);

        //没写完，正常情况下writeSpinCount=0，如果缓冲区满了，writeSpinCount<0
        incompleteWrite(writeSpinCount < 0);
    }

    private int doWriteInternal(ChannelOutboundBuffer in, ByteBuf buf) throws Exception {
        if (!buf.isReadable()) {
            //消息长度为0，从数组中删除
            in.remove();
            return 0;
        }

        //把buf写入Channel
        final int localFlushedAmount = doWriteBytes(buf);
        if (localFlushedAmount > 0) {
            if (!buf.isReadable()) {
                //移除消息，防止重复发送
                in.remove();
            }
            //写入成功，返回次数1
            return 1;
        }
        //如果localFlushedAmount=0代表tcp缓冲区已满，返回 WRITE_STATUS_SNDBUF_FULL
        return WRITE_STATUS_SNDBUF_FULL;
    }

    protected final void incompleteWrite(boolean setOpWrite) {
        if (setOpWrite) {
            //设置半包标志（标志位设为write）
            setOpWrite();
        } else {
            //不设置半包标志（标志位取消write），稍后eventLoop线程去flush
            clearOpWrite();
            eventLoop().execute(flushTask);
        }
    }

    @Override
    protected AbstractNioUnsafe newUnsafe() {
        return new NioByteUnsafe();
    }

    /**
     * 可以处理Byte的NioUnsafe
     */
    protected class NioByteUnsafe extends AbstractNioUnsafe {

        @Override
        public final void read() {
            //获取配置
            final ChannelConfig config = config();

            final ChannelPipeline pipeline = pipeline();
            final ByteBufAllocator allocator = config.getAllocator();

            ByteBuf byteBuf = null;
            boolean close = false;
            try {
                do {
                    //申请一个ByteBuf
                    byteBuf = allocator.heapBuffer();
                    int lastBytesRead = doReadBytes(byteBuf);
                    if (lastBytesRead <= 0) {
                        // 没有读入字节，需要释放byteBuf
                        byteBuf.release();
                        byteBuf = null;

                        //-1代表有IO异常，要关闭连接
                        close = lastBytesRead < 0;
                        if (close) {
                            // 接收到EOF，代表读到结尾
                            readPending = false;
                        }
                        //退出循环
                        break;
                    }

                    readPending = false;

                    //激活读事件（此处不一定是完整的一条消息，可能粘包）
                    pipeline.fireChannelRead(byteBuf);
                    byteBuf = null;
                } while (true);
                //todo: 次数应该有读取上限，防止一次读取过多数据，影响到后面排队的task

                pipeline.fireChannelReadComplete();

                //是否需要关闭Channel
                if (close) {
                    closeOnRead();
                }
            } catch (Throwable t) {
                handleReadException(pipeline, byteBuf, t, close);
            } finally {
                //这里要确认读完后把read op给清除掉
                if (!readPending) {
                    removeReadOp();
                }
            }
        }

        private void closeOnRead() {
            close(voidPromise());
        }

        private void handleReadException(ChannelPipeline pipeline, ByteBuf byteBuf, Throwable cause, boolean close) {
            if (byteBuf != null) {
                if (byteBuf.isReadable()) {
                    readPending = false;
                    pipeline.fireChannelRead(byteBuf);
                } else {
                    byteBuf.release();
                }
            }

            //激活read complete
            pipeline.fireChannelReadComplete();

            //激活exception
            pipeline.fireExceptionCaught(cause);

            if (close || cause instanceof IOException) {
                closeOnRead();
            }
        }
    }

    /**
     * 把byte写入{@link ByteBuf}
     *
     * @param buf
     * @return 写入字节数
     * @throws Exception
     */
    protected abstract int doReadBytes(ByteBuf buf) throws Exception;

    /**
     * 把 {@link ByteBuf} 中的字节写入{@link java.nio.channels.Channel}.
     *
     * @param buf
     * @return 写入的字节数
     * @throws Exception
     */
    protected abstract int doWriteBytes(ByteBuf buf) throws Exception;

    protected final void setOpWrite() {
        final SelectionKey key = selectionKey();
        if (!key.isValid()) {
            return;
        }
        final int interestOps = key.interestOps();
        if ((interestOps & SelectionKey.OP_WRITE) == 0) {
            key.interestOps(interestOps | SelectionKey.OP_WRITE);
        }
    }

    protected final void clearOpWrite() {
        final SelectionKey key = selectionKey();
        if (!key.isValid()) {
            return;
        }
        final int interestOps = key.interestOps();
        if ((interestOps & SelectionKey.OP_WRITE) != 0) {
            key.interestOps(interestOps & ~SelectionKey.OP_WRITE);
        }
    }
}
