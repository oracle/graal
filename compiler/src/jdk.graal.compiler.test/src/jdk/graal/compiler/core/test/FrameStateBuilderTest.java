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
package jdk.graal.compiler.core.test;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.java.FrameStateBuilder;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.MonitorIdNode;

public class FrameStateBuilderTest extends GraalCompilerTest {

    public static void snippet() {
    }

    private FrameStateBuilder newFrame(StructuredGraph graph) {
        return new FrameStateBuilder(null, getResolvedJavaMethod("snippet"), graph);
    }

    private void pushLock(FrameStateBuilder frame, StructuredGraph graph, int depth) {
        ValueNode object = ConstantNode.forConstant(getSnippetReflection().forObject(new Object()), getMetaAccess(), graph);
        frame.pushLock(object, graph.add(new MonitorIdNode(depth)));
    }

    @Test
    public void shallowerFrameDoesNotEqualDeeperFrame() {
        StructuredGraph graph = new StructuredGraph.Builder(getInitialOptions(), getDebugContext()).build();
        FrameStateBuilder shallower = newFrame(graph);
        FrameStateBuilder deeper = shallower.copy();
        pushLock(deeper, graph, 0);
        assertFalse(shallower.equals(deeper));
    }

    @Test
    public void deeperFrameDoesNotEqualShallowerFrame() {
        StructuredGraph graph = new StructuredGraph.Builder(getInitialOptions(), getDebugContext()).build();
        FrameStateBuilder shallower = newFrame(graph);
        FrameStateBuilder deeper = shallower.copy();
        pushLock(deeper, graph, 0);
        assertFalse(deeper.equals(shallower));
    }

    @Test
    public void commonLockPrefixDoesNotMakeFramesEqual() {
        StructuredGraph graph = new StructuredGraph.Builder(getInitialOptions(), getDebugContext()).build();
        FrameStateBuilder shallower = newFrame(graph);
        pushLock(shallower, graph, 0);
        FrameStateBuilder deeper = shallower.copy();
        pushLock(deeper, graph, 1);
        assertFalse(shallower.equals(deeper));
        assertFalse(deeper.equals(shallower));
    }

    @Test
    public void equalFramesHaveEqualHashes() {
        StructuredGraph graph = new StructuredGraph.Builder(getInitialOptions(), getDebugContext()).build();
        FrameStateBuilder frame = newFrame(graph);
        Assert.assertEquals(frame, frame.copy());
        pushLock(frame, graph, 0);
        FrameStateBuilder copy = frame.copy();
        Assert.assertEquals(frame, copy);
        Assert.assertEquals(copy, frame);
        Assert.assertEquals(frame.hashCode(), copy.hashCode());
    }

    @Test
    public void sameDepthDifferentMonitorsAreNotEqual() {
        StructuredGraph graph = new StructuredGraph.Builder(getInitialOptions(), getDebugContext()).build();
        FrameStateBuilder first = newFrame(graph);
        FrameStateBuilder second = first.copy();
        ValueNode object = ConstantNode.forConstant(getSnippetReflection().forObject(new Object()), getMetaAccess(), graph);
        first.pushLock(object, graph.add(new MonitorIdNode(0)));
        second.pushLock(object, graph.add(new MonitorIdNode(0)));
        assertFalse(first.equals(second));
        assertFalse(second.equals(first));
    }
}
