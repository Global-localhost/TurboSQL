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

package com.phonemetra.turbo.internal.visor.compute;

import java.util.List;
import java.util.Map;
import java.util.Set;
import com.phonemetra.turbo.TurboSQLCompute;
import com.phonemetra.turbo.compute.ComputeJobResult;
import com.phonemetra.turbo.compute.ComputeTaskFuture;
import com.phonemetra.turbo.internal.processors.task.GridInternal;
import com.phonemetra.turbo.internal.processors.task.GridVisorManagementTask;
import com.phonemetra.turbo.internal.util.typedef.internal.S;
import com.phonemetra.turbo.internal.visor.VisorJob;
import com.phonemetra.turbo.internal.visor.VisorOneNodeTask;
import com.phonemetra.turbo.lang.TurboSQLUuid;
import org.jetbrains.annotations.Nullable;

/**
 * Cancels given tasks sessions.
 */
@GridInternal
@GridVisorManagementTask
public class VisorComputeCancelSessionsTask extends VisorOneNodeTask<VisorComputeCancelSessionsTaskArg, Void> {
    /** */
    private static final long serialVersionUID = 0L;

    /** {@inheritDoc} */
    @Override protected VisorComputeCancelSessionsJob job(VisorComputeCancelSessionsTaskArg arg) {
        return new VisorComputeCancelSessionsJob(arg, debug);
    }

    /** {@inheritDoc} */
    @Nullable @Override protected Void reduce0(List<ComputeJobResult> results) {
        // No-op, just awaiting all jobs done.
        return null;
    }

    /**
     * Job that cancel tasks.
     */
    private static class VisorComputeCancelSessionsJob extends VisorJob<VisorComputeCancelSessionsTaskArg, Void> {
        /** */
        private static final long serialVersionUID = 0L;

        /**
         * @param arg Map with task sessions IDs to cancel.
         * @param debug Debug flag.
         */
        private VisorComputeCancelSessionsJob(VisorComputeCancelSessionsTaskArg arg, boolean debug) {
            super(arg, debug);
        }

        /** {@inheritDoc} */
        @Override protected Void run(VisorComputeCancelSessionsTaskArg arg) {
            Set<TurboSQLUuid> sesIds = arg.getSessionIds();

            if (sesIds != null && !sesIds.isEmpty()) {
                TurboSQLCompute compute = turboSQL.compute(turboSQL.cluster().forLocal());

                Map<TurboSQLUuid, ComputeTaskFuture<Object>> futs = compute.activeTaskFutures();

                for (TurboSQLUuid sesId : sesIds) {
                    ComputeTaskFuture<Object> fut = futs.get(sesId);

                    if (fut != null)
                        fut.cancel();
                }
            }

            return null;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(VisorComputeCancelSessionsJob.class, this);
        }
    }
}