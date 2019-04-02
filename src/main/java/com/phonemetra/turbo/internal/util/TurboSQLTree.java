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
import com.phonemetra.turbo.internal.util.lang.GridCursor;
import org.jetbrains.annotations.Nullable;

/**
 * Interface for turboSQL internal tree.
 */
public interface TurboSQLTree<L, T> {
    /**
     * Put value in this tree.
     *
     * @param val Value to be associated with the specified key.
     * @return The previous value associated with key.
     * @throws TurboSQLCheckedException If failed.
     */
    public T put(T val) throws TurboSQLCheckedException;

    /**
     * @param key Key.
     * @param x Implementation specific argument, {@code null} always means that we need a full detached data row.
     * @param c Closure.
     * @throws TurboSQLCheckedException If failed.
     */
    public void invoke(L key, Object x, InvokeClosure<T> c) throws TurboSQLCheckedException;

    /**
     * Returns the value to which the specified key is mapped, or {@code null} if this tree contains no mapping for the
     * key.
     *
     * @param key the key whose associated value is to be returned
     * @return the value to which the specified key is mapped, or {@code null} if this tree contains no mapping for the
     *  key.
     * @throws TurboSQLCheckedException If failed.
     */
    public T findOne(L key) throws TurboSQLCheckedException;

    /**
     * Returns a cursor from lower to upper bounds inclusive.
     *
     * @param lower Lower bound or {@code null} if unbounded.
     * @param upper Upper bound or {@code null} if unbounded.
     * @return Cursor.
     * @throws TurboSQLCheckedException If failed.
     */
    public GridCursor<T> find(L lower, L upper) throws TurboSQLCheckedException;

    /**
     * Returns a cursor from lower to upper bounds inclusive.
     *
     * @param lower Lower bound or {@code null} if unbounded.
     * @param upper Upper bound or {@code null} if unbounded.
     * @param x Implementation specific argument, {@code null} always means that we need to return full detached
     *     data row.
     * @return Cursor.
     * @throws TurboSQLCheckedException If failed.
     */
    public GridCursor<T> find(L lower, L upper, Object x) throws TurboSQLCheckedException;

    /**
     * Returns a value mapped to the lowest key, or {@code null} if tree is empty
     * @return Value.
     * @throws TurboSQLCheckedException If failed.
     */
    public T findFirst() throws TurboSQLCheckedException;

    /**
     * Returns a value mapped to the greatest key, or {@code null} if tree is empty
     * @return Value.
     * @throws TurboSQLCheckedException If failed.
     */
    public T findLast() throws TurboSQLCheckedException;

    /**
     * Removes the mapping for a key from this tree if it is present.
     *
     * @param key Key whose mapping is to be removed from the tree.
     * @return The previous value associated with key, or null if there was no mapping for key.
     * @throws TurboSQLCheckedException If failed.
     */
    public T remove(L key) throws TurboSQLCheckedException;

    /**
     * Returns the number of elements in this tree.
     *
     * @return the number of elements in this tree
     * @throws TurboSQLCheckedException If failed.
     */
    public long size() throws TurboSQLCheckedException;

    /**
     *
     */
    interface InvokeClosure<T> {
        /**
         *
         * @param row Old row or {@code null} if old row not found.
         * @throws TurboSQLCheckedException If failed.
         */
        void call(@Nullable T row) throws TurboSQLCheckedException;

        /**
         * @return New row for {@link OperationType#PUT} operation.
         */
        T newRow();

        /**
         * @return Operation type for this closure or {@code null} if it is unknown.
         *      After method {@link #call(Object)} has been called, operation type must
         *      be know and this method can not return {@code null}.
         */
        OperationType operationType();
    }

    /**
     *
     */
    enum OperationType {
        /** */
        NOOP,

        /** */
        REMOVE,

        /** */
        PUT
    }
}
