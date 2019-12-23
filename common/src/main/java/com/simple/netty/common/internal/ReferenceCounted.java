/*
 * Copyright 2013 The Netty Project
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

/**
 * 初始化时，引用从1开始计算。{@link #retain()}增加计数，{@link #release()} 减少计数。
 * * 一旦计数等于0，对象会被释放。
 * <p>
 * A reference-counted object that requires explicit deallocation.
 * <p>
 * When a new {@link ReferenceCounted} is instantiated, it starts with the reference count of {@code 1}.
 * {@link #retain()} increases the reference count, and {@link #release()} decreases the reference count.
 * If the reference count is decreased to {@code 0}, the object will be deallocated explicitly, and accessing
 * the deallocated object will usually result in an access violation.
 * </p>
 * <p>
 * If an object that implements {@link ReferenceCounted} is a container of other objects that implement
 * {@link ReferenceCounted}, the contained objects will also be released via {@link #release()} when the container's
 * reference count becomes 0.
 * </p>
 * Date: 2019-12-14
 * Time: 12:40
 *
 * @author yrw
 */
public interface ReferenceCounted {

    /**
     * 返回对象的引用数。如果等于0，说明已被释放
     */
    int refCnt();

    /**
     * 引用加1
     *
     * @return
     */
    ReferenceCounted retain();

    /**
     * 引用加increment
     *
     * @param increment 数量
     * @return
     */
    ReferenceCounted retain(int increment);

    /**
     * 引用数减1
     *
     * @return 如果引用数等于0并且已经被释放返回会true
     */
    boolean release();

    /**
     * 引用数减decrement
     *
     * @param decrement 数量
     * @return 如果引用数等于0并且已经被释放返回会true
     */
    boolean release(int decrement);
}
