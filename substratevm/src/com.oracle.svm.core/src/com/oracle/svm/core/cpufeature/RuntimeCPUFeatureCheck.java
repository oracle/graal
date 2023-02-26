/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.cpufeature;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;

import com.oracle.svm.core.jdk.RuntimeSupport;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;

/**
 * Utilities for checking the features provided by the target CPU <em>at run time</em>. This allows
 * ahead of time compiled code to make decisions at run time based on the actual CPU features, as
 * opposed to only the flags set at image build time.
 * </p>
 *
 * Startup code that runs before the call to {@link RuntimeSupport#executeInitializationHooks()}
 * will only see the statically supported CPU features specified at build time.
 *
 * @see RuntimeCPUFeatureCheckImpl for the implementation
 */
public final class RuntimeCPUFeatureCheck {

    /**
     * Returns a set of features eligible for a CPU feature check at run time for the given
     * architecture.
     * <p>
     * Not all supported features listed here are enabled by default, see
     * {@link #getDefaultDisabledFeatures}.
     * <p>
     * Keep this set in sync with the default values listed in the documentation for the
     * RuntimeCheckedCPUFeatures option, i.e., that the default values are
     * {@link #getSupportedFeatures(Architecture)} -
     * {@link #getDefaultDisabledFeatures(Architecture)}.
     *
     * @see #getDefaultDisabledFeatures(Architecture)
     */
    public static Set<? extends Enum<?>> getSupportedFeatures(Architecture arch) {
        if (arch instanceof AMD64) {
            return EnumSet.of(AMD64.CPUFeature.AVX, AMD64.CPUFeature.AVX2, AMD64.CPUFeature.AVX512F, AMD64.CPUFeature.AVX512BW);
        } else {
            return Collections.emptySet();
        }
    }

    /**
     * Returns a set of features for which no runtime check will be emitted by default. The idea is
     * that we do not want to generate code for CPU features which are rarely available or which
     * rarely improve performance. (This is for example the case for many implementations of AVX512
     * available in AMD64 CPUs to date). If the {@code RuntimeCheckedCPUFeatures} option is set,
     * this has no effect.
     * <p>
     * The return value must be a subset of {@link #getSupportedFeatures}.
     * <p>
     * Keep this set in sync with the default values listed in the documentation for the
     * RuntimeCheckedCPUFeatures option, i.e., that the default values are
     * {@link #getSupportedFeatures(Architecture)} -
     * {@link #getDefaultDisabledFeatures(Architecture)}.
     *
     * @see #getSupportedFeatures(Architecture)
     */
    public static Set<? extends Enum<?>> getDefaultDisabledFeatures(Architecture arch) {
        if (arch instanceof AMD64) {
            return EnumSet.of(AMD64.CPUFeature.AVX512F, AMD64.CPUFeature.AVX512BW);
        } else {
            return Collections.emptySet();
        }
    }

    /**
     * Create a {@code boolean} value to check at runtime whether the CPU supports the given {@code
     * features}. If the features are statically known to be supported, this generates a
     * {@code true} constant. If the feature is not eligible for runtime checks on the target
     * architecture (e.g., it is a feature from a different architecture, or it is not in the set of
     * {@linkplain #getSupportedFeatures supported features}), this generates a {@code false}
     * constant.
     */
    @NodeIntrinsic(RuntimeCPUFeatureCheckImpl.class)
    public static native <T extends Enum<T>> boolean isSupported(@ConstantNodeParameter Enum<T> arg0);

    /**
     * @see #isSupported(Enum)
     */
    @NodeIntrinsic(RuntimeCPUFeatureCheckImpl.class)
    public static native <T extends Enum<T>> boolean isSupported(@ConstantNodeParameter Enum<T> arg0, @ConstantNodeParameter Enum<T> arg1);

    /**
     * @see #isSupported(Enum)
     */
    @NodeIntrinsic(RuntimeCPUFeatureCheckImpl.class)
    public static native <T extends Enum<T>> boolean isSupported(@ConstantNodeParameter Enum<T> arg0, @ConstantNodeParameter Enum<T> arg1, @ConstantNodeParameter Enum<T> arg2);
}
