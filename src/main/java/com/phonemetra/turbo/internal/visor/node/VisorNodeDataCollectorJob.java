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

package com.phonemetra.turbo.internal.visor.node;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import com.phonemetra.turbo.DataRegionMetrics;
import com.phonemetra.turbo.TurboSQLFileSystem;
import com.phonemetra.turbo.cache.CacheMetrics;
import com.phonemetra.turbo.cluster.ClusterNode;
import com.phonemetra.turbo.configuration.FileSystemConfiguration;
import com.phonemetra.turbo.configuration.TurboSQLConfiguration;
import com.phonemetra.turbo.internal.processors.cache.CacheGroupContext;
import com.phonemetra.turbo.internal.processors.cache.GridCacheAdapter;
import com.phonemetra.turbo.internal.processors.cache.GridCacheContext;
import com.phonemetra.turbo.internal.processors.cache.GridCachePartitionExchangeManager;
import com.phonemetra.turbo.internal.processors.cache.GridCacheProcessor;
import com.phonemetra.turbo.internal.processors.igfs.IgfsProcessorAdapter;
import com.phonemetra.turbo.internal.util.ipc.IpcServerEndpoint;
import com.phonemetra.turbo.internal.util.typedef.F;
import com.phonemetra.turbo.internal.util.typedef.internal.S;
import com.phonemetra.turbo.internal.util.typedef.internal.U;
import com.phonemetra.turbo.internal.visor.VisorJob;
import com.phonemetra.turbo.internal.visor.cache.VisorCache;
import com.phonemetra.turbo.internal.visor.cache.VisorMemoryMetrics;
import com.phonemetra.turbo.internal.visor.compute.VisorComputeMonitoringHolder;
import com.phonemetra.turbo.internal.visor.igfs.VisorIgfs;
import com.phonemetra.turbo.internal.visor.igfs.VisorIgfsEndpoint;
import com.phonemetra.turbo.internal.visor.util.VisorExceptionWrapper;
import com.phonemetra.turbo.lang.TurboSQLProductVersion;

import static com.phonemetra.turbo.internal.processors.cache.GridCacheUtils.isIgfsCache;
import static com.phonemetra.turbo.internal.processors.cache.GridCacheUtils.isSystemCache;
import static com.phonemetra.turbo.internal.visor.compute.VisorComputeMonitoringHolder.COMPUTE_MONITORING_HOLDER_KEY;
import static com.phonemetra.turbo.internal.visor.util.VisorTaskUtils.EVT_MAPPER;
import static com.phonemetra.turbo.internal.visor.util.VisorTaskUtils.MINIMAL_REBALANCE;
import static com.phonemetra.turbo.internal.visor.util.VisorTaskUtils.NOTHING_TO_REBALANCE;
import static com.phonemetra.turbo.internal.visor.util.VisorTaskUtils.REBALANCE_COMPLETE;
import static com.phonemetra.turbo.internal.visor.util.VisorTaskUtils.REBALANCE_NOT_AVAILABLE;
import static com.phonemetra.turbo.internal.visor.util.VisorTaskUtils.VISOR_TASK_EVTS;
import static com.phonemetra.turbo.internal.visor.util.VisorTaskUtils.checkExplicitTaskMonitoring;
import static com.phonemetra.turbo.internal.visor.util.VisorTaskUtils.collectEvents;
import static com.phonemetra.turbo.internal.visor.util.VisorTaskUtils.isProxyCache;
import static com.phonemetra.turbo.internal.visor.util.VisorTaskUtils.isRestartingCache;
import static com.phonemetra.turbo.internal.visor.util.VisorTaskUtils.log;

/**
 * Job that collects data from node.
 */
public class VisorNodeDataCollectorJob extends VisorJob<VisorNodeDataCollectorTaskArg, VisorNodeDataCollectorJobResult> {
    /** */
    private static final long serialVersionUID = 0L;

    /**
     * Create job with given argument.
     *
     * @param arg Job argument.
     * @param debug Debug flag.
     */
    public VisorNodeDataCollectorJob(VisorNodeDataCollectorTaskArg arg, boolean debug) {
        super(arg, debug);
    }

    /**
     * Collect events.
     *
     * @param res Job result.
     * @param evtOrderKey Unique key to take last order key from node local map.
     * @param evtThrottleCntrKey Unique key to take throttle count from node local map.
     * @param all If {@code true} then collect all events otherwise collect only non task events.
     */
    protected void events0(VisorNodeDataCollectorJobResult res, String evtOrderKey, String evtThrottleCntrKey,
        final boolean all) {
        res.getEvents().addAll(collectEvents(turboSQL, evtOrderKey, evtThrottleCntrKey, all, EVT_MAPPER));
    }

    /**
     * Collect events.
     *
     * @param res Job result.
     * @param arg Task argument.
     */
    protected void events(VisorNodeDataCollectorJobResult res, VisorNodeDataCollectorTaskArg arg) {
        try {
            // Visor events explicitly enabled in configuration.
            if (checkExplicitTaskMonitoring(turboSQL))
                res.setTaskMonitoringEnabled(true);
            else {
                // Get current task monitoring state.
                res.setTaskMonitoringEnabled(arg.isTaskMonitoringEnabled());

                if (arg.isTaskMonitoringEnabled()) {
                    ConcurrentMap<String, VisorComputeMonitoringHolder> storage = turboSQL.cluster().nodeLocalMap();

                    VisorComputeMonitoringHolder holder = storage.get(COMPUTE_MONITORING_HOLDER_KEY);

                    if (holder == null) {
                        VisorComputeMonitoringHolder holderNew = new VisorComputeMonitoringHolder();

                        VisorComputeMonitoringHolder holderOld = storage.putIfAbsent(COMPUTE_MONITORING_HOLDER_KEY, holderNew);

                        holder = holderOld == null ? holderNew : holderOld;
                    }

                    // Enable task monitoring for new node in grid.
                    holder.startCollect(turboSQL, arg.getEventsOrderKey());

                    // Update current state after change (it may not changed in some cases).
                    res.setTaskMonitoringEnabled(turboSQL.allEventsUserRecordable(VISOR_TASK_EVTS));
                }
            }

            events0(res, arg.getEventsOrderKey(), arg.getEventsThrottleCounterKey(), arg.isTaskMonitoringEnabled());
        }
        catch (Exception e) {
            res.setEventsEx(new VisorExceptionWrapper(e));
        }
    }

    /**
     * @param ver Version to check.
     * @return {@code true} if found at least one compatible node with specified version.
     */
    protected boolean compatibleWith(TurboSQLProductVersion ver) {
        for (ClusterNode node : turboSQL.cluster().nodes())
            if (node.version().compareToIgnoreTimestamp(ver) <= 0)
                return true;

        return false;
    }

    /**
     * Collect memory metrics.
     *
     * @param res Job result.
     */
    protected void memoryMetrics(VisorNodeDataCollectorJobResult res) {
        try {
            List<VisorMemoryMetrics> memoryMetrics = res.getMemoryMetrics();

            // TODO: Should be really fixed in IGNITE-7111.
            if (turboSQL.cluster().active()) {
                for (DataRegionMetrics m : turboSQL.dataRegionMetrics())
                    memoryMetrics.add(new VisorMemoryMetrics(m));
            }
        }
        catch (Exception e) {
            res.setMemoryMetricsEx(new VisorExceptionWrapper(e));
        }
    }

    /**
     * Collect caches.
     *
     * @param res Job result.
     * @param arg Task argument.
     */
    protected void caches(VisorNodeDataCollectorJobResult res, VisorNodeDataCollectorTaskArg arg) {
        try {
            TurboSQLConfiguration cfg = turboSQL.configuration();

            GridCacheProcessor cacheProc = turboSQL.context().cache();

            Set<String> cacheGrps = arg.getCacheGroups();

            boolean all = F.isEmpty(cacheGrps);

            int partitions = 0;
            double total = 0;
            double ready = 0;

            List<VisorCache> resCaches = res.getCaches();

            boolean rebalanceInProgress = false;

            for (CacheGroupContext grp : cacheProc.cacheGroups()) {
                boolean first = true;

                for (GridCacheContext cache : grp.caches()) {
                    long start0 = U.currentTimeMillis();

                    String cacheName = cache.name();

                    try {
                        if (isProxyCache(turboSQL, cacheName) || isRestartingCache(turboSQL, cacheName))
                            continue;

                        GridCacheAdapter ca = cacheProc.internalCache(cacheName);

                        if (ca == null || !ca.context().started())
                            continue;

                        if (first) {
                            CacheMetrics cm = ca.localMetrics();

                            partitions += cm.getTotalPartitionsCount();

                            long keysTotal = cm.getEstimatedRebalancingKeys();
                            long keysReady = cm.getRebalancedKeys();

                            if (keysReady >= keysTotal)
                                keysReady = Math.max(keysTotal - 1, 0);

                            total += keysTotal;
                            ready += keysReady;

                            if (!rebalanceInProgress && cm.getRebalancingPartitionsCount() > 0)
                                rebalanceInProgress = true;

                            first = false;
                        }

                        boolean addToRes = arg.getSystemCaches() || !(isSystemCache(cacheName) || isIgfsCache(cfg, cacheName));

                        if (addToRes && (all || cacheGrps.contains(ca.configuration().getGroupName())))
                            resCaches.add(new VisorCache(turboSQL, ca, arg.isCollectCacheMetrics()));
                    }
                    catch (IllegalStateException | IllegalArgumentException e) {
                        if (debug && turboSQL.log() != null)
                            turboSQL.log().error("Ignored cache: " + cacheName, e);
                    }
                    finally {
                        if (debug)
                            log(turboSQL.log(), "Collected cache: " + cacheName, getClass(), start0);
                    }
                }
            }

            if (partitions == 0)
                res.setRebalance(NOTHING_TO_REBALANCE);
            else if (total == 0 && rebalanceInProgress)
                res.setRebalance(MINIMAL_REBALANCE);
            else
                res.setRebalance(total > 0 && rebalanceInProgress
                    ? Math.max(ready / total, MINIMAL_REBALANCE)
                    : REBALANCE_COMPLETE);
        }
        catch (Exception e) {
            res.setRebalance(REBALANCE_NOT_AVAILABLE);
            res.setCachesEx(new VisorExceptionWrapper(e));
        }
    }

    /**
     * Collect IGFSs.
     *
     * @param res Job result.
     */
    protected void igfs(VisorNodeDataCollectorJobResult res) {
        try {
            IgfsProcessorAdapter igfsProc = turboSQL.context().igfs();

            for (TurboSQLFileSystem igfs : igfsProc.igfss()) {
                long start0 = U.currentTimeMillis();

                FileSystemConfiguration igfsCfg = igfs.configuration();

                if (isProxyCache(turboSQL, igfsCfg.getDataCacheConfiguration().getName()) ||
                    isProxyCache(turboSQL, igfsCfg.getMetaCacheConfiguration().getName()))
                    continue;

                try {
                    Collection<IpcServerEndpoint> endPoints = igfsProc.endpoints(igfs.name());

                    if (endPoints != null) {
                        for (IpcServerEndpoint ep : endPoints)
                            if (ep.isManagement())
                                res.getIgfsEndpoints().add(new VisorIgfsEndpoint(igfs.name(), turboSQL.name(),
                                    ep.getHost(), ep.getPort()));
                    }

                    res.getIgfss().add(new VisorIgfs(igfs));
                }
                finally {
                    if (debug)
                        log(turboSQL.log(), "Collected IGFS: " + igfs.name(), getClass(), start0);
                }
            }
        }
        catch (Exception e) {
            res.setIgfssEx(new VisorExceptionWrapper(e));
        }
    }

    /**
     * Collect persistence metrics.
     *
     * @param res Job result.
     */
    protected void persistenceMetrics(VisorNodeDataCollectorJobResult res) {
        try {
            res.setPersistenceMetrics(new VisorPersistenceMetrics(turboSQL.dataStorageMetrics()));
        }
        catch (Exception e) {
            res.setPersistenceMetricsEx(new VisorExceptionWrapper(e));
        }
    }

    /** {@inheritDoc} */
    @Override protected VisorNodeDataCollectorJobResult run(VisorNodeDataCollectorTaskArg arg) {
        return run(new VisorNodeDataCollectorJobResult(), arg);
    }

    /**
     * Execution logic of concrete job.
     *
     * @param res Result response.
     * @param arg Job argument.
     * @return Job result.
     */
    protected VisorNodeDataCollectorJobResult run(VisorNodeDataCollectorJobResult res,
        VisorNodeDataCollectorTaskArg arg) {
        res.setGridName(turboSQL.name());

        GridCachePartitionExchangeManager<Object, Object> exchange = turboSQL.context().cache().context().exchange();

        res.setReadyAffinityVersion(new VisorAffinityTopologyVersion(exchange.readyAffinityVersion()));
        res.setHasPendingExchange(exchange.hasPendingExchange());

        res.setTopologyVersion(turboSQL.cluster().topologyVersion());

        long start0 = U.currentTimeMillis();

        events(res, arg);

        if (debug)
            start0 = log(turboSQL.log(), "Collected events", getClass(), start0);

        memoryMetrics(res);

        if (debug)
            start0 = log(turboSQL.log(), "Collected memory metrics", getClass(), start0);

        if (turboSQL.cluster().active())
            caches(res, arg);

        if (debug)
            start0 = log(turboSQL.log(), "Collected caches", getClass(), start0);

        igfs(res);

        if (debug)
            start0 = log(turboSQL.log(), "Collected igfs", getClass(), start0);

        persistenceMetrics(res);

        if (debug)
            log(turboSQL.log(), "Collected persistence metrics", getClass(), start0);

        res.setErrorCount(turboSQL.context().exceptionRegistry().errorCount());

        return res;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(VisorNodeDataCollectorJob.class, this);
    }
}
