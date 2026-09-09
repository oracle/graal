/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.os.AuxiliaryImageIOProvider;
import com.oracle.svm.core.os.VirtualMemoryProvider;
import com.oracle.svm.core.posix.headers.Fcntl;
import com.oracle.svm.core.posix.headers.Unistd;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.RuntimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.InitialLayerOnly;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
@SingletonTraits(access = RuntimeAccessOnly.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = InitialLayerOnly.class)
final class PosixAuxiliaryImageIOProvider implements AuxiliaryImageIOProvider {

    @Uninterruptible(reason = "Called during isolate initialization.")
    static int fromDesc(FileDesc fd) {
        long r = fd.rawValue();
        assert r > 0;
        long l = r - 1;
        assert l == (int) l;
        return (int) l;
    }

    @Override
    @Uninterruptible(reason = "Called during isolate initialization.")
    public FileDesc open(CCharPointer filePath) {
        int fd = Fcntl.NoTransitions.open(filePath, Fcntl.O_RDONLY(), 0);
        if (fd < 0) {
            return Word.zero();
        }
        // 0 is our return value on error, but also a valid file descriptor
        return (FileDesc) Word.unsigned(fd).add(1);
    }

    @Override
    @Uninterruptible(reason = "Called during isolate initialization.")
    public void close(FileDesc fd) {
        Unistd.NoTransitions.close(fromDesc(fd));
    }

    @Override
    @Uninterruptible(reason = "Called during isolate initialization.")
    public SignedWord pread(FileDesc fd, PointerBase buf, UnsignedWord nbytes, SignedWord offset) {
        return Unistd.NoTransitions.pread(fromDesc(fd), buf, nbytes, offset);
    }

    @Override
    @Uninterruptible(reason = "Called during isolate initialization.")
    public Pointer mapFile(PointerBase start, UnsignedWord nbytes, FileDesc desc, UnsignedWord offset, int access) {
        SignedWord fd = Word.signed(fromDesc(desc));
        return VirtualMemoryProvider.get().mapFile(start, nbytes, fd, offset, access);
    }
}

@AutomaticallyRegisteredFeature
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
class PosixAuxiliaryImageIOProviderFeature implements InternalFeature {

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return ImageLayerBuildingSupport.firstImageBuild();
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        if (!ImageSingletons.contains(AuxiliaryImageIOProvider.class)) {
            ImageSingletons.add(AuxiliaryImageIOProvider.class, new PosixAuxiliaryImageIOProvider());
        }
    }
}
