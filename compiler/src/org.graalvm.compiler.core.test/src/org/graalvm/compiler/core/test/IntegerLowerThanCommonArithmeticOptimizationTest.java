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
package org.graalvm.compiler.core.test;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.LeftShiftNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.calc.ZeroExtendNode;
import org.junit.Assert;
import org.junit.Test;

public class IntegerLowerThanCommonArithmeticOptimizationTest extends IntegerLowerThanCommonArithmeticTestBase {

    @Override
    protected void checkHighTierGraph(StructuredGraph graph) {
        if (getArgumentToBind() != null) {
            // we have restricted stamps on the parameter nodes
            assertNotPresent(graph, AddNode.class);
            assertNotPresent(graph, LeftShiftNode.class);
            assertNotPresent(graph, SignExtendNode.class);
            assertNotPresent(graph, ZeroExtendNode.class);
        }
        super.checkHighTierGraph(graph);
    }

    private static void assertNotPresent(StructuredGraph graph, Class<? extends Node> nodeClass) {
        if (graph.getNodes().filter(nodeClass).isNotEmpty()) {
            throw new GraalError("found %s: %s", nodeClass, graph.getNodes().filter(nodeClass));
        }
    }

    @Override
    protected Object[] getBindArgs(Object[] args) {
        IntegerStamp xStamp = IntegerStamp.create(32, 0, Integer.MAX_VALUE >> 4);
        IntegerStamp yStamp = IntegerStamp.create(32, 0, Integer.MAX_VALUE >> 4);
        Assert.assertEquals(4, args.length);
        Assert.assertTrue(xStamp.contains((Integer) args[0]));
        Assert.assertTrue(yStamp.contains((Integer) args[1]));
        return new Object[]{
                        xStamp,
                        yStamp,
                        args[2],
                        args[3],
        };
    }

    public static boolean testSnippet(int x, int y, int c, int d) {
        if ((x << c) + d < (y << c) + d) {
            GraalDirectives.controlFlowAnchor();
            return true;
        } else {
            return false;
        }
    }

    @Test
    public void runTest() {
        runTest("testSnippet", 2, 3, 4, 5);
    }

    public static boolean testSnippetSignExtend(int x, int y, int c, int d) {
        long xL = x;
        long yL = y;
        if ((xL << c) + d < (yL << c) + d) {
            GraalDirectives.controlFlowAnchor();
            return true;
        } else {
            return false;
        }
    }

    @Test
    public void runTestSignExtend() {
        runTest("testSnippetSignExtend", 2, 3, 4, 5);
    }

    public static boolean testSnippetZeroExtend(int x, int y, int c, int d) {
        long xL = Integer.toUnsignedLong(x);
        long yL = Integer.toUnsignedLong(y);
        if ((xL << c) + d < (yL << c) + d) {
            GraalDirectives.controlFlowAnchor();
            return true;
        } else {
            return false;
        }
    }

    @Test
    public void runTestZeroExtend() {
        runTest("testSnippetZeroExtend", 2, 3, 4, 5);
    }
}
