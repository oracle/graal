/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.loop.LoopEx;
import org.graalvm.compiler.nodes.loop.LoopsData;
import org.graalvm.compiler.options.OptionValues;
import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Test to verify that loop max trip count nodes reuse existing {@link PiNode}s if possible.
 */
public class CountedLoopMaxTripCountPiTest extends GraalCompilerTest {

    public static int guardLoopEntered(boolean condition, int guardedValue) {
        if (condition) {
            GraalDirectives.deoptimize();
        }
        return positivePi(guardedValue);
    }

    // intrinsified below
    public static int positivePi(int n) {
        return n;
    }

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        super.registerInvocationPlugins(invocationPlugins);
        Registration r = new Registration(invocationPlugins, CountedLoopMaxTripCountPiTest.class);

        r.register1("positivePi", int.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod method, Receiver receiver, ValueNode n) {
                BeginNode begin = b.add(new BeginNode());
                b.addPush(JavaKind.Int, PiNode.create(n, StampFactory.positiveInt().improveWith(n.stamp(NodeView.DEFAULT)), begin));
                return true;
            }
        });
    }

    int ascendingSnippet(int start, int limit) {
        int maxTripCount = guardLoopEntered(limit < start, limit - start);
        int sum = 0;
        for (int i = start; GraalDirectives.injectIterationCount(101, i < limit); i++) {
            GraalDirectives.sideEffect(i);
            sum += i;
        }
        return sum + maxTripCount;
    }

    int descendingSnippet(int start, int limit) {
        int maxTripCount = guardLoopEntered(start < limit, start - limit + 1);
        int sum = 0;
        for (int i = start; GraalDirectives.injectIterationCount(102, i >= limit); i--) {
            GraalDirectives.sideEffect(i);
            sum += i;
        }
        return sum + maxTripCount;
    }

    /**
     * In the high and mid tiers, make sure that all max trip count nodes are the unique pi node in
     * the graph (i.e., exactly the one proved by the guard). We don't check the low tier because pi
     * nodes are eliminated by FixReads.
     */
    void checkGraph(StructuredGraph graph, LoopsData loops) {
        loops.detectedCountedLoops();
        for (LoopEx loop : loops.loops()) {
            Assert.assertTrue("expect all loops to be counted", loop.isCounted());
            ValueNode maxTripCountNode = loop.counted().maxTripCountNode();
            Assert.assertTrue("expect a PiNode for the guarded maxTripCount, got: " + maxTripCountNode, maxTripCountNode instanceof PiNode);
        }
        PiNode[] pis = graph.getNodes().filter(PiNode.class).snapshot().toArray(new PiNode[0]);
        Assert.assertEquals("number of PiNodes in the graph", 1, pis.length);
    }

    @Override
    protected void checkHighTierGraph(StructuredGraph graph) {
        super.checkHighTierGraph(graph);
        checkGraph(graph, getDefaultHighTierContext().getLoopsDataProvider().getLoopsData(graph));
    }

    @Override
    protected void checkMidTierGraph(StructuredGraph graph) {
        super.checkMidTierGraph(graph);
        checkGraph(graph, getDefaultMidTierContext().getLoopsDataProvider().getLoopsData(graph));
    }

    protected void testLoop(String snippetName, Object... args) {
        OptionValues options = new OptionValues(getInitialOptions(), GraalOptions.PartialUnroll, false, GraalOptions.LoopPeeling, false);
        test(options, snippetName, args);
    }

    @Test
    public void testAscending() {
        testLoop("ascendingSnippet", 0, 100);
    }

    @Test
    public void testDescending() {
        testLoop("descendingSnippet", 100, 0);
    }
}
