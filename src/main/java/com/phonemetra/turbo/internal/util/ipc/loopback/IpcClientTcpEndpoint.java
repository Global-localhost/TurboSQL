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

package com.phonemetra.turbo.internal.util.ipc.loopback;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import com.phonemetra.turbo.TurboSQLCheckedException;
import com.phonemetra.turbo.internal.util.ipc.IpcEndpoint;
import com.phonemetra.turbo.internal.util.typedef.internal.U;

/**
 * Loopback IPC endpoint based on socket.
 */
public class IpcClientTcpEndpoint implements IpcEndpoint {
    /** Client socket. */
    private Socket clientSock;

    /**
     * Creates connected client IPC endpoint.
     *
     * @param clientSock Connected client socket.
     */
    public IpcClientTcpEndpoint(Socket clientSock) {
        assert clientSock != null;

        this.clientSock = clientSock;
    }

    /**
     * Creates and connects client IPC endpoint.
     *
     * @param port Port.
     * @param host Host.
     * @throws TurboSQLCheckedException If connection fails.
     */
    public IpcClientTcpEndpoint(String host, int port) throws TurboSQLCheckedException {
        clientSock = new Socket();

        try {
            clientSock.connect(new InetSocketAddress(host, port));
        }
        catch (IOException e) {
            throw new TurboSQLCheckedException("Failed to connect to endpoint [host=" + host + ", port=" + port + ']', e);
        }
    }

    /** {@inheritDoc} */
    @Override public InputStream inputStream() throws TurboSQLCheckedException {
        try {
            return clientSock.getInputStream();
        }
        catch (IOException e) {
            throw new TurboSQLCheckedException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public OutputStream outputStream() throws TurboSQLCheckedException {
        try {
            return clientSock.getOutputStream();
        }
        catch (IOException e) {
            throw new TurboSQLCheckedException(e);
        }
    }

    /** {@inheritDoc} */
    @Override public void close() {
        U.closeQuiet(clientSock);
    }
}