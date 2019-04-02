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

import com.phonemetra.turbo.plugin.segmentation.SegmentationPolicy;
import org.jetbrains.annotations.Nullable;

/**
 * Possible states of {@link com.phonemetra.turbo.Ignition}. You can register a listener for
 * state change notifications via {@link com.phonemetra.turbo.Ignition#addListener(IgnitionListener)}
 * method.
 */
public enum TurboSQLState {
    /**
     * Grid factory started.
     */
    STARTED,

    /**
     * Grid factory stopped.
     */
    STOPPED,

    /**
     * Grid factory stopped due to network segmentation issues.
     * <p>
     * Notification on this state will be fired only when segmentation policy is
     * set to {@link SegmentationPolicy#STOP} or {@link SegmentationPolicy#RESTART_JVM}
     * and node is stopped from internals of TurboSQL after segment becomes invalid.
     */
    STOPPED_ON_SEGMENTATION,

    /**
     * Grid factory stopped due to a critical failure.
     */
    STOPPED_ON_FAILURE;

    /** Enumerated values. */
    private static final TurboSQLState[] VALS = values();

    /**
     * Efficiently gets enumerated value from its ordinal.
     *
     * @param ord Ordinal value.
     * @return Enumerated value.
     */
    @Nullable public static TurboSQLState fromOrdinal(byte ord) {
        return ord >= 0 && ord < VALS.length ? VALS[ord] : null;
    }
}