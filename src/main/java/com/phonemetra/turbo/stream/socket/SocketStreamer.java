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

package com.phonemetra.turbo.stream.socket;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import com.phonemetra.turbo.TurboSQLCheckedException;
import com.phonemetra.turbo.TurboSQLDataStreamer;
import com.phonemetra.turbo.TurboSQLException;
import com.phonemetra.turbo.TurboSQLLogger;
import com.phonemetra.turbo.internal.TurboSQLKernal;
import com.phonemetra.turbo.internal.util.nio.GridBufferedParser;
import com.phonemetra.turbo.internal.util.nio.GridDelimitedParser;
import com.phonemetra.turbo.internal.util.nio.GridNioCodecFilter;
import com.phonemetra.turbo.internal.util.nio.GridNioFilter;
import com.phonemetra.turbo.internal.util.nio.GridNioParser;
import com.phonemetra.turbo.internal.util.nio.GridNioServer;
import com.phonemetra.turbo.internal.util.nio.GridNioServerListener;
import com.phonemetra.turbo.internal.util.nio.GridNioServerListenerAdapter;
import com.phonemetra.turbo.internal.util.nio.GridNioSession;
import com.phonemetra.turbo.internal.util.typedef.F;
import com.phonemetra.turbo.internal.util.typedef.internal.A;
import com.phonemetra.turbo.internal.util.typedef.internal.U;
import com.phonemetra.turbo.marshaller.Marshaller;
import com.phonemetra.turbo.marshaller.MarshallerUtils;
import com.phonemetra.turbo.marshaller.jdk.JdkMarshaller;
import com.phonemetra.turbo.stream.StreamAdapter;
import com.phonemetra.turbo.stream.StreamTupleExtractor;
import org.jetbrains.annotations.Nullable;

/**
 * Server that receives data from TCP socket, converts it to key-value pairs using {@link StreamTupleExtractor} and
 * streams into {@link TurboSQLDataStreamer} instance.
 * <p>
 * By default server uses size-based message processing. That is every message sent over the socket is prepended with
 * 4-byte integer header containing message size. If message delimiter is defined (see {@link #setDelimiter}) then
 * delimiter-based message processing will be used. That is every message sent over the socket is appended with
 * provided delimiter.
 * <p>
 * Received messages through socket converts to Java object using standard serialization. Conversion functionality
 * can be customized via user defined {@link SocketMessageConverter} (e.g. in order to convert messages from
 * non Java clients).
 */
public class SocketStreamer<T, K, V> extends StreamAdapter<T, K, V> {
    /** Default threads. */
    private static final int DFLT_THREADS = Runtime.getRuntime().availableProcessors();

    /** Logger. */
    private TurboSQLLogger log;

    /** Address. */
    private InetAddress addr;

    /** Server port. */
    private int port;

    /** Threads number. */
    private int threads = DFLT_THREADS;

    /** Direct mode. */
    private boolean directMode;

    /** Delimiter. */
    private byte[] delim;

    /** Converter. */
    private SocketMessageConverter<T> converter;

    /** Server. */
    private GridNioServer<byte[]> srv;

    /**
     * Sets server address.
     *
     * @param addr Address.
     */
    public void setAddr(InetAddress addr) {
        this.addr = addr;
    }

    /**
     * Sets port number.
     *
     * @param port Port.
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Sets threadds amount.
     *
     * @param threads Threads.
     */
    public void setThreads(int threads) {
        this.threads = threads;
    }

    /**
     * Sets direct mode flag.
     *
     * @param directMode Direct mode.
     */
    public void setDirectMode(boolean directMode) {
        this.directMode = directMode;
    }

    /**
     * Sets message delimiter.
     *
     * @param delim Delimiter.
     */
    public void setDelimiter(byte[] delim) {
        this.delim = delim;
    }

    /**
     * Sets message converter.
     *
     * @param converter Converter.
     */
    public void setConverter(SocketMessageConverter<T> converter) {
        this.converter = converter;
    }

    /**
     * Starts streamer.
     *
     * @throws TurboSQLException If failed.
     */
    public void start() {
        A.ensure(getSingleTupleExtractor() != null || getMultipleTupleExtractor() != null,
            "tupleExtractor (single or multiple)");
        A.notNull(getStreamer(), "streamer");
        A.notNull(getTurboSQL(), "turboSQL");
        A.ensure(threads > 0, "threads > 0");

        log = getTurboSQL().log();

        GridNioServerListener<byte[]> lsnr = new GridNioServerListenerAdapter<byte[]>() {
            @Override public void onConnected(GridNioSession ses) {
                assert ses.accepted();

                if (log.isDebugEnabled())
                    log.debug("Accepted connection: " + ses.remoteAddress());
            }

            @Override public void onDisconnected(GridNioSession ses, @Nullable Exception e) {
                if (e != null)
                    log.error("Connection failed with exception", e);
            }

            @Override public void onMessage(GridNioSession ses, byte[] msg) {
                addMessage(converter.convert(msg));
            }
        };

        ByteOrder byteOrder = ByteOrder.BIG_ENDIAN;

        GridNioParser parser = F.isEmpty(delim) ? new GridBufferedParser(directMode, byteOrder) :
            new GridDelimitedParser(delim, directMode);

        if (converter == null)
            converter = new DefaultConverter<>(getTurboSQL().name());

        GridNioFilter codec = new GridNioCodecFilter(parser, log, directMode);

        GridNioFilter[] filters = new GridNioFilter[] {codec};

        try {
            srv = new GridNioServer.Builder<byte[]>()
                .address(addr == null ? InetAddress.getLocalHost() : addr)
                .serverName("sock-streamer")
                .port(port)
                .listener(lsnr)
                .logger(log)
                .selectorCount(threads)
                .byteOrder(byteOrder)
                .filters(filters)
                .build();
        }
        catch (TurboSQLCheckedException | UnknownHostException e) {
            throw new TurboSQLException(e);
        }

        srv.start();

        if (log.isDebugEnabled())
            log.debug("Socket streaming server started on " + addr + ':' + port);
    }

    /**
     * Stops streamer.
     */
    public void stop() {
        if (srv != null)
            srv.stop();

        if (log.isDebugEnabled())
            log.debug("Socket streaming server stopped");
    }

    /**
     * Converts message to Java object using Jdk marshaller.
     */
    private class DefaultConverter<T> implements SocketMessageConverter<T> {
        /** Marshaller. */
        private final Marshaller marsh;

        /**
         * Constructor.
         *
         * @param turboSQLInstanceName TurboSQL instance name.
         */
        private DefaultConverter(@Nullable String turboSQLInstanceName) {
            marsh = new JdkMarshaller(((TurboSQLKernal)turboSQL).context().marshallerContext().classNameFilter());

            MarshallerUtils.setNodeName(marsh, turboSQLInstanceName);
        }

        /** {@inheritDoc} */
        @Override public T convert(byte[] msg) {
            try {
                return U.unmarshal(marsh, msg, null);
            }
            catch (TurboSQLCheckedException e) {
                throw new TurboSQLException(e);
            }
        }
    }
}