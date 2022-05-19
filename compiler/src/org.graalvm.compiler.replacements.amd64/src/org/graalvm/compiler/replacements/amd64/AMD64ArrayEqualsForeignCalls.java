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
package org.graalvm.compiler.replacements.amd64;

import org.graalvm.compiler.asm.amd64.AVXKind;
import org.graalvm.compiler.core.common.StrideUtil;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.amd64.AMD64ArrayEqualsOp;
import org.graalvm.compiler.lir.gen.LIRGenerator;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.replacements.nodes.ArrayEqualsForeignCalls;
import org.graalvm.compiler.replacements.nodes.ArrayEqualsNode;
import org.graalvm.compiler.replacements.nodes.ArrayRegionEqualsNode;

import jdk.vm.ci.meta.JavaKind;

public final class AMD64ArrayEqualsForeignCalls {

    public static ForeignCallDescriptor getArrayEqualsStub(ArrayEqualsNode arrayEqualsNode, LIRGenerator gen) {
        JavaKind stride = arrayEqualsNode.getKind();
        ValueNode length = arrayEqualsNode.getLength();
        if (length.isJavaConstant() && AMD64ArrayEqualsOp.canGenerateConstantLengthCompare(gen.target(), stride, stride, length.asJavaConstant().asInt(), (AVXKind.AVXSize) gen.getMaxVectorSize())) {
            // Yield constant-length arrays comparison assembly
            return null;
        }
        return ArrayEqualsForeignCalls.getArrayEqualsStub(arrayEqualsNode);
    }

    public static ForeignCallDescriptor getRegionEqualsStub(ArrayRegionEqualsNode regionEqualsNode, LIRGenerator gen) {
        int directStubCallIndex = regionEqualsNode.getDirectStubCallIndex();
        GraalError.guarantee(-1 <= directStubCallIndex && directStubCallIndex < 9, "invalid direct stub call index");
        ValueNode length = regionEqualsNode.getLength();
        if (directStubCallIndex >= 0 && length.isJavaConstant() &&
                        AMD64ArrayEqualsOp.canGenerateConstantLengthCompare(gen.target(),
                                        StrideUtil.getConstantStrideA(directStubCallIndex),
                                        StrideUtil.getConstantStrideB(directStubCallIndex), length.asJavaConstant().asInt(), (AVXKind.AVXSize) gen.getMaxVectorSize())) {
            // Yield constant-length arrays comparison assembly
            return null;
        }
        return ArrayEqualsForeignCalls.getRegionEqualsStub(regionEqualsNode);
    }
}
