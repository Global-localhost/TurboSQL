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

import java.io.Closeable;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.ObjectStreamException;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import com.phonemetra.turbo.TurboSQLScheduler;
import com.phonemetra.turbo.internal.util.future.TurboSQLFutureImpl;
import com.phonemetra.turbo.internal.util.typedef.internal.A;
import com.phonemetra.turbo.lang.TurboSQLFuture;
import com.phonemetra.turbo.scheduler.SchedulerFuture;
import org.jetbrains.annotations.Nullable;

/**
 * {@link TurboSQLScheduler} implementation.
 */
public class TurboSQLSchedulerImpl implements TurboSQLScheduler, Externalizable {
    /** */
    private static final long serialVersionUID = 0L;

    /** */
    private GridKernalContext ctx;

    /**
     * Required by {@link Externalizable}.
     */
    public TurboSQLSchedulerImpl() {
        // No-op.
    }

    /**
     * @param ctx Kernal context.
     */
    public TurboSQLSchedulerImpl(GridKernalContext ctx) {
        this.ctx = ctx;
    }

    /** {@inheritDoc} */
    @Override public TurboSQLFuture<?> runLocal(Runnable r) {
        A.notNull(r, "r");

        guard();

        try {
            return new TurboSQLFutureImpl<>(ctx.closure().runLocalSafe(r, false));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public Closeable runLocal(@Nullable Runnable r, long delay, TimeUnit timeUnit) {
        A.notNull(r, "r");
        A.ensure(delay > 0, "Illegal delay");

        guard();

        try {
            return ctx.timeout().schedule(r, timeUnit.toMillis(delay), -1);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <R> TurboSQLFuture<R> callLocal(Callable<R> c) {
        A.notNull(c, "c");

        guard();

        try {
            return new TurboSQLFutureImpl<>(ctx.closure().callLocalSafe(c, false));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public SchedulerFuture<?> scheduleLocal(Runnable job, String ptrn) {
        A.notNull(job, "job");

        guard();

        try {
            return ctx.schedule().schedule(job, ptrn);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <R> SchedulerFuture<R> scheduleLocal(Callable<R> job, String ptrn) {
        A.notNull(job, "job");

        guard();

        try {
            return ctx.schedule().schedule(job, ptrn);
        }
        finally {
            unguard();
        }
    }

    /**
     * <tt>ctx.gateway().readLock()</tt>
     */
    private void guard() {
        ctx.gateway().readLock();
    }

    /**
     * <tt>ctx.gateway().readUnlock()</tt>
     */
    private void unguard() {
        ctx.gateway().readUnlock();
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(ctx);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        ctx = (GridKernalContext)in.readObject();
    }

    /**
     * Reconstructs object on unmarshalling.
     *
     * @return Reconstructed object.
     * @throws ObjectStreamException Thrown in case of unmarshalling error.
     */
    private Object readResolve() throws ObjectStreamException {
        return ctx.grid().scheduler();
    }
}