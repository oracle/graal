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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.VMInspectionOptions;
import com.oracle.svm.core.collections.GrowableWordArray;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.jfr.AbstractJfrEmergencyDumpSupport;
import com.oracle.svm.core.jfr.JfrEmergencyDumpSupport;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.memory.NativeMemory;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.os.RawFileOperationSupport.RawFileDescriptor;
import com.oracle.svm.core.os.RawFileOperationSupport.RawFilePath;
import com.oracle.svm.core.posix.headers.Dirent;
import com.oracle.svm.core.posix.headers.Errno;
import com.oracle.svm.core.posix.headers.Fcntl;
import com.oracle.svm.core.posix.headers.Time;
import com.oracle.svm.core.posix.headers.Unistd;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.PartiallyLayerAware;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.Duplicable;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.SubstrateUtil;

@SingletonTraits(access = AllAccess.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = Duplicable.class, other = PartiallyLayerAware.class)
public class PosixJfrEmergencyDumpSupport extends AbstractJfrEmergencyDumpSupport {
    private static final byte FILE_SEPARATOR = '/';
    private Dirent.DIR directory;
    private int directoryFd;
    private byte[] pidBytes;
    private byte[] cwdBytes;
    private byte[] dumpPathBytes;
    private byte[] repositoryLocationBytes;
    private CCharPointer pathBuffer;
    private boolean pathBufferInitialized;

    @Platforms(Platform.HOSTED_ONLY.class)
    public PosixJfrEmergencyDumpSupport() {
    }

    @Override
    protected void allocatePathBufferIfNeeded() {
        if (!pathBufferInitialized) {
            pathBuffer = NativeMemory.calloc(JVM_MAXPATHLEN + 1, NmtCategory.JFR);
            pathBufferInitialized = true;
        }
    }

    @Override
    protected void initializeRepositoryState() {
        directory = Word.nullPointer();
        directoryFd = -1;
    }

    @Override
    protected void setPid(String pid) {
        pidBytes = pid.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void setSavedCwdText(String cwd) {
        cwdBytes = cwd.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void setDumpPathText(String dumpPath) {
        dumpPathBytes = dumpPath == null ? null : dumpPath.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void setDumpPathToSavedCwd() {
        dumpPathBytes = cwdBytes;
    }

    @Override
    protected void setRepositoryLocationText(String repositoryLocation) {
        repositoryLocationBytes = repositoryLocation.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected long getPathBufferAddress() {
        if (!pathBufferInitialized) {
            return 0L;
        }
        return pathBuffer.rawValue();
    }

    @Override
    protected int pidLength() {
        return pidBytes.length;
    }

    @Override
    protected int dumpPathLength() {
        return dumpPathBytes == null ? -1 : dumpPathBytes.length;
    }

    @Override
    protected int repositoryLocationLength() {
        return repositoryLocationBytes == null ? -1 : repositoryLocationBytes.length;
    }

    @Override
    protected void iterateRepository(GrowableWordArray gwa) {
        int count = 0;
        if (directory.isNull()) {
            return;
        }
        // Iterate files in the repository and append filtered file names to the files array.
        Dirent.dirent entry;
        while ((entry = Dirent.readdir(directory)).isNonNull()) {
            CCharPointer fn = entry.d_name();
            int filenameLength = (int) SubstrateUtil.strlen(fn).rawValue();
            if (addUsableChunkFilename(gwa, (Word) (Pointer) fn, filenameLength)) {
                count++;
            }
        }
        sortChunkFilenames(gwa, count);
    }

    @Uninterruptible(reason = "LibC.errno() must not be overwritten accidentally.")
    @Override
    protected RawFileDescriptor openRepositoryFile(Word filename) {
        return openRepositoryFile((CCharPointer) ((Pointer) filename));
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

    @Override
    protected boolean openRepository() {
        CCharPointer repositoryLocation = getRepositoryLocation();
        if (repositoryLocation.isNull()) {
            SubstrateJVM.getLogging().logJfrSystemError(getOpenDirectoryWarning());
            return false;
        }
        int fd = Fcntl.NoTransitions.restartableOpen(repositoryLocation, O_RDONLY() | O_NOFOLLOW(), 0);
        if (fd == -1) {
            return false;
        }

        directory = Dirent.fdopendir(fd);
        if (directory.isNull()) {
            Unistd.NoTransitions.close(fd);
            SubstrateJVM.getLogging().logJfrSystemError(getOpenDirectoryWarning());
            return false;
        }
        directoryFd = fd;
        return true;
    }

    private CCharPointer getRepositoryLocation() {
        if (repositoryLocationBytes == null || isRepositoryLocationTooLong()) {
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

    @Override
    protected int appendCurrentDateTimeToPathBuffer(int idx) {
        Time.timeval time = StackValue.get(Time.timeval.class);
        if (Time.NoTransitions.gettimeofday(time, Word.nullPointer()) != 0) {
            return -1;
        }
        Time.tm localTime = StackValue.get(Time.tm.class);
        if (Time.NoTransitions.localtime_r(time.addressOftv_sec(), localTime).isNull()) {
            return -1;
        }
        return writeDateTimeToPathBuffer(idx, localTime.tm_year() + 1900, localTime.tm_mon() + 1, localTime.tm_mday(), localTime.tm_hour(), localTime.tm_min(), localTime.tm_sec());
    }

    @Override
    protected RawFilePath pathBuffer() {
        return (RawFilePath) getPathBuffer();
    }

    private CCharPointer getPathBuffer() {
        return pathBuffer;
    }

    @Override
    protected void clearPathBuffer() {
        LibC.memset(getPathBuffer(), Word.signed(0), Word.unsigned(JVM_MAXPATHLEN));
    }

    @Override
    protected int appendDumpPathToPathBuffer(int idx) {
        return writeToPathBuffer(dumpPathBytes, idx);
    }

    @Override
    protected int appendRepositoryLocationToPathBuffer(int idx) {
        return writeToPathBuffer(repositoryLocationBytes, idx);
    }

    @Override
    protected int appendPidToPathBuffer(int idx) {
        return writeToPathBuffer(pidBytes, idx);
    }

    @Override
    protected int appendPathSeparatorToPathBuffer(int idx) {
        getPathBuffer().write(idx, FILE_SEPARATOR);
        return idx + 1;
    }

    private int writeToPathBuffer(byte[] bytes, int start) {
        int idx = start;
        for (int i = 0; i < bytes.length; i++) {
            getPathBuffer().write(idx++, bytes[i]);
        }
        return idx;
    }

    @Override
    protected int filenameCharAt(Word filename, int index) {
        return filename.readByte(index);
    }

    @Override
    protected Word copyChunkFilename(Word filename, int filenameLength) {
        return (Word) (Pointer) LibC.strdup((CCharPointer) ((Pointer) filename));
    }

    @Override
    protected void writePathBufferChar(int index, int ch) {
        getPathBuffer().write(index, (byte) ch);
    }

    @Override
    protected void freeChunkFilename(Word filename) {
        LibC.free(filename);
    }

    @Override
    protected void closeRepository() {
        if (directory.isNonNull()) {
            Dirent.closedir(directory);
            directory = Word.nullPointer();
        }
        directoryFd = -1;
    }

    @Override
    protected void freePathBufferIfInitialized() {
        if (pathBufferInitialized) {
            NativeMemory.free(pathBuffer);
            pathBuffer = Word.nullPointer();
            pathBufferInitialized = false;
        }
    }
}

@AutomaticallyRegisteredFeature
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, other = PartiallyLayerAware.class)
class PosixJfrEmergencyDumpFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return VMInspectionOptions.hasJfrSupport() && (Platform.includedIn(Platform.LINUX.class) || Platform.includedIn(Platform.DARWIN.class));
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(JfrEmergencyDumpSupport.class, new PosixJfrEmergencyDumpSupport());
    }
}
