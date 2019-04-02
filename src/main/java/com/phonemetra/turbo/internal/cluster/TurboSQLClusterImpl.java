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

package com.phonemetra.turbo.internal.cluster;

import java.io.Externalizable;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.ObjectStreamException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import com.phonemetra.turbo.TurboSQLCheckedException;
import com.phonemetra.turbo.TurboSQLCluster;
import com.phonemetra.turbo.TurboSQLException;
import com.phonemetra.turbo.cluster.BaselineNode;
import com.phonemetra.turbo.cluster.ClusterGroup;
import com.phonemetra.turbo.cluster.ClusterGroupEmptyException;
import com.phonemetra.turbo.cluster.ClusterNode;
import com.phonemetra.turbo.cluster.ClusterStartNodeResult;
import com.phonemetra.turbo.configuration.TurboSQLConfiguration;
import com.phonemetra.turbo.events.ClusterActivationEvent;
import com.phonemetra.turbo.events.EventType;
import com.phonemetra.turbo.internal.GridKernalContext;
import com.phonemetra.turbo.internal.TurboSQLComponentType;
import com.phonemetra.turbo.internal.TurboSQLInternalFuture;
import com.phonemetra.turbo.internal.managers.discovery.DiscoCache;
import com.phonemetra.turbo.internal.processors.cluster.BaselineTopology;
import com.phonemetra.turbo.internal.util.future.GridCompoundFuture;
import com.phonemetra.turbo.internal.util.future.GridFinishedFuture;
import com.phonemetra.turbo.internal.util.future.GridFutureAdapter;
import com.phonemetra.turbo.internal.util.future.TurboSQLFutureImpl;
import com.phonemetra.turbo.internal.util.nodestart.TurboSQLRemoteStartSpecification;
import com.phonemetra.turbo.internal.util.nodestart.TurboSQLSshHelper;
import com.phonemetra.turbo.internal.util.nodestart.StartNodeCallable;
import com.phonemetra.turbo.internal.util.tostring.GridToStringExclude;
import com.phonemetra.turbo.internal.util.typedef.CI1;
import com.phonemetra.turbo.internal.util.typedef.F;
import com.phonemetra.turbo.internal.util.typedef.internal.A;
import com.phonemetra.turbo.internal.util.typedef.internal.CU;
import com.phonemetra.turbo.internal.util.typedef.internal.SB;
import com.phonemetra.turbo.internal.util.typedef.internal.U;
import com.phonemetra.turbo.lang.TurboSQLBiTuple;
import com.phonemetra.turbo.lang.TurboSQLFuture;
import com.phonemetra.turbo.lang.TurboSQLPredicate;
import com.phonemetra.turbo.lang.TurboSQLProductVersion;
import org.jetbrains.annotations.Nullable;

import static com.phonemetra.turbo.internal.TurboSQLNodeAttributes.ATTR_IPS;
import static com.phonemetra.turbo.internal.TurboSQLNodeAttributes.ATTR_MACS;
import static com.phonemetra.turbo.internal.util.nodestart.TurboSQLNodeStartUtils.parseFile;
import static com.phonemetra.turbo.internal.util.nodestart.TurboSQLNodeStartUtils.specifications;

/**
 *
 */
public class TurboSQLClusterImpl extends ClusterGroupAdapter implements TurboSQLClusterEx, Externalizable {
    /** */
    private static final long serialVersionUID = 0L;

    /** */
    private TurboSQLConfiguration cfg;

    /** Node local store. */
    @GridToStringExclude
    private ConcurrentMap nodeLoc;

    /** Client reconnect future. */
    private TurboSQLFuture<?> reconnecFut;

    /** Minimal TurboSQLProductVersion supporting BaselineTopology */
    private static final TurboSQLProductVersion MIN_BLT_SUPPORTING_VER = TurboSQLProductVersion.fromString("2.4.0");

    /** Distributed baseline configuration. */
    private DistributedBaselineConfiguration distributedBaselineConfiguration;

    /**
     * Required by {@link Externalizable}.
     */
    public TurboSQLClusterImpl() {
        // No-op.
    }

    /**
     * @param ctx Kernal context.
     */
    public TurboSQLClusterImpl(GridKernalContext ctx) {
        super(ctx, null, (TurboSQLPredicate<ClusterNode>)null);

        cfg = ctx.config();

        nodeLoc = new ClusterNodeLocalMapImpl(ctx);

        distributedBaselineConfiguration = new DistributedBaselineConfiguration(
            cfg, ctx.internalSubscriptionProcessor(), ctx.log(DistributedBaselineConfiguration.class)
        );
    }

    /** {@inheritDoc} */
    @Override public ClusterGroup forLocal() {
        guard();

        try {
            return new ClusterGroupAdapter(ctx, null, Collections.singleton(cfg.getNodeId()));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public ClusterNode localNode() {
        guard();

        try {
            ClusterNode node = ctx.discovery().localNode();

            assert node != null;

            return node;
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override public <K, V> ConcurrentMap<K, V> nodeLocalMap() {
        return nodeLoc;
    }

    /** {@inheritDoc} */
    @Override public boolean pingNode(UUID nodeId) {
        A.notNull(nodeId, "nodeId");

        guard();

        try {
            return ctx.discovery().pingNode(nodeId);
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public long topologyVersion() {
        guard();

        try {
            return ctx.discovery().topologyVersion();
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public Collection<ClusterNode> topology(long topVer) throws UnsupportedOperationException {
        guard();

        try {
            return ctx.discovery().topology(topVer);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public Collection<ClusterStartNodeResult> startNodes(File file,
        boolean restart,
        int timeout,
        int maxConn)
        throws TurboSQLException {
        try {
            return startNodesAsync0(file, restart, timeout, maxConn).get();
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public TurboSQLFuture<Collection<ClusterStartNodeResult>> startNodesAsync(File file, boolean restart,
        int timeout, int maxConn) throws TurboSQLException {
        return new TurboSQLFutureImpl<>(startNodesAsync0(file, restart, timeout, maxConn));
    }

    /** {@inheritDoc} */
    @Override public Collection<ClusterStartNodeResult> startNodes(Collection<Map<String, Object>> hosts,
        @Nullable Map<String, Object> dflts,
        boolean restart,
        int timeout,
        int maxConn)
        throws TurboSQLException {
        try {
            return startNodesAsync0(hosts, dflts, restart, timeout, maxConn).get();
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public TurboSQLFuture<Collection<ClusterStartNodeResult>> startNodesAsync(
        Collection<Map<String, Object>> hosts, @Nullable Map<String, Object> dflts,
        boolean restart, int timeout, int maxConn) throws TurboSQLException {
        return new TurboSQLFutureImpl<>(startNodesAsync0(hosts, dflts, restart, timeout, maxConn));
    }

    /** {@inheritDoc} */
    @Override public void stopNodes() throws TurboSQLException {
        guard();

        try {
            compute().execute(TurboSQLKillTask.class, false);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public void stopNodes(Collection<UUID> ids) throws TurboSQLException {
        guard();

        try {
            ctx.grid().compute(forNodeIds(ids)).execute(TurboSQLKillTask.class, false);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public void restartNodes() throws TurboSQLException {
        guard();

        try {
            compute().execute(TurboSQLKillTask.class, true);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public void restartNodes(Collection<UUID> ids) throws TurboSQLException {
        guard();

        try {
            ctx.grid().compute(forNodeIds(ids)).execute(TurboSQLKillTask.class, true);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public void resetMetrics() {
        guard();

        try {
            ctx.jobMetric().reset();
            ctx.io().resetMetrics();
            ctx.task().resetMetrics();
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public boolean active() {
        guard();

        try {
            return ctx.state().publicApiActiveState(true);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public void active(boolean active) {
        guard();

        try {
            ctx.state().changeGlobalState(active, baselineNodes(), false).get();
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
        finally {
            unguard();
        }
    }

    /** */
    private Collection<BaselineNode> baselineNodes() {
        Collection<ClusterNode> srvNodes = ctx.cluster().get().forServers().nodes();

        ArrayList baselineNodes = new ArrayList(srvNodes.size());

        for (ClusterNode clN : srvNodes)
            baselineNodes.add(clN);

        return baselineNodes;
    }

    /** {@inheritDoc} */
    @Nullable @Override public Collection<BaselineNode> currentBaselineTopology() {
        guard();

        try {
            BaselineTopology blt = ctx.state().clusterState().baselineTopology();

            return blt != null ? blt.currentBaseline() : null;
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public void setBaselineTopology(Collection<? extends BaselineNode> baselineTop) {
        guard();

        try {
            if (isInMemoryMode())
                return;

            validateBeforeBaselineChange(baselineTop);

            ctx.state().changeGlobalState(true, baselineTop, true).get();
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
        finally {
            unguard();
        }
    }

    /**
     * Sets baseline topology constructed from the cluster topology of the given version (the method succeeds only if
     * the cluster topology has not changed). All client and daemon nodes will be filtered out of the resulting
     * baseline.
     *
     * @param topVer Topology version to set.
     */
    public void triggerBaselineAutoAdjust(long topVer) {
        setBaselineTopology(topVer, true);
    }

    /** */
    private boolean isInMemoryMode() {
        return !CU.isPersistenceEnabled(cfg);
    }

    /**
     * Verifies all nodes in current cluster topology support BaselineTopology feature so compatibilityMode flag is
     * enabled to reset.
     *
     * @param discoCache
     */
    private void verifyBaselineTopologySupport(DiscoCache discoCache) {
        if (discoCache.minimumServerNodeVersion().compareTo(MIN_BLT_SUPPORTING_VER) < 0) {
            SB sb = new SB("Cluster contains nodes that don't support BaselineTopology: [");

            for (ClusterNode cn : discoCache.serverNodes()) {
                if (cn.version().compareTo(MIN_BLT_SUPPORTING_VER) < 0)
                    sb
                        .a("[")
                        .a(cn.consistentId())
                        .a(":")
                        .a(cn.version())
                        .a("], ");
            }

            sb.d(sb.length() - 2, sb.length());

            throw new TurboSQLException(sb.a("]").toString());
        }
    }

    /**
     * Executes validation checks of cluster state and BaselineTopology before changing BaselineTopology to new one.
     */
    private void validateBeforeBaselineChange(Collection<? extends BaselineNode> baselineTop) {
        verifyBaselineTopologySupport(ctx.discovery().discoCache());

        if (!ctx.state().clusterState().active())
            throw new TurboSQLException("Changing BaselineTopology on inactive cluster is not allowed.");

        if (baselineTop != null) {
            if (baselineTop.isEmpty())
                throw new TurboSQLException("BaselineTopology must contain at least one node.");

            Collection<Object> onlineNodes = onlineBaselineNodesRequestedForRemoval(baselineTop);

            if (onlineNodes != null) {
                if (!onlineNodes.isEmpty())
                    throw new TurboSQLException("Removing online nodes from BaselineTopology is not supported: " + onlineNodes);
            }
        }
    }

    /** */
    @Nullable private Collection<Object> onlineBaselineNodesRequestedForRemoval(
        Collection<? extends BaselineNode> newBlt) {
        BaselineTopology blt = ctx.state().clusterState().baselineTopology();
        Set<Object> bltConsIds;

        if (blt == null)
            return null;
        else
            bltConsIds = blt.consistentIds();

        ArrayList<Object> onlineNodesRequestedForRemoval = new ArrayList<>();

        Collection<Object> aliveNodesConsIds = getConsistentIds(ctx.discovery().aliveServerNodes());

        Collection<Object> newBltConsIds = getConsistentIds(newBlt);

        for (Object oldBltConsId : bltConsIds) {
            if (aliveNodesConsIds.contains(oldBltConsId)) {
                if (!newBltConsIds.contains(oldBltConsId))
                    onlineNodesRequestedForRemoval.add(oldBltConsId);
            }
        }

        return onlineNodesRequestedForRemoval;
    }

    /** */
    private Collection<Object> getConsistentIds(Collection<? extends BaselineNode> nodes) {
        ArrayList<Object> res = new ArrayList<>(nodes.size());

        for (BaselineNode n : nodes)
            res.add(n.consistentId());

        return res;
    }

    /** {@inheritDoc} */
    @Override public void setBaselineTopology(long topVer) {
        setBaselineTopology(topVer, false);
    }

    private void setBaselineTopology(long topVer, boolean isBaselineAutoAdjust) {
        guard();

        try {
            if (isInMemoryMode())
                return;

            Collection<ClusterNode> top = topology(topVer);

            if (top == null)
                throw new TurboSQLException("Topology version does not exist: " + topVer);

            Collection<BaselineNode> target = new ArrayList<>(top.size());

            for (ClusterNode node : top) {
                if (!node.isClient())
                    target.add(node);
            }

            validateBeforeBaselineChange(target);

            ctx.state().changeGlobalState(true, target, true, isBaselineAutoAdjust).get();
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public void enableStatistics(Collection<String> caches, boolean enabled) {
        guard();

        try {
            ctx.cache().enableStatistics(caches, enabled);
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public void clearStatistics(Collection<String> caches) {
        guard();

        try {
            ctx.cache().clearStatistics(caches);
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public void setTxTimeoutOnPartitionMapExchange(long timeout) {
        guard();

        try {
            ctx.cache().setTxTimeoutOnPartitionMapExchange(timeout);
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public TurboSQLCluster withAsync() {
        return new TurboSQLClusterAsyncImpl(this);
    }

    /** {@inheritDoc} */
    @Override public boolean enableWal(String cacheName) throws TurboSQLException {
        return changeWalMode(cacheName, true);
    }

    /** {@inheritDoc} */
    @Override public boolean disableWal(String cacheName) throws TurboSQLException {
        return changeWalMode(cacheName, false);
    }

    /**
     * Change WAL mode.
     *
     * @param cacheName Cache name.
     * @param enabled Enabled flag.
     * @return {@code True} if WAL mode was changed as a result of this call.
     */
    private boolean changeWalMode(String cacheName, boolean enabled) {
        A.notNull(cacheName, "cacheName");

        guard();

        try {
            return ctx.cache().changeWalMode(Collections.singleton(cacheName), enabled).get();
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public boolean isWalEnabled(String cacheName) {
        guard();

        try {
            return ctx.cache().walEnabled(cacheName);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public boolean isBaselineAutoAdjustEnabled() {
        return distributedBaselineConfiguration.isBaselineAutoAdjustEnabled();
    }

    /** {@inheritDoc} */
    @Override public void baselineAutoAdjustEnabled(boolean baselineAutoAdjustEnabled) {
        baselineAutoAdjustEnabledAsync(baselineAutoAdjustEnabled).get();
    }

    /**
     * @param baselineAutoAdjustEnabled Value of manual baseline control or auto adjusting baseline. {@code True} If
     * cluster in auto-adjust. {@code False} If cluster in manuale.
     * @return Future for await operation completion.
     */
    public TurboSQLFuture<?> baselineAutoAdjustEnabledAsync(boolean baselineAutoAdjustEnabled) {
        guard();

        try {
            return new TurboSQLFutureImpl<>(
                distributedBaselineConfiguration.updateBaselineAutoAdjustEnabledAsync(baselineAutoAdjustEnabled));
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public long baselineAutoAdjustTimeout() {
        return distributedBaselineConfiguration.getBaselineAutoAdjustTimeout();
    }

    /** {@inheritDoc} */
    @Override public void baselineAutoAdjustTimeout(long baselineAutoAdjustTimeout) {
        baselineAutoAdjustTimeoutAsync(baselineAutoAdjustTimeout).get();
    }

    /**
     * @param baselineAutoAdjustTimeout Value of time which we would wait before the actual topology change since last
     * server topology change (node join/left/fail).
     * @return Future for await operation completion.
     */
    public TurboSQLFuture<?> baselineAutoAdjustTimeoutAsync(long baselineAutoAdjustTimeout) {
        A.ensure(baselineAutoAdjustTimeout >= 0, "timeout should be positive or zero");

        guard();

        try {
            return new TurboSQLFutureImpl<>(
                distributedBaselineConfiguration.updateBaselineAutoAdjustTimeoutAsync(baselineAutoAdjustTimeout));
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public boolean isAsync() {
        return false;
    }

    /** {@inheritDoc} */
    @Override public <R> TurboSQLFuture<R> future() {
        throw new IllegalStateException("Asynchronous mode is not enabled.");
    }

    /**
     * @param file Configuration file.
     * @param restart Whether to stop existing nodes.
     * @param timeout Connection timeout.
     * @param maxConn Number of parallel SSH connections to one host.
     * @return Future with results.
     * @see TurboSQLCluster#startNodes(java.io.File, boolean, int, int)
     */
    TurboSQLInternalFuture<Collection<ClusterStartNodeResult>> startNodesAsync0(File file,
        boolean restart,
        int timeout,
        int maxConn) {
        A.notNull(file, "file");
        A.ensure(file.exists(), "file doesn't exist.");
        A.ensure(file.isFile(), "file is a directory.");

        try {
            TurboSQLBiTuple<Collection<Map<String, Object>>, Map<String, Object>> t = parseFile(file);

            return startNodesAsync0(t.get1(), t.get2(), restart, timeout, maxConn);
        }
        catch (TurboSQLCheckedException e) {
            return new GridFinishedFuture<>(e);
        }
    }

    /**
     * @param hosts Startup parameters.
     * @param dflts Default values.
     * @param restart Whether to stop existing nodes
     * @param timeout Connection timeout in milliseconds.
     * @param maxConn Number of parallel SSH connections to one host.
     * @return Future with results.
     * @see TurboSQLCluster#startNodes(java.util.Collection, java.util.Map, boolean, int, int)
     */
    TurboSQLInternalFuture<Collection<ClusterStartNodeResult>> startNodesAsync0(
        Collection<Map<String, Object>> hosts,
        @Nullable Map<String, Object> dflts,
        boolean restart,
        int timeout,
        int maxConn) {
        A.notNull(hosts, "hosts");

        guard();

        try {
            TurboSQLSshHelper sshHelper = TurboSQLComponentType.SSH.create(false);

            Map<String, Collection<TurboSQLRemoteStartSpecification>> specsMap = specifications(hosts, dflts);

            Map<String, ConcurrentLinkedQueue<StartNodeCallable>> runMap = new HashMap<>();

            int nodeCallCnt = 0;

            for (String host : specsMap.keySet()) {
                InetAddress addr;

                try {
                    addr = InetAddress.getByName(host);
                }
                catch (UnknownHostException e) {
                    throw new TurboSQLCheckedException("Invalid host name: " + host, e);
                }

                Collection<? extends ClusterNode> neighbors = null;

                if (addr.isLoopbackAddress())
                    neighbors = neighbors();
                else {
                    for (Collection<ClusterNode> p : U.neighborhood(nodes()).values()) {
                        ClusterNode node = F.first(p);

                        if (node.<String>attribute(ATTR_IPS).contains(addr.getHostAddress())) {
                            neighbors = p;

                            break;
                        }
                    }
                }

                int startIdx = 1;

                if (neighbors != null) {
                    if (restart && !neighbors.isEmpty()) {
                        try {
                            ctx.grid().compute(forNodes(neighbors)).execute(TurboSQLKillTask.class, false);
                        }
                        catch (ClusterGroupEmptyException ignored) {
                            // No-op, nothing to restart.
                        }
                    }
                    else
                        startIdx = neighbors.size() + 1;
                }

                ConcurrentLinkedQueue<StartNodeCallable> nodeRuns = new ConcurrentLinkedQueue<>();

                runMap.put(host, nodeRuns);

                for (TurboSQLRemoteStartSpecification spec : specsMap.get(host)) {
                    assert spec.host().equals(host);

                    for (int i = startIdx; i <= spec.nodes(); i++) {
                        nodeRuns.add(sshHelper.nodeStartCallable(spec, timeout));

                        nodeCallCnt++;
                    }
                }
            }

            // If there is nothing to start, return finished future with empty result.
            if (nodeCallCnt == 0)
                return new GridFinishedFuture<Collection<ClusterStartNodeResult>>(
                    Collections.<ClusterStartNodeResult>emptyList());

            // Exceeding max line width for readability.
            GridCompoundFuture<ClusterStartNodeResult, Collection<ClusterStartNodeResult>> fut =
                new GridCompoundFuture<>(CU.<ClusterStartNodeResult>objectsReducer());

            AtomicInteger cnt = new AtomicInteger(nodeCallCnt);

            // Limit maximum simultaneous connection number per host.
            for (ConcurrentLinkedQueue<StartNodeCallable> queue : runMap.values()) {
                for (int i = 0; i < maxConn; i++) {
                    if (!runNextNodeCallable(queue, fut, cnt))
                        break;
                }
            }

            return fut;
        }
        catch (TurboSQLCheckedException e) {
            return new GridFinishedFuture<>(e);
        }
        finally {
            unguard();
        }
    }

    /**
     * Gets the all grid nodes that reside on the same physical computer as local grid node. Local grid node is
     * excluded. <p> Detection of the same physical computer is based on comparing set of network interface MACs. If two
     * nodes have the same set of MACs, TurboSQL considers these nodes running on the same physical computer.
     *
     * @return Grid nodes that reside on the same physical computer as local grid node.
     */
    private Collection<ClusterNode> neighbors() {
        Collection<ClusterNode> neighbors = new ArrayList<>(1);

        String macs = localNode().attribute(ATTR_MACS);

        assert macs != null;

        for (ClusterNode n : forOthers(localNode()).nodes()) {
            if (macs.equals(n.attribute(ATTR_MACS)))
                neighbors.add(n);
        }

        return neighbors;
    }

    /**
     * Runs next callable from host node start queue.
     *
     * @param queue Queue of tasks to poll from.
     * @param comp Compound future that comprise all started node tasks.
     * @param cnt Atomic counter to check if all futures are added to compound future.
     * @return {@code True} if task was started, {@code false} if queue was empty.
     */
    private boolean runNextNodeCallable(final ConcurrentLinkedQueue<StartNodeCallable> queue,
        final GridCompoundFuture<ClusterStartNodeResult, Collection<ClusterStartNodeResult>>
            comp,
        final AtomicInteger cnt) {
        StartNodeCallable call = queue.poll();

        if (call == null)
            return false;

        TurboSQLInternalFuture<ClusterStartNodeResult> fut = ctx.closure().callLocalSafe(call, true);

        comp.add(fut);

        if (cnt.decrementAndGet() == 0)
            comp.markInitialized();

        fut.listen(new CI1<TurboSQLInternalFuture<ClusterStartNodeResult>>() {
            @Override public void apply(TurboSQLInternalFuture<ClusterStartNodeResult> f) {
                runNextNodeCallable(queue, comp, cnt);
            }
        });

        return true;
    }

    /**
     * Clears node local map.
     */
    public void clearNodeMap() {
        nodeLoc.clear();
    }

    /**
     * @param reconnecFut Reconnect future.
     */
    public void clientReconnectFuture(TurboSQLFuture<?> reconnecFut) {
        this.reconnecFut = reconnecFut;
    }

    /**
     * @return Baseline configuration.
     */
    public DistributedBaselineConfiguration baselineConfiguration() {
        return distributedBaselineConfiguration;
    }

    /** {@inheritDoc} */
    @Nullable @Override public TurboSQLFuture<?> clientReconnectFuture() {
        return reconnecFut;
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        ctx = (GridKernalContext)in.readObject();
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(ctx);
    }

    /** {@inheritDoc} */
    @Override protected Object readResolve() throws ObjectStreamException {
        return ctx.grid().cluster();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return "TurboSQLCluster [turboSQLInstanceName=" + ctx.turboSQLInstanceName() + ']';
    }
}