package com.simple.netty.common.internal;

import java.util.Stack;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Date: 2019-12-26
 * Time: 18:40
 *
 * @author yrw
 */
public class ObjectPool<T> {

    private Function<Consumer<T>, T> objectCreator;

    private final ThreadLocal<Stack<T>> threadLocal;

    public ObjectPool(Function<Consumer<T>, T> objectCreator) {
        this.objectCreator = objectCreator;
        this.threadLocal = ThreadLocal.withInitial(Stack::new);
    }

    /**
     * 从对象池中获取一个对象，如果没有会创建一个新对象
     */
    public T get() {
        Stack<T> stack = threadLocal.get();
        if (stack.isEmpty()) {
            Consumer<T> recycleHandler = stack::push;
            return objectCreator.apply(recycleHandler);
        } else {
            return stack.pop();
        }
    }
}
