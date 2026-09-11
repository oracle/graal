/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.core.logging;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import java.util.Locale;

import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.os.RawFileOperationSupport;
import com.oracle.svm.core.os.RawFileOperationSupport.FileAccessMode;
import com.oracle.svm.core.os.RawFileOperationSupport.FileCreationMode;
import com.oracle.svm.core.os.RawFileOperationSupport.RawFileDescriptor;
import com.oracle.svm.core.os.RawFileOperationSupport.RawFilePath;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.guest.staging.core.memory.UntrackedNullableNativeMemory;
import com.oracle.svm.guest.staging.log.Log;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.BasedOnJDKFile;

/// Writes to a file and performs size-based log file rotation.
final class LogFileOutput extends LogOutput {
    /// Provides a pool of mutexes to share amongst log files.
    private static final VMMutex[] MUTEX_POOL = {
                    new VMMutex("LogOutput.file.0"),
                    new VMMutex("LogOutput.file.1")
    };

    private static int nextMutexIndex;

    /// Default rotation threshold.
    private static final long DEFAULT_FILE_SIZE = 20L * 1024 * 1024;

    /// Default number of archived files.
    private static final int DEFAULT_FILE_COUNT = 5;

    /// Expanded destination name retained for diagnostics and archive path construction.
    private final String pathName;

    /// Serializes native writes and rotation for this file output.
    private final VMMutex mutex;

    /// Native paths retained for all archive slots.
    private RawFilePath[] archivePaths;

    /// Native path retained for active file operations.
    private RawFilePath path;

    /// Number of bytes that triggers rotation, where zero disables size-based rotation.
    private long rotateSize = DEFAULT_FILE_SIZE;

    /// Number of archived files retained, where zero disables rotation.
    private int fileCount = DEFAULT_FILE_COUNT;

    /// Raw descriptor for the active file, recreated after rotation.
    private long rawDescriptor;

    /// Number of bytes written to the current file.
    private long bytesWritten;

    /// Tracks whether the first configuration was validated and opening was attempted.
    private boolean initialized;

    LogFileOutput(String name, String expandedName, RawFilePath path) {
        super("file=" + name);
        this.mutex = nextMutex();
        this.pathName = expandedName;
        this.path = path;
        try {
            this.archivePaths = createArchivePaths(fileCount);
        } catch (RuntimeException | Error exception) {
            /* Construction failure leaves no output object that could release the active path. */
            UntrackedNullableNativeMemory.free(path);
            this.path = Word.nullPointer();
            throw exception;
        }
    }

    /// Returns the native path retained for the active file.
    RawFilePath path() {
        return path;
    }

    /// Attempts to open the active file after every part of its first configuration has been
    /// validated.
    void initialize() {
        assert !initialized;
        initialized = true;
        ensureOpen();
    }

    /// Returns whether the first configuration completed validation.
    boolean isInitialized() {
        return initialized;
    }

    @Override
    protected boolean setOption(String key, String value) {
        if (key.equals("filesize")) {
            rotateSize = parseSize(value);
            return true;
        }
        if (key.equals("filecount")) {
            try {
                fileCount = Integer.parseInt(value);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid filecount '" + value + "'.", ex);
            }
            if (fileCount < 0) {
                throw new IllegalArgumentException("Filecount must not be negative.");
            }
            RawFilePath[] newArchivePaths = createArchivePaths(fileCount);
            freeArchivePaths();
            archivePaths = newArchivePaths;
            return true;
        }
        return false;
    }

    @Override
    protected int writeRaw(CCharPointer bytes, UnsignedWord length) {
        return writeRawLocked(bytes, length);
    }

    private int writeRawLocked(CCharPointer bytes, UnsignedWord length) {
        if (VMOperation.isInProgress()) {
            /* Do not wait for a consumer that is stopped in native output while owning the mutex. */
            RawFileOperationSupport files = RawFileOperationSupport.nativeByteOrder();
            if (rawDescriptor == 0) {
                return ROTATION_OPEN_FAILED;
            }
            if (!files.write(descriptor(), (Pointer) bytes, length)) {
                return WRITE_FAILED;
            }
            bytesWritten += length.rawValue();
            return 0;
        }
        mutex.lock();
        try {
            /* Rotation updates the descriptor while holding the same mutex. */
            if (rawDescriptor == 0) {
                if (!ensureOpenRaw(true)) {
                    return ROTATION_OPEN_FAILED;
                }
            }
            RawFileOperationSupport files = RawFileOperationSupport.nativeByteOrder();
            if (!files.writeSafepointable(descriptor(), (Pointer) bytes, length)) {
                return WRITE_FAILED;
            }
            bytesWritten += length.rawValue();
            if (fileCount > 0 && rotateSize > 0 && bytesWritten >= rotateSize) {
                return rotateRaw();
            }
            return 0;
        } finally {
            mutex.unlock();
        }
    }

    /// Opens a fresh active file, archiving a preexisting file when rotation is enabled.
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25+36/src/hotspot/share/logging/logFileOutput.cpp#L220-L252")
    private void ensureOpen() {
        RawFileOperationSupport files = RawFileOperationSupport.nativeByteOrder();
        if (rawDescriptor == 0) {
            RawFileDescriptor descriptor;
            if (fileCount > 0) {
                RawFileDescriptor existingDescriptor = files.open(path, FileAccessMode.WRITE);
                if (files.isValid(existingDescriptor)) {
                    files.close(existingDescriptor);
                    if (!archiveActiveFile()) {
                        Log.log().string("Could not archive existing log file ").string(pathName).newline();
                        return;
                    }
                }
                /* CREATE avoids truncation if the existence check raced with another creator. */
                descriptor = files.create(path, FileCreationMode.CREATE, FileAccessMode.WRITE);
            } else {
                descriptor = files.create(path, FileCreationMode.CREATE_OR_REPLACE, FileAccessMode.WRITE);
            }
            if (!files.isValid(descriptor)) {
                Log.log().string("Could not open file ") //
                                .string(pathName) //
                                .string(" (code: ") //
                                .signed(descriptor.rawValue()) //
                                .string(")").newline();
                return;
            }
            rawDescriptor = descriptor.rawValue();
            bytesWritten = 0;
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private int rotateRaw() {
        int status = 0;
        RawFileOperationSupport.nativeByteOrder().close(descriptor());
        rawDescriptor = 0;
        boolean activeFileRenamed = archiveActiveFile();
        if (!activeFileRenamed) {
            status |= ROTATION_RENAME_FAILED;
        }
        if (!ensureOpenRaw(!activeFileRenamed)) {
            status |= ROTATION_OPEN_FAILED;
        }
        return status;
    }

    /// Shifts archived files and renames the active file to the first archive slot.
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private boolean archiveActiveFile() {
        if (pathExists(archivePaths[fileCount - 1]) && !LoggingSupport.singleton().delete(archivePaths[fileCount - 1])) {
            return false;
        }
        for (int index = fileCount - 2; index >= 0; index--) {
            if (pathExists(archivePaths[index]) && LoggingSupport.singleton().rename(archivePaths[index], archivePaths[index + 1]) != 0) {
                return false;
            }
        }
        return LoggingSupport.singleton().rename(path, archivePaths[0]) == 0;
    }

    /// Tests whether an archive source exists before treating a failed rename as an error.
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static boolean pathExists(RawFilePath candidate) {
        RawFileOperationSupport files = RawFileOperationSupport.nativeByteOrder();
        RawFileDescriptor descriptor = files.open(candidate, FileAccessMode.READ);
        if (!files.isValid(descriptor)) {
            return false;
        }
        files.close(descriptor);
        return true;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private boolean ensureOpenRaw(boolean appendExisting) {
        RawFileOperationSupport files = RawFileOperationSupport.nativeByteOrder();
        boolean seekToEnd = appendExisting;
        RawFileDescriptor descriptor = appendExisting ? files.open(path, FileAccessMode.WRITE) : files.create(path, FileCreationMode.CREATE_OR_REPLACE, FileAccessMode.WRITE);
        if (appendExisting && !files.isValid(descriptor)) {
            /* HotSpot's append mode also creates the active file when it no longer exists. */
            descriptor = files.create(path, FileCreationMode.CREATE_OR_REPLACE, FileAccessMode.WRITE);
            seekToEnd = false;
        }
        if (!files.isValid(descriptor)) {
            return false;
        }
        long initialSize = 0;
        if (seekToEnd) {
            initialSize = files.size(descriptor);
            if (initialSize < 0 || !files.seek(descriptor, initialSize)) {
                files.close(descriptor);
                return false;
            }
        }
        rawDescriptor = descriptor.rawValue();
        bytesWritten = initialSize;
        return true;
    }

    /// Allocates native path storage for each configured archive slot.
    private RawFilePath[] createArchivePaths(int count) {
        RawFilePath[] result = new RawFilePath[count];
        int allocated = 0;
        try {
            for (; allocated < count; allocated++) {
                result[allocated] = allocatePath(pathName + "." + allocated);
            }
            return result;
        } catch (RuntimeException | Error exception) {
            /* A partially built archive table is not reachable by normal output teardown. */
            for (int index = 0; index < allocated; index++) {
                UntrackedNullableNativeMemory.free(result[index]);
            }
            throw exception;
        }
    }

    /// Converts a Java path to a platform-dependent raw string.
    private static RawFilePath allocatePath(String path) {
        RawFilePath result = RawFileOperationSupport.nativeByteOrder().allocatePath(path);
        if (result.isNull()) {
            throw new IllegalArgumentException("Could not allocate native path for unified log file '" + path + "'.");
        }
        return result;
    }

    /// Releases the native storage held for archive paths.
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void freeArchivePaths() {
        if (archivePaths != null) {
            for (RawFilePath archivePath : archivePaths) {
                UntrackedNullableNativeMemory.free(archivePath);
            }
        }
    }

    @Override
    @Uninterruptible(reason = "File output teardown must not safepoint.")
    protected void closeOutput() {
        mutex.lockNoTransition();
        try {
            RawFileOperationSupport files = RawFileOperationSupport.nativeByteOrder();
            if (rawDescriptor != 0) {
                files.close(descriptor());
                rawDescriptor = 0;
            }
            freeArchivePaths();
            UntrackedNullableNativeMemory.free(path);
            archivePaths = null;
            path = Word.nullPointer();
        } finally {
            mutex.unlock();
        }
    }

    private static synchronized VMMutex nextMutex() {
        VMMutex mutex = MUTEX_POOL[nextMutexIndex];
        if (++nextMutexIndex == MUTEX_POOL.length) {
            nextMutexIndex = 0;
        }
        return mutex;
    }

    /// Reconstructs the platform descriptor from its heap-storable raw value.
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private RawFileDescriptor descriptor() {
        return Word.pointer(rawDescriptor);
    }

    static String expandFilename(String name) {
        String expanded = name;
        if (expanded.contains("%p")) {
            expanded = expanded.replace("%p", Long.toString(LogConfiguration.pid()));
        }
        if (expanded.contains("%t")) {
            expanded = expanded.replace("%t", LogConfiguration.startupTimestamp());
        }
        if (expanded.contains("%hn")) {
            expanded = expanded.replace("%hn", LogConfiguration.hostname());
        }
        return expanded;
    }

    private static long parseSize(String value) {
        String normalized = value.toUpperCase(Locale.ROOT);
        long multiplier = 1;
        if (normalized.endsWith("K") || normalized.endsWith("M") || normalized.endsWith("G")) {
            multiplier = switch (normalized.charAt(normalized.length() - 1)) {
                case 'K' -> 1024L;
                case 'M' -> 1024L * 1024;
                case 'G' -> 1024L * 1024 * 1024;
                default -> throw new AssertionError();
            };
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        try {
            long result = Math.multiplyExact(Long.parseLong(normalized), multiplier);
            if (result < 0) {
                throw new IllegalArgumentException("Filesize must not be negative.");
            }
            return result;
        } catch (ArithmeticException | NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid filesize '" + value + "'.", ex);
        }
    }
}
