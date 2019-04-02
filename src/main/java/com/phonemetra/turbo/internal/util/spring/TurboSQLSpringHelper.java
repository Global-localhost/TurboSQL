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

package com.phonemetra.turbo.internal.util.spring;

import java.io.InputStream;
import java.net.URL;
import java.util.Collection;
import java.util.Map;
import com.phonemetra.turbo.TurboSQLCheckedException;
import com.phonemetra.turbo.TurboSQLLogger;
import com.phonemetra.turbo.configuration.TurboSQLConfiguration;
import com.phonemetra.turbo.internal.processors.resource.GridSpringResourceContext;
import com.phonemetra.turbo.lang.TurboSQLBiTuple;

/**
 * Spring helper which can parse Spring configuration files, interface was introduced to avoid mandatory
 * runtime dependency on Spring framework.
 */
public interface TurboSQLSpringHelper {
    /**
     * Loads all grid configurations specified within given configuration file.
     * <p>
     * Usually Spring XML configuration file will contain only one Grid definition. Note that
     * Grid configuration bean(s) is retrieved form configuration file by type, so the name of
     * the Grid configuration bean is ignored.
     *
     * @param cfgUrl Configuration file path or URL. This cannot be {@code null}.
     * @param excludedProps Properties to exclude.
     * @return Tuple containing all loaded configurations and Spring context used to load them.
     * @throws TurboSQLCheckedException If grid could not be started or configuration
     *      read. This exception will be thrown also if grid with given name has already
     *      been started or Spring XML configuration file is invalid.
     */
    public TurboSQLBiTuple<Collection<TurboSQLConfiguration>, ? extends GridSpringResourceContext> loadConfigurations(
        URL cfgUrl, String... excludedProps) throws TurboSQLCheckedException;

    /**
     * Loads all grid configurations specified within given configuration input stream.
     * <p>
     * Usually Spring XML configuration input stream will contain only one Grid definition. Note that
     * Grid configuration bean(s) is retrieved form configuration input stream by type, so the name of
     * the Grid configuration bean is ignored.
     *
     * @param cfgStream Configuration input stream. This cannot be {@code null}.
     * @param excludedProps Properties to exclude.
     * @return Tuple containing all loaded configurations and Spring context used to load them.
     * @throws TurboSQLCheckedException If grid could not be started or configuration
     *      read. This exception will be thrown also if grid with given name has already
     *      been started or Spring XML configuration file is invalid.
     */
    public TurboSQLBiTuple<Collection<TurboSQLConfiguration>, ? extends GridSpringResourceContext> loadConfigurations(
        InputStream cfgStream, String... excludedProps) throws TurboSQLCheckedException;

    /**
     * Loads all configurations with given type specified within given configuration file.
     *
     * @param cfgUrl Configuration file path or URL. This cannot be {@code null}.
     * @param cls Required type of configuration.
     * @param excludedProps Properties to exclude.
     * @return Tuple containing all loaded configurations and Spring context used to load them.
     * @throws TurboSQLCheckedException If configuration could not be read.
     */
    public <T> TurboSQLBiTuple<Collection<T>, ? extends GridSpringResourceContext> loadConfigurations(
        URL cfgUrl, Class<T> cls, String... excludedProps) throws TurboSQLCheckedException;

    /**
     * Loads all configurations with given type specified within given configuration input stream.
     *
     * @param cfgStream Configuration input stream. This cannot be {@code null}.
     * @param cls Required type of configuration.
     * @param excludedProps Properties to exclude.
     * @return Tuple containing all loaded configurations and Spring context used to load them.
     * @throws TurboSQLCheckedException If configuration could not be read.
     */
    public <T> TurboSQLBiTuple<Collection<T>, ? extends GridSpringResourceContext> loadConfigurations(
        InputStream cfgStream, Class<T> cls, String... excludedProps) throws TurboSQLCheckedException;

    /**
     * Loads bean instances that match the given types from given configuration file.
     *
     * @param cfgUrl Configuration file path or URL. This cannot be {@code null}.
     * @param beanClasses Beans classes.
     * @return Bean class -> loaded bean instance map, if configuration does not contain bean with required type the
     *       map value is {@code null}.
     * @throws TurboSQLCheckedException If failed to load configuration.
     */
    public Map<Class<?>, Object> loadBeans(URL cfgUrl, Class<?>... beanClasses) throws TurboSQLCheckedException;

    /**
     * Loads bean instance by name.
     *
     * @param url Spring XML file URL.
     * @param beanName Bean name.
     * @return Bean instance.
     * @throws TurboSQLCheckedException In case of error.
     */
    public <T> T loadBean(URL url, String beanName) throws TurboSQLCheckedException;

    /**
     * Loads bean instances that match the given types from given configuration input stream.
     *
     * @param cfgStream Input stream containing Spring XML configuration. This cannot be {@code null}.
     * @param beanClasses Beans classes.
     * @return Bean class -> loaded bean instance map, if configuration does not contain bean with required type the
     *       map value is {@code null}.
     * @throws TurboSQLCheckedException If failed to load configuration.
     */
    public Map<Class<?>, Object> loadBeans(InputStream cfgStream, Class<?>... beanClasses)
        throws TurboSQLCheckedException;

    /**
     * Loads bean instance by name.
     *
     * @param stream Input stream containing Spring XML configuration.
     * @param beanName Bean name.
     * @return Bean instance.
     * @throws TurboSQLCheckedException In case of error.
     */
    public <T> T loadBean(InputStream stream, String beanName) throws TurboSQLCheckedException;

    /**
     * Loads bean instance by name from application context.
     *
     * @param appContext Sprint application context.
     * @param beanName Bean name.
     * @return Bean instance.
     * @throws TurboSQLCheckedException In case of error.
     */
    public <T> T loadBeanFromAppContext(Object appContext, String beanName) throws TurboSQLCheckedException;

    /**
     * Gets user version for given class loader by checking
     * {@code META-INF/turboSQL.xml} file for {@code userVersion} attribute. If
     * {@code turboSQL.xml} file is not found, or user version is not specified there,
     * then default version (empty string) is returned.
     *
     * @param ldr Class loader.
     * @param log Logger.
     * @return User version for given class loader or empty string if no version
     *      was explicitly specified.
     */
    public String userVersion(ClassLoader ldr, TurboSQLLogger log);
}