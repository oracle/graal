/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512BW;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512F;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512VL;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.BMI2;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.CLMUL;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.POPCNT;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE2;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE3;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE4_1;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE4_2;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSSE3;

import java.util.EnumSet;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateTarget;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.replacements.nodes.AESNode;
import jdk.graal.compiler.replacements.nodes.Adler32UpdateBytesNode;
import jdk.graal.compiler.replacements.nodes.Base64DecodeBlockNode;
import jdk.graal.compiler.replacements.nodes.Base64EncodeBlockNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerMulAddNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerLeftShiftWorkerNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerMontgomeryMultiplyNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerMontgomerySquareNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerMultiplyToLenNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerRightShiftWorkerNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerSquareToLenNode;
import jdk.graal.compiler.replacements.nodes.ChaCha20Node;
import jdk.graal.compiler.replacements.nodes.CipherBlockChainingAESNode;
import jdk.graal.compiler.replacements.nodes.CounterModeAESNode;
import jdk.graal.compiler.replacements.nodes.CRC32CUpdateBytesNode;
import jdk.graal.compiler.replacements.nodes.CRC32UpdateBytesNode;
import jdk.graal.compiler.replacements.nodes.DilithiumNode;
import jdk.graal.compiler.replacements.nodes.ElectronicCodeBookAESNode;
import jdk.graal.compiler.replacements.nodes.GaloisCounterModeAESNode;
import jdk.graal.compiler.replacements.nodes.GHASHProcessBlocksNode;
import jdk.graal.compiler.replacements.nodes.KyberNode.Kyber12To16Node;
import jdk.graal.compiler.replacements.nodes.KyberNode.KyberAddPoly2Node;
import jdk.graal.compiler.replacements.nodes.KyberNode.KyberAddPoly3Node;
import jdk.graal.compiler.replacements.nodes.KyberNode.KyberBarrettReduceNode;
import jdk.graal.compiler.replacements.nodes.KyberNode.KyberInverseNttNode;
import jdk.graal.compiler.replacements.nodes.KyberNode.KyberNttMultNode;
import jdk.graal.compiler.replacements.nodes.KyberNode.KyberNttNode;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode.MD5Node;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode.SHA1Node;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode.SHA256Node;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode.SHA3Node;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode.SHA512Node;
import jdk.graal.compiler.replacements.nodes.Poly1305ProcessBlocksNode;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;

public final class Stubs {

    @Platforms(Platform.AMD64.class)
    public static class AMD64Features {
        public static final EnumSet<AMD64.CPUFeature> BASELINE_CPU_FEATURES_AMD64 = EnumSet.of(SSE2);
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
        public static final EnumSet<AMD64.CPUFeature> KYBER_CPU_FEATURES_AMD64 = EnumSet.of(AVX, AVX2, AVX512F, AVX512BW, AVX512VL);

        public static EnumSet<AMD64.CPUFeature> getRequiredCPUFeatures(Class<? extends ValueNode> klass) {
            if (Adler32UpdateBytesNode.class.equals(klass)) {
                return Adler32UpdateBytesNode.maxFeaturesAMD64();
            }
            if (AESNode.class.equals(klass)) {
                return AESNode.minFeaturesAMD64();
            }
            if (Base64DecodeBlockNode.class.equals(klass)) {
                return Base64DecodeBlockNode.minFeaturesAMD64();
            }
            if (Base64EncodeBlockNode.class.equals(klass)) {
                return Base64EncodeBlockNode.minFeaturesAMD64();
            }
            if (BigIntegerLeftShiftWorkerNode.class.equals(klass)) {
                return BigIntegerLeftShiftWorkerNode.minFeaturesAMD64();
            }
            if (BigIntegerMontgomeryMultiplyNode.class.equals(klass) || BigIntegerMontgomerySquareNode.class.equals(klass)) {
                return BASELINE_CPU_FEATURES_AMD64;
            }
            if (BigIntegerMulAddNode.class.equals(klass)) {
                return BIGINTEGER_MUL_ADD_CPU_FEATURES_AMD64;
            }
            if (BigIntegerMultiplyToLenNode.class.equals(klass)) {
                return BIGINTEGER_MULTIPLY_TO_LEN_CPU_FEATURES_AMD64;
            }
            if (BigIntegerRightShiftWorkerNode.class.equals(klass)) {
                return BigIntegerRightShiftWorkerNode.minFeaturesAMD64();
            }
            if (BigIntegerSquareToLenNode.class.equals(klass)) {
                return BIGINTEGER_MULTIPLY_TO_LEN_CPU_FEATURES_AMD64;
            }
            if (ChaCha20Node.class.equals(klass)) {
                return ChaCha20Node.minFeaturesAMD64();
            }
            if (CipherBlockChainingAESNode.class.equals(klass)) {
                return CipherBlockChainingAESNode.minFeaturesAMD64();
            }
            if (CounterModeAESNode.class.equals(klass)) {
                return CounterModeAESNode.minFeaturesAMD64();
            }
            if (CRC32CUpdateBytesNode.class.equals(klass)) {
                return CRC32CUpdateBytesNode.maxFeaturesAMD64();
            }
            if (CRC32UpdateBytesNode.class.equals(klass)) {
                return CRC32UpdateBytesNode.maxFeaturesAMD64();
            }
            if (isDilithiumNode(klass)) {
                return DilithiumNode.minFeaturesAMD64();
            }
            if (ElectronicCodeBookAESNode.class.equals(klass)) {
                return ElectronicCodeBookAESNode.minFeaturesAMD64();
            }
            if (GaloisCounterModeAESNode.class.equals(klass)) {
                return GaloisCounterModeAESNode.maxFeaturesAMD64();
            }
            if (GHASHProcessBlocksNode.class.equals(klass)) {
                return GHASH_CPU_FEATURES_AMD64;
            }
            if (KyberNttNode.class.equals(klass) ||
                            KyberInverseNttNode.class.equals(klass) ||
                            KyberNttMultNode.class.equals(klass) ||
                            KyberAddPoly2Node.class.equals(klass) ||
                            KyberAddPoly3Node.class.equals(klass) ||
                            Kyber12To16Node.class.equals(klass) ||
                            KyberBarrettReduceNode.class.equals(klass)) {
                return KYBER_CPU_FEATURES_AMD64;
            }
            if (MD5Node.class.equals(klass)) {
                return BASELINE_CPU_FEATURES_AMD64;
            }
            if (Poly1305ProcessBlocksNode.class.equals(klass)) {
                return Poly1305ProcessBlocksNode.maxFeaturesAMD64();
            }
            if (SHA1Node.class.equals(klass)) {
                return SHA1Node.minFeaturesAMD64();
            }
            if (SHA256Node.class.equals(klass)) {
                return SHA256Node.minFeaturesAMD64();
            }
            if (SHA3Node.class.equals(klass)) {
                return SHA3Node.minFeaturesAMD64();
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
            if (GaloisCounterModeAESNode.class.equals(klass)) {
                return GaloisCounterModeAESNode.minFeaturesAARCH64();
            }
            if (BigIntegerLeftShiftWorkerNode.class.equals(klass)) {
                return EMPTY_CPU_FEATURES_AARCH64;
            }
            if (BigIntegerRightShiftWorkerNode.class.equals(klass)) {
                return EMPTY_CPU_FEATURES_AARCH64;
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
            if (CRC32CUpdateBytesNode.class.equals(klass)) {
                return CRC32CUpdateBytesNode.minFeaturesAARCH64();
            }
            return EMPTY_CPU_FEATURES_AARCH64;
        }
    }

    public static EnumSet<?> getRequiredCPUFeatures(Class<? extends ValueNode> klass) {
        Architecture arch = SubstrateTarget.getArchitecture();
        if (arch instanceof AMD64) {
            return AMD64Features.getRequiredCPUFeatures(klass);
        }
        if (arch instanceof AArch64) {
            return AArch64Features.getRequiredCPUFeatures(klass);
        }
        throw GraalError.unsupportedArchitecture(arch); // ExcludeFromJacocoGeneratedReport
    }

    private static boolean isDilithiumNode(Class<? extends ValueNode> klass) {
        return DilithiumNode.DilithiumAlmostInverseNttNode.class.equals(klass) ||
                        DilithiumNode.DilithiumAlmostNttNode.class.equals(klass) ||
                        DilithiumNode.DilithiumDecomposePolyNode.class.equals(klass) ||
                        DilithiumNode.DilithiumMontMulByConstantNode.class.equals(klass) ||
                        DilithiumNode.DilithiumNttMultNode.class.equals(klass);
    }

    @Fold
    public static EnumSet<?> getRuntimeCheckedCPUFeatures(Class<? extends ValueNode> klass) {
        return getRequiredCPUFeatures(klass);
    }

    public static final String RUNTIME_CHECKED_CPU_FEATURES_NAME_SUFFIX = "RTC";
}
