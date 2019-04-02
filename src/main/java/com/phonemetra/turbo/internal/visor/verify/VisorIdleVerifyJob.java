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

import com.phonemetra.turbo.TurboSQLException;
import com.phonemetra.turbo.compute.ComputeJobContext;
import com.phonemetra.turbo.compute.ComputeTask;
import com.phonemetra.turbo.compute.ComputeTaskFuture;
import com.phonemetra.turbo.internal.util.typedef.internal.S;
import com.phonemetra.turbo.internal.visor.VisorJob;
import com.phonemetra.turbo.lang.TurboSQLFuture;
import com.phonemetra.turbo.lang.TurboSQLInClosure;
import com.phonemetra.turbo.resources.JobContextResource;

/**
 *
 */
class VisorIdleVerifyJob<ResultT> extends VisorJob<VisorIdleVerifyTaskArg, ResultT> {
    /** */
    private static final long serialVersionUID = 0L;

    /** */
    private ComputeTaskFuture<ResultT> fut;

    /** Auto-inject job context. */
    @JobContextResource
    protected transient ComputeJobContext jobCtx;

    /** Task class for execution */
    private final Class<? extends ComputeTask<VisorIdleVerifyTaskArg, ResultT>> taskCls;

    /**
     * @param arg Argument.
     * @param debug Debug.
     * @param taskCls Task class for execution.
     */
    VisorIdleVerifyJob(
        VisorIdleVerifyTaskArg arg,
        boolean debug,
        Class<? extends ComputeTask<VisorIdleVerifyTaskArg, ResultT>> taskCls
    ) {
        super(arg, debug);
        this.taskCls = taskCls;
    }

    /** {@inheritDoc} */
    @Override protected ResultT run(VisorIdleVerifyTaskArg arg) throws TurboSQLException {
        if (fut == null) {
            fut = turboSQL.compute().executeAsync(taskCls, arg);

            if (!fut.isDone()) {
                jobCtx.holdcc();

                fut.listen((TurboSQLInClosure<TurboSQLFuture<ResultT>>)f -> jobCtx.callcc());

                return null;
            }
        }

        return fut.get();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(VisorIdleVerifyJob.class, this);
    }
}
