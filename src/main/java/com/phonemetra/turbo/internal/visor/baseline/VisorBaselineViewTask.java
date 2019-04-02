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

package com.phonemetra.turbo.internal.visor.baseline;

import com.phonemetra.turbo.TurboSQLException;
import com.phonemetra.turbo.internal.cluster.TurboSQLClusterEx;
import com.phonemetra.turbo.internal.processors.cluster.baseline.autoadjust.BaselineAutoAdjustStatistic;
import com.phonemetra.turbo.internal.processors.task.GridInternal;
import com.phonemetra.turbo.internal.util.typedef.internal.S;
import com.phonemetra.turbo.internal.visor.VisorJob;
import com.phonemetra.turbo.internal.visor.VisorOneNodeTask;
import org.jetbrains.annotations.Nullable;

/**
 * Task that will collect information about baseline topology.
 */
@GridInternal
public class VisorBaselineViewTask extends VisorOneNodeTask<Void, VisorBaselineTaskResult> {
    /** */
    private static final long serialVersionUID = 0L;

    /** {@inheritDoc} */
    @Override protected VisorBaselineViewJob job(Void arg) {
        return new VisorBaselineViewJob(arg, debug);
    }

    /**
     * Job that will collect baseline topology information.
     */
    private static class VisorBaselineViewJob extends VisorJob<Void, VisorBaselineTaskResult> {
        /** */
        private static final long serialVersionUID = 0L;

        /**
         * @param arg Formal job argument.
         * @param debug Debug flag.
         */
        private VisorBaselineViewJob(Void arg, boolean debug) {
            super(arg, debug);
        }

        /** {@inheritDoc} */
        @Override protected VisorBaselineTaskResult run(@Nullable Void arg) throws TurboSQLException {
            TurboSQLClusterEx cluster = turboSQL.cluster();

            VisorBaselineAutoAdjustSettings autoAdjustSettings = new VisorBaselineAutoAdjustSettings(
                cluster.isBaselineAutoAdjustEnabled(),
                cluster.baselineAutoAdjustTimeout()
            );

            BaselineAutoAdjustStatistic adjustStatistic = turboSQL.context().cluster().baselineAutoAdjustStatistic();

            return new VisorBaselineTaskResult(
                turboSQL.cluster().active(),
                cluster.topologyVersion(),
                cluster.currentBaselineTopology(),
                cluster.forServers().nodes(),
                autoAdjustSettings,
                adjustStatistic.getBaselineAdjustTimeout(),
                adjustStatistic.getTaskState() == BaselineAutoAdjustStatistic.TaskState.IN_PROGRESS
            );
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(VisorBaselineViewJob.class, this);
        }
    }
}
