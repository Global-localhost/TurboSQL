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

package com.phonemetra.turbo.internal.visor.cache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import com.phonemetra.turbo.compute.ComputeJobResult;
import com.phonemetra.turbo.internal.processors.cache.GridCacheContext;
import com.phonemetra.turbo.internal.processors.cache.GridCacheProcessor;
import com.phonemetra.turbo.internal.processors.cache.TurboSQLCacheProxy;
import com.phonemetra.turbo.internal.processors.task.GridInternal;
import com.phonemetra.turbo.internal.util.typedef.internal.S;
import com.phonemetra.turbo.internal.util.typedef.internal.U;
import com.phonemetra.turbo.internal.visor.VisorJob;
import com.phonemetra.turbo.internal.visor.VisorMultiNodeTask;
import com.phonemetra.turbo.internal.visor.util.VisorTaskUtils;
import org.jetbrains.annotations.Nullable;

/**
 * Task that collect cache metrics from all nodes.
 */
@GridInternal
public class VisorCacheMetricsCollectorTask extends VisorMultiNodeTask<VisorCacheMetricsCollectorTaskArg,
    Iterable<VisorCacheAggregatedMetrics>, Collection<VisorCacheMetrics>> {
    /** */
    private static final long serialVersionUID = 0L;

    /** {@inheritDoc} */
    @Override protected VisorCacheMetricsCollectorJob job(VisorCacheMetricsCollectorTaskArg arg) {
        return new VisorCacheMetricsCollectorJob(arg, debug);
    }

    /** {@inheritDoc} */
    @Nullable @Override protected Iterable<VisorCacheAggregatedMetrics> reduce0(List<ComputeJobResult> results) {
        Map<String, VisorCacheAggregatedMetrics> grpAggrMetrics = U.newHashMap(results.size());

        for (ComputeJobResult res : results) {
            if (res.getException() == null) {
                Collection<VisorCacheMetrics> cms = res.getData();

                for (VisorCacheMetrics cm : cms) {
                    VisorCacheAggregatedMetrics am = grpAggrMetrics.get(cm.getName());

                    if (am == null) {
                        am = new VisorCacheAggregatedMetrics(cm);

                        grpAggrMetrics.put(cm.getName(), am);
                    }

                    am.getMetrics().put(res.getNode().id(), cm);
                }
            }
        }

        // Create serializable result.
        return new ArrayList<>(grpAggrMetrics.values());
    }

    /**
     * Job that collect cache metrics from node.
     */
    private static class VisorCacheMetricsCollectorJob
        extends VisorJob<VisorCacheMetricsCollectorTaskArg, Collection<VisorCacheMetrics>> {

        /** */
        private static final long serialVersionUID = 0L;

        /**
         * Create job with given argument.
         *
         * @param arg Whether to collect metrics for all caches or for specified cache name only.
         * @param debug Debug flag.
         */
        private VisorCacheMetricsCollectorJob(VisorCacheMetricsCollectorTaskArg arg, boolean debug) {
            super(arg, debug);
        }

        /** {@inheritDoc} */
        @Override protected Collection<VisorCacheMetrics> run(final VisorCacheMetricsCollectorTaskArg arg) {
            assert arg != null;

            boolean showSysCaches = arg.isShowSystemCaches();

            Collection<String> cacheNames = arg.getCacheNames();

            assert cacheNames != null;

            GridCacheProcessor cacheProcessor = turboSQL.context().cache();

            Collection<TurboSQLCacheProxy<?, ?>> caches = cacheProcessor.jcaches();

            Collection<VisorCacheMetrics> res = new ArrayList<>(caches.size());

            boolean allCaches = cacheNames.isEmpty();

            for (TurboSQLCacheProxy ca : caches) {
                String cacheName = ca.getName();

                if (!VisorTaskUtils.isRestartingCache(turboSQL, cacheName)) {
                    GridCacheContext ctx = ca.context();

                    if (ctx.started() && (ctx.affinityNode() || ctx.isNear())) {
                        VisorCacheMetrics cm = new VisorCacheMetrics(turboSQL, cacheName);

                        if ((allCaches || cacheNames.contains(cacheName)) && (showSysCaches || !cm.isSystem()))
                            res.add(cm);
                    }
                }
            }

            return res;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(VisorCacheMetricsCollectorJob.class, this);
        }
    }
}
