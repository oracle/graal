/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Arm Limited and affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import org.junit.Test;

import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.calc.NegateNode;
import jdk.graal.compiler.nodes.calc.RightShiftNode;
import jdk.graal.compiler.nodes.calc.UnsignedRightShiftNode;

public class NegateCanonicalizationTest extends GraalCompilerTest {

    public static int negateInt(int x) {
        return -(x >> 31);
    }

    public static long negateLong(long x) {
        return -(x >> 63);
    }

    public static int signExtractInt(int x) {
        return (x >> 31) >>> 31;
    }

    public static long signExtractLong(long x) {
        return (x >> 63) >>> 63;
    }

    public static int negateNegate(int x) {
        int var0 = -x;
        int var1 = -(0 ^ var0);
        return var1;
    }

    public static int negateNotDecrement(int x) {
        return -~(x - 1);
    }

    private void checkNodesOnlyUnsignedRightShift(String methodName) {
        StructuredGraph graph = parseForCompile(getResolvedJavaMethod(methodName));
        createCanonicalizerPhase().apply(graph, getProviders());
        assertTrue(graph.getNodes().filter(NegateNode.class).count() == 0);
        assertTrue(graph.getNodes().filter(RightShiftNode.class).count() == 0);
        assertTrue(graph.getNodes().filter(UnsignedRightShiftNode.class).count() == 1);
    }

    private void checkNodesNoNegate(String methodName) {
        StructuredGraph graph = parseForCompile(getResolvedJavaMethod(methodName));
        createCanonicalizerPhase().apply(graph, getProviders());
        assertTrue(graph.getNodes().filter(NegateNode.class).count() == 0);
    }

    @Test
    public void testNegate() {
        checkNodesOnlyUnsignedRightShift("negateInt");
        checkNodesOnlyUnsignedRightShift("negateLong");
        checkNodesOnlyUnsignedRightShift("signExtractInt");
        checkNodesOnlyUnsignedRightShift("signExtractLong");
        checkNodesNoNegate("negateNegate");
        checkNodesNoNegate("negateNotDecrement");
    }
}
