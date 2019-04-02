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

package com.phonemetra.turbo.spi.indexing.noop;

import java.util.Collection;
import java.util.Iterator;
import javax.cache.Cache;
import com.phonemetra.turbo.spi.TurboSQLSpiAdapter;
import com.phonemetra.turbo.spi.TurboSQLSpiException;
import com.phonemetra.turbo.spi.TurboSQLSpiNoop;
import com.phonemetra.turbo.spi.indexing.IndexingQueryFilter;
import com.phonemetra.turbo.spi.indexing.IndexingSpi;
import org.jetbrains.annotations.Nullable;

/**
 * Default implementation of {@link IndexingSpi} which does not index cache.
 */
@TurboSQLSpiNoop
public class NoopIndexingSpi extends TurboSQLSpiAdapter implements IndexingSpi {
    /** {@inheritDoc} */
    @Override public Iterator<Cache.Entry<?,?>> query(@Nullable String cacheName, Collection<Object> params,
        @Nullable IndexingQueryFilter filters) throws TurboSQLSpiException {
        throw new TurboSQLSpiException("You have to configure custom GridIndexingSpi implementation.");
    }

    /** {@inheritDoc} */
    @Override public void store(@Nullable String cacheName, Object key, Object val, long expirationTime)
        throws TurboSQLSpiException {
        assert false;
    }

    /** {@inheritDoc} */
    @Override public void remove(@Nullable String cacheName, Object key) throws TurboSQLSpiException {
        assert false;
    }

    /** {@inheritDoc} */
    @Override public void spiStart(@Nullable String turboSQLInstanceName) throws TurboSQLSpiException {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public void spiStop() throws TurboSQLSpiException {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public NoopIndexingSpi setName(String name) {
        super.setName(name);

        return this;
    }
}