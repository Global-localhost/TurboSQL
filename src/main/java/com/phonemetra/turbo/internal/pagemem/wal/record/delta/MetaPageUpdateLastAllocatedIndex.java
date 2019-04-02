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

package com.phonemetra.turbo.internal.pagemem.wal.record.delta;

import com.phonemetra.turbo.TurboSQLCheckedException;
import com.phonemetra.turbo.internal.pagemem.PageMemory;
import com.phonemetra.turbo.internal.processors.cache.persistence.tree.io.PageIO;
import com.phonemetra.turbo.internal.processors.cache.persistence.tree.io.PageMetaIO;
import com.phonemetra.turbo.internal.util.typedef.internal.S;

/**
 *
 */
public class MetaPageUpdateLastAllocatedIndex extends PageDeltaRecord {
    /** */
    private final int lastAllocatedIdx;

    /**
     * @param pageId Meta page ID.
     */
    public MetaPageUpdateLastAllocatedIndex(int grpId, long pageId, int lastAllocatedIdx) {
        super(grpId, pageId);

        this.lastAllocatedIdx = lastAllocatedIdx;
    }

    /** {@inheritDoc} */
    @Override public void applyDelta(PageMemory pageMem, long pageAddr) throws TurboSQLCheckedException {
        int type = PageIO.getType(pageAddr);

        assert type == PageIO.T_META || type == PageIO.T_PART_META;

        PageMetaIO io = PageIO.getPageIO(type, PageIO.getVersion(pageAddr));

        io.setLastAllocatedPageCount(pageAddr, lastAllocatedIdx);
    }

    /** {@inheritDoc} */
    @Override public RecordType type() {
        return RecordType.META_PAGE_UPDATE_LAST_ALLOCATED_INDEX;
    }

    /**
     * @return Root ID.
     */
    public int lastAllocatedIndex() {
        return lastAllocatedIdx;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(MetaPageUpdateLastAllocatedIndex.class, this, "super", super.toString());
    }
}

