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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.ObjectStreamException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import com.phonemetra.turbo.TurboSQLCheckedException;
import com.phonemetra.turbo.TurboSQLCompute;
import com.phonemetra.turbo.TurboSQLDeploymentException;
import com.phonemetra.turbo.TurboSQLException;
import com.phonemetra.turbo.cluster.ClusterGroup;
import com.phonemetra.turbo.compute.ComputeTask;
import com.phonemetra.turbo.compute.ComputeTaskFuture;
import com.phonemetra.turbo.internal.cluster.ClusterGroupAdapter;
import com.phonemetra.turbo.internal.managers.deployment.GridDeployment;
import com.phonemetra.turbo.internal.util.typedef.F;
import com.phonemetra.turbo.internal.util.typedef.internal.A;
import com.phonemetra.turbo.internal.util.typedef.internal.CU;
import com.phonemetra.turbo.internal.util.typedef.internal.U;
import com.phonemetra.turbo.lang.TurboSQLCallable;
import com.phonemetra.turbo.lang.TurboSQLClosure;
import com.phonemetra.turbo.lang.TurboSQLFuture;
import com.phonemetra.turbo.lang.TurboSQLReducer;
import com.phonemetra.turbo.lang.TurboSQLRunnable;
import com.phonemetra.turbo.lang.TurboSQLUuid;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.phonemetra.turbo.internal.GridClosureCallMode.BALANCE;
import static com.phonemetra.turbo.internal.GridClosureCallMode.BROADCAST;
import static com.phonemetra.turbo.internal.processors.task.GridTaskThreadContextKey.TC_NO_FAILOVER;
import static com.phonemetra.turbo.internal.processors.task.GridTaskThreadContextKey.TC_NO_RESULT_CACHE;
import static com.phonemetra.turbo.internal.processors.task.GridTaskThreadContextKey.TC_SUBGRID_PREDICATE;
import static com.phonemetra.turbo.internal.processors.task.GridTaskThreadContextKey.TC_SUBJ_ID;
import static com.phonemetra.turbo.internal.processors.task.GridTaskThreadContextKey.TC_TASK_NAME;
import static com.phonemetra.turbo.internal.processors.task.GridTaskThreadContextKey.TC_TIMEOUT;

/**
 * {@link TurboSQLCompute} implementation.
 */
public class TurboSQLComputeImpl extends AsyncSupportAdapter<TurboSQLCompute>
    implements TurboSQLCompute, Externalizable {
    /** */
    private static final long serialVersionUID = 0L;

    /** */
    private GridKernalContext ctx;

    /** */
    private ClusterGroupAdapter prj;

    /** */
    private UUID subjId;

    /** Custom executor name. */
    private String execName;

    /**
     * Required by {@link Externalizable}.
     */
    public TurboSQLComputeImpl() {
        // No-op.
    }

    /**
     * @param ctx Kernal context.
     * @param prj Projection.
     * @param subjId Subject ID.
     */
    public TurboSQLComputeImpl(GridKernalContext ctx, ClusterGroupAdapter prj, UUID subjId) {
        this(ctx, prj, subjId, false);
    }

    /**
     * @param ctx Kernal context.
     * @param prj Projection.
     * @param subjId Subject ID.
     * @param async Async support flag.
     */
    private TurboSQLComputeImpl(GridKernalContext ctx, ClusterGroupAdapter prj, UUID subjId, boolean async) {
        super(async);

        this.ctx = ctx;
        this.prj = prj;
        this.subjId = subjId;
    }

    /**
     * Constructor.
     *
     * @param ctx Kernal context.
     * @param prj Projection.
     * @param subjId Subject ID.
     * @param async Async support flag.
     * @param execName Custom executor name.
     */
    private TurboSQLComputeImpl(GridKernalContext ctx, ClusterGroupAdapter prj, UUID subjId, boolean async,
        String execName) {
        super(async);

        this.ctx = ctx;
        this.prj = prj;
        this.subjId = subjId;
        this.execName = execName;
    }

    /** {@inheritDoc} */
    @Override protected TurboSQLCompute createAsyncInstance() {
        return new TurboSQLComputeImpl(ctx, prj, subjId, true);
    }

    /** {@inheritDoc} */
    @Override public ClusterGroup clusterGroup() {
        return prj;
    }

    /** {@inheritDoc} */
    @Override public void affinityRun(String cacheName, Object affKey, TurboSQLRunnable job) {
        CU.validateCacheName(cacheName);

        try {
            saveOrGet(affinityRunAsync0(cacheName, affKey, job));
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public TurboSQLFuture<Void> affinityRunAsync(String cacheName, Object affKey,
        TurboSQLRunnable job) throws TurboSQLException {
        CU.validateCacheName(cacheName);

        return (TurboSQLFuture<Void>)createFuture(affinityRunAsync0(cacheName, affKey, job));
    }

    /**
     * Affinity run implementation.
     *
     * @param cacheName Cache name.
     * @param affKey Affinity key.
     * @param job Job.
     * @return Internal future.
     */
    private TurboSQLInternalFuture<?> affinityRunAsync0(String cacheName, Object affKey, TurboSQLRunnable job) {
        A.notNull(affKey, "affKey");
        A.notNull(job, "job");

        guard();

        try {
            // In case cache key is passed instead of affinity key.
            final Object affKey0 = ctx.affinity().affinityKey(cacheName, affKey);
            int partId = ctx.affinity().partition(cacheName, affKey0);

            if (partId < 0)
                throw new TurboSQLCheckedException("Failed map key to partition: [cache=" + cacheName + " key="
                    + affKey + ']');

            return ctx.closure().affinityRun(Collections.singletonList(cacheName), partId, job, prj.nodes(), execName);
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public void affinityRun(@NotNull Collection<String> cacheNames, Object affKey, TurboSQLRunnable job) {
        CU.validateCacheNames(cacheNames);

        try {
            saveOrGet(affinityRunAsync0(cacheNames, affKey, job));
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public TurboSQLFuture<Void> affinityRunAsync(@NotNull Collection<String> cacheNames, Object affKey,
        TurboSQLRunnable job) throws TurboSQLException {
        CU.validateCacheNames(cacheNames);

        return (TurboSQLFuture<Void>)createFuture(affinityRunAsync0(cacheNames, affKey, job));
    }

    /**
     * Affinity run implementation.
     *
     * @param cacheNames Cache names collection.
     * @param affKey Affinity key.
     * @param job Job.
     * @return Internal future.
     */
    private TurboSQLInternalFuture<?> affinityRunAsync0(@NotNull Collection<String> cacheNames, Object affKey,
        TurboSQLRunnable job) {
        A.notNull(affKey, "affKey");
        A.notNull(job, "job");
        A.ensure(!cacheNames.isEmpty(), "cachesNames mustn't be empty");

        guard();

        try {
            final String cacheName = F.first(cacheNames);

            // In case cache key is passed instead of affinity key.
            final Object affKey0 = ctx.affinity().affinityKey(cacheName, affKey);
            int partId = ctx.affinity().partition(cacheName, affKey0);

            if (partId < 0)
                throw new TurboSQLCheckedException("Failed map key to partition: [cache=" + cacheName + " key="
                    + affKey + ']');

            return ctx.closure().affinityRun(cacheNames, partId, job, prj.nodes(), execName);
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public void affinityRun(@NotNull Collection<String> cacheNames, int partId, TurboSQLRunnable job) {
        CU.validateCacheNames(cacheNames);

        try {
            saveOrGet(affinityRunAsync0(cacheNames, partId, job));
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public TurboSQLFuture<Void> affinityRunAsync(@NotNull Collection<String> cacheNames, int partId,
        TurboSQLRunnable job) throws TurboSQLException {
        CU.validateCacheNames(cacheNames);

        return (TurboSQLFuture<Void>)createFuture(affinityRunAsync0(cacheNames, partId, job));
    }

    /**
     * Affinity run implementation.
     *
     * @param cacheNames Cache names collection.
     * @param partId partition ID.
     * @param job Job.
     * @return Internal future.
     */
    private TurboSQLInternalFuture<?> affinityRunAsync0(@NotNull Collection<String> cacheNames, int partId,
        TurboSQLRunnable job) {
        A.ensure(partId >= 0, "partId = " + partId);
        A.notNull(job, "job");
        A.ensure(!cacheNames.isEmpty(), "cachesNames mustn't be empty");

        guard();

        try {
            return ctx.closure().affinityRun(cacheNames, partId, job, prj.nodes(), execName);
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <R> R affinityCall(String cacheName, Object affKey, TurboSQLCallable<R> job) {
        CU.validateCacheName(cacheName);

        try {
            return saveOrGet(affinityCallAsync0(cacheName, affKey, job));
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public <R> TurboSQLFuture<R> affinityCallAsync(String cacheName, Object affKey,
        TurboSQLCallable<R> job) throws TurboSQLException {
        CU.validateCacheName(cacheName);

        return createFuture(affinityCallAsync0(cacheName, affKey, job));
    }

    /**
     * Affinity call implementation.

     * @param cacheName Cache name.
     * @param affKey Affinity key.
     * @param job Job.
     * @return Internal future.
     */
    private <R> TurboSQLInternalFuture<R> affinityCallAsync0(String cacheName, Object affKey,
        TurboSQLCallable<R> job) {
        A.notNull(affKey, "affKey");
        A.notNull(job, "job");

        guard();

        try {
            // In case cache key is passed instead of affinity key.
            final Object affKey0 = ctx.affinity().affinityKey(cacheName, affKey);
            int partId = ctx.affinity().partition(cacheName, affKey0);

            if (partId < 0)
                throw new TurboSQLCheckedException("Failed map key to partition: [cache=" + cacheName + " key="
                    + affKey + ']');

            return ctx.closure().affinityCall(Collections.singletonList(cacheName), partId, job, prj.nodes(), execName);
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <R> R affinityCall(@NotNull Collection<String> cacheNames, Object affKey, TurboSQLCallable<R> job) {
        CU.validateCacheNames(cacheNames);

        try {
            return saveOrGet(affinityCallAsync0(cacheNames, affKey, job));
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public <R> TurboSQLFuture<R> affinityCallAsync(@NotNull Collection<String> cacheNames, Object affKey,
        TurboSQLCallable<R> job) throws TurboSQLException {
        CU.validateCacheNames(cacheNames);

        return createFuture(affinityCallAsync0(cacheNames, affKey, job));
    }

    /**
     * Affinity call implementation.

     * @param cacheNames Cache names collection.
     * @param affKey Affinity key.
     * @param job Job.
     * @return Internal future.
     */
    private <R> TurboSQLInternalFuture<R> affinityCallAsync0(@NotNull Collection<String> cacheNames, Object affKey,
        TurboSQLCallable<R> job) {
        A.notNull(affKey, "affKey");
        A.notNull(job, "job");
        A.ensure(!cacheNames.isEmpty(), "cachesNames mustn't be empty");

        guard();

        try {
            final String cacheName = F.first(cacheNames);

            // In case cache key is passed instead of affinity key.
            final Object affKey0 = ctx.affinity().affinityKey(cacheName, affKey);
            int partId = ctx.affinity().partition(cacheName, affKey0);

            if (partId < 0)
                throw new TurboSQLCheckedException("Failed map key to partition: [cache=" + cacheName + " key="
                    + affKey + ']');

            return ctx.closure().affinityCall(cacheNames, partId, job, prj.nodes(), execName);
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <R> R affinityCall(@NotNull Collection<String> cacheNames, int partId, TurboSQLCallable<R> job) {
        CU.validateCacheNames(cacheNames);

        try {
            return saveOrGet(affinityCallAsync0(cacheNames, partId, job));
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public <R> TurboSQLFuture<R> affinityCallAsync(@NotNull Collection<String> cacheNames, int partId,
        TurboSQLCallable<R> job) throws TurboSQLException {
        CU.validateCacheNames(cacheNames);

        return createFuture(affinityCallAsync0(cacheNames, partId, job));
    }

    /**
     * Affinity call implementation.

     * @param cacheNames Cache names collection.
     * @param partId Partition ID.
     * @param job Job.
     * @return Internal future.
     */
    private <R> TurboSQLInternalFuture<R> affinityCallAsync0(@NotNull Collection<String> cacheNames, int partId,
        TurboSQLCallable<R> job) {
        A.ensure(partId >= 0, "partId = " + partId);
        A.notNull(job, "job");
        A.ensure(!cacheNames.isEmpty(), "cachesNames mustn't be empty");

        guard();

        try {
            return ctx.closure().affinityCall(cacheNames, partId, job, prj.nodes(), execName);
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <T, R> R execute(String taskName, @Nullable T arg) {
        try {
            return (R)saveOrGet(executeAsync0(taskName, arg));
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public <T, R> ComputeTaskFuture<R> executeAsync(String taskName, @Nullable T arg) throws TurboSQLException {
        return (ComputeTaskFuture<R>)createFuture(executeAsync0(taskName, arg));
    }

    /**
     * Execute implementation.
     *
     * @param taskName Task name.
     * @param arg Argument.
     * @return Internal future.
     */
    private <T, R> TurboSQLInternalFuture<R> executeAsync0(String taskName, @Nullable T arg) {
        A.notNull(taskName, "taskName");

        guard();

        try {
            ctx.task().setThreadContextIfNotNull(TC_SUBGRID_PREDICATE, prj.predicate());
            ctx.task().setThreadContextIfNotNull(TC_SUBJ_ID, subjId);

            return ctx.task().execute(taskName, arg, execName);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <T, R> R execute(Class<? extends ComputeTask<T, R>> taskCls, @Nullable T arg) {
        try {
            return (R)saveOrGet(executeAsync0(taskCls, arg));
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public <T, R> ComputeTaskFuture<R> executeAsync(Class<? extends ComputeTask<T, R>> taskCls,
        @Nullable T arg) throws TurboSQLException {
        return (ComputeTaskFuture<R>)createFuture(executeAsync0(taskCls, arg));
    }

    /**
     * Execute implementation.
     *
     * @param taskCls Task class.
     * @param arg Argument.
     * @return Internal future.
     */
    private <T, R> TurboSQLInternalFuture<R> executeAsync0(Class<? extends ComputeTask<T, R>> taskCls, @Nullable T arg) {
        A.notNull(taskCls, "taskCls");

        guard();

        try {
            ctx.task().setThreadContextIfNotNull(TC_SUBGRID_PREDICATE, prj.predicate());
            ctx.task().setThreadContextIfNotNull(TC_SUBJ_ID, subjId);

            return ctx.task().execute(taskCls, arg, execName);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <T, R> R execute(ComputeTask<T, R> task, @Nullable T arg) {
        try {
            return (R)saveOrGet(executeAsync0(task, arg));
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public <T, R> ComputeTaskFuture<R> executeAsync(ComputeTask<T, R> task, @Nullable T arg)
        throws TurboSQLException {
        return (ComputeTaskFuture<R>)createFuture(executeAsync0(task, arg));
    }

    /**
     * Execute implementation.
     *
     * @param task Task.
     * @param arg Task argument.
     * @return Task future.
     */
    public <T, R> ComputeTaskInternalFuture<R> executeAsync0(ComputeTask<T, R> task, @Nullable T arg) {
        A.notNull(task, "task");

        guard();

        try {
            ctx.task().setThreadContextIfNotNull(TC_SUBGRID_PREDICATE, prj.predicate());
            ctx.task().setThreadContextIfNotNull(TC_SUBJ_ID, subjId);

            return ctx.task().execute(task, arg, execName);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public void broadcast(TurboSQLRunnable job) {
        try {
            saveOrGet(broadcastAsync0(job));
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public TurboSQLFuture<Void> broadcastAsync(TurboSQLRunnable job) throws TurboSQLException {
        return (TurboSQLFuture<Void>)createFuture(broadcastAsync0(job));
    }

    /**
     * Broadcast implementation.
     *
     * @param job Job.
     * @return Internal future.
     */
    private TurboSQLInternalFuture<?> broadcastAsync0(TurboSQLRunnable job) {
        A.notNull(job, "job");

        guard();

        try {
            return ctx.closure().runAsync(BROADCAST, job, prj.nodes(), execName);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <R> Collection<R> broadcast(TurboSQLCallable<R> job) {
        try {
            return saveOrGet(broadcastAsync0(job));
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public <R> TurboSQLFuture<Collection<R>> broadcastAsync(TurboSQLCallable<R> job) throws TurboSQLException {
        return createFuture(broadcastAsync0(job));
    }

    /**
     * Broadcast implementation.
     *
     * @param job Job.
     * @return Internal future.
     */
    private <R> TurboSQLInternalFuture<Collection<R>> broadcastAsync0(TurboSQLCallable<R> job) {
        A.notNull(job, "job");

        guard();

        try {
            return ctx.closure().callAsync(BROADCAST, Collections.singletonList(job), prj.nodes(), execName);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <R, T> Collection<R> broadcast(TurboSQLClosure<T, R> job, @Nullable T arg) {
        try {
            return saveOrGet(broadcastAsync0(job, arg));
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public <R, T> TurboSQLFuture<Collection<R>> broadcastAsync(TurboSQLClosure<T, R> job,
        @Nullable T arg) throws TurboSQLException {
        return createFuture(broadcastAsync0(job, arg));
    }

    /**
     * Broadcast implementation.
     *
     * @param job Job.
     * @param arg Argument.
     * @return Internal future.
     */
    private <R, T> TurboSQLInternalFuture<Collection<R>> broadcastAsync0(TurboSQLClosure<T, R> job, @Nullable T arg) {
        A.notNull(job, "job");

        guard();

        try {
            return ctx.closure().broadcast(job, arg, prj.nodes(), execName);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public void run(TurboSQLRunnable job) {
        try {
            saveOrGet(runAsync0(job));
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public TurboSQLFuture<Void> runAsync(TurboSQLRunnable job) throws TurboSQLException {
        return (TurboSQLFuture<Void>)createFuture(runAsync0(job));
    }

    /**
     * Run implementation.
     *
     * @param job Job.
     * @return Internal future.
     */
    private TurboSQLInternalFuture<?> runAsync0(TurboSQLRunnable job) {
        A.notNull(job, "job");

        guard();

        try {
            return ctx.closure().runAsync(BALANCE, job, prj.nodes(), execName);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public void run(Collection<? extends TurboSQLRunnable> jobs) {
        try {
            saveOrGet(runAsync0(jobs));
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public TurboSQLFuture<Void> runAsync(Collection<? extends TurboSQLRunnable> jobs)
        throws TurboSQLException {
        return (TurboSQLFuture<Void>)createFuture(runAsync0(jobs));
    }

    /**
     * Run implementation.
     *
     * @param jobs Jobs.
     * @return Internal future.
     */
    private TurboSQLInternalFuture<?> runAsync0(Collection<? extends TurboSQLRunnable> jobs) {
        A.notEmpty(jobs, "jobs");

        guard();

        try {
            return ctx.closure().runAsync(BALANCE, jobs, prj.nodes(), execName);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <R, T> R apply(TurboSQLClosure<T, R> job, @Nullable T arg) {
        try {
            return saveOrGet(applyAsync0(job, arg));
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public <R, T> TurboSQLFuture<R> applyAsync(TurboSQLClosure<T, R> job, @Nullable T arg)
        throws TurboSQLException {
        return (TurboSQLFuture<R>)createFuture(applyAsync0(job, arg));
    }

    /**
     * Apply implementation.
     *
     * @param job Job.
     * @param arg Argument.
     * @return Internal future.
     */
    private <R, T> TurboSQLInternalFuture<R> applyAsync0(TurboSQLClosure<T, R> job, @Nullable T arg) {
        A.notNull(job, "job");

        guard();

        try {
            return ctx.closure().callAsync(job, arg, prj.nodes(), execName);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <R> R call(TurboSQLCallable<R> job) {
        try {
            return saveOrGet(callAsync0(job));
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public <R> TurboSQLFuture<R> callAsync(TurboSQLCallable<R> job) throws TurboSQLException {
        return (TurboSQLFuture<R>)createFuture(callAsync0(job));
    }

    /**
     * Call implementation.
     *
     * @param job Job.
     * @return Internal future.
     */
    private <R> TurboSQLInternalFuture<R> callAsync0(TurboSQLCallable<R> job) {
        A.notNull(job, "job");

        guard();

        try {
            return ctx.closure().callAsync(BALANCE, job, prj.nodes(), execName);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <R> Collection<R> call(Collection<? extends TurboSQLCallable<R>> jobs) {
        try {
            return saveOrGet(callAsync0(jobs));
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public <R> TurboSQLFuture<Collection<R>> callAsync(
        Collection<? extends TurboSQLCallable<R>> jobs) throws TurboSQLException {
        return (TurboSQLFuture<Collection<R>>)createFuture(callAsync0(jobs));
    }

    /**
     * Call implementation.
     *
     * @param jobs Jobs.
     * @return Internal future.
     */
    private <R> TurboSQLInternalFuture<Collection<R>> callAsync0(Collection<? extends TurboSQLCallable<R>> jobs) {
        A.notEmpty(jobs, "jobs");

        guard();

        try {
            return ctx.closure().callAsync(BALANCE, (Collection<? extends Callable<R>>)jobs, prj.nodes(), execName);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <T, R> Collection<R> apply(final TurboSQLClosure<T, R> job, @Nullable Collection<? extends T> args) {
        try {
            return saveOrGet(applyAsync0(job, args));
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public <T, R> TurboSQLFuture<Collection<R>> applyAsync(TurboSQLClosure<T, R> job,
        Collection<? extends T> args) throws TurboSQLException {
        return (TurboSQLFuture<Collection<R>>)createFuture(applyAsync0(job, args));
    }

    /**
     * Apply implementation.
     *
     * @param job Job.
     * @param args Arguments/
     * @return Internal future.
     */
    private <T, R> TurboSQLInternalFuture<Collection<R>> applyAsync0(final TurboSQLClosure<T, R> job,
        @Nullable Collection<? extends T> args) {
        A.notNull(job, "job");
        A.notNull(args, "args");

        guard();

        try {
            return ctx.closure().callAsync(job, args, prj.nodes(), execName);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <R1, R2> R2 call(Collection<? extends TurboSQLCallable<R1>> jobs, TurboSQLReducer<R1, R2> rdc) {
        try {
            return saveOrGet(callAsync0(jobs, rdc));
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public <R1, R2> TurboSQLFuture<R2> callAsync(Collection<? extends TurboSQLCallable<R1>> jobs,
        TurboSQLReducer<R1, R2> rdc) throws TurboSQLException {
        return (TurboSQLFuture<R2>)createFuture(callAsync0(jobs, rdc));
    }

    /**
     * Call with reducer implementation.
     *
     * @param jobs Jobs.
     * @param rdc Reducer.
     * @return Internal future.
     */
    private <R1, R2> TurboSQLInternalFuture<R2> callAsync0(Collection<? extends TurboSQLCallable<R1>> jobs,
        TurboSQLReducer<R1, R2> rdc) {
        A.notEmpty(jobs, "jobs");
        A.notNull(rdc, "rdc");

        guard();

        try {
            return ctx.closure().forkjoinAsync(BALANCE, jobs, rdc, prj.nodes(), execName);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <R1, R2, T> R2 apply(TurboSQLClosure<T, R1> job, Collection<? extends T> args,
        TurboSQLReducer<R1, R2> rdc) {
        try {
            return saveOrGet(applyAsync0(job, args, rdc));
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public <R1, R2, T> TurboSQLFuture<R2> applyAsync(TurboSQLClosure<T, R1> job,
        Collection<? extends T> args, TurboSQLReducer<R1, R2> rdc) throws TurboSQLException {
        return createFuture(applyAsync0(job, args, rdc));
    }

    /**
     * Apply with reducer implementation.
     *
     * @param job Job
     * @param args Arguments.
     * @param rdc Reducer.
     * @return Internal future.
     */
    private <R1, R2, T> TurboSQLInternalFuture<R2> applyAsync0(TurboSQLClosure<T, R1> job, Collection<? extends T> args,
        TurboSQLReducer<R1, R2> rdc) {
        A.notNull(job, "job");
        A.notNull(rdc, "rdc");
        A.notNull(args, "args");

        guard();

        try {
            return ctx.closure().callAsync(job, args, rdc, prj.nodes(), execName);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <R> Map<TurboSQLUuid, ComputeTaskFuture<R>> activeTaskFutures() {
        guard();

        try {
            return ctx.task().taskFutures();
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public TurboSQLCompute withName(String taskName) {
        A.notNull(taskName, "taskName");

        guard();

        try {
            ctx.task().setThreadContext(TC_TASK_NAME, taskName);
        }
        finally {
            unguard();
        }

        return this;
    }

    /** {@inheritDoc} */
    @Override public TurboSQLCompute withTimeout(long timeout) {
        A.ensure(timeout >= 0, "timeout >= 0");

        guard();

        try {
            ctx.task().setThreadContext(TC_TIMEOUT, timeout);
        }
        finally {
            unguard();
        }

        return this;
    }

    /** {@inheritDoc} */
    @Override public TurboSQLCompute withNoFailover() {
        guard();

        try {
            ctx.task().setThreadContext(TC_NO_FAILOVER, true);
        }
        finally {
            unguard();
        }

        return this;
    }

    /** {@inheritDoc} */
    @Override public TurboSQLCompute withNoResultCache() {
        guard();

        try {
            ctx.task().setThreadContext(TC_NO_RESULT_CACHE, true);
        }
        finally {
            unguard();
        }

        return this;
    }

    /** {@inheritDoc} */
    @Override public void localDeployTask(Class<? extends ComputeTask> taskCls, ClassLoader clsLdr) {
        A.notNull(taskCls, "taskCls", clsLdr, "clsLdr");

        guard();

        try {
            GridDeployment dep = ctx.deploy().deploy(taskCls, clsLdr);

            if (dep == null)
                throw new TurboSQLDeploymentException("Failed to deploy task (was task (re|un)deployed?): " + taskCls);
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public Map<String, Class<? extends ComputeTask<?, ?>>> localTasks() {
        guard();

        try {
            return ctx.deploy().findAllTasks();
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public void undeployTask(String taskName) {
        A.notNull(taskName, "taskName");

        guard();

        try {
            ctx.deploy().undeployTask(taskName, prj.node(ctx.localNodeId()) != null,
                prj.forRemotes().nodes());
        }
        finally {
            unguard();
        }
    }

    /**
     * <tt>ctx.gateway().readLock()</tt>
     */
    private void guard() {
        ctx.gateway().readLock();
    }

    /**
     * <tt>ctx.gateway().readUnlock()</tt>
     */
    private void unguard() {
        ctx.gateway().readUnlock();
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(prj);
        out.writeObject(execName);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        prj = (ClusterGroupAdapter)in.readObject();
        execName = (String)in.readObject();
    }

    /**
     * Reconstructs object on unmarshalling.
     *
     * @return Reconstructed object.
     * @throws ObjectStreamException Thrown in case of unmarshalling error.
     */
    protected Object readResolve() throws ObjectStreamException {
        return execName == null ? prj.compute() : prj.compute().withExecutor(execName);
    }

    /** {@inheritDoc} */
    @Override protected <R> TurboSQLFuture<R> createFuture(TurboSQLInternalFuture<R> fut) {
        assert fut instanceof ComputeTaskInternalFuture : fut;

        return ((ComputeTaskInternalFuture<R>)fut).publicFuture();
    }

    /** {@inheritDoc} */
    @Override public <R> ComputeTaskFuture<R> future() {
        return (ComputeTaskFuture<R>)super.future();
    }

    /** {@inheritDoc} */
    @Override public TurboSQLCompute withExecutor(@NotNull String name) {
        return new TurboSQLComputeImpl(ctx, prj, subjId, isAsync(), name);
    }
}