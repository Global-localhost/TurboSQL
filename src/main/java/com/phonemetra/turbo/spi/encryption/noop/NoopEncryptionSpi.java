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

package com.phonemetra.turbo.spi.encryption.noop;

import java.io.Serializable;
import java.nio.ByteBuffer;
import com.phonemetra.turbo.TurboSQLException;
import com.phonemetra.turbo.spi.TurboSQLSpiAdapter;
import com.phonemetra.turbo.spi.TurboSQLSpiException;
import com.phonemetra.turbo.spi.TurboSQLSpiNoop;
import com.phonemetra.turbo.spi.encryption.EncryptionSpi;
import com.phonemetra.turbo.spi.encryption.keystore.KeystoreEncryptionSpi;
import org.jetbrains.annotations.Nullable;

/**
 * No operation {@code EncryptionSPI} implementation.
 *
 * @see EncryptionSpi
 * @see KeystoreEncryptionSpi
 */
@TurboSQLSpiNoop
public class NoopEncryptionSpi extends TurboSQLSpiAdapter implements EncryptionSpi {
    /** {@inheritDoc} */
    @Override public byte[] masterKeyDigest() {
        return null;
    }

    /** {@inheritDoc} */
    @Override public Serializable create() throws TurboSQLException {
        throw new TurboSQLSpiException("You have to configure custom EncryptionSpi implementation.");
    }

    /** {@inheritDoc} */
    @Override public void encrypt(ByteBuffer data, Serializable key, ByteBuffer res) {
        throw new TurboSQLSpiException("You have to configure custom EncryptionSpi implementation.");
    }

    /** {@inheritDoc} */
    @Override public void encryptNoPadding(ByteBuffer data, Serializable key, ByteBuffer res) {
        throw new TurboSQLSpiException("You have to configure custom EncryptionSpi implementation.");
    }

    /** {@inheritDoc} */
    @Override public byte[] decrypt(byte[] data, Serializable key) {
        throw new TurboSQLSpiException("You have to configure custom EncryptionSpi implementation.");
    }

    /** {@inheritDoc} */
    @Override public void decryptNoPadding(ByteBuffer data, Serializable key, ByteBuffer res) {
        throw new TurboSQLSpiException("You have to configure custom EncryptionSpi implementation.");
    }

    /** {@inheritDoc} */
    @Override public byte[] encryptKey(Serializable key) {
        throw new TurboSQLSpiException("You have to configure custom EncryptionSpi implementation.");
    }

    /** {@inheritDoc} */
    @Override public Serializable decryptKey(byte[] key) {
        throw new TurboSQLSpiException("You have to configure custom EncryptionSpi implementation.");
    }

    /** {@inheritDoc} */
    @Override public int encryptedSize(int dataSize) {
        return dataSize;
    }

    /** {@inheritDoc} */
    @Override public int encryptedSizeNoPadding(int dataSize) {
        return dataSize;
    }

    @Override public int blockSize() {
        return 0;
    }

    /** {@inheritDoc} */
    @Override public void spiStart(@Nullable String turboSQLInstanceName) throws TurboSQLSpiException {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public void spiStop() throws TurboSQLSpiException {
        // No-op.
    }
}
