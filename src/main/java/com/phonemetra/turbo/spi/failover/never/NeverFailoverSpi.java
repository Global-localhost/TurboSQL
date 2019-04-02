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

package com.phonemetra.turbo.spi.failover.never;

import java.util.List;
import com.phonemetra.turbo.TurboSQLLogger;
import com.phonemetra.turbo.cluster.ClusterNode;
import com.phonemetra.turbo.internal.util.typedef.internal.S;
import com.phonemetra.turbo.internal.util.typedef.internal.U;
import com.phonemetra.turbo.resources.LoggerResource;
import com.phonemetra.turbo.spi.TurboSQLSpiAdapter;
import com.phonemetra.turbo.spi.TurboSQLSpiException;
import com.phonemetra.turbo.spi.TurboSQLSpiMBeanAdapter;
import com.phonemetra.turbo.spi.TurboSQLSpiMultipleInstancesSupport;
import com.phonemetra.turbo.spi.failover.FailoverContext;
import com.phonemetra.turbo.spi.failover.FailoverSpi;

/**
 * This class provides failover SPI implementation that never fails over. This implementation
 * never fails over a failed job by always returning {@code null} out of
 * {@link com.phonemetra.turbo.spi.failover.FailoverSpi#failover(com.phonemetra.turbo.spi.failover.FailoverContext, List)}
 * method.
 * <h1 class="header">Configuration</h1>
 * <h2 class="header">Mandatory</h2>
 * This SPI has no mandatory configuration parameters.
 * <h2 class="header">Optional</h2>
 * This SPI has no optional configuration parameters.
 * <p>
 * Here is a Java example on how to configure grid with {@code GridNeverFailoverSpi}:
 * <pre name="code" class="java">
 * NeverFailoverSpi spi = new NeverFailoverSpi();
 *
 * TurboSQLConfiguration cfg = new TurboSQLConfiguration();
 *
 * // Override default failover SPI.
 * cfg.setFailoverSpiSpi(spi);
 *
 * // Starts grid.
 * G.start(cfg);
 * </pre>
 * Here is an example on how to configure grid with {@link NeverFailoverSpi} from Spring XML configuration file:
 * <pre name="code" class="xml">
 * &lt;property name="failoverSpi"&gt;
 * &lt;bean class="com.phonemetra.turbo.spi.failover.never.NeverFailoverSpi"/&gt;
 * &lt;/property&gt;
 * </pre>
 * <br>
 * For information about Spring framework visit <a href="http://www.springframework.org/">www.springframework.org</a>
 *
 * @see com.phonemetra.turbo.spi.failover.FailoverSpi
 */
@TurboSQLSpiMultipleInstancesSupport(true)
public class NeverFailoverSpi extends TurboSQLSpiAdapter implements FailoverSpi {
    /** Injected grid logger. */
    @LoggerResource
    private TurboSQLLogger log;

    /** {@inheritDoc} */
    @Override public void spiStart(String turboSQLInstanceName) throws TurboSQLSpiException {
        // Start SPI start stopwatch.
        startStopwatch();

        registerMBean(turboSQLInstanceName, new NeverFailoverSpiMBeanImpl(this), NeverFailoverSpiMBean.class);

        // Ack ok start.
        if (log.isDebugEnabled())
            log.debug(startInfo());
    }

    /** {@inheritDoc} */
    @Override public void spiStop() throws TurboSQLSpiException {
        unregisterMBean();

        // Ack ok stop.
        if (log.isDebugEnabled())
            log.debug(stopInfo());
    }

    /** {@inheritDoc} */
    @Override public ClusterNode failover(FailoverContext ctx, List<ClusterNode> top) {
        U.warn(log, "Returning 'null' node for failed job (failover will not happen) [job=" +
            ctx.getJobResult().getJob() + ", task=" + ctx.getTaskSession().getTaskName() +
            ", sessionId=" + ctx.getTaskSession().getId() + ']');

        return null;
    }

    /** {@inheritDoc} */
    @Override public NeverFailoverSpi setName(String name) {
        super.setName(name);

        return this;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(NeverFailoverSpi.class, this);
    }

    /**
     * MBean implementation for NeverFailoverSpi.
     */
    private class NeverFailoverSpiMBeanImpl extends TurboSQLSpiMBeanAdapter implements NeverFailoverSpiMBean {
        /** {@inheritDoc} */
        NeverFailoverSpiMBeanImpl(TurboSQLSpiAdapter spiAdapter) {
            super(spiAdapter);
        }
    }
}