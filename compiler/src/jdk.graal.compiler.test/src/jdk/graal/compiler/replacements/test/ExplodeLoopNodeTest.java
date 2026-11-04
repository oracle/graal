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
package jdk.graal.compiler.replacements.test;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.extended.ValueAnchorNode;
import jdk.graal.compiler.replacements.nodes.ExplodeLoopNode;

public class ExplodeLoopNodeTest extends GraalCompilerTest {

    public static int loop(int count) {
        int sum = 0;
        for (int i = 0; i < count; i++) {
            sum += i;
        }
        return sum;
    }

    private void findFollowingLoop(int anchors) {
        StructuredGraph graph = parseEager("loop", AllowAssumptions.YES);
        Assert.assertEquals(1, graph.getNodes(LoopBeginNode.TYPE).count());
        LoopBeginNode loop = graph.getNodes(LoopBeginNode.TYPE).first();
        ExplodeLoopNode marker = graph.add(new ExplodeLoopNode());
        graph.addBeforeFixed(loop.forwardEnd(), marker);
        for (int i = 0; i < anchors; i++) {
            graph.addAfterFixed(marker, graph.add(new ValueAnchorNode()));
        }
        Assert.assertTrue(graph.verify());
        Assert.assertSame(loop, marker.findLoopBegin());
    }

    @Test
    public void directPreheader() {
        findFollowingLoop(0);
    }

    @Test
    public void linearPreheader() {
        findFollowingLoop(1);
    }

    @Test
    public void multipleAnchors() {
        findFollowingLoop(3);
    }

    public static int branch(int count) {
        if (count > 0) {
            return loop(count);
        }
        return 0;
    }

    @Test
    public void stopAtBranch() {
        StructuredGraph graph = parseEager("branch", AllowAssumptions.YES);
        ExplodeLoopNode marker = graph.add(new ExplodeLoopNode());
        graph.addAfterFixed(graph.start(), marker);
        graph.addAfterFixed(marker, graph.add(new ValueAnchorNode()));
        Assert.assertTrue(graph.verify());
        Assert.assertNull(marker.findLoopBegin());
    }
}
