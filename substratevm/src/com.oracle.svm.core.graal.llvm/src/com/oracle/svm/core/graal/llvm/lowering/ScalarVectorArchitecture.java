/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.llvm.lowering;

import java.util.Objects;

import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.core.common.calc.FloatConvert;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.IntegerConvertOp;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.calc.NarrowableArithmeticNode;
import jdk.graal.compiler.nodes.calc.ShiftNode;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;

/**
 * Describes the LLVM backend to Graal's vector optimizations without claiming any target-specific
 * SIMD support. LLVM may still vectorize the generated bitcode independently.
 */
public final class ScalarVectorArchitecture extends VectorArchitecture {

    private final int objectAlignment;

    public ScalarVectorArchitecture(int referenceSize, boolean compressedReferences, int objectAlignment) {
        /* Scalar object accesses still need the reference stride for vector loop lowering. */
        super(referenceSize, compressedReferences, true);
        this.objectAlignment = objectAlignment;
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o) && objectAlignment == ((ScalarVectorArchitecture) o).objectAlignment;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), objectAlignment);
    }

    @Override
    public int getMaxVectorLength(Stamp stamp) {
        return 1;
    }

    @Override
    public int getMaxLogicVectorLength(Stamp stamp) {
        return 1;
    }

    @Override
    public int getShortestCoveringVectorLength(Stamp stamp, int length) {
        GraalError.guarantee(length == 1, "LLVM does not support Graal SIMD vectors");
        return 1;
    }

    @Override
    public boolean shouldBroadcastVectorShiftCount(SimdStamp xStamp, IntegerStamp yStamp) {
        return false;
    }

    @Override
    public int getSupportedVectorMoveLength(Stamp stamp, int maxLength) {
        return 1;
    }

    @Override
    public int getSupportedVectorMaskedMoveLength(Stamp stamp, int maxLength) {
        return 1;
    }

    @Override
    public int getSupportedVectorArithmeticLength(Stamp stamp, int maxLength, ArithmeticOpTable.Op op) {
        return 1;
    }

    @Override
    public int getSupportedVectorShiftWithScalarCount(Stamp stamp, int maxLength, ArithmeticOpTable.Op op) {
        return 1;
    }

    @Override
    public boolean narrowedVectorInstructionAvailable(NarrowableArithmeticNode operation, IntegerStamp narrowedStamp) {
        return false;
    }

    @Override
    public boolean narrowedVectorShiftAvailable(ShiftNode<?> op, IntegerStamp narrowedStamp) {
        return false;
    }

    @Override
    public int getSupportedVectorConvertLength(Stamp result, Stamp input, int maxLength, IntegerConvertOp<?> op) {
        return 1;
    }

    @Override
    public int getSupportedVectorConvertLength(Stamp result, Stamp input, int maxLength, FloatConvert op) {
        return 1;
    }

    @Override
    public int getSupportedVectorConditionalLength(Stamp stamp, int maxLength) {
        return 1;
    }

    @Override
    public int getSupportedVectorLogicLength(LogicNode logicNode, int maxLength) {
        return 1;
    }

    @Override
    public int getSupportedVectorComparisonLength(Stamp stamp, CanonicalCondition condition, int maxLength) {
        return 1;
    }

    @Override
    public int getSupportedVectorGatherLength(Stamp elementStamp, Stamp offsetStamp, int maxLength) {
        return 1;
    }

    @Override
    public int getSupportedVectorPermuteLength(Stamp elementStamp, int maxLength) {
        return 1;
    }

    @Override
    public int getSupportedVectorBlendLength(Stamp elementStamp, int maxLength) {
        return 1;
    }

    @Override
    public int getSupportedVectorCompressExpandLength(Stamp elementStamp, int maxLength, CompressExpandOp op) {
        return 1;
    }

    @Override
    public int getObjectAlignment() {
        return objectAlignment;
    }

    @Override
    public boolean logicVectorsAreBitmasks() {
        return true;
    }
}
