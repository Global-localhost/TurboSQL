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

package com.phonemetra.turbo.internal.visor.service;

import com.phonemetra.turbo.TurboSQLServices;
import com.phonemetra.turbo.internal.processors.task.GridInternal;
import com.phonemetra.turbo.internal.processors.task.GridVisorManagementTask;
import com.phonemetra.turbo.internal.util.typedef.internal.S;
import com.phonemetra.turbo.internal.visor.VisorJob;
import com.phonemetra.turbo.internal.visor.VisorOneNodeTask;

/**
 * Task for cancel services with specified name.
 */
@GridInternal
@GridVisorManagementTask
public class VisorCancelServiceTask extends VisorOneNodeTask<VisorCancelServiceTaskArg, Void> {
    /** */
    private static final long serialVersionUID = 0L;

    /** {@inheritDoc} */
    @Override protected VisorCancelServiceJob job(VisorCancelServiceTaskArg arg) {
        return new VisorCancelServiceJob(arg, debug);
    }

    /**
     * Job for cancel services with specified name.
     */
    private static class VisorCancelServiceJob extends VisorJob<VisorCancelServiceTaskArg, Void> {
        /** */
        private static final long serialVersionUID = 0L;

        /**
         * Create job with specified argument.
         *
         * @param arg Job argument.
         * @param debug Debug flag.
         */
        protected VisorCancelServiceJob(VisorCancelServiceTaskArg arg, boolean debug) {
            super(arg, debug);
        }

        /** {@inheritDoc} */
        @Override protected Void run(final VisorCancelServiceTaskArg arg) {
            TurboSQLServices services = turboSQL.services();

            services.cancel(arg.getName());

            return null;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(VisorCancelServiceJob.class, this);
        }
    }
}
