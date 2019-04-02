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

import com.phonemetra.turbo.TurboSQLCheckedException;
import com.phonemetra.turbo.TurboSQLLogger;
import com.phonemetra.turbo.internal.TurboSQLFutureCancelledCheckedException;
import com.phonemetra.turbo.internal.TurboSQLInternalFuture;
import com.phonemetra.turbo.internal.NodeStoppingException;
import com.phonemetra.turbo.internal.cluster.ClusterTopologyCheckedException;
import com.phonemetra.turbo.internal.transactions.TurboSQLTxOptimisticCheckedException;
import com.phonemetra.turbo.internal.util.tostring.GridToStringInclude;
import com.phonemetra.turbo.internal.util.typedef.C1;
import com.phonemetra.turbo.internal.util.typedef.F;
import com.phonemetra.turbo.internal.util.typedef.internal.S;
import com.phonemetra.turbo.internal.util.typedef.internal.U;
import com.phonemetra.turbo.lang.TurboSQLInClosure;
import com.phonemetra.turbo.lang.TurboSQLReducer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * Future composed of multiple inner futures.
 */
public class GridCompoundFuture<T, R> extends GridFutureAdapter<R> implements TurboSQLInClosure<TurboSQLInternalFuture<T>> {
    /** */
    private static final long serialVersionUID = 0L;

    /** Initialization flag. */
    private static final int INIT_FLAG = 0x1;

    /** Flags updater. */
    private static final AtomicIntegerFieldUpdater<GridCompoundFuture> FLAGS_UPD =
        AtomicIntegerFieldUpdater.newUpdater(GridCompoundFuture.class, "initFlag");

    /** Listener calls updater. */
    private static final AtomicIntegerFieldUpdater<GridCompoundFuture> LSNR_CALLS_UPD =
        AtomicIntegerFieldUpdater.newUpdater(GridCompoundFuture.class, "lsnrCalls");

    /** Possible values: null (no future), TurboSQLInternalFuture instance (single future) or List of futures  */
    private volatile Object futs;

    /** Reducer. */
    @GridToStringInclude
    private final TurboSQLReducer<T, R> rdc;

    /** Initialization flag. Updated via {@link #FLAGS_UPD}. */
    @SuppressWarnings("unused")
    private volatile int initFlag;

    /** Listener calls. Updated via {@link #LSNR_CALLS_UPD}. */
    @SuppressWarnings("unused")
    private volatile int lsnrCalls;

    /**
     * Default constructor.
     */
    public GridCompoundFuture() {
        this(null);
    }

    /**
     * @param rdc Reducer.
     */
    public GridCompoundFuture(@Nullable TurboSQLReducer<T, R> rdc) {
        this.rdc = rdc;
    }

    /** {@inheritDoc} */
    @Override public final void apply(TurboSQLInternalFuture<T> fut) {
        try {
            T t = fut.get();

            try {
                if (rdc != null && !rdc.collect(t))
                    onDone(rdc.reduce());
            }
            catch (RuntimeException e) {
                logError(null, "Failed to execute compound future reducer: " + this, e);

                // Exception in reducer is a bug, so we bypass checkComplete here.
                onDone(e);
            }
            catch (AssertionError e) {
                logError(null, "Failed to execute compound future reducer: " + this, e);

                // Bypass checkComplete because need to rethrow.
                onDone(e);

                throw e;
            }
        }
        catch (TurboSQLTxOptimisticCheckedException | TurboSQLFutureCancelledCheckedException |
            ClusterTopologyCheckedException e) {
            if (!processFailure(e, fut))
                onDone(e);
        }
        catch (TurboSQLCheckedException e) {
            if (!processFailure(e, fut)) {
                if (e instanceof NodeStoppingException)
                    logDebug(logger(), "Failed to execute compound future reducer, node stopped.");
                else
                    logError(null, "Failed to execute compound future reducer: " + this, e);

                onDone(e);
            }
        }
        catch (RuntimeException e) {
            logError(null, "Failed to execute compound future reducer: " + this, e);

            onDone(e);
        }
        catch (AssertionError e) {
            logError(null, "Failed to execute compound future reducer: " + this, e);

            // Bypass checkComplete because need to rethrow.
            onDone(e);

            throw e;
        }

        LSNR_CALLS_UPD.incrementAndGet(this);

        checkComplete();
    }

    /** {@inheritDoc} */
    @Override public boolean cancel() throws TurboSQLCheckedException {
        if (onCancelled()) {
            for (TurboSQLInternalFuture<T> fut : futures())
                fut.cancel();

            return true;
        }

        return false;
    }

    /**
     * Gets collection of futures.
     *
     * @return Collection of futures.
     */
    public final synchronized Collection<TurboSQLInternalFuture<T>> futures() {
        if (futs == null)
            return Collections.emptyList();

        if (futs instanceof TurboSQLInternalFuture)
            return Collections.singletonList((TurboSQLInternalFuture<T>)futs);

        return new ArrayList<>((Collection<TurboSQLInternalFuture<T>>)futs);
    }

    /**
     * Checks if this compound future should ignore this particular exception.
     *
     * @param err Exception to check.
     * @return {@code True} if this error should be ignored.
     */
    protected boolean ignoreFailure(Throwable err) {
        return false;
    }

    /**
     * Processes error thrown by some of the inner futures.
     *
     * @param err Thrown exception.
     * @param fut Failed future.
     * @return {@code True} if this error should be ignored.
     */
    protected boolean processFailure(Throwable err, TurboSQLInternalFuture<T> fut) {
        return ignoreFailure(err);
    }

    /**
     * Checks if there are pending futures. This is not the same as
     * {@link #isDone()} because child classes may override {@link #onDone(Object, Throwable)}
     * call and delay completion.
     *
     * @return {@code True} if there are pending futures.
     */
    protected final boolean hasPending() {
        synchronized (this) {
            int size = futuresCountNoLock();

            // Avoid iterator creation and collection copy.
            for (int i = 0; i < size; i++) {
                TurboSQLInternalFuture<T> fut = future(i);

                if (!fut.isDone())
                    return true;
            }
        }

        return false;
    }

    /**
     * Adds a future to this compound future.
     *
     * @param fut Future to add.
     */
    public final void add(TurboSQLInternalFuture<T> fut) {
        assert fut != null;

        synchronized (this) {
            if (futs == null)
                futs = fut;
            else if (futs instanceof TurboSQLInternalFuture) {
                Collection<TurboSQLInternalFuture> futs0 = new ArrayList<>(4);

                futs0.add((TurboSQLInternalFuture)futs);
                futs0.add(fut);

                futs = futs0;
            }
            else
                ((Collection<TurboSQLInternalFuture>)futs).add(fut);
        }

        fut.listen(this);

        if (isCancelled()) {
            try {
                fut.cancel();
            }
            catch (TurboSQLCheckedException e) {
                onDone(e);
            }
        }
    }

    /**
     * Clear futures.
     */
    protected final synchronized void clear() {
        futs = null;
    }

    /**
     * @return {@code True} if this future was initialized. Initialization happens when {@link #markInitialized()}
     * method is called on future.
     */
    public final boolean initialized() {
        return initFlag == INIT_FLAG;
    }

    /**
     * Mark this future as initialized.
     */
    public final void markInitialized() {
        if (FLAGS_UPD.compareAndSet(this, 0, INIT_FLAG))
            checkComplete();
    }

    /**
     * Check completeness of the future.
     */
    private void checkComplete() {
        if (initialized() && !isDone() && lsnrCalls == futuresCount()) {
            try {
                onDone(rdc != null ? rdc.reduce() : null);
            }
            catch (RuntimeException e) {
                logError(logger(), "Failed to execute compound future reducer: " + this, e);

                onDone(e);
            }
            catch (AssertionError e) {
                logError(logger(), "Failed to execute compound future reducer: " + this, e);

                onDone(e);

                throw e;
            }
        }
    }

    /**
     * @param log TurboSQLLogger.
     * @param msg ShortMessage.
     * @param e Exception.
     */
    protected void logError(TurboSQLLogger log, String msg, Throwable e) {
        U.error(log, msg, e);
    }

    /**
     * @param log TurboSQLLogger.
     * @param msg ShortMessage.
     */
    protected void logDebug(TurboSQLLogger log, String msg) {
        if (log != null && log.isDebugEnabled())
            log.debug(msg);
    }

    /**
     * Returns future at the specified position in this list.
     *
     * @param idx - index index of the element to return
     * @return Future.
     */
    @SuppressWarnings("unchecked")
    protected final TurboSQLInternalFuture<T> future(int idx) {
        assert Thread.holdsLock(this);
        assert futs != null && idx >= 0 && idx < futuresCountNoLock();

        if (futs instanceof TurboSQLInternalFuture) {
            assert idx == 0;

            return (TurboSQLInternalFuture<T>)futs;
        }
        else
            return ((List<TurboSQLInternalFuture>)futs).get(idx);
    }

    /**
     * @return Futures size.
     */
    protected final int futuresCountNoLock() {
        assert Thread.holdsLock(this);

        if (futs == null)
            return 0;

        if (futs instanceof TurboSQLInternalFuture)
            return 1;

        return ((Collection<TurboSQLInternalFuture>)futs).size();
    }

    /**
     * @return Futures size.
     */
    private synchronized int futuresCount() {
        return futuresCountNoLock();
    }

    /**
     * @return {@code True} if has at least one future.
     */
    protected final synchronized boolean hasFutures() {
        return futs != null;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCompoundFuture.class, this,
            "done", isDone(),
            "cancelled", isCancelled(),
            "err", error(),
            "futs",
            F.viewReadOnly(futures(), new C1<TurboSQLInternalFuture<T>, String>() {
                @Override public String apply(TurboSQLInternalFuture<T> f) {
                    return Boolean.toString(f.isDone());
                }
            })
        );
    }
}
