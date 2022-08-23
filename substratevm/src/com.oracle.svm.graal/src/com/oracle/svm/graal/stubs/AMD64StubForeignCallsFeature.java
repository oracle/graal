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

import static com.oracle.svm.core.cpufeature.Stubs.AMD64Features.AES_CPU_FEATURES_AMD64;
import static com.oracle.svm.core.cpufeature.Stubs.AMD64Features.RUNTIME_CHECKED_CPU_FEATURES_AMD64;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE2;

import java.util.EnumSet;

import org.graalvm.compiler.replacements.amd64.AMD64ArrayEqualsWithMaskForeignCalls;
import org.graalvm.compiler.replacements.amd64.AMD64CalcStringAttributesForeignCalls;
import org.graalvm.compiler.replacements.amd64.AMD64CalcStringAttributesNode;
import org.graalvm.compiler.replacements.nodes.ArrayCompareToForeignCalls;
import org.graalvm.compiler.replacements.nodes.ArrayCopyWithConversionsForeignCalls;
import org.graalvm.compiler.replacements.nodes.ArrayEqualsForeignCalls;
import org.graalvm.compiler.replacements.nodes.ArrayIndexOfForeignCalls;
import org.graalvm.compiler.replacements.nodes.ArrayRegionCompareToForeignCalls;
import org.graalvm.compiler.replacements.nodes.CryptoForeignCalls;
import org.graalvm.compiler.replacements.nodes.VectorizedMismatchForeignCalls;
import org.graalvm.nativeimage.Platform.AMD64;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.AutomaticFeature;

import jdk.vm.ci.amd64.AMD64.CPUFeature;

@AutomaticFeature
@Platforms(AMD64.class)
public class AMD64StubForeignCallsFeature extends StubForeignCallsFeatureBase {

    private static final EnumSet<CPUFeature> BASELINE = EnumSet.of(SSE2);

    public AMD64StubForeignCallsFeature() {
        super(new StubDescriptor[]{
                        new StubDescriptor(AMD64ArrayEqualsWithMaskForeignCalls.STUBS, true, BASELINE, RUNTIME_CHECKED_CPU_FEATURES_AMD64),
                        new StubDescriptor(AMD64CalcStringAttributesForeignCalls.STUBS, true, AMD64CalcStringAttributesNode.minFeaturesAMD64(), RUNTIME_CHECKED_CPU_FEATURES_AMD64),
                        new StubDescriptor(ArrayCompareToForeignCalls.STUBS, true, BASELINE, RUNTIME_CHECKED_CPU_FEATURES_AMD64),
                        new StubDescriptor(ArrayCopyWithConversionsForeignCalls.STUBS, false, BASELINE, RUNTIME_CHECKED_CPU_FEATURES_AMD64),
                        new StubDescriptor(ArrayEqualsForeignCalls.STUBS, true, BASELINE, RUNTIME_CHECKED_CPU_FEATURES_AMD64),
                        new StubDescriptor(ArrayIndexOfForeignCalls.STUBS_AMD64, true, BASELINE, RUNTIME_CHECKED_CPU_FEATURES_AMD64),
                        new StubDescriptor(ArrayRegionCompareToForeignCalls.STUBS, true, BASELINE, RUNTIME_CHECKED_CPU_FEATURES_AMD64),
                        new StubDescriptor(VectorizedMismatchForeignCalls.STUB, true, BASELINE, RUNTIME_CHECKED_CPU_FEATURES_AMD64),
                        new StubDescriptor(VectorizedMismatchForeignCalls.STUB, true, BASELINE, RUNTIME_CHECKED_CPU_FEATURES_AMD64),
                        new StubDescriptor(CryptoForeignCalls.STUBS, false, AES_CPU_FEATURES_AMD64, AES_CPU_FEATURES_AMD64),
        });
    }
}
