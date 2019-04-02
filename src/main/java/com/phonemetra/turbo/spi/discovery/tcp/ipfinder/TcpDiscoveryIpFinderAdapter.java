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

package com.phonemetra.turbo.spi.discovery.tcp.ipfinder;

import java.net.InetSocketAddress;
import java.util.Collection;
import com.phonemetra.turbo.TurboSQL;
import com.phonemetra.turbo.internal.util.tostring.GridToStringExclude;
import com.phonemetra.turbo.internal.util.typedef.internal.S;
import com.phonemetra.turbo.resources.TurboSQLInstanceResource;
import com.phonemetra.turbo.spi.TurboSQLSpiConfiguration;
import com.phonemetra.turbo.spi.TurboSQLSpiContext;
import com.phonemetra.turbo.spi.TurboSQLSpiException;
import com.phonemetra.turbo.spi.discovery.DiscoverySpi;
import com.phonemetra.turbo.spi.discovery.tcp.TcpDiscoverySpi;

/**
 * IP finder interface implementation adapter.
 */
public abstract class TcpDiscoveryIpFinderAdapter implements TcpDiscoveryIpFinder {
    /** Shared flag. */
    private boolean shared;

    /** SPI context. */
    @GridToStringExclude
    private volatile TurboSQLSpiContext spiCtx;

    /**
     * TurboSQL instance.
     *
     * @deprecated Since 2.8. May contain an invalid TurboSQL instance when multiple nodes shares same
     * {@link TcpDiscoveryIpFinder} instance.
     */
    @Deprecated
    @TurboSQLInstanceResource
    @GridToStringExclude
    protected TurboSQL turboSQL;

    /** {@inheritDoc} */
    @Override public void onSpiContextInitialized(TurboSQLSpiContext spiCtx) throws TurboSQLSpiException {
        this.spiCtx = spiCtx;
    }

    /** {@inheritDoc} */
    @Override public void onSpiContextDestroyed() {
        spiCtx = null;
    }

    /** {@inheritDoc} */
    @Override public void initializeLocalAddresses(Collection<InetSocketAddress> addrs) throws TurboSQLSpiException {
        registerAddresses(addrs);
    }

    /** {@inheritDoc} */
    @Override public boolean isShared() {
        return shared;
    }

    /**
     * Sets shared flag. If {@code true} then it is expected that IP addresses registered
     * with IP finder will be seen by IP finders on all other nodes.
     *
     * @param shared {@code true} if this IP finder is shared.
     * @return {@code this} for chaining.
     */
    @TurboSQLSpiConfiguration(optional = true)
    public TcpDiscoveryIpFinderAdapter setShared(boolean shared) {
        this.shared = shared;

        return this;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(TcpDiscoveryIpFinderAdapter.class, this);
    }

    /** {@inheritDoc} */
    @Override public void close() {
        // No-op.
    }

    /**
     * @return {@code True} if TCP discovery works in client mode.
     * @deprecated Since 2.8. May return incorrect value if client and server nodes shares same {@link
     * TcpDiscoveryIpFinder} instance.
     */
    @Deprecated
    protected boolean discoveryClientMode() {
        boolean clientMode;

        TurboSQL turboSQL0 = turboSQL;

        if (turboSQL0 != null) { // Can be null if used in tests without starting TurboSQL.
            DiscoverySpi discoSpi = turboSQL0.configuration().getDiscoverySpi();

            if (!(discoSpi instanceof TcpDiscoverySpi))
                throw new TurboSQLSpiException("TcpDiscoveryIpFinder should be used with TcpDiscoverySpi: " + discoSpi);

            clientMode = turboSQL0.configuration().isClientMode() && !((TcpDiscoverySpi)discoSpi).isForceServerMode();
        }
        else
            clientMode = false;

        return clientMode;
    }

    /**
     * @return SPI context.
     */
    protected TurboSQLSpiContext spiContext() {
        return spiCtx;
    }
}