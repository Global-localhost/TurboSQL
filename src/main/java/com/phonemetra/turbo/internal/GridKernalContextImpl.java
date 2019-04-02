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
import java.io.InvalidObjectException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.ObjectStreamException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import com.phonemetra.turbo.TurboSQLCheckedException;
import com.phonemetra.turbo.TurboSQLException;
import com.phonemetra.turbo.TurboSQLLogger;
import com.phonemetra.turbo.TurboSQLSystemProperties;
import com.phonemetra.turbo.cluster.ClusterNode;
import com.phonemetra.turbo.configuration.TurboSQLConfiguration;
import com.phonemetra.turbo.failure.FailureType;
import com.phonemetra.turbo.internal.managers.checkpoint.GridCheckpointManager;
import com.phonemetra.turbo.internal.managers.collision.GridCollisionManager;
import com.phonemetra.turbo.internal.managers.communication.GridIoManager;
import com.phonemetra.turbo.internal.managers.deployment.GridDeploymentManager;
import com.phonemetra.turbo.internal.managers.discovery.GridDiscoveryManager;
import com.phonemetra.turbo.internal.managers.encryption.GridEncryptionManager;
import com.phonemetra.turbo.internal.managers.eventstorage.GridEventStorageManager;
import com.phonemetra.turbo.internal.managers.failover.GridFailoverManager;
import com.phonemetra.turbo.internal.managers.indexing.GridIndexingManager;
import com.phonemetra.turbo.internal.managers.loadbalancer.GridLoadBalancerManager;
import com.phonemetra.turbo.internal.processors.service.ServiceProcessorAdapter;
import com.phonemetra.turbo.internal.processors.affinity.GridAffinityProcessor;
import com.phonemetra.turbo.internal.processors.authentication.TurboSQLAuthenticationProcessor;
import com.phonemetra.turbo.internal.processors.cache.CacheConflictResolutionManager;
import com.phonemetra.turbo.internal.processors.cache.GridCacheProcessor;
import com.phonemetra.turbo.internal.processors.cache.binary.CacheObjectBinaryProcessorImpl;
import com.phonemetra.turbo.internal.processors.cache.mvcc.MvccProcessor;
import com.phonemetra.turbo.internal.processors.cache.persistence.filename.PdsFoldersResolver;
import com.phonemetra.turbo.internal.processors.cacheobject.TurboSQLCacheObjectProcessor;
import com.phonemetra.turbo.internal.processors.closure.GridClosureProcessor;
import com.phonemetra.turbo.internal.processors.cluster.ClusterProcessor;
import com.phonemetra.turbo.internal.processors.cluster.GridClusterStateProcessor;
import com.phonemetra.turbo.internal.processors.compress.CompressionProcessor;
import com.phonemetra.turbo.internal.processors.continuous.GridContinuousProcessor;
import com.phonemetra.turbo.internal.processors.datastreamer.DataStreamProcessor;
import com.phonemetra.turbo.internal.processors.datastructures.DataStructuresProcessor;
import com.phonemetra.turbo.internal.processors.failure.FailureProcessor;
import com.phonemetra.turbo.internal.processors.hadoop.HadoopHelper;
import com.phonemetra.turbo.internal.processors.hadoop.HadoopProcessorAdapter;
import com.phonemetra.turbo.internal.processors.igfs.IgfsHelper;
import com.phonemetra.turbo.internal.processors.igfs.IgfsProcessorAdapter;
import com.phonemetra.turbo.internal.processors.job.GridJobProcessor;
import com.phonemetra.turbo.internal.processors.jobmetrics.GridJobMetricsProcessor;
import com.phonemetra.turbo.internal.processors.marshaller.GridMarshallerMappingProcessor;
import com.phonemetra.turbo.internal.processors.configuration.distributed.DistributedConfigurationProcessor;
import com.phonemetra.turbo.internal.processors.metastorage.DistributedMetaStorage;
import com.phonemetra.turbo.internal.processors.nodevalidation.DiscoveryNodeValidationProcessor;
import com.phonemetra.turbo.internal.processors.odbc.ClientListenerProcessor;
import com.phonemetra.turbo.internal.processors.platform.PlatformProcessor;
import com.phonemetra.turbo.internal.processors.platform.plugin.PlatformPluginProcessor;
import com.phonemetra.turbo.internal.processors.plugin.TurboSQLPluginProcessor;
import com.phonemetra.turbo.internal.processors.pool.PoolProcessor;
import com.phonemetra.turbo.internal.processors.port.GridPortProcessor;
import com.phonemetra.turbo.internal.processors.query.GridQueryProcessor;
import com.phonemetra.turbo.internal.processors.resource.GridResourceProcessor;
import com.phonemetra.turbo.internal.processors.rest.GridRestProcessor;
import com.phonemetra.turbo.internal.processors.schedule.TurboSQLScheduleProcessorAdapter;
import com.phonemetra.turbo.internal.processors.security.GridSecurityProcessor;
import com.phonemetra.turbo.internal.processors.segmentation.GridSegmentationProcessor;
import com.phonemetra.turbo.internal.processors.session.GridTaskSessionProcessor;
import com.phonemetra.turbo.internal.processors.subscription.GridInternalSubscriptionProcessor;
import com.phonemetra.turbo.internal.processors.task.GridTaskProcessor;
import com.phonemetra.turbo.internal.processors.timeout.GridTimeoutProcessor;
import com.phonemetra.turbo.internal.stat.IoStatisticsManager;
import com.phonemetra.turbo.internal.suggestions.GridPerformanceSuggestions;
import com.phonemetra.turbo.internal.util.TurboSQLExceptionRegistry;
import com.phonemetra.turbo.internal.util.StripedExecutor;
import com.phonemetra.turbo.internal.util.spring.TurboSQLSpringHelper;
import com.phonemetra.turbo.internal.util.tostring.GridToStringExclude;
import com.phonemetra.turbo.internal.util.tostring.GridToStringInclude;
import com.phonemetra.turbo.internal.util.typedef.X;
import com.phonemetra.turbo.internal.util.typedef.internal.S;
import com.phonemetra.turbo.internal.util.typedef.internal.U;
import com.phonemetra.turbo.internal.worker.WorkersRegistry;
import com.phonemetra.turbo.lang.TurboSQLPredicate;
import com.phonemetra.turbo.plugin.PluginNotFoundException;
import com.phonemetra.turbo.plugin.PluginProvider;
import com.phonemetra.turbo.thread.TurboSQLStripedThreadPoolExecutor;
import org.jetbrains.annotations.Nullable;

import static com.phonemetra.turbo.TurboSQLSystemProperties.IGNITE_DAEMON;
import static com.phonemetra.turbo.internal.TurboSQLComponentType.SPRING;

/**
 * Implementation of kernal context.
 */
@GridToStringExclude
public class GridKernalContextImpl implements GridKernalContext, Externalizable {
    /** */
    private static final long serialVersionUID = 0L;

    /** */
    private static final ThreadLocal<String> stash = new ThreadLocal<>();

    /*
     * Managers.
     * ========
     */

    /** */
    @GridToStringExclude
    private GridDeploymentManager depMgr;

    /** */
    @GridToStringExclude
    private GridIoManager ioMgr;

    /** */
    @GridToStringExclude
    private GridDiscoveryManager discoMgr;

    /** */
    @GridToStringExclude
    private GridCheckpointManager cpMgr;

    /** */
    @GridToStringExclude
    private GridEventStorageManager evtMgr;

    /** */
    @GridToStringExclude
    private GridFailoverManager failoverMgr;

    /** */
    @GridToStringExclude
    private GridCollisionManager colMgr;

    /** */
    @GridToStringExclude
    private GridLoadBalancerManager loadMgr;

    /** */
    @GridToStringExclude
    private GridSecurityProcessor securityProc;

    /** */
    @GridToStringExclude
    private GridIndexingManager indexingMgr;

    /** */
    @GridToStringExclude
    private GridEncryptionManager encryptionMgr;

    /*
     * Processors.
     * ==========
     */

    /** */
    @GridToStringInclude
    private ClientListenerProcessor sqlListenerProc;

    /** */
    @GridToStringInclude
    private GridQueryProcessor qryProc;

    /** */
    @GridToStringInclude
    private GridTaskProcessor taskProc;

    /** */
    @GridToStringInclude
    private GridJobProcessor jobProc;

    /** */
    @GridToStringInclude
    private GridTimeoutProcessor timeProc;

    /** */
    @GridToStringInclude
    private GridResourceProcessor rsrcProc;

    /** */
    @GridToStringInclude
    private GridJobMetricsProcessor metricsProc;

    /** */
    @GridToStringInclude
    private GridClosureProcessor closProc;

    /** */
    @GridToStringInclude
    private ServiceProcessorAdapter srvcProc;

    /** */
    @GridToStringInclude
    private GridCacheProcessor cacheProc;

    /** Cluster state process. */
    @GridToStringInclude
    private GridClusterStateProcessor stateProc;

    /** Global metastorage. */
    @GridToStringInclude
    private DistributedMetaStorage distributedMetastorage;

    /** Global metastorage. */
    @GridToStringInclude
    private DistributedConfigurationProcessor distributedConfigurationProcessor;

    /** */
    @GridToStringInclude
    private GridTaskSessionProcessor sesProc;

    /** */
    @GridToStringInclude
    private GridPortProcessor portProc;

    /** */
    @GridToStringInclude
    private TurboSQLScheduleProcessorAdapter scheduleProc;

    /** */
    @GridToStringInclude
    private GridRestProcessor restProc;

    /** */
    @GridToStringInclude
    private DataStreamProcessor dataLdrProc;

    /** */
    @GridToStringInclude
    private IgfsProcessorAdapter igfsProc;

    /** */
    @GridToStringInclude
    private IgfsHelper igfsHelper;

    /** */
    @GridToStringInclude
    private HadoopHelper hadoopHelper;

    /** */
    @GridToStringInclude
    private GridSegmentationProcessor segProc;

    /** */
    @GridToStringInclude
    private GridAffinityProcessor affProc;

    /** */
    @GridToStringExclude
    private GridContinuousProcessor contProc;

    /** */
    @GridToStringExclude
    private HadoopProcessorAdapter hadoopProc;

    /** */
    @GridToStringExclude
    private PoolProcessor poolProc;

    /** */
    @GridToStringExclude
    private GridMarshallerMappingProcessor mappingProc;

    /** */
    @GridToStringExclude
    private TurboSQLPluginProcessor pluginProc;

    /** */
    @GridToStringExclude
    private TurboSQLCacheObjectProcessor cacheObjProc;

    /** */
    @GridToStringExclude
    private PlatformProcessor platformProc;

    /** */
    @GridToStringExclude
    private TurboSQLSpringHelper spring;

    /** */
    @GridToStringExclude
    private ClusterProcessor cluster;

    /** */
    @GridToStringExclude
    private CompressionProcessor compressProc;

    /** */
    @GridToStringExclude
    private DataStructuresProcessor dataStructuresProc;

    /** Cache mvcc coordinators. */
    @GridToStringExclude
    private MvccProcessor coordProc;

    /** */
    @GridToStringExclude
    private TurboSQLAuthenticationProcessor authProc;

    /** */
    @GridToStringExclude
    private List<GridComponent> comps = new LinkedList<>();

    /** */
    @GridToStringExclude
    protected ExecutorService execSvc;

    /** */
    @GridToStringExclude
    protected ExecutorService svcExecSvc;

    /** */
    @GridToStringExclude
    protected ExecutorService sysExecSvc;

    /** */
    @GridToStringExclude
    protected StripedExecutor stripedExecSvc;

    /** */
    @GridToStringExclude
    private ExecutorService p2pExecSvc;

    /** */
    @GridToStringExclude
    private ExecutorService mgmtExecSvc;

    /** */
    @GridToStringExclude
    private ExecutorService igfsExecSvc;

    /** */
    @GridToStringExclude
    private StripedExecutor dataStreamExecSvc;

    /** */
    @GridToStringExclude
    protected ExecutorService restExecSvc;

    /** */
    @GridToStringExclude
    protected ExecutorService affExecSvc;

    /** */
    @GridToStringExclude
    protected ExecutorService idxExecSvc;

    /** */
    @GridToStringExclude
    protected TurboSQLStripedThreadPoolExecutor callbackExecSvc;

    /** */
    @GridToStringExclude
    protected ExecutorService qryExecSvc;

    /** */
    @GridToStringExclude
    protected ExecutorService schemaExecSvc;

    /** */
    @GridToStringExclude
    private Map<String, ? extends ExecutorService> customExecSvcs;

    /** */
    @GridToStringExclude
    private Map<String, Object> attrs = new HashMap<>();

    /** */
    @GridToStringExclude
    private WorkersRegistry workersRegistry;

    /** */
    @GridToStringExclude
    private LongJVMPauseDetector pauseDetector;

    /** */
    private Thread.UncaughtExceptionHandler hnd;

    /** */
    private TurboSQLEx grid;

    /** */
    private ExecutorService utilityCachePool;

    /** */
    private TurboSQLConfiguration cfg;

    /** */
    private GridKernalGateway gw;

    /** Network segmented flag. */
    private volatile boolean segFlag;

    /** Performance suggestions. */
    private final GridPerformanceSuggestions perf = new GridPerformanceSuggestions();

    /** Marshaller context. */
    private MarshallerContextImpl marshCtx;

    /** */
    private ClusterNode locNode;

    /** */
    private volatile boolean disconnected;

    /** PDS mode folder name resolver, also generates consistent ID in case new folder naming is used */
    private PdsFoldersResolver pdsFolderRslvr;

    /** */
    private GridInternalSubscriptionProcessor internalSubscriptionProc;

    /** Failure processor. */
    private FailureProcessor failureProc;

    /** Recovery mode flag. Flag is set to {@code false} when discovery manager started. */
    private boolean recoveryMode = true;

    /** IO statistics manager. */
    private IoStatisticsManager ioStatMgr;

    /**
     * No-arg constructor is required by externalization.
     */
    public GridKernalContextImpl() {
        // No-op.
    }

    /**
     * Creates new kernal context.
     *
     * @param log Logger.
     * @param grid Grid instance managed by kernal.
     * @param cfg Grid configuration.
     * @param gw Kernal gateway.
     * @param utilityCachePool Utility cache pool.
     * @param execSvc Public executor service.
     * @param sysExecSvc System executor service.
     * @param stripedExecSvc Striped executor.
     * @param p2pExecSvc P2P executor service.
     * @param mgmtExecSvc Management executor service.
     * @param igfsExecSvc IGFS executor service.
     * @param dataStreamExecSvc data stream executor service.
     * @param restExecSvc REST executor service.
     * @param affExecSvc Affinity executor service.
     * @param idxExecSvc Indexing executor service.
     * @param callbackExecSvc Callback executor service.
     * @param qryExecSvc Query executor service.
     * @param schemaExecSvc Schema executor service.
     * @param customExecSvcs Custom named executors.
     * @param plugins Plugin providers.
     * @param workerRegistry Worker registry.
     * @param hnd Default uncaught exception handler used by thread pools.
     * @param pauseDetector Long JVM pause detector.
     */
    @SuppressWarnings("TypeMayBeWeakened")
    protected GridKernalContextImpl(
        GridLoggerProxy log,
        TurboSQLEx grid,
        TurboSQLConfiguration cfg,
        GridKernalGateway gw,
        ExecutorService utilityCachePool,
        ExecutorService execSvc,
        ExecutorService svcExecSvc,
        ExecutorService sysExecSvc,
        StripedExecutor stripedExecSvc,
        ExecutorService p2pExecSvc,
        ExecutorService mgmtExecSvc,
        ExecutorService igfsExecSvc,
        StripedExecutor dataStreamExecSvc,
        ExecutorService restExecSvc,
        ExecutorService affExecSvc,
        @Nullable ExecutorService idxExecSvc,
        TurboSQLStripedThreadPoolExecutor callbackExecSvc,
        ExecutorService qryExecSvc,
        ExecutorService schemaExecSvc,
        @Nullable Map<String, ? extends ExecutorService> customExecSvcs,
        List<PluginProvider> plugins,
        TurboSQLPredicate<String> clsFilter,
        WorkersRegistry workerRegistry,
        Thread.UncaughtExceptionHandler hnd,
        LongJVMPauseDetector pauseDetector
    ) {
        assert grid != null;
        assert cfg != null;
        assert gw != null;

        this.grid = grid;
        this.cfg = cfg;
        this.gw = gw;
        this.utilityCachePool = utilityCachePool;
        this.execSvc = execSvc;
        this.svcExecSvc = svcExecSvc;
        this.sysExecSvc = sysExecSvc;
        this.stripedExecSvc = stripedExecSvc;
        this.p2pExecSvc = p2pExecSvc;
        this.mgmtExecSvc = mgmtExecSvc;
        this.igfsExecSvc = igfsExecSvc;
        this.dataStreamExecSvc = dataStreamExecSvc;
        this.restExecSvc = restExecSvc;
        this.affExecSvc = affExecSvc;
        this.idxExecSvc = idxExecSvc;
        this.callbackExecSvc = callbackExecSvc;
        this.qryExecSvc = qryExecSvc;
        this.schemaExecSvc = schemaExecSvc;
        this.customExecSvcs = customExecSvcs;
        this.workersRegistry = workerRegistry;
        this.hnd = hnd;
        this.pauseDetector = pauseDetector;

        marshCtx = new MarshallerContextImpl(plugins, clsFilter);

        try {
            spring = SPRING.create(false);
        }
        catch (TurboSQLCheckedException ignored) {
            if (log != null && log.isDebugEnabled())
                log.debug("Failed to load spring component, will not be able to extract userVersion from " +
                    "META-INF/turboSQL.xml.");
        }

        ioStatMgr = new IoStatisticsManager();
    }

    /** {@inheritDoc} */
    @Override public Iterator<GridComponent> iterator() {
        return comps.iterator();
    }

    /** {@inheritDoc} */
    @Override public List<GridComponent> components() {
        return Collections.unmodifiableList(comps);
    }

    /**
     * @param comp Manager to add.
     */
    public void add(GridComponent comp) {
        add(comp, true);
    }

    /**
     * @param comp Manager to add.
     * @param addToList If {@code true} component is added to components list.
     */
    public void add(GridComponent comp, boolean addToList) {
        assert comp != null;

        /*
         * Managers.
         * ========
         */

        if (comp instanceof GridDeploymentManager)
            depMgr = (GridDeploymentManager)comp;
        else if (comp instanceof GridIoManager)
            ioMgr = (GridIoManager)comp;
        else if (comp instanceof GridDiscoveryManager)
            discoMgr = (GridDiscoveryManager)comp;
        else if (comp instanceof GridCheckpointManager)
            cpMgr = (GridCheckpointManager)comp;
        else if (comp instanceof GridEventStorageManager)
            evtMgr = (GridEventStorageManager)comp;
        else if (comp instanceof GridFailoverManager)
            failoverMgr = (GridFailoverManager)comp;
        else if (comp instanceof GridCollisionManager)
            colMgr = (GridCollisionManager)comp;
        else if (comp instanceof GridSecurityProcessor)
            securityProc = (GridSecurityProcessor)comp;
        else if (comp instanceof GridLoadBalancerManager)
            loadMgr = (GridLoadBalancerManager)comp;
        else if (comp instanceof GridIndexingManager)
            indexingMgr = (GridIndexingManager)comp;
        else if (comp instanceof GridEncryptionManager)
            encryptionMgr = (GridEncryptionManager)comp;

        /*
         * Processors.
         * ==========
         */

        else if (comp instanceof FailureProcessor)
            failureProc = (FailureProcessor)comp;
        else if (comp instanceof GridTaskProcessor)
            taskProc = (GridTaskProcessor)comp;
        else if (comp instanceof GridJobProcessor)
            jobProc = (GridJobProcessor)comp;
        else if (comp instanceof GridTimeoutProcessor)
            timeProc = (GridTimeoutProcessor)comp;
        else if (comp instanceof GridResourceProcessor)
            rsrcProc = (GridResourceProcessor)comp;
        else if (comp instanceof GridJobMetricsProcessor)
            metricsProc = (GridJobMetricsProcessor)comp;
        else if (comp instanceof GridCacheProcessor)
            cacheProc = (GridCacheProcessor)comp;
        else if (comp instanceof GridClusterStateProcessor)
            stateProc = (GridClusterStateProcessor)comp;
        else if (comp instanceof DistributedMetaStorage)
            distributedMetastorage = (DistributedMetaStorage)comp;
        else if (comp instanceof DistributedConfigurationProcessor)
            distributedConfigurationProcessor = (DistributedConfigurationProcessor)comp;
        else if (comp instanceof GridTaskSessionProcessor)
            sesProc = (GridTaskSessionProcessor)comp;
        else if (comp instanceof GridPortProcessor)
            portProc = (GridPortProcessor)comp;
        else if (comp instanceof GridClosureProcessor)
            closProc = (GridClosureProcessor)comp;
        else if (comp instanceof ServiceProcessorAdapter)
            srvcProc = (ServiceProcessorAdapter)comp;
        else if (comp instanceof TurboSQLScheduleProcessorAdapter)
            scheduleProc = (TurboSQLScheduleProcessorAdapter)comp;
        else if (comp instanceof GridSegmentationProcessor)
            segProc = (GridSegmentationProcessor)comp;
        else if (comp instanceof GridAffinityProcessor)
            affProc = (GridAffinityProcessor)comp;
        else if (comp instanceof GridRestProcessor)
            restProc = (GridRestProcessor)comp;
        else if (comp instanceof DataStreamProcessor)
            dataLdrProc = (DataStreamProcessor)comp;
        else if (comp instanceof IgfsProcessorAdapter)
            igfsProc = (IgfsProcessorAdapter)comp;
        else if (comp instanceof GridContinuousProcessor)
            contProc = (GridContinuousProcessor)comp;
        else if (comp instanceof HadoopProcessorAdapter)
            hadoopProc = (HadoopProcessorAdapter)comp;
        else if (comp instanceof TurboSQLCacheObjectProcessor)
            cacheObjProc = (TurboSQLCacheObjectProcessor)comp;
        else if (comp instanceof TurboSQLPluginProcessor)
            pluginProc = (TurboSQLPluginProcessor)comp;
        else if (comp instanceof GridQueryProcessor)
            qryProc = (GridQueryProcessor)comp;
        else if (comp instanceof ClientListenerProcessor)
            sqlListenerProc = (ClientListenerProcessor)comp;
        else if (comp instanceof DataStructuresProcessor)
            dataStructuresProc = (DataStructuresProcessor)comp;
        else if (comp instanceof ClusterProcessor)
            cluster = (ClusterProcessor)comp;
        else if (comp instanceof PlatformProcessor)
            platformProc = (PlatformProcessor)comp;
        else if (comp instanceof PoolProcessor)
            poolProc = (PoolProcessor)comp;
        else if (comp instanceof GridMarshallerMappingProcessor)
            mappingProc = (GridMarshallerMappingProcessor)comp;
        else if (comp instanceof MvccProcessor)
            coordProc = (MvccProcessor)comp;
        else if (comp instanceof PdsFoldersResolver)
            pdsFolderRslvr = (PdsFoldersResolver)comp;
        else if (comp instanceof GridInternalSubscriptionProcessor)
            internalSubscriptionProc = (GridInternalSubscriptionProcessor)comp;
        else if (comp instanceof TurboSQLAuthenticationProcessor)
            authProc = (TurboSQLAuthenticationProcessor)comp;
        else if (comp instanceof CompressionProcessor)
            compressProc = (CompressionProcessor)comp;
        else if (!(comp instanceof DiscoveryNodeValidationProcessor
            || comp instanceof PlatformPluginProcessor))
            assert (comp instanceof GridPluginComponent) : "Unknown manager class: " + comp.getClass();

        if (addToList)
            comps.add(comp);
    }

    /**
     * @param helper Helper to add.
     */
    public void addHelper(Object helper) {
        assert helper != null;

        if (helper instanceof IgfsHelper)
            igfsHelper = (IgfsHelper)helper;
        else if (helper instanceof HadoopHelper)
            hadoopHelper = (HadoopHelper)helper;
        else
            assert false : "Unknown helper class: " + helper.getClass();
    }

    /** {@inheritDoc} */
    @Override public boolean isStopping() {
        return ((TurboSQLKernal)grid).isStopping();
    }

    /** */
    @Nullable private ClusterNode localNode() {
        if (locNode == null && discoMgr != null)
            locNode = discoMgr.localNode();

        return locNode;
    }

    /** {@inheritDoc} */
    @Override public UUID localNodeId() {
        ClusterNode locNode0 = localNode();

        return locNode0 != null ? locNode0.id() : config().getNodeId();
    }

    /** {@inheritDoc} */
    @Override public String turboSQLInstanceName() {
        return cfg.getTurboSQLInstanceName();
    }

    /** {@inheritDoc} */
    @Override public GridKernalGateway gateway() {
        return gw;
    }

    /** {@inheritDoc} */
    @Override public TurboSQLEx grid() {
        return grid;
    }

    /** {@inheritDoc} */
    @Override public TurboSQLConfiguration config() {
        return cfg;
    }

    /** {@inheritDoc} */
    @Override public GridTaskProcessor task() {
        return taskProc;
    }

    /** {@inheritDoc} */
    @Override public GridJobProcessor job() {
        return jobProc;
    }

    /** {@inheritDoc} */
    @Override public GridTimeoutProcessor timeout() {
        return timeProc;
    }

    /** {@inheritDoc} */
    @Override public GridResourceProcessor resource() {
        return rsrcProc;
    }

    /** {@inheritDoc} */
    @Override public GridJobMetricsProcessor jobMetric() {
        return metricsProc;
    }

    /** {@inheritDoc} */
    @Override public GridCacheProcessor cache() {
        return cacheProc;
    }

    /** {@inheritDoc} */
    @Override public GridClusterStateProcessor state() {
        return stateProc;
    }

    /** {@inheritDoc} */
    @Override public DistributedMetaStorage distributedMetastorage() {
        return distributedMetastorage;
    }

    /** {@inheritDoc} */
    @Override public DistributedConfigurationProcessor distributedConfiguration() {
        return distributedConfigurationProcessor;
    }

    /** {@inheritDoc} */
    @Override public GridTaskSessionProcessor session() {
        return sesProc;
    }

    /** {@inheritDoc} */
    @Override public GridClosureProcessor closure() {
        return closProc;
    }

    /** {@inheritDoc} */
    @Override public ServiceProcessorAdapter service() {
        return srvcProc;
    }

    /** {@inheritDoc} */
    @Override public GridPortProcessor ports() {
        return portProc;
    }

    /** {@inheritDoc} */
    @Override public TurboSQLScheduleProcessorAdapter schedule() {
        return scheduleProc;
    }

    /** {@inheritDoc} */
    @Override public GridDeploymentManager deploy() {
        return depMgr;
    }

    /** {@inheritDoc} */
    @Override public GridIoManager io() {
        return ioMgr;
    }

    /** {@inheritDoc} */
    @Override public GridDiscoveryManager discovery() {
        return discoMgr;
    }

    /** {@inheritDoc} */
    @Override public GridCheckpointManager checkpoint() {
        return cpMgr;
    }

    /** {@inheritDoc} */
    @Override public GridEventStorageManager event() {
        return evtMgr;
    }

    /** {@inheritDoc} */
    @Override public GridFailoverManager failover() {
        return failoverMgr;
    }

    /** {@inheritDoc} */
    @Override public GridCollisionManager collision() {
        return colMgr;
    }

    /** {@inheritDoc} */
    @Override public GridSecurityProcessor security() {
        return securityProc;
    }

    /** {@inheritDoc} */
    @Override public GridLoadBalancerManager loadBalancing() {
        return loadMgr;
    }

    /** {@inheritDoc} */
    @Override public GridIndexingManager indexing() {
        return indexingMgr;
    }

    /** {@inheritDoc} */
    @Override public GridEncryptionManager encryption() {
        return encryptionMgr;
    }

    /** {@inheritDoc} */
    @Override public WorkersRegistry workersRegistry() {
        return workersRegistry;
    }

    /** {@inheritDoc} */
    @Override public GridAffinityProcessor affinity() {
        return affProc;
    }

    /** {@inheritDoc} */
    @Override public GridRestProcessor rest() {
        return restProc;
    }

    /** {@inheritDoc} */
    @Override public GridSegmentationProcessor segmentation() {
        return segProc;
    }

    /** {@inheritDoc} */
    @Override public <K, V> DataStreamProcessor<K, V> dataStream() {
        return (DataStreamProcessor<K, V>)dataLdrProc;
    }

    /** {@inheritDoc} */
    @Override public IgfsProcessorAdapter igfs() {
        return igfsProc;
    }

    /** {@inheritDoc} */
    @Override public IgfsHelper igfsHelper() {
        return igfsHelper;
    }

    /** {@inheritDoc} */
    @Override public HadoopHelper hadoopHelper() {
        return hadoopHelper;
    }

    /** {@inheritDoc} */
    @Override public GridContinuousProcessor continuous() {
        return contProc;
    }

    /** {@inheritDoc} */
    @Override public HadoopProcessorAdapter hadoop() {
        return hadoopProc;
    }

    /** {@inheritDoc} */
    @Override public PoolProcessor pools() {
        return poolProc;
    }

    /** {@inheritDoc} */
    @Override public GridMarshallerMappingProcessor mapping() {
        return mappingProc;
    }

    /** {@inheritDoc} */
    @Override public ExecutorService utilityCachePool() {
        return utilityCachePool;
    }

    /** {@inheritDoc} */
    @Override public TurboSQLStripedThreadPoolExecutor asyncCallbackPool() {
        return callbackExecSvc;
    }

    /** {@inheritDoc} */
    @Override public TurboSQLCacheObjectProcessor cacheObjects() {
        return cacheObjProc;
    }

    /** {@inheritDoc} */
    @Override public GridQueryProcessor query() {
        return qryProc;
    }

    /** {@inheritDoc} */
    @Override public ClientListenerProcessor sqlListener() {
        return sqlListenerProc;
    }

    /** {@inheritDoc} */
    @Override public DataStructuresProcessor dataStructures() {
        return dataStructuresProc;
    }

    /** {@inheritDoc} */
    @Override public MvccProcessor coordinators() {
        return coordProc;
    }

    /** {@inheritDoc} */
    @Override public TurboSQLAuthenticationProcessor authentication() {
        return authProc;
    }

    /** {@inheritDoc} */
    @Override public TurboSQLLogger log(String ctgr) {
        return config().getGridLogger().getLogger(ctgr);
    }

    /** {@inheritDoc} */
    @Override public TurboSQLLogger log(Class<?> cls) {
        return log(cls.getName());
    }

    /** {@inheritDoc} */
    @Override public GridPerformanceSuggestions performance() {
        return perf;
    }

    /** {@inheritDoc} */
    @Override public LongJVMPauseDetector longJvmPauseDetector() {
        return pauseDetector;
    }

    /** {@inheritDoc} */
    @Override public void printMemoryStats() {
        X.println(">>> ");
        X.println(">>> Grid memory stats [turboSQLInstanceName=" + turboSQLInstanceName() + ']');

        for (GridComponent comp : comps)
            comp.printMemoryStats();
    }

    /** {@inheritDoc} */
    @Override public boolean isDaemon() {
        ClusterNode locNode0 = localNode();

        return locNode0 != null ? locNode0.isDaemon() :
            (config().isDaemon() || TurboSQLSystemProperties.getBoolean(IGNITE_DAEMON));
    }

    /** {@inheritDoc} */
    @Override public String userVersion(ClassLoader ldr) {
        return spring != null ? spring.userVersion(ldr, log(spring.getClass())) : U.DFLT_USER_VERSION;
    }

    /** {@inheritDoc} */
    @Override public PluginProvider pluginProvider(String name) throws PluginNotFoundException {
        PluginProvider plugin = pluginProc.pluginProvider(name);

        if (plugin == null)
            throw new PluginNotFoundException(name);

        return plugin;
    }

    /** {@inheritDoc} */
    @Nullable @Override public <T> T createComponent(Class<T> cls) {
        T res = pluginProc.createComponent(cls);

        if (res != null)
            return res;

        if (cls.equals(TurboSQLCacheObjectProcessor.class))
            return (T)new CacheObjectBinaryProcessorImpl(this);

        if (cls.equals(CacheConflictResolutionManager.class))
            return null;

        throw new TurboSQLException("Unsupported component type: " + cls);
    }

    /**
     * @return Plugin manager.
     */
    @Override public TurboSQLPluginProcessor plugins() {
        return pluginProc;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        U.writeString(out, grid.name());
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        U.readString(in); // Read for compatibility only. See #readResolve().
    }

    /**
     * Reconstructs object on unmarshalling.
     *
     * @return Reconstructed object.
     * @throws ObjectStreamException Thrown in case of unmarshalling error.
     */
    protected Object readResolve() throws ObjectStreamException {
        try {
            return IgnitionEx.localTurboSQL().context();
        }
        catch (IllegalStateException e) {
            throw U.withCause(new InvalidObjectException(e.getMessage()), e);
        }
        finally {
            stash.remove();
        }
    }

    /** {@inheritDoc} */
    @Override public ExecutorService getExecutorService() {
        return execSvc;
    }

    /** {@inheritDoc} */
    @Override public ExecutorService getServiceExecutorService() {
        return svcExecSvc;
    }

    /** {@inheritDoc} */
    @Override public ExecutorService getSystemExecutorService() {
        return sysExecSvc;
    }

    /** {@inheritDoc} */
    @Override public StripedExecutor getStripedExecutorService() {
        return stripedExecSvc;
    }

    /** {@inheritDoc} */
    @Override public ExecutorService getManagementExecutorService() {
        return mgmtExecSvc;
    }

    /** {@inheritDoc} */
    @Override public ExecutorService getPeerClassLoadingExecutorService() {
        return p2pExecSvc;
    }

    /** {@inheritDoc} */
    @Override public ExecutorService getIgfsExecutorService() {
        return igfsExecSvc;
    }

    /** {@inheritDoc} */
    @Override public StripedExecutor getDataStreamerExecutorService() {
        return dataStreamExecSvc;
    }

    /** {@inheritDoc} */
    @Override public ExecutorService getRestExecutorService() {
        return restExecSvc;
    }

    /** {@inheritDoc} */
    @Override public ExecutorService getAffinityExecutorService() {
        return affExecSvc;
    }

    /** {@inheritDoc} */
    @Override @Nullable public ExecutorService getIndexingExecutorService() {
        return idxExecSvc;
    }

    /** {@inheritDoc} */
    @Override public ExecutorService getQueryExecutorService() {
        return qryExecSvc;
    }

    /** {@inheritDoc} */
    @Override public ExecutorService getSchemaExecutorService() {
        return schemaExecSvc;
    }

    /** {@inheritDoc} */
    @Override public Map<String, ? extends ExecutorService> customExecutors() {
        return customExecSvcs;
    }

    /** {@inheritDoc} */
    @Override public TurboSQLExceptionRegistry exceptionRegistry() {
        return TurboSQLExceptionRegistry.get();
    }

    /** {@inheritDoc} */
    @Override public Object nodeAttribute(String key) {
        return attrs.get(key);
    }

    /** {@inheritDoc} */
    @Override public boolean hasNodeAttribute(String key) {
        return attrs.containsKey(key);
    }

    /** {@inheritDoc} */
    @Override public Object addNodeAttribute(String key, Object val) {
        return attrs.put(key, val);
    }

    /** {@inheritDoc} */
    @Override public Map<String, Object> nodeAttributes() {
        return attrs;
    }

    /** {@inheritDoc} */
    @Override public ClusterProcessor cluster() {
        return cluster;
    }

    /** {@inheritDoc} */
    @Override public MarshallerContextImpl marshallerContext() {
        return marshCtx;
    }

    /** {@inheritDoc} */
    @Override public boolean clientNode() {
        return cfg.isClientMode() || cfg.isDaemon();
    }

    /** {@inheritDoc} */
    @Override public boolean clientDisconnected() {
        ClusterNode locNode0 = localNode();

        return locNode0 != null ? (locNode0.isClient() && disconnected) : false;
    }

    /** {@inheritDoc} */
    @Override public PlatformProcessor platform() {
        return platformProc;
    }

    /** {@inheritDoc} */
    @Override public GridInternalSubscriptionProcessor internalSubscriptionProcessor() {
        return internalSubscriptionProc;
    }

    /** {@inheritDoc} */
    @Override public IoStatisticsManager ioStats() {
        return ioStatMgr;
    }

    /**
     * @param disconnected Disconnected flag.
     */
    void disconnected(boolean disconnected) {
        this.disconnected = disconnected;
    }

    /** {@inheritDoc} */
    @Override public PdsFoldersResolver pdsFolderResolver() {
        return pdsFolderRslvr;
    }

    /** {@inheritDoc} */
    @Override public boolean invalid() {
        FailureProcessor failureProc = failure();

        return failureProc != null
            && failureProc.failureContext() != null
            && failureProc.failureContext().type() != FailureType.SEGMENTATION;
    }

    /** {@inheritDoc} */
    @Override public boolean segmented() {
        FailureProcessor failureProc = failure();

        return failureProc != null
            && failureProc.failureContext() != null
            && failureProc.failureContext().type() == FailureType.SEGMENTATION;
    }

    /** {@inheritDoc} */
    @Override public FailureProcessor failure() {
        return failureProc;
    }

    /** {@inheritDoc} */
    @Override public Thread.UncaughtExceptionHandler uncaughtExceptionHandler() {
        return hnd;
    }

    /** {@inheritDoc} */
    @Override public CompressionProcessor compress() {
        return compressProc;
    }

    /** {@inheritDoc} */
    @Override public boolean recoveryMode() {
        return recoveryMode;
    }

    /**
     * @param recoveryMode Recovery mode.
     */
    public void recoveryMode(boolean recoveryMode) {
        this.recoveryMode = recoveryMode;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridKernalContextImpl.class, this);
    }
}
