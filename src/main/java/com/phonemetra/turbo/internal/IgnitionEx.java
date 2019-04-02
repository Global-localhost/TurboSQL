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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Constructor;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Handler;
import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import com.phonemetra.turbo.TurboSQL;
import com.phonemetra.turbo.TurboSQLCheckedException;
import com.phonemetra.turbo.TurboSQLException;
import com.phonemetra.turbo.TurboSQLIllegalStateException;
import com.phonemetra.turbo.TurboSQLLogger;
import com.phonemetra.turbo.TurboSQLState;
import com.phonemetra.turbo.TurboSQLSystemProperties;
import com.phonemetra.turbo.Ignition;
import com.phonemetra.turbo.IgnitionListener;
import com.phonemetra.turbo.cache.affinity.rendezvous.RendezvousAffinityFunction;
import com.phonemetra.turbo.compute.ComputeJob;
import com.phonemetra.turbo.configuration.CacheConfiguration;
import com.phonemetra.turbo.configuration.ConnectorConfiguration;
import com.phonemetra.turbo.configuration.DataRegionConfiguration;
import com.phonemetra.turbo.configuration.DataStorageConfiguration;
import com.phonemetra.turbo.configuration.DeploymentMode;
import com.phonemetra.turbo.configuration.ExecutorConfiguration;
import com.phonemetra.turbo.configuration.FileSystemConfiguration;
import com.phonemetra.turbo.configuration.TurboSQLConfiguration;
import com.phonemetra.turbo.configuration.MemoryConfiguration;
import com.phonemetra.turbo.configuration.MemoryPolicyConfiguration;
import com.phonemetra.turbo.configuration.PersistentStoreConfiguration;
import com.phonemetra.turbo.configuration.TransactionConfiguration;
import com.phonemetra.turbo.failure.FailureContext;
import com.phonemetra.turbo.failure.FailureType;
import com.phonemetra.turbo.internal.binary.BinaryMarshaller;
import com.phonemetra.turbo.internal.managers.communication.GridIoPolicy;
import com.phonemetra.turbo.internal.managers.discovery.GridDiscoveryManager;
import com.phonemetra.turbo.internal.processors.datastructures.DataStructuresProcessor;
import com.phonemetra.turbo.internal.processors.igfs.IgfsThreadFactory;
import com.phonemetra.turbo.internal.processors.igfs.IgfsUtils;
import com.phonemetra.turbo.internal.processors.resource.GridSpringResourceContext;
import com.phonemetra.turbo.internal.util.GridConcurrentHashSet;
import com.phonemetra.turbo.internal.util.TurboSQLUtils;
import com.phonemetra.turbo.internal.util.StripedExecutor;
import com.phonemetra.turbo.internal.util.spring.TurboSQLSpringHelper;
import com.phonemetra.turbo.internal.util.typedef.CA;
import com.phonemetra.turbo.internal.util.typedef.F;
import com.phonemetra.turbo.internal.util.typedef.G;
import com.phonemetra.turbo.internal.util.typedef.T2;
import com.phonemetra.turbo.internal.util.typedef.X;
import com.phonemetra.turbo.internal.util.typedef.internal.A;
import com.phonemetra.turbo.internal.util.typedef.internal.CU;
import com.phonemetra.turbo.internal.util.typedef.internal.S;
import com.phonemetra.turbo.internal.util.typedef.internal.U;
import com.phonemetra.turbo.internal.util.worker.GridWorker;
import com.phonemetra.turbo.internal.worker.WorkersRegistry;
import com.phonemetra.turbo.lang.TurboSQLBiInClosure;
import com.phonemetra.turbo.lang.TurboSQLBiTuple;
import com.phonemetra.turbo.lang.TurboSQLInClosure;
import com.phonemetra.turbo.logger.LoggerNodeIdAware;
import com.phonemetra.turbo.logger.java.JavaLogger;
import com.phonemetra.turbo.marshaller.Marshaller;
import com.phonemetra.turbo.marshaller.MarshallerUtils;
import com.phonemetra.turbo.marshaller.jdk.JdkMarshaller;
import com.phonemetra.turbo.mxbean.IgnitionMXBean;
import com.phonemetra.turbo.plugin.segmentation.SegmentationPolicy;
import com.phonemetra.turbo.resources.SpringApplicationContextResource;
import com.phonemetra.turbo.spi.TurboSQLSpi;
import com.phonemetra.turbo.spi.TurboSQLSpiMultipleInstancesSupport;
import com.phonemetra.turbo.spi.checkpoint.noop.NoopCheckpointSpi;
import com.phonemetra.turbo.spi.collision.noop.NoopCollisionSpi;
import com.phonemetra.turbo.spi.communication.tcp.TcpCommunicationSpi;
import com.phonemetra.turbo.spi.deployment.local.LocalDeploymentSpi;
import com.phonemetra.turbo.spi.discovery.tcp.TcpDiscoverySpi;
import com.phonemetra.turbo.spi.discovery.tcp.ipfinder.multicast.TcpDiscoveryMulticastIpFinder;
import com.phonemetra.turbo.spi.encryption.noop.NoopEncryptionSpi;
import com.phonemetra.turbo.spi.eventstorage.NoopEventStorageSpi;
import com.phonemetra.turbo.spi.failover.always.AlwaysFailoverSpi;
import com.phonemetra.turbo.spi.indexing.noop.NoopIndexingSpi;
import com.phonemetra.turbo.spi.loadbalancing.LoadBalancingSpi;
import com.phonemetra.turbo.spi.loadbalancing.roundrobin.RoundRobinLoadBalancingSpi;
import com.phonemetra.turbo.thread.TurboSQLStripedThreadPoolExecutor;
import com.phonemetra.turbo.thread.TurboSQLThread;
import com.phonemetra.turbo.thread.TurboSQLThreadPoolExecutor;
import org.jetbrains.annotations.Nullable;

import static com.phonemetra.turbo.TurboSQLState.STARTED;
import static com.phonemetra.turbo.TurboSQLState.STOPPED;
import static com.phonemetra.turbo.TurboSQLState.STOPPED_ON_FAILURE;
import static com.phonemetra.turbo.TurboSQLState.STOPPED_ON_SEGMENTATION;
import static com.phonemetra.turbo.TurboSQLSystemProperties.IGNITE_CACHE_CLIENT;
import static com.phonemetra.turbo.TurboSQLSystemProperties.IGNITE_CONFIG_URL;
import static com.phonemetra.turbo.TurboSQLSystemProperties.IGNITE_OVERRIDE_CONSISTENT_ID;
import static com.phonemetra.turbo.TurboSQLSystemProperties.IGNITE_DEP_MODE_OVERRIDE;
import static com.phonemetra.turbo.TurboSQLSystemProperties.IGNITE_LOCAL_HOST;
import static com.phonemetra.turbo.TurboSQLSystemProperties.IGNITE_NO_SHUTDOWN_HOOK;
import static com.phonemetra.turbo.TurboSQLSystemProperties.IGNITE_RESTART_CODE;
import static com.phonemetra.turbo.TurboSQLSystemProperties.IGNITE_SUCCESS_FILE;
import static com.phonemetra.turbo.TurboSQLSystemProperties.IGNITE_SYSTEM_WORKER_BLOCKED_TIMEOUT;
import static com.phonemetra.turbo.cache.CacheAtomicityMode.TRANSACTIONAL;
import static com.phonemetra.turbo.cache.CacheMode.REPLICATED;
import static com.phonemetra.turbo.cache.CacheRebalanceMode.SYNC;
import static com.phonemetra.turbo.cache.CacheWriteSynchronizationMode.FULL_SYNC;
import static com.phonemetra.turbo.configuration.TurboSQLConfiguration.DFLT_THREAD_KEEP_ALIVE_TIME;
import static com.phonemetra.turbo.configuration.MemoryConfiguration.DFLT_MEMORY_POLICY_MAX_SIZE;
import static com.phonemetra.turbo.configuration.MemoryConfiguration.DFLT_MEM_PLC_DEFAULT_NAME;
import static com.phonemetra.turbo.failure.FailureType.SYSTEM_WORKER_TERMINATION;
import static com.phonemetra.turbo.internal.TurboSQLComponentType.SPRING;
import static com.phonemetra.turbo.plugin.segmentation.SegmentationPolicy.RESTART_JVM;

/**
 * This class defines a factory for the main TurboSQL API. It controls Grid life cycle
 * and allows listening for grid events.
 * <h1 class="header">Grid Loaders</h1>
 * Although user can apply grid factory directly to start and stop grid, grid is
 * often started and stopped by grid loaders. Grid loaders can be found in
 * {@link com.phonemetra.turbo.startup} package, for example:
 * <ul>
 * <li>{@code CommandLineStartup}</li>
 * <li>{@code ServletStartup}</li>
 * </ul>
 * <h1 class="header">Examples</h1>
 * Use {@link #start()} method to start grid with default configuration. You can also use
 * {@link TurboSQLConfiguration} to override some default configuration. Below is an
 * example on how to start grid with <strong>URI deployment</strong>.
 * <pre name="code" class="java">
 * GridConfiguration cfg = new GridConfiguration();
 */
public class IgnitionEx {
    /** Default configuration path relative to TurboSQL home. */
    public static final String DFLT_CFG = "config/default-config.xml";

    /** Map of named TurboSQL instances. */
    private static final ConcurrentMap<Object, TurboSQLNamedInstance> grids = new ConcurrentHashMap<>();

    /** Map of grid states ever started in this JVM. */
    private static final Map<Object, TurboSQLState> gridStates = new ConcurrentHashMap<>();

    /** Mutex to synchronize updates of default grid reference. */
    private static final Object dfltGridMux = new Object();

    /** Default grid. */
    private static volatile TurboSQLNamedInstance dfltGrid;

    /** Default grid state. */
    private static volatile TurboSQLState dfltGridState;

    /** List of state listeners. */
    private static final Collection<IgnitionListener> lsnrs = new GridConcurrentHashSet<>(4);

    /** */
    private static ThreadLocal<Boolean> daemon = new ThreadLocal<Boolean>() {
        @Override protected Boolean initialValue() {
            return false;
        }
    };

    /** */
    private static ThreadLocal<Boolean> clientMode = new ThreadLocal<>();

    /**
     * Enforces singleton.
     */
    private IgnitionEx() {
        // No-op.
    }

    /**
     * Sets daemon flag.
     * <p>
     * If daemon flag is set then all grid instances created by the factory will be
     * daemon, i.e. the local node for these instances will be a daemon node. Note that
     * if daemon flag is set - it will override the same settings in {@link TurboSQLConfiguration#isDaemon()}.
     * Note that you can set on and off daemon flag at will.
     *
     * @param daemon Daemon flag to set.
     */
    public static void setDaemon(boolean daemon) {
        IgnitionEx.daemon.set(daemon);
    }

    /**
     * Gets daemon flag.
     * <p>
     * If daemon flag it set then all grid instances created by the factory will be
     * daemon, i.e. the local node for these instances will be a daemon node. Note that
     * if daemon flag is set - it will override the same settings in {@link TurboSQLConfiguration#isDaemon()}.
     * Note that you can set on and off daemon flag at will.
     *
     * @return Daemon flag.
     */
    public static boolean isDaemon() {
        return daemon.get();
    }

    /**
     * Sets client mode flag.
     *
     * @param clientMode Client mode flag.
     */
    public static void setClientMode(boolean clientMode) {
        IgnitionEx.clientMode.set(clientMode);
    }

    /**
     * Gets client mode flag.
     *
     * @return Client mode flag.
     */
    public static boolean isClientMode() {
        return clientMode.get() == null ? false : clientMode.get();
    }

    /**
     * Gets state of grid default grid.
     *
     * @return Default grid state.
     */
    public static TurboSQLState state() {
        return state(null);
    }

    /**
     * Gets states of named TurboSQL instance. If name is {@code null}, then state of
     * default no-name TurboSQL instance is returned.
     *
     * @param name TurboSQL instance name. If name is {@code null}, then state of
     *      default no-name TurboSQL instance is returned.
     * @return Grid state.
     */
    public static TurboSQLState state(@Nullable String name) {
        TurboSQLNamedInstance grid = name != null ? grids.get(name) : dfltGrid;

        if (grid == null) {
            TurboSQLState state = name != null ? gridStates.get(name) : dfltGridState;

            return state != null ? state : STOPPED;
        }

        return grid.state();
    }

    /**
     * Stops default grid. This method is identical to {@code G.stop(null, cancel)} apply.
     * Note that method does not wait for all tasks to be completed.
     *
     * @param cancel If {@code true} then all jobs currently executing on
     *      default grid will be cancelled by calling {@link ComputeJob#cancel()}
     *      method. Note that just like with {@link Thread#interrupt()}, it is
     *      up to the actual job to exit from execution
     * @return {@code true} if default grid instance was indeed stopped,
     *      {@code false} otherwise (if it was not started).
     */
    public static boolean stop(boolean cancel) {
        return stop(null, cancel, false);
    }

    /**
     * Stops named TurboSQL instance. If {@code cancel} flag is set to {@code true} then
     * all jobs currently executing on local node will be interrupted. If
     * TurboSQL instance name is {@code null}, then default no-name TurboSQL instance will be stopped.
     * If wait parameter is set to {@code true} then TurboSQL instance will wait for all
     * tasks to be finished.
     *
     * @param name TurboSQL instance name. If {@code null}, then default no-name
     *      TurboSQL instance will be stopped.
     * @param cancel If {@code true} then all jobs currently will be cancelled
     *      by calling {@link ComputeJob#cancel()} method. Note that just like with
     *      {@link Thread#interrupt()}, it is up to the actual job to exit from
     *      execution. If {@code false}, then jobs currently running will not be
     *      canceled. In either case, grid node will wait for completion of all
     *      jobs running on it before stopping.
     * @param stopNotStarted If {@code true} and node start did not finish then interrupts starting thread.
     * @return {@code true} if named TurboSQL instance was indeed found and stopped,
     *      {@code false} otherwise (the instance with given {@code name} was
     *      not found).
     */
    public static boolean stop(@Nullable String name, boolean cancel, boolean stopNotStarted) {
        TurboSQLNamedInstance grid = name != null ? grids.get(name) : dfltGrid;

        if (grid != null && stopNotStarted && grid.startLatch.getCount() != 0) {
            grid.starterThreadInterrupted = true;

            grid.starterThread.interrupt();
        }

        if (grid != null && grid.state() == STARTED) {
            grid.stop(cancel);

            boolean fireEvt;

            if (name != null)
                fireEvt = grids.remove(name, grid);
            else {
                synchronized (dfltGridMux) {
                    fireEvt = dfltGrid == grid;

                    if (fireEvt)
                        dfltGrid = null;
                }
            }

            if (fireEvt)
                notifyStateChange(grid.getName(), grid.state());

            return true;
        }

        // We don't have log at this point...
        U.warn(null, "Ignoring stopping TurboSQL instance that was already stopped or never started: " + name);

        return false;
    }

    /**
     * Behavior of the method is the almost same as {@link IgnitionEx#stop(String, boolean, boolean)}.
     * If node stopping process will not be finished within {@code timeoutMs} whole JVM will be killed.
     *
     * @param timeoutMs Timeout to wait graceful stopping.
     */
    public static boolean stop(@Nullable String name, boolean cancel, boolean stopNotStarted, long timeoutMs) {
        final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        // Schedule delayed node killing if graceful stopping will be not finished within timeout.
        executor.schedule(new Runnable() {
            @Override
            public void run() {
                if (state(name) == TurboSQLState.STARTED) {
                    U.error(null, "Unable to gracefully stop node within timeout " + timeoutMs +
                            " milliseconds. Killing node...");

                    // We are not able to kill only one grid so whole JVM will be stopped.
                    Runtime.getRuntime().halt(Ignition.KILL_EXIT_CODE);
                }
            }
        }, timeoutMs, TimeUnit.MILLISECONDS);

        boolean success = stop(name, cancel, stopNotStarted);

        executor.shutdownNow();

        return success;
    }

    /**
     * Stops <b>all</b> started grids. If {@code cancel} flag is set to {@code true} then
     * all jobs currently executing on local node will be interrupted.
     * If wait parameter is set to {@code true} then grid will wait for all
     * tasks to be finished.
     * <p>
     * <b>Note:</b> it is usually safer and more appropriate to stop grid instances individually
     * instead of blanket operation. In most cases, the party that started the grid instance
     * should be responsible for stopping it.
     *
     * @param cancel If {@code true} then all jobs currently executing on
     *      all grids will be cancelled by calling {@link ComputeJob#cancel()}
     *      method. Note that just like with {@link Thread#interrupt()}, it is
     *      up to the actual job to exit from execution
     */
    public static void stopAll(boolean cancel) {
        TurboSQLNamedInstance dfltGrid0 = dfltGrid;

        if (dfltGrid0 != null) {
            dfltGrid0.stop(cancel);

            boolean fireEvt;

            synchronized (dfltGridMux) {
                fireEvt = dfltGrid == dfltGrid0;

                if (fireEvt)
                    dfltGrid = null;
            }

            if (fireEvt)
                notifyStateChange(dfltGrid0.getName(), dfltGrid0.state());
        }

        // Stop the rest and clear grids map.
        for (TurboSQLNamedInstance grid : grids.values()) {
            grid.stop(cancel);

            boolean fireEvt = grids.remove(grid.getName(), grid);

            if (fireEvt)
                notifyStateChange(grid.getName(), grid.state());
        }
    }

    /**
     * Restarts <b>all</b> started grids. If {@code cancel} flag is set to {@code true} then
     * all jobs currently executing on the local node will be interrupted.
     * If {@code wait} parameter is set to {@code true} then grid will wait for all
     * tasks to be finished.
     * <p>
     * <b>Note:</b> it is usually safer and more appropriate to stop grid instances individually
     * instead of blanket operation. In most cases, the party that started the grid instance
     * should be responsible for stopping it.
     * <p>
     * Note also that restarting functionality only works with the tools that specifically
     * support TurboSQL's protocol for restarting. Currently only standard <tt>turboSQL.{sh|bat}</tt>
     * scripts support restarting of JVM TurboSQL's process.
     *
     * @param cancel If {@code true} then all jobs currently executing on
     *      all grids will be cancelled by calling {@link ComputeJob#cancel()}
     *      method. Note that just like with {@link Thread#interrupt()}, it is
     *      up to the actual job to exit from execution.
     * @see Ignition#RESTART_EXIT_CODE
     */
    public static void restart(boolean cancel) {
        String file = System.getProperty(IGNITE_SUCCESS_FILE);

        if (file == null)
            U.warn(null, "Cannot restart node when restart not enabled.");
        else {
            try {
                new File(file).createNewFile();
            }
            catch (IOException e) {
                U.error(null, "Failed to create restart marker file (restart aborted): " + e.getMessage());

                return;
            }

            U.log(null, "Restarting node. Will exit (" + Ignition.RESTART_EXIT_CODE + ").");

            // Set the exit code so that shell process can recognize it and loop
            // the start up sequence again.
            System.setProperty(IGNITE_RESTART_CODE, Integer.toString(Ignition.RESTART_EXIT_CODE));

            stopAll(cancel);

            // This basically leaves loaders hang - we accept it.
            System.exit(Ignition.RESTART_EXIT_CODE);
        }
    }

    /**
     * Stops <b>all</b> started grids. If {@code cancel} flag is set to {@code true} then
     * all jobs currently executing on the local node will be interrupted.
     * If {@code wait} parameter is set to {@code true} then grid will wait for all
     * tasks to be finished.
     * <p>
     * <b>Note:</b> it is usually safer and more appropriate to stop grid instances individually
     * instead of blanket operation. In most cases, the party that started the grid instance
     * should be responsible for stopping it.
     * <p>
     * Note that upon completion of this method, the JVM with forcefully exist with
     * exit code {@link Ignition#KILL_EXIT_CODE}.
     *
     * @param cancel If {@code true} then all jobs currently executing on
     *      all grids will be cancelled by calling {@link ComputeJob#cancel()}
     *      method. Note that just like with {@link Thread#interrupt()}, it is
     *      up to the actual job to exit from execution.
     * @see Ignition#KILL_EXIT_CODE
     */
    public static void kill(boolean cancel) {
        stopAll(cancel);

        // This basically leaves loaders hang - we accept it.
        System.exit(Ignition.KILL_EXIT_CODE);
    }

    /**
     * Starts grid with default configuration. By default this method will
     * use grid configuration defined in {@code TURBOSQL_HOME/config/default-config.xml}
     * configuration file. If such file is not found, then all system defaults will be used.
     *
     * @return Started grid.
     * @throws TurboSQLCheckedException If default grid could not be started. This exception will be thrown
     *      also if default grid has already been started.
     */
    public static TurboSQL start() throws TurboSQLCheckedException {
        return start((GridSpringResourceContext)null);
    }

    /**
     * Starts grid with default configuration. By default this method will
     * use grid configuration defined in {@code TURBOSQL_HOME/config/default-config.xml}
     * configuration file. If such file is not found, then all system defaults will be used.
     *
     * @param springCtx Optional Spring application context, possibly {@code null}.
     *      Spring bean definitions for bean injection are taken from this context.
     *      If provided, this context can be injected into grid tasks and grid jobs using
     *      {@link SpringApplicationContextResource @SpringApplicationContextResource} annotation.
     * @return Started grid.
     * @throws TurboSQLCheckedException If default grid could not be started. This exception will be thrown
     *      also if default grid has already been started.
     */
    public static TurboSQL start(@Nullable GridSpringResourceContext springCtx) throws TurboSQLCheckedException {
        URL url = U.resolveTurboSQLUrl(DFLT_CFG);

        if (url != null)
            return start(DFLT_CFG, null, springCtx, null);

        U.warn(null, "Default Spring XML file not found (is TURBOSQL_HOME set?): " + DFLT_CFG);

        return start0(new GridStartContext(new TurboSQLConfiguration(), null, springCtx), true)
            .get1().grid();
    }

    /**
     * Starts grid with given configuration. Note that this method is no-op if grid with the name
     * provided in given configuration is already started.
     *
     * @param cfg Grid configuration. This cannot be {@code null}.
     * @return Started grid.
     * @throws TurboSQLCheckedException If grid could not be started. This exception will be thrown
     *      also if named grid has already been started.
     */
    public static TurboSQL start(TurboSQLConfiguration cfg) throws TurboSQLCheckedException {
        return start(cfg, null, true).get1();
    }

    /**
     * Starts a grid with given configuration. If the grid is already started and failIfStarted set to TRUE
     * an exception will be thrown.
     *
     * @param cfg Grid configuration. This cannot be {@code null}.
     * @param failIfStarted When flag is {@code true} and grid with specified name has been already started
     *      the exception is thrown. Otherwise the existing instance of the grid is returned.
     * @return Started grid or existing grid.
     * @throws TurboSQLCheckedException If grid could not be started. This exception will be thrown
     *      also if named grid has already been started.
     */
    public static TurboSQL start(TurboSQLConfiguration cfg, boolean failIfStarted) throws TurboSQLCheckedException {
        return start(cfg, null, failIfStarted).get1();
    }

    /**
     * Gets or starts new grid instance if it hasn't been started yet.
     *
     * @param cfg Grid configuration. This cannot be {@code null}.
     * @return Tuple with: grid instance and flag to indicate the instance is started by this call.
     *      So, when the new turboSQL instance is started the flag is {@code true}. If an existing instance is returned
     *      the flag is {@code false}.
     * @throws TurboSQLException If grid could not be started.
     */
    public static T2<TurboSQL, Boolean> getOrStart(TurboSQLConfiguration cfg) throws TurboSQLException {
        try {
            return start(cfg, null, false);
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
    }


    /**
     * Starts grid with given configuration. Note that this method will throw and exception if grid with the name
     * provided in given configuration is already started.
     *
     * @param cfg Grid configuration. This cannot be {@code null}.
     * @param springCtx Optional Spring application context, possibly {@code null}.
     *      Spring bean definitions for bean injection are taken from this context.
     *      If provided, this context can be injected into grid tasks and grid jobs using
     *      {@link SpringApplicationContextResource @SpringApplicationContextResource} annotation.
     * @return Started grid.
     * @throws TurboSQLCheckedException If grid could not be started. This exception will be thrown
     *      also if named grid has already been started.
     */
    public static TurboSQL start(TurboSQLConfiguration cfg, @Nullable GridSpringResourceContext springCtx) throws TurboSQLCheckedException {
        A.notNull(cfg, "cfg");

        return start0(new GridStartContext(cfg, null, springCtx), true).get1().grid();
    }

    /**
     * Starts grid with given configuration. If the grid is already started and failIfStarted set to TRUE
     * an exception will be thrown.
     *
     * @param cfg Grid configuration. This cannot be {@code null}.
     * @param springCtx Optional Spring application context, possibly {@code null}.
     *      Spring bean definitions for bean injection are taken from this context.
     *      If provided, this context can be injected into grid tasks and grid jobs using
     *      {@link SpringApplicationContextResource @SpringApplicationContextResource} annotation.
     * @param failIfStarted Throw or not an exception if grid is already started.
     * @return Tuple with: grid instance and flag to indicate the instance is started by this call.
     *      So, when the new turboSQL instance is started the flag is {@code true}. If an existing instance is returned
     *      the flag is {@code false}.
     * @throws TurboSQLCheckedException If grid could not be started. This exception will be thrown
     *      also if named grid has already been started.
     */
    public static T2<TurboSQL, Boolean> start(TurboSQLConfiguration cfg, @Nullable GridSpringResourceContext springCtx, boolean failIfStarted) throws TurboSQLCheckedException {
        A.notNull(cfg, "cfg");

        T2<TurboSQLNamedInstance, Boolean> res = start0(new GridStartContext(cfg, null, springCtx), failIfStarted);

        return new T2<>((TurboSQL)res.get1().grid(), res.get2());
    }

    /**
     * Starts all grids specified within given Spring XML configuration file. If grid with given name
     * is already started, then exception is thrown. In this case all instances that may
     * have been started so far will be stopped too.
     * <p>
     * Usually Spring XML configuration file will contain only one Grid definition. Note that
     * Grid configuration bean(s) is retrieved form configuration file by type, so the name of
     * the Grid configuration bean is ignored.
     *
     * @param springCfgPath Spring XML configuration file path or URL.
     * @return Started grid. If Spring configuration contains multiple grid instances,
     *      then the 1st found instance is returned.
     * @throws TurboSQLCheckedException If grid could not be started or configuration
     *      read. This exception will be thrown also if grid with given name has already
     *      been started or Spring XML configuration file is invalid.
     */
    public static TurboSQL start(@Nullable String springCfgPath) throws TurboSQLCheckedException {
        return springCfgPath == null ? start() : start(springCfgPath, null);
    }

    /**
     * Starts all grids specified within given Spring XML configuration file. If grid with given name
     * is already started, then exception is thrown. In this case all instances that may
     * have been started so far will be stopped too.
     * <p>
     * Usually Spring XML configuration file will contain only one Grid definition. Note that
     * Grid configuration bean(s) is retrieved form configuration file by type, so the name of
     * the Grid configuration bean is ignored.
     *
     * @param springCfgPath Spring XML configuration file path or URL.
     * @param turboSQLInstanceName TurboSQL instance name that will override default.
     * @return Started grid. If Spring configuration contains multiple grid instances,
     *      then the 1st found instance is returned.
     * @throws TurboSQLCheckedException If grid could not be started or configuration
     *      read. This exception will be thrown also if grid with given name has already
     *      been started or Spring XML configuration file is invalid.
     */
    public static TurboSQL start(@Nullable String springCfgPath, @Nullable String turboSQLInstanceName)
        throws TurboSQLCheckedException {
        if (springCfgPath == null) {
            TurboSQLConfiguration cfg = new TurboSQLConfiguration();

            if (cfg.getTurboSQLInstanceName() == null && !F.isEmpty(turboSQLInstanceName))
                cfg.setTurboSQLInstanceName(turboSQLInstanceName);

            return start(cfg);
        }
        else
            return start(springCfgPath, turboSQLInstanceName, null, null);
    }

    /**
     * Loads all grid configurations specified within given Spring XML configuration file.
     * <p>
     * Usually Spring XML configuration file will contain only one Grid definition. Note that
     * Grid configuration bean(s) is retrieved form configuration file by type, so the name of
     * the Grid configuration bean is ignored.
     *
     * @param springCfgUrl Spring XML configuration file path or URL. This cannot be {@code null}.
     * @return Tuple containing all loaded configurations and Spring context used to load them.
     * @throws TurboSQLCheckedException If grid could not be started or configuration
     *      read. This exception will be thrown also if grid with given name has already
     *      been started or Spring XML configuration file is invalid.
     */
    public static TurboSQLBiTuple<Collection<TurboSQLConfiguration>, ? extends GridSpringResourceContext>
    loadConfigurations(URL springCfgUrl) throws TurboSQLCheckedException {
        TurboSQLSpringHelper spring = SPRING.create(false);

        return spring.loadConfigurations(springCfgUrl);
    }

    /**
     * Loads all grid configurations specified within given input stream.
     * <p>
     * Usually Spring XML input stream will contain only one Grid definition. Note that
     * Grid configuration bean(s) is retrieved form configuration input stream by type, so the name of
     * the Grid configuration bean is ignored.
     *
     * @param springCfgStream Input stream contained Spring XML configuration. This cannot be {@code null}.
     * @return Tuple containing all loaded configurations and Spring context used to load them.
     * @throws TurboSQLCheckedException If grid could not be started or configuration
     *      read. This exception will be thrown also if grid with given name has already
     *      been started or Spring XML configuration file is invalid.
     */
    public static TurboSQLBiTuple<Collection<TurboSQLConfiguration>, ? extends GridSpringResourceContext>
    loadConfigurations(InputStream springCfgStream) throws TurboSQLCheckedException {
        TurboSQLSpringHelper spring = SPRING.create(false);

        return spring.loadConfigurations(springCfgStream);
    }

    /**
     * Loads all grid configurations specified within given Spring XML configuration file.
     * <p>
     * Usually Spring XML configuration file will contain only one Grid definition. Note that
     * Grid configuration bean(s) is retrieved form configuration file by type, so the name of
     * the Grid configuration bean is ignored.
     *
     * @param springCfgPath Spring XML configuration file path. This cannot be {@code null}.
     * @return Tuple containing all loaded configurations and Spring context used to load them.
     * @throws TurboSQLCheckedException If grid could not be started or configuration
     *      read. This exception will be thrown also if grid with given name has already
     *      been started or Spring XML configuration file is invalid.
     */
    public static TurboSQLBiTuple<Collection<TurboSQLConfiguration>, ? extends GridSpringResourceContext>
    loadConfigurations(String springCfgPath) throws TurboSQLCheckedException {
        A.notNull(springCfgPath, "springCfgPath");
        return loadConfigurations(TurboSQLUtils.resolveSpringUrl(springCfgPath));
    }

    /**
     * Loads first found grid configuration specified within given Spring XML configuration file.
     * <p>
     * Usually Spring XML configuration file will contain only one Grid definition. Note that
     * Grid configuration bean(s) is retrieved form configuration file by type, so the name of
     * the Grid configuration bean is ignored.
     *
     * @param springCfgUrl Spring XML configuration file path or URL. This cannot be {@code null}.
     * @return First found configuration and Spring context used to load it.
     * @throws TurboSQLCheckedException If grid could not be started or configuration
     *      read. This exception will be thrown also if grid with given name has already
     *      been started or Spring XML configuration file is invalid.
     */
    public static TurboSQLBiTuple<TurboSQLConfiguration, GridSpringResourceContext> loadConfiguration(URL springCfgUrl)
        throws TurboSQLCheckedException {
        TurboSQLBiTuple<Collection<TurboSQLConfiguration>, ? extends GridSpringResourceContext> t =
            loadConfigurations(springCfgUrl);

        return F.t(F.first(t.get1()), t.get2());
    }

    /**
     * Loads first found grid configuration specified within given Spring XML configuration file.
     * <p>
     * Usually Spring XML configuration file will contain only one Grid definition. Note that
     * Grid configuration bean(s) is retrieved form configuration file by type, so the name of
     * the Grid configuration bean is ignored.
     *
     * @param springCfgPath Spring XML configuration file path. This cannot be {@code null}.
     * @return First found configuration and Spring context used to load it.
     * @throws TurboSQLCheckedException If grid could not be started or configuration
     *      read. This exception will be thrown also if grid with given name has already
     *      been started or Spring XML configuration file is invalid.
     */
    public static TurboSQLBiTuple<TurboSQLConfiguration, GridSpringResourceContext> loadConfiguration(String springCfgPath)
        throws TurboSQLCheckedException {
        TurboSQLBiTuple<Collection<TurboSQLConfiguration>, ? extends GridSpringResourceContext> t =
            loadConfigurations(springCfgPath);

        return F.t(F.first(t.get1()), t.get2());
    }

    /**
     * Starts all grids specified within given Spring XML configuration file. If grid with given name
     * is already started, then exception is thrown. In this case all instances that may
     * have been started so far will be stopped too.
     * <p>
     * Usually Spring XML configuration file will contain only one Grid definition. Note that
     * Grid configuration bean(s) is retrieved form configuration file by type, so the name of
     * the Grid configuration bean is ignored.
     *
     * @param springCfgPath Spring XML configuration file path or URL. This cannot be {@code null}.
     * @param turboSQLInstanceName TurboSQL instance name that will override default.
     * @param springCtx Optional Spring application context, possibly {@code null}.
     * @param ldr Optional class loader that will be used by default.
     *      Spring bean definitions for bean injection are taken from this context.
     *      If provided, this context can be injected into grid tasks and grid jobs using
     *      {@link SpringApplicationContextResource @SpringApplicationContextResource} annotation.
     * @return Started grid. If Spring configuration contains multiple grid instances,
     *      then the 1st found instance is returned.
     * @throws TurboSQLCheckedException If grid could not be started or configuration
     *      read. This exception will be thrown also if grid with given name has already
     *      been started or Spring XML configuration file is invalid.
     */
    public static TurboSQL start(String springCfgPath, @Nullable String turboSQLInstanceName,
        @Nullable GridSpringResourceContext springCtx, @Nullable ClassLoader ldr) throws TurboSQLCheckedException {
        URL url = U.resolveSpringUrl(springCfgPath);

        return start(url, turboSQLInstanceName, springCtx, ldr);
    }

    /**
     * Starts all grids specified within given Spring XML configuration file URL. If grid with given name
     * is already started, then exception is thrown. In this case all instances that may
     * have been started so far will be stopped too.
     * <p>
     * Usually Spring XML configuration file will contain only one Grid definition. Note that
     * Grid configuration bean(s) is retrieved form configuration file by type, so the name of
     * the Grid configuration bean is ignored.
     *
     * @param springCfgUrl Spring XML configuration file URL. This cannot be {@code null}.
     * @return Started grid. If Spring configuration contains multiple grid instances,
     *      then the 1st found instance is returned.
     * @throws TurboSQLCheckedException If grid could not be started or configuration
     *      read. This exception will be thrown also if grid with given name has already
     *      been started or Spring XML configuration file is invalid.
     */
    public static TurboSQL start(URL springCfgUrl) throws TurboSQLCheckedException {
        return start(springCfgUrl, null, null, null);
    }

    /**
     * Starts all grids specified within given Spring XML configuration file URL. If grid with given name
     * is already started, then exception is thrown. In this case all instances that may
     * have been started so far will be stopped too.
     * <p>
     * Usually Spring XML configuration file will contain only one Grid definition. Note that
     * Grid configuration bean(s) is retrieved form configuration file by type, so the name of
     * the Grid configuration bean is ignored.
     *
     * @param springCfgUrl Spring XML configuration file URL. This cannot be {@code null}.
     * @param ldr Optional class loader that will be used by default.
     * @return Started grid. If Spring configuration contains multiple grid instances,
     *      then the 1st found instance is returned.
     * @throws TurboSQLCheckedException If grid could not be started or configuration
     *      read. This exception will be thrown also if grid with given name has already
     *      been started or Spring XML configuration file is invalid.
     */
    public static TurboSQL start(URL springCfgUrl, @Nullable ClassLoader ldr) throws TurboSQLCheckedException {
        return start(springCfgUrl, null, null, ldr);
    }

    /**
     * Starts all grids specified within given Spring XML configuration file URL. If grid with given name
     * is already started, then exception is thrown. In this case all instances that may
     * have been started so far will be stopped too.
     * <p>
     * Usually Spring XML configuration file will contain only one Grid definition. Note that
     * Grid configuration bean(s) is retrieved form configuration file by type, so the name of
     * the Grid configuration bean is ignored.
     *
     * @param springCfgUrl Spring XML configuration file URL. This cannot be {@code null}.
     * @param turboSQLInstanceName TurboSQL instance name that will override default.
     * @param springCtx Optional Spring application context, possibly {@code null}.
     * @param ldr Optional class loader that will be used by default.
     *      Spring bean definitions for bean injection are taken from this context.
     *      If provided, this context can be injected into grid tasks and grid jobs using
     *      {@link SpringApplicationContextResource @SpringApplicationContextResource} annotation.
     * @return Started grid. If Spring configuration contains multiple grid instances,
     *      then the 1st found instance is returned.
     * @throws TurboSQLCheckedException If grid could not be started or configuration
     *      read. This exception will be thrown also if grid with given name has already
     *      been started or Spring XML configuration file is invalid.
     */
    public static TurboSQL start(URL springCfgUrl, @Nullable String turboSQLInstanceName,
        @Nullable GridSpringResourceContext springCtx, @Nullable ClassLoader ldr) throws TurboSQLCheckedException {
        A.notNull(springCfgUrl, "springCfgUrl");

        boolean isLog4jUsed = U.gridClassLoader().getResource("org/apache/log4j/Appender.class") != null;

        TurboSQLBiTuple<Object, Object> t = null;

        if (isLog4jUsed) {
            try {
                t = U.addLog4jNoOpLogger();
            }
            catch (TurboSQLCheckedException ignore) {
                isLog4jUsed = false;
            }
        }

        Collection<Handler> savedHnds = null;

        if (!isLog4jUsed)
            savedHnds = U.addJavaNoOpLogger();

        TurboSQLBiTuple<Collection<TurboSQLConfiguration>, ? extends GridSpringResourceContext> cfgMap;

        try {
            cfgMap = loadConfigurations(springCfgUrl);
        }
        finally {
            if (isLog4jUsed && t != null)
                U.removeLog4jNoOpLogger(t);

            if (!isLog4jUsed)
                U.removeJavaNoOpLogger(savedHnds);
        }

        return startConfigurations(cfgMap, springCfgUrl, turboSQLInstanceName, springCtx, ldr);
    }

    /**
     * Starts all grids specified within given Spring XML configuration input stream. If grid with given name
     * is already started, then exception is thrown. In this case all instances that may
     * have been started so far will be stopped too.
     * <p>
     * Usually Spring XML configuration input stream will contain only one Grid definition. Note that
     * Grid configuration bean(s) is retrieved form configuration input stream by type, so the name of
     * the Grid configuration bean is ignored.
     *
     * @param springCfgStream Input stream containing Spring XML configuration. This cannot be {@code null}.
     * @return Started grid. If Spring configuration contains multiple grid instances,
     *      then the 1st found instance is returned.
     * @throws TurboSQLCheckedException If grid could not be started or configuration
     *      read. This exception will be thrown also if grid with given name has already
     *      been started or Spring XML configuration file is invalid.
     */
    public static TurboSQL start(InputStream springCfgStream) throws TurboSQLCheckedException {
        return start(springCfgStream, null, null, null);
    }

    /**
     * Starts all grids specified within given Spring XML configuration input stream. If grid with given name
     * is already started, then exception is thrown. In this case all instances that may
     * have been started so far will be stopped too.
     * <p>
     * Usually Spring XML configuration input stream will contain only one Grid definition. Note that
     * Grid configuration bean(s) is retrieved form configuration input stream by type, so the name of
     * the Grid configuration bean is ignored.
     *
     * @param springCfgStream Input stream containing Spring XML configuration. This cannot be {@code null}.
     * @param turboSQLInstanceName TurboSQL instance name that will override default.
     * @param springCtx Optional Spring application context, possibly {@code null}.
     * @param ldr Optional class loader that will be used by default.
     *      Spring bean definitions for bean injection are taken from this context.
     *      If provided, this context can be injected into grid tasks and grid jobs using
     *      {@link SpringApplicationContextResource @SpringApplicationContextResource} annotation.
     * @return Started grid. If Spring configuration contains multiple grid instances,
     *      then the 1st found instance is returned.
     * @throws TurboSQLCheckedException If grid could not be started or configuration
     *      read. This exception will be thrown also if grid with given name has already
     *      been started or Spring XML configuration file is invalid.
     */
    public static TurboSQL start(InputStream springCfgStream, @Nullable String turboSQLInstanceName,
        @Nullable GridSpringResourceContext springCtx, @Nullable ClassLoader ldr) throws TurboSQLCheckedException {
        A.notNull(springCfgStream, "springCfgUrl");

        boolean isLog4jUsed = U.gridClassLoader().getResource("org/apache/log4j/Appender.class") != null;

        TurboSQLBiTuple<Object, Object> t = null;

        if (isLog4jUsed) {
            try {
                t = U.addLog4jNoOpLogger();
            }
            catch (TurboSQLCheckedException ignore) {
                isLog4jUsed = false;
            }
        }

        Collection<Handler> savedHnds = null;

        if (!isLog4jUsed)
            savedHnds = U.addJavaNoOpLogger();

        TurboSQLBiTuple<Collection<TurboSQLConfiguration>, ? extends GridSpringResourceContext> cfgMap;

        try {
            cfgMap = loadConfigurations(springCfgStream);
        }
        finally {
            if (isLog4jUsed && t != null)
                U.removeLog4jNoOpLogger(t);

            if (!isLog4jUsed)
                U.removeJavaNoOpLogger(savedHnds);
        }

        return startConfigurations(cfgMap, null, turboSQLInstanceName, springCtx, ldr);
    }

    /**
     * Internal Spring-based start routine. Starts loaded configurations.
     *
     * @param cfgMap Configuration map.
     * @param springCfgUrl Spring XML configuration file URL.
     * @param turboSQLInstanceName TurboSQL instance name that will override default.
     * @param springCtx Optional Spring application context.
     * @param ldr Optional class loader that will be used by default.
     * @return Started grid.
     * @throws TurboSQLCheckedException If failed.
     */
    private static TurboSQL startConfigurations(
        TurboSQLBiTuple<Collection<TurboSQLConfiguration>, ? extends GridSpringResourceContext> cfgMap,
        URL springCfgUrl,
        @Nullable String turboSQLInstanceName,
        @Nullable GridSpringResourceContext springCtx,
        @Nullable ClassLoader ldr)
        throws TurboSQLCheckedException {
        List<TurboSQLNamedInstance> grids = new ArrayList<>(cfgMap.size());

        try {
            for (TurboSQLConfiguration cfg : cfgMap.get1()) {
                assert cfg != null;

                if (cfg.getTurboSQLInstanceName() == null && !F.isEmpty(turboSQLInstanceName))
                    cfg.setTurboSQLInstanceName(turboSQLInstanceName);

                if (ldr != null && cfg.getClassLoader() == null)
                    cfg.setClassLoader(ldr);

                // Use either user defined context or our one.
                TurboSQLNamedInstance grid = start0(
                    new GridStartContext(cfg, springCfgUrl, springCtx == null
                        ? cfgMap.get2() : springCtx), true).get1();

                // Add it if it was not stopped during startup.
                if (grid != null)
                    grids.add(grid);
            }
        }
        catch (TurboSQLCheckedException e) {
            // Stop all instances started so far.
            for (TurboSQLNamedInstance grid : grids) {
                try {
                    grid.stop(true);
                }
                catch (Exception e1) {
                    U.error(grid.log, "Error when stopping grid: " + grid, e1);
                }
            }

            throw e;
        }

        // Return the first grid started.
        TurboSQLNamedInstance res = !grids.isEmpty() ? grids.get(0) : null;

        return res != null ? res.grid() : null;
    }

    /**
     * Starts grid with given configuration.
     *
     * @param startCtx Start context.
     * @param failIfStarted Throw or not an exception if grid is already started.
     * @return Tuple with: grid instance and flag to indicate the instance is started by this call.
     *      So, when the new turboSQL instance is started the flag is {@code true}. If an existing instance is returned
     *      the flag is {@code false}.
     * @throws TurboSQLCheckedException If grid could not be started.
     */
    private static T2<TurboSQLNamedInstance, Boolean> start0(GridStartContext startCtx, boolean failIfStarted ) throws TurboSQLCheckedException {
        assert startCtx != null;

        String name = startCtx.config().getTurboSQLInstanceName();

        if (name != null && name.isEmpty())
            throw new TurboSQLCheckedException("Non default TurboSQL instances cannot have empty string name.");

        TurboSQLNamedInstance grid = new TurboSQLNamedInstance(name);

        TurboSQLNamedInstance old;

        if (name != null)
            old = grids.putIfAbsent(name, grid);
        else {
            synchronized (dfltGridMux) {
                old = dfltGrid;

                if (old == null)
                    dfltGrid = grid;
            }
        }

        if (old != null)
            if (failIfStarted) {
                if (name == null)
                    throw new TurboSQLCheckedException("Default TurboSQL instance has already been started.");
                else
                    throw new TurboSQLCheckedException("TurboSQL instance with this name has already been started: " +
                        name);
            }
            else
                return new T2<>(old, false);

        if (startCtx.config().getWarmupClosure() != null)
            startCtx.config().getWarmupClosure().apply(startCtx.config());

        startCtx.single(grids.size() == 1);

        boolean success = false;

        try {
            try {
                grid.start(startCtx);
            }
            catch (Exception e) {
                if (X.hasCause(e, TurboSQLInterruptedCheckedException.class, InterruptedException.class)) {
                    if (grid.starterThreadInterrupted)
                        Thread.interrupted();
                }

                throw e;
            }

            notifyStateChange(name, STARTED);

            success = true;
        }
        finally {
            if (!success) {
                if (name != null)
                    grids.remove(name, grid);
                else {
                    synchronized (dfltGridMux) {
                        if (dfltGrid == grid)
                            dfltGrid = null;
                    }
                }

                grid = null;
            }
        }

        if (grid == null)
            throw new TurboSQLCheckedException("Failed to start grid with provided configuration.");

        return new T2<>(grid, true);
    }

    /**
     * Loads spring bean by name.
     *
     * @param springXmlPath Spring XML file path.
     * @param beanName Bean name.
     * @return Bean instance.
     * @throws TurboSQLCheckedException In case of error.
     */
    public static <T> T loadSpringBean(String springXmlPath, String beanName) throws TurboSQLCheckedException {
        A.notNull(springXmlPath, "springXmlPath");
        A.notNull(beanName, "beanName");

        URL url = U.resolveSpringUrl(springXmlPath);

        assert url != null;

        return loadSpringBean(url, beanName);
    }

    /**
     * Loads spring bean by name.
     *
     * @param springXmlUrl Spring XML file URL.
     * @param beanName Bean name.
     * @return Bean instance.
     * @throws TurboSQLCheckedException In case of error.
     */
    public static <T> T loadSpringBean(URL springXmlUrl, String beanName) throws TurboSQLCheckedException {
        A.notNull(springXmlUrl, "springXmlUrl");
        A.notNull(beanName, "beanName");

        TurboSQLSpringHelper spring = SPRING.create(false);

        return spring.loadBean(springXmlUrl, beanName);
    }

    /**
     * Loads spring bean by name.
     *
     * @param springXmlStream Input stream containing Spring XML configuration.
     * @param beanName Bean name.
     * @return Bean instance.
     * @throws TurboSQLCheckedException In case of error.
     */
    public static <T> T loadSpringBean(InputStream springXmlStream, String beanName) throws TurboSQLCheckedException {
        A.notNull(springXmlStream, "springXmlPath");
        A.notNull(beanName, "beanName");

        TurboSQLSpringHelper spring = SPRING.create(false);

        return spring.loadBean(springXmlStream, beanName);
    }

    /**
     * Gets an instance of default no-name grid. Note that
     * caller of this method should not assume that it will return the same
     * instance every time.
     * <p>
     * This method is identical to {@code G.grid(null)} apply.
     *
     * @return An instance of default no-name grid. This method never returns
     *      {@code null}.
     * @throws TurboSQLIllegalStateException Thrown if default grid was not properly
     *      initialized or grid instance was stopped or was not started.
     */
    public static TurboSQL grid() throws TurboSQLIllegalStateException {
        return grid((String)null);
    }

    /**
     * Gets a list of all grids started so far.
     *
     * @return List of all grids started so far.
     */
    public static List<TurboSQL> allGrids() {
        return allGrids(true);
    }

    /**
     * Gets a list of all grids started so far.
     *
     * @return List of all grids started so far.
     */
    public static List<TurboSQL> allGridsx() {
        return allGrids(false);
    }

    /**
     * Gets a list of all grids started so far.
     *
     * @param wait If {@code true} wait for node start finish.
     * @return List of all grids started so far.
     */
    private static List<TurboSQL> allGrids(boolean wait) {
        List<TurboSQL> allTurboSQLs = new ArrayList<>(grids.size() + 1);

        for (TurboSQLNamedInstance grid : grids.values()) {
            TurboSQL g = wait ? grid.grid() : grid.gridx();

            if (g != null)
                allTurboSQLs.add(g);
        }

        TurboSQLNamedInstance dfltGrid0 = dfltGrid;

        if (dfltGrid0 != null) {
            TurboSQLKernal g = wait ? dfltGrid0.grid() : dfltGrid0.gridx();

            if (g != null)
                allTurboSQLs.add(g);
        }

        return allTurboSQLs;
    }

    /**
     * Gets a grid instance for given local node ID. Note that grid instance and local node have
     * one-to-one relationship where node has ID and instance has name of the grid to which
     * both grid instance and its node belong. Note also that caller of this method
     * should not assume that it will return the same instance every time.
     *
     * @param locNodeId ID of local node the requested grid instance is managing.
     * @return An instance of named grid. This method never returns
     *      {@code null}.
     * @throws TurboSQLIllegalStateException Thrown if grid was not properly
     *      initialized or grid instance was stopped or was not started.
     */
    public static TurboSQL grid(UUID locNodeId) throws TurboSQLIllegalStateException {
        A.notNull(locNodeId, "locNodeId");

        TurboSQLNamedInstance dfltGrid0 = dfltGrid;

        if (dfltGrid0 != null) {
            TurboSQLKernal g = dfltGrid0.grid();

            if (g != null && g.getLocalNodeId().equals(locNodeId))
                return g;
        }

        for (TurboSQLNamedInstance grid : grids.values()) {
            TurboSQLKernal g = grid.grid();

            if (g != null && g.getLocalNodeId().equals(locNodeId))
                return g;
        }

        throw new TurboSQLIllegalStateException("Grid instance with given local node ID was not properly " +
            "started or was stopped: " + locNodeId);
    }

    /**
     * Gets grid instance without waiting its initialization and not throwing any exception.
     *
     * @param locNodeId ID of local node the requested grid instance is managing.
     * @return Grid instance or {@code null}.
     */
    public static TurboSQLKernal gridxx(UUID locNodeId) {
        TurboSQLNamedInstance dfltGrid0 = dfltGrid;

        if (dfltGrid0 != null) {
            TurboSQLKernal g = dfltGrid0.grid();

            if (g != null && g.getLocalNodeId().equals(locNodeId))
                return g;
        }

        for (TurboSQLNamedInstance grid : grids.values()) {
            TurboSQLKernal g = grid.grid();

            if (g != null && g.getLocalNodeId().equals(locNodeId))
                return g;
        }

        return null;
    }

    /**
     * Gets an named grid instance. If grid name is {@code null} or empty string,
     * then default no-name grid will be returned. Note that caller of this method
     * should not assume that it will return the same instance every time.
     * <p>
     * Note that Java VM can run multiple grid instances and every grid instance (and its
     * node) can belong to a different grid. Grid name defines what grid a particular grid
     * instance (and correspondingly its node) belongs to.
     *
     * @param name Grid name to which requested grid instance belongs to. If {@code null},
     *      then grid instance belonging to a default no-name grid will be returned.
     * @return An instance of named grid. This method never returns
     *      {@code null}.
     * @throws TurboSQLIllegalStateException Thrown if default grid was not properly
     *      initialized or grid instance was stopped or was not started.
     */
    public static TurboSQL grid(@Nullable String name) throws TurboSQLIllegalStateException {
        TurboSQLNamedInstance grid = name != null ? grids.get(name) : dfltGrid;

        TurboSQL res;

        if (grid == null || (res = grid.grid()) == null)
            throw new TurboSQLIllegalStateException("TurboSQL instance with provided name doesn't exist. " +
                "Did you call Ignition.start(..) to start an TurboSQL instance? [name=" + name + ']');

        return res;
    }

    /**
     * Gets a name of the grid from thread local config, which is owner of current thread.
     *
     * @return Grid instance related to current thread
     * @throws IllegalArgumentException Thrown to indicate, that current thread is not an {@link TurboSQLThread}.
     */
    public static TurboSQLKernal localTurboSQL() throws IllegalArgumentException {
        String name = U.getCurrentTurboSQLName();

        if (U.isCurrentTurboSQLNameSet(name))
            return gridx(name);
        else if (Thread.currentThread() instanceof TurboSQLThread)
            return gridx(((TurboSQLThread)Thread.currentThread()).getTurboSQLInstanceName());
        else
            throw new IllegalArgumentException("TurboSQL instance name thread local must be set or" +
                " this method should be accessed under " + TurboSQLThread.class.getName());
    }

    /**
     * Gets grid instance without waiting its initialization.
     *
     * @param name Grid name.
     * @return Grid instance.
     */
    public  static TurboSQLKernal gridx(@Nullable String name) {
        TurboSQLNamedInstance grid = name != null ? grids.get(name) : dfltGrid;

        TurboSQLKernal res;

        if (grid == null || (res = grid.gridx()) == null)
            throw new TurboSQLIllegalStateException("TurboSQL instance with provided name doesn't exist. " +
                "Did you call Ignition.start(..) to start an TurboSQL instance? [name=" + name + ']');

        return res;
    }

    /**
     * Adds a lsnr for grid life cycle events.
     * <p>
     * Note that unlike other listeners in TurboSQL this listener will be
     * notified from the same thread that triggers the state change. Because of
     * that it is the responsibility of the user to make sure that listener logic
     * is light-weight and properly handles (catches) any runtime exceptions, if any
     * are expected.
     *
     * @param lsnr Listener for grid life cycle events. If this listener was already added
     *      this method is no-op.
     */
    public static void addListener(IgnitionListener lsnr) {
        A.notNull(lsnr, "lsnr");

        lsnrs.add(lsnr);
    }

    /**
     * Removes lsnr added by {@link #addListener(IgnitionListener)} method.
     *
     * @param lsnr Listener to remove.
     * @return {@code true} if lsnr was added before, {@code false} otherwise.
     */
    public static boolean removeListener(IgnitionListener lsnr) {
        A.notNull(lsnr, "lsnr");

        return lsnrs.remove(lsnr);
    }

    /**
     * @param turboSQLInstanceName TurboSQL instance name.
     * @param state Factory state.
     */
    private static void notifyStateChange(@Nullable String turboSQLInstanceName, TurboSQLState state) {
        if (turboSQLInstanceName != null)
            gridStates.put(turboSQLInstanceName, state);
        else
            dfltGridState = state;

        for (IgnitionListener lsnr : lsnrs)
            lsnr.onStateChange(turboSQLInstanceName, state);
    }

    /**
     * Start context encapsulates all starting parameters.
     */
    private static final class GridStartContext {
        /** User-defined configuration. */
        private TurboSQLConfiguration cfg;

        /** Optional configuration path. */
        private URL cfgUrl;

        /** Optional Spring application context. */
        private GridSpringResourceContext springCtx;

        /** Whether or not this is a single grid instance in current VM. */
        private boolean single;

        /**
         *
         * @param cfg User-defined configuration.
         * @param cfgUrl Optional configuration path.
         * @param springCtx Optional Spring application context.
         */
        GridStartContext(TurboSQLConfiguration cfg, @Nullable URL cfgUrl, @Nullable GridSpringResourceContext springCtx) {
            assert(cfg != null);

            this.cfg = cfg;
            this.cfgUrl = cfgUrl;
            this.springCtx = springCtx;
        }

        /**
         * @return Whether or not this is a single grid instance in current VM.
         */
        public boolean single() {
            return single;
        }

        /**
         * @param single Whether or not this is a single grid instance in current VM.
         */
        public void single(boolean single) {
            this.single = single;
        }

        /**
         * @return User-defined configuration.
         */
        TurboSQLConfiguration config() {
            return cfg;
        }

        /**
         * @param cfg User-defined configuration.
         */
        void config(TurboSQLConfiguration cfg) {
            this.cfg = cfg;
        }

        /**
         * @return Optional configuration path.
         */
        URL configUrl() {
            return cfgUrl;
        }

        /**
         * @param cfgUrl Optional configuration path.
         */
        void configUrl(URL cfgUrl) {
            this.cfgUrl = cfgUrl;
        }

        /**
         * @return Optional Spring application context.
         */
        public GridSpringResourceContext springContext() {
            return springCtx;
        }
    }

    /**
     * Grid data container.
     */
    private static final class TurboSQLNamedInstance {
        /** Map of registered MBeans. */
        private static final Map<MBeanServer, GridMBeanServerData> mbeans =
            new HashMap<>();

        /** */
        private static final String[] EMPTY_STR_ARR = new String[0];

        /** Grid name. */
        private final String name;

        /** Grid instance. */
        private volatile TurboSQLKernal grid;

        /** Executor service. */
        private ThreadPoolExecutor execSvc;

        /** Executor service for services. */
        private ThreadPoolExecutor svcExecSvc;

        /** System executor service. */
        private ThreadPoolExecutor sysExecSvc;

        /** */
        private StripedExecutor stripedExecSvc;

        /** Management executor service. */
        private ThreadPoolExecutor mgmtExecSvc;

        /** P2P executor service. */
        private ThreadPoolExecutor p2pExecSvc;

        /** IGFS executor service. */
        private ThreadPoolExecutor igfsExecSvc;

        /** Data streamer executor service. */
        private StripedExecutor dataStreamerExecSvc;

        /** REST requests executor service. */
        private ThreadPoolExecutor restExecSvc;

        /** Utility cache executor service. */
        private ThreadPoolExecutor utilityCacheExecSvc;

        /** Affinity executor service. */
        private ThreadPoolExecutor affExecSvc;

        /** Indexing pool. */
        private ThreadPoolExecutor idxExecSvc;

        /** Continuous query executor service. */
        private TurboSQLStripedThreadPoolExecutor callbackExecSvc;

        /** Query executor service. */
        private ThreadPoolExecutor qryExecSvc;

        /** Query executor service. */
        private ThreadPoolExecutor schemaExecSvc;

        /** Executor service. */
        private Map<String, ThreadPoolExecutor> customExecSvcs;

        /** Grid state. */
        private volatile TurboSQLState state = STOPPED;

        /** Shutdown hook. */
        private Thread shutdownHook;

        /** Grid log. */
        private TurboSQLLogger log;

        /** Start guard. */
        private final AtomicBoolean startGuard = new AtomicBoolean();

        /** Start latch. */
        private final CountDownLatch startLatch = new CountDownLatch(1);

        /**
         * Thread that starts this named instance. This field can be non-volatile since
         * it makes sense only for thread where it was originally initialized.
         */
        @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
        private Thread starterThread;

        /** */
        private boolean starterThreadInterrupted;

        /**
         * Creates un-started named instance.
         *
         * @param name Grid name (possibly {@code null} for default grid).
         */
        TurboSQLNamedInstance(@Nullable String name) {
            this.name = name;
        }

        /**
         * Gets grid name.
         *
         * @return Grid name.
         */
        String getName() {
            return name;
        }

        /**
         * Gets grid instance.
         *
         * @return Grid instance.
         */
        TurboSQLKernal grid() {
            if (starterThread != Thread.currentThread())
                U.awaitQuiet(startLatch);

            return grid;
        }

        /**
         * Gets grid instance without waiting for its initialization.
         *
         * @return Grid instance.
         */
        public TurboSQLKernal gridx() {
            return grid;
        }

        /**
         * Gets grid state.
         *
         * @return Grid state.
         */
        TurboSQLState state() {
            if (starterThread != Thread.currentThread())
                U.awaitQuiet(startLatch);

            return state;
        }

        /**
         * @param spi SPI implementation.
         * @throws TurboSQLCheckedException Thrown in case if multi-instance is not supported.
         */
        private void ensureMultiInstanceSupport(TurboSQLSpi spi) throws TurboSQLCheckedException {
            TurboSQLSpiMultipleInstancesSupport ann = U.getAnnotation(spi.getClass(),
                TurboSQLSpiMultipleInstancesSupport.class);

            if (ann == null || !ann.value())
                throw new TurboSQLCheckedException("SPI implementation doesn't support multiple grid instances in " +
                    "the same VM: " + spi);
        }

        /**
         * @param spis SPI implementations.
         * @throws TurboSQLCheckedException Thrown in case if multi-instance is not supported.
         */
        private void ensureMultiInstanceSupport(TurboSQLSpi[] spis) throws TurboSQLCheckedException {
            for (TurboSQLSpi spi : spis)
                ensureMultiInstanceSupport(spi);
        }

        /**
         * Starts grid with given configuration.
         *
         * @param startCtx Starting context.
         * @throws TurboSQLCheckedException If start failed.
         */
        synchronized void start(GridStartContext startCtx) throws TurboSQLCheckedException {
            if (startGuard.compareAndSet(false, true)) {
                try {
                    starterThread = Thread.currentThread();

                    start0(startCtx);
                }
                catch (Exception e) {
                    if (log != null)
                        stopExecutors(log);

                    throw e;
                }
                finally {
                    startLatch.countDown();
                }
            }
            else
                U.awaitQuiet(startLatch);
        }

        /**
         * @param startCtx Starting context.
         * @throws TurboSQLCheckedException If start failed.
         */
        private void start0(GridStartContext startCtx) throws TurboSQLCheckedException {
            assert grid == null : "Grid is already started: " + name;

            TurboSQLConfiguration cfg = startCtx.config() != null ? startCtx.config() : new TurboSQLConfiguration();

            TurboSQLConfiguration myCfg = initializeConfiguration(cfg);

            // Set configuration URL, if any, into system property.
            if (startCtx.configUrl() != null)
                System.setProperty(IGNITE_CONFIG_URL, startCtx.configUrl().toString());

            // Ensure that SPIs support multiple grid instances, if required.
            if (!startCtx.single()) {
                ensureMultiInstanceSupport(myCfg.getDeploymentSpi());
                ensureMultiInstanceSupport(myCfg.getCommunicationSpi());
                ensureMultiInstanceSupport(myCfg.getDiscoverySpi());
                ensureMultiInstanceSupport(myCfg.getCheckpointSpi());
                ensureMultiInstanceSupport(myCfg.getEventStorageSpi());
                ensureMultiInstanceSupport(myCfg.getCollisionSpi());
                ensureMultiInstanceSupport(myCfg.getFailoverSpi());
                ensureMultiInstanceSupport(myCfg.getLoadBalancingSpi());
            }

            validateThreadPoolSize(cfg.getPublicThreadPoolSize(), "public");

            UncaughtExceptionHandler oomeHnd = new UncaughtExceptionHandler() {
                @Override public void uncaughtException(Thread t, Throwable e) {
                    if (grid != null && X.hasCause(e, OutOfMemoryError.class))
                        grid.context().failure().process(new FailureContext(FailureType.CRITICAL_ERROR, e));
                }
            };

            execSvc = new TurboSQLThreadPoolExecutor(
                "pub",
                cfg.getTurboSQLInstanceName(),
                cfg.getPublicThreadPoolSize(),
                cfg.getPublicThreadPoolSize(),
                DFLT_THREAD_KEEP_ALIVE_TIME,
                new LinkedBlockingQueue<Runnable>(),
                GridIoPolicy.PUBLIC_POOL,
                oomeHnd);

            execSvc.allowCoreThreadTimeOut(true);

            validateThreadPoolSize(cfg.getServiceThreadPoolSize(), "service");

            svcExecSvc = new TurboSQLThreadPoolExecutor(
                "svc",
                cfg.getGridName(),
                cfg.getServiceThreadPoolSize(),
                cfg.getServiceThreadPoolSize(),
                DFLT_THREAD_KEEP_ALIVE_TIME,
                new LinkedBlockingQueue<Runnable>(),
                GridIoPolicy.SERVICE_POOL,
                oomeHnd);

            svcExecSvc.allowCoreThreadTimeOut(true);

            validateThreadPoolSize(cfg.getSystemThreadPoolSize(), "system");

            sysExecSvc = new TurboSQLThreadPoolExecutor(
                "sys",
                cfg.getTurboSQLInstanceName(),
                cfg.getSystemThreadPoolSize(),
                cfg.getSystemThreadPoolSize(),
                DFLT_THREAD_KEEP_ALIVE_TIME,
                new LinkedBlockingQueue<Runnable>(),
                GridIoPolicy.SYSTEM_POOL,
                oomeHnd);

            sysExecSvc.allowCoreThreadTimeOut(true);

            validateThreadPoolSize(cfg.getStripedPoolSize(), "stripedPool");

            WorkersRegistry workerRegistry = new WorkersRegistry(
                new TurboSQLBiInClosure<GridWorker, FailureType>() {
                    @Override public void apply(GridWorker deadWorker, FailureType failureType) {
                        if (grid != null)
                            grid.context().failure().process(new FailureContext(
                                failureType,
                                new TurboSQLException(S.toString(GridWorker.class, deadWorker))));
                    }
                },
                TurboSQLSystemProperties.getLong(IGNITE_SYSTEM_WORKER_BLOCKED_TIMEOUT,
                    cfg.getSystemWorkerBlockedTimeout() != null
                    ? cfg.getSystemWorkerBlockedTimeout()
                    : cfg.getFailureDetectionTimeout()),
                log);

            stripedExecSvc = new StripedExecutor(
                cfg.getStripedPoolSize(),
                cfg.getTurboSQLInstanceName(),
                "sys",
                log,
                new TurboSQLInClosure<Throwable>() {
                    @Override public void apply(Throwable t) {
                        if (grid != null)
                            grid.context().failure().process(new FailureContext(SYSTEM_WORKER_TERMINATION, t));
                    }
                },
                workerRegistry,
                cfg.getFailureDetectionTimeout());

            // Note that since we use 'LinkedBlockingQueue', number of
            // maximum threads has no effect.
            // Note, that we do not pre-start threads here as management pool may
            // not be needed.
            validateThreadPoolSize(cfg.getManagementThreadPoolSize(), "management");

            mgmtExecSvc = new TurboSQLThreadPoolExecutor(
                "mgmt",
                cfg.getTurboSQLInstanceName(),
                cfg.getManagementThreadPoolSize(),
                cfg.getManagementThreadPoolSize(),
                DFLT_THREAD_KEEP_ALIVE_TIME,
                new LinkedBlockingQueue<Runnable>(),
                GridIoPolicy.MANAGEMENT_POOL,
                oomeHnd);

            mgmtExecSvc.allowCoreThreadTimeOut(true);

            // Note that since we use 'LinkedBlockingQueue', number of
            // maximum threads has no effect.
            // Note, that we do not pre-start threads here as class loading pool may
            // not be needed.
            validateThreadPoolSize(cfg.getPeerClassLoadingThreadPoolSize(), "peer class loading");
            p2pExecSvc = new TurboSQLThreadPoolExecutor(
                "p2p",
                cfg.getTurboSQLInstanceName(),
                cfg.getPeerClassLoadingThreadPoolSize(),
                cfg.getPeerClassLoadingThreadPoolSize(),
                DFLT_THREAD_KEEP_ALIVE_TIME,
                new LinkedBlockingQueue<Runnable>(),
                GridIoPolicy.P2P_POOL,
                oomeHnd);

            p2pExecSvc.allowCoreThreadTimeOut(true);

            dataStreamerExecSvc = new StripedExecutor(
                cfg.getDataStreamerThreadPoolSize(),
                cfg.getTurboSQLInstanceName(),
                "data-streamer",
                log,
                new TurboSQLInClosure<Throwable>() {
                    @Override public void apply(Throwable t) {
                        if (grid != null)
                            grid.context().failure().process(new FailureContext(SYSTEM_WORKER_TERMINATION, t));
                    }
                },
                true,
                workerRegistry,
                cfg.getFailureDetectionTimeout());

            // Note that we do not pre-start threads here as igfs pool may not be needed.
            validateThreadPoolSize(cfg.getIgfsThreadPoolSize(), "IGFS");

            igfsExecSvc = new TurboSQLThreadPoolExecutor(
                cfg.getIgfsThreadPoolSize(),
                cfg.getIgfsThreadPoolSize(),
                DFLT_THREAD_KEEP_ALIVE_TIME,
                new LinkedBlockingQueue<>(),
                new IgfsThreadFactory(cfg.getTurboSQLInstanceName(), "igfs"));

            igfsExecSvc.allowCoreThreadTimeOut(true);

            // Note that we do not pre-start threads here as this pool may not be needed.
            validateThreadPoolSize(cfg.getAsyncCallbackPoolSize(), "async callback");

            callbackExecSvc = new TurboSQLStripedThreadPoolExecutor(
                cfg.getAsyncCallbackPoolSize(),
                cfg.getTurboSQLInstanceName(),
                "callback",
                oomeHnd);

            if (myCfg.getConnectorConfiguration() != null) {
                validateThreadPoolSize(myCfg.getConnectorConfiguration().getThreadPoolSize(), "connector");

                restExecSvc = new TurboSQLThreadPoolExecutor(
                    "rest",
                    myCfg.getTurboSQLInstanceName(),
                    myCfg.getConnectorConfiguration().getThreadPoolSize(),
                    myCfg.getConnectorConfiguration().getThreadPoolSize(),
                    DFLT_THREAD_KEEP_ALIVE_TIME,
                    new LinkedBlockingQueue<>(),
                    GridIoPolicy.UNDEFINED,
                    oomeHnd
                );

                restExecSvc.allowCoreThreadTimeOut(true);
            }

            validateThreadPoolSize(myCfg.getUtilityCacheThreadPoolSize(), "utility cache");

            utilityCacheExecSvc = new TurboSQLThreadPoolExecutor(
                "utility",
                cfg.getTurboSQLInstanceName(),
                myCfg.getUtilityCacheThreadPoolSize(),
                myCfg.getUtilityCacheThreadPoolSize(),
                myCfg.getUtilityCacheKeepAliveTime(),
                new LinkedBlockingQueue<>(),
                GridIoPolicy.UTILITY_CACHE_POOL,
                oomeHnd);

            utilityCacheExecSvc.allowCoreThreadTimeOut(true);

            affExecSvc = new TurboSQLThreadPoolExecutor(
                "aff",
                cfg.getTurboSQLInstanceName(),
                1,
                1,
                DFLT_THREAD_KEEP_ALIVE_TIME,
                new LinkedBlockingQueue<>(),
                GridIoPolicy.AFFINITY_POOL,
                oomeHnd);

            affExecSvc.allowCoreThreadTimeOut(true);

            if (TurboSQLComponentType.INDEXING.inClassPath()) {
                int cpus = Runtime.getRuntime().availableProcessors();

                idxExecSvc = new TurboSQLThreadPoolExecutor(
                    "idx",
                    cfg.getTurboSQLInstanceName(),
                    cpus,
                    cpus * 2,
                    3000L,
                    new LinkedBlockingQueue<>(1000),
                    GridIoPolicy.IDX_POOL,
                    oomeHnd
                );
            }

            validateThreadPoolSize(cfg.getQueryThreadPoolSize(), "query");

            qryExecSvc = new TurboSQLThreadPoolExecutor(
                "query",
                cfg.getTurboSQLInstanceName(),
                cfg.getQueryThreadPoolSize(),
                cfg.getQueryThreadPoolSize(),
                DFLT_THREAD_KEEP_ALIVE_TIME,
                new LinkedBlockingQueue<>(),
                GridIoPolicy.QUERY_POOL,
                oomeHnd);

            qryExecSvc.allowCoreThreadTimeOut(true);

            schemaExecSvc = new TurboSQLThreadPoolExecutor(
                "schema",
                cfg.getTurboSQLInstanceName(),
                2,
                2,
                DFLT_THREAD_KEEP_ALIVE_TIME,
                new LinkedBlockingQueue<>(),
                GridIoPolicy.SCHEMA_POOL,
                oomeHnd);

            schemaExecSvc.allowCoreThreadTimeOut(true);

            if (!F.isEmpty(cfg.getExecutorConfiguration())) {
                validateCustomExecutorsConfiguration(cfg.getExecutorConfiguration());

                customExecSvcs = new HashMap<>();

                for(ExecutorConfiguration execCfg : cfg.getExecutorConfiguration()) {
                    ThreadPoolExecutor exec = new TurboSQLThreadPoolExecutor(
                        execCfg.getName(),
                        cfg.getTurboSQLInstanceName(),
                        execCfg.getSize(),
                        execCfg.getSize(),
                        DFLT_THREAD_KEEP_ALIVE_TIME,
                        new LinkedBlockingQueue<>(),
                        GridIoPolicy.UNDEFINED,
                        oomeHnd);

                    customExecSvcs.put(execCfg.getName(), exec);
                }
            }

            // Register TurboSQL MBean for current grid instance.
            registerFactoryMbean(myCfg.getMBeanServer());

            boolean started = false;

            try {
                TurboSQLKernal grid0 = new TurboSQLKernal(startCtx.springContext());

                // Init here to make grid available to lifecycle listeners.
                grid = grid0;

                grid0.start(
                    myCfg,
                    utilityCacheExecSvc,
                    execSvc,
                    svcExecSvc,
                    sysExecSvc,
                    stripedExecSvc,
                    p2pExecSvc,
                    mgmtExecSvc,
                    igfsExecSvc,
                    dataStreamerExecSvc,
                    restExecSvc,
                    affExecSvc,
                    idxExecSvc,
                    callbackExecSvc,
                    qryExecSvc,
                    schemaExecSvc,
                    customExecSvcs,
                    new CA() {
                        @Override public void apply() {
                            startLatch.countDown();
                        }
                    },
                    workerRegistry,
                    oomeHnd
                );

                state = STARTED;

                if (log.isDebugEnabled())
                    log.debug("Grid factory started ok: " + name);

                started = true;
            }
            catch (TurboSQLCheckedException e) {
                unregisterFactoryMBean();

                throw e;
            }
            // Catch Throwable to protect against any possible failure.
            catch (Throwable e) {
                unregisterFactoryMBean();

                if (e instanceof Error)
                    throw e;

                throw new TurboSQLCheckedException("Unexpected exception when starting grid.", e);
            }
            finally {
                if (!started)
                    // Grid was not started.
                    grid = null;
            }

            // Do NOT set it up only if IGNITE_NO_SHUTDOWN_HOOK=TRUE is provided.
            if (!TurboSQLSystemProperties.getBoolean(IGNITE_NO_SHUTDOWN_HOOK, false)) {
                try {
                    Runtime.getRuntime().addShutdownHook(shutdownHook = new Thread() {
                        @Override public void run() {
                            if (log.isInfoEnabled())
                                log.info("Invoking shutdown hook...");

                            TurboSQLNamedInstance.this.stop(true);
                        }
                    });

                    if (log.isDebugEnabled())
                        log.debug("Shutdown hook is installed.");
                }
                catch (IllegalStateException e) {
                    stop(true);

                    throw new TurboSQLCheckedException("Failed to install shutdown hook.", e);
                }
            }
            else {
                if (log.isDebugEnabled())
                    log.debug("Shutdown hook has not been installed because environment " +
                        "or system property " + IGNITE_NO_SHUTDOWN_HOOK + " is set.");
            }
        }

        /**
         * @param poolSize an actual value in the configuration.
         * @param poolName a name of the pool like 'management'.
         * @throws TurboSQLCheckedException If the poolSize is wrong.
         */
        private static void validateThreadPoolSize(int poolSize, String poolName)
            throws TurboSQLCheckedException {
            if (poolSize <= 0) {
                throw new TurboSQLCheckedException("Invalid " + poolName + " thread pool size" +
                    " (must be greater than 0), actual value: " + poolSize);
            }
        }

        /**
         * @param cfgs Array of the executors configurations.
         * @throws TurboSQLCheckedException If configuration is wrong.
         */
        private static void validateCustomExecutorsConfiguration(ExecutorConfiguration[] cfgs)
            throws TurboSQLCheckedException {
            if (cfgs == null)
                return;

            Set<String> names = new HashSet<>(cfgs.length);

            for (ExecutorConfiguration cfg : cfgs) {
                if (F.isEmpty(cfg.getName()))
                    throw new TurboSQLCheckedException("Custom executor name cannot be null or empty.");

                if (!names.add(cfg.getName()))
                    throw new TurboSQLCheckedException("Duplicate custom executor name: " + cfg.getName());

                if (cfg.getSize() <= 0)
                    throw new TurboSQLCheckedException("Custom executor size must be positive [name=" + cfg.getName() +
                        ", size=" + cfg.getSize() + ']');
            }
        }

        /**
         * @param cfg TurboSQL configuration copy to.
         * @return New turboSQL configuration.
         * @throws TurboSQLCheckedException If failed.
         */
        private TurboSQLConfiguration initializeConfiguration(TurboSQLConfiguration cfg)
            throws TurboSQLCheckedException {
            TurboSQLConfiguration myCfg = new TurboSQLConfiguration(cfg);

            String ggHome = cfg.getTurboSQLHome();

            // Set TurboSQL home.
            if (ggHome == null)
                ggHome = U.getTurboSQLHome();
            else
                // If user provided TURBOSQL_HOME - set it as a system property.
                U.setTurboSQLHome(ggHome);

            // Correctly resolve work directory and set it back to configuration.
            String workDir = U.workDirectory(cfg.getWorkDirectory(), ggHome);

            myCfg.setWorkDirectory(workDir);

            // Ensure invariant.
            // It's a bit dirty - but this is a result of late refactoring
            // and I don't want to reshuffle a lot of code.
            assert F.eq(name, cfg.getTurboSQLInstanceName());

            UUID nodeId = cfg.getNodeId() != null ? cfg.getNodeId() : UUID.randomUUID();

            myCfg.setNodeId(nodeId);

            String predefineConsistentId = TurboSQLSystemProperties.getString(IGNITE_OVERRIDE_CONSISTENT_ID);

            if (!F.isEmpty(predefineConsistentId))
                myCfg.setConsistentId(predefineConsistentId);

            TurboSQLLogger cfgLog = initLogger(cfg.getGridLogger(), nodeId, workDir);

            assert cfgLog != null;

            cfgLog = new GridLoggerProxy(cfgLog, null, name, U.id8(nodeId));

            // Initialize factory's log.
            log = cfgLog.getLogger(G.class);

            myCfg.setGridLogger(cfgLog);

            // Check TurboSQL home folder (after log is available).
            if (ggHome != null) {
                File ggHomeFile = new File(ggHome);

                if (!ggHomeFile.exists() || !ggHomeFile.isDirectory())
                    throw new TurboSQLCheckedException("Invalid TurboSQL installation home folder: " + ggHome);
            }

            myCfg.setTurboSQLHome(ggHome);

            // Validate segmentation configuration.
            SegmentationPolicy segPlc = cfg.getSegmentationPolicy();

            // 1. Warn on potential configuration problem: grid is not configured to wait
            // for correct segment after segmentation happens.
            if (!F.isEmpty(cfg.getSegmentationResolvers()) && segPlc == RESTART_JVM && !cfg.isWaitForSegmentOnStart()) {
                U.warn(log, "Found potential configuration problem (forgot to enable waiting for segment" +
                    "on start?) [segPlc=" + segPlc + ", wait=false]");
            }

            if (CU.isPersistenceEnabled(cfg) && myCfg.getConsistentId() == null)
                U.warn(log, "Consistent ID is not set, it is recommended to set consistent ID for production " +
                    "clusters (use TurboSQLConfiguration.setConsistentId property)");

            myCfg.setTransactionConfiguration(myCfg.getTransactionConfiguration() != null ?
                new TransactionConfiguration(myCfg.getTransactionConfiguration()) : null);

            myCfg.setConnectorConfiguration(myCfg.getConnectorConfiguration() != null ?
                new ConnectorConfiguration(myCfg.getConnectorConfiguration()) : null);

            // Local host.
            String locHost = TurboSQLSystemProperties.getString(IGNITE_LOCAL_HOST);

            myCfg.setLocalHost(F.isEmpty(locHost) ? myCfg.getLocalHost() : locHost);

            // Override daemon flag if it was set on the factory.
            if (daemon.get())
                myCfg.setDaemon(true);

            if (myCfg.isClientMode() == null) {
                Boolean threadClient = clientMode.get();

                if (threadClient == null)
                    myCfg.setClientMode(TurboSQLSystemProperties.getBoolean(IGNITE_CACHE_CLIENT, false));
                else
                    myCfg.setClientMode(threadClient);
            }

            // Check for deployment mode override.
            String depModeName = TurboSQLSystemProperties.getString(IGNITE_DEP_MODE_OVERRIDE);

            if (!F.isEmpty(depModeName)) {
                if (!F.isEmpty(myCfg.getCacheConfiguration())) {
                    U.quietAndInfo(log, "Skipping deployment mode override for caches (custom closure " +
                        "execution may not work for console Visor)");
                }
                else {
                    try {
                        DeploymentMode depMode = DeploymentMode.valueOf(depModeName);

                        if (myCfg.getDeploymentMode() != depMode)
                            myCfg.setDeploymentMode(depMode);
                    }
                    catch (IllegalArgumentException e) {
                        throw new TurboSQLCheckedException("Failed to override deployment mode using system property " +
                            "(are there any misspellings?)" +
                            "[name=" + IGNITE_DEP_MODE_OVERRIDE + ", value=" + depModeName + ']', e);
                    }
                }
            }

            if (myCfg.getUserAttributes() == null)
                myCfg.setUserAttributes(Collections.<String, Object>emptyMap());

            if (myCfg.getMBeanServer() == null && !U.IGNITE_MBEANS_DISABLED)
                myCfg.setMBeanServer(ManagementFactory.getPlatformMBeanServer());

            Marshaller marsh = myCfg.getMarshaller();

            if (marsh == null) {
                if (!BinaryMarshaller.available()) {
                    U.warn(log, "OptimizedMarshaller is not supported on this JVM " +
                        "(only recent 1.6 and 1.7 versions HotSpot VMs are supported). " +
                        "To enable fast marshalling upgrade to recent 1.6 or 1.7 HotSpot VM release. " +
                        "Switching to standard JDK marshalling - " +
                        "object serialization performance will be significantly slower.");

                    marsh = new JdkMarshaller();
                }
                else
                    marsh = new BinaryMarshaller();
            }

            MarshallerUtils.setNodeName(marsh, cfg.getTurboSQLInstanceName());

            myCfg.setMarshaller(marsh);

            if (myCfg.getPeerClassLoadingLocalClassPathExclude() == null)
                myCfg.setPeerClassLoadingLocalClassPathExclude(EMPTY_STR_ARR);

            FileSystemConfiguration[] igfsCfgs = myCfg.getFileSystemConfiguration();

            if (igfsCfgs != null) {
                FileSystemConfiguration[] clone = igfsCfgs.clone();

                for (int i = 0; i < igfsCfgs.length; i++)
                    clone[i] = new FileSystemConfiguration(igfsCfgs[i]);

                myCfg.setFileSystemConfiguration(clone);
            }

            initializeDefaultSpi(myCfg);

            GridDiscoveryManager.initCommunicationErrorResolveConfiguration(myCfg);

            initializeDefaultCacheConfiguration(myCfg);

            ExecutorConfiguration[] execCfgs = myCfg.getExecutorConfiguration();

            if (execCfgs != null) {
                ExecutorConfiguration[] clone = execCfgs.clone();

                for (int i = 0; i < execCfgs.length; i++)
                    clone[i] = new ExecutorConfiguration(execCfgs[i]);

                myCfg.setExecutorConfiguration(clone);
            }

            initializeDataStorageConfiguration(myCfg);

            return myCfg;
        }

        /**
         * @param cfg TurboSQL configuration.
         */
        private void initializeDataStorageConfiguration(TurboSQLConfiguration cfg) throws TurboSQLCheckedException {
            if (cfg.getDataStorageConfiguration() != null &&
                (cfg.getMemoryConfiguration() != null || cfg.getPersistentStoreConfiguration() != null)) {
                throw new TurboSQLCheckedException("Data storage can be configured with either legacy " +
                    "(MemoryConfiguration, PersistentStoreConfiguration) or new (DataStorageConfiguration) classes, " +
                    "but not both.");
            }

            if (cfg.getMemoryConfiguration() != null || cfg.getPersistentStoreConfiguration() != null)
                convertLegacyDataStorageConfigurationToNew(cfg);

            if (!cfg.isClientMode() && cfg.getDataStorageConfiguration() == null)
                cfg.setDataStorageConfiguration(new DataStorageConfiguration());
        }

        /**
         * Initialize default cache configuration.
         *
         * @param cfg TurboSQL configuration.
         * @throws TurboSQLCheckedException If failed.
         */
        public void initializeDefaultCacheConfiguration(TurboSQLConfiguration cfg) throws TurboSQLCheckedException {
            List<CacheConfiguration> cacheCfgs = new ArrayList<>();

            cacheCfgs.add(utilitySystemCache());

            if (TurboSQLComponentType.HADOOP.inClassPath())
                cacheCfgs.add(CU.hadoopSystemCache());

            CacheConfiguration[] userCaches = cfg.getCacheConfiguration();

            if (userCaches != null && userCaches.length > 0) {
                if (!U.discoOrdered(cfg.getDiscoverySpi()) && !U.relaxDiscoveryOrdered())
                    throw new TurboSQLCheckedException("Discovery SPI implementation does not support node ordering and " +
                        "cannot be used with cache (use SPI with @DiscoverySpiOrderSupport annotation, " +
                        "like TcpDiscoverySpi)");

                for (CacheConfiguration ccfg : userCaches) {
                    if (CU.isReservedCacheName(ccfg.getName()))
                        throw new TurboSQLCheckedException("Cache name cannot be \"" + ccfg.getName() +
                            "\" because it is reserved for internal purposes.");

                    if (IgfsUtils.matchIgfsCacheName(ccfg.getName()))
                        throw new TurboSQLCheckedException(
                            "Cache name cannot start with \""+ IgfsUtils.IGFS_CACHE_PREFIX
                                + "\" because it is reserved for IGFS internal purposes.");

                    if (DataStructuresProcessor.isDataStructureCache(ccfg.getName()))
                        throw new TurboSQLCheckedException("Cache name cannot be \"" + ccfg.getName() +
                            "\" because it is reserved for data structures.");

                    cacheCfgs.add(ccfg);
                }
            }

            cfg.setCacheConfiguration(cacheCfgs.toArray(new CacheConfiguration[cacheCfgs.size()]));

            assert cfg.getCacheConfiguration() != null;

            IgfsUtils.prepareCacheConfigurations(cfg);
        }

        /**
         * Initialize default SPI implementations.
         *
         * @param cfg TurboSQL configuration.
         */
        private void initializeDefaultSpi(TurboSQLConfiguration cfg) {
            if (cfg.getDiscoverySpi() == null)
                cfg.setDiscoverySpi(new TcpDiscoverySpi());

            if (cfg.getDiscoverySpi() instanceof TcpDiscoverySpi) {
                TcpDiscoverySpi tcpDisco = (TcpDiscoverySpi)cfg.getDiscoverySpi();

                if (tcpDisco.getIpFinder() == null)
                    tcpDisco.setIpFinder(new TcpDiscoveryMulticastIpFinder());
            }

            if (cfg.getCommunicationSpi() == null)
                cfg.setCommunicationSpi(new TcpCommunicationSpi());

            if (cfg.getDeploymentSpi() == null)
                cfg.setDeploymentSpi(new LocalDeploymentSpi());

            if (cfg.getEventStorageSpi() == null)
                cfg.setEventStorageSpi(new NoopEventStorageSpi());

            if (cfg.getCheckpointSpi() == null)
                cfg.setCheckpointSpi(new NoopCheckpointSpi());

            if (cfg.getCollisionSpi() == null)
                cfg.setCollisionSpi(new NoopCollisionSpi());

            if (cfg.getFailoverSpi() == null)
                cfg.setFailoverSpi(new AlwaysFailoverSpi());

            if (cfg.getLoadBalancingSpi() == null)
                cfg.setLoadBalancingSpi(new RoundRobinLoadBalancingSpi());
            else {
                Collection<LoadBalancingSpi> spis = new ArrayList<>();

                boolean dfltLoadBalancingSpi = false;

                for (LoadBalancingSpi spi : cfg.getLoadBalancingSpi()) {
                    spis.add(spi);

                    if (!dfltLoadBalancingSpi && spi instanceof RoundRobinLoadBalancingSpi)
                        dfltLoadBalancingSpi = true;
                }

                // Add default load balancing SPI for internal tasks.
                if (!dfltLoadBalancingSpi)
                    spis.add(new RoundRobinLoadBalancingSpi());

                cfg.setLoadBalancingSpi(spis.toArray(new LoadBalancingSpi[spis.size()]));
            }

            if (cfg.getIndexingSpi() == null)
                cfg.setIndexingSpi(new NoopIndexingSpi());

            if (cfg.getEncryptionSpi() == null)
                cfg.setEncryptionSpi(new NoopEncryptionSpi());
        }

        /**
         * @param cfgLog Configured logger.
         * @param nodeId Local node ID.
         * @param workDir Work directory.
         * @return Initialized logger.
         * @throws TurboSQLCheckedException If failed.
         */
        @SuppressWarnings("ErrorNotRethrown")
        private TurboSQLLogger initLogger(@Nullable TurboSQLLogger cfgLog, UUID nodeId, String workDir)
            throws TurboSQLCheckedException {
            try {
                Exception log4jInitErr = null;

                if (cfgLog == null) {
                    Class<?> log4jCls;

                    try {
                        log4jCls = Class.forName("com.phonemetra.turbo.logger.log4j.Log4JLogger");
                    }
                    catch (ClassNotFoundException | NoClassDefFoundError ignored) {
                        log4jCls = null;
                    }

                    if (log4jCls != null) {
                        try {
                            URL url = U.resolveTurboSQLUrl("config/turboSQL-log4j.xml");

                            if (url == null) {
                                File cfgFile = new File("config/turboSQL-log4j.xml");

                                if (!cfgFile.exists())
                                    cfgFile = new File("../config/turboSQL-log4j.xml");

                                if (cfgFile.exists()) {
                                    try {
                                        url = cfgFile.toURI().toURL();
                                    }
                                    catch (MalformedURLException ignore) {
                                        // No-op.
                                    }
                                }
                            }

                            if (url != null) {
                                boolean configured = (Boolean)log4jCls.getMethod("isConfigured").invoke(null);

                                if (configured)
                                    url = null;
                            }

                            if (url != null) {
                                Constructor<?> ctor = log4jCls.getConstructor(URL.class);

                                cfgLog = (TurboSQLLogger)ctor.newInstance(url);
                            }
                            else
                                cfgLog = (TurboSQLLogger)log4jCls.newInstance();
                        }
                        catch (Exception e) {
                            log4jInitErr = e;
                        }
                    }

                    if (log4jCls == null || log4jInitErr != null)
                        cfgLog = new JavaLogger();
                }

                // Special handling for Java logger which requires work directory.
                if (cfgLog instanceof JavaLogger)
                    ((JavaLogger)cfgLog).setWorkDirectory(workDir);

                // Set node IDs for all file appenders.
                if (cfgLog instanceof LoggerNodeIdAware)
                    ((LoggerNodeIdAware)cfgLog).setNodeId(nodeId);

                if (log4jInitErr != null)
                    U.warn(cfgLog, "Failed to initialize Log4JLogger (falling back to standard java logging): "
                        + log4jInitErr.getCause());

                return cfgLog;
            }
            catch (Exception e) {
                throw new TurboSQLCheckedException("Failed to create logger.", e);
            }
        }

        /**
         * Creates utility system cache configuration.
         *
         * @return Utility system cache configuration.
         */
        private static CacheConfiguration utilitySystemCache() {
            CacheConfiguration cache = new CacheConfiguration();

            cache.setName(CU.UTILITY_CACHE_NAME);
            cache.setCacheMode(REPLICATED);
            cache.setAtomicityMode(TRANSACTIONAL);
            cache.setRebalanceMode(SYNC);
            cache.setWriteSynchronizationMode(FULL_SYNC);
            cache.setAffinity(new RendezvousAffinityFunction(false, 100));
            cache.setNodeFilter(CacheConfiguration.ALL_NODES);
            cache.setRebalanceOrder(-2); //Prior to user caches.
            cache.setCopyOnRead(false);

            return cache;
        }

        /**
         * Stops grid.
         *
         * @param cancel Flag indicating whether all currently running jobs
         *      should be cancelled.
         */
        void stop(boolean cancel) {
            // Stop cannot be called prior to start from public API,
            // since it checks for STARTED state. So, we can assert here.
            assert startGuard.get();

            stop0(cancel);
        }

        /**
         * @param cancel Flag indicating whether all currently running jobs
         *      should be cancelled.
         */
        private synchronized void stop0(boolean cancel) {
            TurboSQLKernal grid0 = grid;

            // Double check.
            if (grid0 == null) {
                if (log != null)
                    U.warn(log, "Attempting to stop an already stopped TurboSQL instance (ignore): " + name);

                return;
            }

            if (shutdownHook != null)
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook);

                    shutdownHook = null;

                    if (log != null && log.isDebugEnabled())
                        log.debug("Shutdown hook is removed.");
                }
                catch (IllegalStateException e) {
                    // Shutdown is in progress...
                    if (log != null && log.isDebugEnabled())
                        log.debug("Shutdown is in progress (ignoring): " + e.getMessage());
                }

            // Unregister TurboSQL MBean.
            unregisterFactoryMBean();

            try {
                grid0.stop(cancel);

                if (log != null && log.isDebugEnabled())
                    log.debug("TurboSQL instance stopped ok: " + name);
            }
            catch (Throwable e) {
                U.error(log, "Failed to properly stop grid instance due to undeclared exception.", e);

                if (e instanceof Error)
                    throw e;
            }
            finally {
                if (grid0.context().segmented())
                    state = STOPPED_ON_SEGMENTATION;
                else if (grid0.context().invalid())
                    state = STOPPED_ON_FAILURE;
                else
                    state = STOPPED;

                grid = null;

                if (log != null)
                    stopExecutors(log);

                log = null;
            }
        }

        /**
         * Stops executor services if they has been started.
         *
         * @param log Grid logger.
         */
        private void stopExecutors(TurboSQLLogger log) {
            boolean interrupted = Thread.interrupted();

            try {
                stopExecutors0(log);
            }
            finally {
                if (interrupted)
                    Thread.currentThread().interrupt();
            }
        }

        /**
         * Stops executor services if they has been started.
         *
         * @param log Grid logger.
         */
        private void stopExecutors0(TurboSQLLogger log) {
            assert log != null;

            U.shutdownNow(getClass(), execSvc, log);

            execSvc = null;

            U.shutdownNow(getClass(), svcExecSvc, log);

            svcExecSvc = null;

            U.shutdownNow(getClass(), sysExecSvc, log);

            sysExecSvc = null;

            U.shutdownNow(getClass(), qryExecSvc, log);

            qryExecSvc = null;

            U.shutdownNow(getClass(), schemaExecSvc, log);

            schemaExecSvc = null;

            U.shutdownNow(getClass(), stripedExecSvc, log);

            stripedExecSvc = null;

            U.shutdownNow(getClass(), mgmtExecSvc, log);

            mgmtExecSvc = null;

            U.shutdownNow(getClass(), p2pExecSvc, log);

            p2pExecSvc = null;

            U.shutdownNow(getClass(), dataStreamerExecSvc, log);

            dataStreamerExecSvc = null;

            U.shutdownNow(getClass(), igfsExecSvc, log);

            igfsExecSvc = null;

            if (restExecSvc != null)
                U.shutdownNow(getClass(), restExecSvc, log);

            restExecSvc = null;

            U.shutdownNow(getClass(), utilityCacheExecSvc, log);

            utilityCacheExecSvc = null;

            U.shutdownNow(getClass(), affExecSvc, log);

            affExecSvc = null;

            U.shutdownNow(getClass(), idxExecSvc, log);

            idxExecSvc = null;

            U.shutdownNow(getClass(), callbackExecSvc, log);

            callbackExecSvc = null;

            if (!F.isEmpty(customExecSvcs)) {
                for (ThreadPoolExecutor exec : customExecSvcs.values())
                    U.shutdownNow(getClass(), exec, log);

                customExecSvcs = null;
            }
        }

        /**
         * Registers delegate Mbean instance for {@link Ignition}.
         *
         * @param srv MBeanServer where mbean should be registered.
         * @throws TurboSQLCheckedException If registration failed.
         */
        private void registerFactoryMbean(MBeanServer srv) throws TurboSQLCheckedException {
            if(U.IGNITE_MBEANS_DISABLED)
                return;

            assert srv != null;

            synchronized (mbeans) {
                GridMBeanServerData data = mbeans.get(srv);

                if (data == null) {
                    try {
                        IgnitionMXBean mbean = new IgnitionMXBeanAdapter();

                        ObjectName objName = U.makeMBeanName(
                            null,
                            "Kernal",
                            Ignition.class.getSimpleName()
                        );

                        // Make check if MBean was already registered.
                        if (!srv.queryMBeans(objName, null).isEmpty())
                            throw new TurboSQLCheckedException("MBean was already registered: " + objName);
                        else {
                            objName = U.registerMBean(
                                srv,
                                null,
                                "Kernal",
                                Ignition.class.getSimpleName(),
                                mbean,
                                IgnitionMXBean.class
                            );

                            data = new GridMBeanServerData(objName);

                            mbeans.put(srv, data);

                            if (log.isDebugEnabled())
                                log.debug("Registered MBean: " + objName);
                        }
                    }
                    catch (JMException e) {
                        throw new TurboSQLCheckedException("Failed to register MBean.", e);
                    }
                }

                assert data != null;

                data.addTurboSQLInstance(name);
                data.setCounter(data.getCounter() + 1);
            }
        }

        /**
         * Unregister delegate Mbean instance for {@link Ignition}.
         */
        private void unregisterFactoryMBean() {
            if(U.IGNITE_MBEANS_DISABLED)
                return;

            synchronized (mbeans) {
                Iterator<Entry<MBeanServer, GridMBeanServerData>> iter = mbeans.entrySet().iterator();

                while (iter.hasNext()) {
                    Entry<MBeanServer, GridMBeanServerData> entry = iter.next();

                    if (entry.getValue().containsTurboSQLInstance(name)) {
                        GridMBeanServerData data = entry.getValue();

                        assert data != null;

                        // Unregister MBean if no grid instances started for current MBeanServer.
                        if (data.getCounter() == 1) {
                            try {
                                entry.getKey().unregisterMBean(data.getMbean());

                                if (log.isDebugEnabled())
                                    log.debug("Unregistered MBean: " + data.getMbean());
                            }
                            catch (JMException e) {
                                U.error(log, "Failed to unregister MBean.", e);
                            }

                            iter.remove();
                        }
                        else {
                            // Decrement counter.
                            data.setCounter(data.getCounter() - 1);
                            data.removeTurboSQLInstance(name);
                        }
                    }
                }
            }
        }

        /**
         * Grid factory MBean data container.
         * Contains necessary data for selected MBeanServer.
         */
        private static class GridMBeanServerData {
            /** Set of grid names for selected MBeanServer. */
            private Collection<String> turboSQLInstanceNames = new HashSet<>();

            /** */
            private ObjectName mbean;

            /** Count of grid instances. */
            private int cnt;

            /**
             * Create data container.
             *
             * @param mbean Object name of MBean.
             */
            GridMBeanServerData(ObjectName mbean) {
                assert mbean != null;

                this.mbean = mbean;
            }

            /**
             * Add TurboSQL instance name.
             *
             * @param turboSQLInstanceName TurboSQL instance name.
             */
            public void addTurboSQLInstance(String turboSQLInstanceName) {
                turboSQLInstanceNames.add(turboSQLInstanceName);
            }

            /**
             * Remove TurboSQL instance name.
             *
             * @param turboSQLInstanceName TurboSQL instance name.
             */
            public void removeTurboSQLInstance(String turboSQLInstanceName) {
                turboSQLInstanceNames.remove(turboSQLInstanceName);
            }

            /**
             * Returns {@code true} if data contains the specified
             * TurboSQL instance name.
             *
             * @param turboSQLInstanceName TurboSQL instance name.
             * @return {@code true} if data contains the specified TurboSQL instance name.
             */
            public boolean containsTurboSQLInstance(String turboSQLInstanceName) {
                return turboSQLInstanceNames.contains(turboSQLInstanceName);
            }

            /**
             * Gets name used in MBean server.
             *
             * @return Object name of MBean.
             */
            public ObjectName getMbean() {
                return mbean;
            }

            /**
             * Gets number of grid instances working with MBeanServer.
             *
             * @return Number of grid instances.
             */
            public int getCounter() {
                return cnt;
            }

            /**
             * Sets number of grid instances working with MBeanServer.
             *
             * @param cnt Number of grid instances.
             */
            public void setCounter(int cnt) {
                this.cnt = cnt;
            }
        }
    }

    /**
     * @param cfg TurboSQL Configuration with legacy data storage configuration.
     */
    private static void convertLegacyDataStorageConfigurationToNew(
        TurboSQLConfiguration cfg) throws TurboSQLCheckedException {
        PersistentStoreConfiguration psCfg = cfg.getPersistentStoreConfiguration();

        boolean persistenceEnabled = psCfg != null;

        DataStorageConfiguration dsCfg = new DataStorageConfiguration();

        MemoryConfiguration memCfg = cfg.getMemoryConfiguration() != null ?
            cfg.getMemoryConfiguration() : new MemoryConfiguration();

        dsCfg.setConcurrencyLevel(memCfg.getConcurrencyLevel());
        dsCfg.setPageSize(memCfg.getPageSize());
        dsCfg.setSystemRegionInitialSize(memCfg.getSystemCacheInitialSize());
        dsCfg.setSystemRegionMaxSize(memCfg.getSystemCacheMaxSize());

        List<DataRegionConfiguration> optionalDataRegions = new ArrayList<>();

        boolean customDfltPlc = false;

        if (memCfg.getMemoryPolicies() != null) {
            for (MemoryPolicyConfiguration mpc : memCfg.getMemoryPolicies()) {
                DataRegionConfiguration region = new DataRegionConfiguration();

                region.setPersistenceEnabled(persistenceEnabled);

                if (mpc.getInitialSize() != 0L)
                    region.setInitialSize(mpc.getInitialSize());

                region.setEmptyPagesPoolSize(mpc.getEmptyPagesPoolSize());
                region.setEvictionThreshold(mpc.getEvictionThreshold());
                region.setMaxSize(mpc.getMaxSize());
                region.setName(mpc.getName());
                region.setPageEvictionMode(mpc.getPageEvictionMode());
                region.setMetricsRateTimeInterval(mpc.getRateTimeInterval());
                region.setMetricsSubIntervalCount(mpc.getSubIntervals());
                region.setSwapPath(mpc.getSwapFilePath());
                region.setMetricsEnabled(mpc.isMetricsEnabled());

                if (persistenceEnabled)
                    region.setCheckpointPageBufferSize(psCfg.getCheckpointingPageBufferSize());

                if (mpc.getName() == null) {
                    throw new TurboSQLCheckedException(new IllegalArgumentException(
                        "User-defined MemoryPolicyConfiguration must have non-null and non-empty name."));
                }

                if (mpc.getName().equals(memCfg.getDefaultMemoryPolicyName())) {
                    customDfltPlc = true;

                    dsCfg.setDefaultDataRegionConfiguration(region);
                } else
                    optionalDataRegions.add(region);
            }
        }

        if (!optionalDataRegions.isEmpty())
            dsCfg.setDataRegionConfigurations(optionalDataRegions.toArray(
                new DataRegionConfiguration[optionalDataRegions.size()]));

        if (!customDfltPlc) {
            if (!DFLT_MEM_PLC_DEFAULT_NAME.equals(memCfg.getDefaultMemoryPolicyName())) {
                throw new TurboSQLCheckedException(new IllegalArgumentException("User-defined default MemoryPolicy " +
                    "name must be presented among configured MemoryPolices: " + memCfg.getDefaultMemoryPolicyName()));
            }

            dsCfg.setDefaultDataRegionConfiguration(new DataRegionConfiguration()
                .setMaxSize(memCfg.getDefaultMemoryPolicySize())
                .setName(memCfg.getDefaultMemoryPolicyName())
                .setPersistenceEnabled(persistenceEnabled));
        } else {
            if (memCfg.getDefaultMemoryPolicySize() != DFLT_MEMORY_POLICY_MAX_SIZE)
                throw new TurboSQLCheckedException(new IllegalArgumentException("User-defined MemoryPolicy " +
                    "configuration and defaultMemoryPolicySize properties are set at the same time."));
        }

        if (persistenceEnabled) {
            dsCfg.setCheckpointFrequency(psCfg.getCheckpointingFrequency());
            dsCfg.setCheckpointThreads(psCfg.getCheckpointingThreads());
            dsCfg.setCheckpointWriteOrder(psCfg.getCheckpointWriteOrder());
            dsCfg.setFileIOFactory(psCfg.getFileIOFactory());
            dsCfg.setLockWaitTime(psCfg.getLockWaitTime());
            dsCfg.setStoragePath(psCfg.getPersistentStorePath());
            dsCfg.setMetricsRateTimeInterval(psCfg.getRateTimeInterval());
            dsCfg.setMetricsSubIntervalCount(psCfg.getSubIntervals());
            dsCfg.setWalThreadLocalBufferSize(psCfg.getTlbSize());
            dsCfg.setWalArchivePath(psCfg.getWalArchivePath());
            dsCfg.setWalAutoArchiveAfterInactivity(psCfg.getWalAutoArchiveAfterInactivity());
            dsCfg.setWalFlushFrequency(psCfg.getWalFlushFrequency());
            dsCfg.setWalFsyncDelayNanos(psCfg.getWalFsyncDelayNanos());
            dsCfg.setWalHistorySize(psCfg.getWalHistorySize());
            dsCfg.setWalMode(psCfg.getWalMode());
            dsCfg.setWalRecordIteratorBufferSize(psCfg.getWalRecordIteratorBufferSize());
            dsCfg.setWalSegments(psCfg.getWalSegments());
            dsCfg.setWalSegmentSize(psCfg.getWalSegmentSize());
            dsCfg.setWalPath(psCfg.getWalStorePath());
            dsCfg.setAlwaysWriteFullPages(psCfg.isAlwaysWriteFullPages());
            dsCfg.setMetricsEnabled(psCfg.isMetricsEnabled());
            dsCfg.setWriteThrottlingEnabled(psCfg.isWriteThrottlingEnabled());
        }

        cfg.setDataStorageConfiguration(dsCfg);
    }
}
