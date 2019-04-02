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

package com.phonemetra.turbo.internal;

import java.io.Serializable;
import java.util.Map;
import com.phonemetra.turbo.TurboSQLCheckedException;
import com.phonemetra.turbo.cluster.ClusterNode;
import com.phonemetra.turbo.lang.TurboSQLFuture;
import com.phonemetra.turbo.plugin.PluginProvider;
import com.phonemetra.turbo.plugin.PluginValidationException;
import com.phonemetra.turbo.spi.TurboSQLNodeValidationResult;
import com.phonemetra.turbo.spi.discovery.DiscoveryDataBag;
import com.phonemetra.turbo.spi.discovery.DiscoveryDataBag.GridDiscoveryData;
import com.phonemetra.turbo.spi.discovery.DiscoveryDataBag.JoiningNodeDiscoveryData;
import org.jetbrains.annotations.Nullable;

/**
 *
 */
public class GridPluginComponent implements GridComponent {
    /** */
    private final PluginProvider plugin;

    /**
     * @param plugin Plugin provider.
     */
    public GridPluginComponent(PluginProvider plugin) {
        this.plugin = plugin;
    }

    /**
     * @return Plugin instance.
     */
    public PluginProvider plugin() {
        return plugin;
    }

    /** {@inheritDoc} */
    @Override public void start() throws TurboSQLCheckedException {
        throw new UnsupportedOperationException();
    }

    /** {@inheritDoc} */
    @Override public void stop(boolean cancel) throws TurboSQLCheckedException {
        plugin.stop(cancel);
    }

    /** {@inheritDoc} */
    @Override public void onKernalStart(boolean active) throws TurboSQLCheckedException {
        plugin.onTurboSQLStart();
    }

    /** {@inheritDoc} */
    @Override public void onDisconnected(TurboSQLFuture<?> reconnectFut) {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public TurboSQLInternalFuture<?> onReconnected(boolean clusterRestarted) {
        return null;
    }

    /** {@inheritDoc} */
    @Override public void onKernalStop(boolean cancel) {
        plugin.onTurboSQLStop(cancel);
    }

    /** {@inheritDoc} */
    @Nullable @Override public DiscoveryDataExchangeType discoveryDataType() {
        return DiscoveryDataExchangeType.PLUGIN;
    }

    /** {@inheritDoc} */
    @Override public void collectJoiningNodeData(DiscoveryDataBag dataBag) {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public void collectGridNodeData(DiscoveryDataBag dataBag) {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public void onGridDataReceived(GridDiscoveryData data) {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public void onJoiningNodeDataReceived(JoiningNodeDiscoveryData data) {
        // No-op.
    }

    /** {@inheritDoc} */
    @Nullable @Override public TurboSQLNodeValidationResult validateNode(ClusterNode node) {
        return null;
    }

    /** {@inheritDoc} */
    @Nullable @Override public TurboSQLNodeValidationResult validateNode(ClusterNode node,
        JoiningNodeDiscoveryData discoData) {
        try {
            Map<String, Serializable> map = (Map<String, Serializable>)discoData.joiningNodeData();

            if (map != null)
                plugin.validateNewNode(node, map.get(plugin.name()));
            else
                plugin.validateNewNode(node, null);

            return null;
        }
        catch (PluginValidationException e) {
            return new TurboSQLNodeValidationResult(e.nodeId(), e.getMessage(), e.remoteMessage());
        }
    }

    /** {@inheritDoc} */
    @Override public void printMemoryStats() {
        // No-op.
    }
}