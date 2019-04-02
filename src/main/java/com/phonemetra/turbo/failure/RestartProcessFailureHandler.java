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

package com.phonemetra.turbo.failure;

import com.phonemetra.turbo.TurboSQL;
import com.phonemetra.turbo.Ignition;
import com.phonemetra.turbo.internal.util.typedef.G;
import com.phonemetra.turbo.internal.util.typedef.internal.S;
import com.phonemetra.turbo.internal.util.typedef.internal.U;

/**
 * This handler could be used only with turboSQL.(sh|bat) script.
 * Process will be terminated using {@link Ignition#restart(boolean)} call.
 */
public class RestartProcessFailureHandler extends AbstractFailureHandler {
    /** {@inheritDoc} */
    @Override protected boolean handle(TurboSQL turboSQL, FailureContext failureCtx) {
        new Thread(
            new Runnable() {
                @Override public void run() {
                    U.error(turboSQL.log(), "Restarting JVM on TurboSQL failure: [failureCtx=" + failureCtx + ']');

                    G.restart(true);
                }
            },
            "node-restarter"
        ).start();

        return true;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(RestartProcessFailureHandler.class, this, "super", super.toString());
    }
}
