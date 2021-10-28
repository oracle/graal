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
package org.graalvm.compiler.replacements.test;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.replacements.nodes.MacroNode;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests if {@link MethodSubstitution}s are inlined correctly. Most test cases only assert that
 * there are no remaining invocations in the graph. This is sufficient if the method that is being
 * substituted is a native method. For Java methods, additional checks are necessary.
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
                new LoweringPhase(this.createCanonicalizerPhase(), LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, context);
            }
            if (graph.getNodes().filter(MacroNode.class).isNotEmpty()) {
                new LoweringPhase(this.createCanonicalizerPhase(), LoweringTool.StandardLoweringStage.MID_TIER).apply(graph, context);
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

    protected static StructuredGraph assertNotInGraph(StructuredGraph graph, Class<?> clazz) {
        for (Node node : graph.getNodes()) {
            if (clazz.isInstance(node)) {
                fail(String.format("found unexpected instance of %s in %s: %s", clazz, graph, node.toString()));
            }
        }
        return graph;
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

    protected static StructuredGraph assertInGraph(StructuredGraph graph, Class<?>... clazzes) {
        for (Node node : graph.getNodes()) {
            for (Class<?> clazz : clazzes) {
                if (clazz.isInstance(node)) {
                    return graph;
                }
            }
        }
        if (clazzes.length == 1) {
            fail("Graph does not contain a node of class " + clazzes[0].getName());
        } else {
            fail("Graph does not contain a node of one these classes class " + Arrays.toString(clazzes));

        }
        return graph;
    }

    protected static Object executeVarargsSafe(InstalledCode code, Object... args) {
        try {
            return code.executeVarargs(args);
        } catch (InvalidInstalledCodeException e) {
            throw new RuntimeException(e);
        }
    }

    protected Object invokeSafe(ResolvedJavaMethod method, Object receiver, Object... args) {
        try {
            return invoke(method, receiver, args);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | InstantiationException e) {
            throw new RuntimeException(e);
        }
    }

}
