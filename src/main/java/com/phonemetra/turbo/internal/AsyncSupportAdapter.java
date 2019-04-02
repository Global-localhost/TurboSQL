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
 */

package com.phonemetra.turbo.internal;

import com.phonemetra.turbo.TurboSQLCheckedException;
import com.phonemetra.turbo.internal.util.future.TurboSQLFutureImpl;
import com.phonemetra.turbo.lang.TurboSQLAsyncSupport;
import com.phonemetra.turbo.lang.TurboSQLFuture;

/**
 * Adapter for {@link com.phonemetra.turbo.lang.TurboSQLAsyncSupport}.
 */
public class AsyncSupportAdapter<T extends TurboSQLAsyncSupport> implements TurboSQLAsyncSupport {
    /** Future for previous asynchronous operation. */
    protected ThreadLocal<TurboSQLFuture<?>> curFut;

    /**
     * Default constructor.
     */
    public AsyncSupportAdapter() {
        // No-op.
    }

    /**
     * @param async Async enabled flag.
     */
    public AsyncSupportAdapter(boolean async) {
        if (async)
            curFut = new ThreadLocal<>();
    }

    /** {@inheritDoc} */
    @Override public T withAsync() {
        if (isAsync())
            return (T)this;

        return createAsyncInstance();
    }

    /**
     * Creates component with asynchronous mode enabled.
     *
     * @return Component with asynchronous mode enabled.
     */
    protected T createAsyncInstance() {
        throw new UnsupportedOperationException();
    }

    /** {@inheritDoc} */
    @Override public boolean isAsync() {
        return curFut != null;
    }

    /** {@inheritDoc} */
    @Override public <R> TurboSQLFuture<R> future() {
        return future(true);
    }

    /**
     * Gets and optionally resets future for previous asynchronous operation.
     *
     * @param reset Specifies whether to reset future.
     *
     * @return Future for previous asynchronous operation.
     */
    public <R> TurboSQLFuture<R> future(boolean reset) {
        if (curFut == null)
            throw new IllegalStateException("Asynchronous mode is disabled.");

        TurboSQLFuture<?> fut = curFut.get();

        if (fut == null)
            throw new IllegalStateException("Asynchronous operation not started.");

        if (reset)
            curFut.set(null);

        return (TurboSQLFuture<R>)fut;
    }

    /**
     * @param fut Future.
     * @return If async mode is enabled saves future and returns {@code null},
     *         otherwise waits for future and returns result.
     * @throws TurboSQLCheckedException If asynchronous mode is disabled and future failed.
     */
    public <R> R saveOrGet(TurboSQLInternalFuture<R> fut) throws TurboSQLCheckedException {
        if (curFut != null) {
            curFut.set(createFuture(fut));

            return null;
        }
        else
            return fut.get();
    }

    /**
     * @param fut Internal future.
     * @return Public API future.
     */
    protected <R> TurboSQLFuture<R> createFuture(TurboSQLInternalFuture<R> fut) {
        return new TurboSQLFutureImpl<>(fut);
    }
}