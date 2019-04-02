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
import com.phonemetra.turbo.TurboSQLCheckedException;
import com.phonemetra.turbo.internal.util.lang.GridCloseableIterator;
import com.phonemetra.turbo.internal.util.lang.GridIteratorAdapter;

/**
 * Convenient adapter for closeable iterator.
 */
public abstract class GridCloseableIteratorAdapter<T> extends GridIteratorAdapter<T> implements
    GridCloseableIterator<T> {
    /** */
    private static final long serialVersionUID = 0L;

    /** Closed flag. */
    private boolean closed;

    /** {@inheritDoc} */
    @Override public final T nextX() throws TurboSQLCheckedException {
        if (!hasNextX())
            throw new NoSuchElementException();

        return onNext();
    }

    /**
     * @return Next element.
     * @throws TurboSQLCheckedException If failed.
     * @throws NoSuchElementException If no element found.
     */
    protected abstract T onNext() throws TurboSQLCheckedException;

    /** {@inheritDoc} */
    @Override public final boolean hasNextX() throws TurboSQLCheckedException {
        return !closed && onHasNext();
    }

    /**
     * @return {@code True} if iterator has next element.
     * @throws TurboSQLCheckedException If failed.
     */
    protected abstract boolean onHasNext() throws TurboSQLCheckedException;

    /** {@inheritDoc} */
    @Override public final void removeX() throws TurboSQLCheckedException {
        checkClosed();

        onRemove();
    }

    /**
     * Called on remove from iterator.
     *
     * @throws TurboSQLCheckedException If failed.
     */
    protected void onRemove() throws TurboSQLCheckedException {
        throw new UnsupportedOperationException();
    }

    /** {@inheritDoc} */
    @Override public final void close() throws TurboSQLCheckedException {
        if (!closed) {
            onClose();

            closed = true;
        }
    }

    /** {@inheritDoc} */
    @Override public boolean isClosed() {
        return closed;
    }

    /**
     * Invoked on iterator close.
     *
     * @throws TurboSQLCheckedException If closing failed.
     */
    protected void onClose() throws TurboSQLCheckedException {
        // No-op.
    }

    /**
     * Throws {@link NoSuchElementException} if iterator has been closed.
     *
     * @throws NoSuchElementException If iterator has already been closed.
     */
    protected final void checkClosed() throws NoSuchElementException {
        if (closed)
            throw new NoSuchElementException("Iterator has been closed.");
    }
}