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

package com.phonemetra.turbo.spi;

import java.text.DateFormat;
import java.util.Date;
import java.util.UUID;
import com.phonemetra.turbo.internal.util.typedef.X;
import com.phonemetra.turbo.internal.util.typedef.internal.U;

/**
 * This class provides convenient adapter for MBean implementations.
 */
public class TurboSQLSpiMBeanAdapter implements TurboSQLSpiManagementMBean {
    /** */
    protected TurboSQLSpiAdapter spiAdapter;

    /**
     * Constructor
     *
     * @param spiAdapter Spi implementation.
     */
    public TurboSQLSpiMBeanAdapter(TurboSQLSpiAdapter spiAdapter) {
        this.spiAdapter = spiAdapter;
    }

    /** {@inheritDoc} */
    @Override public final String getStartTimestampFormatted() {
        return DateFormat.getDateTimeInstance().format(new Date(spiAdapter.getStartTstamp()));
    }

    /** {@inheritDoc} */
    @Override public final String getUpTimeFormatted() {
        return X.timeSpan2DHMSM(getUpTime());
    }

    /** {@inheritDoc} */
    @Override public final long getStartTimestamp() {
        return spiAdapter.getStartTstamp();
    }

    /** {@inheritDoc} */
    @Override public final long getUpTime() {
        final long startTstamp = spiAdapter.getStartTstamp();

        return startTstamp == 0 ? 0 : U.currentTimeMillis() - startTstamp;
    }

    /** {@inheritDoc} */
    @Override public UUID getLocalNodeId() {
        return spiAdapter.turboSQL.cluster().localNode().id();
    }

    /** {@inheritDoc} */
    @Override public final String getTurboSQLHome() {
        return spiAdapter.turboSQL.configuration().getTurboSQLHome();
    }

    /** {@inheritDoc} */
    @Override public String getName() {
        return spiAdapter.getName();
    }
}
