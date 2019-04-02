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

package com.phonemetra.turbo.internal.cluster;

import java.util.List;
import java.util.Map;
import com.phonemetra.turbo.cluster.ClusterNode;
import com.phonemetra.turbo.compute.ComputeJob;
import com.phonemetra.turbo.compute.ComputeJobAdapter;
import com.phonemetra.turbo.compute.ComputeJobResult;
import com.phonemetra.turbo.compute.ComputeJobResultPolicy;
import com.phonemetra.turbo.compute.ComputeTaskAdapter;
import com.phonemetra.turbo.internal.processors.task.GridInternal;
import com.phonemetra.turbo.internal.util.typedef.G;
import com.phonemetra.turbo.internal.util.typedef.internal.U;

import static com.phonemetra.turbo.internal.TurboSQLNodeAttributes.ATTR_DAEMON;

/**
 * Special kill task that never fails over jobs.
 */
@GridInternal
class TurboSQLKillTask extends ComputeTaskAdapter<Boolean, Void> {
    /** */
    private static final long serialVersionUID = 0L;

    /** Restart flag. */
    private boolean restart;

    /** {@inheritDoc} */
    @Override public Map<? extends ComputeJob, ClusterNode> map(List<ClusterNode> subgrid, Boolean restart) {
        assert restart != null;

        this.restart = restart;

        Map<ComputeJob, ClusterNode> jobs = U.newHashMap(subgrid.size());

        for (ClusterNode n : subgrid)
            if (!daemon(n))
                jobs.put(new TurboSQLKillJob(), n);

        return jobs;
    }

    /**
     * Checks if given node is a daemon node.
     *
     * @param n Node.
     * @return Whether node is daemon.
     */
    private boolean daemon(ClusterNode n) {
        return "true".equalsIgnoreCase(n.<String>attribute(ATTR_DAEMON));
    }

    /** {@inheritDoc} */
    @Override public ComputeJobResultPolicy result(ComputeJobResult res, List<ComputeJobResult> rcvd) {
        return ComputeJobResultPolicy.WAIT;
    }

    /** {@inheritDoc} */
    @Override public Void reduce(List<ComputeJobResult> results) {
        return null;
    }

    /**
     * Kill job.
     */
    private class TurboSQLKillJob extends ComputeJobAdapter {
        /** */
        private static final long serialVersionUID = 0L;

        /** {@inheritDoc} */
        @Override public Object execute() {
            if (restart)
                new Thread(new Runnable() {
                    @Override public void run() {
                        G.restart(true);
                    }
                },
                "turboSQL-restarter").start();
            else
                new Thread(new Runnable() {
                    @Override public void run() {
                        G.kill(true);
                    }
                },
                "turboSQL-stopper").start();

            return null;
        }
    }
}