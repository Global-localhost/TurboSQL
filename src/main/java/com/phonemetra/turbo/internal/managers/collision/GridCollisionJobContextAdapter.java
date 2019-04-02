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

package com.phonemetra.turbo.internal.managers.collision;

import com.phonemetra.turbo.compute.ComputeJob;
import com.phonemetra.turbo.compute.ComputeJobContext;
import com.phonemetra.turbo.internal.GridJobSessionImpl;
import com.phonemetra.turbo.internal.processors.job.GridJobWorker;
import com.phonemetra.turbo.spi.collision.CollisionJobContext;

/**
 * Adapter for {@link com.phonemetra.turbo.spi.collision.CollisionJobContext}.
 */
public abstract class GridCollisionJobContextAdapter implements CollisionJobContext {
    /** */
    private final GridJobWorker jobWorker;

    /**
     * @param jobWorker Job worker instance.
     */
    protected GridCollisionJobContextAdapter(GridJobWorker jobWorker) {
        assert jobWorker != null;

        this.jobWorker = jobWorker;
    }

    /** {@inheritDoc} */
    @Override public GridJobSessionImpl getTaskSession() {
        return jobWorker.getSession();
    }

    /** {@inheritDoc} */
    @Override public ComputeJobContext getJobContext() {
        return jobWorker.getJobContext();
    }

    /**
     * @return Job worker.
     */
    public GridJobWorker getJobWorker() {
        return jobWorker;
    }

    /** {@inheritDoc} */
    @Override public ComputeJob getJob() {
        return jobWorker.getJob();
    }
}