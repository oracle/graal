/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.logging;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.impl.InternalPlatform.WINDOWS_BASE;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.os.RawFileOperationSupport;
import com.oracle.svm.core.os.RawFileOperationSupport.RawFileDescriptor;
import com.oracle.svm.core.windows.headers.FileAPI;
import com.oracle.svm.core.windows.headers.WindowsLibC.WCharPointer;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.RuntimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.InitialLayerOnly;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

@Platforms(WINDOWS_BASE.class)
@SingletonTraits(access = RuntimeAccessOnly.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = InitialLayerOnly.class)
final class WindowsLoggingSupport implements LoggingSupport {
    @Override
    public boolean write(boolean stderr, byte[] bytes) {
        RawFileDescriptor descriptor = (RawFileDescriptor) FileAPI.NoTransition.GetStdHandle(stderr ? FileAPI.STD_ERROR_HANDLE() : FileAPI.STD_OUTPUT_HANDLE());
        return RawFileOperationSupport.nativeByteOrder().write(descriptor, bytes);
    }

    @Override
    public boolean write(boolean stderr, CCharPointer bytes, UnsignedWord length) {
        RawFileDescriptor descriptor = (RawFileDescriptor) FileAPI.NoTransition.GetStdHandle(stderr ? FileAPI.STD_ERROR_HANDLE() : FileAPI.STD_OUTPUT_HANDLE());
        return RawFileOperationSupport.nativeByteOrder().write(descriptor, (Pointer) bytes, length);
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean delete(RawFileOperationSupport.RawFilePath path) {
        return WindowsFileNames.delete((WCharPointer) path) != 0;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public int rename(RawFileOperationSupport.RawFilePath source, RawFileOperationSupport.RawFilePath target) {
        return WindowsFileNames.move((WCharPointer) source, (WCharPointer) target, WindowsFileNames.MOVEFILE_REPLACE_EXISTING);
    }

    @Override
    public String hostname() {
        int capacity = 256;
        WCharPointer buffer = StackValue.get(capacity, WCharPointer.class);
        CIntPointer length = StackValue.get(CIntPointer.class);
        length.write(capacity);
        if (WindowsFileNames.getComputerName(buffer, length) == 0) {
            return "localhost";
        }
        StringBuilder result = new StringBuilder(length.read());
        for (int index = 0; index < length.read(); index++) {
            result.append(buffer.read(index));
        }
        return result.toString();
    }

    private static final class WindowsFileNames {
        static final int MOVEFILE_REPLACE_EXISTING = 0x1;

        @CFunction(value = "DeleteFileW", transition = CFunction.Transition.NO_TRANSITION)
        static native int delete(WCharPointer path);

        @CFunction(value = "MoveFileExW", transition = CFunction.Transition.NO_TRANSITION)
        static native int move(WCharPointer source, WCharPointer target, int flags);

        @CFunction(value = "GetComputerNameW", transition = CFunction.Transition.NO_TRANSITION)
        static native int getComputerName(WCharPointer buffer, CIntPointer length);
    }
}

@AutomaticallyRegisteredFeature
@Platforms(WINDOWS_BASE.class)
final class WindowsLoggingSupportFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return ImageLayerBuildingSupport.firstImageBuild() && Platform.includedIn(WINDOWS_BASE.class);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(LoggingSupport.class, new WindowsLoggingSupport());
    }
}
