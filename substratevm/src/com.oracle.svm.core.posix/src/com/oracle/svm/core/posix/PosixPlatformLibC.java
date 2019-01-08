/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.PlatformLibC;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.posix.headers.LibC;
import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.annotate.Uninterruptible;
import org.graalvm.word.WordFactory;

//Checkstyle: stop

/**
 * Basic functions from the standard Visual Studio C Run-Time library
 */
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
public class PosixPlatformLibC implements PlatformLibC {

    protected PosixPlatformLibC() {}

    @Override
    @Uninterruptible(reason = "called from Uninterruptible function")
    public <T extends PointerBase> T malloc(UnsignedWord size) {
        return LibC.malloc(size);
    }

    @Override
    @Uninterruptible(reason = "called from Uninterruptible function")
    public <T extends PointerBase> T calloc(UnsignedWord nmemb, UnsignedWord size) {
        return LibC.calloc(WordFactory.unsigned(1), size);
    }

    @Override
    @Uninterruptible(reason = "called from Uninterruptible function")
    public <T extends PointerBase> T realloc(PointerBase ptr, UnsignedWord size) {
        return LibC.realloc(ptr, size);
    }

    @Override
    @Uninterruptible(reason = "called from Uninterruptible function")
    public void free(PointerBase ptr) {
        LibC.free(ptr);
    }
}

@AutomaticFeature
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
class PosixPlatformLibCFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(PlatformLibC.class, new PosixPlatformLibC());
    }
}
