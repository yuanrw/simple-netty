/*
 * Copyright 2016 The Netty Project
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

import com.simple.netty.common.concurrent.Promise;
import org.slf4j.Logger;

/**
 * Internal utilities to notify {@link Promise}s.
 */
public final class PromiseNotificationUtil {

    private PromiseNotificationUtil() {
    }

    /**
     * Try to mark the {@link Promise} as success and log if {@code logger} is not {@code null} in case this fails.
     */
    public static <V> void trySuccess(Promise<? super V> p, V result, Logger logger) {
        if (!p.trySuccess(result) && logger != null) {
            Throwable err = p.cause();
            if (err == null) {
                logger.warn("Failed to mark a promise as success because it has succeeded already: {}", p);
            } else {
                logger.warn(
                    "Failed to mark a promise as success because it has failed already: {}, unnotified cause:",
                    p, err);
            }
        }
    }

    /**
     * Try to mark the {@link Promise} as failure and log if {@code logger} is not {@code null} in case this fails.
     */
    public static void tryFailure(Promise<?> p, Throwable cause, Logger logger) {
        if (!p.tryFailure(cause) && logger != null) {
            Throwable err = p.cause();
            if (err == null) {
                logger.warn("Failed to mark a promise as failure because it has succeeded already: {}", p, cause);
            } else {
                logger.warn(
                    "Failed to mark a promise as failure because it has failed already: {}, unnotified cause: {}",
                    p, err, cause);
            }
        }
    }

}
