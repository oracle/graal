/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test.processor.verify;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.test.ExpectError;

public class VerifyCompilationFinalProcessorTest {

    static class NonArrayWithNegativeDimension {
        @ExpectError("@CompilationFinal.dimensions cannot be negative.") @CompilerDirectives.CompilationFinal(dimensions = -2) private int opCode;
    }

    static class NonArrayWithPositiveDimension {
        @ExpectError("Positive @CompilationFinal.dimensions (1) not allowed for non array type.") @CompilerDirectives.CompilationFinal(dimensions = 1) private int opCode;
    }

    static class NonArrayWithZeroDimension {
        @CompilerDirectives.CompilationFinal(dimensions = 0) private int opCode;
    }

    static class NonArrayWithNoDimension {
        @CompilerDirectives.CompilationFinal private int opCode;
    }

    static class ArrayWithNegativeDimension {
        @ExpectError("@CompilationFinal.dimensions cannot be negative.") @CompilerDirectives.CompilationFinal(dimensions = -2) private int[] vector;
    }

    static class ArrayWithInvalidDimension {
        @ExpectError("@CompilationFinal.dimensions (4) cannot exceed the array's dimensions (3).") @CompilerDirectives.CompilationFinal(dimensions = 4) private int[][][] matrix;
    }

    static class ArrayWithValidDimension {
        @CompilerDirectives.CompilationFinal(dimensions = 0) private int[][][] matrix1;
        @CompilerDirectives.CompilationFinal(dimensions = 1) private int[][][] matrix2;
        @CompilerDirectives.CompilationFinal(dimensions = 2) private int[][][] matrix3;
        @CompilerDirectives.CompilationFinal(dimensions = 3) private int[][][] matrix4;
    }

    static class ArrayWithNoDimension {
        @ExpectError("@CompilationFinal.dimensions should be given for an array type.") @CompilerDirectives.CompilationFinal private int[] vector;
    }

    public enum ECJ451 {
        A,
        B;
        @CompilerDirectives.CompilationFinal(dimensions = 0) private static final ECJ451[] VALUES = ECJ451.values();
    }
}
