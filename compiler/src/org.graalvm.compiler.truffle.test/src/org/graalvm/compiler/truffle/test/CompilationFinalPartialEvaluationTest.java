/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import static com.oracle.truffle.api.CompilerAsserts.partialEvaluationConstant;

import org.graalvm.compiler.truffle.test.nodes.AbstractTestNode;
import org.graalvm.compiler.truffle.test.nodes.RootTestNode;
import org.junit.Test;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.RootNode;

public class CompilationFinalPartialEvaluationTest extends PartialEvaluationTest {
    static final int[] INT_ARRAY = {3, 1, 4, 1, 5, 9, 0};
    static final Object[] OBJ_ARRAY = {"a", "b", "c", "d", "e", "f", null};

    public static Object constant42() {
        return 42;
    }

    @NodeInfo
    static class CompilationFinalTestNode extends AbstractTestNode {
        @CompilationFinal private int intZero = 0;
        @CompilationFinal private Object objNull = null;

        @CompilationFinal(dimensions = 0) private int[] intArray0 = INT_ARRAY;
        @CompilationFinal(dimensions = 0) private Object[] objArray0 = OBJ_ARRAY;
        @CompilationFinal(dimensions = 1) private int[][] intArray1 = {INT_ARRAY, INT_ARRAY, INT_ARRAY, null};
        @CompilationFinal(dimensions = 1) private Object[][] objArray1 = {OBJ_ARRAY, OBJ_ARRAY, OBJ_ARRAY, null};
        @CompilationFinal(dimensions = 2) private int[][] intArray2 = {INT_ARRAY, INT_ARRAY, INT_ARRAY, null};
        @CompilationFinal(dimensions = 2) private Object[][] objArray2 = {OBJ_ARRAY, OBJ_ARRAY, OBJ_ARRAY, null};

        @CompilationFinal(dimensions = 0) private final int[] intArrayFinal0 = INT_ARRAY;
        @CompilationFinal(dimensions = 0) private final Object[] objArrayFinal0 = OBJ_ARRAY;
        @CompilationFinal(dimensions = 1) private final int[][] intArrayFinal1 = {INT_ARRAY, INT_ARRAY, INT_ARRAY, null};
        @CompilationFinal(dimensions = 1) private final Object[][] objArrayFinal1 = {OBJ_ARRAY, OBJ_ARRAY, OBJ_ARRAY, null};
        @CompilationFinal(dimensions = 2) private final int[][] intArrayFinal2 = {INT_ARRAY, INT_ARRAY, INT_ARRAY, null};
        @CompilationFinal(dimensions = 2) private final Object[][] objArrayFinal2 = {OBJ_ARRAY, OBJ_ARRAY, OBJ_ARRAY, null};

        @CompilationFinal(dimensions = 0) private static int[] intArrayStatic0 = INT_ARRAY;
        @CompilationFinal(dimensions = 0) private static Object[] objArrayStatic0 = OBJ_ARRAY;
        @CompilationFinal(dimensions = 1) private static int[][] intArrayStatic1 = {INT_ARRAY, INT_ARRAY, INT_ARRAY, null};
        @CompilationFinal(dimensions = 1) private static Object[][] objArrayStatic1 = {OBJ_ARRAY, OBJ_ARRAY, OBJ_ARRAY, null};
        @CompilationFinal(dimensions = 2) private static int[][] intArrayStatic2 = {INT_ARRAY, INT_ARRAY, INT_ARRAY, null};
        @CompilationFinal(dimensions = 2) private static Object[][] objArrayStatic2 = {OBJ_ARRAY, OBJ_ARRAY, OBJ_ARRAY, null};

        @CompilationFinal(dimensions = 0) private static final int[] intArrayStaticFinal0 = INT_ARRAY;
        @CompilationFinal(dimensions = 0) private static final Object[] objArrayStaticFinal0 = OBJ_ARRAY;
        @CompilationFinal(dimensions = 1) private static final int[][] intArrayStaticFinal1 = {INT_ARRAY, INT_ARRAY, INT_ARRAY, null};
        @CompilationFinal(dimensions = 1) private static final Object[][] objArrayStaticFinal1 = {OBJ_ARRAY, OBJ_ARRAY, OBJ_ARRAY, null};
        @CompilationFinal(dimensions = 2) private static final int[][] intArrayStaticFinal2 = {INT_ARRAY, INT_ARRAY, INT_ARRAY, null};
        @CompilationFinal(dimensions = 2) private static final Object[][] objArrayStaticFinal2 = {OBJ_ARRAY, OBJ_ARRAY, OBJ_ARRAY, null};

        @SuppressWarnings("VerifyCompilationFinal") @CompilationFinal private Object intArrayDefault0 = INT_ARRAY;
        @SuppressWarnings("VerifyCompilationFinal") @CompilationFinal private Object objArrayDefault0 = OBJ_ARRAY;
        @SuppressWarnings("VerifyCompilationFinal") @CompilationFinal private Object[] intArrayDefault1 = new int[][]{INT_ARRAY, INT_ARRAY, INT_ARRAY, null};
        @SuppressWarnings("VerifyCompilationFinal") @CompilationFinal private Object[] objArrayDefault1 = new Object[][]{OBJ_ARRAY, OBJ_ARRAY, OBJ_ARRAY, null};
        @SuppressWarnings("VerifyCompilationFinal") @CompilationFinal private int[][] intArrayDefault2 = {INT_ARRAY, INT_ARRAY, INT_ARRAY, null};
        @SuppressWarnings("VerifyCompilationFinal") @CompilationFinal private Object[][] objArrayDefault2 = {OBJ_ARRAY, OBJ_ARRAY, OBJ_ARRAY, null};

        CompilationFinalTestNode() {
        }

        @Override
        public int execute(VirtualFrame frame) {
            partialEvaluationConstantAndEquals(intZero, 0);
            partialEvaluationConstantAndEquals(objNull, null);

            checkArray(intArray0, objArray0, intArray1, objArray1, intArray2, objArray2);
            checkArray(intArrayFinal0, objArrayFinal0, intArrayFinal1, objArrayFinal1, intArrayFinal2, objArrayFinal2);
            checkArray(intArrayStatic0, objArrayStatic0, intArrayStatic1, objArrayStatic1, intArrayStatic2, objArrayStatic2);
            checkArray(intArrayStaticFinal0, objArrayStaticFinal0, intArrayStaticFinal1, objArrayStaticFinal1, intArrayStaticFinal2, objArrayStaticFinal2);
            checkArray((int[]) intArrayDefault0, (Object[]) objArrayDefault0, (int[][]) intArrayDefault1, (Object[][]) objArrayDefault1, intArrayDefault2, objArrayDefault2);

            return 42;
        }

        private static void checkArray(int[] intArray0, Object[] objArray0, int[][] intArray1, Object[][] objArray1, int[][] intArray2, Object[][] objArray2) {
            partialEvaluationConstantAndEquals(intArray0, INT_ARRAY);
            partialEvaluationConstantAndEquals(objArray0, OBJ_ARRAY);
            notPartialEvaluationConstant(intArray0[0]);
            notPartialEvaluationConstant(objArray0[0]);

            partialEvaluationConstant(intArray1);
            partialEvaluationConstant(objArray1);
            partialEvaluationConstantAndEquals(intArray1[0], INT_ARRAY);
            partialEvaluationConstantAndEquals(objArray1[0], OBJ_ARRAY);
            partialEvaluationConstantAndEquals(intArray1[intArray1.length - 1], null);
            partialEvaluationConstantAndEquals(objArray1[objArray1.length - 1], null);
            notPartialEvaluationConstant(intArray1[0][0]);
            notPartialEvaluationConstant(objArray1[0][0]);

            partialEvaluationConstant(intArray2);
            partialEvaluationConstant(objArray2);
            partialEvaluationConstantAndEquals(intArray2[0], INT_ARRAY);
            partialEvaluationConstantAndEquals(objArray2[0], OBJ_ARRAY);
            partialEvaluationConstantAndEquals(intArray2[intArray2.length - 1], null);
            partialEvaluationConstantAndEquals(objArray2[objArray2.length - 1], null);
            partialEvaluationConstantAndEquals(intArray2[0][0], 3);
            partialEvaluationConstantAndEquals(objArray2[0][0], "a");
            partialEvaluationConstantAndEquals(intArray2[0][INT_ARRAY.length - 1], 0);
            partialEvaluationConstantAndEquals(objArray2[0][OBJ_ARRAY.length - 1], null);
        }

    }

    static void notPartialEvaluationConstant(int a) {
        if (CompilerDirectives.inCompiledCode() && CompilerDirectives.isPartialEvaluationConstant(a)) {
            throw new AssertionError();
        }
    }

    static void notPartialEvaluationConstant(Object a) {
        if (CompilerDirectives.inCompiledCode() && CompilerDirectives.isPartialEvaluationConstant(a)) {
            throw new AssertionError();
        }
    }

    static void partialEvaluationConstantAndEquals(int a, int b) {
        CompilerAsserts.partialEvaluationConstant(a);
        CompilerAsserts.partialEvaluationConstant(b);
        if (a != b) {
            throw new AssertionError();
        }
    }

    static void partialEvaluationConstantAndEquals(Object a, Object b) {
        CompilerAsserts.partialEvaluationConstant(a);
        CompilerAsserts.partialEvaluationConstant(b);
        if (a != b) {
            throw new AssertionError();
        }
    }

    @Test
    public void compilationFinalTest1() {
        FrameDescriptor fd = new FrameDescriptor();
        AbstractTestNode result = new CompilationFinalTestNode();
        assertPartialEvalEquals("constant42", new RootTestNode(fd, "compilationFinalTest", result));
    }

    @CompilationFinal(dimensions = 1) private static final long[] POWERS_OF_100 = {1L, 100L, 10000L, 21, 100000000L, 10000000000L, 1000000000000L, 100000000000000L, 10000000000000000L,
                    1000000000000000000L};

    @Test
    public void compilationFinalTest2() {
        assertPartialEvalEquals("constant42", new RootNode(null) {

            @CompilationFinal private int i = 3;

            @Override
            public Object execute(VirtualFrame frame) {
                // should fold to 42
                return 2 * POWERS_OF_100[i];
            }
        });
    }

}
