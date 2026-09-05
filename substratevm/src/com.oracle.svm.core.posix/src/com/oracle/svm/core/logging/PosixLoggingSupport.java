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
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.os.RawFileOperationSupport;
import com.oracle.svm.core.posix.headers.Fcntl;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.RuntimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.InitialLayerOnly;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

/// Writes unified logging stream output through the POSIX file descriptors.
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
@SingletonTraits(access = RuntimeAccessOnly.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = InitialLayerOnly.class)
final class PosixLoggingSupport implements LoggingSupport {
    private static final int STDOUT_FILENO = 1;
    private static final int STDERR_FILENO = 2;

    @Override
    public boolean write(boolean stderr, byte[] bytes) {
        return RawFileOperationSupport.nativeByteOrder().write(Word.signed(stderr ? STDERR_FILENO : STDOUT_FILENO), bytes);
    }

    @Override
    public boolean write(boolean stderr, CCharPointer bytes, UnsignedWord length) {
        return RawFileOperationSupport.nativeByteOrder().write(Word.signed(stderr ? STDERR_FILENO : STDOUT_FILENO), (Pointer) bytes, length);
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean delete(RawFileOperationSupport.RawFilePath path) {
        return Fcntl.NoTransitions.unlink((CCharPointer) path) == 0;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public int rename(RawFileOperationSupport.RawFilePath source, RawFileOperationSupport.RawFilePath target) {
        return PosixFileNames.rename((CCharPointer) source, (CCharPointer) target);
    }

    @Override
    public String hostname() {
        int capacity = 256;
        CCharPointer buffer = StackValue.get(capacity);
        if (PosixFileNames.gethostname(buffer, Word.unsigned(capacity)) == 0) {
            return CTypeConversion.toJavaString(buffer);
        }
        return "localhost";
    }

    private static final class PosixFileNames {
        @CFunction(value = "rename", transition = CFunction.Transition.NO_TRANSITION)
        static native int rename(CCharPointer source, CCharPointer target);

        @CFunction(value = "gethostname", transition = CFunction.Transition.NO_TRANSITION)
        static native int gethostname(CCharPointer buffer, UnsignedWord length);
    }
}

@AutomaticallyRegisteredFeature
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class PosixLoggingSupportFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return ImageLayerBuildingSupport.firstImageBuild() && (Platform.includedIn(Platform.LINUX.class) || Platform.includedIn(Platform.DARWIN.class));
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(LoggingSupport.class, new PosixLoggingSupport());
    }
}
