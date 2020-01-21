package com.simple.netty.transport.channel;

import com.simple.netty.common.concurrent.EventExecutor;
import com.simple.netty.common.concurrent.EventExecutorGroup;
import com.simple.netty.common.internal.ObjectUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;

/**
 * 支持运行期间动态修改Handler，因此存在多线程并发场景
 * Date: 2019-12-30
 * Time: 21:01
 *
 * @author yrw
 */
public class DefaultChannelPipeline implements ChannelPipeline {
    static final Logger logger = LoggerFactory.getLogger(DefaultChannelPipeline.class);

    final AbstractChannelHandlerContext head;
    final AbstractChannelHandlerContext tail;

    private Map<EventExecutorGroup, EventExecutor> childExecutors;
    private final Channel channel;
    private final VoidChannelPromise voidPromise;
    private final ChannelFuture succeededFuture;

    /**
     * 往ChannelPipeline里增加Handler或者移除Handler的任务
     * 这是一个链表的头
     */
    private PendingHandlerCallback pendingHandlerCallbackHead;

    private boolean firstRegistration = true;
    private boolean registered;

    protected DefaultChannelPipeline(Channel channel) {
        this.channel = ObjectUtil.checkNotNull(channel, "channel");
        voidPromise = new VoidChannelPromise(channel, true);
        succeededFuture = new SucceededChannelFuture(channel, null);

        head = null;
        tail = null;
    }

    private AbstractChannelHandlerContext newContext(EventExecutorGroup group, String name, ChannelHandler handler) {
        return new DefaultChannelHandlerContext(this, childExecutor(group), name, handler);
    }

    private EventExecutor childExecutor(EventExecutorGroup group) {
        if (group == null) {
            return null;
        }
        Map<EventExecutorGroup, EventExecutor> childExecutors = this.childExecutors;
        if (childExecutors == null) {
            // Use size of 4 as most people only use one extra EventExecutor.
            childExecutors = this.childExecutors = new IdentityHashMap<>(4);
        }
        // Pin one of the child executors once and remember it so that the same child executor
        // is used to fire events for the same channel.
        EventExecutor childExecutor = childExecutors.get(group);
        if (childExecutor == null) {
            childExecutor = group.next();
            childExecutors.put(group, childExecutor);
        }
        return childExecutor;
    }

    @Override
    public ChannelPipeline addFirst(ChannelHandler... handlers) {
        ObjectUtil.checkNotNull(handlers, "handlers");
        if (handlers.length == 0 || handlers[0] == null) {
            return this;
        }

        int size;
        for (size = 1; size < handlers.length; size++) {
            if (handlers[size] == null) {
                break;
            }
        }

        for (int i = size - 1; i >= 0; i--) {
            ChannelHandler h = handlers[i];
            addFirst(null, null, h);
        }

        return this;
    }

    @Override
    public ChannelPipeline addFirst(String name, ChannelHandler handler) {
        return addFirst(null, name, handler);
    }

    @Override
    public final ChannelPipeline addFirst(EventExecutorGroup group, String name, ChannelHandler handler) {
        final AbstractChannelHandlerContext newCtx;
        synchronized (this) {

            //创建新的Context
            newCtx = newContext(group, name, handler);

            //链表中添加节点
            addFirst0(newCtx);

            //如果channel还没有注册到EventLoop上，添加一个任务，一旦channel被注册，就调用ChannelHandler.handlerAdded(...)
            if (!registered) {
                newCtx.setAddPending();
                callHandlerCallbackLater(newCtx, true);
                return this;
            }

            //ChannelHandler.handlerAdded(...)交给EventLoop去调用
            EventExecutor executor = newCtx.executor();
            if (!executor.inEventLoop()) {
                callHandlerAddedInEventLoop(newCtx, executor);
                return this;
            }
        }

        //直接调用ChannelHandler.handlerAdded(...)
        callHandlerAdded0(newCtx);
        return this;
    }

    @Override
    public ChannelPipeline addLast(ChannelHandler... handlers) {
        ObjectUtil.checkNotNull(handlers, "handlers");

        for (ChannelHandler h : handlers) {
            if (h == null) {
                break;
            }
            addLast(null, null, h);
        }

        return this;
    }

    private void addFirst0(AbstractChannelHandlerContext newCtx) {
        AbstractChannelHandlerContext nextCtx = head.next;
        newCtx.prev = head;
        newCtx.next = nextCtx;
        head.next = newCtx;
        nextCtx.prev = newCtx;
    }

    private void callHandlerCallbackLater(AbstractChannelHandlerContext ctx, boolean added) {
        assert !registered;

        PendingHandlerCallback task = added ? new PendingHandlerAddedTask(ctx) : new PendingHandlerRemovedTask(ctx);
        PendingHandlerCallback pending = pendingHandlerCallbackHead;
        if (pending == null) {
            pendingHandlerCallbackHead = task;
        } else {
            // Find the tail of the linked-list.
            while (pending.next != null) {
                pending = pending.next;
            }
            pending.next = task;
        }
    }

    private void callHandlerAddedInEventLoop(final AbstractChannelHandlerContext newCtx, EventExecutor executor) {
        newCtx.setAddPending();
        executor.execute(() -> callHandlerAdded0(newCtx));
    }

    private void callHandlerAdded0(final AbstractChannelHandlerContext ctx) {
        try {
            ctx.callHandlerAdded();
        } catch (Throwable t) {
            boolean removed = false;
            try {
                atomicRemoveFromHandlerList(ctx);
                ctx.callHandlerRemoved();
                removed = true;
            } catch (Throwable t2) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Failed to remove a handler: " + ctx.name(), t2);
                }
            }

            if (removed) {
                fireExceptionCaught(new RuntimeException(
                    ctx.handler().getClass().getName() +
                        ".handlerAdded() has thrown an exception; removed.", t));
            } else {
                fireExceptionCaught(new RuntimeException(
                    ctx.handler().getClass().getName() +
                        ".handlerAdded() has thrown an exception; also failed to remove.", t));
            }
        }
    }

    private void callHandlerRemoved0(final AbstractChannelHandlerContext ctx) {
        // Notify the complete removal.
        try {
            ctx.callHandlerRemoved();
        } catch (Throwable t) {
            fireExceptionCaught(new RuntimeException(
                ctx.handler().getClass().getName() + ".handlerRemoved() has thrown an exception.", t));
        }
    }

    final void invokeHandlerAddedIfNeeded() {
        assert channel.eventLoop().inEventLoop();
        if (firstRegistration) {
            firstRegistration = false;
            // We are now registered to the EventLoop. It's time to call the callbacks for the ChannelHandlers,
            // that were added before the registration was done.
            callHandlerAddedForAllHandlers();
        }
    }

    @Override
    public ChannelPipeline addLast(String name, ChannelHandler handler) {
        return addLast(null, name, handler);
    }

    @Override
    public final ChannelPipeline addLast(EventExecutorGroup group, String name, ChannelHandler handler) {
        final AbstractChannelHandlerContext newCtx;
        synchronized (this) {

            newCtx = newContext(group, name, handler);

            addLast0(newCtx);

            if (!registered) {
                newCtx.setAddPending();
                callHandlerCallbackLater(newCtx, true);
                return this;
            }

            EventExecutor executor = newCtx.executor();
            if (!executor.inEventLoop()) {
                callHandlerAddedInEventLoop(newCtx, executor);
                return this;
            }
        }
        callHandlerAdded0(newCtx);
        return this;
    }

    private void addLast0(AbstractChannelHandlerContext newCtx) {
        AbstractChannelHandlerContext prev = tail.prev;
        newCtx.prev = prev;
        newCtx.next = tail;
        prev.next = newCtx;
        tail.prev = newCtx;
    }

    @Override
    public ChannelPipeline remove(ChannelHandler handler) {
        remove(getContextOrDie(handler));
        return this;
    }

    private AbstractChannelHandlerContext remove(final AbstractChannelHandlerContext ctx) {
        assert ctx != head && ctx != tail;

        synchronized (this) {
            atomicRemoveFromHandlerList(ctx);

            if (!registered) {
                callHandlerCallbackLater(ctx, false);
                return ctx;
            }

            EventExecutor executor = ctx.executor();
            if (!executor.inEventLoop()) {
                executor.execute(() -> callHandlerRemoved0(ctx));
                return ctx;
            }
        }
        callHandlerRemoved0(ctx);
        return ctx;
    }

    private AbstractChannelHandlerContext getContextOrDie(ChannelHandler handler) {
        AbstractChannelHandlerContext ctx = (AbstractChannelHandlerContext) context(handler);
        if (ctx == null) {
            throw new NoSuchElementException(handler.getClass().getName());
        } else {
            return ctx;
        }
    }

    private void callHandlerAddedForAllHandlers() {
        final PendingHandlerCallback pendingHandlerCallbackHead;
        synchronized (this) {
            assert !registered;

            // This Channel itself was registered.
            registered = true;

            pendingHandlerCallbackHead = this.pendingHandlerCallbackHead;
            // Null out so it can be GC'ed.
            this.pendingHandlerCallbackHead = null;
        }

        // This must happen outside of the synchronized(...) block as otherwise handlerAdded(...) may be called while
        // holding the lock and so produce a deadlock if handlerAdded(...) will try to add another handler from outside
        // the EventLoop.
        PendingHandlerCallback task = pendingHandlerCallbackHead;
        while (task != null) {
            task.execute();
            task = task.next;
        }
    }

    @Override
    public ChannelHandler remove(String name) {
        return remove(getContextOrDie(name)).handler();
    }

    private AbstractChannelHandlerContext getContextOrDie(String name) {
        AbstractChannelHandlerContext ctx = (AbstractChannelHandlerContext) context(name);
        if (ctx == null) {
            throw new NoSuchElementException(name);
        } else {
            return ctx;
        }
    }

    private synchronized void atomicRemoveFromHandlerList(AbstractChannelHandlerContext ctx) {
        AbstractChannelHandlerContext prev = ctx.prev;
        AbstractChannelHandlerContext next = ctx.next;
        prev.next = next;
        next.prev = prev;
    }

    @Override
    public ChannelHandler removeFirst() {
        if (head.next == tail) {
            throw new NoSuchElementException();
        }
        return remove(head.next).handler();
    }

    @Override
    public ChannelHandler removeLast() {
        if (head.next == tail) {
            throw new NoSuchElementException();
        }
        return remove(tail.prev).handler();
    }

    @Override
    public ChannelHandler first() {
        ChannelHandlerContext first = firstContext();
        if (first == null) {
            return null;
        }
        return first.handler();
    }

    @Override
    public ChannelHandlerContext firstContext() {
        AbstractChannelHandlerContext first = head.next;
        if (first == tail) {
            return null;
        }
        return head.next;
    }

    @Override
    public ChannelHandler last() {
        AbstractChannelHandlerContext last = tail.prev;
        if (last == head) {
            return null;
        }
        return last.handler();
    }

    @Override
    public ChannelHandlerContext lastContext() {
        AbstractChannelHandlerContext last = tail.prev;
        if (last == head) {
            return null;
        }
        return last;
    }

    @Override
    public ChannelHandlerContext context(ChannelHandler handler) {
        ObjectUtil.checkNotNull(handler, "handler");

        AbstractChannelHandlerContext ctx = head.next;
        for (; ; ) {

            if (ctx == null) {
                return null;
            }

            if (ctx.handler() == handler) {
                return ctx;
            }

            ctx = ctx.next;
        }
    }

    @Override
    public ChannelHandlerContext context(String name) {
        return context0(ObjectUtil.checkNotNull(name, "name"));
    }

    private AbstractChannelHandlerContext context0(String name) {
        AbstractChannelHandlerContext context = head.next;
        while (context != tail) {
            if (context.name().equals(name)) {
                return context;
            }
            context = context.next;
        }
        return null;
    }

    @Override
    public Channel channel() {
        return channel;
    }

    @Override
    public List<String> names() {
        return null;
    }

    @Override
    public ChannelPipeline fireChannelRegistered() {
        AbstractChannelHandlerContext.invokeChannelRegistered(head);
        return this;
    }

    @Override
    public ChannelPipeline fireChannelUnregistered() {
        AbstractChannelHandlerContext.invokeChannelUnregistered(head);
        return this;
    }

    @Override
    public ChannelPipeline fireChannelActive() {
        AbstractChannelHandlerContext.invokeChannelActive(head);
        return this;
    }

    @Override
    public ChannelPipeline fireChannelInactive() {
        AbstractChannelHandlerContext.invokeChannelInactive(head);
        return this;
    }

    @Override
    public ChannelPipeline fireExceptionCaught(Throwable cause) {
        AbstractChannelHandlerContext.invokeExceptionCaught(head, cause);
        return this;
    }

    @Override
    public ChannelPipeline fireUserEventTriggered(Object event) {
        AbstractChannelHandlerContext.invokeUserEventTriggered(head, event);
        return this;
    }

    @Override
    public ChannelPipeline fireChannelRead(Object msg) {
        AbstractChannelHandlerContext.invokeChannelRead(head, msg);
        return this;
    }

    @Override
    public ChannelPipeline fireChannelReadComplete() {
        AbstractChannelHandlerContext.invokeChannelReadComplete(head);
        return this;
    }

    @Override
    public ChannelPipeline fireChannelWritabilityChanged() {
        AbstractChannelHandlerContext.invokeChannelWritabilityChanged(head);
        return this;
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress) {
        return tail.bind(localAddress);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress) {
        return tail.connect(remoteAddress);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        return tail.connect(remoteAddress, localAddress);
    }

    @Override
    public ChannelFuture disconnect() {
        return tail.disconnect();
    }

    @Override
    public ChannelFuture close() {
        return tail.close();
    }

    @Override
    public ChannelFuture deregister() {
        return tail.deregister();
    }

    @Override
    public final ChannelPipeline flush() {
        tail.flush();
        return this;
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
        return tail.bind(localAddress, promise);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
        return tail.connect(remoteAddress, promise);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        return tail.connect(remoteAddress, localAddress, promise);
    }

    @Override
    public final ChannelFuture disconnect(ChannelPromise promise) {
        return tail.disconnect(promise);
    }

    @Override
    public final ChannelFuture close(ChannelPromise promise) {
        return tail.close(promise);
    }

    @Override
    public final ChannelFuture deregister(final ChannelPromise promise) {
        return tail.deregister(promise);
    }

    @Override
    public final ChannelPipeline read() {
        tail.read();
        return this;
    }

    @Override
    public final ChannelFuture write(Object msg) {
        return tail.write(msg);
    }

    @Override
    public final ChannelFuture write(Object msg, ChannelPromise promise) {
        return tail.write(msg, promise);
    }

    protected void incrementPendingOutboundBytes(long size) {
        ChannelOutboundBuffer buffer = channel.unsafe().outboundBuffer();
        if (buffer != null) {
            buffer.incrementPendingOutboundBytes(size);
        }
    }

    protected void decrementPendingOutboundBytes(long size) {
        ChannelOutboundBuffer buffer = channel.unsafe().outboundBuffer();
        if (buffer != null) {
            buffer.decrementPendingOutboundBytes(size);
        }
    }

    @Override
    public final ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
        return tail.writeAndFlush(msg, promise);
    }

    @Override
    public final ChannelFuture writeAndFlush(Object msg) {
        return tail.writeAndFlush(msg);
    }

    @Override
    public final ChannelPromise newPromise() {
        return new DefaultChannelPromise(channel);
    }

    @Override
    public final ChannelFuture newSucceededFuture() {
        return succeededFuture;
    }

    @Override
    public final ChannelFuture newFailedFuture(Throwable cause) {
        return new FailedChannelFuture(channel, null, cause);
    }

    @Override
    public final ChannelPromise voidPromise() {
        return voidPromise;
    }

    @Override
    public Iterator<Map.Entry<String, ChannelHandler>> iterator() {
        return null;
    }

    private abstract static class PendingHandlerCallback implements Runnable {
        final AbstractChannelHandlerContext ctx;
        PendingHandlerCallback next;

        PendingHandlerCallback(AbstractChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        abstract void execute();
    }

    private final class PendingHandlerAddedTask extends PendingHandlerCallback {

        PendingHandlerAddedTask(AbstractChannelHandlerContext ctx) {
            super(ctx);
        }

        @Override
        public void run() {
            callHandlerAdded0(ctx);
        }

        @Override
        void execute() {
            EventExecutor executor = ctx.executor();
            if (executor.inEventLoop()) {
                callHandlerAdded0(ctx);
            } else {
                try {
                    executor.execute(this);
                } catch (RejectedExecutionException e) {
                    if (logger.isWarnEnabled()) {
                        logger.warn(
                            "Can't invoke handlerAdded() as the EventExecutor {} rejected it, removing handler {}.",
                            executor, ctx.name(), e);
                    }
                    atomicRemoveFromHandlerList(ctx);
                    ctx.setRemoved();
                }
            }
        }
    }

    private final class PendingHandlerRemovedTask extends PendingHandlerCallback {

        PendingHandlerRemovedTask(AbstractChannelHandlerContext ctx) {
            super(ctx);
        }

        @Override
        public void run() {
            callHandlerRemoved0(ctx);
        }

        @Override
        void execute() {
            EventExecutor executor = ctx.executor();
            if (executor.inEventLoop()) {
                callHandlerRemoved0(ctx);
            } else {
                try {
                    executor.execute(this);
                } catch (RejectedExecutionException e) {
                    if (logger.isWarnEnabled()) {
                        logger.warn(
                            "Can't invoke handlerRemoved() as the EventExecutor {} rejected it," +
                                " removing handler {}.", executor, ctx.name(), e);
                    }
                    // remove0(...) was call before so just call AbstractChannelHandlerContext.setRemoved().
                    ctx.setRemoved();
                }
            }
        }
    }
}
