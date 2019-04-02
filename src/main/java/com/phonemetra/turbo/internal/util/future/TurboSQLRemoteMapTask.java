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

package com.phonemetra.turbo.internal.util.future;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import com.phonemetra.turbo.TurboSQL;
import com.phonemetra.turbo.TurboSQLCompute;
import com.phonemetra.turbo.TurboSQLException;
import com.phonemetra.turbo.cluster.ClusterNode;
import com.phonemetra.turbo.compute.ComputeJob;
import com.phonemetra.turbo.compute.ComputeJobAdapter;
import com.phonemetra.turbo.compute.ComputeJobContext;
import com.phonemetra.turbo.compute.ComputeJobResult;
import com.phonemetra.turbo.compute.ComputeTask;
import com.phonemetra.turbo.compute.ComputeTaskAdapter;
import com.phonemetra.turbo.compute.ComputeTaskFuture;
import com.phonemetra.turbo.internal.processors.task.GridInternal;
import com.phonemetra.turbo.lang.TurboSQLFuture;
import com.phonemetra.turbo.lang.TurboSQLInClosure;
import com.phonemetra.turbo.resources.TurboSQLInstanceResource;
import com.phonemetra.turbo.resources.JobContextResource;
import org.jetbrains.annotations.Nullable;

/**
 * Util task that will execute ComputeTask on a given node.
 */
@GridInternal public class TurboSQLRemoteMapTask<T, R> extends ComputeTaskAdapter<T, R> {
    /** */
    private static final long serialVersionUID = 0L;

    /** */
    private final ClusterNode node;

    /** */
    private final ComputeTask<T, R> remoteTask;

    /**
     * @param node Target node.
     * @param remoteTask Delegate task.
     */
    public TurboSQLRemoteMapTask(ClusterNode node, ComputeTask<T, R> remoteTask) {
        this.node = node;
        this.remoteTask = remoteTask;
    }

    /** {@inheritDoc} */
    @Nullable @Override public Map<? extends ComputeJob, ClusterNode> map(List<ClusterNode> subgrid,
        @Nullable T arg) throws TurboSQLException {

        for (ClusterNode node : subgrid) {
            if (node.equals(this.node))
                return Collections.singletonMap(new Job<>(remoteTask, arg), node);
        }

        throw new TurboSQLException("Node " + node + " is not present in subgrid.");
    }

    /** {@inheritDoc} */
    @Nullable @Override public R reduce(List<ComputeJobResult> results) throws TurboSQLException {
        assert results.size() == 1;

        return results.get(0).getData();
    }

    /**
     *
     */
    private static class Job<T, R> extends ComputeJobAdapter {
        /** */
        private static final long serialVersionUID = 0L;

        /** Auto-inject job context. */
        @JobContextResource
        private ComputeJobContext jobCtx;

        /** Auto-inject turboSQL instance. */
        @TurboSQLInstanceResource
        private TurboSQL turboSQL;

        /** */
        private final ComputeTask<T, R> remoteTask;

        /** */
        @Nullable private final T arg;

        /** */
        @Nullable private ComputeTaskFuture<R> future;

        /**
         * @param remoteTask Remote task.
         * @param arg Argument.
         */
        public Job(ComputeTask<T, R> remoteTask, @Nullable T arg) {
            this.remoteTask = remoteTask;
            this.arg = arg;
        }

        /** {@inheritDoc} */
        @Override public Object execute() throws TurboSQLException {
            if (future == null) {
                TurboSQLCompute compute = turboSQL.compute().withAsync();

                compute.execute(remoteTask, arg);

                ComputeTaskFuture<R> future = compute.future();

                this.future = future;

                jobCtx.holdcc();

                future.listen(new TurboSQLInClosure<TurboSQLFuture<R>>() {
                    @Override public void apply(TurboSQLFuture<R> future) {
                        jobCtx.callcc();
                    }
                });

                return null;
            }
            else {
                return future.get();
            }
        }
    }

}
