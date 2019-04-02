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

package com.phonemetra.turbo.spi.indexing;

import java.util.Collection;
import java.util.Iterator;
import javax.cache.Cache;
import com.phonemetra.turbo.spi.TurboSQLSpi;
import com.phonemetra.turbo.spi.TurboSQLSpiException;
import org.jetbrains.annotations.Nullable;

/**
 * Indexing SPI allows user to index cache content. Using indexing SPI user can index data in cache and run queries.
 * <p>
 * <b>NOTE:</b> this SPI (i.e. methods in this interface) should never be used directly. SPIs provide
 * internal view on the subsystem and is used internally by TurboSQL kernal. In rare use cases when
 * access to a specific implementation of this SPI is required - an instance of this SPI can be obtained
 * via {@link com.phonemetra.turbo.TurboSQL#configuration()} method to check its configuration properties or call other non-SPI
 * methods. Note again that calling methods from this interface on the obtained instance can lead
 * to undefined behavior and explicitly not supported.
 *
 * <b>NOTE:</b> Key and value arguments of TurboSQLSpi methods can be {@link com.phonemetra.turbo.binary.BinaryObject} instances.
 * BinaryObjects can be deserialized manually if original objects needed.
 *
 * Here is a Java example on how to configure SPI.
 * <pre name="code" class="java">
 * IndexingSpi spi = new MyIndexingSpi();
 *
 * TurboSQLConfiguration cfg = new TurboSQLConfiguration();
 *
 * // Overrides default indexing SPI.
 * cfg.setIndexingSpi(spi);
 *
 * // Starts grid.
 * Ignition.start(cfg);
 * </pre>
 * Here is an example of how to configure SPI from Spring XML configuration file.
 * <pre name="code" class="xml">
 * &lt;property name=&quot;indexingSpi&quot;&gt;
 *     &lt;bean class=&quot;com.example.MyIndexingSpi&quot;&gt;
 *     &lt;/bean&gt;
 * &lt;/property&gt;
 * </pre>
 * <br>
 * For information about Spring framework visit <a href="http://www.springframework.org/">www.springframework.org</a>
 */
public interface IndexingSpi extends TurboSQLSpi {
    /**
     * Executes query.
     *
     * @param cacheName Cache name.
     * @param params Query parameters.
     * @param filters System filters.
     * @return Query result. If the iterator implements {@link AutoCloseable} it will be correctly closed.
     * @throws TurboSQLSpiException If failed.
     */
    public Iterator<Cache.Entry<?,?>> query(@Nullable String cacheName, Collection<Object> params,
        @Nullable IndexingQueryFilter filters) throws TurboSQLSpiException;

    /**
     * Updates index. Note that key is unique for cache, so if cache contains multiple indexes
     * the key should be removed from indexes other than one being updated.
     *
     * @param cacheName Cache name.
     * @param key Key.
     * @param val Value.
     * @param expirationTime Expiration time or 0 if never expires.
     * @throws TurboSQLSpiException If failed.
     */
    public void store(@Nullable String cacheName, Object key, Object val, long expirationTime) throws TurboSQLSpiException;

    /**
     * Removes index entry by key.
     *
     * @param cacheName Cache name.
     * @param key Key.
     * @throws TurboSQLSpiException If failed.
     */
    public void remove(@Nullable String cacheName, Object key) throws TurboSQLSpiException;
}
