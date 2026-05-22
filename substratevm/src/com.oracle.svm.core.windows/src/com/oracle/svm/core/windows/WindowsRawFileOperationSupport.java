/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.windows;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import java.io.File;
import java.nio.ByteOrder;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CLongPointer;
import org.graalvm.nativeimage.impl.InternalPlatform.WINDOWS_BASE;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.jdk.SystemPropertiesSupport;
import com.oracle.svm.core.memory.UntrackedNullableNativeMemory;
import com.oracle.svm.core.os.AbstractRawFileOperationSupport;
import com.oracle.svm.core.os.AbstractRawFileOperationSupport.RawFileOperationSupportHolder;
import com.oracle.svm.core.os.RawFileOperationSupport;
import com.oracle.svm.core.windows.headers.FileAPI;
import com.oracle.svm.core.windows.headers.WinBase;
import com.oracle.svm.core.windows.headers.WinBase.HANDLE;
import com.oracle.svm.core.windows.headers.WindowsLibC.WCharPointer;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.VMError;

/**
 * Raw file operations for Windows. File paths are stored as null-terminated UTF-16 strings so that
 * uninterruptible callers can open files without allocating or converting path data.
 */
public class WindowsRawFileOperationSupport extends AbstractRawFileOperationSupport {

    @Platforms(Platform.HOSTED_ONLY.class)
    public WindowsRawFileOperationSupport(boolean useNativeByteOrder) {
        super(useNativeByteOrder);
    }

    @Override
    public RawFileOperationSupport.RawFilePath allocatePath(String path) {
        int length = path.length();
        Pointer filename = UntrackedNullableNativeMemory.malloc(Word.unsigned(length + 1).multiply(Word.unsigned(Character.BYTES)));
        if (filename.isNull()) {
            return Word.nullPointer();
        }
        for (int i = 0; i < length; i++) {
            filename.writeChar(i * Character.BYTES, path.charAt(i));
        }
        filename.writeChar(length * Character.BYTES, (char) 0);
        return (RawFileOperationSupport.RawFilePath) filename;
    }

    @Override
    public RawFileDescriptor create(File file, FileCreationMode creationMode, FileAccessMode accessMode) {
        try (WindowsUtils.WCharPointerHolder wide = WindowsUtils.toWideCString(file.getPath())) {
            return createWide(wide.get(), creationMode, accessMode);
        }
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public RawFileDescriptor create(RawFileOperationSupport.RawFilePath path, FileCreationMode creationMode, FileAccessMode accessMode) {
        return createWide((WCharPointer) path, creationMode, accessMode);
    }

    @Override
    public String getTempDirectory() {
        return SystemPropertiesSupport.singleton().getInitialProperty("java.io.tmpdir");
    }

    @Override
    public RawFileDescriptor open(File file, FileAccessMode mode) {
        try (WindowsUtils.WCharPointerHolder wide = WindowsUtils.toWideCString(file.getPath())) {
            return openWide(wide.get(), mode);
        }
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public RawFileDescriptor open(RawFileOperationSupport.RawFilePath path, FileAccessMode accessMode) {
        return openWide((WCharPointer) path, accessMode);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static RawFileDescriptor openWide(WCharPointer widePath, FileAccessMode accessMode) {
        if (widePath.isNull()) {
            return Word.nullPointer();
        }
        int access = parseAccess(accessMode);
        HANDLE h = FileAPI.CreateFileW(
                        widePath,
                        access,
                        FileAPI.FILE_SHARE_READ() | FileAPI.FILE_SHARE_WRITE(),
                        Word.nullPointer(), FileAPI.OPEN_EXISTING(),
                        FileAPI.FILE_ATTRIBUTE_NORMAL(),
                        Word.nullPointer());
        return fromHandle(h);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static RawFileDescriptor createWide(WCharPointer widePath, FileCreationMode creationMode, FileAccessMode accessMode) {
        if (widePath.isNull()) {
            return Word.nullPointer();
        }
        int disposition = creationMode == FileCreationMode.CREATE ? FileAPI.CREATE_NEW() : FileAPI.CREATE_ALWAYS();
        int access = parseAccess(accessMode);
        HANDLE h = FileAPI.CreateFileW(
                        widePath,
                        access,
                        FileAPI.FILE_SHARE_READ() | FileAPI.FILE_SHARE_WRITE(),
                        Word.nullPointer(), disposition, FileAPI.FILE_ATTRIBUTE_NORMAL(),
                        Word.nullPointer());
        return fromHandle(h);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static int parseAccess(FileAccessMode mode) {
        switch (mode) {
            case READ:
                return FileAPI.GENERIC_READ();
            case READ_WRITE:
                return FileAPI.GENERIC_READ() | FileAPI.GENERIC_WRITE();
            case WRITE:
                return FileAPI.GENERIC_WRITE();
            default:
                throw VMError.shouldNotReachHereUnexpectedInput(mode); // ExcludeFromJacocoGeneratedReport
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static RawFileDescriptor fromHandle(HANDLE h) {
        if (h.equal(WinBase.INVALID_HANDLE_VALUE())) {
            return Word.nullPointer();
        }
        return (RawFileDescriptor) Word.pointer(h.rawValue());
    }

    @SuppressWarnings("unchecked")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static HANDLE asHandle(RawFileDescriptor fd) {
        return (HANDLE) fd;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean isValid(RawFileDescriptor fd) {
        HANDLE h = asHandle(fd);
        return !h.isNull() && !h.equal(WinBase.INVALID_HANDLE_VALUE());
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean close(RawFileDescriptor fd) {
        if (!isValid(fd)) {
            return false;
        }
        HANDLE h = asHandle(fd);
        return WinBase.CloseHandle(h) != 0;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long size(RawFileDescriptor fd) {
        if (!isValid(fd)) {
            return -1;
        }
        HANDLE h = asHandle(fd);
        CLongPointer size = UnsafeStackValue.get(CLongPointer.class);
        size.write(0);
        if (FileAPI.NoTransition.GetFileSizeEx(h, size) == 0) {
            return -1;
        }
        return size.read();
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long position(RawFileDescriptor fd) {
        if (!isValid(fd)) {
            return -1;
        }
        HANDLE h = asHandle(fd);
        CLongPointer newPos = UnsafeStackValue.get(CLongPointer.class);
        newPos.write(0);
        if (FileAPI.NoTransition.SetFilePointerEx(h, 0, newPos, FileAPI.FILE_CURRENT()) == 0) {
            return -1;
        }
        return newPos.read();
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean seek(RawFileDescriptor fd, long position) {
        if (!isValid(fd)) {
            return false;
        }
        HANDLE h = asHandle(fd);
        CLongPointer newPos = UnsafeStackValue.get(CLongPointer.class);
        newPos.write(0);
        if (FileAPI.NoTransition.SetFilePointerEx(h, position, newPos, FileAPI.FILE_BEGIN()) == 0) {
            return false;
        }
        return newPos.read() == position;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean write(RawFileDescriptor fd, Pointer data, UnsignedWord size) {
        if (!isValid(fd)) {
            return false;
        }
        HANDLE h = asHandle(fd);
        return WindowsUtils.writeUninterruptibly(h, (CCharPointer) data, size);
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long read(RawFileDescriptor fd, Pointer buffer, UnsignedWord bufferSize) {
        if (!isValid(fd)) {
            return -1;
        }
        HANDLE h = asHandle(fd);
        return WindowsUtils.readUninterruptibly(h, (CCharPointer) buffer, bufferSize);
    }
}

@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = SingleLayer.class)
@AutomaticallyRegisteredFeature
class WindowsRawFileOperationFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return ImageLayerBuildingSupport.firstImageBuild() && Platform.includedIn(WINDOWS_BASE.class);
    }

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
