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
package com.oracle.svm.graal.stubs;

import static com.oracle.svm.core.cpufeature.Stubs.AArch64Features.EMPTY_CPU_FEATURES_AARCH64;

import org.graalvm.nativeimage.Platform.AARCH64;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;

import jdk.graal.compiler.replacements.StringLatin1InflateNode;
import jdk.graal.compiler.replacements.StringUTF16CompressNode;
import jdk.graal.compiler.replacements.nodes.AESNode;
import jdk.graal.compiler.replacements.nodes.Adler32UpdateBytesNode;
import jdk.graal.compiler.replacements.nodes.ArrayCompareToForeignCalls;
import jdk.graal.compiler.replacements.nodes.ArrayCopyWithConversionsForeignCalls;
import jdk.graal.compiler.replacements.nodes.ArrayEqualsForeignCalls;
import jdk.graal.compiler.replacements.nodes.ArrayEqualsWithMaskForeignCalls;
import jdk.graal.compiler.replacements.nodes.ArrayFillNode;
import jdk.graal.compiler.replacements.nodes.ArrayIndexOfForeignCalls;
import jdk.graal.compiler.replacements.nodes.ArrayRegionCompareToForeignCalls;
import jdk.graal.compiler.replacements.nodes.Base64DecodeBlockNode;
import jdk.graal.compiler.replacements.nodes.Base64EncodeBlockNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerLeftShiftWorkerNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerMulAddNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerMontgomeryMultiplyNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerMontgomerySquareNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerMultiplyToLenNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerRightShiftWorkerNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerSquareToLenNode;
import jdk.graal.compiler.replacements.nodes.CalcStringAttributesForeignCalls;
import jdk.graal.compiler.replacements.nodes.CalcStringAttributesNode;
import jdk.graal.compiler.replacements.nodes.ChaCha20Node;
import jdk.graal.compiler.replacements.nodes.CipherBlockChainingAESNode;
import jdk.graal.compiler.replacements.nodes.CountPositivesNode;
import jdk.graal.compiler.replacements.nodes.CRC32CUpdateBytesNode;
import jdk.graal.compiler.replacements.nodes.CRC32UpdateBytesNode;
import jdk.graal.compiler.replacements.nodes.CounterModeAESNode;
import jdk.graal.compiler.replacements.nodes.DilithiumNode.DilithiumAlmostInverseNttNode;
import jdk.graal.compiler.replacements.nodes.DilithiumNode.DilithiumAlmostNttNode;
import jdk.graal.compiler.replacements.nodes.DilithiumNode.DilithiumDecomposePolyNode;
import jdk.graal.compiler.replacements.nodes.DilithiumNode.DilithiumMontMulByConstantNode;
import jdk.graal.compiler.replacements.nodes.DilithiumNode.DilithiumNttMultNode;
import jdk.graal.compiler.replacements.nodes.DoubleKeccakNode;
import jdk.graal.compiler.replacements.nodes.EncodeArrayNode;
import jdk.graal.compiler.replacements.nodes.GaloisCounterModeAESNode;
import jdk.graal.compiler.replacements.nodes.GHASHProcessBlocksNode;
import jdk.graal.compiler.replacements.nodes.IndexOfZeroForeignCalls;
import jdk.graal.compiler.replacements.nodes.KyberNode.Kyber12To16Node;
import jdk.graal.compiler.replacements.nodes.KyberNode.KyberAddPoly2Node;
import jdk.graal.compiler.replacements.nodes.KyberNode.KyberAddPoly3Node;
import jdk.graal.compiler.replacements.nodes.KyberNode.KyberBarrettReduceNode;
import jdk.graal.compiler.replacements.nodes.KyberNode.KyberInverseNttNode;
import jdk.graal.compiler.replacements.nodes.KyberNode.KyberNttMultNode;
import jdk.graal.compiler.replacements.nodes.KyberNode.KyberNttNode;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode.MD5MultiBlockNode;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode.MD5Node;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode.SHA1MultiBlockNode;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode.SHA1Node;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode.SHA256MultiBlockNode;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode.SHA256Node;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode.SHA3MultiBlockNode;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode.SHA3Node;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode.SHA512MultiBlockNode;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode.SHA512Node;
import jdk.graal.compiler.replacements.nodes.Poly1305ProcessBlocksNode;
import jdk.graal.compiler.replacements.nodes.VectorizedHashCodeNode;
import jdk.graal.compiler.replacements.nodes.VectorizedMismatchNode;

@AutomaticallyRegisteredFeature
@Platforms(AARCH64.class)
public class AArch64StubForeignCallsFeature extends StubForeignCallsFeatureBase {

    public AArch64StubForeignCallsFeature() {
        super(SVMIntrinsicStubsGen.class, new StubDescriptor[]{
                        new StubDescriptor(Adler32UpdateBytesNode.STUB, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(AESNode.STUBS, AESNode.minFeaturesAARCH64(), AESNode.minFeaturesAARCH64()),
                        new StubDescriptor(ArrayCompareToForeignCalls.STUBS, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(ArrayCopyWithConversionsForeignCalls.STUBS, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(ArrayEqualsForeignCalls.STUBS, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(ArrayEqualsWithMaskForeignCalls.STUBS, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(ArrayFillNode.STUBS, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(ArrayIndexOfForeignCalls.STUBS, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(ArrayRegionCompareToForeignCalls.STUBS, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(Base64DecodeBlockNode.STUB, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(Base64EncodeBlockNode.STUB, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(BigIntegerLeftShiftWorkerNode.STUB, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(BigIntegerMontgomeryMultiplyNode.STUB, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(BigIntegerMontgomerySquareNode.STUB, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(BigIntegerMulAddNode.STUB, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(BigIntegerMultiplyToLenNode.STUB, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(BigIntegerRightShiftWorkerNode.STUB, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(BigIntegerSquareToLenNode.STUB, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(CalcStringAttributesForeignCalls.STUBS, CalcStringAttributesNode.minFeaturesAARCH64(), EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(ChaCha20Node.STUB, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(CipherBlockChainingAESNode.STUBS, CipherBlockChainingAESNode.minFeaturesAARCH64(), CipherBlockChainingAESNode.minFeaturesAARCH64()),
                        new StubDescriptor(CounterModeAESNode.STUB, CounterModeAESNode.minFeaturesAARCH64(), CounterModeAESNode.minFeaturesAARCH64()),
                        new StubDescriptor(CountPositivesNode.STUB, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(CRC32CUpdateBytesNode.STUB, CRC32CUpdateBytesNode.minFeaturesAARCH64(), CRC32CUpdateBytesNode.minFeaturesAARCH64()),
                        new StubDescriptor(CRC32UpdateBytesNode.STUB, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(DilithiumAlmostInverseNttNode.STUB, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(DilithiumAlmostNttNode.STUB, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(DilithiumDecomposePolyNode.STUB, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(DilithiumMontMulByConstantNode.STUB, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(DilithiumNttMultNode.STUB, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(DoubleKeccakNode.STUB, DoubleKeccakNode.minFeaturesAARCH64(), DoubleKeccakNode.minFeaturesAARCH64()),
                        new StubDescriptor(EncodeArrayNode.STUBS, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(GaloisCounterModeAESNode.STUB, GaloisCounterModeAESNode.minFeaturesAARCH64(), GaloisCounterModeAESNode.minFeaturesAARCH64()),
                        new StubDescriptor(GHASHProcessBlocksNode.STUB, GHASHProcessBlocksNode.minFeaturesAARCH64(), GHASHProcessBlocksNode.minFeaturesAARCH64()),
                        new StubDescriptor(IndexOfZeroForeignCalls.STUBS, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(Kyber12To16Node.STUB, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(KyberAddPoly2Node.STUB, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(KyberAddPoly3Node.STUB, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(KyberBarrettReduceNode.STUB, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(KyberInverseNttNode.STUB, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(KyberNttMultNode.STUB, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(KyberNttNode.STUB, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(MD5MultiBlockNode.STUB, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(MD5Node.STUB, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(Poly1305ProcessBlocksNode.STUB, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(SHA1MultiBlockNode.STUB, SHA1MultiBlockNode.minFeaturesAARCH64(), SHA1MultiBlockNode.minFeaturesAARCH64()),
                        new StubDescriptor(SHA1Node.STUB, SHA1Node.minFeaturesAARCH64(), SHA1Node.minFeaturesAARCH64()),
                        new StubDescriptor(SHA256MultiBlockNode.STUB, SHA256MultiBlockNode.minFeaturesAARCH64(), SHA256MultiBlockNode.minFeaturesAARCH64()),
                        new StubDescriptor(SHA256Node.STUB, SHA256Node.minFeaturesAARCH64(), SHA256Node.minFeaturesAARCH64()),
                        new StubDescriptor(SHA3MultiBlockNode.STUB, SHA3MultiBlockNode.minFeaturesAARCH64(), SHA3MultiBlockNode.minFeaturesAARCH64()),
                        new StubDescriptor(SHA3Node.STUB, SHA3Node.minFeaturesAARCH64(), SHA3Node.minFeaturesAARCH64()),
                        new StubDescriptor(SHA512MultiBlockNode.STUB, SHA512MultiBlockNode.minFeaturesAARCH64(), SHA512MultiBlockNode.minFeaturesAARCH64()),
                        new StubDescriptor(SHA512Node.STUB, SHA512Node.minFeaturesAARCH64(), SHA512Node.minFeaturesAARCH64()),
                        new StubDescriptor(StringLatin1InflateNode.STUB, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(StringUTF16CompressNode.STUB, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(VectorizedHashCodeNode.STUBS, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(VectorizedMismatchNode.STUB, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
        });
    }
}
