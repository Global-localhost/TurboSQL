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

package com.phonemetra.turbo.internal.client.thin;

import com.phonemetra.turbo.configuration.BinaryConfiguration;
import com.phonemetra.turbo.configuration.TurboSQLConfiguration;
import com.phonemetra.turbo.internal.binary.BinaryContext;
import com.phonemetra.turbo.internal.binary.BinaryMarshaller;
import com.phonemetra.turbo.internal.binary.BinaryMetadataHandler;
import com.phonemetra.turbo.internal.binary.GridBinaryMarshaller;
import com.phonemetra.turbo.internal.binary.streams.BinaryInputStream;
import com.phonemetra.turbo.logger.NullLogger;
import com.phonemetra.turbo.marshaller.MarshallerContext;

/**
 * Marshals/unmarshals TurboSQL Binary Objects.
 * <p>
 * Maintains schema registry to allow deserialization from TurboSQL Binary format to Java type.
 * </p>
 */
class ClientBinaryMarshaller {
    /** Metadata handler. */
    private final BinaryMetadataHandler metaHnd;

    /** Marshaller context. */
    private final MarshallerContext marshCtx;

    /** Re-using marshaller implementation from TurboSQL core. */
    private GridBinaryMarshaller impl;

    /**
     * Constructor.
     */
    ClientBinaryMarshaller(BinaryMetadataHandler metaHnd, MarshallerContext marshCtx) {
        this.metaHnd = metaHnd;
        this.marshCtx = marshCtx;

        impl = createImpl(null);
    }

    /**
     * Deserializes TurboSQL binary object from input stream.
     *
     * @return Binary object.
     */
    public <T> T unmarshal(BinaryInputStream in) {
        return impl.unmarshal(in);
    }

    /**
     * Serializes Java object into a byte array.
     */
    public byte[] marshal(Object obj) {
        return impl.marshal(obj, false);
    }

    /**
     * Configure marshaller with custom TurboSQL Binary Object configuration.
     */
    public void setBinaryConfiguration(BinaryConfiguration binCfg) {
        if (impl.context().configuration().getBinaryConfiguration() != binCfg)
            impl = createImpl(binCfg);
    }

    /**
     * @return The marshaller context.
     */
    public BinaryContext context() {
        return impl.context();
    }

    /** Create new marshaller implementation. */
    private GridBinaryMarshaller createImpl(BinaryConfiguration binCfg) {
        TurboSQLConfiguration turboSQLCfg = new TurboSQLConfiguration();

        if (binCfg == null) {
            binCfg = new BinaryConfiguration();

            binCfg.setCompactFooter(false);
        }

        turboSQLCfg.setBinaryConfiguration(binCfg);

        BinaryContext ctx = new BinaryContext(metaHnd, turboSQLCfg, new NullLogger());

        BinaryMarshaller marsh = new BinaryMarshaller();

        marsh.setContext(marshCtx);

        ctx.configure(marsh, turboSQLCfg);

        ctx.registerUserTypesSchema();

        return new GridBinaryMarshaller(ctx);
    }
}

