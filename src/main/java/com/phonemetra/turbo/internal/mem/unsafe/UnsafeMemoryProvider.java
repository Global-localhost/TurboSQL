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

package com.phonemetra.turbo.internal.mem.unsafe;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import com.phonemetra.turbo.TurboSQLException;
import com.phonemetra.turbo.TurboSQLLogger;
import com.phonemetra.turbo.internal.mem.DirectMemoryProvider;
import com.phonemetra.turbo.internal.mem.DirectMemoryRegion;
import com.phonemetra.turbo.internal.mem.UnsafeChunk;
import com.phonemetra.turbo.internal.util.GridUnsafe;
import com.phonemetra.turbo.internal.util.typedef.internal.U;

/**
 * Memory provider implementation based on unsafe memory access.
 * <p>
 * Supports memory reuse semantics.
 */
public class UnsafeMemoryProvider implements DirectMemoryProvider {
    /** */
    private long[] sizes;

    /** */
    private List<DirectMemoryRegion> regions;

    /** */
    private TurboSQLLogger log;

    /** Flag shows if current memory provider have been already initialized. */
    private boolean isInit;

    /** */
    private int used = 0;

    /**
     * @param log TurboSQL logger to use.
     */
    public UnsafeMemoryProvider(TurboSQLLogger log) {
        this.log = log;
    }

    /** {@inheritDoc} */
    @Override public void initialize(long[] sizes) {
        if (isInit)
            return;

        this.sizes = sizes;

        regions = new ArrayList<>();

        isInit = true;
    }

    /** {@inheritDoc} */
    @Override public void shutdown(boolean deallocate) {
        if (regions != null) {
            for (Iterator<DirectMemoryRegion> it = regions.iterator(); it.hasNext(); ) {
                DirectMemoryRegion chunk = it.next();

                if (deallocate) {
                    GridUnsafe.freeMemory(chunk.address());

                    // Safety.
                    it.remove();
                }
            }

            if (!deallocate)
                used = 0;
        }
    }

    /** {@inheritDoc} */
    @Override public DirectMemoryRegion nextRegion() {
        if (used == sizes.length)
            return null;

        if (used < regions.size())
            return regions.get(used++);

        long chunkSize = sizes[regions.size()];

        long ptr;

        try {
            ptr = GridUnsafe.allocateMemory(chunkSize);
        }
        catch (IllegalArgumentException e) {
            String msg = "Failed to allocate next memory chunk: " + U.readableSize(chunkSize, true) +
                ". Check if chunkSize is too large and 32-bit JVM is used.";

            if (regions.isEmpty())
                throw new TurboSQLException(msg, e);

            U.error(log, msg);

            return null;
        }

        if (ptr <= 0) {
            U.error(log, "Failed to allocate next memory chunk: " + U.readableSize(chunkSize, true));

            return null;
        }

        DirectMemoryRegion region = new UnsafeChunk(ptr, chunkSize);

        regions.add(region);

        used++;

        return region;
    }
}
