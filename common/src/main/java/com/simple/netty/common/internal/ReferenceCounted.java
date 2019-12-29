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
 */
public interface ReferenceCounted {
    /**
     * 返回对象的引用数，如果是0代表被释放
     */
    int refCnt();

    /**
     * 引用增加1
     */
    ReferenceCounted retain();

    /**
     * 引用增加increment
     */
    ReferenceCounted retain(int increment);

    ReferenceCounted touch();

    ReferenceCounted touch(Object hint);

    /**
     * 引用数减少1，如果数量为0释放
     *
     * @return 引用数减少到0且被释放返回true，否则返回false
     */
    boolean release();

    /**
     * 引用数减少decrement，如果数量为0释放
     *
     * @return 引用数减少到0且被释放返回true，否则返回false
     */
    boolean release(int decrement);
}
