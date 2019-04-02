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

package com.phonemetra.turbo.internal.util.ipc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import com.phonemetra.turbo.TurboSQLCheckedException;
import com.phonemetra.turbo.TurboSQLException;
import com.phonemetra.turbo.TurboSQLLogger;
import com.phonemetra.turbo.internal.util.nio.GridNioFilter;
import com.phonemetra.turbo.internal.util.nio.GridNioFilterAdapter;
import com.phonemetra.turbo.internal.util.nio.GridNioFilterChain;
import com.phonemetra.turbo.internal.util.nio.GridNioFinishedFuture;
import com.phonemetra.turbo.internal.util.nio.GridNioFuture;
import com.phonemetra.turbo.internal.util.nio.GridNioMessageWriterFactory;
import com.phonemetra.turbo.internal.util.nio.GridNioMetricsListener;
import com.phonemetra.turbo.internal.util.nio.GridNioServerListener;
import com.phonemetra.turbo.internal.util.nio.GridNioSession;
import com.phonemetra.turbo.internal.util.nio.GridNioSessionImpl;
import com.phonemetra.turbo.internal.util.typedef.internal.U;
import com.phonemetra.turbo.lang.TurboSQLInClosure;
import com.phonemetra.turbo.plugin.extensions.communication.Message;

/**
 * Allows to re-use existing {@link GridNioFilter}s on IPC (specifically shared memory IPC)
 * communications.
 *
 * Note that this class consumes an entire thread inside {@link #serve()} method
 * in order to serve one {@link IpcEndpoint}.
 */
public class IpcToNioAdapter<T> {
    /** */
    private final IpcEndpoint endp;

    /** */
    private final GridNioFilterChain<T> chain;

    /** */
    private final GridNioSessionImpl ses;

    /** */
    private final AtomicReference<CountDownLatch> latchRef = new AtomicReference<>();

    /** */
    private final ByteBuffer writeBuf;

    /** */
    private final GridNioMetricsListener metricsLsnr;

    /** */
    private final GridNioMessageWriterFactory writerFactory;

    /**
     * @param metricsLsnr Metrics listener.
     * @param log Log.
     * @param endp Endpoint.
     * @param lsnr Listener.
     * @param writerFactory Writer factory.
     * @param filters Filters.
     */
    public IpcToNioAdapter(GridNioMetricsListener metricsLsnr, TurboSQLLogger log, IpcEndpoint endp,
        GridNioServerListener<T> lsnr, GridNioMessageWriterFactory writerFactory, GridNioFilter... filters) {
        assert metricsLsnr != null;

        this.metricsLsnr = metricsLsnr;
        this.endp = endp;
        this.writerFactory = writerFactory;

        chain = new GridNioFilterChain<>(log, lsnr, new HeadFilter(), filters);
        ses = new GridNioSessionImpl(chain, null, null, true);

        writeBuf = ByteBuffer.allocate(8 << 10);

        writeBuf.order(ByteOrder.nativeOrder());
    }

    /**
     * Serves given set of listeners repeatedly reading data from the endpoint.
     *
     * @throws InterruptedException If interrupted.
     */
    public void serve() throws InterruptedException {
        try {
            chain.onSessionOpened(ses);

            InputStream in = endp.inputStream();

            ByteBuffer readBuf = ByteBuffer.allocate(8 << 10);

            readBuf.order(ByteOrder.nativeOrder());

            assert readBuf.hasArray();

            while (!Thread.interrupted()) {
                int pos = readBuf.position();

                int read = in.read(readBuf.array(), pos, readBuf.remaining());

                if (read > 0) {
                    metricsLsnr.onBytesReceived(read);

                    readBuf.position(0);
                    readBuf.limit(pos + read);

                    chain.onMessageReceived(ses, readBuf);

                    if (readBuf.hasRemaining())
                        readBuf.compact();
                    else
                        readBuf.clear();

                    CountDownLatch latch = latchRef.get();

                    if (latch != null)
                        latch.await();
                }
                else if (read < 0) {
                    endp.close();

                    break; // And close below.
                }
            }
        }
        catch (Exception e) {
            chain.onExceptionCaught(ses, new TurboSQLCheckedException("Failed to read from IPC endpoint.", e));
        }
        finally {
            try {
                // Assuming remote end closed connection - pushing event from head to tail.
                chain.onSessionClosed(ses);
            }
            catch (TurboSQLCheckedException e) {
                chain.onExceptionCaught(ses, new TurboSQLCheckedException("Failed to process session close event " +
                    "for IPC endpoint.", e));
            }
        }
    }

    /**
     * Handles write events on chain.
     *
     * @param msg Buffer to send.
     * @return Send result.
     */
    private GridNioFuture<?> send(Message msg) {
        assert writeBuf.hasArray();

        try {
            int cnt = U.writeMessageFully(msg, endp.outputStream(), writeBuf, writerFactory.writer(ses));

            metricsLsnr.onBytesSent(cnt);
        }
        catch (IOException | TurboSQLCheckedException e) {
            return new GridNioFinishedFuture<Object>(e);
        }

        return new GridNioFinishedFuture<>((Object)null);
    }

    /**
     * Filter forwarding messages from chain's head to this server.
     */
    private class HeadFilter extends GridNioFilterAdapter {
        /**
         * Assigns filter name.
         */
        protected HeadFilter() {
            super("HeadFilter");
        }

        /** {@inheritDoc} */
        @Override public void onSessionOpened(GridNioSession ses) throws TurboSQLCheckedException {
            proceedSessionOpened(ses);
        }

        /** {@inheritDoc} */
        @Override public void onSessionClosed(GridNioSession ses) throws TurboSQLCheckedException {
            proceedSessionClosed(ses);
        }

        /** {@inheritDoc} */
        @Override public void onExceptionCaught(GridNioSession ses, TurboSQLCheckedException ex) throws TurboSQLCheckedException {
            proceedExceptionCaught(ses, ex);
        }

        /** {@inheritDoc} */
        @Override public GridNioFuture<?> onSessionWrite(GridNioSession ses,
            Object msg,
            boolean fut,
            TurboSQLInClosure<TurboSQLException> ackC) {
            assert ses == IpcToNioAdapter.this.ses;

            return send((Message)msg);
        }

        /** {@inheritDoc} */
        @Override public void onMessageReceived(GridNioSession ses, Object msg) throws TurboSQLCheckedException {
            proceedMessageReceived(ses, msg);
        }

        /** {@inheritDoc} */
        @Override public GridNioFuture<?> onPauseReads(GridNioSession ses) throws TurboSQLCheckedException {
            // This call should be synced externally to avoid races.
            boolean b = latchRef.compareAndSet(null, new CountDownLatch(1));

            assert b;

            return new GridNioFinishedFuture<>(b);
        }

        /** {@inheritDoc} */
        @Override public GridNioFuture<?> onResumeReads(GridNioSession ses) throws TurboSQLCheckedException {
            // This call should be synced externally to avoid races.
            CountDownLatch latch = latchRef.getAndSet(null);

            if (latch != null)
                latch.countDown();

            return new GridNioFinishedFuture<Object>(latch != null);
        }

        /** {@inheritDoc} */
        @Override public GridNioFuture<Boolean> onSessionClose(GridNioSession ses) {
            assert ses == IpcToNioAdapter.this.ses;

            boolean closed = IpcToNioAdapter.this.ses.setClosed();

            if (closed)
                endp.close();

            return new GridNioFinishedFuture<>(closed);
        }

        /** {@inheritDoc} */
        @Override public void onSessionIdleTimeout(GridNioSession ses) throws TurboSQLCheckedException {
            proceedSessionIdleTimeout(ses);
        }

        /** {@inheritDoc} */
        @Override public void onSessionWriteTimeout(GridNioSession ses) throws TurboSQLCheckedException {
            proceedSessionWriteTimeout(ses);
        }
    }
}