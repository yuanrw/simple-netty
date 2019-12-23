/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.simple.netty.common.internal;

import java.util.Collection;

/**
 * 工具类
 * Date: 2019-12-14
 * Time: 12:40
 *
 * @author yrw
 */
public final class ObjectUtil {

    private ObjectUtil() {
    }

    /**
     * 检查参数不为空
     */
    public static <T> T checkNotNull(T arg, String text) {
        if (arg == null) {
            throw new NullPointerException(text);
        }
        return arg;
    }

    /**
     * 检查参数>0
     */
    public static int checkPositive(int i, String name) {
        if (i <= 0) {
            throw new IllegalArgumentException(name + ": " + i + " (expected: > 0)");
        }
        return i;
    }

    /**
     * 检查参数>0
     */
    public static long checkPositive(long i, String name) {
        if (i <= 0) {
            throw new IllegalArgumentException(name + ": " + i + " (expected: > 0)");
        }
        return i;
    }

    /**
     * 检查参数>=0
     */
    public static int checkPositiveOrZero(int i, String name) {
        if (i < 0) {
            throw new IllegalArgumentException(name + ": " + i + " (expected: >= 0)");
        }
        return i;
    }

    /**
     * 检查参数>=0
     */
    public static long checkPositiveOrZero(long i, String name) {
        if (i < 0) {
            throw new IllegalArgumentException(name + ": " + i + " (expected: >= 0)");
        }
        return i;
    }

    /**
     * 检查参数不为空
     */
    public static <T> T[] checkNonEmpty(T[] array, String name) {
        checkNotNull(array, name);
        checkPositive(array.length, name + ".length");
        return array;
    }

    /**
     * 检查参数不为空
     */
    public static <T extends Collection<?>> T checkNonEmpty(T collection, String name) {
        checkNotNull(collection, name);
        checkPositive(collection.size(), name + ".size");
        return collection;
    }

    /**
     * 把Integer转成int
     *
     * @param wrapper      包装类
     * @param defaultValue 默认值
     * @return 基本类型
     */
    public static int intValue(Integer wrapper, int defaultValue) {
        return wrapper != null ? wrapper : defaultValue;
    }

    /**
     * 把Long转成long
     *
     * @param wrapper      包装类
     * @param defaultValue 默认值
     * @return 基本类型
     */
    public static long longValue(Long wrapper, long defaultValue) {
        return wrapper != null ? wrapper : defaultValue;
    }
}
