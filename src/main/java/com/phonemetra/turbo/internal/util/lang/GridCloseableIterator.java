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

package com.phonemetra.turbo.internal.util.lang;

import com.phonemetra.turbo.TurboSQLCheckedException;
import com.phonemetra.turbo.spi.TurboSQLSpiCloseableIterator;

/**
 * Defines "rich" closeable iterator interface that is also acts like lambda function and iterable.
 * <p>
 * Any instance of this interface should be closed when it's no longer needed.
 * <p>
 * Here is the common use of closable iterator.
 * <pre>
 * GridCloseableIterator<T> iter = getIterator();
 *
 * try {
 *     while(iter.hasNext()) {
 *         T next = iter.next();
 *         ...
 *         ...
 *     }
 * }
 * finally {
 *     iter.close();
 * }
 * </pre>
 */
public interface GridCloseableIterator<T> extends GridIterator<T>, TurboSQLSpiCloseableIterator<T>, AutoCloseable {
    /**
     * Closes the iterator and frees all the resources held by the iterator.
     * Iterator can not be used any more after calling this method.
     * <p>
     * The method is invoked automatically on objects managed by the
     * {@code try-with-resources} statement.
     *
     * @throws TurboSQLCheckedException In case of error.
     */
    @Override public void close() throws TurboSQLCheckedException;

    /**
     * Checks if iterator has been closed.
     *
     * @return {@code True} if iterator has been closed.
     */
    public boolean isClosed();
}