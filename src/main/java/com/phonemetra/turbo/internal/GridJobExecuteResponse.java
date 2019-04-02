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
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;
import com.phonemetra.turbo.TurboSQLException;
import com.phonemetra.turbo.internal.processors.affinity.AffinityTopologyVersion;
import com.phonemetra.turbo.internal.util.tostring.GridToStringExclude;
import com.phonemetra.turbo.internal.util.typedef.internal.S;
import com.phonemetra.turbo.lang.TurboSQLUuid;
import com.phonemetra.turbo.plugin.extensions.communication.Message;
import com.phonemetra.turbo.plugin.extensions.communication.MessageReader;
import com.phonemetra.turbo.plugin.extensions.communication.MessageWriter;
import org.jetbrains.annotations.Nullable;

/**
 * Job execution response.
 */
public class GridJobExecuteResponse implements Message {
    /** */
    private static final long serialVersionUID = 0L;

    /** */
    private UUID nodeId;

    /** */
    private TurboSQLUuid sesId;

    /** */
    private TurboSQLUuid jobId;

    /** */
    private byte[] gridExBytes;

    /** */
    @GridDirectTransient
    private TurboSQLException gridEx;

    /** */
    private byte[] resBytes;

    /** */
    @GridDirectTransient
    private Object res;

    /** */
    private byte[] jobAttrsBytes;

    /** */
    @GridDirectTransient
    private Map<Object, Object> jobAttrs;

    /** */
    private boolean isCancelled;

    /** */
    @GridToStringExclude
    @GridDirectTransient
    private TurboSQLException fakeEx;

    /** */
    private AffinityTopologyVersion retry;

    /**
     * No-op constructor to support {@link Externalizable} interface. This
     * constructor is not meant to be used for other purposes.
     */
    public GridJobExecuteResponse() {
        // No-op.
    }

    /**
     * @param nodeId Sender node ID.
     * @param sesId Task session ID
     * @param jobId Job ID.
     * @param gridExBytes Serialized grid exception.
     * @param gridEx Grid exception.
     * @param resBytes Serialized result.
     * @param res Result.
     * @param jobAttrsBytes Serialized job attributes.
     * @param jobAttrs Job attributes.
     * @param isCancelled Whether job was cancelled or not.
     * @param retry Topology version for that partitions haven't been reserved on the affinity node.
     */
    public GridJobExecuteResponse(UUID nodeId,
        TurboSQLUuid sesId,
        TurboSQLUuid jobId,
        byte[] gridExBytes,
        TurboSQLException gridEx,
        byte[] resBytes,
        Object res,
        byte[] jobAttrsBytes,
        Map<Object, Object> jobAttrs,
        boolean isCancelled,
        AffinityTopologyVersion retry)
    {
        assert nodeId != null;
        assert sesId != null;
        assert jobId != null;

        this.nodeId = nodeId;
        this.sesId = sesId;
        this.jobId = jobId;
        this.gridExBytes = gridExBytes;
        this.gridEx = gridEx;
        this.resBytes = resBytes;
        this.res = res;
        this.jobAttrsBytes = jobAttrsBytes;
        this.jobAttrs = jobAttrs;
        this.isCancelled = isCancelled;
        this.retry = retry;
    }

    /**
     * @return Task session ID.
     */
    public TurboSQLUuid getSessionId() {
        return sesId;
    }

    /**
     * @return Job ID.
     */
    public TurboSQLUuid getJobId() {
        return jobId;
    }

    /**
     * @return Serialized job result.
     */
    @Nullable public byte[] getJobResultBytes() {
        return resBytes;
    }

    /**
     * @return Job result.
     */
    @Nullable public Object getJobResult() {
        return res;
    }

    /**
     * @return Serialized job exception.
     */
    @Nullable public byte[] getExceptionBytes() {
        return gridExBytes;
    }

    /**
     * @return Job exception.
     */
    @Nullable public TurboSQLException getException() {
        return gridEx;
    }

    /**
     * @return Serialized job attributes.
     */
    @Nullable public byte[] getJobAttributesBytes() {
        return jobAttrsBytes;
    }

    /**
     * @return Job attributes.
     */
    @Nullable public Map<Object, Object> getJobAttributes() {
        return jobAttrs;
    }

    /**
     * @return Job cancellation status.
     */
    public boolean isCancelled() {
        return isCancelled;
    }

    /**
     * @return Sender node ID.
     */
    public UUID getNodeId() {
        return nodeId;
    }

    /**
     * @return Fake exception.
     */
    public TurboSQLException getFakeException() {
        return fakeEx;
    }

    /**
     * @param fakeEx Fake exception.
     */
    public void setFakeException(TurboSQLException fakeEx) {
        this.fakeEx = fakeEx;
    }

    /**
     * @return {@code True} if need retry job.
     */
    public boolean retry() {
        return retry != null;
    }

    /**
     * @return Topology version for that specified partitions haven't been reserved
     *          on the affinity node.
     */
    public AffinityTopologyVersion getRetryTopologyVersion() {
        return retry != null ? retry : AffinityTopologyVersion.NONE;
    }

    /** {@inheritDoc} */
    @Override public void onAckReceived() {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public boolean writeTo(ByteBuffer buf, MessageWriter writer) {
        writer.setBuffer(buf);

        if (!writer.isHeaderWritten()) {
            if (!writer.writeHeader(directType(), fieldsCount()))
                return false;

            writer.onHeaderWritten();
        }

        switch (writer.state()) {
            case 0:
                if (!writer.writeByteArray("gridExBytes", gridExBytes))
                    return false;

                writer.incrementState();

            case 1:
                if (!writer.writeBoolean("isCancelled", isCancelled))
                    return false;

                writer.incrementState();

            case 2:
                if (!writer.writeByteArray("jobAttrsBytes", jobAttrsBytes))
                    return false;

                writer.incrementState();

            case 3:
                if (!writer.writeTurboSQLUuid("jobId", jobId))
                    return false;

                writer.incrementState();

            case 4:
                if (!writer.writeUuid("nodeId", nodeId))
                    return false;

                writer.incrementState();

            case 5:
                if (!writer.writeByteArray("resBytes", resBytes))
                    return false;

                writer.incrementState();

            case 6:
                if (!writer.writeAffinityTopologyVersion("retry", retry))
                    return false;

                writer.incrementState();

            case 7:
                if (!writer.writeTurboSQLUuid("sesId", sesId))
                    return false;

                writer.incrementState();

        }

        return true;
    }

    /** {@inheritDoc} */
    @Override public boolean readFrom(ByteBuffer buf, MessageReader reader) {
        reader.setBuffer(buf);

        if (!reader.beforeMessageRead())
            return false;

        switch (reader.state()) {
            case 0:
                gridExBytes = reader.readByteArray("gridExBytes");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

            case 1:
                isCancelled = reader.readBoolean("isCancelled");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

            case 2:
                jobAttrsBytes = reader.readByteArray("jobAttrsBytes");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

            case 3:
                jobId = reader.readTurboSQLUuid("jobId");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

            case 4:
                nodeId = reader.readUuid("nodeId");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

            case 5:
                resBytes = reader.readByteArray("resBytes");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

            case 6:
                retry = reader.readAffinityTopologyVersion("retry");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

            case 7:
                sesId = reader.readTurboSQLUuid("sesId");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

        }

        return reader.afterMessageRead(GridJobExecuteResponse.class);
    }

    /** {@inheritDoc} */
    @Override public short directType() {
        return 2;
    }

    /** {@inheritDoc} */
    @Override public byte fieldsCount() {
        return 8;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridJobExecuteResponse.class, this);
    }
}
