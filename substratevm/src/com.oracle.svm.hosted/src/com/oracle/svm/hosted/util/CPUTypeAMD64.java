/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.util;

import static jdk.vm.ci.amd64.AMD64.CPUFeature.ADX;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AES;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AMD_3DNOW_PREFETCH;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX2;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512BW;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512CD;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512DQ;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512F;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512VL;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.BMI1;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.BMI2;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.CLMUL;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.CLWB;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.CMOV;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.CX8;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.F16C;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.FLUSHOPT;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.FMA;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.FXSR;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.LZCNT;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.MMX;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.PKU;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.POPCNT;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE2;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE3;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE4_1;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE4_2;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSSE3;

import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.NativeImageOptions;
import com.oracle.svm.util.LogUtils;
import com.oracle.svm.util.StringUtil;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64.CPUFeature;

/**
 * AMD64 CPU types used to implement -march.
 * <p>
 * For reference, see <a href= "https://gcc.gnu.org/onlinedocs/gcc/x86-Options.html">gcc's x86
 * Options</a>.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public enum CPUTypeAMD64 implements CPUType {
    /**
     * Microarchitecture levels.
     * <p>
     * For reference, see
     * <a href="https://en.wikipedia.org/wiki/X86-64#Microarchitecture_levels">x86-64:
     * Microarchitecture levels</a>.
     */
    X86_64("x86-64", CMOV, CX8, FXSR, MMX, SSE, SSE2),
    X86_64_V1("x86-64-v1", X86_64),
    X86_64_V2("x86-64-v2", X86_64_V1, POPCNT, SSE3, SSE4_1, SSE4_2, SSSE3),
    X86_64_V3("x86-64-v3", X86_64_V2, AVX, AVX2, BMI1, BMI2, F16C, FMA, LZCNT),
    X86_64_V4("x86-64-v4", X86_64_V3, AVX512F, AVX512BW, AVX512CD, AVX512DQ, AVX512VL),

    // Intel selection
    HASWELL("haswell", X86_64, AES, SSE3, SSSE3, SSE4_1, SSE4_2, POPCNT, CLMUL, AVX, F16C, AVX2, BMI1, BMI2, LZCNT, FMA),
    SKYLAKE("skylake", HASWELL, ADX, AMD_3DNOW_PREFETCH, FLUSHOPT),
    SKYLAKE_AVX512("skylake-avx512", SKYLAKE, PKU, AVX512F, AVX512CD, AVX512VL, AVX512BW, AVX512DQ, CLWB),

    // Special symbols
    COMPATIBILITY(NativeImageOptions.MICRO_ARCHITECTURE_COMPATIBILITY, X86_64),
    NATIVE(NativeImageOptions.MICRO_ARCHITECTURE_NATIVE, getNativeOrEmpty());

    private static CPUFeature[] getNativeOrEmpty() {
        CPUFeature[] empty = new CPUFeature[0];
        if (GraalAccess.getOriginalTarget().arch instanceof AMD64 arch) {
            return arch.getFeatures().toArray(empty);
        } else {
            return empty;
        }
    }

    private final String name;
    private final CPUTypeAMD64 parent;
    private final EnumSet<CPUFeature> specificFeatures;

    CPUTypeAMD64(String cpuTypeName, CPUFeature... features) {
        this(cpuTypeName, null, features);
    }

    CPUTypeAMD64(String cpuTypeName, CPUTypeAMD64 cpuTypeParent, CPUFeature... features) {
        name = cpuTypeName;
        parent = cpuTypeParent;
        specificFeatures = features.length > 0 ? EnumSet.copyOf(List.of(features)) : EnumSet.noneOf(CPUFeature.class);
        assert parent == null || parent.getFeatures().stream().noneMatch(specificFeatures::contains) : "duplicate features detected but not allowed";
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public CPUTypeAMD64 getParent() {
        return parent;
    }

    @Override
    public String getSpecificFeaturesString() {
        return specificFeatures.stream().map(Enum::name).collect(Collectors.joining(" + "));
    }

    public EnumSet<CPUFeature> getFeatures() {
        if (parent == null) {
            return specificFeatures;
        } else {
            return EnumSet.copyOf(Stream.concat(parent.getFeatures().stream(), specificFeatures.stream()).toList());
        }
    }

    public static String getDefaultName(boolean printFallbackWarning) {
        if (NATIVE.getFeatures().containsAll(X86_64_V3.getFeatures())) {
            return X86_64_V3.getName();
        } else {
            if (printFallbackWarning) {
                LogUtils.warning("The host machine does not support all features of '%s'. Falling back to '%s' for best compatibility.",
                                X86_64_V3.getName(), SubstrateOptionsParser.commandArgument(NativeImageOptions.MicroArchitecture, COMPATIBILITY.getName()));
            }
            return COMPATIBILITY.getName();
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static EnumSet<CPUFeature> getSelectedFeatures() {
        String value = NativeImageOptions.MicroArchitecture.getValue();
        if (value == null) {
            value = getDefaultName(true);
        }
        return getCPUFeaturesForArch(value);
    }

    public static EnumSet<CPUFeature> getCPUFeaturesForArch(String marchValue) {
        CPUTypeAMD64 value = typeOf(marchValue);
        if (value == null) {
            throw UserError.abort("Unsupported architecture '%s'. Please adjust '%s'. On AMD64, only %s are available.",
                            marchValue,
                            SubstrateOptionsParser.commandArgument(NativeImageOptions.MicroArchitecture, marchValue),
                            StringUtil.joinSingleQuoted(CPUType.toNames(values())));
        }
        return value.getFeatures();
    }

    public static boolean nativeSupportsMoreFeaturesThanSelected() {
        EnumSet<CPUFeature> nativeFeatures = NATIVE.getFeatures();
        EnumSet<CPUFeature> selectedFeatures = getSelectedFeatures();
        return nativeFeatures.containsAll(selectedFeatures) && nativeFeatures.size() > selectedFeatures.size();
    }

    private static CPUTypeAMD64 typeOf(String marchValue) {
        for (CPUTypeAMD64 value : values()) {
            if (value.name.equals(marchValue)) {
                return value;
            }
        }
        return null;
    }
}
