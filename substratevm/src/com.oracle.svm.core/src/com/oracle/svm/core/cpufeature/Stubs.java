/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.amd64.AMD64.CPUFeature.ADX;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX2;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.BMI2;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.CLMUL;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.POPCNT;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE2;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE3;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE4_1;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE4_2;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSSE3;

import java.util.EnumSet;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.replacements.nodes.AESNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerMulAddNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerMultiplyToLenNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerSquareToLenNode;
import jdk.graal.compiler.replacements.nodes.CipherBlockChainingAESNode;
import jdk.graal.compiler.replacements.nodes.CounterModeAESNode;
import jdk.graal.compiler.replacements.nodes.GHASHProcessBlocksNode;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode.SHA1Node;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode.SHA256Node;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode.SHA3Node;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode.SHA512Node;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateTargetDescription;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;

public final class Stubs {

    @Platforms(Platform.AMD64.class)
    public static class AMD64Features {
        public static final EnumSet<AMD64.CPUFeature> RUNTIME_CHECKED_CPU_FEATURES_AMD64 = EnumSet.of(
                        SSE2,
                        SSE3,
                        SSSE3,
                        SSE4_1,
                        SSE4_2,
                        POPCNT,
                        AVX,
                        AVX2);
        public static final EnumSet<AMD64.CPUFeature> GHASH_CPU_FEATURES_AMD64 = EnumSet.of(AVX, CLMUL);
        public static final EnumSet<AMD64.CPUFeature> BIGINTEGER_MULTIPLY_TO_LEN_CPU_FEATURES_AMD64 = EnumSet.of(AVX, BMI2, ADX);
        public static final EnumSet<AMD64.CPUFeature> BIGINTEGER_MUL_ADD_CPU_FEATURES_AMD64 = EnumSet.of(AVX, BMI2);

        public static EnumSet<AMD64.CPUFeature> getRequiredCPUFeatures(Class<? extends ValueNode> klass) {
            if (AESNode.class.equals(klass)) {
                return AESNode.minFeaturesAMD64();
            }
            if (CounterModeAESNode.class.equals(klass)) {
                return CounterModeAESNode.minFeaturesAMD64();
            }
            if (CipherBlockChainingAESNode.class.equals(klass)) {
                return CipherBlockChainingAESNode.minFeaturesAMD64();
            }
            if (GHASHProcessBlocksNode.class.equals(klass)) {
                return GHASH_CPU_FEATURES_AMD64;
            }
            if (BigIntegerMultiplyToLenNode.class.equals(klass)) {
                return BIGINTEGER_MULTIPLY_TO_LEN_CPU_FEATURES_AMD64;
            }
            if (BigIntegerMulAddNode.class.equals(klass)) {
                return BIGINTEGER_MUL_ADD_CPU_FEATURES_AMD64;
            }
            if (BigIntegerSquareToLenNode.class.equals(klass)) {
                return BIGINTEGER_MULTIPLY_TO_LEN_CPU_FEATURES_AMD64;
            }
            if (SHA1Node.class.equals(klass)) {
                return SHA1Node.minFeaturesAMD64();
            }
            if (SHA256Node.class.equals(klass)) {
                return SHA256Node.minFeaturesAMD64();
            }
            if (SHA512Node.class.equals(klass)) {
                return SHA512Node.minFeaturesAMD64();
            }
            return RUNTIME_CHECKED_CPU_FEATURES_AMD64;
        }
    }

    @Platforms(Platform.AARCH64.class)
    public static class AArch64Features {
        public static final EnumSet<AArch64.CPUFeature> EMPTY_CPU_FEATURES_AARCH64 = EnumSet.noneOf(AArch64.CPUFeature.class);

        public static EnumSet<AArch64.CPUFeature> getRequiredCPUFeatures(Class<? extends ValueNode> klass) {
            if (AESNode.class.equals(klass)) {
                return AESNode.minFeaturesAARCH64();
            }
            if (CounterModeAESNode.class.equals(klass)) {
                return CounterModeAESNode.minFeaturesAARCH64();
            }
            if (CipherBlockChainingAESNode.class.equals(klass)) {
                return CipherBlockChainingAESNode.minFeaturesAARCH64();
            }
            if (GHASHProcessBlocksNode.class.equals(klass)) {
                return GHASHProcessBlocksNode.minFeaturesAARCH64();
            }
            if (SHA1Node.class.equals(klass)) {
                return SHA1Node.minFeaturesAARCH64();
            }
            if (SHA256Node.class.equals(klass)) {
                return SHA256Node.minFeaturesAARCH64();
            }
            if (SHA3Node.class.equals(klass)) {
                return SHA3Node.minFeaturesAARCH64();
            }
            if (SHA512Node.class.equals(klass)) {
                return SHA512Node.minFeaturesAARCH64();
            }
            return EMPTY_CPU_FEATURES_AARCH64;
        }
    }

    public static EnumSet<?> getRequiredCPUFeatures(Class<? extends ValueNode> klass) {
        Architecture arch = ImageSingletons.lookup(SubstrateTargetDescription.class).arch;
        if (arch instanceof AMD64) {
            return AMD64Features.getRequiredCPUFeatures(klass);
        }
        if (arch instanceof AArch64) {
            return AArch64Features.getRequiredCPUFeatures(klass);
        }
        throw GraalError.unsupportedArchitecture(arch); // ExcludeFromJacocoGeneratedReport
    }

    @Fold
    public static EnumSet<?> getRuntimeCheckedCPUFeatures(Class<? extends ValueNode> klass) {
        return getRequiredCPUFeatures(klass);
    }

    public static final String RUNTIME_CHECKED_CPU_FEATURES_NAME_SUFFIX = "RTC";
}
