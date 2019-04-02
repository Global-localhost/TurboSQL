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

package com.phonemetra.turbo.internal.managers;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import javax.management.JMException;
import javax.management.ObjectName;
import com.phonemetra.turbo.TurboSQLCheckedException;
import com.phonemetra.turbo.TurboSQLLogger;
import com.phonemetra.turbo.internal.ClusterLocalNodeMetricsMXBeanImpl;
import com.phonemetra.turbo.internal.ClusterMetricsMXBeanImpl;
import com.phonemetra.turbo.internal.GridKernalContextImpl;
import com.phonemetra.turbo.internal.TurboSQLKernal;
import com.phonemetra.turbo.internal.StripedExecutorMXBeanAdapter;
import com.phonemetra.turbo.internal.ThreadPoolMXBeanAdapter;
import com.phonemetra.turbo.internal.TransactionMetricsMxBeanImpl;
import com.phonemetra.turbo.internal.TransactionsMXBeanImpl;
import com.phonemetra.turbo.internal.processors.cache.persistence.DataStorageMXBeanImpl;
import com.phonemetra.turbo.internal.processors.cluster.BaselineConfigurationMXBeanImpl;
import com.phonemetra.turbo.internal.stat.IoStatisticsMetricsLocalMXBeanImpl;
import com.phonemetra.turbo.internal.util.StripedExecutor;
import com.phonemetra.turbo.internal.util.typedef.internal.U;
import com.phonemetra.turbo.internal.worker.FailureHandlingMxBeanImpl;
import com.phonemetra.turbo.internal.worker.WorkersControlMXBeanImpl;
import com.phonemetra.turbo.internal.worker.WorkersRegistry;
import com.phonemetra.turbo.mxbean.BaselineConfigurationMXBean;
import com.phonemetra.turbo.mxbean.ClusterMetricsMXBean;
import com.phonemetra.turbo.mxbean.DataStorageMXBean;
import com.phonemetra.turbo.mxbean.FailureHandlingMxBean;
import com.phonemetra.turbo.mxbean.TurboSQLMXBean;
import com.phonemetra.turbo.mxbean.IoStatisticsMetricsMXBean;
import com.phonemetra.turbo.mxbean.StripedExecutorMXBean;
import com.phonemetra.turbo.mxbean.ThreadPoolMXBean;
import com.phonemetra.turbo.mxbean.TransactionMetricsMxBean;
import com.phonemetra.turbo.mxbean.TransactionsMXBean;
import com.phonemetra.turbo.mxbean.WorkersControlMXBean;
import com.phonemetra.turbo.thread.TurboSQLStripedThreadPoolExecutor;
import org.jetbrains.annotations.Nullable;

/**
 * Class that registers and unregisters MBeans for kernal.
 */
public class TurboSQLMBeansManager {
    /** TurboSQL kernal */
    private final TurboSQLKernal kernal;

    /** TurboSQL kernal context. */
    private final GridKernalContextImpl ctx;

    /** Logger. */
    private final TurboSQLLogger log;

    /** MBean names stored to be unregistered later. */
    private final Set<ObjectName> mBeanNames = new HashSet<>();

    /**
     * @param kernal Grid kernal.
     */
    public TurboSQLMBeansManager(TurboSQLKernal kernal) {
        this.kernal = kernal;
        ctx = (GridKernalContextImpl)kernal.context();
        log = ctx.log(TurboSQLMBeansManager.class);
    }

    /**
     * Registers all kernal MBeans (for kernal, metrics, thread pools).
     *
     * @param utilityCachePool Utility cache pool.
     * @param execSvc Executor service.
     * @param svcExecSvc Services' executor service.
     * @param sysExecSvc System executor service.
     * @param stripedExecSvc Striped executor.
     * @param p2pExecSvc P2P executor service.
     * @param mgmtExecSvc Management executor service.
     * @param igfsExecSvc IGFS executor service.
     * @param dataStreamExecSvc data stream executor service.
     * @param restExecSvc Reset executor service.
     * @param affExecSvc Affinity executor service.
     * @param idxExecSvc Indexing executor service.
     * @param callbackExecSvc Callback executor service.
     * @param qryExecSvc Query executor service.
     * @param schemaExecSvc Schema executor service.
     * @param customExecSvcs Custom named executors.
     * @param workersRegistry Worker registry.
     * @throws TurboSQLCheckedException if fails to register any of the MBeans.
     */
    public void registerAllMBeans(
        ExecutorService utilityCachePool,
        final ExecutorService execSvc,
        final ExecutorService svcExecSvc,
        final ExecutorService sysExecSvc,
        final StripedExecutor stripedExecSvc,
        ExecutorService p2pExecSvc,
        ExecutorService mgmtExecSvc,
        ExecutorService igfsExecSvc,
        StripedExecutor dataStreamExecSvc,
        ExecutorService restExecSvc,
        ExecutorService affExecSvc,
        @Nullable ExecutorService idxExecSvc,
        TurboSQLStripedThreadPoolExecutor callbackExecSvc,
        ExecutorService qryExecSvc,
        ExecutorService schemaExecSvc,
        @Nullable final Map<String, ? extends ExecutorService> customExecSvcs,
        WorkersRegistry workersRegistry
    ) throws TurboSQLCheckedException {
        if (U.IGNITE_MBEANS_DISABLED)
            return;

        // Kernal
        registerMBean("Kernal", TurboSQLKernal.class.getSimpleName(), kernal, TurboSQLMXBean.class);

        // Metrics
        ClusterMetricsMXBean locMetricsBean = new ClusterLocalNodeMetricsMXBeanImpl(ctx.discovery());
        registerMBean("Kernal", locMetricsBean.getClass().getSimpleName(), locMetricsBean, ClusterMetricsMXBean.class);
        ClusterMetricsMXBean metricsBean = new ClusterMetricsMXBeanImpl(kernal.cluster());
        registerMBean("Kernal", metricsBean.getClass().getSimpleName(), metricsBean, ClusterMetricsMXBean.class);

        //IO metrics
        IoStatisticsMetricsMXBean ioStatMetricsBean = new IoStatisticsMetricsLocalMXBeanImpl(ctx.ioStats());
        registerMBean("IOMetrics", ioStatMetricsBean.getClass().getSimpleName(), ioStatMetricsBean, IoStatisticsMetricsMXBean.class);

        // Transaction metrics
        TransactionMetricsMxBean txMetricsMXBean = new TransactionMetricsMxBeanImpl(ctx.cache().transactions().metrics());
        registerMBean("TransactionMetrics", txMetricsMXBean.getClass().getSimpleName(), txMetricsMXBean, TransactionMetricsMxBean.class);

        // Transactions
        TransactionsMXBean txMXBean = new TransactionsMXBeanImpl(ctx);
        registerMBean("Transactions", txMXBean.getClass().getSimpleName(), txMXBean, TransactionsMXBean.class);

        // Data storage
        DataStorageMXBean dataStorageMXBean = new DataStorageMXBeanImpl(ctx);
        registerMBean("DataStorage", dataStorageMXBean.getClass().getSimpleName(), dataStorageMXBean, DataStorageMXBean.class);

        // Baseline configuration
        BaselineConfigurationMXBean baselineConfigurationMXBean = new BaselineConfigurationMXBeanImpl(ctx);
        registerMBean("Baseline", baselineConfigurationMXBean.getClass().getSimpleName(), baselineConfigurationMXBean, BaselineConfigurationMXBean.class);

        // Executors
        registerExecutorMBean("GridUtilityCacheExecutor", utilityCachePool);
        registerExecutorMBean("GridExecutionExecutor", execSvc);
        registerExecutorMBean("GridServicesExecutor", svcExecSvc);
        registerExecutorMBean("GridSystemExecutor", sysExecSvc);
        registerExecutorMBean("GridClassLoadingExecutor", p2pExecSvc);
        registerExecutorMBean("GridManagementExecutor", mgmtExecSvc);
        registerExecutorMBean("GridIgfsExecutor", igfsExecSvc);
        registerExecutorMBean("GridDataStreamExecutor", dataStreamExecSvc);
        registerExecutorMBean("GridAffinityExecutor", affExecSvc);
        registerExecutorMBean("GridCallbackExecutor", callbackExecSvc);
        registerExecutorMBean("GridQueryExecutor", qryExecSvc);
        registerExecutorMBean("GridSchemaExecutor", schemaExecSvc);

        if (idxExecSvc != null)
            registerExecutorMBean("GridIndexingExecutor", idxExecSvc);

        if (ctx.config().getConnectorConfiguration() != null)
            registerExecutorMBean("GridRestExecutor", restExecSvc);

        if (stripedExecSvc != null) {
            // striped executor uses a custom adapter
            registerMBean("Thread Pools",
                "StripedExecutor",
                new StripedExecutorMXBeanAdapter(stripedExecSvc),
                StripedExecutorMXBean.class);
        }

        if (customExecSvcs != null) {
            for (Map.Entry<String, ? extends ExecutorService> entry : customExecSvcs.entrySet())
                registerExecutorMBean(entry.getKey(), entry.getValue());
        }

        if (U.IGNITE_TEST_FEATURES_ENABLED) {
            WorkersControlMXBean workerCtrlMXBean = new WorkersControlMXBeanImpl(workersRegistry);

            registerMBean("Kernal", workerCtrlMXBean.getClass().getSimpleName(),
                workerCtrlMXBean, WorkersControlMXBean.class);
        }

        FailureHandlingMxBean blockOpCtrlMXBean = new FailureHandlingMxBeanImpl(workersRegistry,
            ctx.cache().context().database());

        registerMBean("Kernal", blockOpCtrlMXBean.getClass().getSimpleName(), blockOpCtrlMXBean,
            FailureHandlingMxBean.class);

        if (ctx.query().moduleEnabled())
            ctx.query().getIndexing().registerMxBeans(this);
    }

    /**
     * Registers a {@link ThreadPoolMXBean} for an executor.
     *
     * @param name name of the bean to register
     * @param exec executor to register a bean for
     * @throws TurboSQLCheckedException if registration fails.
     */
    private void registerExecutorMBean(String name, ExecutorService exec) throws TurboSQLCheckedException {
        registerMBean("Thread Pools", name, new ThreadPoolMXBeanAdapter(exec), ThreadPoolMXBean.class);
    }

    /**
     * Register an TurboSQL MBean.
     *
     * @param grp bean group name
     * @param name bean name
     * @param impl bean implementation
     * @param itf bean interface
     * @param <T> bean type
     * @throws TurboSQLCheckedException if registration fails
     */
    public <T> void registerMBean(String grp, String name, T impl, Class<T> itf) throws TurboSQLCheckedException {
        assert !U.IGNITE_MBEANS_DISABLED;

        try {
            ObjectName objName = U.registerMBean(
                ctx.config().getMBeanServer(),
                ctx.config().getTurboSQLInstanceName(),
                grp, name, impl, itf);

            if (log.isDebugEnabled())
                log.debug("Registered MBean: " + objName);

            mBeanNames.add(objName);
        }
        catch (JMException e) {
            throw new TurboSQLCheckedException("Failed to register MBean " + name, e);
        }
    }

    /**
     * Unregisters all previously registered MBeans.
     *
     * @return {@code true} if all mbeans were unregistered successfully; {@code false} otherwise.
     */
    public boolean unregisterAllMBeans() {
        boolean success = true;

        for (ObjectName name : mBeanNames)
            success = success && unregisterMBean(name);

        return success;
    }

    /**
     * Unregisters given MBean.
     *
     * @param mbean MBean to unregister.
     * @return {@code true} if successfully unregistered, {@code false} otherwise.
     */
    private boolean unregisterMBean(ObjectName mbean) {
        assert !U.IGNITE_MBEANS_DISABLED;

        try {
            ctx.config().getMBeanServer().unregisterMBean(mbean);

            if (log.isDebugEnabled())
                log.debug("Unregistered MBean: " + mbean);

            return true;
        }
        catch (JMException e) {
            U.error(log, "Failed to unregister MBean.", e);

            return false;
        }
    }
}
