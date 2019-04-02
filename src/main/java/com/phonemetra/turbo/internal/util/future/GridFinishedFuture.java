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
import java.util.concurrent.TimeUnit;
import com.phonemetra.turbo.TurboSQLCheckedException;
import com.phonemetra.turbo.internal.TurboSQLInternalFuture;
import com.phonemetra.turbo.internal.util.lang.GridClosureException;
import com.phonemetra.turbo.internal.util.typedef.internal.S;
import com.phonemetra.turbo.internal.util.typedef.internal.U;
import com.phonemetra.turbo.lang.TurboSQLClosure;
import com.phonemetra.turbo.lang.TurboSQLInClosure;

/**
 * Future that is completed at creation time.
 */
public class GridFinishedFuture<T> implements TurboSQLInternalFuture<T> {
    /** */
    private static final byte ERR = 1;

    /** */
    private static final byte RES = 2;

    /** */
    private final byte resFlag;

    /** Complete value. */
    private final Object res;

    /**
     * Creates finished future with complete value.
     */
    public GridFinishedFuture() {
        res = null;
        resFlag = RES;
    }

    /**
     * Creates finished future with complete value.
     *
     * @param t Finished value.
     */
    public GridFinishedFuture(T t) {
        res = t;
        resFlag = RES;
    }

    /**
     * @param err Future error.
     */
    public GridFinishedFuture(Throwable err) {
        res = err;
        resFlag = ERR;
    }

    /** {@inheritDoc} */
    @Override public Throwable error() {
        return (resFlag == ERR) ? (Throwable)res : null;
    }

    /** {@inheritDoc} */
    @Override public T result() {
        return resFlag == RES ? (T)res : null;
    }

    /** {@inheritDoc} */
    @Override public boolean cancel() {
        return false;
    }

    /** {@inheritDoc} */
    @Override public boolean isCancelled() {
        return false;
    }

    /** {@inheritDoc} */
    @Override public boolean isDone() {
        return true;
    }

    /** {@inheritDoc} */
    @Override public T get() throws TurboSQLCheckedException {
        if (resFlag == ERR)
            throw U.cast((Throwable)res);

        return (T)res;
    }

    /** {@inheritDoc} */
    @Override public T get(long timeout) throws TurboSQLCheckedException {
        return get();
    }

    /** {@inheritDoc} */
    @Override public T get(long timeout, TimeUnit unit) throws TurboSQLCheckedException {
        return get();
    }

    /** {@inheritDoc} */
    @Override public T getUninterruptibly() throws TurboSQLCheckedException {
        return get();
    }

    /** {@inheritDoc} */
    @Override public void listen(TurboSQLInClosure<? super TurboSQLInternalFuture<T>> lsnr) {
        assert lsnr != null;

        lsnr.apply(this);
    }

    /** {@inheritDoc} */
    @Override public <R> TurboSQLInternalFuture<R> chain(final TurboSQLClosure<? super TurboSQLInternalFuture<T>, R> doneCb) {
        try {
            return new GridFinishedFuture<>(doneCb.apply(this));
        }
        catch (GridClosureException e) {
            return new GridFinishedFuture<>(e.unwrap());
        }
        catch (RuntimeException | Error e) {
            return new GridFinishedFuture<>(e);
        }
    }

    /** {@inheritDoc} */
    @Override public <T1> TurboSQLInternalFuture<T1> chain(final TurboSQLClosure<? super TurboSQLInternalFuture<T>, T1> doneCb, Executor exec) {
        final GridFutureAdapter<T1> fut = new GridFutureAdapter<>();

        exec.execute(new Runnable() {
            @Override public void run() {
                try {
                    fut.onDone(doneCb.apply(GridFinishedFuture.this));
                }
                catch (GridClosureException e) {
                    fut.onDone(e.unwrap());
                }
                catch (RuntimeException | Error e) {
                    fut.onDone(e);

                    throw e;
                }
            }
        });

        return fut;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridFinishedFuture.class, this);
    }
}