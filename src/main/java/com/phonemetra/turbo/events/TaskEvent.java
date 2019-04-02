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

import java.util.UUID;
import com.phonemetra.turbo.cluster.ClusterNode;
import com.phonemetra.turbo.internal.util.typedef.internal.S;
import com.phonemetra.turbo.internal.util.typedef.internal.U;
import com.phonemetra.turbo.lang.TurboSQLUuid;
import org.jetbrains.annotations.Nullable;

/**
 * Grid task event.
 * <p>
 * Grid events are used for notification about what happens within the grid. Note that by
 * design TurboSQL keeps all events generated on the local node locally and it provides
 * APIs for performing a distributed queries across multiple nodes:
 * <ul>
 *      <li>
 *          {@link com.phonemetra.turbo.TurboSQLEvents#remoteQuery(com.phonemetra.turbo.lang.TurboSQLPredicate, long, int...)} -
 *          asynchronously querying events occurred on the nodes specified, including remote nodes.
 *      </li>
 *      <li>
 *          {@link com.phonemetra.turbo.TurboSQLEvents#localQuery(com.phonemetra.turbo.lang.TurboSQLPredicate, int...)} -
 *          querying only local events stored on this local node.
 *      </li>
 *      <li>
 *          {@link com.phonemetra.turbo.TurboSQLEvents#localListen(com.phonemetra.turbo.lang.TurboSQLPredicate, int...)} -
 *          listening to local grid events (events from remote nodes not included).
 *      </li>
 * </ul>
 * User can also wait for events using method {@link com.phonemetra.turbo.TurboSQLEvents#waitForLocal(com.phonemetra.turbo.lang.TurboSQLPredicate, int...)}.
 * <h1 class="header">Events and Performance</h1>
 * Note that by default all events in TurboSQL are enabled and therefore generated and stored
 * by whatever event storage SPI is configured. TurboSQL can and often does generate thousands events per seconds
 * under the load and therefore it creates a significant additional load on the system. If these events are
 * not needed by the application this load is unnecessary and leads to significant performance degradation.
 * <p>
 * It is <b>highly recommended</b> to enable only those events that your application logic requires
 * by using {@link com.phonemetra.turbo.configuration.TurboSQLConfiguration#getIncludeEventTypes()} method in TurboSQL configuration. Note that certain
 * events are required for TurboSQL's internal operations and such events will still be generated but not stored by
 * event storage SPI if they are disabled in TurboSQL configuration.
 * @see EventType#EVT_TASK_FAILED
 * @see EventType#EVT_TASK_FINISHED
 * @see EventType#EVT_TASK_REDUCED
 * @see EventType#EVT_TASK_STARTED
 * @see EventType#EVT_TASK_SESSION_ATTR_SET
 * @see EventType#EVT_TASK_TIMEDOUT
 * @see EventType#EVTS_TASK_EXECUTION
 */
public class TaskEvent extends EventAdapter {
    /** */
    private static final long serialVersionUID = 0L;

    /** */
    private final String taskName;

    /** */
    private final String taskClsName;

    /** */
    private final TurboSQLUuid sesId;

    /** */
    private final boolean internal;

    /**  */
    private final UUID subjId;

    /** {@inheritDoc} */
    @Override public String shortDisplay() {
        return name() + ": taskName=" + taskName;
    }

    /**
     * Creates task event with given parameters.
     *
     * @param node Node.
     * @param msg Optional message.
     * @param type Event type.
     * @param sesId Task session ID.
     * @param taskName Task name.
     * @param subjId Subject ID.
     */
    public TaskEvent(ClusterNode node, String msg, int type, TurboSQLUuid sesId, String taskName, String taskClsName,
        boolean internal, @Nullable UUID subjId) {
        super(node, msg, type);

        this.sesId = sesId;
        this.taskName = taskName;
        this.taskClsName = taskClsName;
        this.internal = internal;
        this.subjId = subjId;
    }

    /**
     * Gets name of the task that triggered the event.
     *
     * @return Name of the task that triggered the event.
     */
    public String taskName() {
        return taskName;
    }

    /**
     * Gets name of task class that triggered this event.
     *
     * @return Name of task class that triggered the event.
     */
    public String taskClassName() {
        return taskClsName;
    }

    /**
     * Gets session ID of the task that triggered the event.
     *
     * @return Session ID of the task that triggered the event.
     */
    public TurboSQLUuid taskSessionId() {
        return sesId;
    }

    /**
     * Returns {@code true} if task is created by TurboSQL and is used for system needs.
     *
     * @return {@code True} if task is created by TurboSQL and is used for system needs.
     */
    public boolean internal() {
        return internal;
    }

    /**
     * Gets security subject ID initiated this task event, if available. This property
     * is not available for GridEventType#EVT_TASK_SESSION_ATTR_SET task event.
     * <p>
     * Subject ID will be set either to node ID or client ID initiated
     * task execution.
     *
     * @return Subject ID.
     */
    @Nullable public UUID subjectId() {
        return subjId;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(TaskEvent.class, this,
            "nodeId8", U.id8(node().id()),
            "msg", message(),
            "type", name(),
            "tstamp", timestamp());
    }
}