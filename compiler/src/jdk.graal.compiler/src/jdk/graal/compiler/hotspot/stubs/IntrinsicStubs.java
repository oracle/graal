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
package jdk.graal.compiler.hotspot.stubs;

import jdk.graal.compiler.lir.GeneratedStubsHolder;
import jdk.graal.compiler.replacements.nodes.DoubleModStubNode;
import jdk.graal.compiler.replacements.StringLatin1InflateNode;
import jdk.graal.compiler.replacements.StringUTF16CompressNode;
import jdk.graal.compiler.replacements.nodes.AESNode;
import jdk.graal.compiler.replacements.nodes.Adler32UpdateBytesNode;
import jdk.graal.compiler.replacements.nodes.ArrayCompareToNode;
import jdk.graal.compiler.replacements.nodes.ArrayCopyWithConversionsNode;
import jdk.graal.compiler.replacements.nodes.ArrayEqualsNode;
import jdk.graal.compiler.replacements.nodes.ArrayFillNode;
import jdk.graal.compiler.replacements.nodes.ArrayIndexOfNode;
import jdk.graal.compiler.replacements.nodes.ArrayRegionCompareToNode;
import jdk.graal.compiler.replacements.nodes.ArrayRegionEqualsNode;
import jdk.graal.compiler.replacements.nodes.ArrayRegionEqualsWithMaskNode;
import jdk.graal.compiler.replacements.nodes.Base64DecodeBlockNode;
import jdk.graal.compiler.replacements.nodes.Base64EncodeBlockNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerMulAddNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerLeftShiftWorkerNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerMontgomeryMultiplyNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerMontgomerySquareNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerMultiplyToLenNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerRightShiftWorkerNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerSquareToLenNode;
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
import jdk.graal.compiler.replacements.nodes.ElectronicCodeBookAESNode;
import jdk.graal.compiler.replacements.nodes.EncodeArrayNode;
import jdk.graal.compiler.replacements.nodes.GaloisCounterModeAESNode;
import jdk.graal.compiler.replacements.nodes.GHASHProcessBlocksNode;
import jdk.graal.compiler.replacements.nodes.IndexOfZeroNode;
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
import jdk.graal.compiler.replacements.nodes.StringCodepointIndexToByteIndexNode;
import jdk.graal.compiler.replacements.nodes.VectorizedHashCodeNode;
import jdk.graal.compiler.replacements.nodes.VectorizedMismatchNode;

@GeneratedStubsHolder(targetVM = "hotspot", sources = {
                Adler32UpdateBytesNode.class,
                AESNode.class,
                ArrayCompareToNode.class,
                ArrayCopyWithConversionsNode.class,
                ArrayEqualsNode.class,
                ArrayFillNode.class,
                ArrayIndexOfNode.class,
                ArrayRegionCompareToNode.class,
                ArrayRegionEqualsNode.class,
                ArrayRegionEqualsWithMaskNode.class,
                Base64DecodeBlockNode.class,
                Base64EncodeBlockNode.class,
                BigIntegerLeftShiftWorkerNode.class,
                BigIntegerMontgomeryMultiplyNode.class,
                BigIntegerMontgomerySquareNode.class,
                BigIntegerMulAddNode.class,
                BigIntegerMultiplyToLenNode.class,
                BigIntegerRightShiftWorkerNode.class,
                BigIntegerSquareToLenNode.class,
                CalcStringAttributesNode.class,
                ChaCha20Node.class,
                CipherBlockChainingAESNode.class,
                CounterModeAESNode.class,
                CountPositivesNode.class,
                CRC32CUpdateBytesNode.class,
                CRC32UpdateBytesNode.class,
                DilithiumAlmostInverseNttNode.class,
                DilithiumAlmostNttNode.class,
                DilithiumDecomposePolyNode.class,
                DilithiumMontMulByConstantNode.class,
                DilithiumNttMultNode.class,
                DoubleModStubNode.class,
                ElectronicCodeBookAESNode.class,
                EncodeArrayNode.class,
                GaloisCounterModeAESNode.class,
                GHASHProcessBlocksNode.class,
                IndexOfZeroNode.class,
                KyberNttNode.class,
                KyberInverseNttNode.class,
                KyberNttMultNode.class,
                KyberAddPoly2Node.class,
                KyberAddPoly3Node.class,
                Kyber12To16Node.class,
                KyberBarrettReduceNode.class,
                MD5Node.class,
                Poly1305ProcessBlocksNode.class,
                SHA1Node.class,
                SHA256Node.class,
                SHA3Node.class,
                SHA512Node.class,
                StringCodepointIndexToByteIndexNode.class,
                StringLatin1InflateNode.class,
                StringUTF16CompressNode.class,
                VectorizedHashCodeNode.class,
                VectorizedMismatchNode.class,
})
public final class IntrinsicStubs {
}
