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

package com.phonemetra.turbo.internal.managers.discovery;

import com.phonemetra.turbo.TurboSQLLogger;
import com.phonemetra.turbo.cluster.ClusterNode;
import com.phonemetra.turbo.spi.discovery.DiscoverySpi;
import com.phonemetra.turbo.spi.discovery.DiscoverySpiCustomMessage;

/**
 * For TESTING only.
 */
public interface TurboSQLDiscoverySpiInternalListener {
    /**
     * @param locNode Local node.
     * @param log Log.
     */
    public void beforeJoin(ClusterNode locNode, TurboSQLLogger log);

    /**
     * @param locNode Local node.
     * @param log Logger.
     */
    public default void beforeReconnect(ClusterNode locNode, TurboSQLLogger log) {
        // No-op.
    }

    /**
     * @param spi SPI instance.
     * @param log Logger.
     * @param msg Custom message.
     * @return {@code False} to cancel event send.
     */
    public boolean beforeSendCustomEvent(DiscoverySpi spi, TurboSQLLogger log, DiscoverySpiCustomMessage msg);
}
