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

package com.phonemetra.turbo.internal.visor.file;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import com.phonemetra.turbo.internal.processors.task.GridInternal;
import com.phonemetra.turbo.internal.util.typedef.internal.S;
import com.phonemetra.turbo.internal.visor.VisorJob;
import com.phonemetra.turbo.internal.visor.VisorOneNodeTask;
import com.phonemetra.turbo.internal.visor.log.VisorLogFile;
import org.jetbrains.annotations.Nullable;

import static com.phonemetra.turbo.internal.visor.util.VisorTaskUtils.LOG_FILES_COUNT_LIMIT;
import static com.phonemetra.turbo.internal.visor.util.VisorTaskUtils.matchedFiles;
import static com.phonemetra.turbo.internal.visor.util.VisorTaskUtils.resolveTurboSQLPath;

/**
 * Get list files matching filter.
 */
@GridInternal
public class VisorLatestTextFilesTask extends VisorOneNodeTask<VisorLatestTextFilesTaskArg, Collection<VisorLogFile>> {
    /** */
    private static final long serialVersionUID = 0L;

    /** {@inheritDoc} */
    @Override protected VisorLatestTextFilesJob job(VisorLatestTextFilesTaskArg arg) {
        return new VisorLatestTextFilesJob(arg, debug);
    }

    /**
     * Job that gets list of files.
     */
    private static class VisorLatestTextFilesJob extends VisorJob<VisorLatestTextFilesTaskArg, Collection<VisorLogFile>> {
        /** */
        private static final long serialVersionUID = 0L;

        /**
         * @param arg Folder and regexp.
         * @param debug Debug flag.
         */
        private VisorLatestTextFilesJob(VisorLatestTextFilesTaskArg arg, boolean debug) {
            super(arg, debug);
        }

        /** {@inheritDoc} */
        @Nullable @Override protected Collection<VisorLogFile> run(final VisorLatestTextFilesTaskArg arg) {
            String path = arg.getPath();
            String regexp = arg.getRegexp();

            assert path != null;
            assert regexp != null;

            try {
                File folder = resolveTurboSQLPath(path);

                if (folder == null)
                    return null;

                List<VisorLogFile> files = matchedFiles(folder, regexp);

                if (files.isEmpty())
                    return null;

                if (files.size() > LOG_FILES_COUNT_LIMIT)
                    files = new ArrayList<>(files.subList(0, LOG_FILES_COUNT_LIMIT));

                return files;
            }
            catch (Exception ignored) {
                return null;
            }
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(VisorLatestTextFilesJob.class, this);
        }
    }
}
