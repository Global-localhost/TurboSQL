/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonemetra.turbo.internal.visor.verify;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import com.phonemetra.turbo.TurboSQLException;
import com.phonemetra.turbo.compute.ComputeJobContext;
import com.phonemetra.turbo.compute.ComputeTaskFuture;
import com.phonemetra.turbo.internal.processors.cache.verify.CollectConflictPartitionKeysTask;
import com.phonemetra.turbo.internal.processors.cache.verify.PartitionEntryHashRecord;
import com.phonemetra.turbo.internal.processors.cache.verify.PartitionHashRecord;
import com.phonemetra.turbo.internal.processors.cache.verify.RetrieveConflictPartitionValuesTask;
import com.phonemetra.turbo.internal.processors.task.GridInternal;
import com.phonemetra.turbo.internal.util.typedef.internal.S;
import com.phonemetra.turbo.internal.visor.VisorJob;
import com.phonemetra.turbo.internal.visor.VisorOneNodeTask;
import com.phonemetra.turbo.lang.TurboSQLFuture;
import com.phonemetra.turbo.lang.TurboSQLInClosure;
import com.phonemetra.turbo.resources.JobContextResource;

/**
 * Task to find diverged keys of conflict partition.
 */
@GridInternal
public class VisorIdleAnalyzeTask extends VisorOneNodeTask<VisorIdleAnalyzeTaskArg, VisorIdleAnalyzeTaskResult> {
    /** */
    private static final long serialVersionUID = 0L;

    /** {@inheritDoc} */
    @Override protected VisorJob<VisorIdleAnalyzeTaskArg, VisorIdleAnalyzeTaskResult> job(VisorIdleAnalyzeTaskArg arg) {
        return new VisorIdleVerifyJob(arg, debug);
    }

    /**
     *
     */
    private static class VisorIdleVerifyJob extends VisorJob<VisorIdleAnalyzeTaskArg, VisorIdleAnalyzeTaskResult> {
        /** */
        private static final long serialVersionUID = 0L;

        /** */
        private ComputeTaskFuture<Map<PartitionHashRecord, List<PartitionEntryHashRecord>>> conflictKeysFut;

        /** */
        private ComputeTaskFuture<Map<PartitionHashRecord, List<PartitionEntryHashRecord>>> conflictValsFut;

        /** Auto-inject job context. */
        @JobContextResource
        protected transient ComputeJobContext jobCtx;

        /**
         * @param arg Argument.
         * @param debug Debug.
         */
        private VisorIdleVerifyJob(VisorIdleAnalyzeTaskArg arg, boolean debug) {
            super(arg, debug);
        }

        /** {@inheritDoc} */
        @Override protected VisorIdleAnalyzeTaskResult run(VisorIdleAnalyzeTaskArg arg) throws TurboSQLException {
            if (conflictKeysFut == null) {
                conflictKeysFut = turboSQL.compute()
                    .executeAsync(CollectConflictPartitionKeysTask.class, arg.getPartitionKey());

                if (!conflictKeysFut.isDone()) {
                    jobCtx.holdcc();

                    conflictKeysFut.listen(new TurboSQLInClosure<TurboSQLFuture<Map<PartitionHashRecord, List<PartitionEntryHashRecord>>>>() {
                        @Override public void apply(TurboSQLFuture<Map<PartitionHashRecord, List<PartitionEntryHashRecord>>> f) {
                            jobCtx.callcc();
                        }
                    });

                    return null;
                }
            }

            Map<PartitionHashRecord, List<PartitionEntryHashRecord>> conflictKeys = conflictKeysFut.get();

            if (conflictKeys.isEmpty())
                return new VisorIdleAnalyzeTaskResult(Collections.emptyMap());

            if (conflictValsFut == null) {
                conflictValsFut = turboSQL.compute().executeAsync(RetrieveConflictPartitionValuesTask.class, conflictKeys);

                if (!conflictValsFut.isDone()) {
                    jobCtx.holdcc();

                    conflictKeysFut.listen(new TurboSQLInClosure<TurboSQLFuture<Map<PartitionHashRecord, List<PartitionEntryHashRecord>>>>() {
                        @Override public void apply(TurboSQLFuture<Map<PartitionHashRecord, List<PartitionEntryHashRecord>>> f) {
                            jobCtx.callcc();
                        }
                    });

                    return null;
                }
            }

            return new VisorIdleAnalyzeTaskResult(conflictValsFut.get());
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(VisorIdleVerifyJob.class, this);
        }
    }
}
