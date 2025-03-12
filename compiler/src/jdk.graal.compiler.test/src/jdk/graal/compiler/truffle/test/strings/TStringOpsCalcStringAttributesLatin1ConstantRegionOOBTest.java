/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test.strings;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.replacements.nodes.CalcStringAttributesNode;

@RunWith(Parameterized.class)
public class TStringOpsCalcStringAttributesLatin1ConstantRegionOOBTest extends TStringOpsConstantTest<CalcStringAttributesNode> {

    private static final MethodHandle calcLatin1;

    static {
        try {
            Method methodLatin1 = T_STRING_OPS_CLASS.getDeclaredMethod("calcStringAttributesLatin1", com.oracle.truffle.api.nodes.Node.class, byte[].class, long.class, int.class);
            methodLatin1.setAccessible(true);
            calcLatin1 = MethodHandles.lookup().unreflect(methodLatin1);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Parameters(name = "{index}: offset={0}, length={1}")
    public static List<Object[]> data() {
        return List.of(
                        // (offset + length) >= array.length
                        new Object[]{1, 3},
                        // (offset + length) integer overflow
                        new Object[]{Integer.MAX_VALUE, 3},
                        new Object[]{-1, 2},
                        new Object[]{2, -1});
    }

    public TStringOpsCalcStringAttributesLatin1ConstantRegionOOBTest(int offset, int length) {
        super(CalcStringAttributesNode.class, new byte[]{'a', 'b', 'c', -1}, offset, length);
    }

    @Test
    public void testConstantRegionOutOfBounds() throws Throwable {
        setConstantArgs(arrayA, offsetA, lengthA);
        // Make calcStringAttributesLatin1 branch reachable (must be in bounds)
        runTestMethod(arrayA, 2 + byteArrayBaseOffset(), 2, true);
        // Run and compile with constant offset + length out of bounds in the not taken branch.
        runTestMethod(arrayA, offsetA, lengthA, false);
        test("runTestMethod", arrayA, offsetA, lengthA, false);
    }

    public static long runTestMethod(byte[] array, long offset, int length, boolean condition) throws Throwable {
        if (condition) {
            return (int) calcLatin1.invokeExact(DUMMY_LOCATION, array, offset, length);
        } else {
            return -1;
        }
    }

    @Override
    protected void checkLowTierGraph(StructuredGraph graph) {
    }
}
