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

package com.phonemetra.turbo.internal.util.typedef;

import com.phonemetra.turbo.events.Event;
import com.phonemetra.turbo.internal.util.lang.GridFunc;
import com.phonemetra.turbo.lang.TurboSQLPredicate;

/**
 * Defines {@code alias} for <tt>GridPredicate&lt;GridEvent&gt;</tt> by extending
 * {@link com.phonemetra.turbo.lang.TurboSQLPredicate}. Since Java doesn't provide type aliases (like Scala, for example) we resort
 * to these types of measures. This is intended to provide for more concise code without sacrificing
 * readability. For more information see {@link com.phonemetra.turbo.lang.TurboSQLPredicate} and {@link com.phonemetra.turbo.events.Event}.
 * @see com.phonemetra.turbo.lang.TurboSQLPredicate
 * @see com.phonemetra.turbo.events.Event
 * @see GridFunc
 */
public interface PE extends TurboSQLPredicate<Event> { /* No-op. */ }