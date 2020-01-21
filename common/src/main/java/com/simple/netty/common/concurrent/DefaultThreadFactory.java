package com.simple.netty.common.concurrent;

import com.simple.netty.common.internal.ObjectUtil;
import com.simple.netty.common.internal.StringUtil;

import java.util.Locale;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 简单的ThreadFactory，给线程清晰地命名
 * Date: 2020-01-21
 * Time: 15:32
 *
 * @author yrw
 */
public class DefaultThreadFactory implements ThreadFactory {
    private static final AtomicInteger poolId = new AtomicInteger();

    private final AtomicInteger nextId = new AtomicInteger();
    private final String prefix;

    protected final ThreadGroup threadGroup;

    public DefaultThreadFactory(String poolName) {
        ObjectUtil.checkNotNull(poolName, "poolName");

        prefix = poolName + '-' + poolId.incrementAndGet() + '-';
        threadGroup = System.getSecurityManager() == null ?
            Thread.currentThread().getThreadGroup() : System.getSecurityManager().getThreadGroup();
    }

    public static String toPoolName(Class<?> poolType) {
        ObjectUtil.checkNotNull(poolType, "poolType");

        String poolName = StringUtil.simpleClassName(poolType);
        switch (poolName.length()) {
            case 0:
                return "unknown";
            case 1:
                return poolName.toLowerCase(Locale.US);
            default:
                if (Character.isUpperCase(poolName.charAt(0)) && Character.isLowerCase(poolName.charAt(1))) {
                    return Character.toLowerCase(poolName.charAt(0)) + poolName.substring(1);
                } else {
                    return poolName;
                }
        }
    }

    @Override
    public Thread newThread(Runnable r) {
        //创建一个新线程
        return new Thread(null, r, prefix + nextId.incrementAndGet());
    }
}
