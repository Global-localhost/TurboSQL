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

package com.phonemetra.turbo.events;

import java.util.Collection;
import com.phonemetra.turbo.TurboSQLEvents;
import com.phonemetra.turbo.cluster.BaselineNode;
import com.phonemetra.turbo.cluster.ClusterNode;
import com.phonemetra.turbo.configuration.TurboSQLConfiguration;
import com.phonemetra.turbo.lang.TurboSQLPredicate;

/**
 * Cluster activation event.
 * <p>
 * Grid events are used for notification about what happens within the grid. Note that by
 * design TurboSQL keeps all events generated on the local node locally and it provides
 * APIs for performing a distributed queries across multiple nodes:
 * <ul>
 *      <li>
 *          {@link TurboSQLEvents#remoteQuery(TurboSQLPredicate, long, int...)} -
 *          asynchronously querying events occurred on the nodes specified, including remote nodes.
 *      </li>
 *      <li>
 *          {@link TurboSQLEvents#localQuery(TurboSQLPredicate, int...)} -
 *          querying only local events stored on this local node.
 *      </li>
 *      <li>
 *          {@link TurboSQLEvents#localListen(TurboSQLPredicate, int...)} -
 *          listening to local grid events (events from remote nodes not included).
 *      </li>
 * </ul>
 * User can also wait for events using method {@link TurboSQLEvents#waitForLocal(TurboSQLPredicate, int...)}.
 * <h1 class="header">Events and Performance</h1>
 * Note that by default all events in TurboSQL are enabled and therefore generated and stored
 * by whatever event storage SPI is configured. TurboSQL can and often does generate thousands events per seconds
 * under the load and therefore it creates a significant additional load on the system. If these events are
 * not needed by the application this load is unnecessary and leads to significant performance degradation.
 * <p>
 * It is <b>highly recommended</b> to enable only those events that your application logic requires
 * by using {@link TurboSQLConfiguration#getIncludeEventTypes()} method in TurboSQL configuration. Note that certain
 * events are required for TurboSQL's internal operations and such events will still be generated but not stored by
 * event storage SPI if they are disabled in TurboSQL configuration.
 * @see EventType#EVT_CLUSTER_ACTIVATED
 * @see EventType#EVT_CLUSTER_DEACTIVATED
 */
public class ClusterActivationEvent extends EventAdapter {
    /** */
    private static final long serialVersionUID = 0L;

    /** Baseline nodes. */
    private final Collection<BaselineNode> baselineNodes;

    /**
     * Creates deployment event with given parameters.
     *
     * @param node Node.
     * @param msg Optional event message.
     * @param type Event type.
     * @param baselineNodes Baseline nodes.
     */
    public ClusterActivationEvent(ClusterNode node, String msg, int type, Collection<BaselineNode> baselineNodes) {
        super(node, msg, type);

        assert baselineNodes != null;

        this.baselineNodes = baselineNodes;
    }

    /**
     * Gets baseline nodes.
     *
     * @return Baseline nodes.
     */
    public Collection<BaselineNode> baselineNodes() {
        return baselineNodes;
    }
}
