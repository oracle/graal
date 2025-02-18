/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugDumpScope;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.HighTierLoweringPhase;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

public class PushNodesThroughPiTest extends GraalCompilerTest {

    public static class A {

        public long x = 20;
    }

    public static class B extends A {

        public long y = 10;
    }

    public static class C extends B {

        public long z = 5;
    }

    public static long test1Snippet(A a) {
        C c = (C) a;
        long ret = c.x; // this can be pushed before the checkcast
        ret += c.y; // not allowed to push
        ret += c.z; // not allowed to push
        // the null-check should be canonicalized with the null-check of the checkcast
        ret += c != null ? 100 : 200;
        return ret;
    }

    @Ignore
    @Test
    public void test1() {
        final String snippet = "test1Snippet";
        DebugContext debug = getDebugContext();
        try (DebugContext.Scope _ = debug.scope("PushThroughPi", new DebugDumpScope(snippet))) {
            StructuredGraph graph = compileTestSnippet(snippet);
            for (ReadNode rn : graph.getNodes().filter(ReadNode.class)) {
                OffsetAddressNode address = (OffsetAddressNode) rn.getAddress();
                long disp = address.getOffset().asJavaConstant().asLong();

                ResolvedJavaType receiverType = StampTool.typeOrNull(address.getBase());
                ResolvedJavaField field = receiverType.findInstanceFieldWithOffset(disp, rn.getStackKind());

                assert field != null : "Node " + rn + " tries to access a field which doesn't exists for this type";
                if (field.getName().equals("x")) {
                    Assert.assertTrue(address.getBase() instanceof ParameterNode);
                } else {
                    Assert.assertTrue(address.getBase().toString(), address.getBase() instanceof PiNode);
                }
            }

            Assert.assertTrue(graph.getNodes().filter(IsNullNode.class).count() == 1);
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    private StructuredGraph compileTestSnippet(final String snippet) {
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.NO);
        CoreProviders context = getProviders();
        CanonicalizerPhase canonicalizer = this.createCanonicalizerPhase();
        new HighTierLoweringPhase(canonicalizer).apply(graph, context);
        canonicalizer.apply(graph, context);
        canonicalizer.apply(graph, context);

        return graph;
    }
}
