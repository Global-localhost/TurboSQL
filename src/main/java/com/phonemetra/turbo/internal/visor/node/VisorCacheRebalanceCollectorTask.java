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
import com.phonemetra.turbo.cache.CacheMetrics;
import com.phonemetra.turbo.cluster.BaselineNode;
import com.phonemetra.turbo.compute.ComputeJobResult;
import com.phonemetra.turbo.internal.cluster.TurboSQLClusterEx;
import com.phonemetra.turbo.internal.processors.cache.CacheGroupContext;
import com.phonemetra.turbo.internal.processors.cache.GridCacheAdapter;
import com.phonemetra.turbo.internal.processors.cache.GridCacheProcessor;
import com.phonemetra.turbo.internal.processors.cache.GridCacheUtils;
import com.phonemetra.turbo.internal.processors.task.GridInternal;
import com.phonemetra.turbo.internal.util.typedef.internal.S;
import com.phonemetra.turbo.internal.util.typedef.internal.U;
import com.phonemetra.turbo.internal.visor.VisorJob;
import com.phonemetra.turbo.internal.visor.VisorMultiNodeTask;
import org.jetbrains.annotations.Nullable;

import static com.phonemetra.turbo.internal.visor.node.VisorNodeBaselineStatus.BASELINE_NOT_AVAILABLE;
import static com.phonemetra.turbo.internal.visor.node.VisorNodeBaselineStatus.NODE_IN_BASELINE;
import static com.phonemetra.turbo.internal.visor.node.VisorNodeBaselineStatus.NODE_NOT_IN_BASELINE;
import static com.phonemetra.turbo.internal.visor.util.VisorTaskUtils.MINIMAL_REBALANCE;
import static com.phonemetra.turbo.internal.visor.util.VisorTaskUtils.NOTHING_TO_REBALANCE;
import static com.phonemetra.turbo.internal.visor.util.VisorTaskUtils.REBALANCE_COMPLETE;
import static com.phonemetra.turbo.internal.visor.util.VisorTaskUtils.REBALANCE_NOT_AVAILABLE;
import static com.phonemetra.turbo.internal.visor.util.VisorTaskUtils.isProxyCache;
import static com.phonemetra.turbo.internal.visor.util.VisorTaskUtils.isRestartingCache;
import static com.phonemetra.turbo.internal.visor.util.VisorTaskUtils.log;

/**
 * Collects topology rebalance metrics.
 */
@GridInternal
public class VisorCacheRebalanceCollectorTask extends VisorMultiNodeTask<VisorCacheRebalanceCollectorTaskArg,
    VisorCacheRebalanceCollectorTaskResult, VisorCacheRebalanceCollectorJobResult> {
    /** */
    private static final long serialVersionUID = 0L;

    /** {@inheritDoc} */
    @Override protected VisorCacheRebalanceCollectorJob job(VisorCacheRebalanceCollectorTaskArg arg) {
        return new VisorCacheRebalanceCollectorJob(arg, debug);
    }

    /** {@inheritDoc} */
    @Nullable @Override protected VisorCacheRebalanceCollectorTaskResult reduce0(List<ComputeJobResult> results) {
        return reduce(new VisorCacheRebalanceCollectorTaskResult(), results);
    }

    /**
     * @param taskRes Task result.
     * @param results Results.
     * @return Topology rebalance metrics collector task result.
     */
    protected VisorCacheRebalanceCollectorTaskResult reduce(
        VisorCacheRebalanceCollectorTaskResult taskRes,
        List<ComputeJobResult> results
    ) {
        for (ComputeJobResult res : results) {
            VisorCacheRebalanceCollectorJobResult jobRes = res.getData();

            if (jobRes != null) {
                if (res.getException() == null)
                    taskRes.getRebalance().put(res.getNode().id(), jobRes.getRebalance());

                taskRes.getBaseline().put(res.getNode().id(), jobRes.getBaseline());
            }
        }

        return taskRes;
    }

    /**
     * Job that collects rebalance metrics.
     */
    private static class VisorCacheRebalanceCollectorJob extends VisorJob<VisorCacheRebalanceCollectorTaskArg, VisorCacheRebalanceCollectorJobResult> {
        /** */
        private static final long serialVersionUID = 0L;

        /**
         * Create job with given argument.
         *
         * @param arg Job argument.
         * @param debug Debug flag.
         */
        private VisorCacheRebalanceCollectorJob(VisorCacheRebalanceCollectorTaskArg arg, boolean debug) {
            super(arg, debug);
        }

        /** {@inheritDoc} */
        @Override protected VisorCacheRebalanceCollectorJobResult run(VisorCacheRebalanceCollectorTaskArg arg) {
            VisorCacheRebalanceCollectorJobResult res = new VisorCacheRebalanceCollectorJobResult();

            long start0 = U.currentTimeMillis();

            try {
                int partitions = 0;
                double total = 0;
                double ready = 0;

                GridCacheProcessor cacheProc = turboSQL.context().cache();

                boolean rebalanceInProgress = false;

                for (CacheGroupContext grp: cacheProc.cacheGroups()) {
                    String cacheName = grp.config().getName();

                    if (isProxyCache(turboSQL, cacheName) || isRestartingCache(turboSQL, cacheName))
                        continue;

                    try {
                        GridCacheAdapter ca = cacheProc.internalCache(cacheName);

                        if (ca == null || !ca.context().started())
                            continue;

                        CacheMetrics cm = ca.localMetrics();

                        partitions += cm.getTotalPartitionsCount();

                        long keysTotal = cm.getEstimatedRebalancingKeys();
                        long keysReady = cm.getRebalancedKeys();

                        if (keysReady >= keysTotal)
                            keysReady = Math.max(keysTotal - 1, 0);

                        total += keysTotal;
                        ready += keysReady;

                        if (cm.getRebalancingPartitionsCount() > 0)
                            rebalanceInProgress = true;
                    }
                    catch(IllegalStateException | IllegalArgumentException e) {
                        if (debug && turboSQL.log() != null)
                            turboSQL.log().error("Ignored cache group: " + grp.cacheOrGroupName(), e);
                    }
                }

                if (partitions == 0)
                    res.setRebalance(NOTHING_TO_REBALANCE);
                else if (total == 0 && rebalanceInProgress)
                    res.setRebalance(MINIMAL_REBALANCE);
                else
                    res.setRebalance(total > 0 && rebalanceInProgress ? Math.max(ready / total, MINIMAL_REBALANCE) : REBALANCE_COMPLETE);
            }
            catch (Exception e) {
                res.setRebalance(REBALANCE_NOT_AVAILABLE);

                turboSQL.log().error("Failed to collect rebalance metrics", e);
            }

            if (GridCacheUtils.isPersistenceEnabled(turboSQL.configuration())) {
                TurboSQLClusterEx cluster = turboSQL.cluster();

                Object consistentId = turboSQL.localNode().consistentId();

                Collection<? extends BaselineNode> baseline = cluster.currentBaselineTopology();

                if (baseline != null) {
                    boolean inBaseline = baseline.stream().anyMatch(n -> consistentId.equals(n.consistentId()));

                    res.setBaseline(inBaseline ? NODE_IN_BASELINE : NODE_NOT_IN_BASELINE);
                }
                else
                    res.setBaseline(BASELINE_NOT_AVAILABLE);
            }
            else
                res.setBaseline(BASELINE_NOT_AVAILABLE);

            if (debug)
                log(turboSQL.log(), "Collected rebalance metrics", getClass(), start0);

            return res;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(VisorCacheRebalanceCollectorJob.class, this);
        }
    }
}
