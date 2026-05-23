/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, 2025, Red Hat Inc. All rights reserved.
 * Copyright (c) 2026, 2026, IBM Inc. All rights reserved.
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

package com.oracle.svm.core.jfr;

import org.graalvm.nativeimage.StackValue;
import org.graalvm.word.Pointer;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.collections.GrowableWordArray;
import com.oracle.svm.core.collections.GrowableWordArrayAccess;
import com.oracle.svm.core.memory.NullableNativeMemory;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.os.RawFileOperationSupport;
import com.oracle.svm.core.os.RawFileOperationSupport.FileAccessMode;
import com.oracle.svm.core.os.RawFileOperationSupport.FileCreationMode;
import com.oracle.svm.core.os.RawFileOperationSupport.RawFileDescriptor;
import com.oracle.svm.core.os.RawFileOperationSupport.RawFilePath;

public abstract class AbstractJfrEmergencyDumpSupport implements JfrEmergencyDumpSupport {
    protected static final int CHUNK_FILE_HEADER_SIZE = 68;
    protected static final int JVM_MAXPATHLEN = 4096;
    protected static final int ISO_8601_LEN = 19;
    protected static final int DOT = '.';
    protected static final int CHUNKFILE_EXTENSION_LEN = 4;
    protected static final int EMERGENCY_CHUNK_CREATE_ATTEMPTS = 100;
    protected static final String DUMP_FILE_PREFIX = "svm_oom_pid_";
    protected static final String CHUNKFILE_EXTENSION = ".jfr";

    private final GrowableWordArrayAccess.Comparator chunkFilenameComparator = new ChunkFilenameComparator();

    private String pidText;
    private String cwdText;
    private String dumpPathText;
    private boolean repositoryLocationSet;
    private RawFileDescriptor emergencyFd;
    private int emergencyChunkPathCallCount;
    private String openFileWarning;
    private String openDirectoryWarning;

    protected final void savePid() {
        if (pidText == null) {
            pidText = Long.toString(ProcessHandle.current().pid());
            setPid(pidText);
        }
    }

    protected final void saveCwd() {
        if (cwdText == null) {
            String cwd = System.getProperty("user.dir");
            if (cwd != null) {
                cwdText = cwd;
                setSavedCwdText(cwdText);
            }
        }
    }

    @Override
    public final void setRepositoryLocation(String dirText) {
        repositoryLocationSet = true;
        setRepositoryLocationText(dirText);
        if (isRepositoryLocationTooLong()) {
            openDirectoryWarning = "Unable to open repository " + dirText + ". Repository path is too long.";
        } else {
            openDirectoryWarning = "Unable to open repository " + dirText;
        }
    }

    @Override
    public final void setDumpPath(String dumpPath) {
        if (dumpPath == null || dumpPath.isEmpty()) {
            saveCwd();
            dumpPathText = cwdText;
            setDumpPathToSavedCwd();
        } else {
            dumpPathText = dumpPath;
            setDumpPathText(dumpPathText);
        }

        if (dumpPathText != null) {
            savePid();
            if (isDumpPathTooLong()) {
                openFileWarning = "Unable to create an emergency dump file at the location set by dumppath=" + dumpPathText + ". Dump path is too long.";
            } else {
                openFileWarning = "Unable to create an emergency dump file at the location set by dumppath=" + dumpPathText;
            }
        } else {
            openFileWarning = "Unable to create an emergency dump file. Dump path could not be set.";
        }
    }

    @Override
    public final String getDumpPath() {
        if (dumpPathText != null) {
            return dumpPathText;
        }
        return "";
    }

    @Override
    public final RawFileDescriptor chunkPath() {
        if (!repositoryLocationSet) {
            if (!openEmergencyDumpFile()) {
                return Word.nullPointer();
            }
            RawFileDescriptor fd = emergencyFd;
            emergencyFd = Word.nullPointer();
            return fd;
        }
        return createEmergencyChunkPath();
    }

    protected final RawFileDescriptor createEmergencyChunkPath(RawFilePath path, int baseNameEndIndex) {
        emergencyChunkPathCallCount++;
        if (path.isNull()) {
            return Word.nullPointer();
        }
        for (int attempt = 0; attempt < EMERGENCY_CHUNK_CREATE_ATTEMPTS; attempt++) {
            int idx = appendEmergencyChunkSuffix(baseNameEndIndex, attempt);
            if (idx < 0) {
                return Word.nullPointer();
            }
            idx = writeChunkFileExtension(idx);
            writePathBufferChar(idx, 0);

            RawFileDescriptor fd = getFileSupport().create(path, FileCreationMode.CREATE, FileAccessMode.READ_WRITE);
            if (getFileSupport().isValid(fd)) {
                return fd;
            }
        }
        return Word.nullPointer();
    }

    protected final boolean isDumpPathTooLong() {
        int dumpPathLength = dumpPathLength();
        return dumpPathLength >= 0 && dumpPathLength + 1L + DUMP_FILE_PREFIX.length() + pidLength() + CHUNKFILE_EXTENSION_LEN >= JVM_MAXPATHLEN;
    }

    protected final boolean isRepositoryLocationTooLong() {
        int repositoryLocationLength = repositoryLocationLength();
        return repositoryLocationLength >= 0 && repositoryLocationLength + 1L + ISO_8601_LEN + CHUNKFILE_EXTENSION_LEN >= JVM_MAXPATHLEN;
    }

    protected final boolean isValidChunkFilename(Word filename, int filenameLength) {
        if (filenameLength <= CHUNKFILE_EXTENSION_LEN) {
            return false;
        }
        if (!hasChunkFileExtension(filename, filenameLength)) {
            return false;
        }
        return hasChunkFilenameFormat(filename, filenameLength - CHUNKFILE_EXTENSION_LEN);
    }

    private boolean hasChunkFileExtension(Word filename, int filenameLength) {
        for (int i = 0; i < CHUNKFILE_EXTENSION_LEN; i++) {
            int idx1 = CHUNKFILE_EXTENSION_LEN - i - 1;
            int idx2 = filenameLength - i - 1;
            if (chunkFileExtensionCharAt(idx1) != filenameCharAt(filename, idx2)) {
                return false;
            }
        }
        return true;
    }

    private static int chunkFileExtensionCharAt(int index) {
        return switch (index) {
            case 0 -> '.';
            case 1 -> 'j';
            case 2 -> 'f';
            case 3 -> 'r';
            default -> 0;
        };
    }

    private boolean hasChunkFilenameFormat(Word filename, int baseNameLength) {
        if (baseNameLength < ISO_8601_LEN) {
            return false;
        }
        // Repository chunks are timestamped as yyyy_MM_dd_HH_mm_ss[_NN].jfr.
        for (int i = 0; i < ISO_8601_LEN; i++) {
            int ch = filenameCharAt(filename, i);
            if (i == 4 || i == 7 || i == 10 || i == 13 || i == 16) {
                if (ch != '_') {
                    return false;
                }
            } else if (ch < '0' || ch > '9') {
                return false;
            }
        }
        if (baseNameLength == ISO_8601_LEN) {
            return true;
        }
        if (filenameCharAt(filename, ISO_8601_LEN) != '_' || baseNameLength == ISO_8601_LEN + 1) {
            return false;
        }
        for (int i = ISO_8601_LEN + 1; i < baseNameLength; i++) {
            int ch = filenameCharAt(filename, i);
            if (ch < '0' || ch > '9') {
                return false;
            }
        }
        return true;
    }

    protected final int compareChunkFilenames(Word a, Word b) {
        int cmp = compareFilenameCharacters(a, b, ISO_8601_LEN);
        if (cmp == 0) {
            int aLen = filenameIndexOf(a, DOT);
            int bLen = filenameIndexOf(b, DOT);
            if (aLen < bLen) {
                return -1;
            }
            if (aLen > bLen) {
                return 1;
            }
            cmp = compareFilenameCharacters(a, b, aLen);
        }
        return cmp;
    }

    protected final GrowableWordArrayAccess.Comparator chunkFilenameComparator() {
        return chunkFilenameComparator;
    }

    private int compareFilenameCharacters(Word a, Word b, int length) {
        for (int i = 0; i < length; i++) {
            int cmp = Integer.compare(filenameCharAt(a, i), filenameCharAt(b, i));
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    protected final int writeDateTimeToPathBuffer(int idx, int year, int month, int day, int hour, int minute, int second) {
        int pos = idx;
        pos = writeFourDigits(pos, year);
        writePathBufferChar(pos++, '_');
        pos = writeTwoDigits(pos, month);
        writePathBufferChar(pos++, '_');
        pos = writeTwoDigits(pos, day);
        writePathBufferChar(pos++, '_');
        pos = writeTwoDigits(pos, hour);
        writePathBufferChar(pos++, '_');
        pos = writeTwoDigits(pos, minute);
        writePathBufferChar(pos++, '_');
        return writeTwoDigits(pos, second);
    }

    private int appendEmergencyChunkSuffix(int idx, int attempt) {
        int pos = idx;
        if (attempt != 0) {
            writePathBufferChar(pos++, '_');
            pos = writeDecimal(pos, attempt);
        }
        return pos + CHUNKFILE_EXTENSION_LEN >= JVM_MAXPATHLEN ? -1 : pos;
    }

    private int writeChunkFileExtension(int idx) {
        int pos = idx;
        for (int i = 0; i < CHUNKFILE_EXTENSION_LEN; i++) {
            writePathBufferChar(pos++, chunkFileExtensionCharAt(i));
        }
        return pos;
    }

    protected final boolean addChunkFilename(GrowableWordArray chunkFilenames, Word filenameCopy) {
        if (filenameCopy.rawValue() == 0) {
            SubstrateJVM.getLogging().logJfrSystemError("Unable to copy chunk filename during jfr emergency dump");
            return false;
        }
        if (!GrowableWordArrayAccess.add(chunkFilenames, filenameCopy, NmtCategory.JFR)) {
            freeChunkFilename(filenameCopy);
            SubstrateJVM.getLogging().logJfrSystemError("Unable to add chunk filename to list during jfr emergency dump");
            return false;
        }
        return true;
    }

    protected final boolean isUsableChunkFile(Word filename, int filenameLength) {
        if (!isValidChunkFilename(filename, filenameLength)) {
            return false;
        }

        RawFileDescriptor chunkFd = openRepositoryFile(filename);
        if (!getFileSupport().isValid(chunkFd)) {
            return false;
        }

        long chunkFileSize = getFileSupport().size(chunkFd);
        getFileSupport().close(chunkFd);
        return chunkFileSize >= CHUNK_FILE_HEADER_SIZE;
    }

    private int writeFourDigits(int idx, int value) {
        int pos = writeTwoDigits(idx, value / 100);
        return writeTwoDigits(pos, value % 100);
    }

    private int writeTwoDigits(int idx, int value) {
        int pos = idx;
        writePathBufferChar(pos++, '0' + ((value / 10) % 10));
        writePathBufferChar(pos++, '0' + (value % 10));
        return pos;
    }

    private int writeDecimal(int idx, int value) {
        int pos = idx;
        int divisor = 1;
        while (value / divisor >= 10) {
            divisor *= 10;
        }
        while (divisor > 0) {
            writePathBufferChar(pos++, '0' + ((value / divisor) % 10));
            divisor /= 10;
        }
        return pos;
    }

    @Override
    public final void onVmError() {
        if (!repositoryLocationSet) {
            return;
        }
        if (openEmergencyDumpFile()) {
            GrowableWordArray sortedChunkFilenames = StackValue.get(GrowableWordArray.class);
            GrowableWordArrayAccess.initialize(sortedChunkFilenames);
            try {
                if (openRepository()) {
                    try {
                        iterateRepository(sortedChunkFilenames);
                        writeEmergencyDumpFile(sortedChunkFilenames);
                    } finally {
                        closeRepository();
                    }
                }
            } finally {
                freeChunkFilenames(sortedChunkFilenames);
                GrowableWordArrayAccess.freeData(sortedChunkFilenames);
                closeEmergencyDumpFile();
                sortedChunkFilenames = Word.nullPointer();
            }
        }
    }

    private boolean openEmergencyDumpFile() {
        if (getFileSupport().isValid(emergencyFd)) {
            return true;
        }
        emergencyFd = createEmergencyDumpFile();
        if (!getFileSupport().isValid(emergencyFd)) {
            SubstrateJVM.getLogging().logJfrWarning(openFileWarning);
            useCurrentDirectoryDumpPath();
            emergencyFd = createEmergencyDumpFile();
        }
        return getFileSupport().isValid(emergencyFd);
    }

    private RawFileDescriptor createEmergencyDumpFile() {
        RawFilePath path = createEmergencyDumpPath();
        if (path.isNull()) {
            return Word.nullPointer();
        }
        return getFileSupport().create(path, FileCreationMode.CREATE, FileAccessMode.READ_WRITE);
    }

    private void useCurrentDirectoryDumpPath() {
        dumpPathText = cwdText;
        setDumpPathToSavedCwd();
    }

    protected final String getOpenDirectoryWarning() {
        return openDirectoryWarning;
    }

    protected final void writeEmergencyDumpFile(GrowableWordArray sortedChunkFilenames) {
        int blockSize = 1024 * 1024;
        Pointer copyBlock = NullableNativeMemory.malloc(blockSize, NmtCategory.JFR);
        if (copyBlock.isNull()) {
            SubstrateJVM.getLogging().logJfrSystemError("Unable to malloc memory during jfr emergency dump");
            SubstrateJVM.getLogging().logJfrSystemError("Unable to write jfr emergency dump file");
            return;
        }

        for (int i = 0; i < sortedChunkFilenames.getSize(); i++) {
            RawFileDescriptor chunkFd = openRepositoryFile(GrowableWordArrayAccess.get(sortedChunkFilenames, i));
            if (getFileSupport().isValid(chunkFd)) {
                long chunkFileSize = getFileSupport().size(chunkFd);
                long bytesRead = 0;
                if (!getFileSupport().seek(chunkFd, 0)) {
                    SubstrateJVM.getLogging().logJfrInfo("Unable to recover JFR data, seek failed.");
                    getFileSupport().close(chunkFd);
                    continue;
                }
                while (bytesRead < chunkFileSize) {
                    long readResult = getFileSupport().read(chunkFd, copyBlock, Word.unsigned(blockSize));
                    if (readResult <= 0) {
                        if (readResult < 0) {
                            SubstrateJVM.getLogging().logJfrInfo("Unable to recover JFR data, read failed.");
                        }
                        break;
                    }
                    bytesRead += readResult;
                    if (!getFileSupport().write(emergencyFd, copyBlock, Word.unsigned(readResult))) {
                        SubstrateJVM.getLogging().logJfrInfo("Unable to recover JFR data, write failed.");
                        break;
                    }
                }
                getFileSupport().close(chunkFd);
            }
        }
        NullableNativeMemory.free(copyBlock);
    }

    protected final void freeChunkFilenames(GrowableWordArray chunkFilenames) {
        for (int i = 0; i < chunkFilenames.getSize(); i++) {
            Word filename = GrowableWordArrayAccess.get(chunkFilenames, i);
            if (filename.rawValue() != 0) {
                freeChunkFilename(filename);
            }
        }
    }

    protected final void closeEmergencyDumpFile() {
        if (getFileSupport().isValid(emergencyFd)) {
            getFileSupport().close(emergencyFd);
            emergencyFd = Word.nullPointer();
        }
    }

    protected final int getEmergencyChunkPathCallCount() {
        return emergencyChunkPathCallCount;
    }

    protected final void resetEmergencyChunkPathCallCount() {
        emergencyChunkPathCallCount = 0;
    }

    public static class TestingBackdoor {
        public static long getPathBufferAddress(AbstractJfrEmergencyDumpSupport support) {
            return support.getPathBufferAddress();
        }

        public static int getEmergencyChunkPathCallCount(AbstractJfrEmergencyDumpSupport support) {
            return support.getEmergencyChunkPathCallCount();
        }

        public static void resetEmergencyChunkPathCallCount(AbstractJfrEmergencyDumpSupport support) {
            support.resetEmergencyChunkPathCallCount();
        }
    }

    protected static RawFileOperationSupport getFileSupport() {
        return RawFileOperationSupport.bigEndian();
    }

    private final class ChunkFilenameComparator implements GrowableWordArrayAccess.Comparator {
        @Override
        public int compare(Word a, Word b) {
            return compareChunkFilenames(a, b);
        }
    }

    protected abstract void setPid(String pid);

    protected abstract void setSavedCwdText(String cwd);

    protected abstract void setDumpPathText(String dumpPath);

    protected abstract void setDumpPathToSavedCwd();

    protected abstract void setRepositoryLocationText(String repositoryLocation);

    protected abstract long getPathBufferAddress();

    protected abstract int pidLength();

    protected abstract int dumpPathLength();

    protected abstract int repositoryLocationLength();

    protected abstract RawFilePath createEmergencyDumpPath();

    protected abstract RawFileDescriptor createEmergencyChunkPath();

    protected abstract boolean openRepository();

    protected abstract void iterateRepository(GrowableWordArray sortedChunkFilenames);

    protected abstract RawFileDescriptor openRepositoryFile(Word filename);

    protected abstract int filenameCharAt(Word filename, int index);

    protected abstract int filenameIndexOf(Word filename, int needle);

    protected abstract void writePathBufferChar(int index, int ch);

    protected abstract void freeChunkFilename(Word filename);

    protected abstract void closeRepository();
}
