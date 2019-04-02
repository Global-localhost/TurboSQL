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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.phonemetra.turbo.TurboSQLException;
import com.phonemetra.turbo.Ignition;
import com.phonemetra.turbo.compute.ComputeJobResult;
import com.phonemetra.turbo.configuration.CacheConfiguration;
import com.phonemetra.turbo.configuration.NearCacheConfiguration;
import com.phonemetra.turbo.internal.processors.task.GridInternal;
import com.phonemetra.turbo.internal.processors.task.GridVisorManagementTask;
import com.phonemetra.turbo.internal.util.typedef.F;
import com.phonemetra.turbo.internal.util.typedef.internal.S;
import com.phonemetra.turbo.internal.visor.VisorJob;
import com.phonemetra.turbo.internal.visor.VisorMultiNodeTask;
import com.phonemetra.turbo.internal.visor.util.VisorTaskUtils;
import org.jetbrains.annotations.Nullable;

/**
 * Task that start cache or near cache with specified configuration.
 */
@GridInternal
@GridVisorManagementTask
public class VisorCacheStartTask extends VisorMultiNodeTask<VisorCacheStartTaskArg, Map<UUID, TurboSQLException>, Void> {
    /** */
    private static final long serialVersionUID = 0L;

    /** {@inheritDoc} */
    @Override protected VisorCacheStartJob job(VisorCacheStartTaskArg arg) {
        return new VisorCacheStartJob(arg, debug);
    }

    /** {@inheritDoc} */
    @Nullable @Override protected Map<UUID, TurboSQLException> reduce0(List<ComputeJobResult> results) throws TurboSQLException {
        Map<UUID, TurboSQLException> map = new HashMap<>();

        for (ComputeJobResult res : results)
            if (res.getException() != null)
                map.put(res.getNode().id(), res.getException());

        return map;
    }

    /**
     * Job that start cache or near cache with specified configuration.
     */
    private static class VisorCacheStartJob extends VisorJob<VisorCacheStartTaskArg, Void> {
        /** */
        private static final long serialVersionUID = 0L;

        /**
         * Create job.
         *
         * @param arg Contains cache name and XML configurations of cache.
         * @param debug Debug flag.
         */
        private VisorCacheStartJob(VisorCacheStartTaskArg arg, boolean debug) {
            super(arg, debug);
        }

        /** {@inheritDoc} */
        @Override protected Void run(VisorCacheStartTaskArg arg) throws TurboSQLException {
            String cfg = arg.getConfiguration();

            assert !F.isEmpty(cfg);

            try (ByteArrayInputStream bais = new ByteArrayInputStream(cfg.getBytes())) {
                if (arg.isNear()) {
                    NearCacheConfiguration nearCfg = Ignition.loadSpringBean(bais, "nearCacheConfiguration");

                    turboSQL.getOrCreateNearCache(VisorTaskUtils.unescapeName(arg.getName()), nearCfg);
                }
                else {
                    CacheConfiguration cacheCfg = Ignition.loadSpringBean(bais, "cacheConfiguration");

                    turboSQL.getOrCreateCache(cacheCfg);
                }
            }
            catch (IOException e) {
                throw new  TurboSQLException(e);
            }

            return null;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(VisorCacheStartJob.class, this);
        }
    }
}
