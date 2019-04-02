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

package com.phonemetra.turbo.internal.visor.log;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.phonemetra.turbo.internal.util.typedef.internal.S;
import com.phonemetra.turbo.internal.util.typedef.internal.U;
import com.phonemetra.turbo.internal.visor.VisorDataTransferObject;

/**
 * Result for log search operation.
 * Contains found line and several lines before and after, plus other info.
 */
public class VisorLogSearchTaskResult extends VisorDataTransferObject {
    /** */
    private static final long serialVersionUID = 0L;

    /** List of exceptions by node ID. */
    private Map<Exception, UUID> exceptions;

    /** List of log search results. */
    private List<VisorLogSearchResult> results;

    /**
     * Default constructor.
     */
    public VisorLogSearchTaskResult() {
        // No-op.
    }

    /**
     * Create log search result with given parameters.
     *
     * @param exceptions List of exceptions by node ID.
     * @param results List of log search results.
     */
    public VisorLogSearchTaskResult(Map<Exception, UUID> exceptions, List<VisorLogSearchResult> results) {
        this.exceptions = exceptions;
        this.results = results;
    }

    /**
     * @return List of exceptions by node ID.
     */
    public Map<Exception, UUID> getExceptions() {
        return exceptions;
    }

    /**
     * @return List of log search results.
     */
    public List<VisorLogSearchResult> getResults() {
        return results;
    }

    /** {@inheritDoc} */
    @Override protected void writeExternalData(ObjectOutput out) throws IOException {
        U.writeMap(out, exceptions);
        U.writeCollection(out, results);
    }

    /** {@inheritDoc} */
    @Override protected void readExternalData(byte protoVer, ObjectInput in) throws IOException, ClassNotFoundException {
        exceptions = U.readMap(in);
        results = U.readList(in);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(VisorLogSearchTaskResult.class, this);
    }
}