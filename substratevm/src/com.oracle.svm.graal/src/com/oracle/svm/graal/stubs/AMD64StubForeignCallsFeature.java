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

import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE2;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE4_1;

import org.graalvm.compiler.replacements.amd64.AMD64ArrayEqualsWithMaskForeignCalls;
import org.graalvm.compiler.replacements.amd64.AMD64CalcStringAttributesForeignCalls;
import org.graalvm.compiler.replacements.nodes.ArrayCompareToForeignCalls;
import org.graalvm.compiler.replacements.nodes.ArrayCopyWithConversionsForeignCalls;
import org.graalvm.compiler.replacements.nodes.ArrayEqualsForeignCalls;
import org.graalvm.compiler.replacements.nodes.ArrayIndexOfForeignCalls;
import org.graalvm.compiler.replacements.nodes.ArrayRegionCompareToForeignCalls;
import org.graalvm.nativeimage.Platform.AMD64;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.AutomaticFeature;

@AutomaticFeature
@Platforms(AMD64.class)
public class AMD64StubForeignCallsFeature extends StubForeignCallsFeatureBase {

    public AMD64StubForeignCallsFeature() {
        super(new StubDescriptor[]{
                        new StubDescriptor(AMD64ArrayEqualsWithMaskForeignCalls.STUBS, SVMAMD64ArrayEqualsWithMaskForeignCalls.class, true, SSE2),
                        new StubDescriptor(AMD64CalcStringAttributesForeignCalls.STUBS, SVMAMD64CalcStringAttributesForeignCalls.class, true, SSE4_1),
                        new StubDescriptor(ArrayCompareToForeignCalls.STUBS, SVMArrayCompareToForeignCalls.class, true, SSE2),
                        new StubDescriptor(ArrayCopyWithConversionsForeignCalls.STUBS, SVMArrayCopyWithConversionsForeignCalls.class, false, SSE2),
                        new StubDescriptor(ArrayEqualsForeignCalls.STUBS, SVMArrayEqualsForeignCalls.class, true, SSE2),
                        new StubDescriptor(ArrayIndexOfForeignCalls.STUBS_AMD64, SVMArrayIndexOfForeignCalls.class, true, SSE2),
                        new StubDescriptor(ArrayRegionCompareToForeignCalls.STUBS, SVMArrayRegionCompareToForeignCalls.class, true, SSE2),
        });
    }
}
