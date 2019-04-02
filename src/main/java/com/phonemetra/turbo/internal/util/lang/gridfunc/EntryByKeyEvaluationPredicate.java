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

package com.phonemetra.turbo.internal.util.lang.gridfunc;

import java.util.Map;
import com.phonemetra.turbo.internal.util.lang.GridFunc;
import com.phonemetra.turbo.internal.util.typedef.internal.S;
import com.phonemetra.turbo.lang.TurboSQLPredicate;

/**
 * Predicate evaluates to true for given value.
 * Note that evaluation will be short-circuit when first predicate evaluated to false is found.
 */
public class EntryByKeyEvaluationPredicate<K, V> implements TurboSQLPredicate<Map.Entry<K, V>> {
    /** */
    private static final long serialVersionUID = 0L;

    /** */
    private final TurboSQLPredicate<? super K>[] preds;

    /**
     * @param preds Optional set of predicates to use for filtration. If none provided - original map (or its copy) will be
     * returned.
     */
    public EntryByKeyEvaluationPredicate(TurboSQLPredicate<? super K>... preds) {
        this.preds = preds;
    }

    /** {@inheritDoc} */
    @Override public boolean apply(Map.Entry<K, V> e) {
        return GridFunc.isAll(e.getKey(), preds);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(EntryByKeyEvaluationPredicate.class, this);
    }
}
