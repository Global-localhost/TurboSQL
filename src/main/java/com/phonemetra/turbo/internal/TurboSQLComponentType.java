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

import java.lang.reflect.Constructor;
import com.phonemetra.turbo.TurboSQLCheckedException;
import com.phonemetra.turbo.internal.processors.compress.CompressionProcessor;
import com.phonemetra.turbo.internal.util.typedef.internal.U;
import com.phonemetra.turbo.plugin.extensions.communication.MessageFactory;
import org.jetbrains.annotations.Nullable;

/**
 * Component type.
 */
public enum TurboSQLComponentType {
    /** IGFS. */
    IGFS(
        "com.phonemetra.turbo.internal.processors.igfs.IgfsNoopProcessor",
        "com.phonemetra.turbo.internal.processors.igfs.IgfsProcessor",
        "turboSQL-hadoop"
    ),

    /** Hadoop. */
    HADOOP(
        "com.phonemetra.turbo.internal.processors.hadoop.HadoopNoopProcessor",
        "com.phonemetra.turbo.internal.processors.hadoop.HadoopProcessor",
        "turboSQL-hadoop"
    ),

    /** Hadoop Helper component. */
    HADOOP_HELPER(
        "com.phonemetra.turbo.internal.processors.hadoop.HadoopNoopHelper",
        "com.phonemetra.turbo.internal.processors.hadoop.HadoopHelperImpl",
        "turboSQL-hadoop"
    ),

    /** IGFS helper component. */
    IGFS_HELPER(
        "com.phonemetra.turbo.internal.processors.igfs.IgfsNoopHelper",
        "com.phonemetra.turbo.internal.processors.igfs.IgfsHelperImpl",
        "turboSQL-hadoop"
    ),

    /** Spring XML parsing. */
    SPRING(
        null,
        "com.phonemetra.turbo.internal.util.spring.TurboSQLSpringHelperImpl",
        "turboSQL-spring"
    ),

    /** Indexing. */
    INDEXING(
        null,
        "com.phonemetra.turbo.internal.processors.query.h2.TurboSQLH2Indexing",
        "turboSQL-indexing",
        "com.phonemetra.turbo.internal.processors.query.h2.twostep.msg.GridH2ValueMessageFactory"
    ),

    /** Nodes starting using SSH. */
    SSH(
        null,
        "com.phonemetra.turbo.internal.util.nodestart.TurboSQLSshHelperImpl",
        "turboSQL-ssh"
    ),

    /** Integration of cache transactions with JTA. */
    JTA(
        "com.phonemetra.turbo.internal.processors.cache.jta.CacheNoopJtaManager",
        "com.phonemetra.turbo.internal.processors.cache.jta.CacheJtaManager",
        "turboSQL-jta"
    ),

    /** Cron-based scheduling, see {@link com.phonemetra.turbo.TurboSQLScheduler}. */
    SCHEDULE(
        "com.phonemetra.turbo.internal.processors.schedule.TurboSQLNoopScheduleProcessor",
        "com.phonemetra.turbo.internal.processors.schedule.TurboSQLScheduleProcessor",
        "turboSQL-schedule"
    ),

    COMPRESSION(
        CompressionProcessor.class.getName(),
        "com.phonemetra.turbo.internal.processors.compress.CompressionProcessorImpl",
        "turboSQL-compress"
    );

    /** No-op class name. */
    private final String noOpClsName;

    /** Class name. */
    private final String clsName;

    /** Module name. */
    private final String module;

    /** Optional message factory for component. */
    private final String msgFactoryCls;

    /**
     * Constructor.
     *
     * @param noOpClsName Class name for no-op implementation.
     * @param clsName Class name.
     * @param module Module name.
     */
    TurboSQLComponentType(String noOpClsName, String clsName, String module) {
        this(noOpClsName, clsName, module, null);
    }

    /**
     * Constructor.
     *
     * @param noOpClsName Class name for no-op implementation.
     * @param clsName Class name.
     * @param module Module name.
     * @param msgFactoryCls {@link MessageFactory} class for the component.
     */
    TurboSQLComponentType(String noOpClsName, String clsName, String module, String msgFactoryCls) {
        this.noOpClsName = noOpClsName;
        this.clsName = clsName;
        this.module = module;
        this.msgFactoryCls = msgFactoryCls;
    }

    /**
     * @return Component class name.
     */
    public String className() {
        return clsName;
    }

    /**
     * @return Component module name.
     */
    public String module() {
        return module;
    }

    /**
     * Check whether real component class is in classpath.
     *
     * @return {@code True} if in classpath.
     */
    public boolean inClassPath() {
        try {
            Class.forName(clsName);

            return true;
        }
        catch (ClassNotFoundException ignore) {
            return false;
        }
    }

    /**
     * Creates component.
     *
     * @param ctx Kernal context.
     * @param noOp No-op flag.
     * @return Created component.
     * @throws TurboSQLCheckedException If failed.
     */
    public <T> T create(GridKernalContext ctx, boolean noOp) throws TurboSQLCheckedException {
        return create0(ctx, noOp ? noOpClsName : clsName);
    }

    /**
     * Creates component.
     *
     * @param ctx Kernal context.
     * @param mandatory If the component is mandatory.
     * @return Created component.
     * @throws TurboSQLCheckedException If failed.
     */
    public <T> T createIfInClassPath(GridKernalContext ctx, boolean mandatory)
        throws TurboSQLCheckedException {
        String cls = clsName;

        try {
            Class.forName(cls);
        }
        catch (ClassNotFoundException e) {
            if (mandatory)
                throw componentException(e);

            cls = noOpClsName;
        }

        return create0(ctx, cls);
    }

    /**
     * Creates component.
     *
     * @param noOp No-op flag.
     * @return Created component.
     * @throws TurboSQLCheckedException If failed.
     */
    public <T> T create(boolean noOp) throws TurboSQLCheckedException {
        return create0(null, noOp ? noOpClsName : clsName);
    }

    /**
     * First tries to find main component class, if it is not found creates no-op implementation.
     *
     * @param ctx Kernal context.
     * @return Created component or no-op implementation.
     * @throws TurboSQLCheckedException If failed.
     */
    public <T> T createOptional(GridKernalContext ctx) throws TurboSQLCheckedException {
        return createOptional0(ctx);
    }

    /**
     * First tries to find main component class, if it is not found creates no-op implementation.
     *
     * @return Created component or no-op implementation.
     * @throws TurboSQLCheckedException If failed.
     */
    public <T> T createOptional() throws TurboSQLCheckedException {
        return createOptional0(null);
    }

    /**
     * First tries to find main component class, if it is not found creates no-op implementation.
     *
     * @param ctx Kernal context.
     * @return Created component or no-op implementation.
     * @throws TurboSQLCheckedException If failed.
     */
    private <T> T createOptional0(@Nullable GridKernalContext ctx) throws TurboSQLCheckedException {
        Class<?> cls;

        try {
            cls = Class.forName(clsName);
        }
        catch (ClassNotFoundException ignored) {
            try {
                cls = Class.forName(noOpClsName);
            }
            catch (ClassNotFoundException e) {
                throw new TurboSQLCheckedException("Failed to find both real component class and no-op class.", e);
            }
        }

        try {
            if (ctx == null) {
                Constructor<?> ctor = cls.getConstructor();

                return (T)ctor.newInstance();
            }
            else {
                Constructor<?> ctor = cls.getConstructor(GridKernalContext.class);

                return (T)ctor.newInstance(ctx);
            }
        }
        catch (Exception e) {
            throw componentException(e);
        }
    }

    /**
     * Creates component instance.
     *
     * @param ctx Kernal context.
     * @param clsName Component class name.
     * @return Component instance.
     * @throws TurboSQLCheckedException If failed.
     */
    private <T> T create0(@Nullable GridKernalContext ctx, String clsName) throws TurboSQLCheckedException {
        try {
            Class<?> cls = Class.forName(clsName);

            if (ctx == null) {
                Constructor<?> ctor = cls.getConstructor();

                return (T)ctor.newInstance();
            }
            else {
                Constructor<?> ctor = cls.getConstructor(GridKernalContext.class);

                return (T)ctor.newInstance(ctx);
            }
        }
        catch (Throwable e) {
            throw componentException(e);
        }
    }

    /**
     * Creates message factory for the component.
     *
     * @return Message factory or {@code null} if none or the component is not in classpath.
     * @throws TurboSQLCheckedException If failed.
     */
    @Nullable public MessageFactory messageFactory() throws TurboSQLCheckedException {
        Class<?> cls;

        if (msgFactoryCls == null || null == (cls = U.classForName(msgFactoryCls, null)))
            return null;

        return (MessageFactory)U.newInstance(cls);
    }

    /**
     * @param err Creation error.
     * @return Component creation exception.
     */
    private TurboSQLCheckedException componentException(Throwable err) {
        return new TurboSQLCheckedException("Failed to create TurboSQL component (consider adding " + module +
            " module to classpath) [component=" + this + ", cls=" + clsName + ']', err);
    }
}