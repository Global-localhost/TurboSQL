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

package com.phonemetra.turbo.internal.managers.checkpoint;

import java.io.Externalizable;
import java.nio.ByteBuffer;
import com.phonemetra.turbo.internal.util.tostring.GridToStringInclude;
import com.phonemetra.turbo.internal.util.typedef.internal.S;
import com.phonemetra.turbo.lang.TurboSQLUuid;
import com.phonemetra.turbo.plugin.extensions.communication.Message;
import com.phonemetra.turbo.plugin.extensions.communication.MessageReader;
import com.phonemetra.turbo.plugin.extensions.communication.MessageWriter;

/**
 * This class defines checkpoint request.
 */
public class GridCheckpointRequest implements Message {
    /** */
    private static final long serialVersionUID = 0L;

    /** */
    private TurboSQLUuid sesId;

    /** */
    @GridToStringInclude(sensitive = true)
    private String key;

    /** */
    private String cpSpi;

    /**
     * Empty constructor required by {@link Externalizable}.
     */
    public GridCheckpointRequest() {
        // No-op.
    }

    /**
     * @param sesId Task session ID.
     * @param key Checkpoint key.
     * @param cpSpi Checkpoint SPI.
     */
    public GridCheckpointRequest(TurboSQLUuid sesId, String key, String cpSpi) {
        assert sesId != null;
        assert key != null;

        this.sesId = sesId;
        this.key = key;

        this.cpSpi = cpSpi == null || cpSpi.isEmpty() ? null : cpSpi;
    }

    /**
     * @return Session ID.
     */
    public TurboSQLUuid getSessionId() {
        return sesId;
    }

    /**
     * @return Checkpoint key.
     */
    public String getKey() {
        return key;
    }

    /**
     * @return Checkpoint SPI.
     */
    public String getCheckpointSpi() {
        return cpSpi;
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
                if (!writer.writeString("cpSpi", cpSpi))
                    return false;

                writer.incrementState();

            case 1:
                if (!writer.writeString("key", key))
                    return false;

                writer.incrementState();

            case 2:
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
                cpSpi = reader.readString("cpSpi");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

            case 1:
                key = reader.readString("key");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

            case 2:
                sesId = reader.readTurboSQLUuid("sesId");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

        }

        return reader.afterMessageRead(GridCheckpointRequest.class);
    }

    /** {@inheritDoc} */
    @Override public short directType() {
        return 7;
    }

    /** {@inheritDoc} */
    @Override public byte fieldsCount() {
        return 3;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridCheckpointRequest.class, this);
    }
}
