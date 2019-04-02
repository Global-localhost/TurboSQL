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

package com.phonemetra.turbo;

import java.util.Collection;
import java.util.Map;
import com.phonemetra.turbo.cluster.ClusterGroup;
import com.phonemetra.turbo.compute.ComputeTask;
import com.phonemetra.turbo.compute.ComputeTaskFuture;
import com.phonemetra.turbo.compute.ComputeTaskName;
import com.phonemetra.turbo.compute.ComputeTaskSpis;
import com.phonemetra.turbo.configuration.ExecutorConfiguration;
import com.phonemetra.turbo.configuration.TurboSQLConfiguration;
import com.phonemetra.turbo.lang.TurboSQLAsyncSupport;
import com.phonemetra.turbo.lang.TurboSQLAsyncSupported;
import com.phonemetra.turbo.lang.TurboSQLCallable;
import com.phonemetra.turbo.lang.TurboSQLClosure;
import com.phonemetra.turbo.lang.TurboSQLFuture;
import com.phonemetra.turbo.lang.TurboSQLReducer;
import com.phonemetra.turbo.lang.TurboSQLRunnable;
import com.phonemetra.turbo.lang.TurboSQLUuid;
import com.phonemetra.turbo.resources.TurboSQLInstanceResource;
import com.phonemetra.turbo.resources.LoggerResource;
import com.phonemetra.turbo.resources.SpringApplicationContextResource;
import com.phonemetra.turbo.resources.SpringResource;
import com.phonemetra.turbo.resources.TaskSessionResource;
import com.phonemetra.turbo.spi.failover.FailoverSpi;
import com.phonemetra.turbo.spi.loadbalancing.LoadBalancingSpi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Defines compute grid functionality for executing tasks and closures over nodes
 * in the {@link ClusterGroup}. Instance of {@code TurboSQLCompute} is obtained from {@link TurboSQL}
 * as follows:
 * <pre name="code" class="java">
 * TurboSQL turboSQL = Ignition.turboSQL();
 *
 * // Compute over all nodes in the cluster.
 * TurboSQLCompute c = turboSQL.compute();
 * </pre>
 * You can also get an instance of {@link TurboSQLCompute} over a subset of cluster nodes, i.e. over
 * a {@link ClusterGroup}:
 * <pre name="code" class="java">
 * // Cluster group composed of all remote nodes.
 * ClusterGroup rmtGrp = turboSQL.cluster().forRemotes();
 *
 * // Compute over remote nodes only.
 * TurboSQLCompute c = turboSQL.compute(rmtGrp);
 * </pre>
 * The methods are grouped as follows:
 * <ul>
 * <li>{@code apply(...)} methods execute {@link TurboSQLClosure} jobs over nodes in the cluster group.</li>
 * <li>{@code call(...)} methods execute {@link TurboSQLCallable} jobs over nodes in the cluster group.</li>
 * <li>{@code run(...)} methods execute {@link TurboSQLRunnable} jobs over nodes in the cluster group.</li>
 * <li>{@code broadcast(...)} methods broadcast jobs to all nodes in the cluster group.</li>
 * <li>{@code affinityCall(...)} and {@code affinityRun(...)} methods collocate jobs with nodes
 *  on which a specified key is cached.</li>
 * </ul>
 * Note that if attempt is made to execute a computation over an empty cluster group (i.e. cluster group
 * that does not have any alive nodes), then {@link com.phonemetra.turbo.cluster.ClusterGroupEmptyException}
 * will be thrown out of result future.
 * <h1 class="header">Load Balancing</h1>
 * In all cases other than {@code broadcast(...)}, TurboSQL must select a node for a computation
 * to be executed. The node will be selected based on the underlying {@link LoadBalancingSpi},
 * which by default sequentially picks next available node from the underlying cluster group. Other
 * load balancing policies, such as {@code random} or {@code adaptive}, can be configured as well by
 * selecting a different load balancing SPI in TurboSQL configuration. If your logic requires some custom
 * load balancing behavior, consider implementing {@link ComputeTask} directly.
 * <h1 class="header">Fault Tolerance</h1>
 * TurboSQL guarantees that as long as there is at least one grid node standing, every job will be
 * executed. Jobs will automatically failover to another node if a remote node crashed
 * or has rejected execution due to lack of resources. By default, in case of failover, next
 * load balanced node will be picked for job execution. Also jobs will never be re-routed to the
 * nodes they have failed on. This behavior can be changed by configuring any of the existing or a custom
 * {@link FailoverSpi} in grid configuration.
 * <h1 class="header">Resource Injection</h1>
 * All compute jobs, including closures, runnables, callables, and tasks can be injected with
 * turboSQL resources. Both, field and method based injections are supported. The following grid
 * resources can be injected:
 * <ul>
 * <li>{@link TaskSessionResource}</li>
 * <li>{@link TurboSQLInstanceResource}</li>
 * <li>{@link LoggerResource}</li>
 * <li>{@link SpringApplicationContextResource}</li>
 * <li>{@link SpringResource}</li>
 * </ul>
 * Refer to corresponding resource documentation for more information.
 * Here is an example of how to inject instance of {@link TurboSQL} into a computation:
 * <pre name="code" class="java">
 * public class MyTurboSQLJob extends TurboSQLRunnable {
 *      ...
 *      &#64;TurboSQLInstanceResource
 *      private TurboSQL turboSQL;
 *      ...
 *  }
 * </pre>
 * <h1 class="header">Computation SPIs</h1>
 * Note that regardless of which method is used for executing computations, all relevant SPI implementations
 * configured for this compute instance will be used (i.e. failover, load balancing, collision resolution,
 * checkpoints, etc.). If you need to override configured defaults, you should use compute task together with
 * {@link ComputeTaskSpis} annotation. Refer to {@link ComputeTask} documentation for more information.
 */
@SuppressWarnings("deprecation")
public interface TurboSQLCompute extends TurboSQLAsyncSupport {
    /**
     * Gets cluster group to which this {@code TurboSQLCompute} instance belongs.
     *
     * @return Cluster group to which this {@code TurboSQLCompute} instance belongs.
     */
    public ClusterGroup clusterGroup();

    /**
     * Executes given job on the node where data for provided affinity key is located
     * (a.k.a. affinity co-location).
     * </p>
     * It's guaranteed that the data of the whole partition, the affinity key belongs to,
     * will present on the destination node throughout the job execution.
     *
     * @param cacheName Name of the cache to use for affinity co-location.
     * @param affKey Affinity key.
     * @param job Job which will be co-located on the node with given affinity key.
     * @throws TurboSQLException If job failed.
     */
    @TurboSQLAsyncSupported
    public void affinityRun(String cacheName, Object affKey, TurboSQLRunnable job) throws TurboSQLException;

    /**
     * Executes given job asynchronously on the node where data for provided affinity key is located
     * (a.k.a. affinity co-location). The data of the partition where affKey is stored
     * will not be migrated from the target node while the job is executed.
     *
     * @param cacheName Name of the cache to use for affinity co-location.
     * @param affKey Affinity key.
     * @param job Job which will be co-located on the node with given affinity key.
     * @return a Future representing pending completion of the affinity run.
     * @throws TurboSQLException If job failed.
     */
    public TurboSQLFuture<Void> affinityRunAsync(String cacheName, Object affKey, TurboSQLRunnable job)
        throws TurboSQLException;

    /**
     * Executes given job on the node where data for provided affinity key is located
     * (a.k.a. affinity co-location).
     * </p>
     * It's guaranteed that the data of all the partitions of all participating caches,
     * the affinity key belongs to, will present on the destination node throughout the job execution.
     *
     * @param cacheNames Names of the caches to to reserve the partition. The first cache is used for
     *                   affinity co-location.
     * @param affKey Affinity key.
     * @param job Job which will be co-located on the node with given affinity key.
     * @throws TurboSQLException If job failed.
     */
    @TurboSQLAsyncSupported
    public void affinityRun(@NotNull Collection<String> cacheNames, Object affKey, TurboSQLRunnable job)
        throws TurboSQLException;

    /**
     * Executes given job asynchronously on the node where data for provided affinity key is located
     * (a.k.a. affinity co-location). The data of the partition where affKey is stored
     * will not be migrated from the target node while the job is executed. The data
     * of the extra caches' partitions with the same partition number also will not be migrated.
     *
     * @param cacheNames Names of the caches to to reserve the partition. The first cache uses for affinity co-location.
     * @param affKey Affinity key.
     * @param job Job which will be co-located on the node with given affinity key.
     * @return a Future representing pending completion of the affinity run.
     * @throws TurboSQLException If job failed.
     */
    public TurboSQLFuture<Void> affinityRunAsync(@NotNull Collection<String> cacheNames, Object affKey,
        TurboSQLRunnable job) throws TurboSQLException;

    /**
     * Executes given job on the node where partition is located (the partition is primary on the node)
     * </p>
     * It's guaranteed that the data of all the partitions of all participating caches,
     * the affinity key belongs to, will present on the destination node throughout the job execution.
     *
     * @param cacheNames Names of the caches to to reserve the partition. The first cache is used for
     *                   affinity co-location.
     * @param partId Partition number.
     * @param job Job which will be co-located on the node with given affinity key.
     * @throws TurboSQLException If job failed.
     */
    @TurboSQLAsyncSupported
    public void affinityRun(@NotNull Collection<String> cacheNames, int partId, TurboSQLRunnable job)
        throws TurboSQLException;

    /**
     * Executes given job asynchronously on the node where partition is located (the partition is primary on the node)
     * The data of the partition will not be migrated from the target node
     * while the job is executed. The data of the extra caches' partitions with the same partition number
     * also will not be migrated.
     *
     * @param cacheNames Names of the caches to to reserve the partition. The first cache uses for affinity co-location.
     * @param partId Partition number.
     * @param job Job which will be co-located on the node with given affinity key.
     * @return a Future representing pending completion of the affinity run.
     * @throws TurboSQLException If job failed.
     */
    public TurboSQLFuture<Void> affinityRunAsync(@NotNull Collection<String> cacheNames, int partId,
        TurboSQLRunnable job) throws TurboSQLException;

    /**
     * Executes given job on the node where data for provided affinity key is located
     * (a.k.a. affinity co-location).
     * </p>
     * It's guaranteed that the data of the whole partition, the affinity key belongs to,
     * will present on the destination node throughout the job execution.
     *
     * @param cacheName Name of the cache to use for affinity co-location.
     * @param affKey Affinity key.
     * @param job Job which will be co-located on the node with given affinity key.
     * @return Job result.
     * @throws TurboSQLException If job failed.
     */
    @TurboSQLAsyncSupported
    public <R> R affinityCall(String cacheName, Object affKey, TurboSQLCallable<R> job) throws TurboSQLException;

    /**
     * Executes given job asynchronously on the node where data for provided affinity key is located
     * (a.k.a. affinity co-location). The data of the partition where affKey is stored
     * will not be migrated from the target node while the job is executed.
     *
     * @param cacheName Name of the cache to use for affinity co-location.
     * @param affKey Affinity key.
     * @param job Job which will be co-located on the node with given affinity key.
     * @return a Future representing pending completion of the affinity call.
     * @throws TurboSQLException If job failed.
     */
    public <R> TurboSQLFuture<R> affinityCallAsync(String cacheName, Object affKey, TurboSQLCallable<R> job)
        throws TurboSQLException;

    /**
     * Executes given job on the node where data for provided affinity key is located
     * (a.k.a. affinity co-location).
     * </p>
     * It's guaranteed that the data of all the partitions of all participating caches,
     * the affinity key belongs to, will present on the destination node throughout the job execution.
     *
     * @param cacheNames Names of the caches to to reserve the partition. The first cache uses for affinity co-location.
     * @param affKey Affinity key.
     * @param job Job which will be co-located on the node with given affinity key.
     * @return Job result.
     * @throws TurboSQLException If job failed.
     */
    @TurboSQLAsyncSupported
    public <R> R affinityCall(@NotNull Collection<String> cacheNames, Object affKey, TurboSQLCallable<R> job)
        throws TurboSQLException;

    /**
     * Executes given job asynchronously on the node where data for provided affinity key is located
     * (a.k.a. affinity co-location). The data of the partition where affKey is stored
     * will not be migrated from the target node while the job is executed. The data
     * of the extra caches' partitions with the same partition number also will not be migrated.
     *
     * @param cacheNames Names of the caches to to reserve the partition. The first cache uses for affinity co-location.
     * @param affKey Affinity key.
     * @param job Job which will be co-located on the node with given affinity key.
     * @return a Future representing pending completion of the affinity call.
     * @throws TurboSQLException If job failed.
     */
    public <R> TurboSQLFuture<R> affinityCallAsync(@NotNull Collection<String> cacheNames, Object affKey,
        TurboSQLCallable<R> job) throws TurboSQLException;

    /**
     * Executes given job on the node where partition is located (the partition is primary on the node)
     * </p>
     * It's guaranteed that the data of all the partitions of all participating caches,
     * the affinity key belongs to, will present on the destination node throughout the job execution.
     *
     * @param cacheNames Names of the caches to to reserve the partition. The first cache uses for affinity co-location.
     * @param partId Partition to reserve.
     * @param job Job which will be co-located on the node with given affinity key.
     * @return Job result.
     * @throws TurboSQLException If job failed.
     */
    @TurboSQLAsyncSupported
    public <R> R affinityCall(@NotNull Collection<String> cacheNames, int partId, TurboSQLCallable<R> job)
        throws TurboSQLException;

    /**
     * Executes given job asynchronously on the node where partition is located (the partition is primary on the node)
     * The data of the partition will not be migrated from the target node
     * while the job is executed. The data of the extra caches' partitions with the same partition number
     * also will not be migrated.
     *
     * @param cacheNames Names of the caches to to reserve the partition. The first cache uses for affinity co-location.
     * @param partId Partition to reserve.
     * @param job Job which will be co-located on the node with given affinity key.
     * @return a Future representing pending completion of the affinity call.
     * @throws TurboSQLException If job failed.
     */
    public <R> TurboSQLFuture<R> affinityCallAsync(@NotNull Collection<String> cacheNames, int partId,
        TurboSQLCallable<R> job) throws TurboSQLException;

    /**
     * Executes given task on within the cluster group. For step-by-step explanation of task execution process
     * refer to {@link ComputeTask} documentation.
     *
     * @param taskCls Class of the task to execute. If class has {@link ComputeTaskName} annotation,
     *      then task is deployed under a name specified within annotation. Otherwise, full
     *      class name is used as task name.
     * @param arg Optional argument of task execution, can be {@code null}.
     * @return Task result.
     * @throws TurboSQLException If task failed.
     */
    @TurboSQLAsyncSupported
    public <T, R> R execute(Class<? extends ComputeTask<T, R>> taskCls, @Nullable T arg) throws TurboSQLException;

    /**
     * Executes given task asynchronously on within the cluster group. For step-by-step explanation of task execution
     * process refer to {@link ComputeTask} documentation.
     *
     * @param taskCls Class of the task to execute. If class has {@link ComputeTaskName} annotation,
     *      then task is deployed under a name specified within annotation. Otherwise, full
     *      class name is used as task name.
     * @param arg Optional argument of task execution, can be {@code null}.
     * @return a Future representing pending completion of the task.
     * @throws TurboSQLException If task failed.
     */
    public <T, R> ComputeTaskFuture<R> executeAsync(Class<? extends ComputeTask<T, R>> taskCls, @Nullable T arg)
        throws TurboSQLException;

    /**
     * Executes given task within the cluster group. For step-by-step explanation of task execution process
     * refer to {@link ComputeTask} documentation.
     *
     * @param task Instance of task to execute. If task class has {@link ComputeTaskName} annotation,
     *      then task is deployed under a name specified within annotation. Otherwise, full
     *      class name is used as task name.
     * @param arg Optional argument of task execution, can be {@code null}.
     * @return Task result.
     * @throws TurboSQLException If task failed.
     */
    @TurboSQLAsyncSupported
    public <T, R> R execute(ComputeTask<T, R> task, @Nullable T arg) throws TurboSQLException;

    /**
     * Executes given task asynchronously within the cluster group. For step-by-step explanation of task execution
     * process refer to {@link ComputeTask} documentation.
     *
     * @param task Instance of task to execute. If task class has {@link ComputeTaskName} annotation,
     *      then task is deployed under a name specified within annotation. Otherwise, full
     *      class name is used as task name.
     * @param arg Optional argument of task execution, can be {@code null}.
     * @return a Future representing pending completion of the task.
     * @throws TurboSQLException If task failed.
     */
    public <T, R> ComputeTaskFuture<R> executeAsync(ComputeTask<T, R> task, @Nullable T arg) throws TurboSQLException;

    /**
     * Executes given task within the cluster group. For step-by-step explanation of task execution process
     * refer to {@link ComputeTask} documentation.
     * <p>
     * If task for given name has not been deployed yet, then {@code taskName} will be
     * used as task class name to auto-deploy the task (see {@link #localDeployTask(Class, ClassLoader)} method).
     *
     * @param taskName Name of the task to execute.
     * @param arg Optional argument of task execution, can be {@code null}.
     * @return Task result.
     * @throws TurboSQLException If task failed.
     * @see ComputeTask for information about task execution.
     */
    @TurboSQLAsyncSupported
    public <T, R> R execute(String taskName, @Nullable T arg) throws TurboSQLException;

    /**
     * Executes given task asynchronously within the cluster group. For step-by-step explanation of task execution
     * process refer to {@link ComputeTask} documentation.
     * <p>
     * If task for given name has not been deployed yet, then {@code taskName} will be
     * used as task class name to auto-deploy the task (see {@link #localDeployTask(Class, ClassLoader)} method).
     *
     * @param taskName Name of the task to execute.
     * @param arg Optional argument of task execution, can be {@code null}.
     * @return a Future representing pending completion of the task.
     * @throws TurboSQLException If task failed.
     * @see ComputeTask for information about task execution.
     */
    public <T, R> ComputeTaskFuture<R> executeAsync(String taskName, @Nullable T arg) throws TurboSQLException;

    /**
     * Broadcasts given job to all nodes in the cluster group.
     *
     * @param job Job to broadcast to all cluster group nodes.
     * @throws TurboSQLException If job failed.
     */
    @TurboSQLAsyncSupported
    public void broadcast(TurboSQLRunnable job) throws TurboSQLException;

    /**
     * Broadcasts given job asynchronously to all nodes in the cluster group.
     *
     * @param job Job to broadcast to all cluster group nodes.
     * @return a Future representing pending completion of the broadcast execution of the job.
     * @throws TurboSQLException If job failed.
     */
    public TurboSQLFuture<Void> broadcastAsync(TurboSQLRunnable job) throws TurboSQLException;

    /**
     * Broadcasts given job to all nodes in cluster group. Every participating node will return a
     * job result. Collection of all returned job results is returned from the result future.
     *
     * @param job Job to broadcast to all cluster group nodes.
     * @return Collection of results for this execution.
     * @throws TurboSQLException If execution failed.
     */
    @TurboSQLAsyncSupported
    public <R> Collection<R> broadcast(TurboSQLCallable<R> job) throws TurboSQLException;

    /**
     * Broadcasts given job asynchronously to all nodes in cluster group. Every participating node will return a
     * job result. Collection of all returned job results is returned from the result future.
     *
     * @param job Job to broadcast to all cluster group nodes.
     * @return a Future representing pending completion of the broadcast execution of the job.
     * @throws TurboSQLException If execution failed.
     */
    public <R> TurboSQLFuture<Collection<R>> broadcastAsync(TurboSQLCallable<R> job) throws TurboSQLException;

    /**
     * Broadcasts given closure job with passed in argument to all nodes in the cluster group.
     * Every participating node will return a job result. Collection of all returned job results
     * is returned from the result future.
     *
     * @param job Job to broadcast to all cluster group nodes.
     * @param arg Job closure argument.
     * @return Collection of results for this execution.
     * @throws TurboSQLException If execution failed.
     */
    @TurboSQLAsyncSupported
    public <R, T> Collection<R> broadcast(TurboSQLClosure<T, R> job, @Nullable T arg) throws TurboSQLException;

    /**
     * Broadcasts given closure job asynchronously with passed in argument to all nodes in the cluster group.
     * Every participating node will return a job result. Collection of all returned job results
     * is returned from the result future.
     *
     * @param job Job to broadcast to all cluster group nodes.
     * @param arg Job closure argument.
     * @return a Future representing pending completion of the broadcast execution of the job.
     * @throws TurboSQLException If execution failed.
     */
    public <R, T> TurboSQLFuture<Collection<R>> broadcastAsync(TurboSQLClosure<T, R> job, @Nullable T arg)
        throws TurboSQLException;

    /**
     * Executes provided job on a node within the underlying cluster group.
     *
     * @param job Job closure to execute.
     * @throws TurboSQLException If execution failed.
     */
    @TurboSQLAsyncSupported
    public void run(TurboSQLRunnable job) throws TurboSQLException;

    /**
     * Executes provided job asynchronously on a node within the underlying cluster group.
     *
     * @param job Job closure to execute.
     * @return a Future representing pending completion of the job.
     * @throws TurboSQLException If execution failed.
     */
    public TurboSQLFuture<Void> runAsync(TurboSQLRunnable job) throws TurboSQLException;

    /**
     * Executes collection of jobs on grid nodes within the underlying cluster group.
     *
     * @param jobs Collection of jobs to execute.
     * @throws TurboSQLException If execution failed.
     */
    @TurboSQLAsyncSupported
    public void run(Collection<? extends TurboSQLRunnable> jobs) throws TurboSQLException;

    /**
     * Executes collection of jobs asynchronously on grid nodes within the underlying cluster group.
     * Executes asynchronously. Returns control immediately.
     *
     * @param jobs Collection of jobs to execute.
     * @return a Future representing pending completion of the job.
     * @throws TurboSQLException If execution failed.
     */
    public TurboSQLFuture<Void> runAsync(Collection<? extends TurboSQLRunnable> jobs) throws TurboSQLException;

    /**
     * Executes provided job on a node within the underlying cluster group. The result of the
     * job execution is returned from the result closure.
     *
     * @param job Job to execute.
     * @return Job result.
     * @throws TurboSQLException If execution failed.
     */
    @TurboSQLAsyncSupported
    public <R> R call(TurboSQLCallable<R> job) throws TurboSQLException;

    /**
     * Executes provided job asynchronously on a node within the underlying cluster group. The result of the
     * job execution is returned from the result closure.
     *
     * @param job Job to execute.
     * @return a Future representing pending completion of the job.
     * @throws TurboSQLException If execution failed.
     */
    public <R> TurboSQLFuture<R> callAsync(TurboSQLCallable<R> job) throws TurboSQLException;

    /**
     * Executes collection of jobs on nodes within the underlying cluster group.
     * Collection of all returned job results is returned from the result future.
     *
     * @param jobs Collection of jobs to execute.
     * @return Collection of job results for this execution.
     * @throws TurboSQLException If execution failed.
     */
    @TurboSQLAsyncSupported
    public <R> Collection<R> call(Collection<? extends TurboSQLCallable<R>> jobs) throws TurboSQLException;

    /**
     * Executes collection of jobs asynchronously on nodes within the underlying cluster group.
     * Collection of all returned job results is returned from the result future.
     *
     * @param jobs Collection of jobs to execute.
     * @return a Future representing pending completion of the job.
     * @throws TurboSQLException If execution failed.
     */
    public <R> TurboSQLFuture<Collection<R>> callAsync(Collection<? extends TurboSQLCallable<R>> jobs)
        throws TurboSQLException;

    /**
     * Executes collection of jobs on nodes within the underlying cluster group. The returned
     * job results will be reduced into an individual result by provided reducer.
     *
     * @param jobs Collection of jobs to execute.
     * @param rdc Reducer to reduce all job results into one individual return value.
     * @return Reduced job result for this execution.
     * @throws TurboSQLException If execution failed.
     */
    @TurboSQLAsyncSupported
    public <R1, R2> R2 call(Collection<? extends TurboSQLCallable<R1>> jobs, TurboSQLReducer<R1, R2> rdc)
        throws TurboSQLException;

    /**
     * Executes collection of jobs asynchronously on nodes within the underlying cluster group. The returned
     * job results will be reduced into an individual result by provided reducer.
     *
     * @param jobs Collection of jobs to execute.
     * @param rdc Reducer to reduce all job results into one individual return value.
     * @return a Future with reduced job result for this execution.
     * @throws TurboSQLException If execution failed.
     */
    public <R1, R2> TurboSQLFuture<R2> callAsync(Collection<? extends TurboSQLCallable<R1>> jobs,
        TurboSQLReducer<R1, R2> rdc) throws TurboSQLException;

    /**
     * Executes provided closure job on a node within the underlying cluster group. This method is different
     * from {@code run(...)} and {@code call(...)} methods in a way that it receives job argument
     * which is then passed into the closure at execution time.
     *
     * @param job Job to run.
     * @param arg Job argument.
     * @return Job result.
     * @throws TurboSQLException If execution failed.
     */
    @TurboSQLAsyncSupported
    public <R, T> R apply(TurboSQLClosure<T, R> job, @Nullable T arg) throws TurboSQLException;

    /**
     * Executes provided closure job asynchronously on a node within the underlying cluster group.
     * This method is different from {@code run(...)} and {@code call(...)} methods in a way that
     * it receives job argument which is then passed into the closure at execution time.
     *
     * @param job Job to run.
     * @param arg Job argument.
     * @return a Future representing pending completion of the job.
     * @throws TurboSQLException If execution failed.
     */
    public <R, T> TurboSQLFuture<R> applyAsync(TurboSQLClosure<T, R> job, @Nullable T arg) throws TurboSQLException;

    /**
     * Executes provided closure job on nodes within the underlying cluster group. A new job is executed for
     * every argument in the passed in collection. The number of actual job executions will be
     * equal to size of the job arguments collection.
     *
     * @param job Job to run.
     * @param args Job arguments.
     * @return Collection of job results.
     * @throws TurboSQLException If execution failed.
     */
    @TurboSQLAsyncSupported
    public <T, R> Collection<R> apply(TurboSQLClosure<T, R> job, Collection<? extends T> args) throws TurboSQLException;

    /**
     * Executes provided closure job asynchronously on nodes within the underlying cluster group. A new job is executed
     * for every argument in the passed in collection. The number of actual job executions will be
     * equal to size of the job arguments collection.
     *
     * @param job Job to run.
     * @param args Job arguments.
     * @return a Future representing pending completion of the job.
     * @throws TurboSQLException If execution failed.
     */
    public <T, R> TurboSQLFuture<Collection<R>> applyAsync(TurboSQLClosure<T, R> job, Collection<? extends T> args)
        throws TurboSQLException;

    /**
     * Executes provided closure job on nodes within the underlying cluster group. A new job is executed for
     * every argument in the passed in collection. The number of actual job executions will be
     * equal to size of the job arguments collection. The returned job results will be reduced
     * into an individual result by provided reducer.
     *
     * @param job Job to run.
     * @param args Job arguments.
     * @param rdc Reducer to reduce all job results into one individual return value.
     * @return Reduced job result for this execution.
     * @throws TurboSQLException If execution failed.
     */
    @TurboSQLAsyncSupported
    public <R1, R2, T> R2 apply(TurboSQLClosure<T, R1> job, Collection<? extends T> args,
        TurboSQLReducer<R1, R2> rdc) throws TurboSQLException;

    /**
     * Executes provided closure job asynchronously on nodes within the underlying cluster group. A new job is executed
     * for every argument in the passed in collection. The number of actual job executions will be
     * equal to size of the job arguments collection. The returned job results will be reduced
     * into an individual result by provided reducer.
     *
     * @param job Job to run.
     * @param args Job arguments.
     * @param rdc Reducer to reduce all job results into one individual return value.
     * @return a Future with reduced job result for this execution.
     * @throws TurboSQLException If execution failed.
     */
    public <R1, R2, T> TurboSQLFuture<R2> applyAsync(TurboSQLClosure<T, R1> job, Collection<? extends T> args,
        TurboSQLReducer<R1, R2> rdc) throws TurboSQLException;

    /**
     * Gets tasks future for active tasks started on local node.
     *
     * @return Map of active tasks keyed by their task task session ID.
     */
    public <R> Map<TurboSQLUuid, ComputeTaskFuture<R>> activeTaskFutures();

    /**
     * Sets task name for the next executed task in the <b>current thread</b>.
     * When task starts execution, the name is reset, so one name is used only once. You may use
     * this method to set task name when executing jobs directly, without explicitly
     * defining {@link ComputeTask}.
     * <p>
     * Here is an example.
     * <pre name="code" class="java">
     * turboSQL.withName("MyTask").run(new TurboSQLRunnable() {...});
     * </pre>
     *
     * @param taskName Task name.
     * @return This {@code TurboSQLCompute} instance for chaining calls.
     */
    public TurboSQLCompute withName(String taskName);

    /**
     * Sets task timeout for the next executed task in the <b>current thread</b>.
     * When task starts execution, the timeout is reset, so one timeout is used only once. You may use
     * this method to set task name when executing jobs directly, without explicitly
     * defining {@link ComputeTask}.
     * <p>
     * Here is an example.
     * <pre class="brush:java">
     * turboSQL.withTimeout(10000).run(new TurboSQLRunnable() {...});
     * </pre>
     *
     * @param timeout Computation timeout in milliseconds.
     * @return This {@code TurboSQLCompute} instance for chaining calls.
     */
    public TurboSQLCompute withTimeout(long timeout);

    /**
     * Sets no-failover flag for the next task executed in the <b>current thread</b>.
     * If flag is set, job will be never failed over even if remote node crashes or rejects execution.
     * When task starts execution, the no-failover flag is reset, so all other task will use default
     * failover policy, unless this flag is set again.
     * <p>
     * Here is an example.
     * <pre name="code" class="java">
     * turboSQL.compute().withNoFailover().run(new TurboSQLRunnable() {...});
     * </pre>
     *
     * @return This {@code TurboSQLCompute} instance for chaining calls.
     */
    public TurboSQLCompute withNoFailover();


    /**
     * Disables caching for the next executed task in the <b>current thread</b>.
     * Has the same behaviour as annotation {@link com.phonemetra.turbo.compute.ComputeTaskNoResultCache}.
     *
     * <p>
     * Here is an example.
     * <pre name="code" class="java">
     * turboSQL.compute().withNoResultCache().run(new TurboSQLRunnable() {...});
     * </pre>
     * @return This {@code TurboSQLCompute} instance for chaining calls.
     */
    public TurboSQLCompute withNoResultCache();

    /**
     * Explicitly deploys a task with given class loader on the local node. Upon completion of this method,
     * a task can immediately be executed on the grid, considering that all participating
     * remote nodes also have this task deployed.
     * <p>
     * Note that tasks are automatically deployed upon first execution (if peer-class-loading is enabled),
     * so use this method only when the provided class loader is different from the
     * {@code taskClass.getClassLoader()}.
     * <p>
     * Another way of class deployment is deployment from local class path.
     * Classes from local class path always have a priority over P2P deployed ones.
     * <p>
     * Note that class can be deployed multiple times on remote nodes, i.e. re-deployed. Ignition
     * maintains internal version of deployment for each instance of deployment (analogous to
     * class and class loader in Java). Execution happens always on the latest deployed instance.
     * <p>
     * This method has no effect if the class passed in was already deployed.
     *
     * @param taskCls Task class to deploy. If task class has {@link ComputeTaskName} annotation,
     *      then task will be deployed under the name specified within annotation. Otherwise, full
     *      class name will be used as task's name.
     * @param clsLdr Task class loader. This class loader is in charge
     *      of loading all necessary resources for task execution.
     * @throws TurboSQLException If task is invalid and cannot be deployed.
     */
    public void localDeployTask(Class<? extends ComputeTask> taskCls, ClassLoader clsLdr) throws TurboSQLException;

    /**
     * Gets map of all locally deployed tasks keyed by their task name .
     *
     * @return Map of locally deployed tasks keyed by their task name.
     */
    public Map<String, Class<? extends ComputeTask<?, ?>>> localTasks();

    /**
     * Makes the best attempt to undeploy a task with given name within the underlying cluster group.
     * Note that this method returns immediately and does not wait until the task will actually be
     * undeployed on every node.
     *
     * @param taskName Name of the task to undeploy.
     * @throws TurboSQLException Thrown if undeploy failed.
     */
    public void undeployTask(String taskName) throws TurboSQLException;

    /** {@inheritDoc} */
    @Deprecated
    @Override public <R> ComputeTaskFuture<R> future();

    /** {@inheritDoc} */
    @Deprecated
    @Override public TurboSQLCompute withAsync();

    /**
     * Gets instance of the compute API associated with custom executor. All tasks and closures submitted to returned
     * instance will be processed by this executor on both remote and local nodes. If executor with the given name
     * doesn't exist, task will be processed in default ("public") pool.
     * <p>
     * Executor should be defined in {@link TurboSQLConfiguration#setExecutorConfiguration(ExecutorConfiguration...)}.
     *
     * @param name Custom executor name.
     * @return Instance of compute API associated with custom executor.
     */
    public TurboSQLCompute withExecutor(@NotNull String name);
}