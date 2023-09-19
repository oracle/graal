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
package com.oracle.svm.graal.stubs;

import static com.oracle.svm.core.cpufeature.Stubs.AArch64Features.EMPTY_CPU_FEATURES_AARCH64;

import org.graalvm.compiler.replacements.StringLatin1InflateNode;
import org.graalvm.compiler.replacements.StringUTF16CompressNode;
import org.graalvm.compiler.replacements.nodes.AESNode;
import org.graalvm.compiler.replacements.nodes.ArrayCompareToForeignCalls;
import org.graalvm.compiler.replacements.nodes.ArrayCopyWithConversionsForeignCalls;
import org.graalvm.compiler.replacements.nodes.ArrayEqualsForeignCalls;
import org.graalvm.compiler.replacements.nodes.ArrayEqualsWithMaskForeignCalls;
import org.graalvm.compiler.replacements.nodes.ArrayIndexOfForeignCalls;
import org.graalvm.compiler.replacements.nodes.ArrayRegionCompareToForeignCalls;
import org.graalvm.compiler.replacements.nodes.BigIntegerMulAddNode;
import org.graalvm.compiler.replacements.nodes.BigIntegerMultiplyToLenNode;
import org.graalvm.compiler.replacements.nodes.BigIntegerSquareToLenNode;
import org.graalvm.compiler.replacements.nodes.CalcStringAttributesForeignCalls;
import org.graalvm.compiler.replacements.nodes.CalcStringAttributesNode;
import org.graalvm.compiler.replacements.nodes.CipherBlockChainingAESNode;
import org.graalvm.compiler.replacements.nodes.CountPositivesNode;
import org.graalvm.compiler.replacements.nodes.CounterModeAESNode;
import org.graalvm.compiler.replacements.nodes.EncodeArrayNode;
import org.graalvm.compiler.replacements.nodes.GHASHProcessBlocksNode;
import org.graalvm.compiler.replacements.nodes.MessageDigestNode.MD5Node;
import org.graalvm.compiler.replacements.nodes.MessageDigestNode.SHA1Node;
import org.graalvm.compiler.replacements.nodes.MessageDigestNode.SHA256Node;
import org.graalvm.compiler.replacements.nodes.MessageDigestNode.SHA3Node;
import org.graalvm.compiler.replacements.nodes.MessageDigestNode.SHA512Node;
import org.graalvm.compiler.replacements.nodes.VectorizedMismatchNode;
import org.graalvm.nativeimage.Platform.AARCH64;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;

@AutomaticallyRegisteredFeature
@Platforms(AARCH64.class)
public class AArch64StubForeignCallsFeature extends StubForeignCallsFeatureBase {

    public AArch64StubForeignCallsFeature() {
        super(SVMIntrinsicStubsGen.class, new StubDescriptor[]{
                        new StubDescriptor(ArrayCopyWithConversionsForeignCalls.STUBS, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(ArrayCompareToForeignCalls.STUBS, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(ArrayRegionCompareToForeignCalls.STUBS, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(ArrayEqualsForeignCalls.STUBS, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(ArrayEqualsWithMaskForeignCalls.STUBS, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(ArrayIndexOfForeignCalls.STUBS, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(CalcStringAttributesForeignCalls.STUBS, CalcStringAttributesNode.minFeaturesAARCH64(), EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(StringLatin1InflateNode.STUB, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(StringUTF16CompressNode.STUB, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(EncodeArrayNode.STUBS, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(CountPositivesNode.STUB, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(VectorizedMismatchNode.STUB, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(AESNode.STUBS, AESNode.minFeaturesAARCH64(), AESNode.minFeaturesAARCH64()),
                        new StubDescriptor(CounterModeAESNode.STUB, CounterModeAESNode.minFeaturesAARCH64(), CounterModeAESNode.minFeaturesAARCH64()),
                        new StubDescriptor(CipherBlockChainingAESNode.STUBS, CipherBlockChainingAESNode.minFeaturesAARCH64(), CipherBlockChainingAESNode.minFeaturesAARCH64()),
                        new StubDescriptor(GHASHProcessBlocksNode.STUB, GHASHProcessBlocksNode.minFeaturesAARCH64(), GHASHProcessBlocksNode.minFeaturesAARCH64()),
                        new StubDescriptor(BigIntegerMultiplyToLenNode.STUB, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(BigIntegerMulAddNode.STUB, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(BigIntegerSquareToLenNode.STUB, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
                        new StubDescriptor(SHA1Node.STUB, SHA1Node.minFeaturesAARCH64(), SHA1Node.minFeaturesAARCH64()),
                        new StubDescriptor(SHA256Node.STUB, SHA256Node.minFeaturesAARCH64(), SHA256Node.minFeaturesAARCH64()),
                        new StubDescriptor(SHA3Node.STUB, SHA3Node.minFeaturesAARCH64(), SHA3Node.minFeaturesAARCH64()),
                        new StubDescriptor(SHA512Node.STUB, SHA512Node.minFeaturesAARCH64(), SHA512Node.minFeaturesAARCH64()),
                        new StubDescriptor(MD5Node.STUB, EMPTY_CPU_FEATURES_AARCH64, EMPTY_CPU_FEATURES_AARCH64),
        });
    }
}
