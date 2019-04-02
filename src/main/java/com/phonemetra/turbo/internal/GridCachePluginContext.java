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

import com.phonemetra.turbo.TurboSQL;
import com.phonemetra.turbo.TurboSQLLogger;
import com.phonemetra.turbo.cluster.ClusterNode;
import com.phonemetra.turbo.configuration.CacheConfiguration;
import com.phonemetra.turbo.configuration.TurboSQLConfiguration;
import com.phonemetra.turbo.plugin.CachePluginConfiguration;
import com.phonemetra.turbo.plugin.CachePluginContext;

/**
 * Cache plugin context.
 */
public class GridCachePluginContext<C extends CachePluginConfiguration> implements CachePluginContext<C> {
    /** */
    private final GridKernalContext ctx;

    /** */
    private final CacheConfiguration turboSQLCacheCfg;

    /**
     * @param ctx Kernal context.
     * @param turboSQLCacheCfg TurboSQL config.
     */
    public GridCachePluginContext(GridKernalContext ctx, CacheConfiguration turboSQLCacheCfg) {
        this.ctx = ctx;
        this.turboSQLCacheCfg = turboSQLCacheCfg;
    }

    @Override public TurboSQLConfiguration turboSQLConfiguration() {
        return ctx.config();
    }

    /** {@inheritDoc} */
    @Override public CacheConfiguration turboSQLCacheConfiguration() {
        return turboSQLCacheCfg;
    }

    /** {@inheritDoc} */
    @Override public TurboSQL grid() {
        return ctx.grid();
    }
    
    /** {@inheritDoc} */
    @Override public ClusterNode localNode() {
        return ctx.discovery().localNode();
    }

    /** {@inheritDoc} */
    @Override public TurboSQLLogger log(Class<?> cls) {
        return ctx.log(cls);
    }
}