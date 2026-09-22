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
package jdk.graal.compiler.api.directives.test;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.calc.AssumeIntNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;

public class AssumeIntDirectiveTest extends GraalCompilerTest {

    public static long snippet(long value) {
        long assumedInt = GraalDirectives.assumeInt(value);
        return (int) assumedInt;
    }

    @Test
    public void testAssumeInt() {
        test("snippet", (long) Integer.MIN_VALUE);
        test("snippet", -1L);
        test("snippet", 0L);
        test("snippet", (long) Integer.MAX_VALUE);
    }

    @Override
    protected void checkHighTierGraph(StructuredGraph graph) {
        Assert.assertEquals(1, graph.getNodes().filter(AssumeIntNode.class).count());
        Assert.assertTrue(graph.getNodes().filter(NarrowNode.class).isEmpty());

        ReturnNode returnNode = graph.getNodes(ReturnNode.TYPE).first();
        IntegerStamp stamp = (IntegerStamp) returnNode.result().stamp(NodeView.DEFAULT);
        Assert.assertEquals(64, stamp.getBits());
        Assert.assertEquals(Integer.MIN_VALUE, stamp.lowerBound());
        Assert.assertEquals(Integer.MAX_VALUE, stamp.upperBound());
    }
}
