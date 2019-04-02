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

package com.phonemetra.turbo.igfs.mapreduce;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.phonemetra.turbo.TurboSQL;
import com.phonemetra.turbo.TurboSQLException;
import com.phonemetra.turbo.TurboSQLFileSystem;
import com.phonemetra.turbo.cluster.ClusterNode;
import com.phonemetra.turbo.compute.ComputeJob;
import com.phonemetra.turbo.compute.ComputeTaskAdapter;
import com.phonemetra.turbo.igfs.IgfsBlockLocation;
import com.phonemetra.turbo.igfs.IgfsFile;
import com.phonemetra.turbo.igfs.IgfsPath;
import com.phonemetra.turbo.internal.TurboSQLKernal;
import com.phonemetra.turbo.internal.processors.igfs.IgfsProcessorAdapter;
import com.phonemetra.turbo.internal.util.typedef.internal.U;
import com.phonemetra.turbo.resources.TurboSQLInstanceResource;
import org.jetbrains.annotations.Nullable;

/**
 * IGFS task which can be executed on the grid using one of {@code TurboSQLFs.execute()} methods. Essentially IGFS task
 * is regular {@link com.phonemetra.turbo.compute.ComputeTask} with different map logic. Instead of implementing
 * {@link com.phonemetra.turbo.compute.ComputeTask#map(List, Object)} method to split task into jobs, you must implement
 * {@link IgfsTask#createJob(com.phonemetra.turbo.igfs.IgfsPath, IgfsFileRange, IgfsTaskArgs)} method.
 * <p>
 * Each file participating in IGFS task is split into {@link IgfsFileRange}s first. Normally range is a number of
 * consequent bytes located on a single node (see {@code IgfssGroupDataBlocksKeyMapper}). In case maximum range size
 * is provided (either through {@link com.phonemetra.turbo.configuration.FileSystemConfiguration#getMaximumTaskRangeLength()} or {@code TurboSQLFs.execute()}
 * argument), then ranges could be further divided into smaller chunks.
 * <p>
 * Once file is split into ranges, each range is passed to {@code IgfsTask.createJob()} method in order to create a
 * {@link IgfsJob}.
 * <p>
 * Finally all generated jobs are sent to Grid nodes for execution.
 * <p>
 * As with regular {@code ComputeTask} you can define your own logic for results handling and reduce step.
 * <p>
 * Here is an example of such a task:
 * <pre name="code" class="java">
 * public class WordCountTask extends IgfsTask&lt;String, Integer&gt; {
 *     &#64;Override
 *     public IgfsJob createJob(IgfsPath path, IgfsFileRange range, IgfsTaskArgs&lt;T&gt; args) throws TurboSQLCheckedException {
 *         // New job will be created for each range within each file.
 *         // We pass user-provided argument (which is essentially a word to look for) to that job.
 *         return new WordCountJob(args.userArgument());
 *     }
 *
 *     // Aggregate results into one compound result.
 *     public Integer reduce(List&lt;ComputeJobResult&gt; results) throws TurboSQLCheckedException {
 *         Integer total = 0;
 *
 *         for (ComputeJobResult res : results) {
 *             Integer cnt = res.getData();
 *
 *             // Null can be returned for non-existent file in case we decide to ignore such situations.
 *             if (cnt != null)
 *                 total += cnt;
 *         }
 *
 *         return total;
 *     }
 * }
 * </pre>
 */
public abstract class IgfsTask<T, R> extends ComputeTaskAdapter<IgfsTaskArgs<T>, R> {
    /** */
    private static final long serialVersionUID = 0L;

    /** Injected grid. */
    @TurboSQLInstanceResource
    private TurboSQL turboSQL;

    /** {@inheritDoc} */
    @Nullable @Override public final Map<? extends ComputeJob, ClusterNode> map(List<ClusterNode> subgrid,
        @Nullable IgfsTaskArgs<T> args) {
        assert turboSQL != null;
        assert args != null;

        TurboSQLFileSystem fs = turboSQL.fileSystem(args.igfsName());
        IgfsProcessorAdapter igfsProc = ((TurboSQLKernal) turboSQL).context().igfs();

        Map<ComputeJob, ClusterNode> splitMap = new HashMap<>();

        Map<UUID, ClusterNode> nodes = mapSubgrid(subgrid);

        for (IgfsPath path : args.paths()) {
            IgfsFile file = fs.info(path);

            if (file == null) {
                if (args.skipNonExistentFiles())
                    continue;
                else
                    throw new TurboSQLException("Failed to process IGFS file because it doesn't exist: " + path);
            }

            Collection<IgfsBlockLocation> aff = fs.affinity(path, 0, file.length(), args.maxRangeLength());

            long totalLen = 0;

            for (IgfsBlockLocation loc : aff) {
                ClusterNode node = null;

                for (UUID nodeId : loc.nodeIds()) {
                    node = nodes.get(nodeId);

                    if (node != null)
                        break;
                }

                if (node == null)
                    throw new TurboSQLException("Failed to find any of block affinity nodes in subgrid [loc=" + loc +
                        ", subgrid=" + subgrid + ']');

                IgfsJob job = createJob(path, new IgfsFileRange(file.path(), loc.start(), loc.length()), args);

                if (job != null) {
                    ComputeJob jobImpl = igfsProc.createJob(job, fs.name(), file.path(), loc.start(),
                        loc.length(), args.recordResolver());

                    splitMap.put(jobImpl, node);
                }

                totalLen += loc.length();
            }

            assert totalLen == file.length();
        }

        return splitMap;
    }

    /**
     * Callback invoked during task map procedure to create job that will process specified split
     * for IGFS file.
     *
     * @param path Path.
     * @param range File range based on consecutive blocks. This range will be further
     *      realigned to record boundaries on destination node.
     * @param args Task argument.
     * @return IGFS job. If {@code null} is returned, the passed in file range will be skipped.
     * @throws TurboSQLException If job creation failed.
     */
    @Nullable public abstract IgfsJob createJob(IgfsPath path, IgfsFileRange range,
        IgfsTaskArgs<T> args) throws TurboSQLException;

    /**
     * Maps list by node ID.
     *
     * @param subgrid Subgrid.
     * @return Map.
     */
    private Map<UUID, ClusterNode> mapSubgrid(Collection<ClusterNode> subgrid) {
        Map<UUID, ClusterNode> res = U.newHashMap(subgrid.size());

        for (ClusterNode node : subgrid)
            res.put(node.id(), node);

        return res;
    }
}