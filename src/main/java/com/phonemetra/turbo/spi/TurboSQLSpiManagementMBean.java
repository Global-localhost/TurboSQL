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

package com.phonemetra.turbo.spi;

import java.util.UUID;
import com.phonemetra.turbo.mxbean.MXBeanDescription;

/**
 * This interface defines basic MBean for all SPI implementations. Every SPI implementation
 * should provide implementation for this MBean interface. Note that SPI implementation can extend this
 * interface as necessary.
 */
public interface TurboSQLSpiManagementMBean {
    /**
     * Gets string presentation of the start timestamp.
     *
     * @return String presentation of the start timestamp.
     */
    @MXBeanDescription("String presentation of the start timestamp.")
    public String getStartTimestampFormatted();

    /**
     * Gets string presentation of up-time for this SPI.
     *
     * @return String presentation of up-time for this SPI.
     */
    @MXBeanDescription("String presentation of up-time for this SPI.")
    public String getUpTimeFormatted();

    /**
     * Get start timestamp of this SPI.
     *
     * @return Start timestamp of this SPI.
     */
    @MXBeanDescription("Start timestamp of this SPI.")
    public long getStartTimestamp();

    /**
     * Gets up-time of this SPI in ms.
     *
     * @return Up-time of this SPI.
     */
    @MXBeanDescription("Up-time of this SPI in milliseconds.")
    public long getUpTime();

    /**
     * Gets TurboSQL installation home folder (i.e. ${TURBOSQL_HOME});
     *
     * @return TurboSQL installation home folder.
     */
    @MXBeanDescription("TurboSQL installation home folder.")
    public String getTurboSQLHome();

    /**
     * Gets ID of the local node.
     *
     * @return ID of the local node.
     */
    @MXBeanDescription("ID of the local node.")
    public UUID getLocalNodeId();

    /**
     * Gets name of the SPI.
     *
     * @return Name of the SPI.
     */
    @MXBeanDescription("Name of the SPI.")
    public String getName();
}