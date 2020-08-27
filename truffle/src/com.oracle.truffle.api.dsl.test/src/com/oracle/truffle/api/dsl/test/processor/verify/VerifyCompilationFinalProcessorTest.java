/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
