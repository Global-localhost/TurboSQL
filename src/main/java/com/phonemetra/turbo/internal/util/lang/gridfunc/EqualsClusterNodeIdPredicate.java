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

import java.util.UUID;
import com.phonemetra.turbo.cluster.ClusterNode;
import com.phonemetra.turbo.internal.util.typedef.internal.S;
import com.phonemetra.turbo.lang.TurboSQLPredicate;

/**
 * Creates grid node predicate evaluating on the given node ID.
 */
public class EqualsClusterNodeIdPredicate<T extends ClusterNode> implements TurboSQLPredicate<T> {
    /** */
    private static final long serialVersionUID = -7082730222779476623L;

    /** */
    private final UUID nodeId;

    /**
     * @param nodeId (Collection)
     */
    public EqualsClusterNodeIdPredicate(UUID nodeId) {
        this.nodeId = nodeId;
    }

    /** {@inheritDoc} */
    @Override public boolean apply(ClusterNode e) {
        return e.id().equals(nodeId);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(EqualsClusterNodeIdPredicate.class, this);
    }
}
