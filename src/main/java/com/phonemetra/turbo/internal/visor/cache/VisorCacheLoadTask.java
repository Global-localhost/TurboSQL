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

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;
import javax.cache.expiry.ExpiryPolicy;
import com.phonemetra.turbo.TurboSQLCache;
import com.phonemetra.turbo.cache.CachePeekMode;
import com.phonemetra.turbo.internal.processors.task.GridInternal;
import com.phonemetra.turbo.internal.processors.task.GridVisorManagementTask;
import com.phonemetra.turbo.internal.util.typedef.internal.S;
import com.phonemetra.turbo.internal.util.typedef.internal.U;
import com.phonemetra.turbo.internal.visor.VisorJob;
import com.phonemetra.turbo.internal.visor.VisorOneNodeTask;

/**
 * Task to loads caches.
 */
@GridInternal
@GridVisorManagementTask
public class VisorCacheLoadTask extends
    VisorOneNodeTask<VisorCacheLoadTaskArg, Map<String, Integer>> {
    /** */
    private static final long serialVersionUID = 0L;

    /** {@inheritDoc} */
    @Override protected VisorCachesLoadJob job(VisorCacheLoadTaskArg arg) {
        return new VisorCachesLoadJob(arg, debug);
    }

    /** Job that load caches. */
    private static class VisorCachesLoadJob extends
        VisorJob<VisorCacheLoadTaskArg, Map<String, Integer>> {
        /** */
        private static final long serialVersionUID = 0L;

        /**
         * @param arg Cache names, ttl and loader arguments.
         * @param debug Debug flag.
         */
        private VisorCachesLoadJob(VisorCacheLoadTaskArg arg, boolean debug) {
            super(arg, debug);
        }

        /** {@inheritDoc} */
        @Override protected Map<String, Integer> run(VisorCacheLoadTaskArg arg) {
            Set<String> cacheNames = arg.getCacheNames();
            long ttl = arg.getTtl();
            Object[] ldrArgs = arg.getLoaderArguments();

            assert cacheNames != null && !cacheNames.isEmpty();

            Map<String, Integer> res = U.newHashMap(cacheNames.size());

            ExpiryPolicy plc = null;

            for (String cacheName : cacheNames) {
                TurboSQLCache cache = turboSQL.cache(cacheName);

                if (cache == null)
                    throw new IllegalStateException("Failed to find cache for name: " + cacheName);

                if (ttl > 0) {
                    if (plc == null)
                        plc = new CreatedExpiryPolicy(new Duration(TimeUnit.MILLISECONDS, ttl));

                    cache = cache.withExpiryPolicy(plc);
                }

                cache.loadCache(null, ldrArgs);

                res.put(cacheName, cache.size(CachePeekMode.PRIMARY));
            }

            return res;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(VisorCachesLoadJob.class, this);
        }
    }
}
