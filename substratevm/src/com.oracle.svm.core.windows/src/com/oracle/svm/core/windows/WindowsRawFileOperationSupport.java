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
package com.oracle.svm.core.windows;

import java.io.File;
import java.nio.ByteOrder;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.IOHelper;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.os.AbstractRawFileOperationSupport;
import com.oracle.svm.core.os.AbstractRawFileOperationSupport.RawFileOperationSupportHolder;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.core.windows.headers.IO;

public class WindowsRawFileOperationSupport extends AbstractRawFileOperationSupport {
    @Platforms(Platform.HOSTED_ONLY.class)
    public WindowsRawFileOperationSupport(boolean useNativeByteOrder) {
        super(useNativeByteOrder);
    }

    @Override
    public RawFileDescriptor create(File file, FileCreationMode creationMode, FileAccessMode accessMode) {
        String path = file.getPath();
        int flags = parseMode(creationMode) | parseMode(accessMode) | IO._O_BINARY();
        return open0(path, flags);
    }

    @Override
    public RawFileDescriptor open(File file, FileAccessMode mode) {
        String path = file.getPath();
        int flags = parseMode(mode) | IO._O_BINARY();
        return open0(path, flags);
    }

    private static RawFileDescriptor open0(String path, int flags) {
        try (CTypeConversion.CCharPointerHolder cPath = CTypeConversion.toCString(path)) {
            return WordFactory.signed(IOHelper.openFile(cPath.get(), flags, IO._S_IREAD() | IO._S_IWRITE()));
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public boolean isValid(RawFileDescriptor fd) {
        int windowsFd = getWindowsFileDescriptor(fd);
        /* > 0 to ensure the default value 0 is invalid on all platforms */
        return windowsFd > 0;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public boolean close(RawFileDescriptor fd) {
        int windowsFd = getWindowsFileDescriptor(fd);
        int result = IO.NoTransitions._close(windowsFd);
        return result == 0;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public long size(RawFileDescriptor fd) {
        int windowsFd = getWindowsFileDescriptor(fd);
        return IO.NoTransitions._filelengthi64(windowsFd);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public long position(RawFileDescriptor fd) {
        int windowsFd = getWindowsFileDescriptor(fd);
        return IO.NoTransitions._lseeki64(windowsFd, 0L, IO.SEEK_CUR());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public boolean seek(RawFileDescriptor fd, long position) {
        assert position >= 0;
        int windowsFd = getWindowsFileDescriptor(fd);
        long actualPosition = IO.NoTransitions._lseeki64(windowsFd, position, IO.SEEK_SET());
        return position == actualPosition;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public boolean write(RawFileDescriptor fd, Pointer data, UnsignedWord size) {
        int windowsFd = getWindowsFileDescriptor(fd);

        Pointer pos = data;
        UnsignedWord remaining = size;
        while (remaining.notEqual(0)) {
            int uBytesToWrite = WindowsUtils.toUnsignedIntOrMaxValue(remaining);
            int ret = IO.NoTransitions._write(windowsFd, pos, uBytesToWrite);
            if (ret == -1) {
                return false;
            }

            UnsignedWord writtenBytes = WindowsUtils.unsignedIntToUnsignedWord(ret);
            pos = pos.add(writtenBytes);
            remaining = remaining.subtract(writtenBytes);
        }
        return true;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public long read(RawFileDescriptor fd, Pointer buffer, UnsignedWord bufferSize) {
        int windowsFd = getWindowsFileDescriptor(fd);
        int uMaxBytes = WindowsUtils.toUnsignedIntOrMaxValue(bufferSize);
        int readBytes = IO.NoTransitions._read(windowsFd, buffer, uMaxBytes);
        if (readBytes == -1) {
            return -1L;
        }
        return WindowsUtils.unsignedIntToLong(readBytes);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int getWindowsFileDescriptor(RawFileDescriptor fd) {
        int result = (int) fd.rawValue();
        assert result == fd.rawValue();
        return result;
    }

    private static int parseMode(FileCreationMode mode) {
        return switch (mode) {
            case CREATE -> IO._O_CREAT() | IO._O_EXCL();
            case CREATE_OR_REPLACE -> IO._O_CREAT() | IO._O_TRUNC();
            default -> throw VMError.shouldNotReachHere();
        };
    }

    private static int parseMode(FileAccessMode mode) {
        return switch (mode) {
            case READ -> IO._O_RDONLY();
            case READ_WRITE -> IO._O_RDWR();
            case WRITE -> IO._O_WRONLY();
            default -> throw VMError.shouldNotReachHere();
        };
    }
}

@AutomaticallyRegisteredFeature
class WindowsRawFileOperationFeature implements InternalFeature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ByteOrder nativeByteOrder = ByteOrder.nativeOrder();
        assert nativeByteOrder == ByteOrder.LITTLE_ENDIAN || nativeByteOrder == ByteOrder.BIG_ENDIAN;

        WindowsRawFileOperationSupport littleEndian = new WindowsRawFileOperationSupport(ByteOrder.LITTLE_ENDIAN == nativeByteOrder);
        WindowsRawFileOperationSupport bigEndian = new WindowsRawFileOperationSupport(ByteOrder.BIG_ENDIAN == nativeByteOrder);
        WindowsRawFileOperationSupport nativeOrder = nativeByteOrder == ByteOrder.LITTLE_ENDIAN ? littleEndian : bigEndian;

        ImageSingletons.add(RawFileOperationSupportHolder.class, new RawFileOperationSupportHolder(littleEndian, bigEndian, nativeOrder));
    }
}
