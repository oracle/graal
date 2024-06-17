/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix;

import java.io.File;
import java.nio.ByteOrder;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.Pointer;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.memory.UntrackedNullableNativeMemory;
import com.oracle.svm.core.os.AbstractRawFileOperationSupport;
import com.oracle.svm.core.os.AbstractRawFileOperationSupport.RawFileOperationSupportHolder;
import com.oracle.svm.core.posix.headers.Errno;
import com.oracle.svm.core.posix.headers.Fcntl;
import com.oracle.svm.core.posix.headers.Unistd;
import com.oracle.svm.core.util.VMError;

public class PosixRawFileOperationSupport extends AbstractRawFileOperationSupport {
    @Platforms(Platform.HOSTED_ONLY.class)
    public PosixRawFileOperationSupport(boolean useNativeByteOrder) {
        super(useNativeByteOrder);
    }

    @Override
    public CCharPointer allocateCPath(String path) {
        byte[] data = path.getBytes();
        CCharPointer filename = UntrackedNullableNativeMemory.malloc(WordFactory.unsigned(data.length + 1));
        if (filename.isNull()) {
            return WordFactory.nullPointer();
        }

        for (int i = 0; i < data.length; i++) {
            filename.write(i, data[i]);
        }
        filename.write(data.length, (byte) 0);
        return filename;
    }

    @Override
    public RawFileDescriptor create(File file, FileCreationMode creationMode, FileAccessMode accessMode) {
        String path = file.getPath();
        int flags = parseMode(creationMode) | parseMode(accessMode);
        return open0(path, flags);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public RawFileDescriptor create(CCharPointer cPath, FileCreationMode creationMode, FileAccessMode accessMode) {
        int flags = parseMode(creationMode) | parseMode(accessMode);
        return open0(cPath, flags);
    }

    @Override
    public RawFileDescriptor open(File file, FileAccessMode mode) {
        String path = file.getPath();
        int flags = parseMode(mode);
        return open0(path, flags);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public RawFileDescriptor open(CCharPointer cPath, FileAccessMode mode) {
        int flags = parseMode(mode);
        return open0(cPath, flags);
    }

    private static RawFileDescriptor open0(String path, int flags) {
        try (CTypeConversion.CCharPointerHolder cPath = CTypeConversion.toCString(path)) {
            return open0(cPath.get(), flags);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static RawFileDescriptor open0(CCharPointer cPath, int flags) {
        int permissions = PosixStat.S_IRUSR() | PosixStat.S_IWUSR();
        return WordFactory.signed(Fcntl.NoTransitions.open(cPath, flags, permissions));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public boolean isValid(RawFileDescriptor fd) {
        int posixFd = getPosixFileDescriptor(fd);
        // > 0 to ensure the default value 0 is invalid on all platforms
        return posixFd > 0;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public boolean close(RawFileDescriptor fd) {
        int posixFd = getPosixFileDescriptor(fd);
        int result = Unistd.NoTransitions.close(posixFd);
        return result == 0;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public long size(RawFileDescriptor fd) {
        int posixFd = getPosixFileDescriptor(fd);
        return PosixStat.getSize(posixFd);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public long position(RawFileDescriptor fd) {
        int posixFd = getPosixFileDescriptor(fd);
        return Unistd.NoTransitions.lseek(posixFd, WordFactory.signed(0), Unistd.SEEK_CUR()).rawValue();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public boolean seek(RawFileDescriptor fd, long position) {
        int posixFd = getPosixFileDescriptor(fd);
        SignedWord newPos = Unistd.NoTransitions.lseek(posixFd, WordFactory.signed(position), Unistd.SEEK_SET());
        return position == newPos.rawValue();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public boolean write(RawFileDescriptor fd, Pointer data, UnsignedWord size) {
        int posixFd = getPosixFileDescriptor(fd);

        Pointer position = data;
        UnsignedWord remaining = size;
        while (remaining.aboveThan(0)) {
            SignedWord writtenBytes = Unistd.NoTransitions.write(posixFd, position, remaining);
            if (writtenBytes.equal(-1)) {
                if (LibC.errno() == Errno.EINTR()) {
                    // Retry the write if it was interrupted before any bytes were written.
                    continue;
                }
                return false;
            }
            position = position.add((UnsignedWord) writtenBytes);
            remaining = remaining.subtract((UnsignedWord) writtenBytes);
        }
        return true;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public long read(RawFileDescriptor fd, Pointer buffer, UnsignedWord bufferSize) {
        int posixFd = getPosixFileDescriptor(fd);

        SignedWord readBytes;
        do {
            readBytes = Unistd.NoTransitions.read(posixFd, buffer, bufferSize);
        } while (readBytes.equal(-1) && LibC.errno() == Errno.EINTR());

        return readBytes.rawValue();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int getPosixFileDescriptor(RawFileDescriptor fd) {
        int result = (int) fd.rawValue();
        assert result == fd.rawValue();
        return result;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int parseMode(FileCreationMode mode) {
        switch (mode) {
            case CREATE:
                return Fcntl.O_CREAT() | Fcntl.O_EXCL();
            case CREATE_OR_REPLACE:
                return Fcntl.O_CREAT() | Fcntl.O_TRUNC();
            default:
                throw VMError.shouldNotReachHereUnexpectedInput(mode); // ExcludeFromJacocoGeneratedReport
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int parseMode(FileAccessMode mode) {
        switch (mode) {
            case READ:
                return Fcntl.O_RDONLY();
            case READ_WRITE:
                return Fcntl.O_RDWR();
            case WRITE:
                return Fcntl.O_WRONLY();
            default:
                throw VMError.shouldNotReachHereUnexpectedInput(mode); // ExcludeFromJacocoGeneratedReport
        }
    }
}

@AutomaticallyRegisteredFeature
class PosixRawFileOperationFeature implements InternalFeature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ByteOrder nativeByteOrder = ByteOrder.nativeOrder();
        assert nativeByteOrder == ByteOrder.LITTLE_ENDIAN || nativeByteOrder == ByteOrder.BIG_ENDIAN;

        PosixRawFileOperationSupport littleEndian = new PosixRawFileOperationSupport(ByteOrder.LITTLE_ENDIAN == nativeByteOrder);
        PosixRawFileOperationSupport bigEndian = new PosixRawFileOperationSupport(ByteOrder.BIG_ENDIAN == nativeByteOrder);
        PosixRawFileOperationSupport nativeOrder = nativeByteOrder == ByteOrder.LITTLE_ENDIAN ? littleEndian : bigEndian;

        ImageSingletons.add(RawFileOperationSupportHolder.class, new RawFileOperationSupportHolder(littleEndian, bigEndian, nativeOrder));
    }
}
