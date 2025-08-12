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

import static com.oracle.svm.core.cpufeature.Stubs.AMD64Features.BIGINTEGER_MULTIPLY_TO_LEN_CPU_FEATURES_AMD64;
import static com.oracle.svm.core.cpufeature.Stubs.AMD64Features.BIGINTEGER_MUL_ADD_CPU_FEATURES_AMD64;
import static com.oracle.svm.core.cpufeature.Stubs.AMD64Features.GHASH_CPU_FEATURES_AMD64;
import static com.oracle.svm.core.cpufeature.Stubs.AMD64Features.RUNTIME_CHECKED_CPU_FEATURES_AMD64;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE2;

import java.util.EnumSet;

import org.graalvm.nativeimage.Platform.AMD64;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.layeredimagesingleton.FeatureSingleton;
import com.oracle.svm.core.layeredimagesingleton.UnsavedSingleton;
import com.oracle.svm.core.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.core.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.Independent;
import com.oracle.svm.core.traits.SingletonTraits;

import jdk.graal.compiler.replacements.StringLatin1InflateNode;
import jdk.graal.compiler.replacements.StringUTF16CompressNode;
import jdk.graal.compiler.replacements.nodes.AESNode;
import jdk.graal.compiler.replacements.nodes.ArrayCompareToForeignCalls;
import jdk.graal.compiler.replacements.nodes.ArrayCopyWithConversionsForeignCalls;
import jdk.graal.compiler.replacements.nodes.ArrayEqualsForeignCalls;
import jdk.graal.compiler.replacements.nodes.ArrayEqualsWithMaskForeignCalls;
import jdk.graal.compiler.replacements.nodes.ArrayIndexOfForeignCalls;
import jdk.graal.compiler.replacements.nodes.ArrayRegionCompareToForeignCalls;
import jdk.graal.compiler.replacements.nodes.BigIntegerMulAddNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerMultiplyToLenNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerSquareToLenNode;
import jdk.graal.compiler.replacements.nodes.CalcStringAttributesForeignCalls;
import jdk.graal.compiler.replacements.nodes.CalcStringAttributesNode;
import jdk.graal.compiler.replacements.nodes.CipherBlockChainingAESNode;
import jdk.graal.compiler.replacements.nodes.CountPositivesNode;
import jdk.graal.compiler.replacements.nodes.CounterModeAESNode;
import jdk.graal.compiler.replacements.nodes.EncodeArrayNode;
import jdk.graal.compiler.replacements.nodes.GHASHProcessBlocksNode;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode.MD5Node;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode.SHA1Node;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode.SHA256Node;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode.SHA3Node;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode.SHA512Node;
import jdk.graal.compiler.replacements.nodes.StringCodepointIndexToByteIndexForeignCalls;
import jdk.graal.compiler.replacements.nodes.StringCodepointIndexToByteIndexNode;
import jdk.graal.compiler.replacements.nodes.VectorizedHashCodeNode;
import jdk.graal.compiler.replacements.nodes.VectorizedMismatchNode;
import jdk.vm.ci.amd64.AMD64.CPUFeature;

@AutomaticallyRegisteredFeature
@Platforms(AMD64.class)
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = Independent.class)
public class AMD64StubForeignCallsFeature extends StubForeignCallsFeatureBase implements FeatureSingleton, UnsavedSingleton {

    private static final EnumSet<CPUFeature> BASELINE = EnumSet.of(SSE2);

    public AMD64StubForeignCallsFeature() {
        super(SVMIntrinsicStubsGen.class, new StubDescriptor[]{
                        new StubDescriptor(ArrayEqualsWithMaskForeignCalls.STUBS, BASELINE, RUNTIME_CHECKED_CPU_FEATURES_AMD64),
                        new StubDescriptor(CalcStringAttributesForeignCalls.STUBS, CalcStringAttributesNode.minFeaturesAMD64(), RUNTIME_CHECKED_CPU_FEATURES_AMD64),
                        new StubDescriptor(ArrayCompareToForeignCalls.STUBS, BASELINE, RUNTIME_CHECKED_CPU_FEATURES_AMD64),
                        new StubDescriptor(ArrayCopyWithConversionsForeignCalls.STUBS, BASELINE, RUNTIME_CHECKED_CPU_FEATURES_AMD64),
                        new StubDescriptor(ArrayEqualsForeignCalls.STUBS, BASELINE, RUNTIME_CHECKED_CPU_FEATURES_AMD64),
                        new StubDescriptor(ArrayEqualsForeignCalls.STUBS_AMD64, BASELINE, RUNTIME_CHECKED_CPU_FEATURES_AMD64),
                        new StubDescriptor(ArrayIndexOfForeignCalls.STUBS, ArrayIndexOfForeignCalls::getMinimumFeaturesAMD64, RUNTIME_CHECKED_CPU_FEATURES_AMD64),
                        new StubDescriptor(ArrayRegionCompareToForeignCalls.STUBS, BASELINE, RUNTIME_CHECKED_CPU_FEATURES_AMD64),
                        new StubDescriptor(StringLatin1InflateNode.STUB, BASELINE, RUNTIME_CHECKED_CPU_FEATURES_AMD64),
                        new StubDescriptor(StringUTF16CompressNode.STUB, BASELINE, RUNTIME_CHECKED_CPU_FEATURES_AMD64),
                        new StubDescriptor(StringCodepointIndexToByteIndexForeignCalls.STUBS, StringCodepointIndexToByteIndexNode.minFeaturesAMD64(), RUNTIME_CHECKED_CPU_FEATURES_AMD64),
                        new StubDescriptor(EncodeArrayNode.STUBS, BASELINE, RUNTIME_CHECKED_CPU_FEATURES_AMD64),
                        new StubDescriptor(CountPositivesNode.STUB, BASELINE, RUNTIME_CHECKED_CPU_FEATURES_AMD64),
                        new StubDescriptor(VectorizedMismatchNode.STUB, BASELINE, RUNTIME_CHECKED_CPU_FEATURES_AMD64),
                        new StubDescriptor(VectorizedHashCodeNode.STUBS, VectorizedHashCodeNode.minFeaturesAMD64(), VectorizedHashCodeNode.minFeaturesAMD64()),
                        new StubDescriptor(AESNode.STUBS, AESNode.minFeaturesAMD64(), AESNode.minFeaturesAMD64()),
                        new StubDescriptor(CounterModeAESNode.STUB, CounterModeAESNode.minFeaturesAMD64(), CounterModeAESNode.minFeaturesAMD64()),
                        new StubDescriptor(CipherBlockChainingAESNode.STUBS, CipherBlockChainingAESNode.minFeaturesAMD64(), CipherBlockChainingAESNode.minFeaturesAMD64()),
                        new StubDescriptor(GHASHProcessBlocksNode.STUB, GHASHProcessBlocksNode.minFeaturesAMD64(), GHASH_CPU_FEATURES_AMD64),
                        new StubDescriptor(BigIntegerMultiplyToLenNode.STUB, BASELINE, BIGINTEGER_MULTIPLY_TO_LEN_CPU_FEATURES_AMD64),
                        new StubDescriptor(BigIntegerMulAddNode.STUB, BASELINE, BIGINTEGER_MUL_ADD_CPU_FEATURES_AMD64),
                        new StubDescriptor(BigIntegerSquareToLenNode.STUB, BASELINE, BIGINTEGER_MUL_ADD_CPU_FEATURES_AMD64),
                        new StubDescriptor(SHA1Node.STUB, SHA1Node.minFeaturesAMD64(), SHA1Node.minFeaturesAMD64()),
                        new StubDescriptor(SHA256Node.STUB, SHA256Node.minFeaturesAMD64(), SHA256Node.minFeaturesAMD64()),
                        new StubDescriptor(SHA3Node.STUB, SHA3Node.minFeaturesAMD64(), SHA3Node.minFeaturesAMD64()),
                        new StubDescriptor(SHA512Node.STUB, SHA512Node.minFeaturesAMD64(), SHA512Node.minFeaturesAMD64()),
                        new StubDescriptor(MD5Node.STUB, BASELINE, BASELINE),
        });
    }
}
