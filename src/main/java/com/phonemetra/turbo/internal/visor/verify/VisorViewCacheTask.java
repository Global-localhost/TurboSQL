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

import com.phonemetra.turbo.TurboSQLException;
import com.phonemetra.turbo.internal.processors.cache.verify.ViewCacheClosure;
import com.phonemetra.turbo.internal.processors.task.GridInternal;
import com.phonemetra.turbo.internal.util.typedef.internal.S;
import com.phonemetra.turbo.internal.visor.VisorJob;
import com.phonemetra.turbo.internal.visor.VisorOneNodeTask;
import org.jetbrains.annotations.Nullable;

/**
 *
 */
@GridInternal
public class VisorViewCacheTask extends VisorOneNodeTask<VisorViewCacheTaskArg, VisorViewCacheTaskResult> {
    /** */
    private static final long serialVersionUID = 0L;

    /** {@inheritDoc} */
    @Override protected VisorJob<VisorViewCacheTaskArg, VisorViewCacheTaskResult> job(VisorViewCacheTaskArg arg) {
        return new VisorViewCacheJob(arg, debug);
    }

    /**
     *
     */
    private static class VisorViewCacheJob extends VisorJob<VisorViewCacheTaskArg, VisorViewCacheTaskResult> {
        /** */
        private static final long serialVersionUID = 0L;

        /**
         * @param arg Argument.
         * @param debug Debug.
         */
        protected VisorViewCacheJob(@Nullable VisorViewCacheTaskArg arg, boolean debug) {
            super(arg, debug);
        }

        /** {@inheritDoc} */
        @Override protected VisorViewCacheTaskResult run(@Nullable VisorViewCacheTaskArg arg) throws TurboSQLException {
            try {
                ViewCacheClosure clo = new ViewCacheClosure(arg.regex(), arg.command());

                turboSQL.context().resource().injectGeneric(clo);

                return new VisorViewCacheTaskResult(clo.call());
            }
            catch (Exception e) {
                throw new TurboSQLException(e);
            }
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(VisorViewCacheJob.class, this);
        }
    }
}
