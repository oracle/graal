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

import org.junit.Test;

import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.replacements.nodes.CalcStringAttributesNode;

public class TStringOpsCalcStringAttributesMixedConstantTest extends TStringOpsConstantTest<CalcStringAttributesNode> {

    private static final MethodHandle calcLatin1;
    private static final MethodHandle calcUTF16;

    static {
        try {
            Method methodLatin1 = T_STRING_OPS_CLASS.getDeclaredMethod("calcStringAttributesLatin1", com.oracle.truffle.api.nodes.Node.class, byte[].class, long.class, int.class);
            methodLatin1.setAccessible(true);
            calcLatin1 = MethodHandles.lookup().unreflect(methodLatin1);
            Method methodUTF16 = T_STRING_OPS_CLASS.getDeclaredMethod("calcStringAttributesUTF16", com.oracle.truffle.api.nodes.Node.class, byte[].class, long.class, int.class, boolean.class);
            methodUTF16.setAccessible(true);
            calcUTF16 = MethodHandles.lookup().unreflect(methodUTF16);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public TStringOpsCalcStringAttributesMixedConstantTest() {
        super(CalcStringAttributesNode.class, new byte[]{'a', 'b', 'c'}, 0, 3);
    }

    @Test
    public void testMixed() throws Throwable {
        setConstantArgs(arrayA, offsetA, lengthA);
        runTestMethod(arrayA, offsetA, lengthA, false);
        runTestMethod(arrayA, offsetA, 1, true);
        test("runTestMethod", arrayA, offsetA, lengthA, false);
    }

    public static long runTestMethod(byte[] array, long offset, int length, boolean condition) throws Throwable {
        if (condition) {
            return (long) calcUTF16.invokeExact(DUMMY_LOCATION, array, offset, length, false);
        } else {
            return (int) calcLatin1.invokeExact(DUMMY_LOCATION, array, offset, length);
        }
    }

    @Override
    protected void checkLowTierGraph(StructuredGraph graph) {
    }
}
