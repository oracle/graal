/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.jdk;

import java.util.EnumSet;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateTargetDescription;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.asm.amd64.AMD64BaseAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.CPUFeatureName;

/**
 * Provides access to a computation of the maximum Vector API vector size for the target platform.
 */
@AutomaticallyRegisteredImageSingleton
public final class VectorAPISupport {

    /**
     * The size in bytes of the target's largest vector type. This value is -1 if the target does
     * not support vectors.
     */
    private final int maxVectorBytes;

    @Platforms(Platform.HOSTED_ONLY.class) //
    protected VectorAPISupport() {
        SubstrateTargetDescription targetDescription = ImageSingletons.lookup(SubstrateTargetDescription.class);
        EnumSet<? extends CPUFeatureName> features = null;
        if (targetDescription.arch instanceof AMD64 amd64) {
            features = amd64.getFeatures();
        } else if (targetDescription.arch instanceof AArch64 aarch64) {
            features = aarch64.getFeatures();
        }
        this.maxVectorBytes = computeMaxVectorBytes(features);
    }

    public static VectorAPISupport singleton() {
        return ImageSingletons.lookup(VectorAPISupport.class);
    }

    @Platforms(Platform.HOSTED_ONLY.class) //
    @SuppressWarnings("unchecked")
    private static int computeMaxVectorBytes(EnumSet<? extends CPUFeatureName> features) {
        if (features == null) {
            return -1;
        } else if (features.contains(AArch64.CPUFeature.ASIMD)) {
            return AArch64Kind.V128_BYTE.getSizeInBytes();
        } else if (features.contains(AMD64.CPUFeature.AVX)) {
            if (features.contains(AMD64.CPUFeature.AVX512F) && AMD64BaseAssembler.supportsFullAVX512((EnumSet<AMD64.CPUFeature>) features)) {
                return AVXKind.AVXSize.ZMM.getBytes();
            } else if (features.contains(AMD64.CPUFeature.AVX2)) {
                return AVXKind.AVXSize.YMM.getBytes();
            } else if (features.contains(AMD64.CPUFeature.AVX)) {
                return AVXKind.AVXSize.XMM.getBytes();
            } else {
                return -1;
            }
        }
        return -1;
    }

    /**
     * Returns the maximum number of bytes in a vector on the target platform, or -1 if no vectors
     * are supported. Must only be called after all image singletons are registered.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public int getMaxVectorBytes() {
        return maxVectorBytes;
    }

    /**
     * Returns the maximum number of elements (lanes) in a vector with the given element type. If no
     * vectors are supported, returns the same result as if the maximum vector size were 64 bits.
     * The {@code etype} must be a compile-time constant specifying one of the element types
     * supported by Vector API vectors. Must only be called after all image singletons are
     * registered.
     */
    public int getMaxLaneCount(Class<?> etype) {
        /*
         * Like the JDK, use a minimum of 64 bits even if the target vector size computation returns
         * -1 to signal that no vectors are available.
         */
        int maxVectorBits = Math.max(maxVectorBytes * Byte.SIZE, 64);
        int elementBytes;
        if (etype == byte.class) {
            elementBytes = 1;
        } else if (etype == short.class) {
            elementBytes = 2;
        } else if (etype == int.class || etype == float.class) {
            elementBytes = 4;
        } else if (etype == long.class || etype == double.class) {
            elementBytes = 8;
        } else {
            throw VMError.shouldNotReachHereUnexpectedInput(etype);
        }
        return maxVectorBits / (elementBytes * Byte.SIZE);
    }
}
