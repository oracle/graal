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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.collections.GrowableWordArray;
import com.oracle.svm.core.collections.GrowableWordArrayAccess;
import com.oracle.svm.core.memory.NativeMemory;
import com.oracle.svm.core.memory.NullableNativeMemory;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.os.RawFileOperationSupport;
import com.oracle.svm.core.os.RawFileOperationSupport.FileAccessMode;
import com.oracle.svm.core.os.RawFileOperationSupport.FileCreationMode;
import com.oracle.svm.core.os.RawFileOperationSupport.RawFileDescriptor;
import com.oracle.svm.core.os.RawFileOperationSupport.RawFilePath;
import com.oracle.svm.shared.util.BasedOnJDKFile;

/**
 * Shared support for writing JFR emergency dump files when a Native Image process terminates
 * after an out-of-memory error. The platform subclasses provide path encoding, directory
 * iteration, and file reopening primitives; this class contains the platform-independent
 * chunk-name filtering, ordering, fallback dump-file creation, and chunk-copying logic.
 */
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25-ga/src/hotspot/share/jfr/recorder/repository/jfrEmergencyDump.cpp#L43-L445")
public abstract class AbstractJfrEmergencyDumpSupport implements JfrEmergencyDumpSupport {
    private static final int CHUNK_FILE_HEADER_SIZE = 68;
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25-ga/src/hotspot/os/posix/include/jvm_md.h#L57") //
    protected static final int JVM_MAXPATHLEN = 4096;
    private static final int ISO_8601_LEN = 19;
    private static final int DOT = '.';
    private static final String CHUNK_FILE_EXTENSION = ".jfr";
    private static final int CHUNKFILE_EXTENSION_LEN = CHUNK_FILE_EXTENSION.length();
    private static final String DUMP_FILE_PREFIX = "svm_oom_pid_";
    private static final int DUMP_FILE_PREFIX_LEN = DUMP_FILE_PREFIX.length();
    private static final int EMERGENCY_CHUNK_CREATE_ATTEMPTS = 100;

    private final GrowableWordArrayAccess.Comparator chunkFilenameComparator = new ChunkFilenameComparator();

    private String pidText;
    private String cwdText;
    private String dumpPathText;
    private final byte[] chunkFileExtensionBytes;
    private final byte[] dumpFilePrefixBytes;
    private byte[] pidBytes;
    private byte[] cwdBytes;
    private byte[] dumpPathBytes;
    protected byte[] repositoryLocationBytes;
    protected RawFilePath pathBuffer;
    private boolean repositoryLocationSet;
    private int emergencyChunkPathCallCount;
    private String openFileWarning;
    private String openDirectoryWarning;

    @SuppressWarnings("this-escape")
    @Platforms(Platform.HOSTED_ONLY.class)
    protected AbstractJfrEmergencyDumpSupport() {
        this.chunkFileExtensionBytes = toBytes(CHUNK_FILE_EXTENSION);
        this.dumpFilePrefixBytes = toBytes(DUMP_FILE_PREFIX);
    }

    @Override
    public final void initialize() {
        savePidIfNeeded();
        allocatePathBufferIfNeeded();
        initializeRepositoryState();
        saveCwdIfNeeded();
    }

    @Override
    public final void teardown() {
        closeRepository();
        freePathBuffer();
    }

    private void savePidIfNeeded() {
        if (pidText == null) {
            pidText = Long.toString(ProcessHandle.current().pid());
            pidBytes = toBytes(pidText);
        }
    }

    private void allocatePathBufferIfNeeded() {
        if (pathBuffer.isNull()) {
            pathBuffer = NativeMemory.calloc(pathBufferSizeInBytes(), NmtCategory.JFR);
        }
    }

    private void saveCwdIfNeeded() {
        if (cwdText == null) {
            String cwd = System.getProperty("user.dir");
            if (cwd != null) {
                cwdText = cwd;
                cwdBytes = toBytes(cwdText);
            }
        }
    }

    @Override
    public final void setRepositoryLocation(String dirText) {
        repositoryLocationSet = true;
        repositoryLocationBytes = toBytes(dirText);
        if (isRepositoryLocationTooLong()) {
            openDirectoryWarning = "Unable to open repository " + dirText + ". Repository path is too long.";
        } else {
            openDirectoryWarning = "Unable to open repository " + dirText;
        }
    }

    @Override
    public final void setDumpPath(String dumpPath) {
        if (dumpPath == null || dumpPath.isEmpty()) {
            saveCwdIfNeeded();
            dumpPathText = cwdText;
            dumpPathBytes = cwdBytes;
        } else {
            dumpPathText = dumpPath;
            dumpPathBytes = toBytes(dumpPathText);
        }

        if (dumpPathText != null) {
            savePidIfNeeded();
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
    public final RawFileDescriptor chunkFile() {
        if (!repositoryLocationSet) {
            return openEmergencyDumpFile();
        }
        return createEmergencyChunkFile();
    }

    private RawFileDescriptor createEmergencyChunkFile() {
        if (isRepositoryLocationTooLong()) {
            return Word.nullPointer();
        }
        int idx = 0;
        idx = appendRepositoryLocationToPathBuffer(idx);
        idx = appendFileSeparatorToPathBuffer(idx);
        idx = appendCurrentDateTimeToPathBuffer(idx);
        if (idx < 0) {
            return Word.nullPointer();
        }

        emergencyChunkPathCallCount++;
        RawFilePath path = pathBuffer;
        if (path.isNull()) {
            return Word.nullPointer();
        }
        for (int attempt = 0; attempt < EMERGENCY_CHUNK_CREATE_ATTEMPTS; attempt++) {
            int endIdx = appendEmergencyChunkSuffix(idx, attempt);
            if (endIdx < 0) {
                return Word.nullPointer();
            }
            endIdx = appendChunkFileExtensionToPathBuffer(endIdx);
            writePathBufferElement(endIdx, (char) 0);

            RawFileDescriptor fd = getFileSupport().create(path, FileCreationMode.CREATE, FileAccessMode.READ_WRITE);
            if (getFileSupport().isValid(fd)) {
                return fd;
            }
        }
        return Word.nullPointer();
    }

    private boolean isDumpPathTooLong() {
        int separatorLength = dumpPathBytes == null ? 0 : 1;
        return dumpPathLength() + separatorLength + DUMP_FILE_PREFIX_LEN + pidLength() + CHUNKFILE_EXTENSION_LEN >= JVM_MAXPATHLEN;
    }

    protected final boolean isRepositoryLocationTooLong() {
        return repositoryLocationLength() + 1L + ISO_8601_LEN + CHUNKFILE_EXTENSION_LEN >= JVM_MAXPATHLEN;
    }

    /**
     * Binary comparison of elements. This works reliably because this method is only
     * called for elements that represent ASCII characters. Note that each ASCII
     * character may be encoded as more than 1 byte though.
     */
    private int compareChunkFilenames(Pointer a, Pointer b) {
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

    private int filenameIndexOf(Pointer filename, int needle) {
        int idx = 0;
        char ch;
        while ((ch = readElement(filename, idx)) != 0) {
            if (ch == needle) {
                return idx;
            }
            idx++;
        }
        return idx;
    }

    private int compareFilenameCharacters(Pointer a, Pointer b, int length) {
        for (int i = 0; i < length; i++) {
            char aChar = readElement(a, i);
            assert aChar <= 0x7f;

            char bChar = readElement(b, i);
            assert bChar <= 0x7f;

            int cmp = Character.compare(aChar, bChar);
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    protected final int writeDateTimeToPathBuffer(int idx, int year, int month, int day, int hour, int minute, int second) {
        int pos = idx;
        pos = writeFourDigits(pos, year);
        writePathBufferElement(pos++, '_');
        pos = writeTwoDigits(pos, month);
        writePathBufferElement(pos++, '_');
        pos = writeTwoDigits(pos, day);
        writePathBufferElement(pos++, '_');
        pos = writeTwoDigits(pos, hour);
        writePathBufferElement(pos++, '_');
        pos = writeTwoDigits(pos, minute);
        writePathBufferElement(pos++, '_');
        return writeTwoDigits(pos, second);
    }

    private int appendEmergencyChunkSuffix(int idx, int attempt) {
        int pos = idx;
        if (attempt != 0) {
            writePathBufferElement(pos++, '_');
            pos = writeTwoDigits(pos, attempt);
        }
        return pos + CHUNKFILE_EXTENSION_LEN + 1 >= JVM_MAXPATHLEN ? -1 : pos;
    }

    private int appendChunkFileExtensionToPathBuffer(int idx) {
        return appendToPathBuffer(chunkFileExtensionBytes, idx);
    }

    protected final boolean addUsableChunkFilename(GrowableWordArray chunkFilenames, Pointer filename, int filenameLength) {
        if (!isUsableChunkFile(filename, filenameLength)) {
            return false;
        }
        Pointer filenameCopy = copyChunkFilename(filename, filenameLength);
        if (filenameCopy.isNull()) {
            SubstrateJVM.getLogging().logJfrSystemError("Unable to copy chunk filename during jfr emergency dump");
            return false;
        }
        if (!GrowableWordArrayAccess.add(chunkFilenames, (Word) filenameCopy, NmtCategory.JFR)) {
            freeChunkFilename(filenameCopy);
            SubstrateJVM.getLogging().logJfrSystemError("Unable to add chunk filename to list during jfr emergency dump");
            return false;
        }
        return true;
    }

    private boolean isUsableChunkFile(Pointer filename, int filenameLength) {
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

    private boolean isValidChunkFilename(Pointer filename, int filenameLength) {
        if (filenameLength <= CHUNKFILE_EXTENSION_LEN) {
            return false;
        }
        if (!hasChunkFileExtension(filename, filenameLength)) {
            return false;
        }
        return hasChunkFilenameFormat(filename, filenameLength - CHUNKFILE_EXTENSION_LEN);
    }

    private boolean hasChunkFileExtension(Pointer filename, int filenameLength) {
        int extensionStart = filenameLength - CHUNKFILE_EXTENSION_LEN;
        UnsignedWord extensionStartOffset = Word.unsigned(extensionStart).multiply(elementSize());
        for (int i = 0; i < chunkFileExtensionBytes.length; i++) {
            if (filename.readByte(extensionStartOffset.add(i)) != chunkFileExtensionBytes[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean hasChunkFilenameFormat(Pointer filename, int baseNameLength) {
        if (baseNameLength < ISO_8601_LEN) {
            return false;
        }
        // Repository chunks are timestamped as yyyy_MM_dd_HH_mm_ss[_NN].jfr.
        for (int i = 0; i < ISO_8601_LEN; i++) {
            char ch = readElement(filename, i);
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
        if (readElement(filename, ISO_8601_LEN) != '_' || baseNameLength == ISO_8601_LEN + 1) {
            return false;
        }
        for (int i = ISO_8601_LEN + 1; i < baseNameLength; i++) {
            char ch = readElement(filename, i);
            if (ch < '0' || ch > '9') {
                return false;
            }
        }
        return true;
    }

    protected final void sortChunkFilenames(GrowableWordArray chunkFilenames, int count) {
        if (count > 0) {
            GrowableWordArrayAccess.qsort(chunkFilenames, 0, count - 1, chunkFilenameComparator);
        }
    }

    private int writeFourDigits(int idx, int value) {
        int pos = writeTwoDigits(idx, value / 100);
        return writeTwoDigits(pos, value % 100);
    }

    private int writeTwoDigits(int idx, int value) {
        int pos = idx;
        writePathBufferElement(pos++, (char) ('0' + ((value / 10) % 10)));
        writePathBufferElement(pos++, (char) ('0' + (value % 10)));
        return pos;
    }

    @Override
    public final void onVmError() {
        if (!repositoryLocationSet) {
            return;
        }
        RawFileDescriptor fd = openEmergencyDumpFile();
        if (getFileSupport().isValid(fd)) {
            GrowableWordArray sortedChunkFilenames = StackValue.get(GrowableWordArray.class);
            GrowableWordArrayAccess.initialize(sortedChunkFilenames);
            try {
                if (openRepository()) {
                    try {
                        iterateRepository(sortedChunkFilenames);
                        writeEmergencyDumpFile(fd, sortedChunkFilenames);
                    } finally {
                        closeRepository();
                    }
                }
            } finally {
                freeChunkFilenames(sortedChunkFilenames);
                GrowableWordArrayAccess.freeData(sortedChunkFilenames);
                getFileSupport().close(fd);
                sortedChunkFilenames = Word.nullPointer();
            }
        }
    }

    private RawFileDescriptor openEmergencyDumpFile() {
        RawFileDescriptor fd = createEmergencyDumpFile();
        if (!getFileSupport().isValid(fd)) {
            SubstrateJVM.getLogging().logJfrWarning(openFileWarning);
            useCurrentDirectoryDumpPath();
            fd = createEmergencyDumpFile();
        }
        return fd;
    }

    private void useCurrentDirectoryDumpPath() {
        dumpPathText = cwdText;
        dumpPathBytes = cwdBytes;
    }

    private RawFileDescriptor createEmergencyDumpFile() {
        RawFilePath path = createEmergencyDumpPath();
        if (path.isNull()) {
            return Word.nullPointer();
        }
        return getFileSupport().create(path, FileCreationMode.CREATE, FileAccessMode.READ_WRITE);
    }

    private RawFilePath createEmergencyDumpPath() {
        if (isDumpPathTooLong()) {
            return Word.nullPointer();
        }

        int idx = 0;
        if (dumpPathBytes != null) {
            idx = appendToPathBuffer(dumpPathBytes, idx);
            idx = appendFileSeparatorToPathBuffer(idx);
        }
        idx = appendToPathBuffer(dumpFilePrefixBytes, idx);
        idx = appendToPathBuffer(pidBytes, idx);
        idx = appendChunkFileExtensionToPathBuffer(idx);
        writePathBufferElement(idx, (char) 0);
        return pathBuffer;
    }

    private Pointer copyChunkFilename(Pointer filename, int filenameLength) {
        Pointer copy = NullableNativeMemory.malloc(Word.unsigned(filenameLength + 1).multiply(elementSize()), NmtCategory.JFR);
        if (copy.isNull()) {
            return Word.nullPointer();
        }

        for (int i = 0; i < filenameLength; i++) {
            char ch = readElement(filename, i);
            writeElement(copy, i, ch);
        }
        writeElement(copy, filenameLength, (char) 0);
        return copy;
    }

    private static void freeChunkFilename(Pointer filename) {
        NullableNativeMemory.free(filename);
    }

    protected final void logOpenDirectoryWarning() {
        SubstrateJVM.getLogging().logJfrSystemError(openDirectoryWarning);
    }

    private void writeEmergencyDumpFile(RawFileDescriptor fd, GrowableWordArray sortedChunkFilenames) {
        UnsignedWord blockSize = Word.unsigned(1024 * 1024);
        Pointer copyBlock = NullableNativeMemory.malloc(blockSize, NmtCategory.JFR);
        if (copyBlock.isNull()) {
            SubstrateJVM.getLogging().logJfrSystemError("Unable to malloc memory during jfr emergency dump.");
            SubstrateJVM.getLogging().logJfrSystemError("Unable to write jfr emergency dump file.");
            return;
        }

        try {
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
                        long readResult = getFileSupport().read(chunkFd, copyBlock, blockSize);
                        if (readResult <= 0) {
                            if (readResult < 0) {
                                SubstrateJVM.getLogging().logJfrInfo("Unable to recover JFR data, read failed.");
                            }
                            break;
                        }
                        bytesRead += readResult;
                        if (!getFileSupport().write(fd, copyBlock, Word.unsigned(readResult))) {
                            SubstrateJVM.getLogging().logJfrInfo("Unable to recover JFR data, write failed.");
                            break;
                        }
                    }
                    getFileSupport().close(chunkFd);
                }
            }
        } finally {
            NullableNativeMemory.free(copyBlock);
        }
    }

    private static void freeChunkFilenames(GrowableWordArray chunkFilenames) {
        for (int i = 0; i < chunkFilenames.getSize(); i++) {
            Pointer filename = GrowableWordArrayAccess.get(chunkFilenames, i);
            if (filename.isNonNull()) {
                freeChunkFilename(filename);
            }
        }
    }

    private static RawFileOperationSupport getFileSupport() {
        return RawFileOperationSupport.bigEndian();
    }

    private void freePathBuffer() {
        NativeMemory.free(pathBuffer);
        pathBuffer = Word.nullPointer();
    }

    private int pidLength() {
        return elementCount(pidBytes);
    }

    private int dumpPathLength() {
        return dumpPathBytes == null ? 0 : elementCount(dumpPathBytes);
    }

    protected final int repositoryLocationLength() {
        return elementCount(repositoryLocationBytes);
    }

    protected final int appendRepositoryLocationToPathBuffer(int idx) {
        return appendToPathBuffer(repositoryLocationBytes, idx);
    }

    private int appendToPathBuffer(byte[] bytes, int start) {
        assert start >= 0;

        UnsignedWord startOffset = Word.unsigned(start).multiply(elementSize());
        assert startOffset.add(bytes.length).belowOrEqual(pathBufferSizeInBytes());

        Pointer ptr = (Pointer) pathBuffer;
        for (int i = 0; i < bytes.length; i++) {
            ptr.writeByte(startOffset.add(i), bytes[i]);
        }
        return start + elementCount(bytes);
    }

    protected final int appendFileSeparatorToPathBuffer(int idx) {
        assert isValidPathBufferIndex(idx);

        if (idx == 0 || !isFileSeparator(readElement((Pointer) pathBuffer, idx - 1))) {
            writePathBufferElement(idx, getFileSeparator());
            return idx + 1;
        }
        return idx;
    }

    protected static boolean isValidPathBufferIndex(int idx) {
        return idx >= 0 && idx < JVM_MAXPATHLEN + 1;
    }

    private int pathBufferSizeInBytes() {
        return (JVM_MAXPATHLEN + 1) * elementSize();
    }

    private int elementCount(byte[] bytes) {
        assert bytes.length % elementSize() == 0;
        return bytes.length / elementSize();
    }

    protected final void writePathBufferElement(int idx, char ch) {
        assert isValidPathBufferIndex(idx);
        writeElement((Pointer) pathBuffer, idx, ch);
    }

    protected abstract void initializeRepositoryState();

    protected abstract byte[] toBytes(String text);

    protected abstract char getFileSeparator();

    protected abstract boolean isFileSeparator(char ch);

    protected abstract int appendCurrentDateTimeToPathBuffer(int idx);

    protected abstract boolean openRepository();

    protected abstract void iterateRepository(GrowableWordArray sortedChunkFilenames);

    protected abstract RawFileDescriptor openRepositoryFile(Pointer filename);

    /**
     * Returns the OS-specific number of bytes per element in a path buffer. Note that individual elements
     * are not necessarily valid characters, so don't use them as such.
     */
    protected abstract int elementSize();

    protected abstract char readElement(Pointer buffer, int index);

    protected abstract void writeElement(Pointer buffer, int index, char ch);

    protected abstract void closeRepository();

    private final class ChunkFilenameComparator implements GrowableWordArrayAccess.Comparator {
        @Override
        public int compare(Word a, Word b) {
            return compareChunkFilenames(a, b);
        }
    }

    public static class TestingBackdoor {
        public static long getPathBufferAddress(AbstractJfrEmergencyDumpSupport support) {
            return support.pathBuffer.rawValue();
        }

        public static int getEmergencyChunkPathCallCount(AbstractJfrEmergencyDumpSupport support) {
            return support.emergencyChunkPathCallCount;
        }

        public static void resetEmergencyChunkPathCallCount(AbstractJfrEmergencyDumpSupport support) {
            support.emergencyChunkPathCallCount = 0;
        }

        public static void clearCachedCwd(AbstractJfrEmergencyDumpSupport support) {
            support.cwdText = null;
            support.cwdBytes = null;
        }

        public static void clearRepositoryLocation(AbstractJfrEmergencyDumpSupport support) {
            support.repositoryLocationSet = false;
            support.repositoryLocationBytes = null;
            support.openDirectoryWarning = null;
        }
    }
}
