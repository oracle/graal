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
import jdk.graal.compiler.replacements.nodes.BigIntegerMontgomeryMultiplyNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerMontgomerySquareNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerMulAddNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerMultiplyToLenNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerRightShiftWorkerNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerSquareToLenNode;
import jdk.graal.compiler.replacements.nodes.CRC32CUpdateBytesNode;
import jdk.graal.compiler.replacements.nodes.CRC32UpdateBytesNode;
import jdk.graal.compiler.replacements.nodes.CalcStringAttributesForeignCalls;
import jdk.graal.compiler.replacements.nodes.ChaCha20Node;
import jdk.graal.compiler.replacements.nodes.CipherBlockChainingAESNode;
import jdk.graal.compiler.replacements.nodes.CountPositivesNode;
import jdk.graal.compiler.replacements.nodes.CounterModeAESNode;
import jdk.graal.compiler.replacements.nodes.DilithiumNode.DilithiumAlmostInverseNttNode;
import jdk.graal.compiler.replacements.nodes.DilithiumNode.DilithiumAlmostNttNode;
import jdk.graal.compiler.replacements.nodes.DilithiumNode.DilithiumDecomposePolyNode;
import jdk.graal.compiler.replacements.nodes.DilithiumNode.DilithiumMontMulByConstantNode;
import jdk.graal.compiler.replacements.nodes.DilithiumNode.DilithiumNttMultNode;
import jdk.graal.compiler.replacements.nodes.DoubleKeccakNode;
import jdk.graal.compiler.replacements.nodes.EncodeArrayNode;
import jdk.graal.compiler.replacements.nodes.GHASHProcessBlocksNode;
import jdk.graal.compiler.replacements.nodes.GaloisCounterModeAESNode;
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
        super(SVMIntrinsicStubsGen.class, SVMIntrinsicStubsGen::getMinimumCPUFeatures, SVMIntrinsicStubsGen::getRuntimeCPUFeatures, new StubDescriptor[]{
                        new StubDescriptor(Adler32UpdateBytesNode.STUB),
                        new StubDescriptor(AESNode.STUBS),
                        new StubDescriptor(ArrayCompareToForeignCalls.STUBS),
                        new StubDescriptor(ArrayCopyWithConversionsForeignCalls.STUBS),
                        new StubDescriptor(ArrayEqualsForeignCalls.STUBS),
                        new StubDescriptor(ArrayEqualsWithMaskForeignCalls.STUBS),
                        new StubDescriptor(ArrayFillNode.STUBS),
                        new StubDescriptor(ArrayIndexOfForeignCalls.STUBS),
                        new StubDescriptor(ArrayRegionCompareToForeignCalls.STUBS),
                        new StubDescriptor(Base64DecodeBlockNode.STUB),
                        new StubDescriptor(Base64EncodeBlockNode.STUB),
                        new StubDescriptor(BigIntegerLeftShiftWorkerNode.STUB),
                        new StubDescriptor(BigIntegerMontgomeryMultiplyNode.STUB),
                        new StubDescriptor(BigIntegerMontgomerySquareNode.STUB),
                        new StubDescriptor(BigIntegerMulAddNode.STUB),
                        new StubDescriptor(BigIntegerMultiplyToLenNode.STUB),
                        new StubDescriptor(BigIntegerRightShiftWorkerNode.STUB),
                        new StubDescriptor(BigIntegerSquareToLenNode.STUB),
                        new StubDescriptor(CalcStringAttributesForeignCalls.STUBS),
                        new StubDescriptor(ChaCha20Node.STUB),
                        new StubDescriptor(CipherBlockChainingAESNode.STUBS),
                        new StubDescriptor(CounterModeAESNode.STUB),
                        new StubDescriptor(CountPositivesNode.STUB),
                        new StubDescriptor(CRC32CUpdateBytesNode.STUB),
                        new StubDescriptor(CRC32UpdateBytesNode.STUB),
                        new StubDescriptor(DilithiumAlmostInverseNttNode.STUB),
                        new StubDescriptor(DilithiumAlmostNttNode.STUB),
                        new StubDescriptor(DilithiumDecomposePolyNode.STUB),
                        new StubDescriptor(DilithiumMontMulByConstantNode.STUB),
                        new StubDescriptor(DilithiumNttMultNode.STUB),
                        new StubDescriptor(DoubleKeccakNode.STUB),
                        new StubDescriptor(EncodeArrayNode.STUBS),
                        new StubDescriptor(GaloisCounterModeAESNode.STUB),
                        new StubDescriptor(GHASHProcessBlocksNode.STUB),
                        new StubDescriptor(IndexOfZeroForeignCalls.STUBS),
                        new StubDescriptor(Kyber12To16Node.STUB),
                        new StubDescriptor(KyberAddPoly2Node.STUB),
                        new StubDescriptor(KyberAddPoly3Node.STUB),
                        new StubDescriptor(KyberBarrettReduceNode.STUB),
                        new StubDescriptor(KyberInverseNttNode.STUB),
                        new StubDescriptor(KyberNttMultNode.STUB),
                        new StubDescriptor(KyberNttNode.STUB),
                        new StubDescriptor(MD5MultiBlockNode.STUB),
                        new StubDescriptor(MD5Node.STUB),
                        new StubDescriptor(Poly1305ProcessBlocksNode.STUB),
                        new StubDescriptor(SHA1MultiBlockNode.STUB),
                        new StubDescriptor(SHA1Node.STUB),
                        new StubDescriptor(SHA256MultiBlockNode.STUB),
                        new StubDescriptor(SHA256Node.STUB),
                        new StubDescriptor(SHA3MultiBlockNode.STUB),
                        new StubDescriptor(SHA3Node.STUB),
                        new StubDescriptor(SHA512MultiBlockNode.STUB),
                        new StubDescriptor(SHA512Node.STUB),
                        new StubDescriptor(StringLatin1InflateNode.STUB),
                        new StubDescriptor(StringUTF16CompressNode.STUB),
                        new StubDescriptor(VectorizedHashCodeNode.STUBS),
                        new StubDescriptor(VectorizedMismatchNode.STUB),
        });
    }
}
