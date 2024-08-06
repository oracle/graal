/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.InstanceOfNode;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaType;

public class SingleImplementorInterfaceTest extends GraalCompilerTest {

    public interface Interface0 {
        void interfaceMethod();
    }

    public interface Interface1 extends Interface0 {

    }

    public interface Interface2 extends Interface1 {
    }

    @SuppressWarnings("all")
    public static class SingleImplementor1 implements Interface1 {
        public void interfaceMethod() {
        }
    }

    // Requires that the CHA analysis starts from the referenced type. Since {@code
    // SingleImplementor1}
    // is not a single implementor of {@code Interface2} devirtualization shouldn't happen.
    @SuppressWarnings("all")
    private static void singleImplementorInterfaceSnippet1(Interface2 i) {
        i.interfaceMethod();
    }

    // Devirtualization should happen in this case.
    @SuppressWarnings("all")
    private static void singleImplementorInterfaceSnippet2(Interface1 i) {
        i.interfaceMethod();
    }

    @Test
    public void testSingleImplementorInterfaceDevirtualization1() {
        ResolvedJavaType singleImplementorType = getMetaAccess().lookupJavaType(SingleImplementor1.class);
        ResolvedJavaType expectedReferencedType = getMetaAccess().lookupJavaType(Interface2.class);
        singleImplementorType.initialize();
        StructuredGraph graph = parseEager("singleImplementorInterfaceSnippet1", StructuredGraph.AllowAssumptions.YES);
        createCanonicalizerPhase().apply(graph, getProviders());
        // Devirtualization shouldn't work in this case. The invoke should remain intact.
        InvokeNode invoke = graph.getNodes().filter(InvokeNode.class).first();
        assertTrue(invoke != null, "Should have an invoke");
        assertTrue(invoke.callTarget().invokeKind() == CallTargetNode.InvokeKind.Interface, "Should still be an interface call");
        assertTrue(invoke.callTarget().referencedType() != null, "Invoke should have a reference class set");
        assertTrue(invoke.callTarget().referencedType().equals(expectedReferencedType));
    }

    @Test
    public void testSingleImplementorInterfaceDevirtualization2() {
        ResolvedJavaType singleImplementorType = getMetaAccess().lookupJavaType(SingleImplementor1.class);
        singleImplementorType.initialize();
        StructuredGraph graph = parseEager("singleImplementorInterfaceSnippet2", StructuredGraph.AllowAssumptions.YES);
        createCanonicalizerPhase().apply(graph, getProviders());
        InvokeNode invoke = graph.getNodes().filter(InvokeNode.class).first();
        assertTrue(invoke != null, "Should have an invoke");
        assertTrue(invoke.callTarget().invokeKind() == CallTargetNode.InvokeKind.Special, "Should be devirtualized");
        InstanceOfNode instanceOfNode = graph.getNodes().filter(InstanceOfNode.class).first();
        assertTrue(instanceOfNode != null, "Missing the subtype check");
        assertTrue(instanceOfNode.getCheckedStamp().type().equals(singleImplementorType), "Checking against a wrong type");
    }

    @Test
    public void testSingleImplementorInterfaceInlining1() {
        ResolvedJavaType singleImplementorType = getMetaAccess().lookupJavaType(SingleImplementor1.class);
        ResolvedJavaType expectedReferencedType = getMetaAccess().lookupJavaType(Interface2.class);
        singleImplementorType.initialize();
        StructuredGraph graph = parseEager("singleImplementorInterfaceSnippet1", StructuredGraph.AllowAssumptions.YES);
        HighTierContext context = new HighTierContext(getProviders(), getDefaultGraphBuilderSuite(), OptimisticOptimizations.ALL);
        createInliningPhase().apply(graph, context);
        // Inlining shouldn't do anything
        InvokeNode invoke = graph.getNodes().filter(InvokeNode.class).first();
        assertTrue(invoke != null, "Should have an invoke");
        assertTrue(invoke.callTarget().referencedType() != null, "Invoke should have a reference class set");
        assertTrue(invoke.callTarget().invokeKind() == CallTargetNode.InvokeKind.Interface, "Should still be an interface call");
        assertTrue(invoke.callTarget().referencedType().equals(expectedReferencedType));
    }

    @Test
    public void testSingleImplementorInterfaceInlining2() {
        ResolvedJavaType singleImplementorType = getMetaAccess().lookupJavaType(SingleImplementor1.class);
        ResolvedJavaType expectedReferencedType = getMetaAccess().lookupJavaType(Interface1.class);
        singleImplementorType.initialize();
        StructuredGraph graph = parseEager("singleImplementorInterfaceSnippet2", StructuredGraph.AllowAssumptions.YES);
        HighTierContext context = new HighTierContext(getProviders(), getDefaultGraphBuilderSuite(), OptimisticOptimizations.ALL);
        createInliningPhase().apply(graph, context);

        // Right now inlining will not do anything, but if it starts doing devirtualization of
        // interface calls
        // in the future there should be a subtype check.
        InvokeNode invoke = graph.getNodes().filter(InvokeNode.class).first();
        if (invoke != null) {
            assertTrue(invoke.callTarget().invokeKind() == CallTargetNode.InvokeKind.Interface, "Should still be an interface call");
            assertTrue(invoke.callTarget().referencedType() != null, "Invoke should have a reference class set");
            assertTrue(invoke.callTarget().referencedType().equals(expectedReferencedType));
        } else {
            InstanceOfNode instanceOfNode = graph.getNodes().filter(InstanceOfNode.class).first();
            assertTrue(instanceOfNode != null, "Missing the subtype check");
            assertTrue(instanceOfNode.getCheckedStamp().type().equals(singleImplementorType), "Checking against a wrong type");
        }
    }
}
