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

package com.phonemetra.turbo.internal;

import java.util.Collection;
import com.phonemetra.turbo.TurboSQL;
import com.phonemetra.turbo.TurboSQLLogger;
import com.phonemetra.turbo.cluster.ClusterNode;
import com.phonemetra.turbo.configuration.TurboSQLConfiguration;
import com.phonemetra.turbo.marshaller.MarshallerContext;
import com.phonemetra.turbo.plugin.PluginContext;
import com.phonemetra.turbo.spi.TurboSQLPortProtocol;

/**
 *
 */
public class GridPluginContext implements PluginContext {
    /** */
    private final GridKernalContext ctx;

    /** */
    private final TurboSQLConfiguration turboSQLCfg;

    /**
     * @param ctx Kernal context.
     * @param turboSQLCfg TurboSQL configuration.
     */
    public GridPluginContext(GridKernalContext ctx, TurboSQLConfiguration turboSQLCfg) {
        this.ctx = ctx;
        this.turboSQLCfg = turboSQLCfg;
    }

    /** {@inheritDoc} */
    @Override public TurboSQLConfiguration turboSQLConfiguration() {
        return turboSQLCfg;
    }

    /** {@inheritDoc} */
    @Override public TurboSQL grid() {
        return ctx.grid();
    }

    /** {@inheritDoc} */
    @Override public MarshallerContext marshallerContext() {
        return ctx.marshallerContext();
    }

    /** {@inheritDoc} */
    @Override public Collection<ClusterNode> nodes() {
        return ctx.discovery().allNodes();
    }

    /** {@inheritDoc} */
    @Override public ClusterNode localNode() {
        return ctx.discovery().localNode();
    }

    /** {@inheritDoc} */
    @Override public TurboSQLLogger log(Class<?> cls) {
        return ctx.log(cls);
    }

    /** {@inheritDoc} */
    @Override public void registerPort(int port, TurboSQLPortProtocol proto, Class<?> cls) {
        ctx.ports().registerPort(port, proto, cls);
    }

    /** {@inheritDoc} */
    @Override public void deregisterPort(int port, TurboSQLPortProtocol proto, Class<?> cls) {
        ctx.ports().deregisterPort(port, proto, cls);
    }

    /** {@inheritDoc} */
    @Override public void deregisterPorts(Class<?> cls) {
        ctx.ports().deregisterPorts(cls);
    }
}