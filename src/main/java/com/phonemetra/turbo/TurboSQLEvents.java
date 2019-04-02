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
import java.util.List;
import java.util.UUID;
import com.phonemetra.turbo.cluster.ClusterGroup;
import com.phonemetra.turbo.events.Event;
import com.phonemetra.turbo.lang.TurboSQLAsyncSupport;
import com.phonemetra.turbo.lang.TurboSQLAsyncSupported;
import com.phonemetra.turbo.lang.TurboSQLBiPredicate;
import com.phonemetra.turbo.lang.TurboSQLFuture;
import com.phonemetra.turbo.lang.TurboSQLPredicate;
import org.jetbrains.annotations.Nullable;

/**
 * Provides functionality for local and remote event notifications on nodes defined by {@link #clusterGroup()}.
 * There are {@code 2} ways to subscribe to event listening, {@code local} and {@code remote}.
 * <p>
 * Instance of {@code TurboSQLEvents} is obtained from {@link TurboSQL} as follows:
 * <pre class="brush:java">
 * TurboSQL turboSQL = Ignition.turboSQL();
 *
 * TurboSQLEvents evts = turboSQL.events();
 * </pre>
 * You can also obtain an instance of the events facade over a specific cluster group:
 * <pre class="brush:java">
 * // Cluster group over remote nodes (excluding the local node).
 * ClusterGroup remoteNodes = turboSQL.cluster().forRemotes();
 *
 * // Events instance spanning all remote cluster nodes.
 * TurboSQLEvents evts = turboSQL.events(remoteNodes);
 * </pre>
 * <p>
 * Local subscription, defined by {@link #localListen(TurboSQLPredicate, int...)} method, will add
 * a listener for specified events on local node only. This listener will be notified whenever any
 * of subscribed events happen on local node regardless of whether local node belongs to underlying
 * cluster group or not.
 * <p>
 * Remote subscription, defined by {@link #remoteListen(TurboSQLBiPredicate, TurboSQLPredicate, int...)}, will add an
 * event listener for specified events on all nodes in the cluster group (possibly including local node if
 * it belongs to the cluster group as well). All cluster group nodes will then be notified of the subscribed events.
 * If the events pass the remote event filter, the events will be sent to local node for local listener notification.
 * <p>
 * Note that by default, all events in TurboSQL are disabled for performance reasons. You must only enable
 * events that you need within your logic. You can enable/disable events either by calling {@link #enableLocal(int...)}
 * or {@link #disableLocal(int...)} methods, or from XML configuration.
 * For example, you can enable all cache events as follows:
 * <pre name="code" class="xml">
 * &lt;property name="includeEventTypes"&gt;
 *     &lt;util:constant static-field="com.phonemetra.turbo.events.TurboSQLEventType.EVTS_CACHE"/&gt;
 * &lt;/property&gt;
 * </pre>
 */
public interface TurboSQLEvents extends TurboSQLAsyncSupport {
    /**
     * Gets cluster group to which this {@code TurboSQLEvents} instance belongs.
     *
     * @return Cluster group to which this {@code TurboSQLEvents} instance belongs.
     */
    public ClusterGroup clusterGroup();

    /**
     * Queries nodes in this cluster group for events using passed in predicate filter for event
     * selection.
     *
     * @param p Predicate filter used to query events on remote nodes.
     * @param timeout Maximum time to wait for result, {@code 0} to wait forever.
     * @param types Event types to be queried.
     * @return Collection of grid events returned from specified nodes.
     * @throws TurboSQLException If query failed.
     */
    @TurboSQLAsyncSupported
    public <T extends Event> List<T> remoteQuery(TurboSQLPredicate<T> p, long timeout, @Nullable int... types)
        throws TurboSQLException;

    /**
     * Asynchronously queries nodes in this cluster group for events using passed in predicate filter for event
     * selection.
     *
     * @param p Predicate filter used to query events on remote nodes.
     * @param timeout Maximum time to wait for result, {@code 0} to wait forever.
     * @param types Event types to be queried.
     * @return a Future representing pending completion of the query. The completed future contains
     *      collection of grid events returned from specified nodes.
     * @throws TurboSQLException If query failed.
     */
    public <T extends Event> TurboSQLFuture<List<T>> remoteQueryAsync(TurboSQLPredicate<T> p, long timeout,
        @Nullable int... types) throws TurboSQLException;

    /**
     * Adds event listener for specified events to all nodes in the cluster group (possibly including
     * local node if it belongs to the cluster group as well). This means that all events occurring on
     * any node within this cluster group that pass remote filter will be sent to local node for
     * local listener notifications.
     * <p>
     * The listener can be unsubscribed automatically if local node stops, if {@code locLsnr} callback
     * returns {@code false} or if {@link #stopRemoteListen(UUID)} or {@link #stopRemoteListenAsync(UUID)} are called.
     *
     * @param locLsnr Listener callback that is called on local node. If {@code null}, this events will be handled
     *      on remote nodes by passed in {@code rmtFilter}.
     * @param rmtFilter Filter callback that is called on remote node. Only events that pass the remote filter
     *      will be sent to local node. If {@code null}, all events of specified types will
     *      be sent to local node. This remote filter can be used to pre-handle events remotely,
     *      before they are passed in to local callback. It will be auto-unsubsribed on the node
     *      where event occurred in case if it returns {@code false}.
     * @param types Types of events to listen for. If not provided, all events that pass the
     *      provided remote filter will be sent to local node.
     * @param <T> Type of the event.
     * @return {@code Operation ID} that can be passed to {@link #stopRemoteListen(UUID)} or
     * {@link #stopRemoteListenAsync(UUID)} methods to stop listening.
     * @throws TurboSQLException If failed to add listener.
     */
    @TurboSQLAsyncSupported
    public <T extends Event> UUID remoteListen(@Nullable TurboSQLBiPredicate<UUID, T> locLsnr,
        @Nullable TurboSQLPredicate<T> rmtFilter,
        @Nullable int... types)
        throws TurboSQLException;

    /**
     * Asynchronously adds event listener for specified events to all nodes in the cluster group (possibly including
     * local node if it belongs to the cluster group as well). This means that all events occurring on
     * any node within this cluster group that pass remote filter will be sent to local node for
     * local listener notifications.
     * <p>
     * The listener can be unsubscribed automatically if local node stops, if {@code locLsnr} callback
     * returns {@code false} or if {@link #stopRemoteListen(UUID)} or {@link #stopRemoteListenAsync(UUID)} are called.
     *
     * @param <T> Type of the event.
     * @param locLsnr Listener callback that is called on local node. If {@code null}, this events will be handled
     *      on remote nodes by passed in {@code rmtFilter}.
     * @param rmtFilter Filter callback that is called on remote node. Only events that pass the remote filter
     *      will be sent to local node. If {@code null}, all events of specified types will
     *      be sent to local node. This remote filter can be used to pre-handle events remotely,
     *      before they are passed in to local callback. It will be auto-unsubsribed on the node
     *      where event occurred in case if it returns {@code false}.
     * @param types Types of events to listen for. If not provided, all events that pass the
     *      provided remote filter will be sent to local node.
     * @return a Future representing pending completion of the operation. The completed future contains
     *      {@code Operation ID} that can be passed to {@link #stopRemoteListen(UUID)} or
     *      {@link #stopRemoteListenAsync(UUID)} methods to stop listening.
     * @throws TurboSQLException If failed to add listener.
     */
    public <T extends Event> TurboSQLFuture<UUID> remoteListenAsync(@Nullable TurboSQLBiPredicate<UUID, T> locLsnr,
        @Nullable TurboSQLPredicate<T> rmtFilter,
        @Nullable int... types)
        throws TurboSQLException;

    /**
     * Adds event listener for specified events to all nodes in the cluster group (possibly including
     * local node if it belongs to the cluster group as well). This means that all events occurring on
     * any node within this cluster group that pass remote filter will be sent to local node for
     * local listener notification.
     * <p>
     * Supports asynchronous execution (see {@link TurboSQLAsyncSupport}).
     *
     * @param bufSize Remote events buffer size. Events from remote nodes won't be sent until buffer
     *      is full or time interval is exceeded.
     * @param interval Maximum time interval after which events from remote node will be sent. Events
     *      from remote nodes won't be sent until buffer is full or time interval is exceeded.
     * @param autoUnsubscribe Flag indicating that event listeners on remote nodes should be
     *      automatically unregistered if master node (node that initiated event listening) leaves
     *      topology. If this flag is {@code false}, listeners will be unregistered only when
     *      {@link #stopRemoteListen(UUID)} method is called, or the {@code 'callback (locLsnr)'}
     *      passed in returns {@code false}.
     * @param locLsnr Callback that is called on local node. If this predicate returns {@code true},
     *      the implementation will continue listening to events. Otherwise, events
     *      listening will be stopped and listeners will be unregistered on all nodes
     *      in the cluster group. If {@code null}, this events will be handled on remote nodes by
     *      passed in {@code rmtFilter} until local node stops (if {@code 'autoUnsubscribe'} is {@code true})
     *      or until {@link #stopRemoteListen(UUID)} is called.
     * @param rmtFilter Filter callback that is called on remote node. Only events that pass the remote filter
     *      will be sent to local node. If {@code null}, all events of specified types will
     *      be sent to local node. This remote filter can be used to pre-handle events remotely,
     *      before they are passed in to local callback. It will be auto-unsubsribed on the node
     *      where event occurred in case if it returns {@code false}.
     * @param types Types of events to listen for. If not provided, all events that pass the
     *      provided remote filter will be sent to local node.
     * @param <T> Type of the event.
     * @return {@code Operation ID} that can be passed to {@link #stopRemoteListen(UUID)} or
     *      {@link #stopRemoteListen(UUID)} methods to stop listening.
     * @throws TurboSQLException If failed to add listener.
     * @see #stopRemoteListen(UUID)
     * @see #stopRemoteListenAsync(UUID)
     */
    @TurboSQLAsyncSupported
    public <T extends Event> UUID remoteListen(int bufSize,
        long interval,
        boolean autoUnsubscribe,
        @Nullable TurboSQLBiPredicate<UUID, T> locLsnr,
        @Nullable TurboSQLPredicate<T> rmtFilter,
        @Nullable int... types)
        throws TurboSQLException;

    /**
     * Asynchronously adds event listener for specified events to all nodes in the cluster group (possibly including
     * local node if it belongs to the cluster group as well). This means that all events occurring on
     * any node within this cluster group that pass remote filter will be sent to local node for
     * local listener notification.
     *
     * @param <T> Type of the event.
     * @param bufSize Remote events buffer size. Events from remote nodes won't be sent until buffer
     *      is full or time interval is exceeded.
     * @param interval Maximum time interval after which events from remote node will be sent. Events
     *      from remote nodes won't be sent until buffer is full or time interval is exceeded.
     * @param autoUnsubscribe Flag indicating that event listeners on remote nodes should be
     *      automatically unregistered if master node (node that initiated event listening) leaves
     *      topology. If this flag is {@code false}, listeners will be unregistered only when
     *      {@link #stopRemoteListen(UUID)} method is called, or the {@code 'callback (locLsnr)'}
     *      passed in returns {@code false}.
     * @param locLsnr Callback that is called on local node. If this predicate returns {@code true},
     *      the implementation will continue listening to events. Otherwise, events
     *      listening will be stopped and listeners will be unregistered on all nodes
     *      in the cluster group. If {@code null}, this events will be handled on remote nodes by
     *      passed in {@code rmtFilter} until local node stops (if {@code 'autoUnsubscribe'} is {@code true})
     *      or until {@link #stopRemoteListen(UUID)} is called.
     * @param rmtFilter Filter callback that is called on remote node. Only events that pass the remote filter
     *      will be sent to local node. If {@code null}, all events of specified types will
     *      be sent to local node. This remote filter can be used to pre-handle events remotely,
     *      before they are passed in to local callback. It will be auto-unsubsribed on the node
     *      where event occurred in case if it returns {@code false}.
     * @param types Types of events to listen for. If not provided, all events that pass the
     *      provided remote filter will be sent to local node.
     * @return a Future representing pending completion of the operation. The completed future contains
     *      {@code Operation ID} that can be passed to {@link #stopRemoteListen(UUID)}
     *      or {@link #stopRemoteListen(UUID)} methods to stop listening.
     * @throws TurboSQLException If failed to add listener.
     * @see #stopRemoteListen(UUID)
     * @see #stopRemoteListenAsync(UUID)
     */
    public <T extends Event> TurboSQLFuture<UUID> remoteListenAsync(int bufSize,
        long interval,
        boolean autoUnsubscribe,
        @Nullable TurboSQLBiPredicate<UUID, T> locLsnr,
        @Nullable TurboSQLPredicate<T> rmtFilter,
        @Nullable int... types)
        throws TurboSQLException;

    /**
     * Stops listening to remote events. This will unregister all listeners identified with provided
     * operation ID on all nodes defined by {@link #clusterGroup()}.
     * <p>
     * Supports asynchronous execution (see {@link TurboSQLAsyncSupport}).
     *
     * @param opId Operation ID that was returned from
     *      {@link #remoteListen(TurboSQLBiPredicate, TurboSQLPredicate, int...)} method.
     * @throws TurboSQLException If failed to stop listeners.
     * @see #remoteListen(TurboSQLBiPredicate, TurboSQLPredicate, int...)
     * @see #remoteListenAsync(int, long, boolean, TurboSQLBiPredicate, TurboSQLPredicate, int...)
     */
    @TurboSQLAsyncSupported
    public void stopRemoteListen(UUID opId) throws TurboSQLException;

    /**
     * Asynchronously stops listening to remote events. This will unregister all listeners identified with provided
     * operation ID on all nodes defined by {@link #clusterGroup()}.
     *
     * @param opId Operation ID that was returned from
     *      {@link #remoteListen(TurboSQLBiPredicate, TurboSQLPredicate, int...)} method.
     * @return a Future representing pending completion of the operation.
     * @throws TurboSQLException If failed to stop listeners.
     * @see #remoteListen(TurboSQLBiPredicate, TurboSQLPredicate, int...)
     * @see #remoteListenAsync(int, long, boolean, TurboSQLBiPredicate, TurboSQLPredicate, int...)
     */
    public TurboSQLFuture<Void> stopRemoteListenAsync(UUID opId) throws TurboSQLException;

    /**
     * Waits for the specified events.
     * <p>
     * Supports asynchronous execution (see {@link TurboSQLAsyncSupport}).
     *
     * @param filter Optional filtering predicate. Only if predicates evaluates to {@code true} will the event
     *      end the wait.
     * @param types Types of the events to wait for. If not provided, all events will be passed to the filter.
     * @return Grid event.
     * @throws TurboSQLException If wait was interrupted.
     */
    @TurboSQLAsyncSupported
    public <T extends Event> T waitForLocal(@Nullable TurboSQLPredicate<T> filter, @Nullable int... types)
        throws TurboSQLException;

    /**
     * Create future to wait for the specified events.
     *
     * @param filter Optional filtering predicate. Only if predicates evaluates to {@code true} will the event
     *      end the wait.
     * @param types Types of the events to wait for. If not provided, all events will be passed to the filter.
     * @return a Future representing pending completion of the operation. The completed future contains grid event.
     * @throws TurboSQLException If wait was interrupted.
     */
    public <T extends Event> TurboSQLFuture<T> waitForLocalAsync(@Nullable TurboSQLPredicate<T> filter,
        @Nullable int... types) throws TurboSQLException;

    /**
     * Queries local node for events using passed-in predicate filter for event selection.
     *
     * @param p Predicate to filter events. All predicates must be satisfied for the
     *      event to be returned.
     * @param types Event types to be queried.
     * @return Collection of grid events found on local node.
     */
    public <T extends Event> Collection<T> localQuery(TurboSQLPredicate<T> p, @Nullable int... types);

    /**
     * Records customer user generated event. All registered local listeners will be notified.
     * <p>
     * NOTE: all types in range <b>from 1 to 1000 are reserved</b> for
     * internal TurboSQL events and should not be used by user-defined events.
     * Attempt to record internal event with this method will cause {@code IllegalArgumentException}
     * to be thrown.
     *
     * @param evt Locally generated event.
     * @throws IllegalArgumentException If event type is within TurboSQL reserved range between {@code 1} and
     *      {@code 1000}.
     */
    public void recordLocal(Event evt);

    /**
     * Adds an event listener for local events. Note that listener will be added regardless of whether
     * local node is in this cluster group or not.
     *
     * @param lsnr Predicate that is called on each received event. If predicate returns {@code false},
     *      it will be unregistered and will stop receiving events.
     * @param types Event types for which this listener will be notified.
     * @throws IllegalArgumentException Thrown in case when passed in array of event types is empty.
     */
    public void localListen(TurboSQLPredicate<? extends Event> lsnr, int... types);

    /**
     * Removes local event listener.
     *
     * @param lsnr Local event listener to remove.
     * @param types Types of events for which to remove listener. If not specified,
     *      then listener will be removed for all types it was registered for.
     * @return {@code true} if listener was removed, {@code false} otherwise.
     */
    public boolean stopLocalListen(TurboSQLPredicate<? extends Event> lsnr, @Nullable int... types);

    /**
     * Enables provided events. Allows to start recording events that
     * were disabled before. Note that specified events will be enabled
     * regardless of whether local node is in this cluster group or not.
     *
     * @param types Events to enable.
     */
    public void enableLocal(int... types);

    /**
     * Disables provided events. Allows to stop recording events that
     * were enabled before. Note that specified events will be disabled
     * regardless of whether local node is in this cluster group or not.
     *
     * @param types Events to disable.
     */
    public void disableLocal(int... types);

    /**
     * Gets types of enabled events.
     *
     * @return Types of enabled events.
     */
    public int[] enabledEvents();

    /**
     * Check if event is enabled.
     *
     * @param type Event type.
     * @return {@code True} if event of passed in type is enabled.
     */
    public boolean isEnabled(int type);

    /** {@inheritDoc} */
    @Deprecated
    @Override public TurboSQLEvents withAsync();
}