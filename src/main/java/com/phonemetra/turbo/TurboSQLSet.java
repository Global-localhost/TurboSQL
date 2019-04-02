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

package com.phonemetra.turbo;

import java.io.Closeable;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import com.phonemetra.turbo.lang.TurboSQLCallable;
import com.phonemetra.turbo.lang.TurboSQLRunnable;

/**
 * Set implementation based on on In-Memory Data Grid.
 * <h1 class="header">Overview</h1>
 * Cache set implements {@link Set} interface and provides all methods from collections.
 * Note that all {@link Collection} methods in the set may throw {@link TurboSQLException} in case of failure
 * or if set was removed.
 * <h1 class="header">Collocated vs Non-collocated</h1>
 * Set items can be placed on one node or distributed throughout grid nodes
 * (governed by {@code collocated} parameter). {@code Non-collocated} mode is provided only
 * for partitioned caches. If {@code collocated} parameter is {@code true}, then all set items
 * will be collocated on one node, otherwise items will be distributed through all grid nodes.
 * @see TurboSQL#set(String, com.phonemetra.turbo.configuration.CollectionConfiguration)
 */
public interface TurboSQLSet<T> extends Set<T>, Closeable {
    /** {@inheritDoc} */
    @Override boolean add(T t) throws TurboSQLException;

    /** {@inheritDoc} */
    @Override boolean addAll(Collection<? extends T> c) throws TurboSQLException;

    /** {@inheritDoc} */
    @Override void clear() throws TurboSQLException;

    /** {@inheritDoc} */
    @Override boolean contains(Object o) throws TurboSQLException;

    /** {@inheritDoc} */
    @Override boolean containsAll(Collection<?> c) throws TurboSQLException;

    /** {@inheritDoc} */
    @Override boolean isEmpty() throws TurboSQLException;

    /** {@inheritDoc} */
    @Override Iterator<T> iterator() throws TurboSQLException;

    /** {@inheritDoc} */
    @Override boolean remove(Object o) throws TurboSQLException;

    /** {@inheritDoc} */
    @Override boolean removeAll(Collection<?> c) throws TurboSQLException;

    /** {@inheritDoc} */
    @Override boolean retainAll(Collection<?> c) throws TurboSQLException;

    /** {@inheritDoc} */
    @Override int size() throws TurboSQLException;

    /** {@inheritDoc} */
    @Override Object[] toArray() throws TurboSQLException;

    /** {@inheritDoc} */
    @Override <T1> T1[] toArray(T1[] a) throws TurboSQLException;

    /**
     * Removes this set.
     *
     * @throws TurboSQLException If operation failed.
     */
    @Override public void close() throws TurboSQLException;

    /**
     * Gets set name.
     *
     * @return Set name.
     */
    public String name();

    /**
     * Returns {@code true} if this set can be kept on the one node only.
     * Returns {@code false} if this set can be kept on the many nodes.
     *
     * @return {@code True} if this set is in {@code collocated} mode {@code false} otherwise.
     */
    public boolean collocated();

    /**
     * Gets status of set.
     *
     * @return {@code True} if set was removed from cache {@code false} otherwise.
     */
    public boolean removed();

    /**
     * Executes given job on collocated set on the node where the set is located
     * (a.k.a. affinity co-location).
     * <p>
     * This is not supported for non-collocated sets.
     *
     * @param job Job which will be co-located with the set.
     * @throws TurboSQLException If job failed.
     */
    public void affinityRun(TurboSQLRunnable job) throws TurboSQLException;

    /**
     * Executes given job on collocated set on the node where the set is located
     * (a.k.a. affinity co-location).
     * <p>
     * This is not supported for non-collocated sets.
     *
     * @param job Job which will be co-located with the set.
     * @throws TurboSQLException If job failed.
     */
    public <R> R affinityCall(TurboSQLCallable<R> job) throws TurboSQLException;
}
