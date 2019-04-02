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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.phonemetra.turbo.TurboSQLException;
import com.phonemetra.turbo.compute.ComputeJobResult;
import com.phonemetra.turbo.internal.processors.task.GridInternal;
import com.phonemetra.turbo.internal.visor.VisorOneNodeTask;
import org.jetbrains.annotations.Nullable;

/**
 * Task that collect cache metrics from all nodes.
 */
@GridInternal
public class VisorCacheConfigurationCollectorTask
    extends VisorOneNodeTask<VisorCacheConfigurationCollectorTaskArg, Map<String, VisorCacheConfiguration>> {
    /** */
    private static final long serialVersionUID = 0L;

    /** {@inheritDoc} */
    @Override protected VisorCacheConfigurationCollectorJob job(VisorCacheConfigurationCollectorTaskArg arg) {
        return new VisorCacheConfigurationCollectorJob(arg, debug);
    }

    /** {@inheritDoc} */
    @Override protected @Nullable Map<String, VisorCacheConfiguration> reduce0(
        List<ComputeJobResult> results
    ) throws TurboSQLException {
        if (results == null)
            return null;

        Map<String, VisorCacheConfiguration> map = new HashMap<>();

        List<Exception> resultsExceptions = null;

        for (ComputeJobResult res : results) {
            if (res.getException() == null)
                map.putAll(res.getData());
            else {
                if (resultsExceptions == null)
                    resultsExceptions = new ArrayList<>(results.size());

                resultsExceptions.add(new TurboSQLException("Job failed on node: " + res.getNode().id(), res.getException()));
            }
        }

        if (resultsExceptions != null) {
            TurboSQLException e = new TurboSQLException("Reduce failed because of job failed on some nodes");

            for (Exception ex : resultsExceptions)
                e.addSuppressed(ex);

            throw e;
        }

        return map;
    }
}
