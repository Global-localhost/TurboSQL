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

import com.phonemetra.turbo.TurboSQLCheckedException;
import com.phonemetra.turbo.internal.util.lang.GridCloseableIterator;
import com.phonemetra.turbo.spi.TurboSQLSpiCloseableIterator;

/**
 * Wrapper used to covert {@link com.phonemetra.turbo.spi.TurboSQLSpiCloseableIterator} to {@link GridCloseableIterator}.
 */
public class GridSpiCloseableIteratorWrapper<T> extends GridCloseableIteratorAdapter<T> {
    /** */
    private static final long serialVersionUID = 0L;

    /** */
    private final TurboSQLSpiCloseableIterator<T> iter;

    /**
     * @param iter Spi iterator.
     */
    public GridSpiCloseableIteratorWrapper(TurboSQLSpiCloseableIterator<T> iter) {
        assert iter != null;

        this.iter = iter;
    }

    /** {@inheritDoc} */
    @Override protected T onNext() throws TurboSQLCheckedException {
        return iter.next();
    }

    /** {@inheritDoc} */
    @Override protected boolean onHasNext() throws TurboSQLCheckedException {
        return iter.hasNext();
    }

    /** {@inheritDoc} */
    @Override protected void onClose() throws TurboSQLCheckedException {
        iter.close();
    }

    /** {@inheritDoc} */
    @Override protected void onRemove() throws TurboSQLCheckedException {
        iter.remove();
    }
}