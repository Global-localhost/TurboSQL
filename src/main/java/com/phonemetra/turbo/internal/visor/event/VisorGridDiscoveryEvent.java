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

package com.phonemetra.turbo.internal.visor.event;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.UUID;
import com.phonemetra.turbo.internal.util.typedef.internal.S;
import com.phonemetra.turbo.internal.util.typedef.internal.U;
import com.phonemetra.turbo.internal.visor.VisorDataTransferObjectInput;
import com.phonemetra.turbo.internal.visor.VisorDataTransferObjectOutput;
import com.phonemetra.turbo.lang.TurboSQLUuid;
import org.jetbrains.annotations.Nullable;

/**
 * Lightweight counterpart for {@link com.phonemetra.turbo.events.DiscoveryEvent}.
 */
public class VisorGridDiscoveryEvent extends VisorGridEvent {
    /** */
    private static final long serialVersionUID = 0L;

    /** Node that caused this event to be generated. */
    private UUID evtNodeId;

    /** Node address that caused this event to be generated. */
    private String addr;

    /** If node that caused this event is daemon. */
    private boolean isDaemon;

    /** Topology version. */
    private long topVer;

    /**
     * Default constructor.
     */
    public VisorGridDiscoveryEvent() {
        // No-op.
    }

    /**
     * Create event with given parameters.
     *
     * @param typeId Event type.
     * @param id Event id.
     * @param name Event name.
     * @param nid Event node ID.
     * @param ts Event timestamp.
     * @param msg Event message.
     * @param shortDisplay Shortened version of {@code toString()} result.
     * @param evtNodeId Event node id.
     * @param addr Event node address.
     * @param isDaemon If event node is daemon on not.
     * @param topVer Topology version.
     */
    public VisorGridDiscoveryEvent(
        int typeId,
        TurboSQLUuid id,
        String name,
        UUID nid,
        long ts,
        @Nullable String msg,
        String shortDisplay,
        UUID evtNodeId,
        String addr,
        boolean isDaemon,
        long topVer
    ) {
        super(typeId, id, name, nid, ts, msg, shortDisplay);

        this.evtNodeId = evtNodeId;
        this.addr = addr;
        this.isDaemon = isDaemon;
        this.topVer = topVer;
    }

    /**
     * @return Event node ID.
     */
    public UUID getEventNodeId() {
        return evtNodeId;
    }

    /**
     * @return Node address that caused this event to be generated.
     */
    public String getAddress() {
        return addr;
    }

    /**
     * @return If node that caused this event is daemon.
     */
    public boolean isDaemon() {
        return isDaemon;
    }

    /**
     * @return Topology version or {@code 0} if configured discovery SPI implementation
     *      does not support versioning.
     **/
    public long getTopologyVersion() {
        return topVer;
    }

    /** {@inheritDoc} */
    @Override protected void writeExternalData(ObjectOutput out) throws IOException {
        try (VisorDataTransferObjectOutput dtout = new VisorDataTransferObjectOutput(out)) {
            dtout.writeByte(super.getProtocolVersion());

            super.writeExternalData(dtout);
        }

        U.writeUuid(out, evtNodeId);
        U.writeString(out, addr);
        out.writeBoolean(isDaemon);
        out.writeLong(topVer);
    }

    /** {@inheritDoc} */
    @Override protected void readExternalData(byte protoVer, ObjectInput in) throws IOException, ClassNotFoundException {
        try (VisorDataTransferObjectInput dtin = new VisorDataTransferObjectInput(in)) {
            super.readExternalData(dtin.readByte(), dtin);
        }

        evtNodeId = U.readUuid(in);
        addr = U.readString(in);
        isDaemon = in.readBoolean();
        topVer = in.readLong();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(VisorGridDiscoveryEvent.class, this);
    }
}
