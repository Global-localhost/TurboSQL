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
import com.phonemetra.turbo.spi.TurboSQLSpiContext;
import com.phonemetra.turbo.spi.TurboSQLSpiException;

/**
 * IP finder interface for {@link com.phonemetra.turbo.spi.discovery.tcp.TcpDiscoverySpi}.
 */
public interface TcpDiscoveryIpFinder {
    /**
     * Callback invoked when SPI context is initialized after {@link com.phonemetra.turbo.spi.discovery.tcp.TcpDiscoverySpi#spiStart(String)}
     * method is completed, SPI context can be stored for future access.
     *
     * @param spiCtx Spi context.
     * @throws TurboSQLSpiException In case of error.
     */
    public void onSpiContextInitialized(TurboSQLSpiContext spiCtx) throws TurboSQLSpiException;

    /**
     * Callback invoked prior to stopping grid before SPI context is destroyed.
     * Note that invoking SPI context after this callback is complete is considered
     * illegal and may produce unknown results.
     */
    public void onSpiContextDestroyed();

    /**
     * Initializes addresses discovery SPI binds to.
     *
     * @param addrs Addresses discovery SPI binds to.
     * @throws TurboSQLSpiException In case of error.
     */
    public void initializeLocalAddresses(Collection<InetSocketAddress> addrs) throws TurboSQLSpiException;

    /**
     * Gets all addresses registered in this finder.
     *
     * @return All known addresses, potentially empty, but never {@code null}.
     * @throws TurboSQLSpiException In case of error.
     */
    public Collection<InetSocketAddress> getRegisteredAddresses() throws TurboSQLSpiException;

    /**
     * Checks whether IP finder is shared or not.
     * <p>
     * If this property is set to {@code true} then IP finder allows to add and remove
     * addresses in runtime and this is how, for example, IP finder should work in
     * Amazon EC2 environment or any other environment where IPs may not be known beforehand.
     * <p>
     * If this property is set to {@code false} then IP finder is immutable and all the addresses
     * should be listed in configuration before TurboSQL start. This is the most use case for IP finders
     * local to current VM. Since, usually such IP finders are created per each TurboSQL instance and
     * all the known IPs are listed right away, but there is also an option to make such IP finders shared
     * by setting this property to {@code true} and literally share it between local VM TurboSQL instances.
     * This way user does not have to list any IPs before start, instead all starting nodes add their addresses
     * to the finder, then get the registered addresses and continue with discovery procedure.
     *
     * @return {@code true} if IP finder is shared.
     */
    public boolean isShared();

    /**
     * Registers new addresses.
     * <p>
     * Implementation should accept duplicates quietly, but should not register address if it
     * is already registered.
     *
     * @param addrs Addresses to register. Not {@code null} and not empty.
     * @throws TurboSQLSpiException In case of error.
     */
    public void registerAddresses(Collection<InetSocketAddress> addrs) throws TurboSQLSpiException;

    /**
     * Unregisters provided addresses.
     * <p>
     * Implementation should accept addresses that are currently not
     * registered quietly (just no-op).
     *
     * @param addrs Addresses to unregister. Not {@code null} and not empty.
     * @throws TurboSQLSpiException In case of error.
     */
    public void unregisterAddresses(Collection<InetSocketAddress> addrs) throws TurboSQLSpiException;

    /**
     * Closes this IP finder and releases any system resources associated with it.
     */
    public void close();
}
