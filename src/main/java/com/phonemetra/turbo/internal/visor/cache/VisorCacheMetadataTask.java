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

import com.phonemetra.turbo.TurboSQLCheckedException;
import com.phonemetra.turbo.TurboSQLException;
import com.phonemetra.turbo.internal.processors.cache.TurboSQLInternalCache;
import com.phonemetra.turbo.internal.processors.cache.query.GridCacheSqlMetadata;
import com.phonemetra.turbo.internal.processors.task.GridInternal;
import com.phonemetra.turbo.internal.util.typedef.F;
import com.phonemetra.turbo.internal.util.typedef.internal.S;
import com.phonemetra.turbo.internal.util.typedef.internal.U;
import com.phonemetra.turbo.internal.visor.VisorJob;
import com.phonemetra.turbo.internal.visor.VisorOneNodeTask;

import static com.phonemetra.turbo.internal.visor.util.VisorTaskUtils.escapeName;

/**
 * Task to get cache SQL metadata.
 */
@GridInternal
public class VisorCacheMetadataTask extends VisorOneNodeTask<VisorCacheMetadataTaskArg, VisorCacheSqlMetadata> {
    /** */
    private static final long serialVersionUID = 0L;

    /** {@inheritDoc} */
    @Override protected VisorCacheMetadataJob job(VisorCacheMetadataTaskArg arg) {
        return new VisorCacheMetadataJob(arg, debug);
    }

    /**
     * Job to get cache SQL metadata.
     */
    private static class VisorCacheMetadataJob extends VisorJob<VisorCacheMetadataTaskArg, VisorCacheSqlMetadata> {
        /** */
        private static final long serialVersionUID = 0L;

        /**
         * @param arg Cache name to take metadata.
         * @param debug Debug flag.
         */
        private VisorCacheMetadataJob(VisorCacheMetadataTaskArg arg, boolean debug) {
            super(arg, debug);
        }

        /** {@inheritDoc} */
        @Override protected VisorCacheSqlMetadata run(VisorCacheMetadataTaskArg arg) {
            try {
                TurboSQLInternalCache<Object, Object> cache = turboSQL.cachex(arg.getCacheName());

                if (cache != null) {
                    GridCacheSqlMetadata meta = F.first(cache.context().queries().sqlMetadata());

                    if (meta != null)
                        return new VisorCacheSqlMetadata(meta);

                    return null;
                }

                throw new TurboSQLException("Cache not found: " + escapeName(arg.getCacheName()));
            }
            catch (TurboSQLCheckedException e) {
                throw U.convertException(e);
            }
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(VisorCacheMetadataJob.class, this);
        }
    }
}
