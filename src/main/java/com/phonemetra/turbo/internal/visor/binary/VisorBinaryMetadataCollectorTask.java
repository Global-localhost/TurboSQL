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

package com.phonemetra.turbo.internal.visor.binary;

import com.phonemetra.turbo.TurboSQLBinary;
import com.phonemetra.turbo.internal.binary.BinaryMarshaller;
import com.phonemetra.turbo.internal.processors.task.GridInternal;
import com.phonemetra.turbo.internal.processors.task.GridVisorManagementTask;
import com.phonemetra.turbo.internal.util.typedef.internal.S;
import com.phonemetra.turbo.internal.visor.VisorJob;
import com.phonemetra.turbo.internal.visor.VisorOneNodeTask;
import com.phonemetra.turbo.marshaller.Marshaller;

/**
 * Task that collects binary metadata.
 */
@GridInternal
@GridVisorManagementTask
public class VisorBinaryMetadataCollectorTask
    extends VisorOneNodeTask<VisorBinaryMetadataCollectorTaskArg, VisorBinaryMetadataCollectorTaskResult> {
    /** */
    private static final long serialVersionUID = 0L;

    /** {@inheritDoc} */
    @Override protected VisorBinaryCollectMetadataJob job(VisorBinaryMetadataCollectorTaskArg lastUpdate) {
        return new VisorBinaryCollectMetadataJob(lastUpdate, debug);
    }

    /** Job that collect portables metadata on node. */
    private static class VisorBinaryCollectMetadataJob
        extends VisorJob<VisorBinaryMetadataCollectorTaskArg, VisorBinaryMetadataCollectorTaskResult> {
        /** */
        private static final long serialVersionUID = 0L;

        /**
         * Create job with given argument.
         *
         * @param arg Task argument.
         * @param debug Debug flag.
         */
        private VisorBinaryCollectMetadataJob(VisorBinaryMetadataCollectorTaskArg arg, boolean debug) {
            super(arg, debug);
        }

        /** {@inheritDoc} */
        @Override protected VisorBinaryMetadataCollectorTaskResult run(VisorBinaryMetadataCollectorTaskArg arg) {
            Marshaller marsh =  turboSQL.configuration().getMarshaller();

            TurboSQLBinary binary = marsh == null || marsh instanceof BinaryMarshaller ? turboSQL.binary() : null;

            return new VisorBinaryMetadataCollectorTaskResult(0L, VisorBinaryMetadata.list(binary));
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(VisorBinaryCollectMetadataJob.class, this);
        }
    }
}
