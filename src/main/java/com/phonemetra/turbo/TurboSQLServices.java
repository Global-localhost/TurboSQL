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
import com.phonemetra.turbo.cluster.ClusterGroup;
import com.phonemetra.turbo.configuration.TurboSQLConfiguration;
import com.phonemetra.turbo.services.ServiceDeploymentException;
import com.phonemetra.turbo.lang.TurboSQLAsyncSupport;
import com.phonemetra.turbo.lang.TurboSQLAsyncSupported;
import com.phonemetra.turbo.lang.TurboSQLFuture;
import com.phonemetra.turbo.resources.TurboSQLInstanceResource;
import com.phonemetra.turbo.services.Service;
import com.phonemetra.turbo.services.ServiceConfiguration;
import com.phonemetra.turbo.services.ServiceDescriptor;
import org.jetbrains.annotations.Nullable;

/**
 * Defines functionality necessary to deploy distributed services on the grid.
 * <p>
 * Instance of {@code TurboSQLServices} which spans all cluster nodes can be obtained from TurboSQL as follows:
 * <pre class="brush:java">
 * TurboSQL turboSQL = Ignition.turboSQL();
 *
 * TurboSQLServices svcs = turboSQL.services();
 * </pre>
 * You can also obtain an instance of the services facade over a specific cluster group:
 * <pre class="brush:java">
 * // Cluster group over remote nodes (excluding the local node).
 * ClusterGroup remoteNodes = turboSQL.cluster().forRemotes();
 *
 * // Services instance spanning all remote cluster nodes.
 * TurboSQLServices svcs = turboSQL.services(remoteNodes);
 * </pre>
 * <p>
 * With distributed services you can do the following:
 * <ul>
 * <li>Automatically deploy any number of service instances on the grid.</li>
 * <li>
 *     Automatically deploy singletons, including <b>cluster-singleton</b>,
 *     <b>node-singleton</b>, or <b>key-affinity-singleton</b>.
 * </li>
 * <li>Automatically deploy services on node start-up by specifying them in grid configuration.</li>
 * <li>Undeploy any of the deployed services.</li>
 * <li>Get information about service deployment topology within the grid.</li>
 * </ul>
 * <h1 class="header">Deployment From Configuration</h1>
 * In addition to deploying managed services by calling any of the provided {@code deploy(...)} methods,
 * you can also automatically deploy services on startup by specifying them in {@link TurboSQLConfiguration}
 * like so:
 * <pre name="code" class="java">
 * TurboSQLConfiguration cfg = new TurboSQLConfiguration();
 *
 * ServiceConfiguration svcCfg1 = new ServiceConfiguration();
 *
 * // Cluster-wide singleton configuration.
 * svcCfg1.setName("myClusterSingletonService");
 * svcCfg1.setMaxPerNodeCount(1);
 * svcCfg1.setTotalCount(1);
 * svcCfg1.setService(new MyClusterSingletonService());
 *
 * ServiceConfiguration svcCfg2 = new ServiceConfiguration();
 *
 * // Per-node singleton configuration.
 * svcCfg2.setName("myNodeSingletonService");
 * svcCfg2.setMaxPerNodeCount(1);
 * svcCfg2.setService(new MyNodeSingletonService());
 *
 * cfg.setServiceConfiguration(svcCfg1, svcCfg2);
 * ...
 * Ignition.start(cfg);
 * </pre>
 * <h1 class="header">Load Balancing</h1>
 * In all cases, other than singleton service deployment, TurboSQL will automatically make sure that
 * an about equal number of services are deployed on each node within the grid. Whenever cluster topology
 * changes, TurboSQL will re-evaluate service deployments and may re-deploy an already deployed service
 * on another node for better load balancing.
 * <h1 class="header">Fault Tolerance</h1>
 * TurboSQL guarantees that services are deployed according to specified configuration regardless
 * of any topology changes, including node crashes.
 * <h1 class="header">Resource Injection</h1>
 * All distributed services can be injected with
 * turboSQL resources. Both, field and method based injections are supported. The following turboSQL
 * resources can be injected:
 * <ul>
 * <li>{@link TurboSQLInstanceResource}</li>
 * <li>{@link com.phonemetra.turbo.resources.LoggerResource}</li>
 * <li>{@link com.phonemetra.turbo.resources.SpringApplicationContextResource}</li>
 * <li>{@link com.phonemetra.turbo.resources.SpringResource}</li>
 * </ul>
 * Refer to corresponding resource documentation for more information.
 * <h1 class="header">Service Example</h1>
 * Here is an example of how an distributed service may be implemented and deployed:
 * <pre name="code" class="java">
 * // Simple service implementation.
 * public class MyTurboSQLService implements Service {
 *      ...
 *      // Example of turboSQL resource injection. All resources are optional.
 *      // You should inject resources only as needed.
 *      &#64;TurboSQLInstanceResource
 *      private TurboSQL turboSQL;
 *      ...
 *      &#64;Override public void cancel(ServiceContext ctx) {
 *          // No-op.
 *      }
 *
 *      &#64;Override public void execute(ServiceContext ctx) {
 *          // Loop until service is cancelled.
 *          while (!ctx.isCancelled()) {
 *              // Do something.
 *              ...
 *          }
 *      }
 *  }
 * ...
 * TurboSQLServices svcs = turboSQL.services();
 *
 * svcs.deployClusterSingleton("mySingleton", new MyTurboSQLService());
 * </pre>
 */
public interface TurboSQLServices extends TurboSQLAsyncSupport {
    /**
     * Gets the cluster group to which this {@code TurboSQLServices} instance belongs.
     *
     * @return Cluster group to which this {@code TurboSQLServices} instance belongs.
     */
    public ClusterGroup clusterGroup();

    /**
     * Deploys a cluster-wide singleton service. TurboSQL will guarantee that there is always
     * one instance of the service in the cluster. In case if grid node on which the service
     * was deployed crashes or stops, TurboSQL will automatically redeploy it on another node.
     * However, if the node on which the service is deployed remains in topology, then the
     * service will always be deployed on that node only, regardless of topology changes.
     * <p>
     * Note that in case of topology changes, due to network delays, there may be a temporary situation
     * when a singleton service instance will be active on more than one node (e.g. crash detection delay).
     * <p>
     * This method is analogous to calling
     * {@link #deployMultiple(String, com.phonemetra.turbo.services.Service, int, int) deployMultiple(name, svc, 1, 1)}
     * method.
     *
     * @param name Service name.
     * @param svc Service instance.
     * @throws ServiceDeploymentException If failed to deploy service.
     */
    @TurboSQLAsyncSupported
    public void deployClusterSingleton(String name, Service svc) throws ServiceDeploymentException;

    /**
     * Asynchronously deploys a cluster-wide singleton service. TurboSQL will guarantee that there is always
     * one instance of the service in the cluster. In case if grid node on which the service
     * was deployed crashes or stops, TurboSQL will automatically redeploy it on another node.
     * However, if the node on which the service is deployed remains in topology, then the
     * service will always be deployed on that node only, regardless of topology changes.
     * <p>
     * Note that in case of topology changes, due to network delays, there may be a temporary situation
     * when a singleton service instance will be active on more than one node (e.g. crash detection delay).
     * <p>
     * This method is analogous to calling
     * {@link #deployMultipleAsync(String, com.phonemetra.turbo.services.Service, int, int)
     * deployMultipleAsync(name, svc, 1, 1)} method.
     *
     * @param name Service name.
     * @param svc Service instance.
     * @return a Future representing pending completion of the operation.
     */
    public TurboSQLFuture<Void> deployClusterSingletonAsync(String name, Service svc);

    /**
     * Deploys a per-node singleton service. TurboSQL will guarantee that there is always
     * one instance of the service running on each node. Whenever new nodes are started
     * within the underlying cluster group, TurboSQL will automatically deploy one instance of
     * the service on every new node.
     * <p>
     * This method is analogous to calling
     * {@link #deployMultiple(String, com.phonemetra.turbo.services.Service, int, int) deployMultiple(name, svc, 0, 1)}
     * method.
     *
     * @param name Service name.
     * @param svc Service instance.
     * @throws ServiceDeploymentException If failed to deploy service.
     */
    @TurboSQLAsyncSupported
    public void deployNodeSingleton(String name, Service svc) throws ServiceDeploymentException;

    /**
     * Asynchronously deploys a per-node singleton service. TurboSQL will guarantee that there is always
     * one instance of the service running on each node. Whenever new nodes are started
     * within the underlying cluster group, TurboSQL will automatically deploy one instance of
     * the service on every new node.
     * <p>
     * This method is analogous to calling
     * {@link #deployMultipleAsync(String, com.phonemetra.turbo.services.Service, int, int)
     * deployMultipleAsync(name, svc, 0, 1)} method.
     *
     * @param name Service name.
     * @param svc Service instance.
     * @return a Future representing pending completion of the operation.
     */
    public TurboSQLFuture<Void> deployNodeSingletonAsync(String name, Service svc);

    /**
     * Deploys one instance of this service on the primary node for a given affinity key.
     * Whenever topology changes and primary node assignment changes, TurboSQL will always
     * make sure that the service is undeployed on the previous primary node and deployed
     * on the new primary node.
     * <p>
     * Note that in case of topology changes, due to network delays, there may be a temporary situation
     * when a service instance will be active on more than one node (e.g. crash detection delay).
     * <p>
     * This method is analogous to the invocation of {@link #deploy(com.phonemetra.turbo.services.ServiceConfiguration)}
     * method as follows:
     * <pre name="code" class="java">
     *     ServiceConfiguration cfg = new ServiceConfiguration();
     *
     *     cfg.setName(name);
     *     cfg.setService(svc);
     *     cfg.setCacheName(cacheName);
     *     cfg.setAffinityKey(affKey);
     *     cfg.setTotalCount(1);
     *     cfg.setMaxPerNodeCount(1);
     *
     *     turboSQL.services().deploy(cfg);
     * </pre>
     *
     * @param name Service name.
     * @param svc Service instance.
     * @param cacheName Name of the cache on which affinity for key should be calculated, {@code null} for
     *      default cache.
     * @param affKey Affinity cache key.
     * @throws ServiceDeploymentException If failed to deploy service.
     */
    @TurboSQLAsyncSupported
    public void deployKeyAffinitySingleton(String name, Service svc, @Nullable String cacheName, Object affKey)
        throws ServiceDeploymentException;

    /**
     * Asynchronously deploys one instance of this service on the primary node for a given affinity key.
     * Whenever topology changes and primary node assignment changes, TurboSQL will always
     * make sure that the service is undeployed on the previous primary node and deployed
     * on the new primary node.
     * <p>
     * Note that in case of topology changes, due to network delays, there may be a temporary situation
     * when a service instance will be active on more than one node (e.g. crash detection delay).
     * <p>
     * This method is analogous to the invocation of
     * {@link #deployAsync(com.phonemetra.turbo.services.ServiceConfiguration)} method as follows:
     * <pre name="code" class="java">
     *     ServiceConfiguration cfg = new ServiceConfiguration();
     *
     *     cfg.setName(name);
     *     cfg.setService(svc);
     *     cfg.setCacheName(cacheName);
     *     cfg.setAffinityKey(affKey);
     *     cfg.setTotalCount(1);
     *     cfg.setMaxPerNodeCount(1);
     *
     *     turboSQL.services().deployAsync(cfg);
     * </pre>
     *
     * @param name Service name.
     * @param svc Service instance.
     * @param cacheName Name of the cache on which affinity for key should be calculated, {@code null} for
     *      default cache.
     * @param affKey Affinity cache key.
     * @return a Future representing pending completion of the operation.
     */
    public TurboSQLFuture<Void> deployKeyAffinitySingletonAsync(String name, Service svc, @Nullable String cacheName,
        Object affKey);

    /**
     * Deploys multiple instances of the service on the grid. TurboSQL will deploy a
     * maximum amount of services equal to {@code 'totalCnt'} parameter making sure that
     * there are no more than {@code 'maxPerNodeCnt'} service instances running
     * on each node. Whenever topology changes, TurboSQL will automatically rebalance
     * the deployed services within cluster to make sure that each node will end up with
     * about equal number of deployed instances whenever possible.
     * <p>
     * Note that at least one of {@code 'totalCnt'} or {@code 'maxPerNodeCnt'} parameters must have
     * value greater than {@code 0}.
     * <p>
     * This method is analogous to the invocation of {@link #deploy(com.phonemetra.turbo.services.ServiceConfiguration)}
     * method as follows:
     * <pre name="code" class="java">
     *     ServiceConfiguration cfg = new ServiceConfiguration();
     *
     *     cfg.setName(name);
     *     cfg.setService(svc);
     *     cfg.setTotalCount(totalCnt);
     *     cfg.setMaxPerNodeCount(maxPerNodeCnt);
     *
     *     turboSQL.services().deploy(cfg);
     * </pre>
     *
     * @param name Service name.
     * @param svc Service instance.
     * @param totalCnt Maximum number of deployed services in the grid, {@code 0} for unlimited.
     * @param maxPerNodeCnt Maximum number of deployed services on each node, {@code 0} for unlimited.
     * @throws ServiceDeploymentException If failed to deploy service.
     */
    @TurboSQLAsyncSupported
    public void deployMultiple(String name, Service svc, int totalCnt, int maxPerNodeCnt)
        throws ServiceDeploymentException;

    /**
     * Asynchronously deploys multiple instances of the service on the grid. TurboSQL will deploy a
     * maximum amount of services equal to {@code 'totalCnt'} parameter making sure that
     * there are no more than {@code 'maxPerNodeCnt'} service instances running
     * on each node. Whenever topology changes, TurboSQL will automatically rebalance
     * the deployed services within cluster to make sure that each node will end up with
     * about equal number of deployed instances whenever possible.
     * <p>
     * Note that at least one of {@code 'totalCnt'} or {@code 'maxPerNodeCnt'} parameters must have
     * value greater than {@code 0}.
     * <p>
     * This method is analogous to the invocation of
     * {@link #deployAsync(com.phonemetra.turbo.services.ServiceConfiguration)} method as follows:
     * <pre name="code" class="java">
     *     ServiceConfiguration cfg = new ServiceConfiguration();
     *
     *     cfg.setName(name);
     *     cfg.setService(svc);
     *     cfg.setTotalCount(totalCnt);
     *     cfg.setMaxPerNodeCount(maxPerNodeCnt);
     *
     *     turboSQL.services().deployAsync(cfg);
     * </pre>
     *
     * @param name Service name.
     * @param svc Service instance.
     * @param totalCnt Maximum number of deployed services in the grid, {@code 0} for unlimited.
     * @param maxPerNodeCnt Maximum number of deployed services on each node, {@code 0} for unlimited.
     * @return a Future representing pending completion of the operation.
     */
    public TurboSQLFuture<Void> deployMultipleAsync(String name, Service svc, int totalCnt, int maxPerNodeCnt);

    /**
     * Deploys multiple instances of the service on the grid according to provided
     * configuration. TurboSQL will deploy a maximum amount of services equal to
     * {@link com.phonemetra.turbo.services.ServiceConfiguration#getTotalCount() cfg.getTotalCount()}  parameter
     * making sure that there are no more than
     * {@link com.phonemetra.turbo.services.ServiceConfiguration#getMaxPerNodeCount() cfg.getMaxPerNodeCount()}
     * service instances running on each node. Whenever topology changes, TurboSQL will automatically rebalance
     * the deployed services within cluster to make sure that each node will end up with
     * about equal number of deployed instances whenever possible.
     * <p>
     * If {@link com.phonemetra.turbo.services.ServiceConfiguration#getAffinityKey() cfg.getAffinityKey()}
     * is not {@code null}, then TurboSQL  will deploy the service on the primary node for given affinity key.
     * The affinity will be calculated on the cache with
     * {@link com.phonemetra.turbo.services.ServiceConfiguration#getCacheName() cfg.getCacheName()} name.
     * <p>
     * If {@link com.phonemetra.turbo.services.ServiceConfiguration#getNodeFilter() cfg.getNodeFilter()}
     * is not {@code null}, then  TurboSQL will deploy service on all grid nodes for which
     * the provided filter evaluates to {@code true}.
     * The node filter will be checked in addition to the underlying cluster group filter, or the
     * whole grid, if the underlying cluster group includes all the cluster nodes.
     * <p>
     * Note that at least one of {@code 'totalCnt'} or {@code 'maxPerNodeCnt'} parameters must have
     * value greater than {@code 0}.
     * <p>
     * Here is an example of creating service deployment configuration:
     * <pre name="code" class="java">
     *     ServiceConfiguration cfg = new ServiceConfiguration();
     *
     *     cfg.setName(name);
     *     cfg.setService(svc);
     *     cfg.setTotalCount(0); // Unlimited.
     *     cfg.setMaxPerNodeCount(2); // Deploy 2 instances of service on each node.
     *
     *     turboSQL.services().deploy(cfg);
     * </pre>
     *
     * @param cfg Service configuration.
     * @throws ServiceDeploymentException If failed to deploy service.
     */
    @TurboSQLAsyncSupported
    public void deploy(ServiceConfiguration cfg) throws ServiceDeploymentException;

    /**
     * Asynchronously deploys multiple instances of the service on the grid according to provided
     * configuration. TurboSQL will deploy a maximum amount of services equal to
     * {@link com.phonemetra.turbo.services.ServiceConfiguration#getTotalCount() cfg.getTotalCount()}  parameter
     * making sure that there are no more than
     * {@link com.phonemetra.turbo.services.ServiceConfiguration#getMaxPerNodeCount() cfg.getMaxPerNodeCount()}
     * service instances running on each node. Whenever topology changes, TurboSQL will automatically rebalance
     * the deployed services within cluster to make sure that each node will end up with
     * about equal number of deployed instances whenever possible.
     * <p>
     * If {@link com.phonemetra.turbo.services.ServiceConfiguration#getAffinityKey() cfg.getAffinityKey()}
     * is not {@code null}, then TurboSQL
     * will deploy the service on the primary node for given affinity key. The affinity will be calculated
     * on the cache with {@link com.phonemetra.turbo.services.ServiceConfiguration#getCacheName() cfg.getCacheName()} name.
     * <p>
     * If {@link com.phonemetra.turbo.services.ServiceConfiguration#getNodeFilter() cfg.getNodeFilter()}
     * is not {@code null}, then TurboSQL will deploy service on all grid nodes
     * for which the provided filter evaluates to {@code true}.
     * The node filter will be checked in addition to the underlying cluster group filter, or the
     * whole grid, if the underlying cluster group includes all the cluster nodes.
     * <p>
     * Note that at least one of {@code 'totalCnt'} or {@code 'maxPerNodeCnt'} parameters must have
     * value greater than {@code 0}.
     * <p>
     * Here is an example of creating service deployment configuration:
     * <pre name="code" class="java">
     *     ServiceConfiguration cfg = new ServiceConfiguration();
     *
     *     cfg.setName(name);
     *     cfg.setService(svc);
     *     cfg.setTotalCount(0); // Unlimited.
     *     cfg.setMaxPerNodeCount(2); // Deploy 2 instances of service on each node.
     *
     *     turboSQL.services().deployAsync(cfg);
     * </pre>
     *
     * @param cfg Service configuration.
     * @return a Future representing pending completion of the operation.
     */
    public TurboSQLFuture<Void> deployAsync(ServiceConfiguration cfg);

    /**
     * Deploys multiple services described by provided configurations. Depending on specified parameters, multiple
     * instances of the same service may be deployed (see {@link ServiceConfiguration}). Whenever topology changes,
     * TurboSQL will automatically rebalance the deployed services within cluster to make sure that each node will end up
     * with about equal number of deployed instances whenever possible.
     *
     * If deployment of some of the provided services fails, then {@link ServiceDeploymentException} containing a list
     * of failed services will be thrown. It is guaranteed that all services that were provided to this method and are
     * not present in the list of failed services are successfully deployed by the moment of the exception being thrown.
     * Note that if exception is thrown, then partial deployment may have occurred.
     *
     * @param cfgs {@link Collection} of service configurations to be deployed.
     * @throws ServiceDeploymentException If failed to deploy services.
     * @see TurboSQLServices#deploy(ServiceConfiguration)
     * @see TurboSQLServices#deployAllAsync(Collection)
     */
    public void deployAll(Collection<ServiceConfiguration> cfgs) throws ServiceDeploymentException;

    /**
     * Asynchronously deploys multiple services described by provided configurations. Depending on specified parameters,
     * multiple instances of the same service may be deployed (see {@link ServiceConfiguration}). Whenever topology
     * changes, TurboSQL will automatically rebalance the deployed services within cluster to make sure that each node
     * will end up with about equal number of deployed instances whenever possible.
     *
     * If deployment of some of the provided services fails, then {@link ServiceDeploymentException} containing a list
     * of failed services will be thrown from {@link TurboSQLFuture#get get()} method of the returned future. It is
     * guaranteed that all services, that were provided to this method and are not present in the list of failed
     * services, are successfully deployed by the moment of the exception being thrown. Note that if exception is
     * thrown, then partial deployment may have occurred.
     *
     * @param cfgs {@link Collection} of service configurations to be deployed.
     * @return a Future representing pending completion of the operation.
     * @see TurboSQLServices#deploy(ServiceConfiguration)
     * @see TurboSQLServices#deployAll(Collection)
     */
    public TurboSQLFuture<Void> deployAllAsync(Collection<ServiceConfiguration> cfgs);

    /**
     * Cancels service deployment. If a service with specified name was deployed on the grid,
     * then {@link com.phonemetra.turbo.services.Service#cancel(com.phonemetra.turbo.services.ServiceContext)}
     * method will be called on it.
     * <p>
     * Note that TurboSQL cannot guarantee that the service exits from
     * {@link com.phonemetra.turbo.services.Service#execute(com.phonemetra.turbo.services.ServiceContext)}
     * method whenever {@link com.phonemetra.turbo.services.Service#cancel(com.phonemetra.turbo.services.ServiceContext)}
     * is called. It is up to the user to  make sure that the service code properly reacts to cancellations.
     * <p>
     * Supports asynchronous execution (see {@link TurboSQLAsyncSupport}).
     *
     * @param name Name of service to cancel.
     * @throws TurboSQLException If failed to cancel service.
     */
    @TurboSQLAsyncSupported
    public void cancel(String name) throws TurboSQLException;

    /**
     * Asynchronously cancels service deployment. If a service with specified name was deployed on the grid,
     * then {@link com.phonemetra.turbo.services.Service#cancel(com.phonemetra.turbo.services.ServiceContext)}
     * method will be called on it.
     * <p>
     * Note that TurboSQL cannot guarantee that the service exits from
     * {@link com.phonemetra.turbo.services.Service#execute(com.phonemetra.turbo.services.ServiceContext)}
     * method whenever {@link com.phonemetra.turbo.services.Service#cancel(com.phonemetra.turbo.services.ServiceContext)}
     * is called. It is up to the user to
     * make sure that the service code properly reacts to cancellations.
     *
     * @param name Name of service to cancel.
     * @return a Future representing pending completion of the operation.
     */
    public TurboSQLFuture<Void> cancelAsync(String name);

    /**
     * Cancels services with specified names.
     * <p>
     * Note that depending on user logic, it may still take extra time for a service to
     * finish execution, even after it was cancelled.
     * <p>
     * Supports asynchronous execution (see {@link TurboSQLAsyncSupport}).
     *
     * @param names Names of services to cancel.
     * @throws TurboSQLException If failed to cancel services.
     */
    @TurboSQLAsyncSupported
    public void cancelAll(Collection<String> names) throws TurboSQLException;

    /**
     * Asynchronously cancels services with specified names.
     * <p>
     * Note that depending on user logic, it may still take extra time for a service to
     * finish execution, even after it was cancelled.
     *
     * @param names Names of services to cancel.
     * @return a Future representing pending completion of the operation.
     */
    public TurboSQLFuture<Void> cancelAllAsync(Collection<String> names);

    /**
     * Cancels all deployed services.
     * <p>
     * Note that depending on user logic, it may still take extra time for a service to
     * finish execution, even after it was cancelled.
     * <p>
     * Supports asynchronous execution (see {@link TurboSQLAsyncSupport}).
     *
     * @throws TurboSQLException If failed to cancel services.
     */
    @TurboSQLAsyncSupported
    public void cancelAll() throws TurboSQLException;

    /**
     * Asynchronously cancels all deployed services.
     * <p>
     * Note that depending on user logic, it may still take extra time for a service to
     * finish execution, even after it was cancelled.
     *
     * @return a Future representing pending completion of the operation.
     */
    public TurboSQLFuture<Void> cancelAllAsync();

    /**
     * Gets metadata about all deployed services in the grid.
     *
     * @return Metadata about all deployed services in the grid.
     */
    public Collection<ServiceDescriptor> serviceDescriptors();

    /**
     * Gets locally deployed service with specified name.
     *
     * @param name Service name.
     * @param <T> Service type
     * @return Deployed service with specified name.
     */
    public <T> T service(String name);

    /**
     * Gets all locally deployed services with specified name.
     *
     * @param name Service name.
     * @param <T> Service type.
     * @return all deployed services with specified name.
     */
    public <T> Collection<T> services(String name);

    /**
     * Gets a remote handle on the service. If service is available locally,
     * then local instance is returned, otherwise, a remote proxy is dynamically
     * created and provided for the specified service.
     *
     * @param name Service name.
     * @param svcItf Interface for the service.
     * @param sticky Whether or not TurboSQL should always contact the same remote
     *      service or try to load-balance between services.
     * @return Either proxy over remote service or local service if it is deployed locally.
     * @throws TurboSQLException If failed to create service proxy.
     */
    public <T> T serviceProxy(String name, Class<? super T> svcItf, boolean sticky) throws TurboSQLException;

    /**
     * Gets a remote handle on the service with timeout. If service is available locally,
     * then local instance is returned and timeout ignored, otherwise, a remote proxy is dynamically
     * created and provided for the specified service.
     *
     * @param name Service name.
     * @param svcItf Interface for the service.
     * @param sticky Whether or not TurboSQL should always contact the same remote
     *      service or try to load-balance between services.
     * @param timeout If greater than 0 created proxy will wait for service availability only specified time,
     *  and will limit remote service invocation time.
     * @return Either proxy over remote service or local service if it is deployed locally.
     * @throws TurboSQLException If failed to create service proxy.
     */
    public <T> T serviceProxy(String name, Class<? super T> svcItf, boolean sticky, long timeout)
        throws TurboSQLException;

    /** {@inheritDoc} */
    @Deprecated
    @Override public TurboSQLServices withAsync();
}
