/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.tests;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

import junit.framework.*;

import com.oracle.graal.api.*;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.*;
import com.oracle.graal.compiler.phases.*;
import com.oracle.graal.compiler.phases.PhasePlan.PhasePosition;
import com.oracle.graal.compiler.schedule.*;
import com.oracle.graal.cri.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Node.Verbosity;
import com.oracle.graal.java.*;
import com.oracle.graal.lir.cfg.*;
import com.oracle.graal.nodes.*;

/**
 * Base class for Graal compiler unit tests. These are white box tests
 * for Graal compiler transformations. The general pattern for a test is:
 * <ol>
 * <li>Create a graph by {@linkplain #parse(String) parsing} a method.</li>
 * <li>Manually modify the graph (e.g. replace a parameter node with a constant).</li>
 * <li>Apply a transformation to the graph.</li>
 * <li>Assert that the transformed graph is equal to an expected graph.</li>
 * </ol>
 * <p>
 * See {@link InvokeTest} as an example.
 * <p>
 * The tests can be run in Eclipse with the "Compiler Unit Test" Eclipse
 * launch configuration found in the top level of this project or by
 * running {@code mx unittest} on the command line.
 */
public abstract class GraalCompilerTest {

    protected final GraalCodeCacheProvider runtime;

    public GraalCompilerTest() {
        Debug.enable();
        this.runtime = Graal.getRuntime().getCapability(GraalCodeCacheProvider.class);
    }

    protected void assertEquals(StructuredGraph expected, StructuredGraph graph) {
        String expectedString = getCanonicalGraphString(expected);
        String actualString = getCanonicalGraphString(graph);
        String mismatchString = "mismatch in graphs:\n========= expected =========\n" + expectedString + "\n\n========= actual =========\n" + actualString;

        if (expected.getNodeCount() != graph.getNodeCount()) {
            Debug.dump(expected, "Node count not matching - expected");
            Debug.dump(graph, "Node count not matching - actual");
            Assert.fail("Graphs do not have the same number of nodes: " + expected.getNodeCount() + " vs. " + graph.getNodeCount() + "\n" + mismatchString);
        }
        if (!expectedString.equals(actualString)) {
            Debug.dump(expected, "mismatching graphs - expected");
            Debug.dump(graph, "mismatching graphs - actual");
            Assert.fail(mismatchString);
        }
    }

    protected void assertConstantReturn(StructuredGraph graph, int value) {
        String graphString = getCanonicalGraphString(graph);
        Assert.assertEquals("unexpected number of ReturnNodes: " + graphString, graph.getNodes(ReturnNode.class).count(), 1);
        ValueNode result = graph.getNodes(ReturnNode.class).first().result();
        Assert.assertTrue("unexpected ReturnNode result node: " + graphString, result.isConstant());
        Assert.assertEquals("unexpected ReturnNode result kind: " + graphString, result.asConstant().kind, Kind.Int);
        Assert.assertEquals("unexpected ReturnNode result: " + graphString, result.asConstant().asInt(), value);
    }

    protected static String getCanonicalGraphString(StructuredGraph graph) {
        SchedulePhase schedule = new SchedulePhase();
        schedule.apply(graph);

        NodeMap<Integer> canonicalId = graph.createNodeMap();
        int nextId = 0;

        StringBuilder result = new StringBuilder();
        for (Block block : schedule.getCFG().getBlocks()) {
            result.append("Block " + block + " ");
            if (block == schedule.getCFG().getStartBlock()) {
                result.append("* ");
            }
            result.append("-> ");
            for (Block succ : block.getSuccessors()) {
                result.append(succ + " ");
            }
            result.append("\n");
            for (Node node : schedule.getBlockToNodesMap().get(block)) {
                int id;
                if (canonicalId.get(node) != null) {
                    id = canonicalId.get(node);
                } else {
                    id = nextId++;
                    canonicalId.set(node, id);
                }
                String name = node instanceof ConstantNode ? node.toString(Verbosity.Name) : node.getClass().getSimpleName();
                result.append("  " + id + "|" + name + "    (" + node.usages().size() + ")\n");
            }
        }
        return result.toString();
    }

    protected GraalCodeCacheProvider runtime() {
        return runtime;
    }

    /**
     * Parses a Java method to produce a graph.
     *
     * @param methodName the name of the method in {@code this.getClass()} to be parsed
     */
    protected StructuredGraph parse(String methodName) {
        return parse(getMethod(methodName));
    }

    protected Method getMethod(String methodName) {
        Method found = null;
        for (Method m : this.getClass().getMethods()) {
            if (m.getName().equals(methodName)) {
                Assert.assertNull(found);
                found = m;
            }
        }
        if (found != null) {
            return found;
        } else {
            throw new RuntimeException("method not found: " + methodName);
        }
    }

    private static int compilationId = 0;

    protected void assertEquals(Object expected, Object actual) {
        Assert.assertEquals(expected, actual);
    }

    protected void test(String name, Object... args) {
        Method method = getMethod(name);
        Object expect = null;
        Throwable exception = null;
        try {
            // This gives us both the expected return value as well as ensuring that the method to be compiled is fully resolved
            expect = method.invoke(null, args);
        } catch (InvocationTargetException e) {
            exception = e.getTargetException();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        InstalledCode compiledMethod = getCode(runtime.getResolvedJavaMethod(method), parse(method));

        if (exception != null) {
            try {
                compiledMethod.executeVarargs(args);
                Assert.fail("expected " + exception);
            } catch (Throwable e) {
                Assert.assertEquals(exception.getClass(), e.getClass());
            }
        } else {
            Object actual = compiledMethod.executeVarargs(args);
            assertEquals(expect, actual);
        }
    }

    private Map<ResolvedJavaMethod, InstalledCode> cache = new HashMap<>();

    /**
     * Gets installed code for a given method and graph, compiling it first if necessary.
     */
    protected InstalledCode getCode(final ResolvedJavaMethod method, final StructuredGraph graph) {
        return getCode(method, graph, false);
    }

    /**
     * Gets installed code for a given method and graph, compiling it first if necessary.
     *
     * @param forceCompile specifies whether to ignore any previous code cached for the (method, key) pair
     */
    protected InstalledCode getCode(final ResolvedJavaMethod method, final StructuredGraph graph, boolean forceCompile) {
        if (!forceCompile) {
            InstalledCode cached = cache.get(method);
            if (cached != null && cached.isValid()) {
                return cached;
            }
        }
        InstalledCode installedCode = Debug.scope("Compiling", new DebugDumpScope(String.valueOf(compilationId++), true), new Callable<InstalledCode>() {
            public InstalledCode call() throws Exception {
                CompilationResult targetMethod = runtime.compile(method, graph);
                return addMethod(method, targetMethod);
            }
        });
        cache.put(method, installedCode);
        return installedCode;
    }

    protected InstalledCode addMethod(final ResolvedJavaMethod method, final CompilationResult tm) {
        GraalCompiler graalCompiler = Graal.getRuntime().getCapability(GraalCompiler.class);
        assert graalCompiler != null;
        return Debug.scope("CodeInstall", new Object[] {graalCompiler, method}, new Callable<InstalledCode>() {
            @Override
            public InstalledCode call() throws Exception {
                final CodeInfo[] info = Debug.isDumpEnabled() ? new CodeInfo[1] : null;
                InstalledCode installedMethod = runtime.addMethod(method, tm, info);
                if (info != null) {
                    Debug.dump(new Object[] {tm, info[0]}, "After code installation");
                }
                return installedMethod;
            }
        });
    }

    /**
     * Parses a Java method to produce a graph.
     *
     * @param methodName the name of the method in {@code this.getClass()} to be parsed
     */
    protected StructuredGraph parseProfiled(String methodName) {
        return parseProfiled(getMethod(methodName));
    }

    /**
     * Parses a Java method to produce a graph.
     */
    protected StructuredGraph parse(Method m) {
        ResolvedJavaMethod javaMethod = runtime.getResolvedJavaMethod(m);
        StructuredGraph graph = new StructuredGraph(javaMethod);
        new GraphBuilderPhase(runtime, GraphBuilderConfiguration.getSnippetDefault(), OptimisticOptimizations.ALL).apply(graph);
        return graph;
    }

    /**
     * Parses a Java method to produce a graph.
     */
    protected StructuredGraph parseProfiled(Method m) {
        ResolvedJavaMethod javaMethod = runtime.getResolvedJavaMethod(m);
        StructuredGraph graph = new StructuredGraph(javaMethod);
        new GraphBuilderPhase(runtime, GraphBuilderConfiguration.getDefault(), OptimisticOptimizations.ALL).apply(graph);
        return graph;
    }

    protected PhasePlan getDefaultPhasePlan() {
        PhasePlan plan = new PhasePlan();
        plan.addPhase(PhasePosition.AFTER_PARSING, new GraphBuilderPhase(runtime, GraphBuilderConfiguration.getSnippetDefault(), OptimisticOptimizations.ALL));
        return plan;
    }
}
