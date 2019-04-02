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

import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import com.phonemetra.turbo.TurboSQLCheckedException;
import com.phonemetra.turbo.lang.TurboSQLClosure;
import com.phonemetra.turbo.lang.TurboSQLInClosure;
import org.jetbrains.annotations.Async;

/**
 * Extension for standard {@link Future} interface. It adds simplified exception handling,
 * functional programming support and ability to listen for future completion via functional
 * callback.
 * @param <R> Type of the result for the future.
 */
public interface TurboSQLInternalFuture<R> {
    /**
     * Synchronously waits for completion of the computation and
     * returns computation result.
     *
     * @return Computation result.
     * @throws TurboSQLInterruptedCheckedException Subclass of {@link TurboSQLCheckedException} thrown if the wait was interrupted.
     * @throws TurboSQLFutureCancelledCheckedException Subclass of {@link TurboSQLCheckedException} throws if computation was cancelled.
     * @throws TurboSQLCheckedException If computation failed.
     */
    public R get() throws TurboSQLCheckedException;

    /**
     * Synchronously waits for completion of the computation for
     * up to the timeout specified and returns computation result.
     * This method is equivalent to calling {@link #get(long, TimeUnit) get(long, TimeUnit.MILLISECONDS)}.
     *
     * @param timeout The maximum time to wait in milliseconds.
     * @return Computation result.
     * @throws TurboSQLInterruptedCheckedException Subclass of {@link TurboSQLCheckedException} thrown if the wait was interrupted.
     * @throws TurboSQLFutureTimeoutCheckedException Subclass of {@link TurboSQLCheckedException} thrown if the wait was timed out.
     * @throws TurboSQLFutureCancelledCheckedException Subclass of {@link TurboSQLCheckedException} throws if computation was cancelled.
     * @throws TurboSQLCheckedException If computation failed.
     */
    public R get(long timeout) throws TurboSQLCheckedException;

    /**
     * Synchronously waits for completion of the computation for
     * up to the timeout specified and returns computation result.
     *
     * @param timeout The maximum time to wait.
     * @param unit The time unit of the {@code timeout} argument.
     * @return Computation result.
     * @throws TurboSQLInterruptedCheckedException Subclass of {@link TurboSQLCheckedException} thrown if the wait was interrupted.
     * @throws TurboSQLFutureTimeoutCheckedException Subclass of {@link TurboSQLCheckedException} thrown if the wait was timed out.
     * @throws TurboSQLFutureCancelledCheckedException Subclass of {@link TurboSQLCheckedException} throws if computation was cancelled.
     * @throws TurboSQLCheckedException If computation failed.
     */
    public R get(long timeout, TimeUnit unit) throws TurboSQLCheckedException;

    /**
     * Synchronously waits for completion of the computation and returns computation result ignoring interrupts.
     *
     * @return Computation result.
     * @throws TurboSQLFutureCancelledCheckedException Subclass of {@link TurboSQLCheckedException} throws if computation
     *     was cancelled.
     * @throws TurboSQLCheckedException If computation failed.
     */
    public R getUninterruptibly() throws TurboSQLCheckedException;

    /**
     * Cancels this future.
     *
     * @return {@code True} if future was canceled (i.e. was not finished prior to this call).
     * @throws TurboSQLCheckedException If cancellation failed.
     */
    public boolean cancel() throws TurboSQLCheckedException;

    /**
     * Checks if computation is done.
     *
     * @return {@code True} if computation is done, {@code false} otherwise.
     */
    public boolean isDone();

    /**
     * Returns {@code true} if this computation was cancelled before it completed normally.
     *
     * @return {@code True} if this computation was cancelled before it completed normally.
     */
    public boolean isCancelled();

    /**
     * Registers listener closure to be asynchronously notified whenever future completes.
     *
     * @param lsnr Listener closure to register. If not provided - this method is no-op.
     */
    @Async.Schedule
    public void listen(TurboSQLInClosure<? super TurboSQLInternalFuture<R>> lsnr);

    /**
     * Make a chained future to convert result of this future (when complete) into a new format.
     * It is guaranteed that done callback will be called only ONCE.
     *
     * @param doneCb Done callback that is applied to this future when it finishes to produce chained future result.
     * @return Chained future that finishes after this future completes and done callback is called.
     */
    @Async.Schedule
    public <T> TurboSQLInternalFuture<T> chain(TurboSQLClosure<? super TurboSQLInternalFuture<R>, T> doneCb);

    /**
     * Make a chained future to convert result of this future (when complete) into a new format.
     * It is guaranteed that done callback will be called only ONCE.
     *
     * @param doneCb Done callback that is applied to this future when it finishes to produce chained future result.
     * @param exec Executor to run callback.
     * @return Chained future that finishes after this future completes and done callback is called.
     */
    @Async.Schedule
    public <T> TurboSQLInternalFuture<T> chain(TurboSQLClosure<? super TurboSQLInternalFuture<R>, T> doneCb, Executor exec);

    /**
     * @return Error value if future has already been completed with error.
     */
    public Throwable error();

    /**
     * @return Result value if future has already been completed normally.
     */
    public R result();
}