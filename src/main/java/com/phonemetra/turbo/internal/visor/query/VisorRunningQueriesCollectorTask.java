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

package com.phonemetra.turbo.internal.visor.query;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.phonemetra.turbo.TurboSQLException;
import com.phonemetra.turbo.compute.ComputeJobResult;
import com.phonemetra.turbo.internal.processors.query.GridRunningQueryInfo;
import com.phonemetra.turbo.internal.processors.task.GridInternal;
import com.phonemetra.turbo.internal.util.typedef.internal.U;
import com.phonemetra.turbo.internal.visor.VisorJob;
import com.phonemetra.turbo.internal.visor.VisorMultiNodeTask;
import org.jetbrains.annotations.Nullable;

/**
 * Task to collect currently running queries.
 */
@GridInternal
public class VisorRunningQueriesCollectorTask extends VisorMultiNodeTask<VisorRunningQueriesCollectorTaskArg, Map<UUID, Collection<VisorRunningQuery>>, Collection<VisorRunningQuery>> {
    /** */
    private static final long serialVersionUID = 0L;

    /** {@inheritDoc} */
    @Override protected VisorCollectRunningQueriesJob job(VisorRunningQueriesCollectorTaskArg arg) {
        return new VisorCollectRunningQueriesJob(arg, debug);
    }

    /** {@inheritDoc} */
    @Nullable @Override protected Map<UUID, Collection<VisorRunningQuery>> reduce0(List<ComputeJobResult> results) throws TurboSQLException {
        Map<UUID, Collection<VisorRunningQuery>> map = new HashMap<>();

        for (ComputeJobResult res : results)
            if (res.getException() == null) {
                Collection<VisorRunningQuery> queries = res.getData();

                map.put(res.getNode().id(), queries);
            }

        return map;
    }

    /**
     * Job to collect currently running queries from node.
     */
    private static class VisorCollectRunningQueriesJob
        extends VisorJob<VisorRunningQueriesCollectorTaskArg, Collection<VisorRunningQuery>> {
        /** */
        private static final long serialVersionUID = 0L;

        /**
         * Create job with specified argument.
         *
         * @param arg Job argument.
         * @param debug Flag indicating whether debug information should be printed into node log.
         */
        protected VisorCollectRunningQueriesJob(@Nullable VisorRunningQueriesCollectorTaskArg arg, boolean debug) {
            super(arg, debug);
        }

        /** {@inheritDoc} */
        @Override protected Collection<VisorRunningQuery> run(@Nullable VisorRunningQueriesCollectorTaskArg arg)
            throws TurboSQLException {
            assert arg != null;

            Collection<GridRunningQueryInfo> queries = turboSQL.context().query()
                .runningQueries(arg.getDuration());

            Collection<VisorRunningQuery> res = new ArrayList<>(queries.size());

            long curTime = U.currentTimeMillis();

            for (GridRunningQueryInfo qry : queries)
                res.add(new VisorRunningQuery(qry.id(), qry.query(), qry.queryType(), qry.schemaName(),
                    qry.startTime(), curTime - qry.startTime(),
                    qry.cancelable(), qry.local()));

            return res;
        }
    }
}
