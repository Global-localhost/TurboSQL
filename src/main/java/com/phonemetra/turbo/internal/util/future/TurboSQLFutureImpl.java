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
import com.phonemetra.turbo.TurboSQLException;
import com.phonemetra.turbo.internal.TurboSQLInternalFuture;
import com.phonemetra.turbo.internal.util.lang.GridClosureException;
import com.phonemetra.turbo.internal.util.typedef.C1;
import com.phonemetra.turbo.internal.util.typedef.internal.A;
import com.phonemetra.turbo.internal.util.typedef.internal.U;
import com.phonemetra.turbo.lang.TurboSQLClosure;
import com.phonemetra.turbo.lang.TurboSQLFuture;
import com.phonemetra.turbo.lang.TurboSQLInClosure;
import org.jetbrains.annotations.Nullable;

/**
 * Implementation of public API future.
 */
public class TurboSQLFutureImpl<V> implements TurboSQLFuture<V> {
    /** */
    protected final TurboSQLInternalFuture<V> fut;

    /**
     * @param fut Future.
     */
    public TurboSQLFutureImpl(TurboSQLInternalFuture<V> fut) {
        assert fut != null;

        this.fut = fut;
    }

    /**
     * @return Internal future.
     */
    public TurboSQLInternalFuture<V> internalFuture() {
        return fut;
    }

    /** {@inheritDoc} */
    @Override public boolean isCancelled() {
        return fut.isCancelled();
    }

    /** {@inheritDoc} */
    @Override public boolean isDone() {
        return fut.isDone();
    }

    /** {@inheritDoc} */
    @Override public void listen(TurboSQLInClosure<? super TurboSQLFuture<V>> lsnr) {
        A.notNull(lsnr, "lsnr");

        fut.listen(new InternalFutureListener(lsnr));
    }

    /** {@inheritDoc} */
    @Override public void listenAsync(TurboSQLInClosure<? super TurboSQLFuture<V>> lsnr, Executor exec) {
        A.notNull(lsnr, "lsnr");
        A.notNull(exec, "exec");

        fut.listen(new InternalFutureListener(new AsyncFutureListener<>(lsnr, exec)));
    }

    /** {@inheritDoc} */
    @Override public <T> TurboSQLFuture<T> chain(final TurboSQLClosure<? super TurboSQLFuture<V>, T> doneCb) {
        return new TurboSQLFutureImpl<>(chainInternal(doneCb, null));
    }

    /** {@inheritDoc} */
    @Override public <T> TurboSQLFuture<T> chainAsync(final TurboSQLClosure<? super TurboSQLFuture<V>, T> doneCb,
        Executor exec) {
        A.notNull(doneCb, "doneCb");
        A.notNull(exec, "exec");

        return new TurboSQLFutureImpl<>(chainInternal(doneCb, exec));
    }

    /**
     * @param doneCb Done callback.
     * @return Internal future
     */
    protected  <T> TurboSQLInternalFuture<T> chainInternal(final TurboSQLClosure<? super TurboSQLFuture<V>, T> doneCb,
        @Nullable Executor exec) {
        C1<TurboSQLInternalFuture<V>, T> clos = new C1<TurboSQLInternalFuture<V>, T>() {
            @Override public T apply(TurboSQLInternalFuture<V> fut) {
                assert TurboSQLFutureImpl.this.fut == fut;

                try {
                    return doneCb.apply(TurboSQLFutureImpl.this);
                }
                catch (Exception e) {
                    throw new GridClosureException(e);
                }
            }
        };

        if (exec != null)
            return fut.chain(clos, exec);

        return fut.chain(clos);
    }

    /** {@inheritDoc} */
    @Override public boolean cancel() throws TurboSQLException {
        try {
            return fut.cancel();
        }
        catch (TurboSQLCheckedException e) {
            throw convertException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public V get() {
        try {
            return fut.get();
        }
        catch (TurboSQLCheckedException e) {
            throw convertException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public V get(long timeout) {
        try {
            return fut.get(timeout);
        }
        catch (TurboSQLCheckedException e) {
            throw convertException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public V get(long timeout, TimeUnit unit) {
        try {
            return fut.get(timeout, unit);
        }
        catch (TurboSQLCheckedException e) {
            throw convertException(e);
        }
    }

    /**
     * Convert internal exception to public exception.
     *
     * @param e Internal exception.
     * @return Public excpetion.
     */
    protected RuntimeException convertException(TurboSQLCheckedException e) {
        return U.convertException(e);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return "TurboSQLFuture [orig=" + fut + ']';
    }

    /**
     *
     */
    private class InternalFutureListener implements TurboSQLInClosure<TurboSQLInternalFuture<V>> {
        /** */
        private static final long serialVersionUID = 0L;

        /** */
        private final TurboSQLInClosure<? super TurboSQLFuture<V>> lsnr;

        /**
         * @param lsnr Wrapped listener.
         */
        private InternalFutureListener(TurboSQLInClosure<? super TurboSQLFuture<V>> lsnr) {
            assert lsnr != null;

            this.lsnr = lsnr;
        }

        /** {@inheritDoc} */
        @Override public int hashCode() {
            return lsnr.hashCode();
        }

        /** {@inheritDoc} */
        @Override public boolean equals(Object obj) {
            if (obj == null || !obj.getClass().equals(InternalFutureListener.class))
                return false;

            InternalFutureListener lsnr0 = (InternalFutureListener)obj;

            return lsnr.equals(lsnr0.lsnr);
        }

        /** {@inheritDoc} */
        @Override public void apply(TurboSQLInternalFuture<V> fut) {
            assert TurboSQLFutureImpl.this.fut == fut;

            lsnr.apply(TurboSQLFutureImpl.this);
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return lsnr.toString();
        }
    }
}