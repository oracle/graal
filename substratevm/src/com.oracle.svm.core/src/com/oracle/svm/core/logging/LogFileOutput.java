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

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.graalvm.nativeimage.ProcessProperties;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.os.RawFileOperationSupport;
import com.oracle.svm.core.os.RawFileOperationSupport.FileAccessMode;
import com.oracle.svm.core.os.RawFileOperationSupport.FileCreationMode;
import com.oracle.svm.core.os.RawFileOperationSupport.RawFileDescriptor;
import com.oracle.svm.core.os.RawFileOperationSupport.RawFilePath;
import com.oracle.svm.guest.staging.core.memory.UntrackedNullableNativeMemory;
import com.oracle.svm.guest.staging.log.Log;

/// Writes to a file and performs size-based log file rotation.
final class LogFileOutput extends LogOutput {
    /// Provides a pool of mutexes to share amongst log files.
    private static final VMMutex[] MUTEX_POOL = HasULSupport.get() ? new VMMutex[]{
                    new VMMutex("LogOutput.file.0"),
                    new VMMutex("LogOutput.file.1")
    } : null;

    private static int nextMutexIndex;

    /// Default rotation threshold.
    private static final long DEFAULT_FILE_SIZE = 20L * 1024 * 1024;

    /// Default number of archived files.
    private static final int DEFAULT_FILE_COUNT = 5;

    /// Expanded destination path retained for all runtime file operations.
    private final String pathName;

    /// Native paths retained for all archive slots.
    private RawFilePath[] archivePaths;

    /// Native path retained for opening the active file.
    private RawFilePath path;

    /// Number of bytes that triggers rotation, where zero disables size-based rotation.
    private long rotateSize = DEFAULT_FILE_SIZE;

    /// Number of archived files retained, where zero disables rotation.
    private int fileCount = DEFAULT_FILE_COUNT;

    /// Lazily recreated raw descriptor after each rotation.
    private long rawDescriptor;

    /// Number of bytes written to the current file.
    private long bytesWritten;

    LogFileOutput(String name) {
        super("file=" + name, nextMutex());
        this.pathName = expandFilename(name);
        this.path = allocatePath(pathName);
        this.archivePaths = createArchivePaths(fileCount);
        ensureOpen();
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
    protected boolean writeRaw(CCharPointer bytes, UnsignedWord length) {
        /* The descriptor is opened during startup, and emergency logging cannot reopen it. */
        if (rawDescriptor != 0) {
            RawFileOperationSupport files = RawFileOperationSupport.nativeByteOrder();
            if (!files.write(descriptor(), (Pointer) bytes, length)) {
                return false;
            } else {
                bytesWritten += length.rawValue();
            }
        }
        return true;
    }

    @Override
    protected void finishRawLine() {
        if (fileCount > 0 && rotateSize > 0 && bytesWritten >= rotateSize) {
            rotate();
        }
    }

    /// Reopens the active file after startup or after a completed rotation.
    private void ensureOpen() {
        RawFileOperationSupport files = RawFileOperationSupport.nativeByteOrder();
        if (rawDescriptor == 0) {
            RawFileDescriptor descriptor = files.create(path, FileCreationMode.CREATE_OR_REPLACE, FileAccessMode.WRITE);
            if (!files.isValid(descriptor)) {
                Log.log().string("Could not open file ") //
                                .string((CCharPointer) path) //
                                .string(" (code: ") //
                                .signed(descriptor.rawValue()) //
                                .string(")").newline();
                return;
            }
            rawDescriptor = descriptor.rawValue();
            bytesWritten = 0;
        }
    }

    /// Rotates the active file through the precomputed native archive paths.
    private void rotate() {
        RawFileOperationSupport.nativeByteOrder().close(descriptor());
        rawDescriptor = 0;
        LoggingSupport.singleton().delete(archivePaths[fileCount - 1]);
        for (int index = fileCount - 2; index >= 0; index--) {
            rename(archivePaths[index], archivePaths[index + 1], false);
        }
        rename(path, archivePaths[0], true);
        ensureOpen();
    }

    private static void rename(RawFilePath oldPath, RawFilePath newPath, boolean printFailure) {
        int res = LoggingSupport.singleton().rename(oldPath, newPath);
        int errno = LibC.errno();
        if (res != 0 && printFailure) {
            Log.log().string("Could not rename ") //
                            .string((CCharPointer) oldPath) //
                            .string(" to ") //
                            .string((CCharPointer) newPath) //
                            .string(" (res: ") //
                            .signed(res) //
                            .string(", errno: ") //
                            .signed(errno) //
                            .string(")").newline();
        }
    }

    /// Allocates native path storage for each configured archive slot.
    private RawFilePath[] createArchivePaths(int count) {
        RawFilePath[] result = new RawFilePath[count];
        for (int index = 0; index < count; index++) {
            result[index] = allocatePath(pathName + "." + index);
        }
        return result;
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
    private void freeArchivePaths() {
        if (archivePaths != null) {
            for (RawFilePath archivePath : archivePaths) {
                UntrackedNullableNativeMemory.free(archivePath);
            }
        }
    }

    @Override
    protected void closeOutput() {
        RawFileOperationSupport files = RawFileOperationSupport.nativeByteOrder();
        if (rawDescriptor != 0) {
            files.close(descriptor());
            rawDescriptor = 0;
        }
        freeArchivePaths();
        UntrackedNullableNativeMemory.free(path);
        archivePaths = null;
        path = Word.nullPointer();
    }

    private static synchronized VMMutex nextMutex() {
        VMMutex mutex = MUTEX_POOL[nextMutexIndex];
        if (++nextMutexIndex == MUTEX_POOL.length) {
            nextMutexIndex = 0;
        }
        return mutex;
    }

    /// Reconstructs the platform descriptor from its heap-storable raw value.
    private RawFileDescriptor descriptor() {
        return Word.pointer(rawDescriptor);
    }

    private static String expandFilename(String name) {
        String expanded = name;
        if (expanded.contains("%p")) {
            expanded = expanded.replace("%p", Long.toString(ProcessProperties.getProcessID()));
        }
        if (expanded.contains("%t")) {
            DateTimeFormatter timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
            expanded = expanded.replace("%t", timestamp.format(LocalDateTime.now()));
        }
        if (expanded.contains("%hn")) {
            expanded = expanded.replace("%hn", hostname());
        }
        return new File(expanded).getAbsolutePath();
    }

    private static String hostname() {
        return LoggingSupport.singleton().hostname();
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
