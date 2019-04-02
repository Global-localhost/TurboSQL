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

package com.phonemetra.turbo.internal.client.router.impl;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.phonemetra.turbo.TurboSQLCheckedException;
import com.phonemetra.turbo.TurboSQLException;
import com.phonemetra.turbo.TurboSQLLogger;
import com.phonemetra.turbo.failure.FailureType;
import com.phonemetra.turbo.internal.client.GridClientException;
import com.phonemetra.turbo.internal.client.GridClientFuture;
import com.phonemetra.turbo.internal.client.GridClientFutureListener;
import com.phonemetra.turbo.internal.client.marshaller.GridClientMarshaller;
import com.phonemetra.turbo.internal.client.marshaller.jdk.GridClientJdkMarshaller;
import com.phonemetra.turbo.internal.client.marshaller.optimized.GridClientOptimizedMarshaller;
import com.phonemetra.turbo.internal.client.marshaller.optimized.GridClientZipOptimizedMarshaller;
import com.phonemetra.turbo.internal.processors.rest.client.message.GridClientHandshakeRequest;
import com.phonemetra.turbo.internal.processors.rest.client.message.GridClientHandshakeResponse;
import com.phonemetra.turbo.internal.processors.rest.client.message.GridClientMessage;
import com.phonemetra.turbo.internal.processors.rest.client.message.GridClientPingPacket;
import com.phonemetra.turbo.internal.processors.rest.client.message.GridClientResponse;
import com.phonemetra.turbo.internal.processors.rest.client.message.GridRouterRequest;
import com.phonemetra.turbo.internal.processors.rest.client.message.GridRouterResponse;
import com.phonemetra.turbo.internal.util.nio.GridNioServerListener;
import com.phonemetra.turbo.internal.util.nio.GridNioSession;
import com.phonemetra.turbo.internal.util.typedef.internal.U;
import com.phonemetra.turbo.lang.TurboSQLPredicate;
import com.phonemetra.turbo.marshaller.MarshallerUtils;
import com.phonemetra.turbo.plugin.PluginProvider;
import org.jetbrains.annotations.Nullable;

import static com.phonemetra.turbo.internal.util.nio.GridNioSessionMetaKey.MARSHALLER;
import static com.phonemetra.turbo.internal.util.nio.GridNioSessionMetaKey.MARSHALLER_ID;

/**
 * Nio listener for the router. Extracts necessary meta information from messages
 * and delegates their delivery to underlying client.
 */
public abstract class GridTcpRouterNioListenerAdapter implements GridNioServerListener<GridClientMessage> {
    /** Supported protocol versions. */
    private static final Collection<Short> SUPP_VERS = new HashSet<>();

    /**
     */
    static {
        SUPP_VERS.add((short)1);
    }

    /** Logger. */
    private final TurboSQLLogger log;

    /** Client for grid access. */
    private final GridRouterClientImpl client;

    /** Marshallers map. */
    protected final Map<Byte, GridClientMarshaller> marshMap;

    /**
     * @param log Logger.
     * @param client Client for grid access.
     */
    @SuppressWarnings({"AbstractMethodCallInConstructor", "OverriddenMethodCallDuringObjectConstruction"})
    public GridTcpRouterNioListenerAdapter(TurboSQLLogger log, GridRouterClientImpl client) {
        this.log = log;
        this.client = client;

        marshMap = new HashMap<>();

        List<PluginProvider> providers = U.allPluginProviders();
        GridClientOptimizedMarshaller optdMarsh = new GridClientOptimizedMarshaller(providers);

        marshMap.put(GridClientOptimizedMarshaller.ID, optdMarsh);
        marshMap.put(GridClientZipOptimizedMarshaller.ID, new GridClientZipOptimizedMarshaller(optdMarsh, providers));

        try {
            TurboSQLPredicate<String> clsFilter = MarshallerUtils.classNameFilter(this.getClass().getClassLoader());

            marshMap.put(GridClientJdkMarshaller.ID, new GridClientJdkMarshaller(clsFilter));
        }
        catch (TurboSQLCheckedException e) {
            throw new TurboSQLException(e);
        }

        init();
    }

    /**
     */
    protected abstract void init();

    /** {@inheritDoc} */
    @Override public void onConnected(GridNioSession ses) {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public void onDisconnected(GridNioSession ses, @Nullable Exception e) {
        if (e != null) {
            if (e instanceof RuntimeException)
                U.error(log, "Failed to process request from remote client: " + ses, e);
            else
                U.warn(log, "Closed client session due to exception [ses=" + ses + ", err=" + e.getMessage() + ']');
        }
    }

    /** {@inheritDoc} */
    @Override public void onMessageSent(GridNioSession ses, GridClientMessage msg) {
        // No-op.
    }

    /** {@inheritDoc} */
    @SuppressWarnings("TypeMayBeWeakened")
    @Override public void onMessage(final GridNioSession ses, final GridClientMessage msg) {
        if (msg instanceof GridRouterRequest) {
            GridRouterRequest routerMsg = (GridRouterRequest)msg;

            final UUID clientId = routerMsg.clientId();
            final long reqId = routerMsg.requestId();

            try {
                client.forwardMessage(routerMsg, routerMsg.destinationId(), ses.<Byte>meta(MARSHALLER_ID.ordinal()))
                    .listen(new GridClientFutureListener() {
                        @Override public void onDone(GridClientFuture fut) {
                            try {
                                GridRouterResponse res = (GridRouterResponse)fut.get();
                                // Restoring original request id, because it was overwritten by the client.
                                res.requestId(reqId);

                                ses.send(res);
                            }
                            catch (GridClientException e) {
                                ses.send(makeFailureResponse(e, clientId, reqId));
                            }
                        }
                    });
            }
            catch (GridClientException e) {
                ses.send(makeFailureResponse(e, clientId, reqId));
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                U.warn(
                    log,
                    "Message forwarding was interrupted (will ignore last message): " + e.getMessage());
            }
        }
        else if (msg instanceof GridClientHandshakeRequest) {
            GridClientHandshakeRequest hs = (GridClientHandshakeRequest)msg;

            short ver = hs.version();

            if (!SUPP_VERS.contains(ver)) {
                U.error(log, "Client protocol version is not supported [ses=" + ses +
                    ", ver=" + ver +
                    ", supported=" + SUPP_VERS + ']');

                ses.close();
            }
            else {
                byte marshId = hs.marshallerId();

                GridClientMarshaller marsh = marshMap.get(marshId);

                if (marsh == null) {
                    U.error(log, "Client marshaller ID is invalid. Note that .NET and C++ clients " +
                        "are supported only in enterprise edition [ses=" + ses + ", marshId=" + marshId + ']');

                    ses.close();
                }
                else {
                    ses.addMeta(MARSHALLER_ID.ordinal(), marshId);
                    ses.addMeta(MARSHALLER.ordinal(), marsh);

                    ses.send(GridClientHandshakeResponse.OK);
                }
            }
        }
        else if (msg instanceof GridClientPingPacket)
            ses.send(GridClientPingPacket.PING_MESSAGE);
        else
            throw new IllegalArgumentException("Unsupported input message: " + msg);
    }

    /** {@inheritDoc} */
    @Override public void onFailure(FailureType failureType, Throwable failure) {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public void onSessionWriteTimeout(GridNioSession ses) {
        U.warn(log, "Closing NIO session because of write timeout.");

        ses.close();
    }

    /** {@inheritDoc} */
    @Override public void onSessionIdleTimeout(GridNioSession ses) {
        U.warn(log, "Closing NIO session because of idle.");

        ses.close();
    }

    /**
     * Creates a failure response, based on the given exception.
     *
     * @param e Exception to extract failure report from.
     * @param clientId Client id.
     * @param reqId Request id.
     * @return Failure response.
     */
    private GridClientResponse makeFailureResponse(GridClientException e, UUID clientId, Long reqId) {
        U.error(log, "Failed to process message on router.", e);

        GridClientResponse res = new GridClientResponse();

        res.clientId(clientId);
        res.requestId(reqId);
        res.successStatus(GridClientResponse.STATUS_FAILED);
        res.errorMessage("Failed to process message on router " +
            "[exception=" + e.getClass().getSimpleName() + ", message=" + e.getMessage() + ']');

        return res;
    }
}
