/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.phases.common.DeadCodeEliminationPhase;
import jdk.graal.compiler.phases.common.HighTierLoweringPhase;
import jdk.graal.compiler.phases.common.MidTierLoweringPhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.replacements.nodes.MacroNode;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests if method substitutions are inlined correctly. Most test cases only assert that there are
 * no remaining invocations in the graph. This is sufficient if the method that is being substituted
 * is a native method. For Java methods, additional checks are necessary.
 */
public abstract class MethodSubstitutionTest extends GraalCompilerTest {

    protected StructuredGraph testGraph(final String snippet) {
        return testGraph(snippet, null);
    }

    protected StructuredGraph testGraph(final String snippet, boolean assertInvoke) {
        return testGraph(snippet, null, assertInvoke);
    }

    protected StructuredGraph testGraph(final String snippet, String name) {
        return testGraph(snippet, name, false);
    }

    @SuppressWarnings("try")
    protected StructuredGraph testGraph(final String snippet, String name, boolean assertInvoke) {
        return testGraph(getResolvedJavaMethod(snippet), name, assertInvoke);
    }

    /**
     * Tests properties of the graph produced by parsing {@code method}. The properties tested are:
     * <ul>
     * <li>the graph does not contain any {@link MacroNode}s</li>
     * <li>if {@code name != null} the graph does (if {@code assertInvoke == true}) or does not (if
     * {@code assertInvoke == false}) contain a call to a method with this name</li>
     * <li>if {@code name == null} the graph does (if {@code assertInvoke == true}) or does not (if
     * {@code assertInvoke == false}) contain an {@link Invoke} node</li>
     * </ul>
     */
    @SuppressWarnings("try")
    protected StructuredGraph testGraph(final ResolvedJavaMethod method, String name, boolean assertInvoke) {
        DebugContext debug = getDebugContext();
        try (DebugContext.Scope s = debug.scope("MethodSubstitutionTest", method)) {
            StructuredGraph graph = parseEager(method, AllowAssumptions.YES, debug);
            HighTierContext context = getDefaultHighTierContext();
            debug.dump(DebugContext.BASIC_LEVEL, graph, "Graph");
            createInliningPhase().apply(graph, context);
            debug.dump(DebugContext.BASIC_LEVEL, graph, "Graph");
            createCanonicalizerPhase().apply(graph, context);
            new DeadCodeEliminationPhase().apply(graph);
            // Try to ensure any macro nodes are lowered to expose any resulting invokes
            if (graph.getNodes().filter(MacroNode.class).isNotEmpty()) {
                new HighTierLoweringPhase(this.createCanonicalizerPhase()).apply(graph, context);
            }
            if (graph.getNodes().filter(MacroNode.class).isNotEmpty()) {
                new MidTierLoweringPhase(this.createCanonicalizerPhase()).apply(graph, context);
            }
            assertNotInGraph(graph, MacroNode.class);
            if (name != null) {
                for (Node node : graph.getNodes()) {
                    if (node instanceof Invoke) {
                        Invoke invoke = (Invoke) node;
                        if (invoke.callTarget() instanceof MethodCallTargetNode) {
                            MethodCallTargetNode call = (MethodCallTargetNode) invoke.callTarget();
                            boolean found = call.targetMethod().getName().equals(name);
                            if (assertInvoke) {
                                assertTrue(found, "Expected to find a call to %s", name);
                            } else {
                                assertFalse(found, "Unexpected call to %s", name);
                            }
                        }
                    }

                }
            } else {
                if (assertInvoke) {
                    assertInGraph(graph, Invoke.class);
                } else {
                    assertNotInGraph(graph, Invoke.class);
                }
            }
            return graph;
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    protected void testSubstitution(String testMethodName, Class<?> intrinsicClass, Class<?> holder, String methodName, Class<?>[] parameterTypes, boolean optional, boolean forceCompilation,
                    Object[] args1, Object[] args2) {
        ResolvedJavaMethod realMethod = getResolvedJavaMethod(holder, methodName, parameterTypes);
        ResolvedJavaMethod testMethod = getResolvedJavaMethod(testMethodName);
        StructuredGraph graph = testGraph(testMethodName);

        // Check to see if the resulting graph contains the expected node
        StructuredGraph replacement = getReplacements().getInlineSubstitution(realMethod, 0, Invoke.InlineControl.Normal, false, null, graph.allowAssumptions(), graph.getOptions());
        if (replacement == null && !optional) {
            assertInGraph(graph, intrinsicClass);
        }

        // Force compilation
        InstalledCode code = getCode(testMethod, null, forceCompilation);
        assert optional || code != null;

        for (int i = 0; i < args1.length; i++) {
            Object arg1 = args1[i];
            Object arg2 = args2[i];
            Object expected = invokeSafe(realMethod, null, arg1, arg2);
            // Verify that the original method and the substitution produce the same value
            assertDeepEquals(expected, invokeSafe(testMethod, null, arg1, arg2));
            // Verify that the generated code and the original produce the same value
            assertDeepEquals(expected, executeVarargsSafe(code, arg1, arg2));
        }
    }

}
