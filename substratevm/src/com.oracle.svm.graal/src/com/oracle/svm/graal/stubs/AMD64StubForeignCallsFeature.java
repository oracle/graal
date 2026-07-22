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

import static com.oracle.svm.core.cpufeature.Stubs.AMD64Features.BIGINTEGER_MULTIPLY_TO_LEN_CPU_FEATURES_AMD64;
import static com.oracle.svm.core.cpufeature.Stubs.AMD64Features.BIGINTEGER_MUL_ADD_CPU_FEATURES_AMD64;
import static com.oracle.svm.core.cpufeature.Stubs.AMD64Features.GHASH_CPU_FEATURES_AMD64;
import static com.oracle.svm.core.cpufeature.Stubs.AMD64Features.KYBER_CPU_FEATURES_AMD64;
import static com.oracle.svm.core.cpufeature.Stubs.AMD64Features.RUNTIME_CHECKED_CPU_FEATURES_AMD64;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE2;

import java.util.EnumSet;

import jdk.graal.compiler.replacements.nodes.IndexOfZeroNode;
import org.graalvm.nativeimage.Platform.AMD64;
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
import jdk.graal.compiler.replacements.nodes.ArrayIndexOfForeignCalls;
import jdk.graal.compiler.replacements.nodes.ArrayRegionCompareToForeignCalls;
import jdk.graal.compiler.replacements.nodes.BigIntegerLeftShiftWorkerNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerMulAddNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerMontgomeryMultiplyNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerMontgomerySquareNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerMultiplyToLenNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerRightShiftWorkerNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerSquareToLenNode;
import jdk.graal.compiler.replacements.nodes.Base64DecodeBlockNode;
import jdk.graal.compiler.replacements.nodes.Base64EncodeBlockNode;
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
import jdk.graal.compiler.replacements.nodes.DoubleModStubNode;
import jdk.graal.compiler.replacements.nodes.ElectronicCodeBookAESNode;
import jdk.graal.compiler.replacements.nodes.EncodeArrayNode;
import jdk.graal.compiler.replacements.nodes.GaloisCounterModeAESNode;
import jdk.graal.compiler.replacements.nodes.GHASHProcessBlocksNode;
import jdk.graal.compiler.replacements.nodes.IndexOfZeroForeignCalls;
import jdk.graal.compiler.replacements.nodes.IntegerPolynomialAssignNode;
import jdk.graal.compiler.replacements.nodes.IntegerPolynomialP256MontgomeryMultNode;
import jdk.graal.compiler.replacements.nodes.KyberNode;
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
import jdk.graal.compiler.replacements.nodes.StringCodepointIndexToByteIndexForeignCalls;
import jdk.graal.compiler.replacements.nodes.StringCodepointIndexToByteIndexNode;
import jdk.graal.compiler.replacements.nodes.VectorizedHashCodeNode;
import jdk.graal.compiler.replacements.nodes.VectorizedMismatchNode;
import jdk.vm.ci.amd64.AMD64.CPUFeature;

@AutomaticallyRegisteredFeature
@Platforms(AMD64.class)
public class AMD64StubForeignCallsFeature extends StubForeignCallsFeatureBase {

    private static final EnumSet<CPUFeature> BASELINE = EnumSet.of(SSE2);

    public AMD64StubForeignCallsFeature() {
        super(SVMIntrinsicStubsGen.class, new StubDescriptor[]{
                        new StubDescriptor(Adler32UpdateBytesNode.STUB, Adler32UpdateBytesNode.minFeaturesAMD64(), Adler32UpdateBytesNode.maxFeaturesAMD64()),
                        new StubDescriptor(AESNode.STUBS, AESNode.minFeaturesAMD64(), AESNode.minFeaturesAMD64()),
                        new StubDescriptor(ArrayCompareToForeignCalls.STUBS, BASELINE, RUNTIME_CHECKED_CPU_FEATURES_AMD64),
                        new StubDescriptor(ArrayCopyWithConversionsForeignCalls.STUBS, BASELINE, RUNTIME_CHECKED_CPU_FEATURES_AMD64),
                        new StubDescriptor(ArrayEqualsForeignCalls.STUBS, BASELINE, RUNTIME_CHECKED_CPU_FEATURES_AMD64),
                        new StubDescriptor(ArrayEqualsForeignCalls.STUBS_AMD64, BASELINE, RUNTIME_CHECKED_CPU_FEATURES_AMD64),
                        new StubDescriptor(ArrayEqualsWithMaskForeignCalls.STUBS, BASELINE, RUNTIME_CHECKED_CPU_FEATURES_AMD64),
                        new StubDescriptor(ArrayIndexOfForeignCalls.STUBS, ArrayIndexOfForeignCalls::getMinimumFeaturesAMD64, RUNTIME_CHECKED_CPU_FEATURES_AMD64),
                        new StubDescriptor(ArrayRegionCompareToForeignCalls.STUBS, BASELINE, RUNTIME_CHECKED_CPU_FEATURES_AMD64),
                        new StubDescriptor(Base64DecodeBlockNode.STUB, Base64DecodeBlockNode.minFeaturesAMD64(), Base64DecodeBlockNode.minFeaturesAMD64()),
                        new StubDescriptor(Base64EncodeBlockNode.STUB, Base64EncodeBlockNode.minFeaturesAMD64(), Base64EncodeBlockNode.minFeaturesAMD64()),
                        new StubDescriptor(BigIntegerLeftShiftWorkerNode.STUB, BigIntegerLeftShiftWorkerNode.minFeaturesAMD64(), BigIntegerLeftShiftWorkerNode.minFeaturesAMD64()),
                        new StubDescriptor(BigIntegerMontgomeryMultiplyNode.STUB, BASELINE, BASELINE),
                        new StubDescriptor(BigIntegerMontgomerySquareNode.STUB, BASELINE, BASELINE),
                        new StubDescriptor(BigIntegerMulAddNode.STUB, BASELINE, BIGINTEGER_MUL_ADD_CPU_FEATURES_AMD64),
                        new StubDescriptor(BigIntegerMultiplyToLenNode.STUB, BASELINE, BIGINTEGER_MULTIPLY_TO_LEN_CPU_FEATURES_AMD64),
                        new StubDescriptor(BigIntegerRightShiftWorkerNode.STUB, BigIntegerRightShiftWorkerNode.minFeaturesAMD64(), BigIntegerRightShiftWorkerNode.minFeaturesAMD64()),
                        new StubDescriptor(BigIntegerSquareToLenNode.STUB, BASELINE, BIGINTEGER_MUL_ADD_CPU_FEATURES_AMD64),
                        new StubDescriptor(CalcStringAttributesForeignCalls.STUBS, CalcStringAttributesNode.minFeaturesAMD64(), RUNTIME_CHECKED_CPU_FEATURES_AMD64),
                        new StubDescriptor(ChaCha20Node.STUB, ChaCha20Node.minFeaturesAMD64(), ChaCha20Node.minFeaturesAMD64()),
                        new StubDescriptor(CipherBlockChainingAESNode.STUBS, CipherBlockChainingAESNode.minFeaturesAMD64(), CipherBlockChainingAESNode.minFeaturesAMD64()),
                        new StubDescriptor(CounterModeAESNode.STUB, CounterModeAESNode.minFeaturesAMD64(), CounterModeAESNode.minFeaturesAMD64()),
                        new StubDescriptor(CountPositivesNode.STUB, BASELINE, RUNTIME_CHECKED_CPU_FEATURES_AMD64),
                        new StubDescriptor(CRC32CUpdateBytesNode.STUB, CRC32CUpdateBytesNode.minFeaturesAMD64(), CRC32CUpdateBytesNode.maxFeaturesAMD64()),
                        new StubDescriptor(CRC32UpdateBytesNode.STUB, CRC32UpdateBytesNode.minFeaturesAMD64(), CRC32UpdateBytesNode.maxFeaturesAMD64()),
                        new StubDescriptor(DilithiumAlmostInverseNttNode.STUB, DilithiumAlmostInverseNttNode.minFeaturesAMD64(), DilithiumAlmostInverseNttNode.minFeaturesAMD64()),
                        new StubDescriptor(DilithiumAlmostNttNode.STUB, DilithiumAlmostNttNode.minFeaturesAMD64(), DilithiumAlmostNttNode.minFeaturesAMD64()),
                        new StubDescriptor(DilithiumDecomposePolyNode.STUB, DilithiumDecomposePolyNode.minFeaturesAMD64(), DilithiumDecomposePolyNode.minFeaturesAMD64()),
                        new StubDescriptor(DilithiumMontMulByConstantNode.STUB, DilithiumMontMulByConstantNode.minFeaturesAMD64(), DilithiumMontMulByConstantNode.minFeaturesAMD64()),
                        new StubDescriptor(DilithiumNttMultNode.STUB, DilithiumNttMultNode.minFeaturesAMD64(), DilithiumNttMultNode.minFeaturesAMD64()),
                        new StubDescriptor(DoubleKeccakNode.STUB, DoubleKeccakNode.minFeaturesAMD64(), DoubleKeccakNode.minFeaturesAMD64()),
                        new StubDescriptor(DoubleModStubNode.STUB, DoubleModStubNode.minFeaturesAMD64(), DoubleModStubNode.maxFeaturesAMD64()),
                        new StubDescriptor(ElectronicCodeBookAESNode.STUBS, ElectronicCodeBookAESNode.minFeaturesAMD64(), ElectronicCodeBookAESNode.minFeaturesAMD64()),
                        new StubDescriptor(EncodeArrayNode.STUBS, BASELINE, RUNTIME_CHECKED_CPU_FEATURES_AMD64),
                        new StubDescriptor(GaloisCounterModeAESNode.STUB, GaloisCounterModeAESNode.minFeaturesAMD64(), GaloisCounterModeAESNode.maxFeaturesAMD64()),
                        new StubDescriptor(GHASHProcessBlocksNode.STUB, GHASHProcessBlocksNode.minFeaturesAMD64(), GHASH_CPU_FEATURES_AMD64),
                        new StubDescriptor(IndexOfZeroForeignCalls.STUBS, IndexOfZeroNode.minFeaturesAMD64(), RUNTIME_CHECKED_CPU_FEATURES_AMD64),
                        // Match the SVM plugin predicate and generated stub feature set.
                        new StubDescriptor(IntegerPolynomialAssignNode.STUB, IntegerPolynomialAssignNode.maxFeaturesAMD64(),
                                        IntegerPolynomialAssignNode.maxFeaturesAMD64()),
                        new StubDescriptor(IntegerPolynomialP256MontgomeryMultNode.STUB, IntegerPolynomialP256MontgomeryMultNode.maxFeaturesAMD64(),
                                        IntegerPolynomialP256MontgomeryMultNode.maxFeaturesAMD64()),
                        new StubDescriptor(Kyber12To16Node.STUB, KyberNode.minFeaturesAMD64(), KYBER_CPU_FEATURES_AMD64),
                        new StubDescriptor(KyberAddPoly2Node.STUB, KyberNode.minFeaturesAMD64(), KYBER_CPU_FEATURES_AMD64),
                        new StubDescriptor(KyberAddPoly3Node.STUB, KyberNode.minFeaturesAMD64(), KYBER_CPU_FEATURES_AMD64),
                        new StubDescriptor(KyberBarrettReduceNode.STUB, KyberNode.minFeaturesAMD64(), KYBER_CPU_FEATURES_AMD64),
                        new StubDescriptor(KyberInverseNttNode.STUB, KyberNode.minFeaturesAMD64(), KYBER_CPU_FEATURES_AMD64),
                        new StubDescriptor(KyberNttMultNode.STUB, KyberNode.minFeaturesAMD64(), KYBER_CPU_FEATURES_AMD64),
                        new StubDescriptor(KyberNttNode.STUB, KyberNode.minFeaturesAMD64(), KYBER_CPU_FEATURES_AMD64),
                        new StubDescriptor(MD5Node.STUB, BASELINE, BASELINE),
                        // GR-76192: match the SVM plugin predicate and generated stub feature set.
                        new StubDescriptor(Poly1305ProcessBlocksNode.STUB, Poly1305ProcessBlocksNode.maxFeaturesAMD64(), Poly1305ProcessBlocksNode.maxFeaturesAMD64()),
                        new StubDescriptor(SHA1Node.STUB, SHA1Node.minFeaturesAMD64(), SHA1Node.minFeaturesAMD64()),
                        new StubDescriptor(SHA256Node.STUB, SHA256Node.minFeaturesAMD64(), SHA256Node.minFeaturesAMD64()),
                        new StubDescriptor(SHA3Node.STUB, SHA3Node.minFeaturesAMD64(), SHA3Node.minFeaturesAMD64()),
                        new StubDescriptor(SHA512Node.STUB, SHA512Node.minFeaturesAMD64(), SHA512Node.minFeaturesAMD64()),
                        new StubDescriptor(StringCodepointIndexToByteIndexForeignCalls.STUBS, StringCodepointIndexToByteIndexNode.minFeaturesAMD64(), RUNTIME_CHECKED_CPU_FEATURES_AMD64),
                        new StubDescriptor(StringLatin1InflateNode.STUB, BASELINE, RUNTIME_CHECKED_CPU_FEATURES_AMD64),
                        new StubDescriptor(StringUTF16CompressNode.STUB, BASELINE, RUNTIME_CHECKED_CPU_FEATURES_AMD64),
                        new StubDescriptor(VectorizedHashCodeNode.STUBS, VectorizedHashCodeNode.minFeaturesAMD64(), VectorizedHashCodeNode.minFeaturesAMD64()),
                        new StubDescriptor(VectorizedMismatchNode.STUB, BASELINE, RUNTIME_CHECKED_CPU_FEATURES_AMD64),
        });
    }
}
