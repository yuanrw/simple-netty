package com.simple.netty.common.internal;

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Date: 2020-01-05
 * Time: 14:23
 *
 * @author yrw
 */
public class InternalThreadLocalMap {

    static final ThreadLocal<InternalThreadLocalMap> threadLocalMap = new ThreadLocal<>();
    static final AtomicInteger nextIndex = new AtomicInteger();

    int futureListenerStackDepth;
    int localChannelReaderStackDepth;
    Map<Class<?>, Boolean> handlerSharableCache;
    ThreadLocalRandom random;

    // String-related thread-locals
    StringBuilder stringBuilder;
    Map<Charset, CharsetEncoder> charsetEncoderCache;
    Map<Charset, CharsetDecoder> charsetDecoderCache;

    // ArrayList-related thread-locals
    ArrayList<Object> arrayList;

    public static InternalThreadLocalMap getIfSet() {
        return threadLocalMap.get();
    }

    public static InternalThreadLocalMap get() {
        InternalThreadLocalMap ret = threadLocalMap.get();
        if (ret == null) {
            ret = new InternalThreadLocalMap();
            threadLocalMap.set(ret);
        }
        return ret;
    }

    public static void remove() {
        threadLocalMap.remove();
    }

    public static void destroy() {
        threadLocalMap.remove();
    }

    public int futureListenerStackDepth() {
        return futureListenerStackDepth;
    }

    public void setFutureListenerStackDepth(int futureListenerStackDepth) {
        this.futureListenerStackDepth = futureListenerStackDepth;
    }
}
