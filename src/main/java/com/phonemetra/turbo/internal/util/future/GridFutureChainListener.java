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

package com.phonemetra.turbo.internal.util.future;

import java.util.concurrent.Executor;
import com.phonemetra.turbo.internal.TurboSQLInternalFuture;
import com.phonemetra.turbo.internal.util.lang.GridClosureException;
import com.phonemetra.turbo.lang.TurboSQLClosure;
import com.phonemetra.turbo.lang.TurboSQLInClosure;
import org.jetbrains.annotations.Nullable;

/**
 * Future listener to fill chained future with converted result of the source future.
 */
class GridFutureChainListener<T, R> implements TurboSQLInClosure<TurboSQLInternalFuture<T>> {
    /** */
    private static final long serialVersionUID = 0L;

    /** Target future. */
    private final GridFutureAdapter<R> fut;

    /** Done callback. */
    private final TurboSQLClosure<? super TurboSQLInternalFuture<T>, R> doneCb;

    /** */
    private Executor cbExec;

    /**
     * Constructs chain listener.
     *
     *  @param fut Target future.
     * @param doneCb Done callback.
     * @param cbExec Optional executor to run callback.
     */
    public GridFutureChainListener(
        GridFutureAdapter<R> fut,
        TurboSQLClosure<? super TurboSQLInternalFuture<T>, R> doneCb,
        @Nullable Executor cbExec
    ) {
        this.fut = fut;
        this.doneCb = doneCb;
        this.cbExec = cbExec;
    }

    /** {@inheritDoc} */
    @Override public void apply(final TurboSQLInternalFuture<T> t) {
        if (cbExec != null) {
            cbExec.execute(new Runnable() {
                @Override public void run() {
                    applyCallback(t);
                }
            });
        }
        else
            applyCallback(t);
    }

    /**
     * @param t Target future.
     */
    private void applyCallback(TurboSQLInternalFuture<T> t) {
        try {
            fut.onDone(doneCb.apply(t));
        }
        catch (GridClosureException e) {
            fut.onDone(e.unwrap());
        }
        catch (RuntimeException | Error e) {
            fut.onDone(e);

            throw e;
        }
    }
}