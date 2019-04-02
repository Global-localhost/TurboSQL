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

package com.phonemetra.turbo.internal.visor.misc;

import java.net.InetAddress;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import com.phonemetra.turbo.TurboSQLException;
import com.phonemetra.turbo.internal.processors.task.GridInternal;
import com.phonemetra.turbo.internal.util.TurboSQLUtils;
import com.phonemetra.turbo.internal.util.typedef.internal.S;
import com.phonemetra.turbo.internal.visor.VisorJob;
import com.phonemetra.turbo.internal.visor.VisorOneNodeTask;
import com.phonemetra.turbo.lang.TurboSQLBiTuple;

/**
 * Task that resolve host name for specified IP address from node.
 */
@GridInternal
public class VisorResolveHostNameTask extends VisorOneNodeTask<Void, Map<String, String>> {
    /** */
    private static final long serialVersionUID = 0L;

    /** {@inheritDoc} */
    @Override protected VisorResolveHostNameJob job(Void arg) {
        return new VisorResolveHostNameJob(arg, debug);
    }

    /**
     * Job that resolve host name for specified IP address.
     */
    private static class VisorResolveHostNameJob extends VisorJob<Void, Map<String, String>> {
        /** */
        private static final long serialVersionUID = 0L;

        /**
         * Create job.
         *
         * @param arg List of IP address for resolve.
         * @param debug Debug flag.
         */
        private VisorResolveHostNameJob(Void arg, boolean debug) {
            super(arg, debug);
        }

        /** {@inheritDoc} */
        @Override protected Map<String, String> run(Void arg) {
            Map<String, String> res = new HashMap<>();

            try {
                TurboSQLBiTuple<Collection<String>, Collection<String>> addrs =
                    TurboSQLUtils.resolveLocalAddresses(InetAddress.getByName("0.0.0.0"), true);

                assert (addrs.get1() != null);
                assert (addrs.get2() != null);

                Iterator<String> ipIt = addrs.get1().iterator();
                Iterator<String> hostIt = addrs.get2().iterator();

                while (ipIt.hasNext() && hostIt.hasNext()) {
                    String ip = ipIt.next();

                    String hostName = hostIt.next();

                    if (hostName == null || hostName.trim().isEmpty()) {
                        try {
                            if (InetAddress.getByName(ip).isLoopbackAddress())
                                res.put(ip, "localhost");
                        }
                        catch (Exception ignore) {
                            //no-op
                        }
                    }
                    else if (!hostName.equals(ip))
                        res.put(ip, hostName);
                }
            }
            catch (Exception e) {
                throw new TurboSQLException("Failed to resolve host name", e);
            }

            return res;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(VisorResolveHostNameJob.class, this);
        }
    }
}
