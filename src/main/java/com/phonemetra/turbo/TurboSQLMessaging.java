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

package com.phonemetra.turbo;

import java.util.Collection;
import java.util.UUID;
import com.phonemetra.turbo.cluster.ClusterGroup;
import com.phonemetra.turbo.cluster.ClusterGroupEmptyException;
import com.phonemetra.turbo.configuration.TurboSQLConfiguration;
import com.phonemetra.turbo.lang.TurboSQLAsyncSupport;
import com.phonemetra.turbo.lang.TurboSQLAsyncSupported;
import com.phonemetra.turbo.lang.TurboSQLBiPredicate;
import com.phonemetra.turbo.lang.TurboSQLFuture;
import org.jetbrains.annotations.Nullable;

/**
 * Provides functionality for topic-based message exchange among nodes defined by {@link #clusterGroup()}.
 * Users can send ordered and unordered messages to various topics. Note that same topic name
 * cannot be reused between ordered and unordered messages.
 * <p>
 * Instance of {@code TurboSQLMessaging} is obtained from {@link TurboSQL} as follows:
 * <pre class="brush:java">
 * TurboSQL turboSQL = Ignition.turboSQL();
 *
 * // Messaging instance spanning all cluster nodes.
 * TurboSQLMessaging m = turboSQL.message();
 * </pre>
 * You can also obtain an instance of messaging facade over a specific cluster group:
 * <pre class="brush:java">
 * // Cluster group over remote nodes (excluding the local node).
 * ClusterGroup remoteNodes = turboSQL.cluster().forRemotes();
 *
 * // Messaging instance spanning all remote cluster nodes.
 * TurboSQLMessaging m = turboSQL.message(remoteNodes);
 * </pre>
 * <p>
 * There are {@code 2} ways to subscribe to message listening, {@code local} and {@code remote}.
 * <p>
 * Local subscription, defined by {@link #localListen(Object, TurboSQLBiPredicate)} method, will add
 * a listener for a given topic on local node only. This listener will be notified whenever any
 * node within the underlying cluster group will send a message for a given topic to this node. Local listen
 * subscription will happen regardless of whether the local node belongs to this cluster group or not.
 * <p>
 * Remote subscription, defined by {@link #remoteListen(Object, TurboSQLBiPredicate)}, will add a
 * message listener for a given topic to all nodes in the underlying cluster group (possibly including this node if
 * it belongs to the cluster group as well). This means that any node within this cluster group can send
 * a message for a given topic and all nodes within the cluster group will receive listener notifications.
 * <h1 class="header">Ordered vs Unordered</h1>
 * TurboSQL allows for sending ordered messages (see {@link #sendOrdered(Object, Object, long)}), i.e.
 * messages will be received in the same order they were sent. Ordered messages have a {@code timeout}
 * parameter associated with them which specifies how long an out-of-order message will stay in a queue,
 * waiting for messages that are ordered ahead of it to arrive. If timeout expires, then all ordered
 * messages for a given topic that have not arrived yet will be skipped. When (and if) expired messages
 * actually do arrive, they will be ignored.
 */
public interface TurboSQLMessaging extends TurboSQLAsyncSupport {
    /**
     * Gets the cluster group to which this {@code GridMessaging} instance belongs.
     *
     * @return Cluster group to which this {@code GridMessaging} instance belongs.
     */
    public ClusterGroup clusterGroup();

    /**
     * Sends given message with specified topic to the nodes in the underlying cluster group.
     * <p>
     * By default all local listeners will be executed in the calling thread, or if you use
     * {@link #withAsync()}, listeners will execute in public thread pool (in this case it is user's
     * responsibility to implement back-pressure and limit number of concurrently executed async messages).
     *
     * @param topic Topic to send to, {@code null} for default topic.
     * @param msg Message to send.
     * @throws TurboSQLException If failed to send a message to any of the nodes.
     * @throws ClusterGroupEmptyException Thrown in case when cluster group is empty.
     */
    public void send(@Nullable Object topic, Object msg) throws TurboSQLException;

    /**
     * Sends given messages with the specified topic to the nodes in the underlying cluster group.
     * <p>
     * By default all local listeners will be executed in the calling thread, or if you use
     * {@link #withAsync()}, listeners will execute in public thread pool (in this case it is user's
     * responsibility to implement back-pressure and limit number of concurrently executed async messages).
     *
     * @param topic Topic to send to, {@code null} for default topic.
     * @param msgs Messages to send. Order of the sending is undefined. If the method produces
     *      the exception none or some messages could have been sent already.
     * @throws TurboSQLException If failed to send a message to any of the nodes.
     * @throws ClusterGroupEmptyException Thrown in case when cluster group is empty.
     */
    public void send(@Nullable Object topic, Collection<?> msgs) throws TurboSQLException;

    /**
     * Sends given message with specified topic to the nodes in the underlying cluster group. Messages sent with
     * this method will arrive in the same order they were sent. Note that if a topic is used
     * for ordered messages, then it cannot be reused for non-ordered messages. Note that local listeners
     * are always executed in public thread pool, no matter default or {@link #withAsync()} mode is used.
     * <p>
     * The {@code timeout} parameter specifies how long an out-of-order message will stay in a queue,
     * waiting for messages that are ordered ahead of it to arrive. If timeout expires, then all ordered
     * messages that have not arrived before this message will be skipped. When (and if) expired messages
     * actually do arrive, they will be ignored.
     *
     * @param topic Topic to send to, {@code null} for default topic.
     * @param msg Message to send.
     * @param timeout Message timeout in milliseconds, {@code 0} for default
     *      which is {@link TurboSQLConfiguration#getNetworkTimeout()}.
     * @throws TurboSQLException If failed to send a message to any of the nodes.
     * @throws ClusterGroupEmptyException Thrown in case when cluster group is empty.
     */
    public void sendOrdered(@Nullable Object topic, Object msg, long timeout) throws TurboSQLException;

    /**
     * Adds local listener for given topic on local node only. This listener will be notified whenever any
     * node within the cluster group will send a message for a given topic to this node. Local listen
     * subscription will happen regardless of whether local node belongs to this cluster group or not.
     *
     * @param topic Topic to subscribe to.
     * @param p Predicate that is called on each received message. If predicate returns {@code false},
     *      then it will be unsubscribed from any further notifications.
     */
    public void localListen(@Nullable Object topic, TurboSQLBiPredicate<UUID, ?> p);

    /**
     * Unregisters local listener for given topic on local node only.
     *
     * @param topic Topic to unsubscribe from.
     * @param p Listener predicate.
     */
    public void stopLocalListen(@Nullable Object topic, TurboSQLBiPredicate<UUID, ?> p);

    /**
     * Adds a message listener for a given topic to all nodes in the cluster group (possibly including
     * this node if it belongs to the cluster group as well). This means that any node within this cluster
     * group can send a message for a given topic and all nodes within the cluster group will receive
     * listener notifications.
     *
     * @param topic Topic to subscribe to, {@code null} means default topic.
     * @param p Predicate that is called on each node for each received message. If predicate returns {@code false},
     *      then it will be unsubscribed from any further notifications.
     * @return {@code Operation ID} that can be passed to {@link #stopRemoteListen(UUID)} method to stop listening.
     * @throws TurboSQLException If failed to add listener.
     */
    @TurboSQLAsyncSupported
    public UUID remoteListen(@Nullable Object topic, TurboSQLBiPredicate<UUID, ?> p) throws TurboSQLException;

    /**
     * Asynchronously adds a message listener for a given topic to all nodes in the cluster group (possibly including
     * this node if it belongs to the cluster group as well). This means that any node within this cluster
     * group can send a message for a given topic and all nodes within the cluster group will receive
     * listener notifications.
     *
     * @param topic Topic to subscribe to, {@code null} means default topic.
     * @param p Predicate that is called on each node for each received message. If predicate returns {@code false},
     *      then it will be unsubscribed from any further notifications.
     * @return a Future representing pending completion of the operation. The completed future contains
     *      {@code Operation ID} that can be passed to {@link #stopRemoteListen(UUID)} method to stop listening.
     * @throws TurboSQLException If failed to add listener.
     */
    public TurboSQLFuture<UUID> remoteListenAsync(@Nullable Object topic, TurboSQLBiPredicate<UUID, ?> p)
        throws TurboSQLException;

    /**
     * Unregisters all listeners identified with provided operation ID on all nodes in the cluster group.
     * <p>
     * Supports asynchronous execution (see {@link TurboSQLAsyncSupport}).
     *
     * @param opId Listen ID that was returned from {@link #remoteListen(Object, TurboSQLBiPredicate)} method.
     * @throws TurboSQLException If failed to unregister listeners.
     */
    @TurboSQLAsyncSupported
    public void stopRemoteListen(UUID opId) throws TurboSQLException;

    /**
     * Asynchronously unregisters all listeners identified with provided operation ID on all nodes in the cluster group.
     *
     * @param opId Listen ID that was returned from {@link #remoteListen(Object, TurboSQLBiPredicate)} method.
     * @return a Future representing pending completion of the operation.
     * @throws TurboSQLException If failed to unregister listeners.
     */
    public TurboSQLFuture<Void> stopRemoteListenAsync(UUID opId) throws TurboSQLException;

    /** {@inheritDoc} */
    @Deprecated
    @Override TurboSQLMessaging withAsync();
}