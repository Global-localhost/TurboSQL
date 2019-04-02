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

package com.phonemetra.turbo.compute.gridify.aop;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import com.phonemetra.turbo.TurboSQL;
import com.phonemetra.turbo.cluster.ClusterNode;
import com.phonemetra.turbo.compute.ComputeJob;
import com.phonemetra.turbo.compute.ComputeJobResult;
import com.phonemetra.turbo.compute.ComputeLoadBalancer;
import com.phonemetra.turbo.compute.ComputeTaskAdapter;
import com.phonemetra.turbo.compute.gridify.GridifyArgument;
import com.phonemetra.turbo.internal.util.gridify.GridifyJobAdapter;
import com.phonemetra.turbo.internal.util.lang.GridPeerDeployAware;
import com.phonemetra.turbo.internal.util.typedef.internal.U;
import com.phonemetra.turbo.resources.TurboSQLInstanceResource;
import com.phonemetra.turbo.resources.LoadBalancerResource;

/**
 * Default gridify task which simply executes a method on remote node.
 * <p>
 * See {@link com.phonemetra.turbo.compute.gridify.Gridify} documentation for more information about execution of
 * {@code gridified} methods.
 * @see com.phonemetra.turbo.compute.gridify.Gridify
 */
public class GridifyDefaultTask extends ComputeTaskAdapter<GridifyArgument, Object>
    implements GridPeerDeployAware {
    /** */
    private static final long serialVersionUID = 0L;

    /** Deploy class. */
    @SuppressWarnings({"TransientFieldNotInitialized"})
    private final transient Class<?> p2pCls;

    /** Class loader. */
    @SuppressWarnings({"TransientFieldNotInitialized"})
    private final transient ClassLoader clsLdr;

    /** Grid instance. */
    @TurboSQLInstanceResource
    private TurboSQL turboSQL;

    /** Load balancer. */
    @LoadBalancerResource
    private ComputeLoadBalancer balancer;

    /**
     * Creates gridify default task with given deployment class.
     *
     * @param cls Deployment class for peer-deployment.
     */
    public GridifyDefaultTask(Class<?> cls) {
        assert cls != null;

        p2pCls = cls;

        clsLdr = U.detectClassLoader(cls);
    }

    /** {@inheritDoc} */
    @Override public Class<?> deployClass() {
        return p2pCls;
    }

    /** {@inheritDoc} */
    @Override public ClassLoader classLoader() {
        return clsLdr;
    }

    /** {@inheritDoc} */
    @Override public Map<? extends ComputeJob, ClusterNode> map(List<ClusterNode> subgrid, GridifyArgument arg) {
        assert !subgrid.isEmpty() : "Subgrid should not be empty: " + subgrid;

        assert turboSQL != null : "Grid instance could not be injected";
        assert balancer != null : "Load balancer could not be injected";

        ComputeJob job = new GridifyJobAdapter(arg);

        ClusterNode node = balancer.getBalancedNode(job, Collections.<ClusterNode>singletonList(turboSQL.cluster().localNode()));

        if (node != null) {
            // Give preference to remote nodes.
            return Collections.singletonMap(job, node);
        }

        return Collections.singletonMap(job, balancer.getBalancedNode(job, null));
    }

    /** {@inheritDoc} */
    @Override public final Object reduce(List<ComputeJobResult> results) {
        assert results.size() == 1;

        ComputeJobResult res = results.get(0);

        if (res.getException() != null)
            throw res.getException();

        return res.getData();
    }
}