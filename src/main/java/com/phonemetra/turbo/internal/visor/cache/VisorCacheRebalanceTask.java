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

package com.phonemetra.turbo.internal.visor.cache;

import java.util.ArrayList;
import java.util.Collection;
import com.phonemetra.turbo.TurboSQLCheckedException;
import com.phonemetra.turbo.internal.TurboSQLInternalFuture;
import com.phonemetra.turbo.internal.processors.cache.TurboSQLInternalCache;
import com.phonemetra.turbo.internal.processors.task.GridInternal;
import com.phonemetra.turbo.internal.processors.task.GridVisorManagementTask;
import com.phonemetra.turbo.internal.util.typedef.internal.S;
import com.phonemetra.turbo.internal.util.typedef.internal.U;
import com.phonemetra.turbo.internal.visor.VisorJob;
import com.phonemetra.turbo.internal.visor.VisorOneNodeTask;

/**
 * Pre-loads caches. Made callable just to conform common pattern.
 */
@GridInternal
@GridVisorManagementTask
public class VisorCacheRebalanceTask extends VisorOneNodeTask<VisorCacheRebalanceTaskArg, Void> {
    /** */
    private static final long serialVersionUID = 0L;

    /** {@inheritDoc} */
    @Override protected VisorCachesRebalanceJob job(VisorCacheRebalanceTaskArg arg) {
        return new VisorCachesRebalanceJob(arg, debug);
    }

    /**
     * Job that rebalance caches.
     */
    private static class VisorCachesRebalanceJob extends VisorJob<VisorCacheRebalanceTaskArg, Void> {
        /** */
        private static final long serialVersionUID = 0L;

        /**
         * @param arg Caches names.
         * @param debug Debug flag.
         */
        private VisorCachesRebalanceJob(VisorCacheRebalanceTaskArg arg, boolean debug) {
            super(arg, debug);
        }

        /** {@inheritDoc} */
        @Override protected Void run(VisorCacheRebalanceTaskArg arg) {
            try {
                Collection<TurboSQLInternalFuture<?>> futs = new ArrayList<>();

                for (TurboSQLInternalCache c : turboSQL.cachesx()) {
                    if (arg.getCacheNames().contains(c.name()))
                        futs.add(c.rebalance());
                }

                for (TurboSQLInternalFuture f : futs)
                    f.get();

                return null;
            }
            catch (TurboSQLCheckedException e) {
                throw U.convertException(e);
            }
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(VisorCachesRebalanceJob.class, this);
        }
    }
}