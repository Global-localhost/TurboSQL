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

package com.phonemetra.turbo.internal.visor.igfs;

import com.phonemetra.turbo.TurboSQLCheckedException;
import com.phonemetra.turbo.TurboSQLException;
import com.phonemetra.turbo.internal.processors.igfs.IgfsEx;
import com.phonemetra.turbo.internal.processors.task.GridInternal;
import com.phonemetra.turbo.internal.util.typedef.internal.S;
import com.phonemetra.turbo.internal.util.typedef.internal.U;
import com.phonemetra.turbo.internal.visor.VisorJob;
import com.phonemetra.turbo.internal.visor.VisorOneNodeTask;

/**
 * Task to set IGFS instance sampling state.
 */
@GridInternal
public class VisorIgfsSamplingStateTask extends VisorOneNodeTask<VisorIgfsSamplingStateTaskArg, Void> {
    /** */
    private static final long serialVersionUID = 0L;

    /**
     * Job that perform parsing of IGFS profiler logs.
     */
    private static class VisorIgfsSamplingStateJob extends VisorJob<VisorIgfsSamplingStateTaskArg, Void> {
        /** */
        private static final long serialVersionUID = 0L;

        /**
         * Create job with given argument.
         *
         * @param arg Job argument.
         * @param debug Debug flag.
         */
        private VisorIgfsSamplingStateJob(VisorIgfsSamplingStateTaskArg arg, boolean debug) {
            super(arg, debug);
        }

        /** {@inheritDoc} */
        @Override protected Void run(VisorIgfsSamplingStateTaskArg arg) {
            try {
                ((IgfsEx)turboSQL.fileSystem(arg.getName())).globalSampling(arg.isEnabled());

                return null;
            }
            catch (IllegalArgumentException iae) {
                throw new TurboSQLException("Failed to set sampling state for IGFS: " + arg.getName(), iae);
            }
            catch (TurboSQLCheckedException e) {
                throw U.convertException(e);
            }
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(VisorIgfsSamplingStateJob.class, this);
        }
    }

    /** {@inheritDoc} */
    @Override protected VisorIgfsSamplingStateJob job(VisorIgfsSamplingStateTaskArg arg) {
        return new VisorIgfsSamplingStateJob(arg, debug);
    }
}
