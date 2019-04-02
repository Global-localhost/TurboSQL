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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.phonemetra.turbo.TurboSQLCheckedException;
import com.phonemetra.turbo.TurboSQLException;
import com.phonemetra.turbo.TurboSQLLogger;
import com.phonemetra.turbo.compute.ComputeJobSibling;
import com.phonemetra.turbo.compute.ComputeTask;
import com.phonemetra.turbo.compute.ComputeTaskFuture;
import com.phonemetra.turbo.compute.ComputeTaskSession;
import com.phonemetra.turbo.compute.ComputeTaskSessionAttributeListener;
import com.phonemetra.turbo.compute.ComputeTaskSessionScope;
import com.phonemetra.turbo.internal.util.future.GridFutureAdapter;
import com.phonemetra.turbo.internal.util.future.TurboSQLFinishedFutureImpl;
import com.phonemetra.turbo.internal.util.future.TurboSQLFutureImpl;
import com.phonemetra.turbo.internal.util.tostring.GridToStringExclude;
import com.phonemetra.turbo.internal.util.typedef.internal.S;
import com.phonemetra.turbo.internal.util.typedef.internal.U;
import com.phonemetra.turbo.lang.TurboSQLFuture;
import com.phonemetra.turbo.lang.TurboSQLUuid;
import com.phonemetra.turbo.plugin.security.SecurityPermission;
import org.jetbrains.annotations.Nullable;

/**
 * This class provide implementation for task future.
 * @param <R> Type of the task result returning from {@link ComputeTask#reduce(List)} method.
 */
public class ComputeTaskInternalFuture<R> extends GridFutureAdapter<R> {
    /** */
    private ComputeTaskSession ses;

    /** */
    private GridKernalContext ctx;

    /** */
    @GridToStringExclude
    private ComputeFuture<R> userFut;

    /** */
    private transient TurboSQLLogger log;

    /**
     * @param ses Task session instance.
     * @param ctx Kernal context.
     */
    public ComputeTaskInternalFuture(ComputeTaskSession ses, GridKernalContext ctx) {
        assert ses != null;
        assert ctx != null;

        this.ses = ses;
        this.ctx = ctx;

        userFut = new ComputeFuture<>(this);

        log = ctx.log(ComputeTaskInternalFuture.class);
    }

    /**
     * @param ctx Context.
     * @param taskCls Task class.
     * @param e Error.
     * @return Finished task future.
     */
    public static <R> ComputeTaskInternalFuture<R> finishedFuture(final GridKernalContext ctx,
        final Class<?> taskCls,
        TurboSQLCheckedException e) {
        assert ctx != null;
        assert taskCls != null;
        assert e != null;

        final long time = U.currentTimeMillis();

        final TurboSQLUuid id = TurboSQLUuid.fromUuid(ctx.localNodeId());

        ComputeTaskSession ses = new ComputeTaskSession() {
            @Override public String getTaskName() {
                return taskCls.getName();
            }

            @Override public UUID getTaskNodeId() {
                return ctx.localNodeId();
            }

            @Override public long getStartTime() {
                return time;
            }

            @Override public long getEndTime() {
                return time;
            }

            @Override public TurboSQLUuid getId() {
                return id;
            }

            @Override public ClassLoader getClassLoader() {
                return null;
            }

            @Override public Collection<ComputeJobSibling> getJobSiblings() throws TurboSQLException {
                return Collections.emptyList();
            }

            @Override public Collection<ComputeJobSibling> refreshJobSiblings() throws TurboSQLException {
                return Collections.emptyList();
            }

            @Nullable @Override public ComputeJobSibling getJobSibling(TurboSQLUuid jobId) throws TurboSQLException {
                return null;
            }

            @Override public void setAttribute(Object key, @Nullable Object val) throws TurboSQLException {
            }

            @Nullable @Override public <K, V> V getAttribute(K key) {
                return null;
            }

            @Override public void setAttributes(Map<?, ?> attrs) throws TurboSQLException {
                // No-op.
            }

            @Override public Map<?, ?> getAttributes() {
                return Collections.emptyMap();
            }

            @Override public void addAttributeListener(ComputeTaskSessionAttributeListener lsnr, boolean rewind) {
                // No-op.
            }

            @Override public boolean removeAttributeListener(ComputeTaskSessionAttributeListener lsnr) {
                return false;
            }

            @Override public <K, V> V waitForAttribute(K key, long timeout) throws InterruptedException {
                throw new InterruptedException("Session was closed.");
            }

            @Override public <K, V> boolean waitForAttribute(K key, @Nullable V val, long timeout) throws InterruptedException {
                throw new InterruptedException("Session was closed.");
            }

            @Override public Map<?, ?> waitForAttributes(Collection<?> keys, long timeout) throws InterruptedException {
                throw new InterruptedException("Session was closed.");
            }

            @Override public boolean waitForAttributes(Map<?, ?> attrs, long timeout) throws InterruptedException {
                throw new InterruptedException("Session was closed.");
            }

            @Override public void saveCheckpoint(String key, Object state) {
                throw new TurboSQLException("Session was closed.");
            }

            @Override public void saveCheckpoint(String key,
                Object state,
                ComputeTaskSessionScope scope,
                long timeout)
            {
                throw new TurboSQLException("Session was closed.");
            }

            @Override public void saveCheckpoint(String key,
                Object state,
                ComputeTaskSessionScope scope,
                long timeout,
                boolean overwrite) {
                throw new TurboSQLException("Session was closed.");
            }

            @Nullable @Override public <T> T loadCheckpoint(String key) throws TurboSQLException {
                throw new TurboSQLException("Session was closed.");
            }

            @Override public boolean removeCheckpoint(String key) throws TurboSQLException {
                throw new TurboSQLException("Session was closed.");
            }

            @Override public Collection<UUID> getTopology() {
                return Collections.emptyList();
            }

            @Override public TurboSQLFuture<?> mapFuture() {
                return new TurboSQLFinishedFutureImpl<Object>();
            }
        };

        ComputeTaskInternalFuture<R> fut = new ComputeTaskInternalFuture<>(ses, ctx);

        fut.onDone(e);

        return fut;
    }

    /**
     * @return Future returned by public API.
     */
    public ComputeTaskFuture<R> publicFuture() {
        return userFut;
    }

    /**
     * Gets task timeout.
     *
     * @return Task timeout.
     */
    public ComputeTaskSession getTaskSession() {
        if (ses == null)
            throw new IllegalStateException("Cannot access task session after future has been deserialized.");

        return ses;
    }

    /** {@inheritDoc} */
    @Override public boolean cancel() throws TurboSQLCheckedException {
        ctx.security().authorize(ses.getTaskName(), SecurityPermission.TASK_CANCEL, null);

        if (onCancelled()) {
            ctx.task().onCancelled(ses.getId());

            return true;
        }

        return isCancelled();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(ComputeTaskInternalFuture.class, this, "super", super.toString());
    }

    /** {@inheritDoc} */
    @Override public TurboSQLLogger logger() {
        return log;
    }

    /**
     *
     */
    private static class ComputeFuture<R> extends TurboSQLFutureImpl<R> implements ComputeTaskFuture<R> {
        /**
         * @param fut Future.
         */
        private ComputeFuture(ComputeTaskInternalFuture<R> fut) {
            super(fut);
        }

        /** {@inheritDoc} */
        @Override public ComputeTaskSession getTaskSession() {
            return ((ComputeTaskInternalFuture<R>)fut).getTaskSession();
        }
    }
}