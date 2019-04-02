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
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import com.phonemetra.turbo.TurboSQLCheckedException;
import com.phonemetra.turbo.internal.managers.deployment.GridDeployment;
import com.phonemetra.turbo.internal.managers.deployment.GridDeploymentInfoBean;
import com.phonemetra.turbo.internal.processors.affinity.AffinityTopologyVersion;
import com.phonemetra.turbo.internal.processors.continuous.GridContinuousBatch;
import com.phonemetra.turbo.internal.processors.continuous.GridContinuousBatchAdapter;
import com.phonemetra.turbo.internal.processors.continuous.GridContinuousHandler;
import com.phonemetra.turbo.internal.util.lang.GridPeerDeployAware;
import com.phonemetra.turbo.internal.util.typedef.T2;
import com.phonemetra.turbo.internal.util.typedef.internal.S;
import com.phonemetra.turbo.internal.util.typedef.internal.U;
import com.phonemetra.turbo.lang.TurboSQLBiPredicate;
import org.jetbrains.annotations.Nullable;

/**
 * Continuous handler for message subscription.
 */
public class GridMessageListenHandler implements GridContinuousHandler {
    /** */
    private static final long serialVersionUID = 0L;

    /** */
    private Object topic;

    /** */
    private TurboSQLBiPredicate<UUID, Object> pred;

    /** */
    private byte[] topicBytes;

    /** */
    private byte[] predBytes;

    /** */
    private String clsName;

    /** */
    private GridDeploymentInfoBean depInfo;

    /** */
    private boolean depEnabled;

    /**
     * Required by {@link Externalizable}.
     */
    public GridMessageListenHandler() {
        // No-op.
    }

    /**
     * @param topic Topic.
     * @param pred Predicate.
     */
    public GridMessageListenHandler(@Nullable Object topic, TurboSQLBiPredicate<UUID, Object> pred) {
        assert pred != null;

        this.topic = topic;
        this.pred = pred;
    }

    /**
     *
     * @param orig Handler to be copied.
     */
    public GridMessageListenHandler(GridMessageListenHandler orig) {
        assert orig != null;

        this.clsName = orig.clsName;
        this.depInfo = orig.depInfo;
        this.pred = orig.pred;
        this.predBytes = orig.predBytes;
        this.topic = orig.topic;
        this.topicBytes = orig.topicBytes;
        this.depEnabled = false;
    }

    /** {@inheritDoc} */
    @Override public boolean isEvents() {
        return false;
    }

    /** {@inheritDoc} */
    @Override public boolean isMessaging() {
        return true;
    }

    /** {@inheritDoc} */
    @Override public boolean isQuery() {
        return false;
    }

    /** {@inheritDoc} */
    @Override public boolean keepBinary() {
        return false;
    }

    /** {@inheritDoc} */
    @Override public String cacheName() {
        throw new IllegalStateException();
    }

    /** {@inheritDoc} */
    @Override public void updateCounters(AffinityTopologyVersion topVer, Map<UUID, Map<Integer, T2<Long, Long>>> cntrsPerNode,
        Map<Integer, T2<Long, Long>> cntrs) {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public Map<Integer, T2<Long, Long>> updateCounters() {
        return Collections.emptyMap();
    }

    /** {@inheritDoc} */
    @Override public RegisterStatus register(UUID nodeId, UUID routineId, final GridKernalContext ctx)
        throws TurboSQLCheckedException {
        ctx.io().addUserMessageListener(topic, pred);

        return RegisterStatus.REGISTERED;
    }

    /** {@inheritDoc} */
    @Override public void unregister(UUID routineId, GridKernalContext ctx) {
        ctx.io().removeUserMessageListener(topic, pred);
    }

    /** {@inheritDoc} */
    @Override public void notifyCallback(UUID nodeId, UUID routineId, Collection<?> objs, GridKernalContext ctx) {
        assert false;
    }

    /** {@inheritDoc} */
    @Override public void p2pMarshal(GridKernalContext ctx) throws TurboSQLCheckedException {
        assert ctx != null;
        assert ctx.config().isPeerClassLoadingEnabled();

        if (topic != null)
            topicBytes = U.marshal(ctx.config().getMarshaller(), topic);

        predBytes = U.marshal(ctx.config().getMarshaller(), pred);

        // Deploy only listener, as it is very likely to be of some user class.
        GridPeerDeployAware pda = U.peerDeployAware(pred);

        clsName = pda.deployClass().getName();

        GridDeployment dep = ctx.deploy().deploy(pda.deployClass(), pda.classLoader());

        if (dep == null)
            throw new TurboSQLDeploymentCheckedException("Failed to deploy message listener.");

        depInfo = new GridDeploymentInfoBean(dep);

        depEnabled = true;
    }

    /** {@inheritDoc} */
    @Override public void p2pUnmarshal(UUID nodeId, GridKernalContext ctx) throws TurboSQLCheckedException {
        assert nodeId != null;
        assert ctx != null;
        assert ctx.config().isPeerClassLoadingEnabled();

        GridDeployment dep = ctx.deploy().getGlobalDeployment(depInfo.deployMode(), clsName, clsName,
            depInfo.userVersion(), nodeId, depInfo.classLoaderId(), depInfo.participants(), null);

        if (dep == null)
            throw new TurboSQLDeploymentCheckedException("Failed to obtain deployment for class: " + clsName);

        ClassLoader ldr = dep.classLoader();

        if (topicBytes != null)
            topic = U.unmarshal(ctx, topicBytes, U.resolveClassLoader(ldr, ctx.config()));

        pred = U.unmarshal(ctx, predBytes, U.resolveClassLoader(ldr, ctx.config()));
    }

    /** {@inheritDoc} */
    @Override public GridContinuousBatch createBatch() {
        return new GridContinuousBatchAdapter();
    }

    /** {@inheritDoc} */
    @Override public void onClientDisconnected() {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public void onBatchAcknowledged(UUID routineId, GridContinuousBatch batch, GridKernalContext ctx) {
        // No-op.
    }

    /** {@inheritDoc} */
    @Nullable @Override public Object orderedTopic() {
        return null;
    }

    /** {@inheritDoc} */
    @Override public GridContinuousHandler clone() {
        try {
            return (GridContinuousHandler)super.clone();
        }
        catch (CloneNotSupportedException e) {
            throw new IllegalStateException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public void onNodeLeft() {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        out.writeBoolean(depEnabled);

        if (depEnabled) {
            U.writeByteArray(out, topicBytes);
            U.writeByteArray(out, predBytes);
            U.writeString(out, clsName);
            out.writeObject(depInfo);
        }
        else {
            out.writeObject(topic);
            out.writeObject(pred);
        }
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        depEnabled = in.readBoolean();

        if (depEnabled) {
            topicBytes = U.readByteArray(in);
            predBytes = U.readByteArray(in);
            clsName = U.readString(in);
            depInfo = (GridDeploymentInfoBean)in.readObject();
        }
        else {
            topic = in.readObject();
            pred = (TurboSQLBiPredicate<UUID, Object>)in.readObject();
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridMessageListenHandler.class, this);
    }
}