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

package com.phonemetra.turbo.internal.visor.tx;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import com.phonemetra.turbo.TurboSQLException;
import com.phonemetra.turbo.cluster.ClusterNode;
import com.phonemetra.turbo.compute.ComputeJobResult;
import com.phonemetra.turbo.internal.TurboSQLInternalFuture;
import com.phonemetra.turbo.internal.processors.cache.distributed.GridDistributedTxMapping;
import com.phonemetra.turbo.internal.processors.cache.distributed.dht.GridDhtTxLocal;
import com.phonemetra.turbo.internal.processors.cache.distributed.dht.GridDhtTxRemote;
import com.phonemetra.turbo.internal.processors.cache.distributed.near.GridNearTxLocal;
import com.phonemetra.turbo.internal.processors.cache.distributed.near.TurboSQLTxMappings;
import com.phonemetra.turbo.internal.processors.cache.transactions.TurboSQLInternalTx;
import com.phonemetra.turbo.internal.processors.cache.transactions.TurboSQLTxManager;
import com.phonemetra.turbo.internal.processors.cache.transactions.TurboSQLTxRemoteEx;
import com.phonemetra.turbo.internal.processors.cache.version.GridCacheVersion;
import com.phonemetra.turbo.internal.processors.task.GridInternal;
import com.phonemetra.turbo.internal.util.future.GridFinishedFuture;
import com.phonemetra.turbo.internal.util.typedef.F;
import com.phonemetra.turbo.internal.util.typedef.internal.U;
import com.phonemetra.turbo.internal.visor.VisorJob;
import com.phonemetra.turbo.internal.visor.VisorMultiNodeTask;
import com.phonemetra.turbo.internal.visor.VisorTaskArgument;
import com.phonemetra.turbo.lang.TurboSQLBiClosure;
import com.phonemetra.turbo.lang.TurboSQLClosure;
import com.phonemetra.turbo.lang.TurboSQLPredicate;
import com.phonemetra.turbo.transactions.TransactionState;
import org.jetbrains.annotations.Nullable;

import static com.phonemetra.turbo.transactions.TransactionState.COMMITTED;
import static com.phonemetra.turbo.transactions.TransactionState.COMMITTING;

/**
 *
 */
@GridInternal
public class VisorTxTask extends VisorMultiNodeTask<VisorTxTaskArg, Map<ClusterNode, VisorTxTaskResult>, VisorTxTaskResult> {
    /** */
    private static final long serialVersionUID = 0L;

    /** {@inheritDoc} */
    @Override protected VisorJob<VisorTxTaskArg, VisorTxTaskResult> job(VisorTxTaskArg arg) {
        return new VisorTxJob(arg, debug);
    }

    /** {@inheritDoc} */
    @Override protected Collection<UUID> jobNodes(VisorTaskArgument<VisorTxTaskArg> arg) {
        final VisorTxTaskArg taskArg = arg.getArgument();

        if (taskArg.getConsistentIds() != null) {
            return F.transform(turboSQL.cluster().forPredicate(new TurboSQLPredicate<ClusterNode>() {
                @Override public boolean apply(ClusterNode node) {
                    return taskArg.getConsistentIds().contains((String)node.consistentId().toString());
                }
            }).nodes(), new TurboSQLClosure<ClusterNode, UUID>() {
                @Override public UUID apply(ClusterNode node) {
                    return node.id();
                }
            });
        }

        if (taskArg.getProjection() == VisorTxProjection.SERVER) {
            return F.transform(turboSQL.cluster().forServers().nodes(), new TurboSQLClosure<ClusterNode, UUID>() {
                @Override public UUID apply(ClusterNode node) {
                    return node.id();
                }
            });
        }

        if (taskArg.getProjection() == VisorTxProjection.CLIENT) {
            return F.transform(turboSQL.cluster().forClients().nodes(), new TurboSQLClosure<ClusterNode, UUID>() {
                @Override public UUID apply(ClusterNode node) {
                    return node.id();
                }
            });
        }

        return F.transform(turboSQL.cluster().nodes(), new TurboSQLClosure<ClusterNode, UUID>() {
            @Override public UUID apply(ClusterNode node) {
                return node.id();
            }
        });
    }

    /** {@inheritDoc} */
    @Nullable @Override protected Map<ClusterNode, VisorTxTaskResult> reduce0(List<ComputeJobResult> results) throws TurboSQLException {
        Map<ClusterNode, VisorTxTaskResult> mapRes = new TreeMap<>();

        Map<UUID, ClusterNode> nodeMap = new HashMap<>();

        for (ComputeJobResult result : results) {
            VisorTxTaskResult data = result.getData();

            if (data == null || data.getInfos().isEmpty())
                continue;

            mapRes.put(result.getNode(), data);

            nodeMap.put(result.getNode().id(), result.getNode());
        }

        // Remove local and remote txs for which near txs are present.
        for (VisorTxTaskResult result : mapRes.values()) {
            List<VisorTxInfo> infos = result.getInfos();

            Iterator<VisorTxInfo> it = infos.iterator();

            while (it.hasNext()) {
                VisorTxInfo info = it.next();

                if (!info.getXid().equals(info.getNearXid())) {
                    UUID nearNodeId = info.getMasterNodeIds().iterator().next();

                    // Try find id.
                    ClusterNode node = nodeMap.get(nearNodeId);

                    if (node == null)
                        continue;

                    VisorTxTaskResult res0 = mapRes.get(node);

                    if (res0 == null)
                        continue;

                    boolean exists = false;

                    for (VisorTxInfo txInfo : res0.getInfos()) {
                        if (txInfo.getXid().equals(info.getNearXid())) {
                            exists = true;

                            break;
                        }
                    }

                    if (exists)
                        it.remove();
                }
            }
        }

        return mapRes;
    }

    /**
     *
     */
    private static class VisorTxJob extends VisorJob<VisorTxTaskArg, VisorTxTaskResult> {
        /** */
        private static final long serialVersionUID = 0L;

        /** */
        private static final int DEFAULT_LIMIT = 50;

        /** */
        private static final TxKillClosure NEAR_KILL_CLOSURE = new NearKillClosure();

        /** */
        private static final TxKillClosure LOCAL_KILL_CLOSURE = new LocalKillClosure();

        /** */
        private static final TxKillClosure REMOTE_KILL_CLOSURE = new RemoteKillClosure();

        /**
         * @param arg Formal job argument.
         * @param debug Debug flag.
         */
        private VisorTxJob(VisorTxTaskArg arg, boolean debug) {
            super(arg, debug);
        }

        /** {@inheritDoc} */
        @Override protected VisorTxTaskResult run(@Nullable VisorTxTaskArg arg) throws TurboSQLException {
            if (arg == null)
                return new VisorTxTaskResult(Collections.emptyList());

            TurboSQLTxManager tm = turboSQL.context().cache().context().tm();

            Collection<TurboSQLInternalTx> transactions = tm.activeTransactions();

            List<VisorTxInfo> infos = new ArrayList<>();

            int limit = arg.getLimit() == null ? DEFAULT_LIMIT : arg.getLimit();

            Pattern lbMatch = null;

            if (arg.getLabelRegex() != null) {
                try {
                    lbMatch = Pattern.compile(arg.getLabelRegex());
                }
                catch (PatternSyntaxException ignored) {
                    // No-op.
                }
            }

            for (TurboSQLInternalTx locTx : transactions) {
                if (arg.getXid() != null && !locTx.xid().toString().equals(arg.getXid()))
                    continue;

                if (arg.getState() != null && locTx.state() != arg.getState())
                    continue;

                long duration = U.currentTimeMillis() - locTx.startTime();

                if (arg.getMinDuration() != null && duration < arg.getMinDuration())
                    continue;

                String lb = null;
                int size = 0;
                Collection<UUID> mappings = null;
                TxKillClosure killClo = null;

                // This filter conditions have meaning only for near txs, so we skip dht because it will never match.
                boolean skip = arg.getMinSize() != null || lbMatch != null;

                if (locTx instanceof GridNearTxLocal) {
                    GridNearTxLocal locTx0 = (GridNearTxLocal)locTx;

                    lb = locTx0.label();

                    if (lbMatch != null && !lbMatch.matcher(lb == null ? "null" : lb).matches())
                        continue;

                    mappings = new ArrayList<>();

                    if (locTx0.mappings() != null) {
                        TurboSQLTxMappings txMappings = locTx0.mappings();

                        for (GridDistributedTxMapping mapping :
                            txMappings.single() ? Collections.singleton(txMappings.singleMapping()) : txMappings.mappings()) {
                            if (mapping == null)
                                continue;

                            mappings.add(mapping.primary().id());

                            size += mapping.entries().size(); // Entries are not synchronized so no visibility guaranties for size.
                        }
                    }

                    if (arg.getMinSize() != null && size < arg.getMinSize())
                        continue;

                    killClo = NEAR_KILL_CLOSURE;
                }
                else if (locTx instanceof GridDhtTxLocal) {
                    if (skip)
                        continue;

                    GridDhtTxLocal locTx0 = (GridDhtTxLocal)locTx;

                    Map<UUID, GridDistributedTxMapping> dhtMap = U.field(locTx0, "dhtMap");

                    mappings = new ArrayList<>();

                    if (dhtMap != null) {
                        for (GridDistributedTxMapping mapping : dhtMap.values()) {
                            mappings.add(mapping.primary().id());

                            size += mapping.entries().size();
                        }
                    }

                    Map<UUID, GridDistributedTxMapping> nearMap = U.field(locTx, "nearMap");

                    if (nearMap != null) {
                        for (GridDistributedTxMapping mapping : nearMap.values()) {
                            mappings.add(mapping.primary().id());

                            size += mapping.entries().size();
                        }
                    }

                    killClo = LOCAL_KILL_CLOSURE;
                }
                else if (locTx instanceof GridDhtTxRemote) {
                    if (skip)
                        continue;

                    GridDhtTxRemote locTx0 = (GridDhtTxRemote)locTx;

                    size = locTx0.readMap().size() + locTx.writeMap().size();

                    killClo = REMOTE_KILL_CLOSURE;
                }

                infos.add(new VisorTxInfo(locTx.xid(), locTx.startTime(), duration, locTx.isolation(), locTx.concurrency(),
                    locTx.timeout(), lb, mappings, locTx.state(),
                    size, locTx.nearXidVersion().asGridUuid(), locTx.masterNodeIds(), locTx.topologyVersionSnapshot()));

                if (arg.getOperation() == VisorTxOperation.KILL)
                    killClo.apply(locTx, tm);

                if (infos.size() == limit)
                    break;
            }

            Comparator<VisorTxInfo> comp = TxDurationComparator.INSTANCE;

            if (arg.getSortOrder() != null) {
                switch (arg.getSortOrder()) {
                    case DURATION:
                        comp = TxDurationComparator.INSTANCE;

                        break;

                    case SIZE:
                        comp = TxSizeComparator.INSTANCE;

                        break;

                    case START_TIME:
                        comp = TxStartTimeComparator.INSTANCE;

                        break;

                    default:
                }
            }

            Collections.sort(infos, comp);

            return new VisorTxTaskResult(infos);
        }
    }

    /**
     *
     */
    private static class TxStartTimeComparator implements Comparator<VisorTxInfo> {
        /** Instance. */
        public static final TxStartTimeComparator INSTANCE = new TxStartTimeComparator();

        /** {@inheritDoc} */
        @Override public int compare(VisorTxInfo o1, VisorTxInfo o2) {
            return Long.compare(o2.getStartTime(), o1.getStartTime());
        }
    }

    /**
     *
     */
    private static class TxDurationComparator implements Comparator<VisorTxInfo> {
        /** Instance. */
        public static final TxDurationComparator INSTANCE = new TxDurationComparator();

        /** {@inheritDoc} */
        @Override public int compare(VisorTxInfo o1, VisorTxInfo o2) {
            return Long.compare(o2.getDuration(), o1.getDuration());
        }
    }

    /**
     *
     */
    private static class TxSizeComparator implements Comparator<VisorTxInfo> {
        /** Instance. */
        public static final TxSizeComparator INSTANCE = new TxSizeComparator();

        /** {@inheritDoc} */
        @Override public int compare(VisorTxInfo o1, VisorTxInfo o2) {
            return Long.compare(o2.getSize(), o1.getSize());
        }
    }

    /** Type shortcut. */
    private interface TxKillClosure extends
        TurboSQLBiClosure<TurboSQLInternalTx, TurboSQLTxManager, TurboSQLInternalFuture<TurboSQLInternalTx>> {
    }

    /** Kills near tx. */
    private static class NearKillClosure implements TxKillClosure {
        /** */
        private static final long serialVersionUID = 0L;

        /** {@inheritDoc} */
        @Override public TurboSQLInternalFuture<TurboSQLInternalTx> apply(TurboSQLInternalTx tx, TurboSQLTxManager tm) {
            return tx.isRollbackOnly() || tx.state() == COMMITTING || tx.state() == COMMITTED ?
                new GridFinishedFuture<>() : ((GridNearTxLocal)tx).rollbackNearTxLocalAsync(false, false);
        }
    }

    /** Kills local tx. */
    private static class LocalKillClosure implements TxKillClosure {
        /** */
        private static final long serialVersionUID = 0L;

        /** {@inheritDoc} */
        @Override public TurboSQLInternalFuture<TurboSQLInternalTx> apply(TurboSQLInternalTx tx, TurboSQLTxManager tm) {
            return tx.isRollbackOnly() || tx.state() == COMMITTING || tx.state() == COMMITTED ?
                new GridFinishedFuture<>() : ((GridDhtTxLocal)tx).rollbackDhtLocalAsync();
        }
    }

    /** Kills remote tx. */
    private static class RemoteKillClosure implements TxKillClosure {
        /** */
        private static final long serialVersionUID = 0L;

        /** {@inheritDoc} */
        @Override public TurboSQLInternalFuture<TurboSQLInternalTx> apply(TurboSQLInternalTx tx, TurboSQLTxManager tm) {
            TurboSQLTxRemoteEx remote = (TurboSQLTxRemoteEx)tx;

            if (tx.isRollbackOnly() || tx.state() == COMMITTING || tx.state() == COMMITTED)
                return new GridFinishedFuture<>();

            if (tx.state() == TransactionState.PREPARED)
                remote.doneRemote(tx.xidVersion(),
                    Collections.<GridCacheVersion>emptyList(),
                    Collections.<GridCacheVersion>emptyList(),
                    Collections.<GridCacheVersion>emptyList());

            return tx.rollbackAsync();
        }
    }
}
