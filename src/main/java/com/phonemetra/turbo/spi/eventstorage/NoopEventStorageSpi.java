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

package com.phonemetra.turbo.spi.eventstorage;

import java.util.Collection;
import java.util.Collections;
import com.phonemetra.turbo.events.Event;
import com.phonemetra.turbo.lang.TurboSQLPredicate;
import com.phonemetra.turbo.spi.TurboSQLSpiAdapter;
import com.phonemetra.turbo.spi.TurboSQLSpiException;
import com.phonemetra.turbo.spi.TurboSQLSpiMultipleInstancesSupport;
import org.jetbrains.annotations.Nullable;

/**
 * No-op implementation of {@link EventStorageSpi}.
 */
@TurboSQLSpiMultipleInstancesSupport(true)
public class NoopEventStorageSpi extends TurboSQLSpiAdapter implements EventStorageSpi {
    /** {@inheritDoc} */
    @Override public <T extends Event> Collection<T> localEvents(TurboSQLPredicate<T> p) {
        return Collections.emptyList();
    }

    /** {@inheritDoc} */
    @Override public void record(Event evt) throws TurboSQLSpiException {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public void spiStart(@Nullable String gridName) throws TurboSQLSpiException {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public void spiStop() throws TurboSQLSpiException {
        // No-op.
    }
}
