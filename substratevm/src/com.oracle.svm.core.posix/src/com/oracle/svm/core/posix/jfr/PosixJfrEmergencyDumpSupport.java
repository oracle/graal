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

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.VMInspectionOptions;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.posix.headers.Dirent;
import com.oracle.svm.core.posix.headers.Errno;
import com.oracle.svm.core.posix.headers.Fcntl;
import com.oracle.svm.core.posix.headers.Unistd;
import org.graalvm.word.Pointer;
import jdk.graal.compiler.word.Word;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;

import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jfr.JfrEmergencyDumpSupport;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.memory.NativeMemory;
import com.oracle.svm.core.memory.NullableNativeMemory;
import com.oracle.svm.core.os.RawFileOperationSupport;
import com.oracle.svm.core.os.RawFileOperationSupport.FileAccessMode;
import com.oracle.svm.core.os.RawFileOperationSupport.FileCreationMode;
import com.oracle.svm.core.os.RawFileOperationSupport.RawFileDescriptor;
import com.oracle.svm.core.util.BasedOnJDKFile;
import com.oracle.svm.core.collections.GrowableWordArray;
import com.oracle.svm.core.collections.GrowableWordArrayAccess;

import jdk.graal.compiler.api.replacements.Fold;

import java.nio.charset.StandardCharsets;

import static com.oracle.svm.core.posix.headers.Fcntl.O_NOFOLLOW;
import static com.oracle.svm.core.posix.headers.Fcntl.O_RDONLY;

public class PosixJfrEmergencyDumpSupport implements com.oracle.svm.core.jfr.JfrEmergencyDumpSupport {
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+2/src/hotspot/share/jfr/recorder/repository/jfrEmergencyDump.cpp#L49") //
    private static final int CHUNK_FILE_HEADER_SIZE = 68;
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+2/src/hotspot/os/posix/include/jvm_md.h#L57") //
    private static final int JVM_MAXPATHLEN = 4096;
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+2/src/hotspot/share/jfr/recorder/repository/jfrEmergencyDump.cpp#L47") //
    private static final int ISO_8601_LEN = 19;
    private static final byte FILE_SEPARATOR = '/';
    private static final byte DOT = '.';
    // It does not really matter what the name is.
    private static final byte[] EMERGENCY_CHUNK_BYTES = "emergency_chunk".getBytes(StandardCharsets.UTF_8);
    private static final byte[] DUMP_FILE_PREFIX = "svm_oom_pid_".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CHUNKFILE_EXTENSION_BYTES = ".jfr".getBytes(StandardCharsets.UTF_8);
    private Dirent.DIR directory;
    private byte[] pidBytes;
    private byte[] dumpPathBytes;
    private byte[] repositoryLocationBytes;
    private byte[] cwdBytes;
    private RawFileDescriptor emergencyFd;
    private CCharPointer pathBuffer;
    private String openFileWarning;
    private String openDirectoryWarning;

    @Platforms(Platform.HOSTED_ONLY.class)
    public PosixJfrEmergencyDumpSupport() {
    }

    @Override
    public void initialize() {
        pidBytes = Long.toString(ProcessHandle.current().pid()).getBytes(StandardCharsets.UTF_8);
        pathBuffer = NativeMemory.calloc(JVM_MAXPATHLEN + 1, NmtCategory.JFR);
        directory = Word.nullPointer();
        saveCwd();
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
        openDirectoryWarning = "Unable to open repository " + dirText;
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
            openFileWarning = "Unable to create an emergency dump file at the location set by dumppath=" + new String(dumpPathBytes, StandardCharsets.UTF_8);
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
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+3/src/hotspot/share/jfr/recorder/repository/jfrEmergencyDump.cpp#L433-L445")
    public RawFileDescriptor chunkPath() {
        if (repositoryLocationBytes == null) {
            openEmergencyDumpFile();
            /*
             * We can directly use the emergency dump file name as the new chunk since there are no
             * other chunk files.
             */
            return emergencyFd;
        }
        return createEmergencyChunkPath();
    }

    /**
     * The normal chunkfile name format is: repository path + file separator + date time +
     * extension. In this case we just use a hardcoded string instead of date time, which will
     * successfully rank last in lexographic order among other chunkfile names.
     */
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+3/src/hotspot/share/jfr/recorder/repository/jfrEmergencyDump.cpp#L418-L431")
    private RawFileDescriptor createEmergencyChunkPath() {
        clearPathBuffer();
        int idx = 0;
        idx = writeToPathBuffer(repositoryLocationBytes, idx);
        getPathBuffer().write(idx++, FILE_SEPARATOR);
        idx = writeToPathBuffer(EMERGENCY_CHUNK_BYTES, idx);
        idx = writeToPathBuffer(CHUNKFILE_EXTENSION_BYTES, idx);
        getPathBuffer().write(idx++, (byte) 0);
        return getFileSupport().create(getPathBuffer(), FileCreationMode.CREATE, FileAccessMode.READ_WRITE);
    }

    @Override
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+2/src/hotspot/share/jfr/recorder/repository/jfrEmergencyDump.cpp#L409-L416")
    public void onVmError() {
        if (openEmergencyDumpFile()) {
            GrowableWordArray sortedChunkFilenames = StackValue.get(GrowableWordArray.class);
            GrowableWordArrayAccess.initialize(sortedChunkFilenames);
            try {
                iterateRepository(sortedChunkFilenames);
                writeEmergencyDumpFile(sortedChunkFilenames);
                closeEmergencyDumpFile();
            } finally {
                GrowableWordArrayAccess.freeData(sortedChunkFilenames);
                sortedChunkFilenames = Word.nullPointer();
            }
        }
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+2/src/hotspot/share/jfr/recorder/repository/jfrEmergencyDump.cpp#L131-L146")
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+2/src/hotspot/share/jfr/recorder/repository/jfrEmergencyDump.cpp#L86-L89")
    private boolean openEmergencyDumpFile() {
        if (getFileSupport().isValid(emergencyFd)) {
            return true;
        }
        // O_CREAT | O_RDWR and S_IREAD | S_IWRITE permissions
        emergencyFd = getFileSupport().create(createEmergencyDumpPath(), FileCreationMode.CREATE, FileAccessMode.READ_WRITE);
        if (!getFileSupport().isValid(emergencyFd)) {
            SubstrateJVM.getLogging().logJfrWarning(openFileWarning);
            // Fallback. Try to create it in the current directory.
            dumpPathBytes = null;
            emergencyFd = getFileSupport().create(createEmergencyDumpPath(), FileCreationMode.CREATE, FileAccessMode.READ_WRITE);
        }
        return getFileSupport().isValid(emergencyFd);
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+2/src/hotspot/share/jfr/recorder/repository/jfrEmergencyDump.cpp#L110-L129")
    private CCharPointer createEmergencyDumpPath() {
        int idx = 0;
        clearPathBuffer();

        if (dumpPathBytes == null) {
            dumpPathBytes = cwdBytes;
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

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+3/src/hotspot/share/jfr/recorder/repository/jfrEmergencyDump.cpp#L310-L345")
    private void iterateRepository(GrowableWordArray gwa) {
        int count = 0;
        // Open directory
        if (openDirectory()) {
            if (directory.isNull()) {
                return;
            }
            // Iterate files in the repository and append filtered file names to the files array
            Dirent.dirent entry;
            while ((entry = Dirent.readdir(directory)).isNonNull()) {
                // Filter files
                CCharPointer fn = entry.d_name();
                if (filter(fn)) {
                    // Append filtered files to list
                    if (!GrowableWordArrayAccess.add(gwa, (Word) fn, NmtCategory.JFR)) {
                        SubstrateJVM.getLogging().logJfrSystemError("Unable to add chunk filename to list during jfr emergency dump");
                    }
                    count++;
                }
            }
            closeDirectory();
            if (count > 0) {
                GrowableWordArrayAccess.qsort(gwa, 0, count - 1, PosixJfrEmergencyDumpSupport::compare);
            }
        }
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+2/src/hotspot/share/jfr/recorder/repository/jfrEmergencyDump.cpp#L191-L212")
    static int compare(Word a, Word b) {
        CCharPointer filenameA = (CCharPointer) a;
        CCharPointer filenameB = (CCharPointer) b;
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
            CCharPointer fn = (CCharPointer) GrowableWordArrayAccess.get(sortedChunkFilenames, i);
            RawFileDescriptor chunkFd = getFileSupport().open(fullyQualified(fn), FileAccessMode.READ_WRITE);
            if (getFileSupport().isValid(chunkFd)) {

                // Read it's size
                long chunkFileSize = getFileSupport().size(chunkFd);
                long bytesRead = 0;
                long bytesWritten = 0;
                while (bytesRead < chunkFileSize) {
                    // Start at beginning
                    getFileSupport().seek(chunkFd, 0);
                    // Read from chunk file to copy block
                    long readResult = getFileSupport().read(chunkFd, copyBlock, Word.unsigned(blockSize));
                    if (readResult < 0) { // -1 if read failed
                        SubstrateJVM.getLogging().logJfrInfo("Unable to recover JFR data, read failed.");
                        break;
                    }
                    bytesRead += readResult;
                    assert bytesRead - bytesWritten <= blockSize;
                    // Write from copy block to dump file
                    if (!getFileSupport().write(emergencyFd, copyBlock, Word.unsigned(bytesRead - bytesWritten))) {
                        SubstrateJVM.getLogging().logJfrInfo("Unable to recover JFR data, write failed.");
                        break;
                    }
                    bytesWritten = bytesRead;
                }
                getFileSupport().close(chunkFd);
            }
        }
        NullableNativeMemory.free(copyBlock);
    }

    private boolean openDirectory() {
        int fd = Fcntl.NoTransitions.restartableOpen(getRepositoryLocation(), O_RDONLY() | O_NOFOLLOW(), 0);
        if (fd == -1) {
            return false;
        }

        directory = Dirent.fdopendir(fd);
        if (directory.isNull()) {
            Unistd.NoTransitions.close(fd);
            SubstrateJVM.getLogging().logJfrSystemError(openDirectoryWarning);
            return false;
        }
        return true;
    }

    private CCharPointer getRepositoryLocation() {
        clearPathBuffer();
        writeToPathBuffer(repositoryLocationBytes, 0);
        return getPathBuffer();
    }

    /**
     * See com.oracle.svm.core.posix.jvmstat.PosixPerfMemoryProvider#restartableOpen(CCharPointer,
     * int, int).
     */
    @Uninterruptible(reason = "LibC.errno() must not be overwritten accidentally.")
    private static int restartableOpen(CCharPointer directory, int flags, int mode) {
        int result;
        do {
            result = Fcntl.NoTransitions.open(directory, flags, mode);
        } while (result == -1 && LibC.errno() == Errno.EINTR());

        return result;
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+2/src/hotspot/share/jfr/recorder/repository/jfrEmergencyDump.cpp#L276-L308")
    private boolean filter(CCharPointer fn) {

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

        // Verify it can be opened and receive a valid file descriptor
        RawFileDescriptor chunkFd = getFileSupport().open(fullyQualified(fn), FileAccessMode.READ_WRITE);
        if (!getFileSupport().isValid(chunkFd)) {
            return false;
        }

        // Verify file size
        long chunkFileSize = getFileSupport().size(chunkFd);
        if (chunkFileSize < CHUNK_FILE_HEADER_SIZE) {
            return false;
        }
        getFileSupport().close(chunkFd);
        return true;
    }

    /**
     * Given a chunk file name, it returns the fully qualified filename.
     */
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+2/src/hotspot/share/jfr/recorder/repository/jfrEmergencyDump.cpp#L263-L273")
    private CCharPointer fullyQualified(CCharPointer fn) {
        long fnLength = SubstrateUtil.strlen(fn).rawValue();
        int idx = 0;

        clearPathBuffer();

        // Cached in RepositoryIterator::RepositoryIterator and used in fully_qualified
        idx = writeToPathBuffer(repositoryLocationBytes, idx);

        // Add delimiter
        getPathBuffer().write(idx++, FILE_SEPARATOR);

        for (int i = 0; i < fnLength; i++) {
            getPathBuffer().write(idx++, fn.read(i));
        }
        getPathBuffer().write(idx++, (byte) 0);
        return getPathBuffer();
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

    @Fold
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
    }

    @Override
    public void teardown() {
        closeEmergencyDumpFile();
        closeDirectory();
        NativeMemory.free(pathBuffer);
    }
}

@AutomaticallyRegisteredFeature
class PosixJfrEmergencyDumpFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return VMInspectionOptions.hasJfrSupport();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(JfrEmergencyDumpSupport.class, new PosixJfrEmergencyDumpSupport());
    }
}
