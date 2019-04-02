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

package com.phonemetra.turbo.internal.util;

import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;
import com.phonemetra.turbo.TurboSQLCheckedException;
import com.phonemetra.turbo.internal.util.lang.GridCloseableIterator;
import com.phonemetra.turbo.internal.util.lang.GridIteratorAdapter;

/**
 * Adapter for closeable iterator that can be safely closed concurrently.
 */
public abstract class GridCloseableIteratorAdapterEx<T> extends GridIteratorAdapter<T>
    implements GridCloseableIterator<T> {
    /** */
    private static final long serialVersionUID = 0L;

    /** Closed flag. */
    private final AtomicBoolean closed = new AtomicBoolean();

    /** {@inheritDoc} */
    @Override public final T nextX() throws TurboSQLCheckedException {
        if (closed.get())
            return null;

        try {
            if (!onHasNext())
                throw new NoSuchElementException();

            return onNext();
        }
        catch (TurboSQLCheckedException e) {
            if (closed.get())
                return null;
            else
                throw e;
        }
    }

    /**
     * @return Next element.
     * @throws TurboSQLCheckedException If failed.
     * @throws NoSuchElementException If no element found.
     */
    protected abstract T onNext() throws TurboSQLCheckedException;

    /** {@inheritDoc} */
    @Override public final boolean hasNextX() throws TurboSQLCheckedException {
        if (closed.get())
            return false;

        try {
            return onHasNext();
        }
        catch (TurboSQLCheckedException e) {
            if (closed.get())
                return false;
            else
                throw e;
        }
    }

    /**
     * @return {@code True} if iterator has next element.
     * @throws TurboSQLCheckedException If failed.
     */
    protected abstract boolean onHasNext() throws TurboSQLCheckedException;

    /** {@inheritDoc} */
    @Override public final void removeX() throws TurboSQLCheckedException {
        if (closed.get())
            throw new NoSuchElementException("Iterator has been closed.");

        try {
            onRemove();
        }
        catch (TurboSQLCheckedException e) {
            if (!closed.get())
                throw e;
        }
    }

    /**
     * Called on remove from iterator.
     *
     * @throws TurboSQLCheckedException If failed.
     */
    protected void onRemove() throws TurboSQLCheckedException {
        throw new UnsupportedOperationException("Remove is not supported.");
    }

    /** {@inheritDoc} */
    @Override public final void close() throws TurboSQLCheckedException {
        if (closed.compareAndSet(false, true))
            onClose();
    }

    /**
     * Invoked on iterator close.
     *
     * @throws TurboSQLCheckedException If closing failed.
     */
    protected void onClose() throws TurboSQLCheckedException {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public boolean isClosed() {
        return closed.get();
    }
}