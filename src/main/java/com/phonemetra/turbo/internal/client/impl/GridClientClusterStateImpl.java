/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonemetra.turbo.internal.client.impl;

import java.util.Collection;
import java.util.UUID;
import com.phonemetra.turbo.internal.client.GridClientClosedException;
import com.phonemetra.turbo.internal.client.GridClientClusterState;
import com.phonemetra.turbo.internal.client.GridClientException;
import com.phonemetra.turbo.internal.client.GridClientFuture;
import com.phonemetra.turbo.internal.client.GridClientNode;
import com.phonemetra.turbo.internal.client.GridClientPredicate;
import com.phonemetra.turbo.internal.client.balancer.GridClientLoadBalancer;
import com.phonemetra.turbo.internal.client.impl.connection.GridClientConnection;
import com.phonemetra.turbo.internal.client.impl.connection.GridClientConnectionResetException;

/**
 *
 */
public class GridClientClusterStateImpl extends GridClientAbstractProjection<GridClientClusterStateImpl>
    implements GridClientClusterState {
    /**
     * Creates projection with specified client.
     *
     * @param client Client instance to use.
     * @param nodes Collections of nodes included in this projection.
     * @param filter Node filter to be applied.
     * @param balancer Balancer to use.
     */
    public GridClientClusterStateImpl(
        GridClientImpl client,
        Collection<GridClientNode> nodes,
        GridClientPredicate<? super GridClientNode> filter,
        GridClientLoadBalancer balancer
    ) {
        super(client, nodes, filter, balancer);
    }

    /** {@inheritDoc} */
    @Override public void active(final boolean active) throws GridClientException {
        withReconnectHandling(new ClientProjectionClosure<Void>() {
            @Override public GridClientFuture apply(
                GridClientConnection conn, UUID nodeId
            ) throws GridClientConnectionResetException, GridClientClosedException {
                return conn.changeState(active, nodeId);
            }
        }).get();
    }

    /** {@inheritDoc} */
    @Override public boolean active() throws GridClientException {
        return withReconnectHandling(new ClientProjectionClosure<Boolean>() {
            @Override public GridClientFuture<Boolean> apply(
                GridClientConnection conn, UUID nodeId
            ) throws GridClientConnectionResetException, GridClientClosedException {
                return conn.currentState(nodeId);
            }
        }).get();
    }
}
