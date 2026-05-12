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
import com.oracle.svm.core.jfr.AbstractJfrEmergencyDumpSupport;
import com.oracle.svm.core.jfr.JfrEmergencyDumpSupport;
import com.oracle.svm.core.os.RawFileOperationSupport.RawFileDescriptor;
import com.oracle.svm.core.posix.headers.Dirent;
import com.oracle.svm.core.posix.headers.Fcntl;
import com.oracle.svm.core.posix.headers.Time;
import com.oracle.svm.core.posix.headers.Unistd;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.PartiallyLayerAware;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.Duplicable;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.SubstrateUtil;

import jdk.graal.compiler.core.common.NumUtil;

/**
 * POSIX implementation of JFR emergency dump support. Directory scanning opens the
 * repository once and reopens chunk files relative to that validated directory.
 */
@SingletonTraits(access = AllAccess.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = Duplicable.class, other = PartiallyLayerAware.class)
public class PosixJfrEmergencyDumpSupport extends AbstractJfrEmergencyDumpSupport {
    private static final byte FILE_SEPARATOR = '/';

    private Dirent.DIR directory;
    private int directoryFd;

    @Platforms(Platform.HOSTED_ONLY.class)
    public PosixJfrEmergencyDumpSupport() {
    }

    @Override
    protected void initializeRepositoryState() {
        directory = Word.nullPointer();
        directoryFd = -1;
    }

    @Override
    protected byte[] toBytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected int elementSize() {
        return Byte.BYTES;
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
            int filenameLength = UnsignedUtils.safeToInt(SubstrateUtil.strlen(fn));
            if (addUsableChunkFilename(gwa, (Pointer) fn, filenameLength)) {
                count++;
            }
        }
        sortChunkFilenames(gwa, count);
    }

    @Uninterruptible(reason = "LibC.errno() must not be overwritten accidentally.")
    @Override
    protected RawFileDescriptor openRepositoryFile(Pointer filename) {
        CCharPointer fn = (CCharPointer) filename;
        if (directoryFd == -1) {
            return Word.nullPointer();
        }
        // Reopen the validated repository entry relative to the directory we are currently
        // scanning.
        return Word.signed(Fcntl.NoTransitions.restartableOpenat(directoryFd, fn, O_RDONLY() | O_NOFOLLOW(), 0));
    }

    @Override
    protected boolean openRepository() {
        CCharPointer repositoryLocation = getRepositoryLocation();
        if (repositoryLocation.isNull()) {
            logOpenDirectoryWarning();
            return false;
        }

        int fd = Fcntl.NoTransitions.restartableOpen(repositoryLocation, O_RDONLY() | O_NOFOLLOW(), 0);
        if (fd == -1) {
            return false;
        }

        directory = Dirent.fdopendir(fd);
        if (directory.isNull()) {
            Unistd.NoTransitions.close(fd);
            logOpenDirectoryWarning();
            return false;
        }
        directoryFd = fd;
        return true;
    }

    private CCharPointer getRepositoryLocation() {
        if (isRepositoryLocationTooLong()) {
            return Word.nullPointer();
        }
        int idx = appendRepositoryLocationToPathBuffer(0);
        writePathBufferElement(idx, (char) 0);
        return getPathBuffer();
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

    private CCharPointer getPathBuffer() {
        return (CCharPointer) pathBuffer;
    }

    @Override
    protected char getFileSeparator() {
        return FILE_SEPARATOR;
    }

    @Override
    protected boolean isFileSeparator(char ch) {
        return ch == FILE_SEPARATOR;
    }

    @Override
    protected void writeElement(Pointer buffer, int idx, char ch) {
        byte b = NumUtil.safeToUByte(ch);
        buffer.writeByte(idx, b);
    }

    @Override
    protected char readElement(Pointer buffer, int index) {
        return (char) (buffer.readByte(index) & 0xFF);
    }

    @Override
    protected void closeRepository() {
        if (directory.isNonNull()) {
            Dirent.closedir(directory);
            directory = Word.nullPointer();
        }
        directoryFd = -1;
    }
}

@AutomaticallyRegisteredFeature
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
