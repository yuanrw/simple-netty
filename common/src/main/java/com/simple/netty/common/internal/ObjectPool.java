package com.simple.netty.common.internal;

import java.util.Stack;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Date: 2019-12-26
 * Time: 18:40
 *
 * @author yrw
 */
public class ObjectPool<T> {

    private Supplier<T> objectCreator;
    private Consumer<T> recycleHandler;

    private final ThreadLocal<Stack<T>> threadLocal;

    public ObjectPool(Supplier<T> objectCreator, Consumer<T> recycleHandler) {
        this.objectCreator = objectCreator;
        this.recycleHandler = recycleHandler;
        this.threadLocal = ThreadLocal.withInitial(Stack::new);
    }

    /**
     * 从对象池中获取一个对象，如果没有会创建一个新对象
     */
    public T get() {
        Stack<T> stack = threadLocal.get();
        if (stack.isEmpty()) {
            return objectCreator.get();
        } else {
            return stack.pop();
        }
    }

    public void recycle(T object) {
        recycleHandler.accept(object);
        Stack<T> stack = threadLocal.get();
        stack.push(object);
    }
}
