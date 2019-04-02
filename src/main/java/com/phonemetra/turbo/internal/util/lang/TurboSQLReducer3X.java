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
import com.phonemetra.turbo.internal.util.typedef.F;
import com.phonemetra.turbo.internal.util.typedef.RX3;

/**
 * Convenient reducer subclass that allows for thrown grid exception. This class
 * implements {@link #apply()} method that calls {@link #applyx()} method and
 * properly wraps {@link TurboSQLCheckedException} into {@link GridClosureException} instance.
 * @see RX3
 */
public abstract class TurboSQLReducer3X<E1, E2, E3, R> implements TurboSQLReducer3<E1, E2, E3, R> {
    /** */
    private static final long serialVersionUID = 0L;

    /** {@inheritDoc} */
    @Override public R apply() {
        try {
            return applyx();
        }
        catch (TurboSQLCheckedException e) {
            throw F.wrap(e);
        }
    }

    /**
     * Reducer body that can throw {@link TurboSQLCheckedException}.
     *
     * @return Reducer return value.
     * @throws TurboSQLCheckedException Thrown in case of any error condition inside of the reducer.
     */
    public abstract R applyx() throws TurboSQLCheckedException;
}