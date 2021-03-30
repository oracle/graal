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
import java.util.Objects;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.Pointer;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.CErrorNumber;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.os.RawFileOperationSupport;
import com.oracle.svm.core.posix.headers.Errno;
import com.oracle.svm.core.posix.headers.Fcntl;
import com.oracle.svm.core.posix.headers.Unistd;

public class PosixRawFileOperationSupport extends RawFileOperationSupport {
    private static final int DEFAULT_PERMISSIONS = 0666;

    @Override
    public RawFileDescriptor open(File file, FileAccessMode mode) {
        String path = file.getPath();
        int flags = parseMode(mode);

        try (CTypeConversion.CCharPointerHolder cPath = CTypeConversion.toCString(path)) {
            return WordFactory.signed(Fcntl.NoTransitions.open(cPath.get(), flags, DEFAULT_PERMISSIONS));
        }
    }

    @Override
    public boolean isValid(RawFileDescriptor fd) {
        int posixFd = getPosixFileDescriptor(fd);
        return posixFd >= 0;
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
            SignedWord writtenBytes;
            do {
                writtenBytes = Unistd.NoTransitions.write(posixFd, position, remaining);
            } while (writtenBytes.equal(-1) && CErrorNumber.getCErrorNumber() == Errno.EINTR());

            if (writtenBytes.equal(-1)) {
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
        } while (readBytes.equal(-1) && CErrorNumber.getCErrorNumber() == Errno.EINTR());

        return readBytes;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int getPosixFileDescriptor(RawFileDescriptor fd) {
        int result = (int) fd.rawValue();
        assert result == fd.rawValue();
        return result;
    }

    private static int parseMode(FileAccessMode mode) {
        if (mode == FileAccessMode.READ) {
            return Fcntl.O_RDONLY();
        } else if (mode == FileAccessMode.READ_WRITE) {
            return Fcntl.O_RDWR() | Fcntl.O_CREAT();
        } else if (mode == FileAccessMode.WRITE) {
            return Fcntl.O_WRONLY() | Fcntl.O_CREAT();
        }

        throw new IllegalArgumentException("Illegal file access mode '" + Objects.toString(mode) + "'.");
    }
}

@AutomaticFeature
class PosixRawFileOperationFeature implements Feature {
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        ImageSingletons.add(RawFileOperationSupport.class, new PosixRawFileOperationSupport());
    }
}
