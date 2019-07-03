/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle.nfi;

import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.truffle.TruffleFeature;
import com.oracle.truffle.nfi.NFILanguage;

/**
 * Support for the default (trufflenfi/native) backend of the {@link NFILanguage} on SVM. This is
 * re-using most of the code of the default (libffi based) implementation from the Truffle
 * repository. All substitutions in this package (unless noted otherwise in a separate comment) are
 * direct re-implementations of the original NFI functions with the C interface of Substrate VM. If
 * this feature is enabled, the image is statically linked with libffi.
 */
public final class TruffleNFIFeature implements Feature {

    public static class IsEnabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return ImageSingletons.contains(TruffleNFIFeature.class);
        }
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Arrays.asList(TruffleFeature.class);
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        access.registerObjectReplacer(new NativeObjectReplacer(access));
    }
}
