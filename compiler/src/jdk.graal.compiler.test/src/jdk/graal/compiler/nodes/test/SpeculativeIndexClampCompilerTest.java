/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.test;

import static jdk.graal.compiler.core.common.SpectrePHTMitigations.Options.SpectrePHTIndexMasking;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.AndNode;
import jdk.graal.compiler.nodes.calc.NotNode;
import jdk.graal.compiler.nodes.calc.RightShiftNode;
import jdk.graal.compiler.nodes.calc.SubNode;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.options.OptionValues;

/**
 * White-box compiler test for the speculative branchless index clamp. This test complements
 * {@link SpeculativeIndexClampTest}: that test validates the arithmetic behavior directly, while
 * this test validates the compiler lowering level by compiling Java array loads and stores with
 * {@link SpectrePHTIndexMasking} enabled. The IR matcher is intentionally tightly coupled to the
 * current compiler implementation so a change to the lowered clamp shape will probably require
 * a change in this test.
 */
public class SpeculativeIndexClampCompilerTest extends GraalCompilerTest {

    public static int maskedLoadSnippet(int[] array, int index) {
        return array[index];
    }

    public static void maskedStoreSnippet(int[] array, int index, int value) {
        array[index] = value;
    }

    @Test
    public void testMaskedLoadContainsBranchlessClamp() {
        assertCompiledArrayAccessContainsBranchlessClamp("maskedLoadSnippet", ReadNode.class);
    }

    @Test
    public void testMaskedStoreContainsBranchlessClamp() {
        assertCompiledArrayAccessContainsBranchlessClamp("maskedStoreSnippet", WriteNode.class);
    }

    private void assertCompiledArrayAccessContainsBranchlessClamp(String methodName, Class<? extends Node> loweredAccessType) {
        OptionValues options = new OptionValues(getInitialOptions(), SpectrePHTIndexMasking, true);
        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod(methodName), options);

        Assert.assertTrue(methodName + " should lower an array access", graph.getNodes().filter(loweredAccessType).isNotEmpty());
        Assert.assertTrue(methodName + " should contain the speculative branchless index clamp: " + describeAddNodes(graph), graphContainsBranchlessClamp(graph));
    }

    private static boolean graphContainsBranchlessClamp(StructuredGraph graph) {
        for (AddNode add : graph.getNodes().filter(AddNode.class)) {
            if (isBranchlessMinNonNegative(add)) {
                return true;
            }
        }
        return false;
    }

    private static String describeAddNodes(StructuredGraph graph) {
        StringBuilder sb = new StringBuilder();
        for (AddNode add : graph.getNodes().filter(AddNode.class)) {
            sb.append(add).append(" = ").append(describe(add, 3)).append("; ");
        }
        return sb.toString();
    }

    private static String describe(ValueNode value, int depth) {
        if (value == null) {
            return "null";
        }
        if (depth == 0) {
            return value.getClass().getSimpleName();
        }
        if (value instanceof AddNode add) {
            return "Add(" + describe(add.getX(), depth - 1) + ", " + describe(add.getY(), depth - 1) + ")";
        } else if (value instanceof SubNode sub) {
            return "Sub(" + describe(sub.getX(), depth - 1) + ", " + describe(sub.getY(), depth - 1) + ")";
        } else if (value instanceof AndNode and) {
            return "And(" + describe(and.getX(), depth - 1) + ", " + describe(and.getY(), depth - 1) + ")";
        } else if (value instanceof RightShiftNode shift) {
            return "Shr(" + describe(shift.getX(), depth - 1) + ", " + describe(shift.getY(), depth - 1) + ")";
        } else if (value instanceof NotNode not) {
            return "Not(" + describe(not.getValue(), depth - 1) + ")";
        } else if (value instanceof ConstantNode constant) {
            return "Const(" + constant.asJavaConstant() + ")";
        }
        return value.getClass().getSimpleName();
    }

    /**
     * Matches {@code y + ((x - y) & ((x - y) >> 31))}, where {@code x} is the
     * checked index and {@code y} is {@code length - 1}.
     */
    private static boolean isBranchlessMinNonNegative(AddNode add) {
        return isBranchlessMinNonNegative(add.getX(), add.getY()) || isBranchlessMinNonNegative(add.getY(), add.getX());
    }

    private static boolean isBranchlessMinNonNegative(ValueNode lengthMinusOne, ValueNode maskedDelta) {
        if (!isLengthMinusOne(lengthMinusOne) || !(maskedDelta instanceof AndNode and)) {
            return false;
        }
        return isMaskedDelta(and.getX(), and.getY(), lengthMinusOne) || isMaskedDelta(and.getY(), and.getX(), lengthMinusOne);
    }

    private static boolean isMaskedDelta(ValueNode delta, ValueNode mask, ValueNode lengthMinusOne) {
        if (!(delta instanceof SubNode sub)) {
            return false;
        }
        return isIndexValue(sub.getX()) && sub.getY() == lengthMinusOne && isRightShiftBy31(mask, delta);
    }

    private static boolean isLengthMinusOne(ValueNode value) {
        if (value instanceof AddNode add) {
            return isConstant(add.getX(), -1) || isConstant(add.getY(), -1);
        } else if (value instanceof SubNode sub) {
            return isConstant(sub.getY(), 1);
        }
        ValueNode input = branchlessMaxZeroInput(value);
        return input != null && isLengthMinusOne(input);
    }

    private static boolean isIndexValue(ValueNode value) {
        if (value instanceof ParameterNode parameter) {
            return parameter.index() == 1;
        } else if (value instanceof PiNode pi) {
            return isIndexValue(pi.object());
        }
        ValueNode input = branchlessMaxZeroInput(value);
        return input != null && isIndexValue(input);
    }

    /**
     * Matches {@code value & ~(value >> 31)} and returns the protected value.
     */
    private static ValueNode branchlessMaxZeroInput(ValueNode value) {
        if (!(value instanceof AndNode and)) {
            return null;
        }
        ValueNode input = branchlessMaxZeroInput(and.getX(), and.getY());
        return input != null ? input : branchlessMaxZeroInput(and.getY(), and.getX());
    }

    private static ValueNode branchlessMaxZeroInput(ValueNode input, ValueNode mask) {
        if (mask instanceof NotNode not && isRightShiftBy31(not.getValue(), input)) {
            return input;
        }
        return null;
    }

    private static boolean isRightShiftBy31(ValueNode value, ValueNode shiftedValue) {
        return value instanceof RightShiftNode shift && shift.getX() == shiftedValue && isConstant(shift.getY(), Integer.SIZE - 1);
    }

    private static boolean isConstant(ValueNode value, int expected) {
        return value instanceof ConstantNode constant && constant.asJavaConstant().asInt() == expected;
    }
}
