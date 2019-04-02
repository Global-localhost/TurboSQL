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

package com.phonemetra.turbo.startup;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import com.phonemetra.turbo.TurboSQL;
import com.phonemetra.turbo.TurboSQLCheckedException;
import com.phonemetra.turbo.TurboSQLException;
import com.phonemetra.turbo.TurboSQLSystemProperties;
import com.phonemetra.turbo.Ignition;
import com.phonemetra.turbo.configuration.CacheConfiguration;
import com.phonemetra.turbo.configuration.TurboSQLConfiguration;
import com.phonemetra.turbo.internal.TurboSQLKernal;
import com.phonemetra.turbo.internal.processors.cache.TurboSQLCacheProxy;
import com.phonemetra.turbo.internal.processors.cache.TurboSQLInternalCache;
import com.phonemetra.turbo.internal.util.tostring.GridToStringInclude;
import com.phonemetra.turbo.internal.util.typedef.F;
import com.phonemetra.turbo.internal.util.typedef.internal.CU;
import com.phonemetra.turbo.internal.util.typedef.internal.S;
import com.phonemetra.turbo.internal.util.typedef.internal.U;
import com.phonemetra.turbo.lang.TurboSQLInClosure;
import com.phonemetra.turbo.logger.NullLogger;
import com.phonemetra.turbo.spi.discovery.tcp.TcpDiscoverySpi;
import com.phonemetra.turbo.spi.discovery.tcp.ipfinder.TcpDiscoveryIpFinder;
import com.phonemetra.turbo.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;

/**
 * Basic warm-up closure which warm-ups cache operations.
 */
public class BasicWarmupClosure implements TurboSQLInClosure<TurboSQLConfiguration> {
    /** */
    private static final long serialVersionUID = 9175346848249957458L;

    /** Default grid count to warm up. */
    public static final int DFLT_GRID_CNT = 2;

    /** Default iteration count per thread. */
    public static final int DFLT_ITERATION_CNT = 30_000;

    /** Default key range. */
    public static final int DFLT_KEY_RANGE = 10_000;

    /** Grid count. */
    private int gridCnt = DFLT_GRID_CNT;

    /** Warmup date format. */
    private static final SimpleDateFormat WARMUP_DATE_FMT = new SimpleDateFormat("HH:mm:ss");

    /** Warmup thread count. */
    private int threadCnt = Runtime.getRuntime().availableProcessors() * 2;

    /** Per thread iteration count. */
    private int iterCnt = DFLT_ITERATION_CNT;

    /** Key range. */
    private int keyRange = DFLT_KEY_RANGE;

    /** Warmup discovery port. */
    private int discoveryPort = 27000;

    /** Methods to warmup. */
    @GridToStringInclude
    private String[] warmupMethods = {"put", "putx", "get", "remove", "removex", "putIfAbsent", "replace"};

    /**
     * Gets number of grids to start and run warmup.
     *
     * @return Number of grids.
     */
    public int getGridCount() {
        return gridCnt;
    }

    /**
     * Sets number of grids to start and run the warmup.
     *
     * @param gridCnt Number of grids.
     */
    public void setGridCount(int gridCnt) {
        this.gridCnt = gridCnt;
    }

    /**
     * Gets warmup methods to use for cache warmup.
     *
     * @return Warmup methods.
     */
    public String[] getWarmupMethods() {
        return warmupMethods;
    }

    /**
     * Sets warmup methods to use for cache warmup.
     *
     * @param warmupMethods Array of warmup methods.
     */
    public void setWarmupMethods(String... warmupMethods) {
        this.warmupMethods = warmupMethods;
    }

    /**
     * Gets thread count for warmup.
     *
     * @return Thread count.
     */
    public int getThreadCount() {
        return threadCnt;
    }

    /**
     * Sets thread count for warmup.
     *
     * @param threadCnt Thread count.
     */
    public void setThreadCount(int threadCnt) {
        this.threadCnt = threadCnt;
    }

    /**
     * Gets iteration count for warmup.
     *
     * @return Iteration count.
     */
    public int getIterationCount() {
        return iterCnt;
    }

    /**
     * Sets iteration count for warmup.
     *
     * @param iterCnt Iteration count for warmup.
     */
    public void setIterationCount(int iterCnt) {
        this.iterCnt = iterCnt;
    }

    /**
     * Gets key range.
     *
     * @return Key range.
     */
    public int getKeyRange() {
        return keyRange;
    }

    /**
     * Sets key range.
     *
     * @param keyRange Key range.
     */
    public void setKeyRange(int keyRange) {
        this.keyRange = keyRange;
    }

    /**
     * Gets discovery port for warmup.
     *
     * @return Discovery port.
     */
    public int getDiscoveryPort() {
        return discoveryPort;
    }

    /**
     * Sets discovery port for warmup.
     *
     * @param discoveryPort Discovery port.
     */
    public void setDiscoveryPort(int discoveryPort) {
        this.discoveryPort = discoveryPort;
    }

    /** {@inheritDoc} */
    @Override public void apply(TurboSQLConfiguration gridCfg) {
        // Remove cache duplicates, clean up the rest, etc.
        TurboSQLConfiguration cfg = prepareConfiguration(gridCfg);

        // Do nothing if no caches found.
        if (cfg == null)
            return;

        out("Starting grids to warmup caches [gridCnt=" + gridCnt +
            ", caches=" + cfg.getCacheConfiguration().length + ']');

        Collection<TurboSQL> turboSQLs = new LinkedList<>();

        String old = System.getProperty(TurboSQLSystemProperties.IGNITE_UPDATE_NOTIFIER);

        try {
            System.setProperty(TurboSQLSystemProperties.IGNITE_UPDATE_NOTIFIER, "false");

            TcpDiscoveryIpFinder ipFinder = new TcpDiscoveryVmIpFinder(true);

            for (int i = 0; i < gridCnt; i++) {
                TurboSQLConfiguration cfg0 = new TurboSQLConfiguration(cfg);

                TcpDiscoverySpi discoSpi = new TcpDiscoverySpi();

                discoSpi.setIpFinder(ipFinder);

                discoSpi.setLocalPort(discoveryPort);

                cfg0.setDiscoverySpi(discoSpi);

                cfg0.setGridLogger(new NullLogger());

                cfg0.setTurboSQLInstanceName("turboSQL-warmup-grid-" + i);

                turboSQLs.add(Ignition.start(cfg0));
            }

            doWarmup(turboSQLs);
        }
        catch (Exception e) {
            throw new TurboSQLException(e);
        }
        finally {
            for (TurboSQL turboSQL : turboSQLs)
                Ignition.stop(turboSQL.name(), false);

            out("Stopped warmup grids.");

            if (old == null)
                old = "false";

            System.setProperty(TurboSQLSystemProperties.IGNITE_UPDATE_NOTIFIER, old);
        }
    }

    /**
     * @param grids Grids to warmup.
     */
    private void doWarmup(Iterable<TurboSQL> grids) throws Exception {
        TurboSQL first = F.first(grids);

        ExecutorService svc = Executors.newFixedThreadPool(threadCnt);

        try {
            for (TurboSQLCacheProxy cache : ((TurboSQLKernal)first).caches()) {
                if (!cache.context().userCache())
                    continue;

                TurboSQLInternalCache<Object, Object> cache0 = cache.context().cache();

                for (String warmupMethod : warmupMethods) {
                    Collection<Future> futs = new ArrayList<>(threadCnt);

                    for (int i = 0; i < threadCnt; i++) {
                        Callable call;

                        switch (warmupMethod) {
                            case "get": {
                                call = new GetCallable(cache0);

                                break;
                            }

                            case "put": {
                                call = new PutCallable(cache0);

                                break;
                            }

                            case "putx": {
                                call = new PutxCallable(cache0);

                                break;
                            }

                            case "remove": {
                                call = new RemoveCallable(cache0);

                                break;
                            }

                            case "removex": {
                                call = new RemovexCallable(cache0);

                                break;
                            }

                            case "putIfAbsent": {
                                call = new PutIfAbsentCallable(cache0);

                                break;
                            }

                            case "replace": {
                                call = new ReplaceCallable(cache0);

                                break;
                            }

                            default:
                                throw new TurboSQLCheckedException("Unsupported warmup method: " + warmupMethod);
                        }

                        futs.add(svc.submit(call));
                    }

                    out("Running warmup [cacheName=" + U.maskName(cache.getName()) + ", method=" + warmupMethod + ']');

                    for (Future fut : futs)
                        fut.get();

                    for (int key = 0; key < keyRange; key++)
                        cache0.getAndRemove(key);
                }
            }
        }
        finally {
            svc.shutdownNow();
        }
    }

    /**
     * Output for warmup messages.
     *
     * @param msg Format message.
     */
    private static void out(String msg) {
        System.out.println('[' + WARMUP_DATE_FMT.format(new Date(System.currentTimeMillis())) + "][WARMUP][" +
            Thread.currentThread().getName() + ']' + ' ' + msg);
    }

    /**
     * Prepares configuration for warmup.
     *
     * @param gridCfg Original grid configuration.
     * @return Prepared configuration or {@code null} if no caches found.
     */
    private TurboSQLConfiguration prepareConfiguration(TurboSQLConfiguration gridCfg) {
        if (F.isEmpty(gridCfg.getCacheConfiguration()))
            return null;

        TurboSQLConfiguration cp = new TurboSQLConfiguration();

        cp.setConnectorConfiguration(null);

        Collection<CacheConfiguration> reduced = new ArrayList<>();

        for (CacheConfiguration ccfg : gridCfg.getCacheConfiguration()) {
            if (CU.isSystemCache(ccfg.getName()))
                continue;

            if (!matches(reduced, ccfg)) {
                CacheConfiguration ccfgCp = new CacheConfiguration(ccfg);

                ccfgCp.setCacheStoreFactory(null);
                ccfgCp.setWriteBehindEnabled(false);

                reduced.add(ccfgCp);
            }
        }

        if (F.isEmpty(reduced))
            return null;

        CacheConfiguration[] res = new CacheConfiguration[reduced.size()];

        reduced.toArray(res);

        cp.setCacheConfiguration(res);

        return cp;
    }

    /**
     * Checks if passed configuration matches one of the configurations in the list.
     *
     * @param reduced Reduced configurations.
     * @param ccfg Cache configuration to match.
     * @return {@code True} if matching configuration is found, {@code false} otherwise.
     */
    private boolean matches(Iterable<CacheConfiguration> reduced, CacheConfiguration ccfg) {
        for (CacheConfiguration ccfg0 : reduced) {
            if (matches(ccfg0, ccfg))
                return true;
        }

        return false;
    }

    /**
     * Checks if cache configurations are alike for warmup.
     *
     * @param ccfg0 First configuration.
     * @param ccfg1 Second configuration.
     * @return {@code True} if configurations match.
     */
    private boolean matches(CacheConfiguration ccfg0, CacheConfiguration ccfg1) {
        return
            F.eq(ccfg0.getCacheMode(), ccfg1.getCacheMode()) &&
            F.eq(ccfg0.getBackups(), ccfg1.getBackups()) &&
            F.eq(ccfg0.getAtomicityMode(), ccfg1.getAtomicityMode());
    }

    /**
     * Base class for all warmup callables.
     */
    private abstract class BaseWarmupCallable implements Callable<Object> {
        /** Cache. */
        protected final TurboSQLInternalCache<Object, Object> cache;

        /**
         * @param cache Cache.
         */
        protected BaseWarmupCallable(TurboSQLInternalCache<Object, Object> cache) {
            this.cache = cache;
        }

        /** {@inheritDoc} */
        @Override public Object call() throws Exception {
            ThreadLocalRandom rnd = ThreadLocalRandom.current();

            for (int i = 0; i < iterCnt; i++)
                operation(rnd.nextInt(keyRange));

            return null;
        }

        /**
         * Runs operation.
         *
         * @param key Key.
         * @throws Exception If failed.
         */
        protected abstract void operation(int key) throws Exception;
    }

    /**
     *
     */
    private class GetCallable extends BaseWarmupCallable {
        /**
         * @param cache Cache.
         */
        private GetCallable(TurboSQLInternalCache<Object, Object> cache) {
            super(cache);
        }

        /** {@inheritDoc} */
        @Override protected void operation(int key) throws Exception {
            cache.get(key);
        }
    }

    /**
     *
     */
    private class PutCallable extends BaseWarmupCallable {
        /**
         * @param cache Cache.
         */
        private PutCallable(TurboSQLInternalCache<Object, Object> cache) {
            super(cache);
        }

        /** {@inheritDoc} */
        @Override protected void operation(int key) throws Exception {
            cache.getAndPut(key, key);
        }
    }

    /**
     *
     */
    private class PutxCallable extends BaseWarmupCallable {
        /**
         * @param cache Cache.
         */
        private PutxCallable(TurboSQLInternalCache<Object, Object> cache) {
            super(cache);
        }

        /** {@inheritDoc} */
        @Override protected void operation(int key) throws Exception {
            cache.put(key, key);
        }
    }

    /**
     *
     */
    private class RemoveCallable extends BaseWarmupCallable {
        /**
         * @param cache Cache.
         */
        private RemoveCallable(TurboSQLInternalCache<Object, Object> cache) {
            super(cache);
        }

        /** {@inheritDoc} */
        @Override protected void operation(int key) throws Exception {
            cache.getAndRemove(key);
        }
    }

    /**
     *
     */
    private class RemovexCallable extends BaseWarmupCallable {
        /**
         * @param cache Cache.
         */
        private RemovexCallable(TurboSQLInternalCache<Object, Object> cache) {
            super(cache);
        }

        /** {@inheritDoc} */
        @Override protected void operation(int key) throws Exception {
            cache.remove(key);
        }
    }

    /**
     *
     */
    private class PutIfAbsentCallable extends BaseWarmupCallable {
        /**
         * @param cache Cache.
         */
        private PutIfAbsentCallable(TurboSQLInternalCache<Object, Object> cache) {
            super(cache);
        }

        /** {@inheritDoc} */
        @Override protected void operation(int key) throws Exception {
            cache.getAndPutIfAbsent(key, key);
        }
    }

    /**
     *
     */
    private class ReplaceCallable extends BaseWarmupCallable {
        /**
         * @param cache Cache.
         */
        private ReplaceCallable(TurboSQLInternalCache<Object, Object> cache) {
            super(cache);
        }

        /** {@inheritDoc} */
        @Override protected void operation(int key) throws Exception {
            cache.replace(key, key, key);
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(BasicWarmupClosure.class, this);
    }
}
