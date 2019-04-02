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
import java.util.UUID;
import com.phonemetra.turbo.cluster.ClusterNode;
import com.phonemetra.turbo.internal.processors.task.GridInternal;
import com.phonemetra.turbo.internal.util.typedef.internal.S;
import com.phonemetra.turbo.internal.visor.VisorJob;
import com.phonemetra.turbo.internal.visor.VisorOneNodeTask;

/**
 * Task that returns collection of cache data nodes IDs.
 */
@GridInternal
public class VisorCacheNodesTask extends VisorOneNodeTask<VisorCacheNodesTaskArg, Collection<UUID>> {
    /** */
    private static final long serialVersionUID = 0L;

    /** {@inheritDoc} */
    @Override protected VisorCacheNodesJob job(VisorCacheNodesTaskArg arg) {
        return new VisorCacheNodesJob(arg, debug);
    }

    /**
     * Job that collects cluster group for specified cache.
     */
    private static class VisorCacheNodesJob extends VisorJob<VisorCacheNodesTaskArg, Collection<UUID>> {
        /** */
        private static final long serialVersionUID = 0L;

        /**
         * Create job.
         *
         * @param cacheName Cache name to clear.
         * @param debug Debug flag.
         */
        private VisorCacheNodesJob(VisorCacheNodesTaskArg cacheName, boolean debug) {
            super(cacheName, debug);
        }

        /** {@inheritDoc} */
        @Override protected Collection<UUID> run(VisorCacheNodesTaskArg arg) {
            Collection<ClusterNode> nodes = turboSQL.cluster().forDataNodes(arg.getCacheName()).nodes();

            Collection<UUID> res = new ArrayList<>(nodes.size());

            for (ClusterNode node : nodes)
                res.add(node.id());

            return res;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(VisorCacheNodesJob.class, this);
        }
    }
}