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
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.ObjectStreamException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import com.phonemetra.turbo.TurboSQLCheckedException;
import com.phonemetra.turbo.TurboSQLEvents;
import com.phonemetra.turbo.TurboSQLException;
import com.phonemetra.turbo.cluster.ClusterGroup;
import com.phonemetra.turbo.events.Event;
import com.phonemetra.turbo.internal.cluster.ClusterGroupAdapter;
import com.phonemetra.turbo.internal.util.future.TurboSQLFutureImpl;
import com.phonemetra.turbo.internal.util.typedef.F;
import com.phonemetra.turbo.internal.util.typedef.internal.A;
import com.phonemetra.turbo.internal.util.typedef.internal.U;
import com.phonemetra.turbo.lang.TurboSQLBiPredicate;
import com.phonemetra.turbo.lang.TurboSQLFuture;
import com.phonemetra.turbo.lang.TurboSQLPredicate;
import org.jetbrains.annotations.Nullable;

/**
 * {@link TurboSQLEvents} implementation.
 */
public class TurboSQLEventsImpl extends AsyncSupportAdapter<TurboSQLEvents> implements TurboSQLEvents, Externalizable {
    /** */
    private static final long serialVersionUID = 0L;

    /** */
    private GridKernalContext ctx;

    /** */
    private ClusterGroupAdapter prj;

    /**
     * Required by {@link Externalizable}.
     */
    public TurboSQLEventsImpl() {
        // No-op.
    }

    /**
     * @param ctx Kernal context.
     * @param prj Projection.
     * @param async Async support flag.
     */
    public TurboSQLEventsImpl(GridKernalContext ctx, ClusterGroupAdapter prj, boolean async) {
        super(async);

        this.ctx = ctx;
        this.prj = prj;
    }

    /** {@inheritDoc} */
    @Override public ClusterGroup clusterGroup() {
        return prj;
    }

    /** {@inheritDoc} */
    @Override public <T extends Event> List<T> remoteQuery(TurboSQLPredicate<T> p, long timeout, @Nullable int... types) {
        A.notNull(p, "p");

        guard();

        try {
            return saveOrGet(ctx.event().remoteEventsAsync(compoundPredicate(p, types), prj.nodes(), timeout));
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <T extends Event> TurboSQLFuture<List<T>> remoteQueryAsync(TurboSQLPredicate<T> p, long timeout,
        @Nullable int... types) throws TurboSQLException {

        guard();

        try {
            return new TurboSQLFutureImpl<>(ctx.event().remoteEventsAsync(compoundPredicate(p, types),
                prj.nodes(), timeout));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <T extends Event> UUID remoteListen(@Nullable TurboSQLBiPredicate<UUID, T> locLsnr,
        @Nullable TurboSQLPredicate<T> rmtFilter, @Nullable int... types) {
        return remoteListen(1, 0, true, locLsnr, rmtFilter, types);
    }

    /** {@inheritDoc} */
    @Override public <T extends Event> TurboSQLFuture<UUID> remoteListenAsync(
        @Nullable TurboSQLBiPredicate<UUID, T> locLsnr, @Nullable TurboSQLPredicate<T> rmtFilter,
        @Nullable int... types) throws TurboSQLException {
        return remoteListenAsync(1, 0, true, locLsnr, rmtFilter, types);
    }

    /** {@inheritDoc} */
    @Override public <T extends Event> UUID remoteListen(int bufSize, long interval,
        boolean autoUnsubscribe, @Nullable TurboSQLBiPredicate<UUID, T> locLsnr, @Nullable TurboSQLPredicate<T> rmtFilter,
        @Nullable int... types) {
        A.ensure(bufSize > 0, "bufSize > 0");
        A.ensure(interval >= 0, "interval >= 0");

        guard();

        try {
            GridEventConsumeHandler hnd = new GridEventConsumeHandler((TurboSQLBiPredicate<UUID, Event>)locLsnr,
                (TurboSQLPredicate<Event>)rmtFilter, types);

            return saveOrGet(ctx.continuous().startRoutine(
                hnd,
                false,
                bufSize,
                interval,
                autoUnsubscribe,
                prj.predicate()));
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <T extends Event> TurboSQLFuture<UUID> remoteListenAsync(int bufSize, long interval,
        boolean autoUnsubscribe, @Nullable TurboSQLBiPredicate<UUID, T> locLsnr, @Nullable TurboSQLPredicate<T> rmtFilter,
        @Nullable int... types) throws TurboSQLException {
        A.ensure(bufSize > 0, "bufSize > 0");
        A.ensure(interval >= 0, "interval >= 0");

        guard();

        try {
            GridEventConsumeHandler hnd = new GridEventConsumeHandler((TurboSQLBiPredicate<UUID, Event>)locLsnr,
                (TurboSQLPredicate<Event>)rmtFilter, types);

            return new TurboSQLFutureImpl<>(ctx.continuous().startRoutine(
                hnd,
                false,
                bufSize,
                interval,
                autoUnsubscribe,
                prj.predicate()));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public void stopRemoteListen(UUID opId) {
        A.notNull(opId, "consumeId");

        guard();

        try {
            saveOrGet(ctx.continuous().stopRoutine(opId));
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public TurboSQLFuture<Void> stopRemoteListenAsync(UUID opId) throws TurboSQLException {
        A.notNull(opId, "consumeId");

        guard();

        try {
            return (TurboSQLFuture<Void>)new TurboSQLFutureImpl<>(ctx.continuous().stopRoutine(opId));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <T extends Event> T waitForLocal(@Nullable TurboSQLPredicate<T> filter,
        @Nullable int... types) {
        guard();

        try {
            return saveOrGet(ctx.event().waitForEvent(filter, types));
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <T extends Event> TurboSQLFuture<T> waitForLocalAsync(@Nullable TurboSQLPredicate<T> filter,
        @Nullable int... types) throws TurboSQLException {
        guard();

        try {
            return new TurboSQLFutureImpl<>(ctx.event().waitForEvent(filter, types));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public <T extends Event> Collection<T> localQuery(TurboSQLPredicate<T> p, @Nullable int... types) {
        A.notNull(p, "p");

        guard();

        try {
            return ctx.event().localEvents(compoundPredicate(p, types));
        }
        catch (TurboSQLCheckedException e) {
            throw U.convertException(e);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public void recordLocal(Event evt) {
        A.notNull(evt, "evt");

        if (evt.type() <= 1000)
            throw new IllegalArgumentException("All types in range from 1 to 1000 are reserved for " +
                "internal TurboSQL events [evtType=" + evt.type() + ", evt=" + evt + ']');

        guard();

        try {
            ctx.event().record(evt);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public void localListen(TurboSQLPredicate<? extends Event> lsnr, int[] types) {
        A.notNull(lsnr, "lsnr");
        A.notEmpty(types, "types");

        guard();

        try {
            ctx.event().addLocalEventListener(lsnr, types);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public boolean stopLocalListen(TurboSQLPredicate<? extends Event> lsnr, @Nullable int... types) {
        A.notNull(lsnr, "lsnr");

        guard();

        try {
            return ctx.event().removeLocalEventListener(lsnr, types);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public void enableLocal(int[] types) {
        A.notEmpty(types, "types");

        guard();

        try {
            ctx.event().enableEvents(types);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public void disableLocal(int[] types) {
        A.notEmpty(types, "types");

        guard();

        try {
            ctx.event().disableEvents(types);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public int[] enabledEvents() {
        return ctx.event().enabledEvents();
    }

    /** {@inheritDoc} */
    @Override public boolean isEnabled(int type) {
        if (type < 0)
            throw new IllegalArgumentException("Invalid event type: " + type);

        return ctx.event().isUserRecordable(type);
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

    /**
     * @param p Predicate.
     * @param types Event types.
     * @return Compound predicate.
     */
    private static <T extends Event> TurboSQLPredicate<T> compoundPredicate(final TurboSQLPredicate<T> p,
        @Nullable final int... types) {

        return F.isEmpty(types) ? p :
            new TurboSQLPredicate<T>() {
                @Override public boolean apply(T t) {
                    for (int type : types) {
                        if (type == t.type())
                            return p.apply(t);
                    }

                    return false;
                }
            };
    }

    /** {@inheritDoc} */
    @Override protected TurboSQLEvents createAsyncInstance() {
        return new TurboSQLEventsImpl(ctx, prj, true);
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(prj);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        prj = (ClusterGroupAdapter)in.readObject();
    }

    /**
     * Reconstructs object on unmarshalling.
     *
     * @return Reconstructed object.
     * @throws ObjectStreamException Thrown in case of unmarshalling error.
     */
    protected Object readResolve() throws ObjectStreamException {
        return prj.events();
    }
}
