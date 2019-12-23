package com.simple.netty.common.internal;

import java.util.Stack;

/**
 * 一个简单的对象池
 * Date: 2019-12-15
 * Time: 20:22
 *
 * @author yrw
 */
public abstract class ObjectPool<T> {

    private Stack<T> stack;

    ObjectPool() {
    }

    private final ThreadLocal<Stack<T>> threadLocal = new ThreadLocal<>();

    /**
     * 从对象池里拿一个对象，如果池中没有对象会用newObject()创建一个
     */
    public T get() {
        Stack<T> stack = threadLocal.get();
        T object = stack.pop();
        return object != null ? object : newObject();
    }

    /**
     * 创建新对象的方法
     *
     * @param <T>
     */
    public interface ObjectCreator<T> {
        T newObject();
    }

    protected abstract T newObject();

    /**
     * 创建{@link ObjectPool}
     */
    public static <T> ObjectPool<T> newPool(final ObjectCreator<T> creator) {
        return new ObjectPool<T>() {
            @Override
            protected T newObject() {
                return creator.newObject();
            }
        };
    }
}
