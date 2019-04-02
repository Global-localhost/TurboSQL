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

package com.phonemetra.turbo.spi.failover;

import java.util.List;
import com.phonemetra.turbo.cluster.ClusterNode;
import com.phonemetra.turbo.spi.TurboSQLSpi;

/**
 * Failover SPI provides developer with ability to supply custom logic for handling
 * failed execution of a grid job. Job execution can fail for a number of reasons:
 * <ul>
 *      <li>Job execution threw an exception (runtime, assertion or error)</li>
 *      <li>Node on which job was execution left topology (crashed or stopped)</li>
 *      <li>Collision SPI on remote node cancelled a job before it got a chance to execute (job rejection).</li>
 * </ul>
 * In all cases failover SPI takes failed job (as failover context) and list of all
 * grid nodes and provides another node on which the job execution will be retried.
 * It is up to failover SPI to make sure that job is not mapped to the node it
 * failed on. The failed node can be retrieved from
 * {@link com.phonemetra.turbo.compute.ComputeJobResult#getNode() GridFailoverContext.getJobResult().node()}
 * method.
 * <p>
 * TurboSQL comes with the following built-in failover SPI implementations:
 * <ul>
 *      <li>{@link com.phonemetra.turbo.spi.failover.never.NeverFailoverSpi}</li>
 *      <li>{@link com.phonemetra.turbo.spi.failover.always.AlwaysFailoverSpi}</li>
 *      <li>{@link com.phonemetra.turbo.spi.failover.jobstealing.JobStealingFailoverSpi}</li>
 * </ul>
 * <b>NOTE:</b> this SPI (i.e. methods in this interface) should never be used directly. SPIs provide
 * internal view on the subsystem and is used internally by TurboSQL kernal. In rare use cases when
 * access to a specific implementation of this SPI is required - an instance of this SPI can be obtained
 * via {@link com.phonemetra.turbo.TurboSQL#configuration()} method to check its configuration properties or call other non-SPI
 * methods. Note again that calling methods from this interface on the obtained instance can lead
 * to undefined behavior and explicitly not supported.
 */
public interface FailoverSpi extends TurboSQLSpi {
    /**
     * This method is called when method {@link com.phonemetra.turbo.compute.ComputeTask#result(com.phonemetra.turbo.compute.ComputeJobResult, List)} returns
     * value {@link com.phonemetra.turbo.compute.ComputeJobResultPolicy#FAILOVER} policy indicating that the result of
     * job execution must be failed over. Implementation of this method should examine failover
     * context and choose one of the grid nodes from supplied {@code topology} to retry job execution
     * on it. For best performance it is advised that {@link FailoverContext#getBalancedNode(List)}
     * method is used to select node for execution of failed job.
     *
     * @param ctx Failover context.
     * @param top Collection of all grid nodes within task topology (may include failed node).
     * @return New node to route this job to or {@code null} if new node cannot be picked.
     *      If job failover fails (returns {@code null}) the whole task will be failed.
     */
    public ClusterNode failover(FailoverContext ctx, List<ClusterNode> top);
}