/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.Pointer;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.os.AbstractRawFileOperationSupport;
import com.oracle.svm.core.os.AbstractRawFileOperationSupport.RawFileOperationSupportHolder;
import com.oracle.svm.core.posix.headers.Errno;
import com.oracle.svm.core.posix.headers.Fcntl;
import com.oracle.svm.core.posix.headers.Unistd;
import com.oracle.svm.core.util.VMError;

public class PosixRawFileOperationSupport extends AbstractRawFileOperationSupport {
    private static final int DEFAULT_PERMISSIONS = 0666;

    @Platforms(Platform.HOSTED_ONLY.class)
    public PosixRawFileOperationSupport(boolean useNativeByteOrder) {
        super(useNativeByteOrder);
    }

    @Override
    public RawFileDescriptor open(File file, FileAccessMode mode) {
        String path = file.getPath();
        int flags = parseMode(mode);

        try (CTypeConversion.CCharPointerHolder cPath = CTypeConversion.toCString(path)) {
            return WordFactory.signed(Fcntl.NoTransitions.open(cPath.get(), flags, DEFAULT_PERMISSIONS));
        }
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
    public SignedWord size(RawFileDescriptor fd) {
        int posixFd = getPosixFileDescriptor(fd);
        return PosixStat.getSize(posixFd);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public SignedWord position(RawFileDescriptor fd) {
        int posixFd = getPosixFileDescriptor(fd);
        return Unistd.NoTransitions.lseek(posixFd, WordFactory.signed(0), Unistd.SEEK_CUR());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public boolean seek(RawFileDescriptor fd, SignedWord position) {
        int posixFd = getPosixFileDescriptor(fd);
        SignedWord newPos = Unistd.NoTransitions.lseek(posixFd, position, Unistd.SEEK_SET());
        return position.equal(newPos);
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
    public SignedWord read(RawFileDescriptor fd, Pointer buffer, UnsignedWord bufferSize) {
        int posixFd = getPosixFileDescriptor(fd);

        SignedWord readBytes;
        do {
            readBytes = Unistd.NoTransitions.read(posixFd, buffer, bufferSize);
        } while (readBytes.equal(-1) && LibC.errno() == Errno.EINTR());

        return readBytes;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int getPosixFileDescriptor(RawFileDescriptor fd) {
        int result = (int) fd.rawValue();
        assert result == fd.rawValue();
        return result;
    }

    private static int parseMode(FileAccessMode mode) {
        switch (mode) {
            case READ:
                return Fcntl.O_RDONLY();
            case READ_WRITE:
                return Fcntl.O_RDWR() | Fcntl.O_CREAT();
            case WRITE:
                return Fcntl.O_WRONLY() | Fcntl.O_CREAT();
            default:
                throw VMError.shouldNotReachHere();
        }
    }
}

@AutomaticallyRegisteredFeature
class PosixRawFileOperationFeature implements InternalFeature {
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        ByteOrder nativeByteOrder = ByteOrder.nativeOrder();
        assert nativeByteOrder == ByteOrder.LITTLE_ENDIAN || nativeByteOrder == ByteOrder.BIG_ENDIAN;

        PosixRawFileOperationSupport littleEndianSupport = new PosixRawFileOperationSupport(ByteOrder.LITTLE_ENDIAN == nativeByteOrder);
        PosixRawFileOperationSupport bigEndianSupport = new PosixRawFileOperationSupport(ByteOrder.BIG_ENDIAN == nativeByteOrder);
        PosixRawFileOperationSupport nativeByteOrderSupport = nativeByteOrder == ByteOrder.LITTLE_ENDIAN ? littleEndianSupport : bigEndianSupport;

        ImageSingletons.add(RawFileOperationSupportHolder.class, new RawFileOperationSupportHolder(littleEndianSupport, bigEndianSupport, nativeByteOrderSupport));
    }
}
