/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, 2025, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.posix.jfr;

import static com.oracle.svm.core.posix.headers.Fcntl.O_NOFOLLOW;
import static com.oracle.svm.core.posix.headers.Fcntl.O_RDONLY;

import java.nio.charset.StandardCharsets;

import org.graalvm.nativeimage.impl.InternalPlatform;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.VMInspectionOptions;
import com.oracle.svm.core.collections.GrowableWordArray;
import com.oracle.svm.core.collections.GrowableWordArrayAccess;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.jfr.JfrEmergencyDumpSupport;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.memory.NativeMemory;
import com.oracle.svm.core.memory.NullableNativeMemory;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.os.RawFileOperationSupport;
import com.oracle.svm.core.os.RawFileOperationSupport.FileAccessMode;
import com.oracle.svm.core.os.RawFileOperationSupport.FileCreationMode;
import com.oracle.svm.core.os.RawFileOperationSupport.RawFileDescriptor;
import com.oracle.svm.core.posix.headers.Dirent;
import com.oracle.svm.core.posix.headers.Errno;
import com.oracle.svm.core.posix.headers.Fcntl;
import com.oracle.svm.core.posix.headers.Unistd;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.PartiallyLayerAware;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.Duplicable;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.BasedOnJDKFile;
import com.oracle.svm.shared.util.SubstrateUtil;

@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25-ga/src/hotspot/share/jfr/recorder/repository/jfrEmergencyDump.cpp#L43-L445")
@SingletonTraits(access = AllAccess.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = Duplicable.class, other = PartiallyLayerAware.class)
public class PosixJfrEmergencyDumpSupport implements com.oracle.svm.core.jfr.JfrEmergencyDumpSupport {
    private static final int CHUNK_FILE_HEADER_SIZE = 68;
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25-ga/src/hotspot/os/posix/include/jvm_md.h#L57") //
    private static final int JVM_MAXPATHLEN = 4096;
    private static final int ISO_8601_LEN = 19;
    private static final byte FILE_SEPARATOR = '/';
    private static final byte DOT = '.';
    // It does not really matter what the name is.
    private static final byte[] EMERGENCY_CHUNK_BYTES = "emergency_chunk".getBytes(StandardCharsets.UTF_8);
    private static final byte[] DUMP_FILE_PREFIX = "svm_oom_pid_".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CHUNKFILE_EXTENSION_BYTES = ".jfr".getBytes(StandardCharsets.UTF_8);
    private Dirent.DIR directory;
    private int directoryFd;
    private byte[] pidBytes;
    private byte[] dumpPathBytes;
    private byte[] repositoryLocationBytes;
    private byte[] cwdBytes;
    private RawFileDescriptor emergencyFd;
    private CCharPointer pathBuffer;
    private boolean pathBufferInitialized;
    private int emergencyChunkPathCallCount;
    private String openFileWarning;
    private String openDirectoryWarning;

    @Platforms(Platform.HOSTED_ONLY.class)
    public PosixJfrEmergencyDumpSupport() {
    }

    @Override
    public void initialize() {
        savePid();
        if (!pathBufferInitialized) {
            pathBuffer = NativeMemory.calloc(JVM_MAXPATHLEN + 1, NmtCategory.JFR);
            pathBufferInitialized = true;
        }
        directory = Word.nullPointer();
        directoryFd = -1;
        saveCwd();
    }

    private void savePid() {
        if (pidBytes == null) {
            pidBytes = Long.toString(ProcessHandle.current().pid()).getBytes(StandardCharsets.UTF_8);
        }
    }

    private void saveCwd() {
        if (cwdBytes == null) {
            String cwd = System.getProperty("user.dir");
            if (cwd != null) {
                cwdBytes = cwd.getBytes(StandardCharsets.UTF_8);
            }
        }
    }

    @Override
    public void setRepositoryLocation(String dirText) {
        repositoryLocationBytes = dirText.getBytes(StandardCharsets.UTF_8);
        if (isRepositoryLocationTooLong(repositoryLocationBytes)) {
            openDirectoryWarning = "Unable to open repository " + dirText + ". Repository path is too long.";
        } else {
            openDirectoryWarning = "Unable to open repository " + dirText;
        }
    }

    /** This method is called during JFR initialization. */
    @Override
    public void setDumpPath(String dumpPathText) {
        if (dumpPathText == null || dumpPathText.isEmpty()) {
            saveCwd();
            dumpPathBytes = cwdBytes;
        } else {
            dumpPathBytes = dumpPathText.getBytes(StandardCharsets.UTF_8);
        }

        if (dumpPathBytes != null) {
            savePid(); // setDumpPath may be called before initalize() when setting JFR arguments.
            if (isDumpPathTooLong(dumpPathBytes)) {
                openFileWarning = "Unable to create an emergency dump file at the location set by dumppath=" + new String(dumpPathBytes, StandardCharsets.UTF_8) + ". Dump path is too long.";
            } else {
                openFileWarning = "Unable to create an emergency dump file at the location set by dumppath=" + new String(dumpPathBytes, StandardCharsets.UTF_8);
            }
        } else {
            openFileWarning = "Unable to create an emergency dump file. Dump path could not be set.";
        }

    }

    @Override
    public String getDumpPath() {
        if (dumpPathBytes != null) {
            return new String(dumpPathBytes, StandardCharsets.UTF_8);
        }
        return "";
    }

    /**
     * This method either creates and uses the dumpfile itself as a new chunk, or creates a new file
     * in the repository location.
     */
    @Override
    public RawFileDescriptor chunkPath() {
        if (repositoryLocationBytes == null) {
            if (!openEmergencyDumpFile()) {
                return Word.nullPointer();
            }
            /*
             * We can directly use the emergency dump file name as the new chunk since there are no
             * other chunk files.
             */
            RawFileDescriptor fd = emergencyFd;
            emergencyFd = Word.nullPointer();
            return fd;
        }
        return createEmergencyChunkPath();
    }

    /**
     * The normal chunkfile name format is: repository path + file separator + date time +
     * extension. In this case we just use a hardcoded string instead of date time, which will
     * successfully rank last in lexographic order among other chunkfile names.
     */
    private RawFileDescriptor createEmergencyChunkPath() {
        emergencyChunkPathCallCount++;
        if (isRepositoryLocationTooLong(repositoryLocationBytes)) {
            return Word.nullPointer();
        }
        clearPathBuffer();
        int idx = 0;
        idx = writeToPathBuffer(repositoryLocationBytes, idx);
        getPathBuffer().write(idx++, FILE_SEPARATOR);
        idx = writeToPathBuffer(EMERGENCY_CHUNK_BYTES, idx);
        idx = writeToPathBuffer(CHUNKFILE_EXTENSION_BYTES, idx);
        getPathBuffer().write(idx++, (byte) 0);
        return getFileSupport().create(getPathBuffer(), FileCreationMode.CREATE_OR_REPLACE, FileAccessMode.READ_WRITE);
    }

    @Override
    public void onVmError() {
        if (repositoryLocationBytes == null) {
            return;
        }
        if (openEmergencyDumpFile()) {
            GrowableWordArray sortedChunkFilenames = StackValue.get(GrowableWordArray.class);
            GrowableWordArrayAccess.initialize(sortedChunkFilenames);
            try {
                if (openDirectory()) {
                    try {
                        /*
                         * Keep the repository directory open for the whole scan so validation and
                         * chunk reopening stay anchored to the same directory instance.
                         */
                        iterateRepository(sortedChunkFilenames);
                        writeEmergencyDumpFile(sortedChunkFilenames);
                    } finally {
                        closeDirectory();
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
        // O_CREAT | O_RDWR and S_IREAD | S_IWRITE permissions
        emergencyFd = createEmergencyDumpFile();
        if (!getFileSupport().isValid(emergencyFd)) {
            SubstrateJVM.getLogging().logJfrWarning(openFileWarning);
            // Fallback. Try to create it in the current directory.
            dumpPathBytes = null;
            emergencyFd = createEmergencyDumpFile();
        }
        return getFileSupport().isValid(emergencyFd);
    }

    private RawFileDescriptor createEmergencyDumpFile() {
        CCharPointer path = createEmergencyDumpPath();
        if (path.isNull()) {
            return Word.nullPointer();
        }
        return getFileSupport().create(path, FileCreationMode.CREATE, FileAccessMode.READ_WRITE);
    }

    private CCharPointer createEmergencyDumpPath() {
        int idx = 0;
        clearPathBuffer();

        if (dumpPathBytes == null) {
            dumpPathBytes = cwdBytes;
        }
        if (isDumpPathTooLong(dumpPathBytes)) {
            return Word.nullPointer();
        }
        if (dumpPathBytes != null) {
            idx = writeToPathBuffer(dumpPathBytes, idx);
            // Add delimiter
            getPathBuffer().write(idx++, FILE_SEPARATOR);
        }

        idx = writeToPathBuffer(DUMP_FILE_PREFIX, idx);
        idx = writeToPathBuffer(pidBytes, idx);
        idx = writeToPathBuffer(CHUNKFILE_EXTENSION_BYTES, idx);
        getPathBuffer().write(idx, (byte) 0);
        return getPathBuffer();
    }

    private void iterateRepository(GrowableWordArray gwa) {
        int count = 0;
        if (directory.isNull()) {
            return;
        }
        // Iterate files in the repository and append filtered file names to the files array.
        Dirent.dirent entry;
        while ((entry = Dirent.readdir(directory)).isNonNull()) {
            // Filter files.
            if (filter(entry)) {
                CCharPointer fn = entry.d_name();
                CCharPointer fnCopy = LibC.strdup(fn);
                if (fnCopy.isNull()) {
                    SubstrateJVM.getLogging().logJfrSystemError("Unable to copy chunk filename during jfr emergency dump");
                    continue;
                }
                // Append filtered files to list.
                if (!GrowableWordArrayAccess.add(gwa, (Word) (Pointer) fnCopy, NmtCategory.JFR)) {
                    LibC.free(fnCopy);
                    SubstrateJVM.getLogging().logJfrSystemError("Unable to add chunk filename to list during jfr emergency dump");
                } else {
                    count++;
                }
            }
        }
        if (count > 0) {
            GrowableWordArrayAccess.qsort(gwa, 0, count - 1, PosixJfrEmergencyDumpSupport::compare);
        }
    }

    static int compare(Word a, Word b) {
        CCharPointer filenameA = (CCharPointer) ((Pointer) a);
        CCharPointer filenameB = (CCharPointer) ((Pointer) b);
        int lengthA = (int) SubstrateUtil.strlen(filenameA).rawValue();
        int lengthB = (int) SubstrateUtil.strlen(filenameB).rawValue();
        boolean emergencyChunkA = isEmergencyChunkFilename(filenameA, lengthA);
        boolean emergencyChunkB = isEmergencyChunkFilename(filenameB, lengthB);
        if (emergencyChunkA || emergencyChunkB) {
            assert !(emergencyChunkA && emergencyChunkB) : "repository must not contain multiple emergency chunk files";
            if (emergencyChunkA && emergencyChunkB) {
                return 0;
            }
            return emergencyChunkA ? 1 : -1;
        }

        int cmp = LibC.strncmp(filenameA, filenameB, Word.unsigned(ISO_8601_LEN));
        if (cmp == 0) {
            CCharPointer aDot = SubstrateUtil.strchr(filenameA, DOT);
            CCharPointer bDot = SubstrateUtil.strchr(filenameB, DOT);
            long aLen = aDot.rawValue() - a.rawValue();
            long bLen = bDot.rawValue() - b.rawValue();
            if (aLen < bLen) {
                return -1;
            }
            if (aLen > bLen) {
                return 1;
            }
            cmp = LibC.strncmp(filenameA, filenameB, Word.unsigned(aLen));
        }
        return cmp;
    }

    private void writeEmergencyDumpFile(GrowableWordArray sortedChunkFilenames) {
        int blockSize = 1024 * 1024;
        Pointer copyBlock = NullableNativeMemory.malloc(blockSize, NmtCategory.JFR);
        if (copyBlock.isNull()) {
            SubstrateJVM.getLogging().logJfrSystemError("Unable to malloc memory during jfr emergency dump");
            SubstrateJVM.getLogging().logJfrSystemError("Unable to write jfr emergency dump file");
            return;
        }

        for (int i = 0; i < sortedChunkFilenames.getSize(); i++) {
            CCharPointer fn = (CCharPointer) ((Pointer) GrowableWordArrayAccess.get(sortedChunkFilenames, i));
            RawFileDescriptor chunkFd = openRepositoryFile(fn);
            if (getFileSupport().isValid(chunkFd)) {

                // Read it's size
                long chunkFileSize = getFileSupport().size(chunkFd);
                long bytesRead = 0;
                if (!getFileSupport().seek(chunkFd, 0)) {
                    SubstrateJVM.getLogging().logJfrInfo("Unable to recover JFR data, seek failed.");
                    getFileSupport().close(chunkFd);
                    continue;
                }
                while (bytesRead < chunkFileSize) {
                    // Read from chunk file to copy block
                    long readResult = getFileSupport().read(chunkFd, copyBlock, Word.unsigned(blockSize));
                    if (readResult <= 0) {
                        if (readResult < 0) {
                            SubstrateJVM.getLogging().logJfrInfo("Unable to recover JFR data, read failed.");
                        }
                        break;
                    }
                    bytesRead += readResult;
                    // Write from copy block to dump file
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

    @Uninterruptible(reason = "LibC.errno() must not be overwritten accidentally.")
    private RawFileDescriptor openRepositoryFile(CCharPointer fn) {
        if (directoryFd == -1) {
            return Word.nullPointer();
        }
        // Reopen the validated repository entry relative to the directory we are currently
        // scanning.
        return Word.signed(restartableOpenat(directoryFd, fn, O_RDONLY() | O_NOFOLLOW(), 0));
    }

    private static void freeChunkFilenames(GrowableWordArray chunkFilenames) {
        for (int i = 0; i < chunkFilenames.getSize(); i++) {
            CCharPointer fn = (CCharPointer) ((Pointer) GrowableWordArrayAccess.get(chunkFilenames, i));
            if (fn.isNonNull()) {
                LibC.free(fn);
            }
        }
    }

    private boolean openDirectory() {
        CCharPointer repositoryLocation = getRepositoryLocation();
        if (repositoryLocation.isNull()) {
            SubstrateJVM.getLogging().logJfrSystemError(openDirectoryWarning);
            return false;
        }
        int fd = Fcntl.NoTransitions.restartableOpen(repositoryLocation, O_RDONLY() | O_NOFOLLOW(), 0);
        if (fd == -1) {
            return false;
        }

        directory = Dirent.fdopendir(fd);
        if (directory.isNull()) {
            Unistd.NoTransitions.close(fd);
            SubstrateJVM.getLogging().logJfrSystemError(openDirectoryWarning);
            return false;
        }
        directoryFd = fd;
        return true;
    }

    private CCharPointer getRepositoryLocation() {
        if (repositoryLocationBytes == null || isRepositoryLocationTooLong(repositoryLocationBytes)) {
            return Word.nullPointer();
        }
        clearPathBuffer();
        writeToPathBuffer(repositoryLocationBytes, 0);
        return getPathBuffer();
    }

    @Uninterruptible(reason = "LibC.errno() must not be overwritten accidentally.")
    private static int restartableOpenat(int fd, CCharPointer filename, int flags, int mode) {
        int result;
        do {
            result = Fcntl.NoTransitions.openat(fd, filename, flags, mode);
        } while (result == -1 && LibC.errno() == Errno.EINTR());

        return result;
    }

    private boolean filter(Dirent.dirent entry) {
        CCharPointer fn = entry.d_name();

        // Check filename length
        int filenameLength = (int) SubstrateUtil.strlen(fn).rawValue();
        if (filenameLength <= CHUNKFILE_EXTENSION_BYTES.length) {
            return false;
        }

        // Verify file extension
        for (int i = 0; i < CHUNKFILE_EXTENSION_BYTES.length; i++) {
            int idx1 = CHUNKFILE_EXTENSION_BYTES.length - i - 1;
            int idx2 = filenameLength - i - 1;
            if (CHUNKFILE_EXTENSION_BYTES[idx1] != ((Pointer) fn).readByte(idx2)) {
                return false;
            }
        }
        boolean emergencyChunk = isEmergencyChunkFilename(fn, filenameLength);
        // Merge timestamped repository chunks and the synthetic emergency repository chunk only.
        if (!emergencyChunk && !hasChunkFilenameFormat(fn, filenameLength - CHUNKFILE_EXTENSION_BYTES.length)) {
            return false;
        }

        // Verify it can be opened and receive a valid file descriptor.
        RawFileDescriptor chunkFd = openRepositoryFile(fn);
        if (!getFileSupport().isValid(chunkFd)) {
            return false;
        }

        // Verify file size
        long chunkFileSize = getFileSupport().size(chunkFd);
        getFileSupport().close(chunkFd);
        if (chunkFileSize < CHUNK_FILE_HEADER_SIZE) {
            return false;
        }
        return true;
    }

    private static boolean isEmergencyChunkFilename(CCharPointer fn, int filenameLength) {
        int expectedLength = EMERGENCY_CHUNK_BYTES.length + CHUNKFILE_EXTENSION_BYTES.length;
        if (filenameLength != expectedLength) {
            return false;
        }
        for (int i = 0; i < EMERGENCY_CHUNK_BYTES.length; i++) {
            if (fn.read(i) != EMERGENCY_CHUNK_BYTES[i]) {
                return false;
            }
        }
        for (int i = 0; i < CHUNKFILE_EXTENSION_BYTES.length; i++) {
            if (fn.read(EMERGENCY_CHUNK_BYTES.length + i) != CHUNKFILE_EXTENSION_BYTES[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasChunkFilenameFormat(CCharPointer fn, int baseNameLength) {
        if (baseNameLength < ISO_8601_LEN) {
            return false;
        }
        // Repository chunks are timestamped as yyyy_MM_dd_HH_mm_ss[_NN].jfr.
        for (int i = 0; i < ISO_8601_LEN; i++) {
            byte ch = fn.read(i);
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
        if (fn.read(ISO_8601_LEN) != '_' || baseNameLength == ISO_8601_LEN + 1) {
            return false;
        }
        for (int i = ISO_8601_LEN + 1; i < baseNameLength; i++) {
            byte ch = fn.read(i);
            if (ch < '0' || ch > '9') {
                return false;
            }
        }
        return true;
    }

    private CCharPointer getPathBuffer() {
        return pathBuffer;
    }

    private void clearPathBuffer() {
        LibC.memset(getPathBuffer(), Word.signed(0), Word.unsigned(JVM_MAXPATHLEN));
    }

    private int writeToPathBuffer(byte[] bytes, int start) {
        int idx = start;
        for (int i = 0; i < bytes.length; i++) {
            getPathBuffer().write(idx++, bytes[i]);
        }
        return idx;
    }

    private static boolean isRepositoryLocationTooLong(byte[] repositoryLocation) {
        return repositoryLocation != null && repositoryLocation.length + 1L + EMERGENCY_CHUNK_BYTES.length + CHUNKFILE_EXTENSION_BYTES.length >= JVM_MAXPATHLEN;
    }

    private boolean isDumpPathTooLong(byte[] dumpPath) {
        return dumpPath != null && dumpPath.length + 1L + DUMP_FILE_PREFIX.length + pidBytes.length + CHUNKFILE_EXTENSION_BYTES.length >= JVM_MAXPATHLEN;
    }

    static RawFileOperationSupport getFileSupport() {
        return RawFileOperationSupport.bigEndian();
    }

    private void closeEmergencyDumpFile() {
        if (getFileSupport().isValid(emergencyFd)) {
            getFileSupport().close(emergencyFd);
            emergencyFd = Word.nullPointer();
        }
    }

    private void closeDirectory() {
        if (directory.isNonNull()) {
            Dirent.closedir(directory);
            directory = Word.nullPointer();
        }
        directoryFd = -1;
    }

    @Override
    public void teardown() {
        closeEmergencyDumpFile();
        closeDirectory();
        if (pathBufferInitialized) {
            NativeMemory.free(pathBuffer);
            pathBufferInitialized = false;
        }
    }

    public static class TestingBackdoor {
        public static long getPathBufferAddress(PosixJfrEmergencyDumpSupport support) {
            if (!support.pathBufferInitialized) {
                return 0L;
            }
            return support.pathBuffer.rawValue();
        }

        public static int getEmergencyChunkPathCallCount(PosixJfrEmergencyDumpSupport support) {
            return support.emergencyChunkPathCallCount;
        }

        public static void resetEmergencyChunkPathCallCount(PosixJfrEmergencyDumpSupport support) {
            support.emergencyChunkPathCallCount = 0;
        }
    }
}

@AutomaticallyRegisteredFeature
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, other = PartiallyLayerAware.class)
class PosixJfrEmergencyDumpFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return VMInspectionOptions.hasJfrSupport() && !Platform.includedIn(InternalPlatform.WINDOWS_BASE.class);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(JfrEmergencyDumpSupport.class, new PosixJfrEmergencyDumpSupport());
    }
}
