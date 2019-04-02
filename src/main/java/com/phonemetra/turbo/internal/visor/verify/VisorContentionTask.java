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

package com.phonemetra.turbo.internal.visor.verify;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.phonemetra.turbo.TurboSQLException;
import com.phonemetra.turbo.compute.ComputeJobResult;
import com.phonemetra.turbo.internal.processors.cache.verify.ContentionClosure;
import com.phonemetra.turbo.internal.processors.cache.verify.ContentionInfo;
import com.phonemetra.turbo.internal.processors.task.GridInternal;
import com.phonemetra.turbo.internal.util.typedef.internal.S;
import com.phonemetra.turbo.internal.visor.VisorJob;
import com.phonemetra.turbo.internal.visor.VisorMultiNodeTask;
import org.jetbrains.annotations.Nullable;

/**
 *
 */
@GridInternal
public class VisorContentionTask extends VisorMultiNodeTask<VisorContentionTaskArg,
    VisorContentionTaskResult, VisorContentionJobResult> {
    /** */
    private static final long serialVersionUID = 0L;

    /** {@inheritDoc} */
    @Nullable @Override protected VisorContentionTaskResult reduce0(List<ComputeJobResult> list) throws TurboSQLException {
        Map<UUID, Exception> exceptions = new HashMap<>();
        List<VisorContentionJobResult> infos = new ArrayList<>();

        for (ComputeJobResult res : list) {
            if (res.getException() != null)
                exceptions.put(res.getNode().id(), res.getException());
            else
                infos.add(res.getData());
        }

        return new VisorContentionTaskResult(infos, exceptions);
    }

    /** {@inheritDoc} */
    @Override protected VisorJob<VisorContentionTaskArg, VisorContentionJobResult> job(VisorContentionTaskArg arg) {
        return new VisorContentionJob(arg, debug);
    }

    /**
     *
     */
    private static class VisorContentionJob extends VisorJob<VisorContentionTaskArg, VisorContentionJobResult> {
        /** */
        private static final long serialVersionUID = 0L;

        /**
         * @param arg Argument.
         * @param debug Debug.
         */
        protected VisorContentionJob(@Nullable VisorContentionTaskArg arg, boolean debug) {
            super(arg, debug);
        }

        /** {@inheritDoc} */
        @Override protected VisorContentionJobResult run(@Nullable VisorContentionTaskArg arg) throws TurboSQLException {
            try {
                ContentionClosure clo = new ContentionClosure(arg.minQueueSize(), arg.maxPrint());

                turboSQL.context().resource().injectGeneric(clo);

                ContentionInfo info = clo.call();

                return new VisorContentionJobResult(info);
            }
            catch (Exception e) {
                throw new TurboSQLException(e);
            }
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(VisorContentionJob.class, this);
        }
    }
}
