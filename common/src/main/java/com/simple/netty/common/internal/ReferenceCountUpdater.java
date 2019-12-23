/*
 * Copyright 2019 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.simple.netty.common.internal;

import com.simple.netty.common.IllegalReferenceCountException;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static com.simple.netty.common.internal.ObjectUtil.checkPositive;

/**
 * 引用计数的简单实现
 * For the updated int field:
 * 偶数 => 真实refCount=(refCnt >>> 1)
 * 奇数 => 真实refCount=0
 * <p>
 * 判断奇数的时候
 * 由于(x & y) 比(x == y)的性能差，因此把小的数字单独比较，提高性能
 * Common logic for {@link ReferenceCounted} implementations
 * Date: 2019-12-14
 * Time: 12:40
 *
 * @author yrw
 */
public abstract class ReferenceCountUpdater<T extends ReferenceCounted> {

    protected ReferenceCountUpdater() {
    }

    protected abstract AtomicIntegerFieldUpdater<T> updater();

    /**
     * 获取计数域的偏移地址，方便对其进行原子操作
     *
     * @return address
     */
    protected abstract long unsafeOffset();

    /**
     * 获取对象某个域的内存偏移地址
     *
     * @param clz       类
     * @param fieldName 域
     * @return
     */
    public static long getUnsafeOffset(Class<? extends ReferenceCounted> clz, String fieldName) {
        try {
            if (PlatformDependent.hasUnsafe()) {
                return PlatformDependent.objectFieldOffset(clz.getDeclaredField(fieldName));
            }
        } catch (Throwable ignore) {
            // fall-back
        }
        return -1;
    }

    public final int initialValue() {
        return 2;
    }

    private static int realRefCnt(int rawCnt) {
        return rawCnt != 2 && rawCnt != 4 && (rawCnt & 1) != 0 ? 0 : rawCnt >>> 1;
    }

    /**
     * 如果refCnt == 0抛异常
     */
    private static int toLiveRealRefCnt(int rawCnt, int decrement) {
        if (rawCnt == 2 || rawCnt == 4 || (rawCnt & 1) == 0) {
            return rawCnt >>> 1;
        }
        // rawCnt为奇数说明已经释放
        throw new IllegalReferenceCountException(0, -decrement);
    }

    private int nonVolatileRawCnt(T instance) {
        final long offset = unsafeOffset();
        return offset != -1 ? PlatformDependent.getInt(instance, offset) : updater().get(instance);
    }

    /**
     * 获取对象实例的引用计数
     *
     * @param instance 对象
     * @return
     */
    public final int refCnt(T instance) {
        return realRefCnt(updater().get(instance));
    }

    public final boolean isLiveNonVolatile(T instance) {
        final long offset = unsafeOffset();
        final int rawCnt = offset != -1 ? PlatformDependent.getInt(instance, offset) : updater().get(instance);

        // The "real" ref count is > 0 if the rawCnt is even.
        return rawCnt == 2 || rawCnt == 4 || rawCnt == 6 || rawCnt == 8 || (rawCnt & 1) == 0;
    }

    /**
     * 直接修改计数
     */
    public final void setRefCnt(T instance, int refCnt) {
        // overflow OK here
        updater().set(instance, refCnt > 0 ? refCnt << 1 : 1);
    }

    /**
     * 重置refCnt=1
     */
    public final void resetRefCnt(T instance) {
        updater().set(instance, initialValue());
    }

    public final T retain(T instance) {
        return retain0(instance, 1, 2);
    }

    public final T retain(T instance, int increment) {
        //raw count 等于 rel count<<2，溢出不要紧
        int rawIncrement = checkPositive(increment, "increment") << 1;
        return retain0(instance, increment, rawIncrement);
    }


    private T retain0(T instance, final int increment, final int rawIncrement) {
        int oldRef = updater().getAndAdd(instance, rawIncrement);
        //奇数抛出异常
        if (oldRef != 2 && oldRef != 4 && (oldRef & 1) != 0) {
            throw new IllegalReferenceCountException(0, increment);
        }
        // don't pass 0!
        if ((oldRef <= 0 && oldRef + rawIncrement >= 0)
            || (oldRef >= 0 && oldRef + rawIncrement < oldRef)) {
            // 处理溢出
            updater().getAndAdd(instance, -rawIncrement);
            throw new IllegalReferenceCountException(realRefCnt(oldRef), increment);
        }
        return instance;
    }

    public final boolean release(T instance) {
        int rawCnt = nonVolatileRawCnt(instance);
        return rawCnt == 2 ? tryFinalRelease0(instance, 2) || retryRelease0(instance, 1)
            : nonFinalRelease0(instance, 1, rawCnt, toLiveRealRefCnt(rawCnt, 1));
    }

    public final boolean release(T instance, int decrement) {
        int rawCnt = nonVolatileRawCnt(instance);
        int realCnt = toLiveRealRefCnt(rawCnt, checkPositive(decrement, "decrement"));
        return decrement == realCnt ? tryFinalRelease0(instance, rawCnt) || retryRelease0(instance, decrement)
            : nonFinalRelease0(instance, decrement, rawCnt, realCnt);
    }

    private boolean tryFinalRelease0(T instance, int expectRawCnt) {
        // 把引用设置为奇数，代表释放
        return updater().compareAndSet(instance, expectRawCnt, 1);
    }

    private boolean nonFinalRelease0(T instance, int decrement, int rawCnt, int realCnt) {
        if (decrement < realCnt
            // 溢出不要紧
            && updater().compareAndSet(instance, rawCnt, rawCnt - (decrement << 1))) {
            return false;
        }
        return retryRelease0(instance, decrement);
    }

    private boolean retryRelease0(T instance, int decrement) {
        for (; ; ) {
            int rawCnt = updater().get(instance), realCnt = toLiveRealRefCnt(rawCnt, decrement);
            if (decrement == realCnt) {
                if (tryFinalRelease0(instance, rawCnt)) {
                    return true;
                }
            } else if (decrement < realCnt) {
                if (updater().compareAndSet(instance, rawCnt, rawCnt - (decrement << 1))) {
                    return false;
                }
            } else {
                throw new IllegalReferenceCountException(realCnt, -decrement);
            }

            // this benefits throughput under high contention
            Thread.yield();
        }
    }
}
