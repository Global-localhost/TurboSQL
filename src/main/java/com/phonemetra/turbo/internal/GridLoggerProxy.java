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

import java.io.Externalizable;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.ObjectStreamException;
import java.util.Collections;
import com.phonemetra.turbo.TurboSQLLogger;
import com.phonemetra.turbo.internal.util.tostring.GridToStringExclude;
import com.phonemetra.turbo.internal.util.typedef.internal.S;
import com.phonemetra.turbo.internal.util.typedef.internal.U;
import com.phonemetra.turbo.lang.TurboSQLBiTuple;
import com.phonemetra.turbo.lifecycle.LifecycleAware;
import org.jetbrains.annotations.Nullable;

import static com.phonemetra.turbo.TurboSQLSystemProperties.IGNITE_LOG_GRID_NAME;
import static com.phonemetra.turbo.TurboSQLSystemProperties.IGNITE_LOG_INSTANCE_NAME;

/**
 *
 */
public class GridLoggerProxy implements TurboSQLLogger, LifecycleAware, Externalizable {
    /** */
    private static final long serialVersionUID = 0L;

    /** */
    private static ThreadLocal<TurboSQLBiTuple<String, Object>> stash = new ThreadLocal<TurboSQLBiTuple<String, Object>>() {
        @Override protected TurboSQLBiTuple<String, Object> initialValue() {
            return new TurboSQLBiTuple<>();
        }
    };

    /** */
    private TurboSQLLogger impl;

    /** */
    private String turboSQLInstanceName;

    /** */
    private String id8;

    /** */
    @GridToStringExclude
    private Object ctgr;

    /** Whether or not to log TurboSQL instance name. */
    private static final boolean logTurboSQLInstanceName = System.getProperty(IGNITE_LOG_INSTANCE_NAME) != null ||
        System.getProperty(IGNITE_LOG_GRID_NAME) != null;

    /**
     * No-arg constructor is required by externalization.
     */
    public GridLoggerProxy() {
        // No-op.
    }

    /**
     *
     * @param impl Logger implementation to proxy to.
     * @param ctgr Optional logger category.
     * @param turboSQLInstanceName TurboSQL instance name (can be {@code null} for default grid).
     * @param id8 Node ID.
     */
    public GridLoggerProxy(TurboSQLLogger impl, @Nullable Object ctgr, @Nullable String turboSQLInstanceName, String id8) {
        assert impl != null;

        this.impl = impl;
        this.ctgr = ctgr;
        this.turboSQLInstanceName = turboSQLInstanceName;
        this.id8 = id8;
    }

    /** {@inheritDoc} */
    @Override public void start() {
        if (impl instanceof LifecycleAware)
            ((LifecycleAware)impl).start();
    }

    /** {@inheritDoc} */
    @Override public void stop() {
        U.stopLifecycleAware(this, Collections.singleton(impl));
    }

    /** {@inheritDoc} */
    @Override public TurboSQLLogger getLogger(Object ctgr) {
        assert ctgr != null;

        return new GridLoggerProxy(impl.getLogger(ctgr), ctgr, turboSQLInstanceName, id8);
    }

    /** {@inheritDoc} */
    @Nullable @Override public String fileName() {
        return impl.fileName();
    }

    /** {@inheritDoc} */
    @Override public void trace(String msg) {
        impl.trace(enrich(msg));
    }

    /** {@inheritDoc} */
    @Override public void trace(@Nullable String marker, String msg) {
        impl.trace(marker, enrich(msg));
    }

    /** {@inheritDoc} */
    @Override public void debug(String msg) {
        impl.debug(enrich(msg));
    }

    /** {@inheritDoc} */
    @Override public void debug(@Nullable String marker, String msg) {
        impl.debug(marker, enrich(msg));
    }

    /** {@inheritDoc} */
    @Override public void info(String msg) {
        impl.info(enrich(msg));
    }

    /** {@inheritDoc} */
    @Override public void info(@Nullable String marker, String msg) {
        impl.info(marker, enrich(msg));
    }

    /** {@inheritDoc} */
    @Override public void warning(String msg) {
        impl.warning(enrich(msg));
    }

    /** {@inheritDoc} */
    @Override public void warning(String msg, Throwable e) {
        impl.warning(enrich(msg), e);
    }

    /** {@inheritDoc} */
    @Override public void warning(@Nullable String marker, String msg, @Nullable Throwable e) {
        impl.warning(marker, enrich(msg), e);
    }

    /** {@inheritDoc} */
    @Override public void error(String msg) {
        impl.error(enrich(msg));
    }

    /** {@inheritDoc} */
    @Override public void error(String msg, Throwable e) {
        impl.error(enrich(msg), e);
    }

    /** {@inheritDoc} */
    @Override public void error(@Nullable String marker, String msg, @Nullable Throwable e) {
        impl.error(marker, enrich(msg), e);
    }

    /** {@inheritDoc} */
    @Override public boolean isTraceEnabled() {
        return impl.isTraceEnabled();
    }

    /** {@inheritDoc} */
    @Override public boolean isDebugEnabled() {
        return impl.isDebugEnabled();
    }

    /** {@inheritDoc} */
    @Override public boolean isInfoEnabled() {
        return impl.isInfoEnabled();
    }

    /** {@inheritDoc} */
    @Override public boolean isQuiet() {
        return impl.isQuiet();
    }

    /**
     * Gets the class name and parameters of the Logger type used.
     *
     * @return Logger information (name and parameters)
     */
    public String getLoggerInfo() {
        return impl.toString();
    }

    /**
     * Enriches the log message with TurboSQL instance name if
     * {@link com.phonemetra.turbo.TurboSQLSystemProperties#IGNITE_LOG_INSTANCE_NAME} or
     * {@link com.phonemetra.turbo.TurboSQLSystemProperties#IGNITE_LOG_GRID_NAME} system property is set.
     *
     * @param m Message to enrich.
     * @return Enriched message or the original one.
     */
    private String enrich(@Nullable String m) {
        return logTurboSQLInstanceName && m != null ? "<" + turboSQLInstanceName + '-' + id8 + "> " + m : m;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        U.writeString(out, turboSQLInstanceName);
        out.writeObject(ctgr);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        TurboSQLBiTuple<String, Object> t = stash.get();

        t.set1(U.readString(in));
        t.set2(in.readObject());
    }

    /**
     * Reconstructs object on unmarshalling.
     *
     * @return Reconstructed object.
     * @throws ObjectStreamException Thrown in case of unmarshalling error.
     */
    protected Object readResolve() throws ObjectStreamException {
        try {
            TurboSQLBiTuple<String, Object> t = stash.get();

            Object ctgrR = t.get2();

            TurboSQLLogger log = IgnitionEx.localTurboSQL().log();

            return ctgrR != null ? log.getLogger(ctgrR) : log;
        }
        catch (IllegalStateException e) {
            throw U.withCause(new InvalidObjectException(e.getMessage()), e);
        }
        finally {
            stash.remove();
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridLoggerProxy.class, this);
    }
}
