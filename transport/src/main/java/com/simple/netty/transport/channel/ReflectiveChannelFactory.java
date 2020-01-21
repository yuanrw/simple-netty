package com.simple.netty.transport.channel;

import com.simple.netty.common.internal.ObjectUtil;
import com.simple.netty.common.internal.StringUtil;

import java.lang.reflect.Constructor;

/**
 * Date: 2020-01-20
 * Time: 18:28
 *
 * @author yrw
 */
public class ReflectiveChannelFactory<T extends Channel> {
    private final Constructor<? extends T> constructor;

    public ReflectiveChannelFactory(Class<? extends T> clazz) {
        ObjectUtil.checkNotNull(clazz, "clazz");
        try {
            this.constructor = clazz.getConstructor();
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Class " + StringUtil.simpleClassName(clazz) +
                " does not have a public non-arg constructor", e);
        }
    }

    public T newChannel() {
        try {
            return constructor.newInstance();
        } catch (Throwable t) {
            throw new RuntimeException("Unable to create Channel from class " + constructor.getDeclaringClass(), t);
        }
    }
}
