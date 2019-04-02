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

package com.phonemetra.turbo.internal.util.future;

import com.phonemetra.turbo.TurboSQLCheckedException;
import com.phonemetra.turbo.TurboSQLIllegalStateException;
import com.phonemetra.turbo.internal.TurboSQLInternalFuture;
import com.phonemetra.turbo.internal.util.lang.GridClosureException;
import com.phonemetra.turbo.internal.util.typedef.internal.S;
import com.phonemetra.turbo.internal.util.typedef.internal.U;
import com.phonemetra.turbo.lang.TurboSQLBiClosure;
import com.phonemetra.turbo.lang.TurboSQLInClosure;
import com.phonemetra.turbo.lang.TurboSQLOutClosure;

/**
 * Future which waits for embedded future to complete and then asynchronously executes
 * provided closure with embedded future result.
 */
public class GridEmbeddedFuture<A, B> extends GridFutureAdapter<A> {
    /** Embedded future to wait for. */
    private TurboSQLInternalFuture<B> embedded;

    /**
     * @param c Closure to execute upon completion of embedded future.
     * @param embedded Embedded future.
     */
    public GridEmbeddedFuture(
        final TurboSQLBiClosure<B, Exception, A> c,
        TurboSQLInternalFuture<B> embedded
    ) {
        assert embedded != null;
        assert c != null;

        this.embedded = embedded;

        embedded.listen(new AL1() {
            @Override public void applyx(TurboSQLInternalFuture<B> embedded) {
                try {
                    onDone(c.apply(embedded.get(), null));
                }
                catch (TurboSQLCheckedException | RuntimeException e) {
                    onDone(c.apply(null, e));
                }
                catch (Error e) {
                    onDone(e);

                    throw e;
                }
            }
        });
    }

    /**
     * Embeds futures. Specific change order of arguments to avoid conflicts.
     *
     * @param embedded Embedded future.
     * @param c Closure which runs upon completion of embedded closure and which returns another future.
     */
    public GridEmbeddedFuture(
        TurboSQLInternalFuture<B> embedded,
        final TurboSQLBiClosure<B, Exception, TurboSQLInternalFuture<A>> c
    ) {
        assert embedded != null;
        assert c != null;

        this.embedded = embedded;

        embedded.listen(new AL1() {
            @Override public void applyx(TurboSQLInternalFuture<B> embedded) {
                try {
                    TurboSQLInternalFuture<A> next = c.apply(embedded.get(), null);

                    if (next == null) {
                        onDone();

                        return;
                    }

                    next.listen(new AL2() {
                        @Override public void applyx(TurboSQLInternalFuture<A> next) {
                            try {
                                onDone(next.get());
                            }
                            catch (GridClosureException e) {
                                onDone(e.unwrap());
                            }
                            catch (TurboSQLCheckedException | RuntimeException e) {
                                onDone(e);
                            }
                            catch (Error e) {
                                onDone(e);

                                throw e;
                            }
                        }
                    });
                }
                catch (GridClosureException e) {
                    c.apply(null, e);

                    onDone(e.unwrap());
                }
                catch (TurboSQLCheckedException | RuntimeException e) {
                    c.apply(null, e);

                    onDone(e);
                }
                catch (Error e) {
                    onDone(e);

                    throw e;
                }
            }
        });
    }

    /**
     * Embeds futures.
     *
     * @param embedded Future.
     * @param c1 Closure which runs upon completion of embedded future and which returns another future.
     * @param c2 Closure will runs upon completion of future returned by {@code c1} closure.
     */
    public GridEmbeddedFuture(
        TurboSQLInternalFuture<B> embedded,
        final TurboSQLBiClosure<B, Exception, TurboSQLInternalFuture<A>> c1,
        final TurboSQLBiClosure<A, Exception, A> c2
    ) {
        assert embedded != null;
        assert c1 != null;
        assert c2 != null;

        this.embedded = embedded;

        embedded.listen(new AL1() {
            @Override public void applyx(TurboSQLInternalFuture<B> embedded) {
                try {
                    TurboSQLInternalFuture<A> next = c1.apply(embedded.get(), null);

                    if (next == null) {
                        onDone();

                        return;
                    }

                    next.listen(new AL2() {
                        @Override public void applyx(TurboSQLInternalFuture<A> next) {
                            try {
                                onDone(c2.apply(next.get(), null));
                            }
                            catch (GridClosureException e) {
                                c2.apply(null, e);

                                onDone(e.unwrap());
                            }
                            catch (TurboSQLCheckedException | RuntimeException e) {
                                c2.apply(null, e);

                                onDone(e);
                            }
                            catch (Error e) {
                                onDone(e);

                                throw e;
                            }
                        }
                    });
                }
                catch (GridClosureException e) {
                    c1.apply(null, e);

                    onDone(e.unwrap());
                }
                catch (TurboSQLCheckedException | RuntimeException e) {
                    c1.apply(null, e);

                    onDone(e);
                }
                catch (Error e) {
                    onDone(e);

                    throw e;
                }
            }
        });
    }

    /**
     * @param embedded Embedded future.
     * @param c Closure to create next future.
     */
    public GridEmbeddedFuture(
        TurboSQLInternalFuture<B> embedded,
        final TurboSQLOutClosure<TurboSQLInternalFuture<A>> c
    ) {
        assert embedded != null;
        assert c != null;

        this.embedded = embedded;

        embedded.listen(new AL1() {
            @Override public void applyx(TurboSQLInternalFuture<B> embedded) {
                try {
                    TurboSQLInternalFuture<A> next = c.apply();

                    if (next == null) {
                        onDone();

                        return;
                    }

                    next.listen(new AL2() {
                        @Override public void applyx(TurboSQLInternalFuture<A> next) {
                            try {
                                onDone(next.get());
                            }
                            catch (GridClosureException e) {
                                onDone(e.unwrap());
                            }
                            catch (TurboSQLCheckedException | RuntimeException e) {
                                onDone(e);
                            }
                            catch (Error e) {
                                onDone(e);

                                throw e;
                            }
                        }
                    });
                }
                catch (Error e) {
                    onDone(e);

                    throw e;
                }
            }
        });
    }

    /** {@inheritDoc} */
    @Override public boolean cancel() throws TurboSQLCheckedException {
        return embedded.cancel();
    }

    /** {@inheritDoc} */
    @Override public boolean isCancelled() {
        return embedded.isCancelled();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridEmbeddedFuture.class, this);
    }

    /** Typedef. */
    private abstract class AL1 extends AsyncListener1 {
        /** */
        private static final long serialVersionUID = 0L;
    }

    /** Typedef. */
    private abstract class AL2 extends AsyncListener2 {
        /** */
        private static final long serialVersionUID = 0L;
    }

    /**
     * Make sure that listener does not throw exceptions.
     */
    private abstract class AsyncListener1 implements TurboSQLInClosure<TurboSQLInternalFuture<B>> {
        /** */
        private static final long serialVersionUID = 0L;

        /** {@inheritDoc} */
        @Override public final void apply(TurboSQLInternalFuture<B> f) {
            try {
                applyx(f);
            }
            catch (TurboSQLIllegalStateException ignore) {
                U.warn(null, "Will not execute future listener (grid is stopping): " + this);
            }
            catch (Exception e) {
                onDone(e);
            }
            catch (Error e) {
                onDone(e);

                throw e;
            }
        }

        /**
         * @param f Future.
         * @throws Exception In case of error.
         */
        protected abstract void applyx(TurboSQLInternalFuture<B> f) throws Exception;
    }

    /**
     * Make sure that listener does not throw exceptions.
     */
    private abstract class AsyncListener2 implements TurboSQLInClosure<TurboSQLInternalFuture<A>> {
        /** */
        private static final long serialVersionUID = 0L;

        /** {@inheritDoc} */
        @Override public final void apply(TurboSQLInternalFuture<A> f) {
            try {
                applyx(f);
            }
            catch (TurboSQLIllegalStateException ignore) {
                U.warn(null, "Will not execute future listener (grid is stopping): " + this);
            }
            catch (Exception e) {
                onDone(e);
            }
            catch (Error e) {
                onDone(e);

                throw e;
            }
        }

        /**
         * @param f Future.
         * @throws Exception In case of error.
         */
        protected abstract void applyx(TurboSQLInternalFuture<A> f) throws Exception;
    }
}