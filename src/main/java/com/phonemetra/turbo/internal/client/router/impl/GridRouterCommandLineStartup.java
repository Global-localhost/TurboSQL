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

package com.phonemetra.turbo.internal.client.router.impl;

import java.net.URL;
import java.util.Collection;
import java.util.Map;
import java.util.logging.Handler;
import com.phonemetra.turbo.TurboSQLCheckedException;
import com.phonemetra.turbo.TurboSQLLogger;
import com.phonemetra.turbo.internal.client.router.GridTcpRouterConfiguration;
import com.phonemetra.turbo.internal.util.spring.TurboSQLSpringHelper;
import com.phonemetra.turbo.internal.util.typedef.X;
import com.phonemetra.turbo.internal.util.typedef.internal.U;
import com.phonemetra.turbo.lang.TurboSQLBiTuple;
import com.phonemetra.turbo.lifecycle.LifecycleAware;

import static com.phonemetra.turbo.internal.TurboSQLComponentType.SPRING;
import static com.phonemetra.turbo.internal.TurboSQLVersionUtils.ACK_VER_STR;
import static com.phonemetra.turbo.internal.TurboSQLVersionUtils.COPYRIGHT;

/**
 * Loader class for router.
 */
public class GridRouterCommandLineStartup {
    /** Logger. */
    private TurboSQLLogger log;

    /** TCP router. */
    private LifecycleAware tcpRouter;

    /**
     * Search given context for required configuration and starts router.
     *
     * @param beans Beans loaded from spring configuration file.
     */
    public void start(Map<Class<?>, Object> beans) {
        log = (TurboSQLLogger)beans.get(TurboSQLLogger.class);

        if (log == null) {
            U.error(log, "Failed to find logger definition in application context. Stopping the router.");

            return;
        }

        GridTcpRouterConfiguration tcpCfg = (GridTcpRouterConfiguration)beans.get(GridTcpRouterConfiguration.class);

        if (tcpCfg == null)
            U.warn(log, "TCP router startup skipped (configuration not found).");
        else {
            tcpRouter = new GridTcpRouterImpl(tcpCfg);

            try {
                tcpRouter.start();
            }
            catch (Exception e) {
                U.error(log, "Failed to start TCP router on port " + tcpCfg.getPort() + ": " + e.getMessage(), e);

                tcpRouter = null;
            }
        }
    }

    /**
     * Stops router.
     */
    public void stop() {
        if (tcpRouter != null) {
            try {
                tcpRouter.stop();
            }
            catch (Exception e) {
                U.error(log, "Error while stopping the router.", e);
            }
        }
    }

    /**
     * Wrapper method to run router from command-line.
     *
     * @param args Command-line arguments.
     * @throws TurboSQLCheckedException If failed.
     */
    public static void main(String[] args) throws TurboSQLCheckedException {
        X.println(
            "   __________  ________________ ",
            "  /  _/ ___/ |/ /  _/_  __/ __/ ",
            " _/ // (_ /    // /  / / / _/   ",
            "/___/\\___/_/|_/___/ /_/ /___/  ",
            " ",
            "TurboSQL Router Command Line Loader",
            "ver. " + ACK_VER_STR,
            COPYRIGHT,
            " "
        );

        TurboSQLSpringHelper spring = SPRING.create(false);

        if (args.length < 1) {
            X.error("Missing XML configuration path.");

            System.exit(1);
        }

        String cfgPath = args[0];

        URL cfgUrl = U.resolveTurboSQLUrl(cfgPath);

        if (cfgUrl == null) {
            X.error("Spring XML file not found (is TURBOSQL_HOME set?): " + cfgPath);

            System.exit(1);
        }

        boolean isLog4jUsed = U.gridClassLoader().getResource("org/apache/log4j/Appender.class") != null;

        TurboSQLBiTuple<Object, Object> t = null;
        Collection<Handler> savedHnds = null;

        if (isLog4jUsed) {
            try {
                t = U.addLog4jNoOpLogger();
            }
            catch (Exception ignored) {
                isLog4jUsed = false;
            }
        }

        if (!isLog4jUsed)
            savedHnds = U.addJavaNoOpLogger();

        Map<Class<?>, Object> beans;

        try {
            beans = spring.loadBeans(cfgUrl, TurboSQLLogger.class, GridTcpRouterConfiguration.class);
        }
        finally {
            if (isLog4jUsed && t != null)
                U.removeLog4jNoOpLogger(t);

            if (!isLog4jUsed)
                U.removeJavaNoOpLogger(savedHnds);
        }

        final GridRouterCommandLineStartup routerStartup = new GridRouterCommandLineStartup();

        routerStartup.start(beans);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override public void run() {
                routerStartup.stop();
            }
        });
    }
}
