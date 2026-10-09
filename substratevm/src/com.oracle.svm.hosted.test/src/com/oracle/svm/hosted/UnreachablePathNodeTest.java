/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.svm.core.graal.nodes.UnreachablePathNode;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class UnreachablePathNodeTest extends GraalCompilerTest {
    private static volatile int effect;

    @Override
    protected void registerInvocationPlugins(InvocationPlugins plugins) {
        super.registerInvocationPlugins(plugins);
        plugins.register(GraalDirectives.class, new InvocationPlugin("unreachable") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.add(new UnreachablePathNode());
                return true;
            }
        });
    }

    public static void straightLine() {
        effect = 1;
        GraalDirectives.unreachable();
    }

    public static int conditional(boolean reject) {
        effect = 1;
        if (reject) {
            effect = 2;
            GraalDirectives.unreachable();
        }
        return 42;
    }

    public static void merged(boolean branch) {
        if (branch) {
            effect = 1;
        } else {
            effect = 2;
        }
        GraalDirectives.unreachable();
    }

    public static void bothBranches(boolean branch) {
        if (branch) {
            effect = 1;
            GraalDirectives.unreachable();
        } else {
            effect = 2;
            GraalDirectives.unreachable();
        }
    }

    public static void afterLoop(int count) {
        for (int i = 0; i < count; i++) {
            effect = i;
        }
        GraalDirectives.unreachable();
    }

    public static void afterThrowingCall(Object value) {
        try {
            effect = throwingCall(value);
        } catch (RuntimeException ex) {
            effect = 2;
            return;
        }
        GraalDirectives.unreachable();
    }

    @BytecodeParserNeverInline(invokeWithException = true)
    public static int throwingCall(Object value) {
        return value.hashCode();
    }

    private StructuredGraph simplify(String name) {
        StructuredGraph graph = parseEager(name, AllowAssumptions.NO);
        CanonicalizerPhase.create().apply(graph, getProviders());
        Assert.assertTrue(graph.verify());
        return graph;
    }

    private void assertEntryTrap(String name) {
        StructuredGraph graph = simplify(name);
        Assert.assertTrue(graph.start().next() instanceof UnreachablePathNode);
        Assert.assertEquals(0, graph.getNodes().filter(StoreFieldNode.class).count());
        Assert.assertEquals(0, graph.getNodes().filter(ReturnNode.class).count());
        Assert.assertEquals(1, graph.getNodes().filter(UnreachablePathNode.class).count());
    }

    @Test
    public void testStraightLine() {
        assertEntryTrap("straightLine");
    }

    @Test
    public void testMerge() {
        assertEntryTrap("merged");
    }

    @Test
    public void testBothBranches() {
        assertEntryTrap("bothBranches");
    }

    @Test
    public void testConditional() {
        StructuredGraph graph = simplify("conditional");
        Assert.assertEquals(1, graph.getNodes().filter(IfNode.class).count());
        Assert.assertEquals(1, graph.getNodes().filter(StoreFieldNode.class).count());
        Assert.assertEquals(1, graph.getNodes().filter(ReturnNode.class).count());
    }

    @Test
    public void testSpecializedConditional() {
        StructuredGraph graph = parseEager("conditional", AllowAssumptions.NO);
        graph.getParameter(0).replaceAtUsages(ConstantNode.forBoolean(true, graph));
        CanonicalizerPhase.create().apply(graph, getProviders());
        Assert.assertTrue(graph.verify());
        Assert.assertTrue(graph.start().next() instanceof UnreachablePathNode);
        Assert.assertEquals(0, graph.getNodes().filter(StoreFieldNode.class).count());
    }

    @Test
    public void testLoopBoundary() {
        StructuredGraph graph = simplify("afterLoop");
        Assert.assertEquals(1, graph.getNodes().filter(LoopBeginNode.class).count());
        Assert.assertEquals(1, graph.getNodes().filter(StoreFieldNode.class).count());
    }

    @Test
    public void testExceptionBoundary() {
        StructuredGraph graph = simplify("afterThrowingCall");
        Assert.assertTrue(graph.getInvokes().iterator().hasNext());
        Assert.assertEquals(1, graph.getNodes().filter(ReturnNode.class).count());
        Assert.assertEquals(1, graph.getNodes().filter(UnreachablePathNode.class).count());
    }
}
