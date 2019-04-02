/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.phonemetra.turbo;

import com.phonemetra.turbo.lang.TurboSQLFuture;
import org.jetbrains.annotations.Nullable;

/**
 * Exception thrown from turboSQL cache API if cache is restarting.
 */
public class TurboSQLCacheRestartingException extends TurboSQLException {
    /** */
    private static final long serialVersionUID = 0L;

    /** */
    private final transient TurboSQLFuture<?> restartFut;

    /**
     * @param cacheName Error message.
     */
    public TurboSQLCacheRestartingException(String cacheName) {
        this(null, cacheName, null);
    }

    /**
     * @param restartFut Restart future.
     * @param cacheName Error message.
     */
    public TurboSQLCacheRestartingException(TurboSQLFuture<?> restartFut, String cacheName) {
        this(restartFut, cacheName, null);
    }

    /**
     * @param restartFut Restart future.
     * @param cacheName Cache name what is restarting.
     * @param cause Optional nested exception (can be {@code null}).
     */
    public TurboSQLCacheRestartingException(
        TurboSQLFuture<?> restartFut,
        String cacheName,
        @Nullable Throwable cause
    ) {
        super("Cache is restarting:" + cacheName + ", you could wait restart completion with restartFuture", cause);

        this.restartFut = restartFut;
    }

    /**
     * @return Future that will be completed when cache is restarted.
     */
    public TurboSQLFuture<?> restartFuture() {
        return restartFut;
    }
}
