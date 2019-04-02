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

import java.util.List;
import java.util.Map;
import com.phonemetra.turbo.TurboSQLException;
import com.phonemetra.turbo.compute.ComputeJobContext;
import com.phonemetra.turbo.compute.ComputeTaskFuture;
import com.phonemetra.turbo.internal.processors.cache.verify.PartitionHashRecord;
import com.phonemetra.turbo.internal.processors.cache.verify.PartitionKey;
import com.phonemetra.turbo.internal.processors.cache.verify.VerifyBackupPartitionsTask;
import com.phonemetra.turbo.internal.processors.task.GridInternal;
import com.phonemetra.turbo.internal.util.typedef.internal.S;
import com.phonemetra.turbo.internal.visor.VisorJob;
import com.phonemetra.turbo.internal.visor.VisorOneNodeTask;
import com.phonemetra.turbo.lang.TurboSQLFuture;
import com.phonemetra.turbo.lang.TurboSQLInClosure;
import com.phonemetra.turbo.resources.JobContextResource;

/**
 * Task to verify checksums of backup partitions.
 */
@GridInternal
@Deprecated
public class VisorIdleVerifyTask extends VisorOneNodeTask<VisorIdleVerifyTaskArg, VisorIdleVerifyTaskResult> {
    /** */
    private static final long serialVersionUID = 0L;

    /** {@inheritDoc} */
    @Override protected VisorJob<VisorIdleVerifyTaskArg, VisorIdleVerifyTaskResult> job(VisorIdleVerifyTaskArg arg) {
        return new VisorIdleVerifyJob(arg, debug);
    }

    /**
     *
     */
    @Deprecated
    private static class VisorIdleVerifyJob extends VisorJob<VisorIdleVerifyTaskArg, VisorIdleVerifyTaskResult> {
        /** */
        private static final long serialVersionUID = 0L;

        /** */
        private ComputeTaskFuture<Map<PartitionKey, List<PartitionHashRecord>>> fut;

        /** Auto-inject job context. */
        @JobContextResource
        protected transient ComputeJobContext jobCtx;

        /**
         * @param arg Argument.
         * @param debug Debug.
         */
        private VisorIdleVerifyJob(VisorIdleVerifyTaskArg arg, boolean debug) {
            super(arg, debug);
        }

        /** {@inheritDoc} */
        @Override protected VisorIdleVerifyTaskResult run(VisorIdleVerifyTaskArg arg) throws TurboSQLException {
            if (fut == null) {
                fut = turboSQL.compute().executeAsync(VerifyBackupPartitionsTask.class, arg.getCaches());

                if (!fut.isDone()) {
                    jobCtx.holdcc();

                    fut.listen(new TurboSQLInClosure<TurboSQLFuture<Map<PartitionKey, List<PartitionHashRecord>>>>() {
                        @Override public void apply(TurboSQLFuture<Map<PartitionKey, List<PartitionHashRecord>>> f) {
                            jobCtx.callcc();
                        }
                    });

                    return null;
                }
            }

            return new VisorIdleVerifyTaskResult(fut.get());
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(VisorIdleVerifyJob.class, this);
        }
    }
}
