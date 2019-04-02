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

package com.phonemetra.turbo.internal;

import com.phonemetra.turbo.TurboSQLCheckedException;
import com.phonemetra.turbo.lang.TurboSQLFuture;

/**
 *
 */
public class TurboSQLClientDisconnectedCheckedException extends TurboSQLCheckedException {
    /** */
    private static final long serialVersionUID = 0L;

    /** */
    private TurboSQLFuture<?> reconnectFut;

    /**
     * @param reconnectFut Reconnect future.
     * @param msg Message.
     */
    public TurboSQLClientDisconnectedCheckedException(TurboSQLFuture<?> reconnectFut, String msg) {
        super(msg);

        this.reconnectFut = reconnectFut;
    }

    /**
     * @return Reconnect future.
     */
    public TurboSQLFuture<?> reconnectFuture() {
        return reconnectFut;
    }
}