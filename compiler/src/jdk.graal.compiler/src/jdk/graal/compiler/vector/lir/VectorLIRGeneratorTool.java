/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.lir;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

public interface VectorLIRGeneratorTool extends ArithmeticLIRGeneratorTool {

    /**
     * Array of kinds suitable as SIMD bitmask elements. Must be sorted for easy indexing based on
     * the log of the size in bytes.
     */
    JavaKind[] MASK_JAVA_KINDS = new JavaKind[]{JavaKind.Byte, JavaKind.Short, JavaKind.Int, JavaKind.Long};

    /**
     * Fills a vector with duplicates of the provided scalar value.
     */
    Value emitVectorFill(LIRKind kind, Value value);

    /**
     * Converts a scalar value into a SIMD with the scalar value at index 0, and the rest of the
     * register zeroed out. See SimdFromScalarNode for more details.
     */
    Value emitSimdFromScalar(LIRKind kind, Value value);

    Value emitVectorCut(int startIdx, int length, Value vector);

    Value emitVectorInsert(int offset, Value vector, Value val);

    /**
     * Checks whether two vectors are equal. Note that this method currently requires both inputs to
     * be numeric integers.
     */
    Value emitVectorPackedEquals(Value vectorA, Value vectorB);

    Value emitVectorPackedComparison(CanonicalCondition condition, Value vectorA, Value vectorB, boolean unorderedIsTrue);

    Value emitVectorToBitMask(LIRKind resultKind, Value vector);

    Value emitVectorGather(LIRKind resultKind, Value base, Value offsets);

    Value emitVectorSimpleConcat(LIRKind resultKind, Value low, Value high);

    Value emitVectorBlend(Value zeroValue, Value oneValue, Value mask);

    Value emitVectorBlend(Value zeroValue, Value oneValue, boolean[] selector);

    Value emitVectorPermute(LIRKind resultKind, Value source, Value indices);

    Value emitMoveOpMaskToInteger(LIRKind resultKind, Value mask, int maskLen);

    Value emitMoveIntegerToOpMask(LIRKind resultKind, Value mask);

    Value emitVectorCompress(LIRKind resultKind, Value source, Value mask);

    Value emitVectorExpand(LIRKind resultKind, Value source, Value mask);
}
