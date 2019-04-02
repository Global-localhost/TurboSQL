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

import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import com.phonemetra.turbo.client.ClientException;
import com.phonemetra.turbo.configuration.ClientConfiguration;
import com.phonemetra.turbo.configuration.TurboSQLConfiguration;
import com.phonemetra.turbo.internal.IgnitionEx;
import com.phonemetra.turbo.internal.client.thin.TcpTurboSQLClient;
import com.phonemetra.turbo.internal.util.typedef.internal.U;
import com.phonemetra.turbo.client.TurboSQLClient;
import com.phonemetra.turbo.thread.TurboSQLThread;
import org.jetbrains.annotations.Nullable;

/**
 * This class defines a factory for the main TurboSQL API. It controls Grid life cycle
 * and allows listening for grid events.
 * <h1 class="header">Grid Loaders</h1>
 * Although user can apply grid factory directly to start and stop grid, grid is
 * often started and stopped by grid loaders. Grid loaders can be found in
 * {@link com.phonemetra.turbo.startup} package, for example:
 * <ul>
 * <li>{@link com.phonemetra.turbo.startup.cmdline.CommandLineStartup}</li>
 * <li>{@turboSQLlink com.phonemetra.turbo.startup.servlet.ServletStartup}</li>
 * </ul>
 * <h1 class="header">Examples</h1>
 * Use {@link #start()} method to start grid with default configuration. You can also use
 * {@link com.phonemetra.turbo.configuration.TurboSQLConfiguration} to override some default configuration. Below is an
 * example on how to start grid with custom configuration for <strong>URI deployment</strong>.
 * <pre name="code" class="java">
 * TurboSQLConfiguration cfg = new TurboSQLConfiguration();
 *
 * GridUriDeployment deploySpi = new GridUriDeployment();
 *
 * deploySpi.setUriList(Collections.singletonList("classes://tmp/output/classes"));
 *
 * cfg.setDeploymentSpi(deploySpi);
 *
 * Ignition.start(cfg);
 * </pre>
 * Here is how a grid instance can be configured from Spring XML configuration file. The
 * example below configures a grid instance with additional user attributes
 * (see {@link com.phonemetra.turbo.cluster.ClusterNode#attributes()}) and specifies a TurboSQL instance name:
 * <pre name="code" class="xml">
 * &lt;bean id="grid.cfg" class="com.phonemetra.turbo.configuration.TurboSQLConfiguration"&gt;
 *     ...
 *     &lt;property name="turboSQLInstanceName" value="grid"/&gt;
 *     &lt;property name="userAttributes"&gt;
 *         &lt;map&gt;
 *             &lt;entry key="group" value="worker"/&gt;
 *         &lt;/map&gt;
 *     &lt;/property&gt;
 *     ...
 * &lt;/bean&gt;
 * </pre>
 * A grid instance with Spring configuration above can be started as following. Note that
 * you do not need to pass path to Spring XML file if you are using
 * {@code TURBOSQL_HOME/config/default-config.xml}. Also note, that the path can be
 * absolute or relative to TURBOSQL_HOME.
 * <pre name="code" class="java">
 * ...
 * Ignition.start("/path/to/spring/xml/file.xml");
 * ...
 * </pre>
 * You can also instantiate grid directly from Spring without using {@code Ignition}.
 * For more information refer to {@turboSQLlink com.phonemetra.turbo.TurboSQLSpringBean} documentation.
 */
public class Ignition {
    /**
     * This is restart code that can be used by external tools, like Shell scripts,
     * to auto-restart the TurboSQL JVM process. Note that there is no standard way
     * for a JVM to restart itself from Java application and therefore we rely on
     * external tools to provide that capability.
     * <p>
     * Note that standard <tt>turboSQL.{sh|bat}</tt> scripts support restarting when
     * JVM process exits with this code.
     */
    public static final int RESTART_EXIT_CODE = 250;

    /**
     * This is kill code that can be used by external tools, like Shell scripts,
     * to auto-stop the TurboSQL JVM process without restarting.
     */
    public static final int KILL_EXIT_CODE = 130;

    /**
     * Enforces singleton.
     */
    protected Ignition() {
        // No-op.
    }

    /**
     * Sets daemon flag.
     * <p>
     * If daemon flag is set then all grid instances created by the factory will be
     * daemon, i.e. the local node for these instances will be a daemon node. Note that
     * if daemon flag is set - it will override the same settings in {@link com.phonemetra.turbo.configuration.TurboSQLConfiguration#isDaemon()}.
     * Note that you can set on and off daemon flag at will.
     *
     * @param daemon Daemon flag to set.
     */
    public static void setDaemon(boolean daemon) {
        IgnitionEx.setDaemon(daemon);
    }

    /**
     * Gets daemon flag.
     * <p>
     * If daemon flag it set then all grid instances created by the factory will be
     * daemon, i.e. the local node for these instances will be a daemon node. Note that
     * if daemon flag is set - it will override the same settings in {@link com.phonemetra.turbo.configuration.TurboSQLConfiguration#isDaemon()}.
     * Note that you can set on and off daemon flag at will.
     *
     * @return Daemon flag.
     */
    public static boolean isDaemon() {
        return IgnitionEx.isDaemon();
    }

    /**
     * Sets client mode thread-local flag.
     * <p>
     * This flag used when node is started if {@link TurboSQLConfiguration#isClientMode()}
     * is {@code null}. When {@link TurboSQLConfiguration#isClientMode()} is set this flag is ignored.
     *
     * @param clientMode Client mode flag.
     * @see TurboSQLConfiguration#isClientMode()
     */
    public static void setClientMode(boolean clientMode) {
        IgnitionEx.setClientMode(clientMode);
    }

    /**
     * Gets client mode thread-local flag.
     * <p>
     * This flag used when node is started if {@link TurboSQLConfiguration#isClientMode()}
     * is {@code null}. When {@link TurboSQLConfiguration#isClientMode()} is set this flag is ignored.
     *
     * @return Client mode flag.
     * @see TurboSQLConfiguration#isClientMode()
     */
    public static boolean isClientMode() {
        return IgnitionEx.isClientMode();
    }

    /**
     * Gets state of grid default grid.
     *
     * @return Default grid state.
     */
    public static TurboSQLState state() {
        return IgnitionEx.state();
    }

    /**
     * Gets states of named TurboSQL instance. If name is {@code null}, then state of
     * default no-name TurboSQL instance is returned.
     *
     * @param name TurboSQL instance name. If name is {@code null}, then state of
     *      default no-name TurboSQL instance is returned.
     * @return TurboSQL instance state.
     */
    public static TurboSQLState state(@Nullable String name) {
        return IgnitionEx.state(name);
    }

    /**
     * Stops default grid. This method is identical to {@code G.stop(null, cancel)} apply.
     * Note that method does not wait for all tasks to be completed.
     *
     * @param cancel If {@code true} then all jobs currently executing on
     *      default grid will be cancelled by calling {@link com.phonemetra.turbo.compute.ComputeJob#cancel()}
     *      method. Note that just like with {@link Thread#interrupt()}, it is
     *      up to the actual job to exit from execution
     * @return {@code true} if default grid instance was indeed stopped,
     *      {@code false} otherwise (if it was not started).
     */
    public static boolean stop(boolean cancel) {
        return IgnitionEx.stop(cancel);
    }

    /**
     * Stops named TurboSQL instance. If {@code cancel} flag is set to {@code true} then
     * all jobs currently executing on local node will be interrupted. If
     * TurboSQL instance name is {@code null}, then default no-name TurboSQL instance will be stopped.
     * If wait parameter is set to {@code true} then TurboSQL instance will wait for all
     * tasks to be finished.
     *
     * @param name TurboSQL instance name. If {@code null}, then default no-name TurboSQL instance will
     *      be stopped.
     * @param cancel If {@code true} then all jobs currently will be cancelled
     *      by calling {@link com.phonemetra.turbo.compute.ComputeJob#cancel()} method. Note that just like with
     *      {@link Thread#interrupt()}, it is up to the actual job to exit from
     *      execution. If {@code false}, then jobs currently running will not be
     *      canceled. In either case, grid node will wait for completion of all
     *      jobs running on it before stopping.
     * @return {@code true} if named TurboSQL instance was indeed found and stopped,
     *      {@code false} otherwise (the instance with given {@code name} was
     *      not found).
     */
    public static boolean stop(@Nullable String name, boolean cancel) {
        return IgnitionEx.stop(name, cancel, false);
    }

    /**
     * Stops <b>all</b> started grids in current JVM. If {@code cancel} flag is set to {@code true} then
     * all jobs currently executing on local node will be interrupted.
     * If wait parameter is set to {@code true} then grid will wait for all
     * tasks to be finished.
     * <p>
     * <b>Note:</b> it is usually safer and more appropriate to stop grid instances individually
     * instead of blanket operation. In most cases, the party that started the grid instance
     * should be responsible for stopping it.
     *
     * @param cancel If {@code true} then all jobs currently executing on
     *      all grids will be cancelled by calling {@link com.phonemetra.turbo.compute.ComputeJob#cancel()}
     *      method. Note that just like with {@link Thread#interrupt()}, it is
     *      up to the actual job to exit from execution
     */
    public static void stopAll(boolean cancel) {
        IgnitionEx.stopAll(cancel);
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
     *      all grids will be cancelled by calling {@link com.phonemetra.turbo.compute.ComputeJob#cancel()}
     *      method. Note that just like with {@link Thread#interrupt()}, it is
     *      up to the actual job to exit from execution.
     * @see #RESTART_EXIT_CODE
     */
    public static void restart(boolean cancel) {
        IgnitionEx.restart(cancel);
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
     * exit code {@link #KILL_EXIT_CODE}.
     *
     * @param cancel If {@code true} then all jobs currently executing on
     *      all grids will be cancelled by calling {@link com.phonemetra.turbo.compute.ComputeJob#cancel()}
     *      method. Note that just like with {@link Thread#interrupt()}, it is
     *      up to the actual job to exit from execution.
     * @see #KILL_EXIT_CODE
     */
    public static void kill(boolean cancel) {
        IgnitionEx.kill(cancel);
    }

    /**
     * Starts grid with default configuration. By default this method will
     * use grid configuration defined in {@code TURBOSQL_HOME/config/default-config.xml}
     * configuration file. If such file is not found, then all system defaults will be used.
     *
     * @return Started grid.
     * @throws TurboSQLException If default grid could not be started. This exception will be thrown
     *      also if default grid has already been started.
     */
    public static TurboSQL start() throws TurboSQLException {
        try {
            return IgnitionEx.start();
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
    }

    /**
     * Starts grid with given configuration. Note that this method will throw an exception if grid with the name
     * provided in given configuration is already started.
     *
     * @param cfg Grid configuration. This cannot be {@code null}.
     * @return Started grid.
     * @throws TurboSQLException If grid could not be started. This exception will be thrown
     *      also if named grid has already been started.
     */
    public static TurboSQL start(TurboSQLConfiguration cfg) throws TurboSQLException {
        try {
            return IgnitionEx.start(cfg);
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
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
     * @throws TurboSQLException If grid could not be started or configuration
     *      read. This exception will be thrown also if grid with given name has already
     *      been started or Spring XML configuration file is invalid.
     */
    public static TurboSQL start(String springCfgPath) throws TurboSQLException {
        try {
            return IgnitionEx.start(springCfgPath);
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
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
     * @throws TurboSQLException If grid could not be started or configuration
     *      read. This exception will be thrown also if grid with given name has already
     *      been started or Spring XML configuration file is invalid.
     */
    public static TurboSQL start(URL springCfgUrl) throws TurboSQLException {
        try {
            return IgnitionEx.start(springCfgUrl);
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
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
     * @throws TurboSQLException If grid could not be started or configuration
     *      read. This exception will be thrown also if grid with given name has already
     *      been started or Spring XML configuration file is invalid.
     */
    public static TurboSQL start(InputStream springCfgStream) throws TurboSQLException {
        try {
            return IgnitionEx.start(springCfgStream);
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
    }


    /**
     * Gets or starts new grid instance if it hasn't been started yet.
     *
     * @param cfg Grid configuration. This cannot be {@code null}.
     * @return Grid instance.
     * @throws TurboSQLException If grid could not be started.
     */
    public static TurboSQL getOrStart(TurboSQLConfiguration cfg) throws TurboSQLException {
        try {
            return IgnitionEx.start(cfg, false);
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
    }

    /**
     * Loads Spring bean by its name from given Spring XML configuration file. If bean
     * with such name doesn't exist, exception is thrown.
     *
     * @param springXmlPath Spring XML configuration file path (cannot be {@code null}).
     * @param beanName Bean name (cannot be {@code null}).
     * @return Loaded bean instance.
     * @throws TurboSQLException If bean with provided name was not found or in case any other error.
     */
    public static <T> T loadSpringBean(String springXmlPath, String beanName) throws TurboSQLException {
        try {
            return IgnitionEx.loadSpringBean(springXmlPath, beanName);
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
    }

    /**
     * Loads Spring bean by its name from given Spring XML configuration file. If bean
     * with such name doesn't exist, exception is thrown.
     *
     * @param springXmlUrl Spring XML configuration file URL (cannot be {@code null}).
     * @param beanName Bean name (cannot be {@code null}).
     * @return Loaded bean instance.
     * @throws TurboSQLException If bean with provided name was not found or in case any other error.
     */
    public static <T> T loadSpringBean(URL springXmlUrl, String beanName) throws TurboSQLException {
        try {
            return IgnitionEx.loadSpringBean(springXmlUrl, beanName);
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
    }

    /**
     * Loads Spring bean by its name from given Spring XML configuration file. If bean
     * with such name doesn't exist, exception is thrown.
     *
     * @param springXmlStream Input stream containing Spring XML configuration (cannot be {@code null}).
     * @param beanName Bean name (cannot be {@code null}).
     * @return Loaded bean instance.
     * @throws TurboSQLException If bean with provided name was not found or in case any other error.
     */
    public static <T> T loadSpringBean(InputStream springXmlStream, String beanName) throws TurboSQLException {
        try {
            return IgnitionEx.loadSpringBean(springXmlStream, beanName);
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
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
    public static TurboSQL turboSQL() throws TurboSQLIllegalStateException {
        return IgnitionEx.grid();
    }

    /**
     * Gets a list of all grids started so far.
     *
     * @return List of all grids started so far.
     */
    public static List<TurboSQL> allGrids() {
        return IgnitionEx.allGrids();
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
    public static TurboSQL turboSQL(UUID locNodeId) throws TurboSQLIllegalStateException {
        return IgnitionEx.grid(locNodeId);
    }

    /**
     * Gets a named TurboSQL instance. If TurboSQL instance name is {@code null} or empty string,
     * then default no-name TurboSQL instance will be returned. Note that caller of this method
     * should not assume that it will return the same instance every time.
     * <p>
     * The name allows having multiple TurboSQL instances with different names within the same Java VM.
     *
     * @param name TurboSQL instance name. If {@code null}, then a default no-name
     *      TurboSQL instance will be returned.
     * @return A named TurboSQL instance. This method never returns {@code null}.
     * @throws TurboSQLIllegalStateException Thrown if default TurboSQL instance was not properly
     *      initialized or TurboSQL instance was stopped or was not started.
     */
    public static TurboSQL turboSQL(@Nullable String name) throws TurboSQLIllegalStateException {
        return IgnitionEx.grid(name);
    }

    /**
     * This method is used to address a local {@link TurboSQL} instance, principally from closure.
     * <p>
     * According to contract this method has to be called only under {@link TurboSQLThread}.
     * An {@link IllegalArgumentException} will be thrown otherwise.
     *
     * @return A current {@link TurboSQL} instance to address from closure.
     * @throws TurboSQLIllegalStateException Thrown if grid was not properly
     *      initialized or grid instance was stopped or was not started
     * @throws IllegalArgumentException Thrown if current thread is not an {@link TurboSQLThread}.
     */
    public static TurboSQL localTurboSQL() throws TurboSQLIllegalStateException, IllegalArgumentException {
        return IgnitionEx.localTurboSQL();
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
        IgnitionEx.addListener(lsnr);
    }

    /**
     * Removes lsnr added by {@link #addListener(IgnitionListener)} method.
     *
     * @param lsnr Listener to remove.
     * @return {@code true} if lsnr was added before, {@code false} otherwise.
     */
    public static boolean removeListener(IgnitionListener lsnr) {
        return IgnitionEx.removeListener(lsnr);
    }

    /**
     * Initializes new instance of {@link TurboSQLClient}.
     * <p>
     * Server connection will be lazily initialized when first required.
     *
     * @param cfg Thin client configuration.
     * @return Successfully opened thin client connection.
     */
    public static TurboSQLClient startClient(ClientConfiguration cfg) throws ClientException {
        Objects.requireNonNull(cfg, "cfg");

        return TcpTurboSQLClient.start(cfg);
    }
}