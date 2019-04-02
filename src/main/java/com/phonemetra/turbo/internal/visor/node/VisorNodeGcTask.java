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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.phonemetra.turbo.cluster.ClusterMetrics;
import com.phonemetra.turbo.cluster.ClusterNode;
import com.phonemetra.turbo.compute.ComputeJobResult;
import com.phonemetra.turbo.internal.processors.task.GridInternal;
import com.phonemetra.turbo.internal.processors.task.GridVisorManagementTask;
import com.phonemetra.turbo.internal.util.typedef.internal.S;
import com.phonemetra.turbo.internal.visor.VisorJob;
import com.phonemetra.turbo.internal.visor.VisorMultiNodeTask;
import org.jetbrains.annotations.Nullable;

/**
 * Task to run gc on nodes.
 */
@GridInternal
@GridVisorManagementTask
public class VisorNodeGcTask extends VisorMultiNodeTask<Void, Map<UUID, VisorNodeGcTaskResult>, VisorNodeGcTaskResult> {
    /** */
    private static final long serialVersionUID = 0L;

    /** {@inheritDoc} */
    @Override protected VisorNodeGcJob job(Void arg) {
        return new VisorNodeGcJob(arg, debug);
    }

    /** {@inheritDoc} */
    @Nullable @Override protected Map<UUID, VisorNodeGcTaskResult> reduce0(List<ComputeJobResult> results) {
        Map<UUID, VisorNodeGcTaskResult> total = new HashMap<>();

        for (ComputeJobResult res : results) {
            VisorNodeGcTaskResult jobRes = res.getData();

            total.put(res.getNode().id(), jobRes);
        }

        return total;
    }

    /** Job that perform GC on node. */
    private static class VisorNodeGcJob extends VisorJob<Void, VisorNodeGcTaskResult> {
        /** */
        private static final long serialVersionUID = 0L;

        /**
         * Create job with given argument.
         *
         * @param arg Formal task argument.
         * @param debug Debug flag.
         */
        private VisorNodeGcJob(Void arg, boolean debug) {
            super(arg, debug);
        }

        /** {@inheritDoc} */
        @Override protected VisorNodeGcTaskResult run(Void arg) {
            ClusterNode locNode = turboSQL.localNode();

            long before = freeHeap(locNode);

            System.gc();

            return new VisorNodeGcTaskResult(before, freeHeap(locNode));
        }

        /**
         * @param node Node.
         * @return Current free heap.
         */
        private long freeHeap(ClusterNode node) {
            final ClusterMetrics m = node.metrics();

            return m.getHeapMemoryMaximum() - m.getHeapMemoryUsed();
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(VisorNodeGcJob.class, this);
        }
    }
}
