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
import com.phonemetra.turbo.internal.processors.cache.persistence.tree.io.AbstractDataPageIO;
import com.phonemetra.turbo.internal.processors.cache.persistence.tree.io.PageIO;
import com.phonemetra.turbo.internal.util.typedef.internal.S;

/**
 * Insert into data page.
 */
public class DataPageInsertRecord extends PageDeltaRecord {
    /** */
    private byte[] payload;

    /**
     * @param grpId Cache group ID.
     * @param pageId Page ID.
     * @param payload Remainder of the record.
     */
    public DataPageInsertRecord(
        int grpId,
        long pageId,
        byte[] payload
    ) {
        super(grpId, pageId);

        this.payload = payload;
    }

    /**
     * @return Insert record payload.
     */
    public byte[] payload() {
        return payload;
    }

    /** {@inheritDoc} */
    @Override public void applyDelta(PageMemory pageMem, long pageAddr) throws TurboSQLCheckedException {
        assert payload != null;

        AbstractDataPageIO io = PageIO.getPageIO(pageAddr);

        io.addRow(pageAddr, payload, pageMem.realPageSize(groupId()));
    }

    /** {@inheritDoc} */
    @Override public RecordType type() {
        return RecordType.DATA_PAGE_INSERT_RECORD;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(DataPageInsertRecord.class, this, "super", super.toString());
    }
}
