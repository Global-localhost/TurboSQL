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

package com.phonemetra.turbo;

import java.util.Collection;
import java.util.Map;
import com.phonemetra.turbo.configuration.FileSystemConfiguration;
import com.phonemetra.turbo.igfs.IgfsBlockLocation;
import com.phonemetra.turbo.igfs.IgfsFile;
import com.phonemetra.turbo.igfs.IgfsInputStream;
import com.phonemetra.turbo.igfs.IgfsMetrics;
import com.phonemetra.turbo.igfs.IgfsMode;
import com.phonemetra.turbo.igfs.IgfsOutputStream;
import com.phonemetra.turbo.igfs.IgfsPath;
import com.phonemetra.turbo.igfs.IgfsPathSummary;
import com.phonemetra.turbo.igfs.mapreduce.IgfsRecordResolver;
import com.phonemetra.turbo.igfs.mapreduce.IgfsTask;
import com.phonemetra.turbo.lang.TurboSQLAsyncSupport;
import com.phonemetra.turbo.lang.TurboSQLFuture;
import com.phonemetra.turbo.lang.TurboSQLUuid;
import org.jetbrains.annotations.Nullable;
import com.phonemetra.turbo.igfs.IgfsPathNotFoundException;

/**
 * <b>IG</b>nite <b>F</b>ile <b>S</b>ystem API. It provides a typical file system "view" on a particular cache:
 * <ul>
 *     <li>list directories or get information for a single path</li>
 *     <li>create/move/delete files or directories</li>
 *     <li>write/read data streams into/from files</li>
 * </ul>
 * The data of each file is split on separate data blocks and stored in the cache.
 * You can access file's data with a standard Java streaming API. Moreover, for each part
 * of the file you can calculate an affinity and process file's content on corresponding
 * nodes to escape unnecessary networking.
 * <p/>
 * This API is fully thread-safe and you can use it from several threads.
 * <h1 class="header">IGFS Configuration</h1>
 * The simplest way to run a TurboSQL node with configured file system is to pass
 * special configuration file included in TurboSQL distribution to {@code turboSQL.sh} or
 * {@code turboSQL.bat} scripts, like this: {@code turboSQL.sh config/hadoop/default-config.xml}
 * <p>
 * {@code IGFS} can be started as a data node or as a client node. Data node is responsible for
 * caching data, while client node is responsible for basic file system operations and accessing
 * data nodes remotely. When used as Hadoop file system, clients nodes usually started together
 * with {@code job-submitter} or {@code job-scheduler} processes, while data nodes are usually
 * started together with Hadoop {@code task-tracker} processes.
 * <h1 class="header">Integration With Hadoop</h1>
 * In addition to direct file system API, {@code IGFS} can be integrated with {@code Hadoop} by
 * plugging in as {@code Hadoop FileSystem}. Refer to
 * {@code com.phonemetra.turbo.hadoop.fs.v1.TurboSQLHadoopFileSystem} or
 * {@code com.phonemetra.turbo.hadoop.fs.v2.TurboSQLHadoopFileSystem} for more information.
 * <p>
 * <b>NOTE:</b> integration with Hadoop is available only in {@code In-Memory Accelerator For Hadoop} edition.
 */
public interface TurboSQLFileSystem extends TurboSQLAsyncSupport {
    /** IGFS scheme name. */
    public static final String IGFS_SCHEME = "igfs";

    /**
     * Gets IGFS name.
     *
     * @return IGFS name.
     */
    public String name();

    /**
     * Gets IGFS configuration.
     *
     * @return IGFS configuration.
     */
    public FileSystemConfiguration configuration();

    /**
     * Gets summary (total number of files, total number of directories and total length)
     * for a given path.
     *
     * @param path Path to get information for.
     * @return Summary object.
     * @throws IgfsPathNotFoundException If path is not found.
     * @throws TurboSQLException If failed.
     */
    public IgfsPathSummary summary(IgfsPath path) throws TurboSQLException;

    /**
     * Opens a file for reading.
     *
     * @param path File path to read.
     * @return File input stream to read data from.
     * @throws TurboSQLException In case of error.
     * @throws IgfsPathNotFoundException If path doesn't exist.
     */
    public IgfsInputStream open(IgfsPath path) throws TurboSQLException;

    /**
     * Opens a file for reading.
     *
     * @param path File path to read.
     * @param bufSize Read buffer size (bytes) or {@code zero} to use default value.
     * @return File input stream to read data from.
     * @throws TurboSQLException In case of error.
     * @throws IgfsPathNotFoundException If path doesn't exist.
     */
    public IgfsInputStream open(IgfsPath path, int bufSize) throws TurboSQLException;

    /**
     * Opens a file for reading.
     *
     * @param path File path to read.
     * @param bufSize Read buffer size (bytes) or {@code zero} to use default value.
     * @param seqReadsBeforePrefetch Amount of sequential reads before prefetch is started.
     * @return File input stream to read data from.
     * @throws TurboSQLException In case of error.
     * @throws IgfsPathNotFoundException If path doesn't exist.
     */
    public IgfsInputStream open(IgfsPath path, int bufSize, int seqReadsBeforePrefetch) throws TurboSQLException;

    /**
     * Creates a file and opens it for writing.
     *
     * @param path File path to create.
     * @param overwrite Overwrite file if it already exists. Note: you cannot overwrite an existent directory.
     * @return File output stream to write data to.
     * @throws TurboSQLException In case of error.
     */
    public IgfsOutputStream create(IgfsPath path, boolean overwrite) throws TurboSQLException;

    /**
     * Creates a file and opens it for writing.
     *
     * @param path File path to create.
     * @param bufSize Write buffer size (bytes) or {@code zero} to use default value.
     * @param overwrite Overwrite file if it already exists. Note: you cannot overwrite an existent directory.
     * @param replication Replication factor.
     * @param blockSize Block size.
     * @param props File properties to set.
     * @return File output stream to write data to.
     * @throws TurboSQLException In case of error.
     */
    public IgfsOutputStream create(IgfsPath path, int bufSize, boolean overwrite, int replication,
        long blockSize, @Nullable Map<String, String> props) throws TurboSQLException;

    /**
     * Creates a file and opens it for writing.
     *
     * @param path File path to create.
     * @param bufSize Write buffer size (bytes) or {@code zero} to use default value.
     * @param overwrite Overwrite file if it already exists. Note: you cannot overwrite an existent directory.
     * @param affKey Affinity key used to store file blocks. If not {@code null}, the whole file will be
     *      stored on node where {@code affKey} resides.
     * @param replication Replication factor.
     * @param blockSize Block size.
     * @param props File properties to set.
     * @return File output stream to write data to.
     * @throws TurboSQLException In case of error.
     */
    public IgfsOutputStream create(IgfsPath path, int bufSize, boolean overwrite,
        @Nullable TurboSQLUuid affKey, int replication, long blockSize, @Nullable Map<String, String> props)
        throws TurboSQLException;

    /**
     * Opens an output stream to an existing file for appending data.
     *
     * @param path File path to append.
     * @param create Create file if it doesn't exist yet.
     * @return File output stream to append data to.
     * @throws TurboSQLException In case of error.
     * @throws IgfsPathNotFoundException If path doesn't exist and create flag is {@code false}.
     */
    public IgfsOutputStream append(IgfsPath path, boolean create) throws TurboSQLException;

    /**
     * Opens an output stream to an existing file for appending data.
     *
     * @param path File path to append.
     * @param bufSize Write buffer size (bytes) or {@code zero} to use default value.
     * @param create Create file if it doesn't exist yet.
     * @param props File properties to set only in case it file was just created.
     * @return File output stream to append data to.
     * @throws TurboSQLException In case of error.
     * @throws IgfsPathNotFoundException If path doesn't exist and create flag is {@code false}.
     */
    public IgfsOutputStream append(IgfsPath path, int bufSize, boolean create, @Nullable Map<String, String> props)
        throws TurboSQLException;

    /**
     * Sets last access time and last modification time for a given path. If argument is {@code null},
     * corresponding time will not be changed.
     *
     * @param path Path to update.
     * @param modificationTime Optional last modification time to set. Value {@code -1} does not update
     *      modification time.
     * @param accessTime Optional last access time to set. Value {@code -1} does not update access time.
     * @throws IgfsPathNotFoundException If target was not found.
     * @throws TurboSQLException If error occurred.
     */
    public void setTimes(IgfsPath path, long modificationTime, long accessTime) throws TurboSQLException;

    /**
     * Gets affinity block locations for data blocks of the file, i.e. the nodes, on which the blocks
     * are stored.
     *
     * @param path File path to get affinity for.
     * @param start Position in the file to start affinity resolution from.
     * @param len Size of data in the file to resolve affinity for.
     * @return Affinity block locations.
     * @throws TurboSQLException In case of error.
     * @throws IgfsPathNotFoundException If path doesn't exist.
     */
    public Collection<IgfsBlockLocation> affinity(IgfsPath path, long start, long len) throws TurboSQLException;

    /**
     * Get affinity block locations for data blocks of the file. In case {@code maxLen} parameter is set and
     * particular block location length is greater than this value, block locations will be split into smaller
     * chunks.
     *
     * @param path File path to get affinity for.
     * @param start Position in the file to start affinity resolution from.
     * @param len Size of data in the file to resolve affinity for.
     * @param maxLen Maximum length of a single returned block location length.
     * @return Affinity block locations.
     * @throws TurboSQLException In case of error.
     * @throws IgfsPathNotFoundException If path doesn't exist.
     */
    public Collection<IgfsBlockLocation> affinity(IgfsPath path, long start, long len, long maxLen)
        throws TurboSQLException;

    /**
     * Gets metrics snapshot for this file system.
     *
     * @return Metrics.
     * @throws TurboSQLException In case of error.
     */
    public IgfsMetrics metrics() throws TurboSQLException;

    /**
     * Resets metrics for this file system.
     *
     * @throws TurboSQLException In case of error.
     */
    public void resetMetrics() throws TurboSQLException;

    /**
     * Determines size of the file denoted by provided path. In case if path is a directory, then
     * total size of all containing entries will be calculated recursively.
     *
     * @param path File system path.
     * @return Total size.
     * @throws TurboSQLException In case of error.
     */
    public long size(IgfsPath path) throws TurboSQLException;

    /**
     * Formats the file system removing all existing entries from it, but not removing anything in secondary
     * file system (if any).
     *
     * @throws TurboSQLException In case clear failed.
     */
    public void clear() throws TurboSQLException;

    /**
     * Formats the file system removing all existing entries from it, but not removing anything in secondary
     * file system (if any).
     *
     * @return Future representing pending completion of the clear operation.
     */
    public TurboSQLFuture<Void> clearAsync() throws TurboSQLException;

    /**
     * Executes IGFS task.
     *
     * @param task Task to execute.
     * @param rslvr Optional resolver to control split boundaries.
     * @param paths Collection of paths to be processed within this task.
     * @param arg Optional task argument.
     * @return Task result.
     * @throws TurboSQLException If execution failed.
     */
    public <T, R> R execute(IgfsTask<T, R> task, @Nullable IgfsRecordResolver rslvr,
        Collection<IgfsPath> paths, @Nullable T arg) throws TurboSQLException;

    /**
     * Executes IGFS task asynchronously.
     *
     * @param task Task to execute.
     * @param rslvr Optional resolver to control split boundaries.
     * @param paths Collection of paths to be processed within this task.
     * @param arg Optional task argument.
     * @return a Future representing pending completion of the task.
     * @throws TurboSQLException If execution failed.
     */
    public <T, R> TurboSQLFuture<R> executeAsync(IgfsTask<T, R> task, @Nullable IgfsRecordResolver rslvr,
        Collection<IgfsPath> paths, @Nullable T arg) throws TurboSQLException;


    /**
     * Executes IGFS task with overridden maximum range length (see
     * {@link com.phonemetra.turbo.configuration.FileSystemConfiguration#getMaximumTaskRangeLength()} for more information).
     * <p>
     * Supports asynchronous execution (see {@link TurboSQLAsyncSupport}).
     *
     * @param task Task to execute.
     * @param rslvr Optional resolver to control split boundaries.
     * @param paths Collection of paths to be processed within this task.
     * @param skipNonExistentFiles Whether to skip non existent files. If set to {@code true} non-existent files will
     *     be ignored. Otherwise an exception will be thrown.
     * @param maxRangeLen Optional maximum range length. If {@code 0}, then by default all consecutive
     *      IGFS blocks will be included.
     * @param arg Optional task argument.
     * @return Task result.
     * @throws TurboSQLException If execution failed.
     */
    public <T, R> R execute(IgfsTask<T, R> task, @Nullable IgfsRecordResolver rslvr,
        Collection<IgfsPath> paths, boolean skipNonExistentFiles, long maxRangeLen, @Nullable T arg)
        throws TurboSQLException;

    /**
     * Executes IGFS task asynchronously with overridden maximum range length (see
     * {@link com.phonemetra.turbo.configuration.FileSystemConfiguration#getMaximumTaskRangeLength()} for more information).
     *
     * @param task Task to execute.
     * @param rslvr Optional resolver to control split boundaries.
     * @param paths Collection of paths to be processed within this task.
     * @param skipNonExistentFiles Whether to skip non existent files. If set to {@code true} non-existent files will
     *     be ignored. Otherwise an exception will be thrown.
     * @param maxRangeLen Optional maximum range length. If {@code 0}, then by default all consecutive
     *      IGFS blocks will be included.
     * @param arg Optional task argument.
     * @return a Future representing pending completion of the task.
     * @throws TurboSQLException If execution failed.
     */
    public <T, R> TurboSQLFuture<R> executeAsync(IgfsTask<T, R> task, @Nullable IgfsRecordResolver rslvr,
        Collection<IgfsPath> paths, boolean skipNonExistentFiles, long maxRangeLen, @Nullable T arg)
        throws TurboSQLException;

    /**
     * Executes IGFS task.
     * <p>
     * Supports asynchronous execution (see {@link TurboSQLAsyncSupport}).
     *
     * @param taskCls Task class to execute.
     * @param rslvr Optional resolver to control split boundaries.
     * @param paths Collection of paths to be processed within this task.
     * @param arg Optional task argument.
     * @return Task result.
     * @throws TurboSQLException If execution failed.
     */
    public <T, R> R execute(Class<? extends IgfsTask<T, R>> taskCls,
        @Nullable IgfsRecordResolver rslvr, Collection<IgfsPath> paths, @Nullable T arg) throws TurboSQLException;

    /**
     * Executes IGFS task asynchronously.
     *
     * @param taskCls Task class to execute.
     * @param rslvr Optional resolver to control split boundaries.
     * @param paths Collection of paths to be processed within this task.
     * @param arg Optional task argument.
     * @return a Future representing pending completion of the task.
     * @throws TurboSQLException If execution failed.
     */
    public <T, R> TurboSQLFuture<R> executeAsync(Class<? extends IgfsTask<T, R>> taskCls,
        @Nullable IgfsRecordResolver rslvr, Collection<IgfsPath> paths, @Nullable T arg) throws TurboSQLException;


    /**
     * Executes IGFS task with overridden maximum range length (see
     * {@link com.phonemetra.turbo.configuration.FileSystemConfiguration#getMaximumTaskRangeLength()} for more information).
     * <p>
     * Supports asynchronous execution (see {@link TurboSQLAsyncSupport}).
     *
     * @param taskCls Task class to execute.
     * @param rslvr Optional resolver to control split boundaries.
     * @param paths Collection of paths to be processed within this task.
     * @param skipNonExistentFiles Whether to skip non existent files. If set to {@code true} non-existent files will
     *     be ignored. Otherwise an exception will be thrown.
     * @param maxRangeLen Maximum range length.
     * @param arg Optional task argument.
     * @return Task result.
     * @throws TurboSQLException If execution failed.
     */
    public <T, R> R execute(Class<? extends IgfsTask<T, R>> taskCls,
        @Nullable IgfsRecordResolver rslvr, Collection<IgfsPath> paths, boolean skipNonExistentFiles,
        long maxRangeLen, @Nullable T arg) throws TurboSQLException;

    /**
     * Executes IGFS task asynchronously with overridden maximum range length (see
     * {@link com.phonemetra.turbo.configuration.FileSystemConfiguration#getMaximumTaskRangeLength()} for more information).
     *
     * @param taskCls Task class to execute.
     * @param rslvr Optional resolver to control split boundaries.
     * @param paths Collection of paths to be processed within this task.
     * @param skipNonExistentFiles Whether to skip non existent files. If set to {@code true} non-existent files will
     *     be ignored. Otherwise an exception will be thrown.
     * @param maxRangeLen Maximum range length.
     * @param arg Optional task argument.
     * @return a Future representing pending completion of the task.
     * @throws TurboSQLException If execution failed.
     */
    public <T, R> TurboSQLFuture<R> executeAsync(Class<? extends IgfsTask<T, R>> taskCls,
        @Nullable IgfsRecordResolver rslvr, Collection<IgfsPath> paths, boolean skipNonExistentFiles,
        long maxRangeLen, @Nullable T arg) throws TurboSQLException;

    /**
     * Checks if the specified path exists in the file system.
     *
     * @param path Path to check for existence in the file system.
     * @return {@code True} if such file exists, otherwise - {@code false}.
     * @throws TurboSQLException In case of error.
     */
    public boolean exists(IgfsPath path);

    /**
     * Updates file information for the specified path. Existent properties, not listed in the passed collection,
     * will not be affected. Other properties will be added or overwritten. Passed properties with {@code null} values
     * will be removed from the stored properties or ignored if they don't exist in the file info.
     * <p>
     * When working in {@code DUAL_SYNC} or {@code DUAL_ASYNC} modes with Hadoop secondary file system only the following properties will be updated:
     * <ul>
     * <li>{@code usrName} - file owner name;</li>
     * <li>{@code grpName} - file owner group;</li>
     * <li>{@code permission} - Unix-style string representing file permissions.</li>
     * </ul>
     *
     * @param path File path to set properties for.
     * @param props Properties to update.
     * @return File information for specified path or {@code null} if such path does not exist.
     * @throws TurboSQLException In case of error.
     */
    public IgfsFile update(IgfsPath path, Map<String, String> props) throws TurboSQLException;

    /**
     * Renames/moves a file.
     * <p>
     * You are free to rename/move data files as you wish, but directories can be only renamed.
     * You cannot move the directory between different parent directories.
     * <p>
     * Examples:
     * <ul>
     *     <li>"/work/file.txt" => "/home/project/Presentation Scenario.txt"</li>
     *     <li>"/work" => "/work-2012.bkp"</li>
     *     <li>"/work" => "<strike>/backups/work</strike>" - such operation is restricted for directories.</li>
     * </ul>
     *
     * @param src Source file path to rename.
     * @param dest Destination file path. If destination path is a directory, then source file will be placed
     *     into destination directory with original name.
     * @throws TurboSQLException In case of error.
     * @throws IgfsPathNotFoundException If source file doesn't exist.
     */
    public void rename(IgfsPath src, IgfsPath dest) throws TurboSQLException;

    /**
     * Deletes file.
     *
     * @param path File path to delete.
     * @param recursive Delete non-empty directories recursively.
     * @return {@code True} in case of success, {@code false} otherwise.
     * @throws TurboSQLException In case of error.
     */
    public boolean delete(IgfsPath path, boolean recursive) throws TurboSQLException;

    /**
     * Creates directories under specified path.
     *
     * @param path Path of directories chain to create.
     * @throws TurboSQLException In case of error.
     */
    public void mkdirs(IgfsPath path) throws TurboSQLException;

    /**
     * Creates directories under specified path with the specified properties.
     * Note that the properties are applied only to created directories, but never
     * updated for existing ones.
     *
     * @param path Path of directories chain to create.
     * @param props Metadata properties to set on created directories.
     * @throws TurboSQLException In case of error.
     */
    public void mkdirs(IgfsPath path, @Nullable Map<String, String> props) throws TurboSQLException;

    /**
     * Lists file paths under the specified path.
     *
     * @param path Path to list files under.
     * @return List of paths under the specified path.
     * @throws TurboSQLException In case of error.
     * @throws IgfsPathNotFoundException If path doesn't exist.
     */
    public Collection<IgfsPath> listPaths(IgfsPath path) throws TurboSQLException;

    /**
     * Lists files under the specified path.
     *
     * @param path Path to list files under.
     * @return List of files under the specified path.
     * @throws TurboSQLException In case of error.
     * @throws IgfsPathNotFoundException If path doesn't exist.
     */
    public Collection<IgfsFile> listFiles(IgfsPath path) throws TurboSQLException;

    /**
     * Gets file information for the specified path.
     *
     * @param path Path to get information for.
     * @return File information for specified path or {@code null} if such path does not exist.
     * @throws TurboSQLException In case of error.
     */
    @Nullable public IgfsFile info(IgfsPath path) throws TurboSQLException;

    /**
     * Get mode for the given path.
     *
     * @param path Path.
     * @return Mode used for this path.
     */
    public IgfsMode mode(IgfsPath path);

    /**
     * Gets used space in bytes.
     *
     * @return Used space in bytes.
     * @throws TurboSQLException In case of error.
     */
    public long usedSpaceSize() throws TurboSQLException;

    /** {@inheritDoc} */
    @Deprecated
    @Override public TurboSQLFileSystem withAsync();
}