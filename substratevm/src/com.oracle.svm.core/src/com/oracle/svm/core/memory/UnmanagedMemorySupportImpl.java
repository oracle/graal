/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.memory;

import jdk.graal.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.headers.LibCSupport;
import com.oracle.svm.core.util.UnsignedUtils;

import jdk.graal.compiler.api.replacements.Fold;

/**
 * Delegates to the libc-specific memory management functions. Some platforms use a different
 * implementation.
 */
class UnmanagedMemorySupportImpl implements UnmanagedMemorySupport {
    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public <T extends PointerBase> T malloc(UnsignedWord size) {
        /*
         * Some libc implementations may return a nullptr when the size is 0. We use a minimum size
         * of 1 to ensure that we always get a unique pointer if the allocation succeeds.
         */
        UnsignedWord allocationSize = UnsignedUtils.max(size, Word.unsigned(1));
        return libc().malloc(allocationSize);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public <T extends PointerBase> T calloc(UnsignedWord size) {
        return libc().calloc(Word.unsigned(1), size);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public <T extends PointerBase> T realloc(T ptr, UnsignedWord size) {
        return libc().realloc(ptr, size);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void free(PointerBase ptr) {
        libc().free(ptr);
    }

    @Fold
    static LibCSupport libc() {
        return ImageSingletons.lookup(LibCSupport.class);
    }
}

@AutomaticallyRegisteredFeature
class UnmanagedMemorySupportFeature implements InternalFeature {
    @Override
    public void duringSetup(DuringSetupAccess access) {
        if (!ImageSingletons.contains(UnmanagedMemorySupport.class)) {
            ImageSingletons.add(UnmanagedMemorySupport.class, new UnmanagedMemorySupportImpl());
        }
    }
}
