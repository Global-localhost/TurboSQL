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

package com.phonemetra.turbo.stream;

import java.util.Collection;
import java.util.Map;
import javax.cache.processor.EntryProcessor;
import javax.cache.processor.EntryProcessorException;
import javax.cache.processor.MutableEntry;
import com.phonemetra.turbo.TurboSQLCache;
import com.phonemetra.turbo.TurboSQLException;
import com.phonemetra.turbo.TurboSQLSystemProperties;
import com.phonemetra.turbo.cache.CacheEntryProcessor;
import com.phonemetra.turbo.internal.util.lang.GridPeerDeployAware;
import com.phonemetra.turbo.internal.util.typedef.internal.U;

/**
 * Convenience adapter to transform update existing values in streaming cache
 * based on the previously cached value.
 * <p>
 * This transformer implement {@link EntryProcessor} and internally will call
 * {@link TurboSQLCache#invoke(Object, EntryProcessor, Object...)} method. Note
 * that the value received from the data streamer will be passed to the entry
 * processor as an argument.
 */
public abstract class StreamTransformer<K, V> implements StreamReceiver<K, V>, EntryProcessor<K, V, Object> {
    /** */
    private static final long serialVersionUID = 0L;

    /** Compatibility mode flag. */
    private static final boolean compatibilityMode =
        TurboSQLSystemProperties.getBoolean(TurboSQLSystemProperties.IGNITE_STREAM_TRANSFORMER_COMPATIBILITY_MODE);

    /** {@inheritDoc} */
    @Override public void receive(TurboSQLCache<K, V> cache, Collection<Map.Entry<K, V>> entries) throws TurboSQLException {
        for (Map.Entry<K, V> entry : entries)
            cache.invoke(entry.getKey(), this, entry.getValue());
    }

    /**
     * Creates a new transformer based on instance of {@link CacheEntryProcessor}.
     *
     * @param ep Entry processor.
     * @return Stream transformer.
     */
    public static <K, V> StreamTransformer<K, V> from(final CacheEntryProcessor<K, V, Object> ep) {
        if (compatibilityMode)
            return new StreamTransformer<K, V>() {
                @Override public Object process(MutableEntry<K, V> entry, Object... args) throws EntryProcessorException {
                    return ep.process(entry, args);
                }
            };
        else
            return new EntryProcessorWrapper<>(ep);
    }

    /**
     * @param <K> Key type.
     * @param <V> Value type.
     */
    private static class EntryProcessorWrapper<K, V> extends StreamTransformer<K,V> implements GridPeerDeployAware {
        /** */
        private static final long serialVersionUID = 0L;

        /** */
        private CacheEntryProcessor<K, V, Object> ep;

        /** */
        private transient ClassLoader ldr;

        /**
         * @param ep Entry processor.
         */
        EntryProcessorWrapper(CacheEntryProcessor<K, V, Object> ep) {
            this.ep = ep;
        }

        /** {@inheritDoc} */
        @Override public Object process(MutableEntry<K, V> entry, Object... args) throws EntryProcessorException {
            return ep.process(entry, args);
        }

        /** {@inheritDoc} */
        @Override public Class<?> deployClass() {
            return ep.getClass();
        }

        /** {@inheritDoc} */
        @Override public ClassLoader classLoader() {
            if (ldr == null)
                ldr = U.detectClassLoader(deployClass());

            return ldr;
        }
    }
}
