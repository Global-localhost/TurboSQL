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

package com.phonemetra.turbo.internal.util.nio;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.UUID;
import com.phonemetra.turbo.TurboSQLCheckedException;
import com.phonemetra.turbo.TurboSQLException;
import com.phonemetra.turbo.internal.util.lang.TurboSQLInClosure2X;
import com.phonemetra.turbo.lang.TurboSQLInClosure;
import com.phonemetra.turbo.plugin.extensions.communication.Message;
import org.jetbrains.annotations.Nullable;

/**
 *
 */
public interface GridCommunicationClient {
    /**
     * Executes the given handshake closure on opened client passing underlying IO streams.
     * This method pulled to client interface a handshake is only operation requiring access
     * to both output and input streams.
     *
     * @param handshakeC Handshake.
     * @throws TurboSQLCheckedException If handshake failed.
     */
    public void doHandshake(TurboSQLInClosure2X<InputStream, OutputStream> handshakeC) throws TurboSQLCheckedException;

    /**
     * @return {@code True} if client has been closed by this call,
     *      {@code false} if failed to close client (due to concurrent reservation or concurrent close).
     */
    public boolean close();

    /**
     * Forces client close.
     */
    public void forceClose();

    /**
     * @return {@code True} if client is closed;
     */
    public boolean closed();

    /**
     * @return {@code True} if client was reserved, {@code false} otherwise.
     */
    public boolean reserve();

    /**
     * Releases this client by decreasing reservations.
     */
    public void release();

    /**
     * Gets idle time of this client.
     *
     * @return Idle time of this client.
     */
    public long getIdleTime();

    /**
     * @param data Data to send.
     * @throws TurboSQLCheckedException If failed.
     */
    public void sendMessage(ByteBuffer data) throws TurboSQLCheckedException;

    /**
     * @param data Data to send.
     * @param len Length.
     * @throws TurboSQLCheckedException If failed.
     */
    public void sendMessage(byte[] data, int len) throws TurboSQLCheckedException;

    /**
     * @param nodeId Remote node ID. Provided only for sync clients.
     * @param msg Message to send.
     * @param c Ack closure.
     * @return {@code True} if should try to resend message.
     * @throws TurboSQLCheckedException If failed.
     */
    public boolean sendMessage(@Nullable UUID nodeId, Message msg, @Nullable TurboSQLInClosure<TurboSQLException> c)
        throws TurboSQLCheckedException;

    /**
     * @return {@code True} if send is asynchronous.
     */
    public boolean async();

    /**
     * @return Connection index.
     */
    public int connectionIndex();
}
