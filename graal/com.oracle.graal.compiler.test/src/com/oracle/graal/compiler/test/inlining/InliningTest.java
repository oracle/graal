/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.compiler.test.inlining;

import static org.junit.Assert.*;

import java.util.concurrent.*;

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;

// TODO (chaeubl): add more test cases
@SuppressWarnings("unused")
public class InliningTest extends GraalCompilerTest {
    @Test
    public void testInvokeStaticInlining() {
        assertInlined(getGraph("invokeStaticSnippet"));
        assertInlined(getGraph("invokeStaticOnInstanceSnippet"));
    }

    @SuppressWarnings("all")
    public static Boolean invokeStaticSnippet(boolean value) {
        return Boolean.valueOf(value);
    }
    @SuppressWarnings("all")
    public static Boolean invokeStaticOnInstanceSnippet(Boolean obj, boolean value) {
        return obj.valueOf(value);
    }


    @Test
    public void testStaticBindableInlining() {
        assertInlined(getGraph("invokeConstructorSnippet"));
        assertInlined(getGraph("invokeFinalMethodSnippet"));
        assertInlined(getGraph("invokeMethodOnFinalClassSnippet"));
    }

    @SuppressWarnings("all")
    public static Object invokeConstructorSnippet(int value) {
        return new SuperClass(value);
    }
    @SuppressWarnings("all")
    public static int invokeFinalMethodSnippet(SuperClass superClass, SubClassA subClassA, FinalSubClass finalSubClass) {
        return superClass.publicFinalMethod() +
               subClassA.publicFinalMethod() +
               finalSubClass.publicFinalMethod() +
               superClass.protectedFinalMethod() +
               subClassA.protectedFinalMethod() +
               finalSubClass.protectedFinalMethod();
    }
    @SuppressWarnings("all")
    public static int invokeMethodOnFinalClassSnippet(FinalSubClass finalSubClass) {
        return finalSubClass.publicFinalMethod() +
               finalSubClass.publicNotOverriddenMethod() +
               finalSubClass.publicOverriddenMethod() +
               finalSubClass.protectedFinalMethod() +
               finalSubClass.protectedNotOverriddenMethod() +
               finalSubClass.protectedOverriddenMethod();
    }


    @Test
    public void testClassHierarchyAnalysis() {
        assertInlined(getGraph("invokeLeafClassMethodSnippet"));
        assertInlined(getGraph("invokeConcreteMethodSnippet"));
        assertInlined(getGraph("invokeSingleImplementorInterfaceSnippet"));
        assertInlined(getGraph("invokeConcreteInterfaceMethodSnippet"));

        assertNotInlined(getGraph("invokeOverriddenInterfaceMethodSnippet"));
    }

    @SuppressWarnings("all")
    public static int invokeLeafClassMethodSnippet(SubClassA subClassA) {
        return subClassA.publicFinalMethod() +
               subClassA.publicNotOverriddenMethod() +
               subClassA.publicOverriddenMethod();
    }
    @SuppressWarnings("all")
    public static int invokeConcreteMethodSnippet(SuperClass superClass) {
        return superClass.publicNotOverriddenMethod() +
               superClass.protectedNotOverriddenMethod();
    }
    @SuppressWarnings("all")
    public static int invokeSingleImplementorInterfaceSnippet(SingleImplementorInterface testInterface) {
        return testInterface.publicNotOverriddenMethod() +
               testInterface.publicOverriddenMethod();
    }
    @SuppressWarnings("all")
    public static int invokeConcreteInterfaceMethodSnippet(MultipleImplementorsInterface testInterface) {
        return testInterface.publicNotOverriddenMethod();
    }
    @SuppressWarnings("all")
    public static int invokeOverriddenInterfaceMethodSnippet(MultipleImplementorsInterface testInterface) {
        return testInterface.publicOverriddenMethod();
    }

    private StructuredGraph getGraph(final String snippet) {
        return Debug.scope("InliningTest", new DebugDumpScope(snippet), new Callable<StructuredGraph>() {
            @Override
            public StructuredGraph call() {
                StructuredGraph graph = parse(snippet);
                PhasePlan phasePlan = getDefaultPhasePlan();
                Assumptions assumptions = new Assumptions(true);
                new ComputeProbabilityPhase().apply(graph);
                Debug.dump(graph, "Graph");
                new InliningPhase(null, runtime(), null, assumptions, null, phasePlan, OptimisticOptimizations.ALL).apply(graph);
                Debug.dump(graph, "Graph");
                new CanonicalizerPhase(null, runtime(), assumptions).apply(graph);
                new DeadCodeEliminationPhase().apply(graph);
                return graph;
            }
        });
    }

    private static StructuredGraph assertInlined(StructuredGraph graph) {
        return assertNotInGraph(graph, Invoke.class);
    }

    private static StructuredGraph assertNotInlined(StructuredGraph graph) {
        return assertInGraph(graph, Invoke.class);
    }

    private static StructuredGraph assertNotInGraph(StructuredGraph graph, Class<?> clazz) {
        for (Node node: graph.getNodes()) {
            if (clazz.isInstance(node)) {
                fail(node.toString());
            }
        }
        return graph;
    }

    private static StructuredGraph assertInGraph(StructuredGraph graph, Class<?> clazz) {
        for (Node node: graph.getNodes()) {
            if (clazz.isInstance(node)) {
                return graph;
            }
        }
        fail("Graph does not contain a node of class " + clazz.getName());
        return graph;
    }


    // some interfaces and classes for testing
    private interface MultipleImplementorsInterface {
        int publicNotOverriddenMethod();
        int publicOverriddenMethod();
    }

    private interface SingleImplementorInterface {
        int publicNotOverriddenMethod();
        int publicOverriddenMethod();
    }

    private static class SuperClass implements MultipleImplementorsInterface {
        protected int value;

        public SuperClass(int value) {
            this.value = value;
        }

        public int publicNotOverriddenMethod() {
            return value;
        }

        public int publicOverriddenMethod() {
            return value;
        }

        protected int protectedNotOverriddenMethod() {
            return value;
        }

        protected int protectedOverriddenMethod() {
            return value;
        }

        public final int publicFinalMethod() {
            return value + 255;
        }

        protected final int protectedFinalMethod() {
            return value + 255;
        }
    }

    private static class SubClassA extends SuperClass implements SingleImplementorInterface {
        public SubClassA(int value) {
            super(value);
        }

        @Override
        public int publicOverriddenMethod() {
            return value + 2;
        }

        @Override
        protected int protectedOverriddenMethod() {
            return value * 2;
        }
    }

    private static class SubClassB extends SuperClass {
        public SubClassB(int value) {
            super(value);
        }

        @Override
        public int publicOverriddenMethod() {
            return value + 3;
        }

        @Override
        protected int protectedOverriddenMethod() {
            return value * 3;
        }
    }

    private static class SubClassC extends SuperClass {
        public SubClassC(int value) {
            super(value);
        }

        @Override
        public int publicOverriddenMethod() {
            return value + 4;
        }

        @Override
        protected int protectedOverriddenMethod() {
            return value * 4;
        }
    }

    private static final class FinalSubClass extends SuperClass {
        public FinalSubClass(int value) {
            super(value);
        }

        @Override
        public int publicOverriddenMethod() {
            return value + 5;
        }

        @Override
        protected int protectedOverriddenMethod() {
            return value * 5;
        }
    }
}
