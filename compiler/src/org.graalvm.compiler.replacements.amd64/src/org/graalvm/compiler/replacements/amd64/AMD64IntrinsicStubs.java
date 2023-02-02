/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.EnumSet;

import org.graalvm.compiler.asm.amd64.AVXKind;
import org.graalvm.compiler.core.common.Stride;
import org.graalvm.compiler.core.common.StrideUtil;
import org.graalvm.compiler.lir.amd64.AMD64ArrayEqualsOp;
import org.graalvm.compiler.lir.gen.LIRGenerator;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.replacements.nodes.ArrayRegionEqualsWithMaskNode;
import org.graalvm.compiler.replacements.nodes.ArrayEqualsNode;
import org.graalvm.compiler.replacements.nodes.ArrayRegionEqualsNode;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.meta.JavaKind;

public final class AMD64IntrinsicStubs {

    /**
     * Returns {@code true} if the given intrinsic node should not be converted to a stub call.
     */
    public static boolean shouldInlineIntrinsic(ValueNode valueNode, LIRGenerator gen) {
        if (valueNode instanceof ArrayEqualsNode) {
            return shouldInline((ArrayEqualsNode) valueNode, gen);
        } else if (valueNode instanceof ArrayRegionEqualsNode) {
            return shouldInline((ArrayRegionEqualsNode) valueNode, gen);
        } else if (valueNode instanceof ArrayRegionEqualsWithMaskNode) {
            return shouldInline((ArrayRegionEqualsWithMaskNode) valueNode, gen);
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static boolean shouldInline(ArrayEqualsNode arrayEqualsNode, LIRGenerator gen) {
        Stride stride = Stride.fromJavaKind(arrayEqualsNode.getKind());
        ValueNode length = arrayEqualsNode.getLength();
        return length.isJavaConstant() && AMD64ArrayEqualsOp.canGenerateConstantLengthCompare(
                        gen.target(),
                        (EnumSet<AMD64.CPUFeature>) arrayEqualsNode.getRuntimeCheckedCPUFeatures(),
                        arrayEqualsNode.getKind(), stride, stride,
                        length.asJavaConstant().asInt(),
                        (AVXKind.AVXSize) gen.getMaxVectorSize(arrayEqualsNode.getRuntimeCheckedCPUFeatures()));
    }

    @SuppressWarnings("unchecked")
    private static boolean shouldInline(ArrayRegionEqualsNode regionEqualsNode, LIRGenerator gen) {
        ValueNode length = regionEqualsNode.getLength();
        int directStubCallIndex = regionEqualsNode.getDirectStubCallIndex();
        return directStubCallIndex >= 0 && length.isJavaConstant() && AMD64ArrayEqualsOp.canGenerateConstantLengthCompare(
                        gen.target(),
                        (EnumSet<AMD64.CPUFeature>) regionEqualsNode.getRuntimeCheckedCPUFeatures(),
                        JavaKind.Byte,
                        StrideUtil.getConstantStrideA(directStubCallIndex),
                        StrideUtil.getConstantStrideB(directStubCallIndex),
                        length.asJavaConstant().asInt(),
                        (AVXKind.AVXSize) gen.getMaxVectorSize(regionEqualsNode.getRuntimeCheckedCPUFeatures()));
    }

    @SuppressWarnings("unchecked")
    private static boolean shouldInline(ArrayRegionEqualsWithMaskNode node, LIRGenerator gen) {
        ValueNode length = node.getLength();
        if (node.getDirectStubCallIndex() >= 0 && length.isJavaConstant() && AMD64ArrayEqualsOp.canGenerateConstantLengthCompare(
                        gen.target(),
                        (EnumSet<AMD64.CPUFeature>) node.getRuntimeCheckedCPUFeatures(),
                        JavaKind.Byte,
                        StrideUtil.getConstantStrideA(node.getDirectStubCallIndex()),
                        StrideUtil.getConstantStrideB(node.getDirectStubCallIndex()),
                        length.asJavaConstant().asInt(), (AVXKind.AVXSize) gen.getMaxVectorSize(node.getRuntimeCheckedCPUFeatures()))) {
            // Yield constant-length arrays comparison assembly
            return true;
        }
        return false;
    }
}
