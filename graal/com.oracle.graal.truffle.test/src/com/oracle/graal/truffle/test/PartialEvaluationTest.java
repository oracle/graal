/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle.test;

import org.junit.*;

import com.oracle.graal.compiler.test.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.printer.*;
import com.oracle.graal.truffle.*;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.nodes.*;

public class PartialEvaluationTest extends GraalCompilerTest {
    private final TruffleCompilerImpl truffleCompiler;

    public PartialEvaluationTest() {
        // Make sure Truffle runtime is initialized.
        Assert.assertTrue(Truffle.getRuntime() != null);
        this.truffleCompiler = new TruffleCompilerImpl();

        DebugEnvironment.initialize(System.out);
    }

    protected OptimizedCallTarget assertPartialEvalEquals(String methodName, RootNode root) {
        return assertPartialEvalEquals(methodName, root, new Object[0]);
    }

    protected OptimizedCallTarget compileHelper(String methodName, RootNode root, Object[] arguments) {
        final OptimizedCallTarget compilable = (OptimizedCallTarget) Truffle.getRuntime().createCallTarget(root);
        StructuredGraph actual = partialEval(compilable, arguments, AllowAssumptions.YES);
        truffleCompiler.compileMethodHelper(actual, methodName, null, getSpeculationLog(), compilable);
        return compilable;
    }

    protected OptimizedCallTarget assertPartialEvalEquals(String methodName, RootNode root, Object[] arguments) {
        final OptimizedCallTarget compilable = (OptimizedCallTarget) Truffle.getRuntime().createCallTarget(root);
        StructuredGraph actual = partialEval(compilable, arguments, AllowAssumptions.YES);
        truffleCompiler.compileMethodHelper(actual, methodName, null, getSpeculationLog(), compilable);
        removeFrameStates(actual);
        StructuredGraph expected = parseForComparison(methodName);
        Assert.assertEquals(getCanonicalGraphString(expected, true, true), getCanonicalGraphString(actual, true, true));
        return compilable;
    }

    protected void assertPartialEvalNoInvokes(RootNode root) {
        assertPartialEvalNoInvokes(root, new Object[0]);
    }

    protected void assertPartialEvalNoInvokes(RootNode root, Object[] arguments) {
        final OptimizedCallTarget compilable = (OptimizedCallTarget) Truffle.getRuntime().createCallTarget(root);
        StructuredGraph actual = partialEval(compilable, arguments, AllowAssumptions.YES);
        removeFrameStates(actual);
        for (MethodCallTargetNode node : actual.getNodes(MethodCallTargetNode.class)) {
            Assert.fail("Found invalid method call target node: " + node);
        }
    }

    protected StructuredGraph partialEval(OptimizedCallTarget compilable, Object[] arguments, AllowAssumptions allowAssumptions) {
        // Executed AST so that all classes are loaded and initialized.
        compilable.call(arguments);
        compilable.call(arguments);
        compilable.call(arguments);

        try (Scope s = Debug.scope("TruffleCompilation", new TruffleDebugJavaMethod(compilable))) {
            return truffleCompiler.getPartialEvaluator().createGraph(compilable, allowAssumptions, null);
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    protected void removeFrameStates(StructuredGraph graph) {
        for (FrameState frameState : graph.getNodes(FrameState.class)) {
            frameState.replaceAtUsages(null);
            frameState.safeDelete();
        }
        new CanonicalizerPhase(true).apply(graph, new PhaseContext(getProviders()));
        new DeadCodeEliminationPhase().apply(graph);
    }

    protected StructuredGraph parseForComparison(final String methodName) {
        try (Scope s = Debug.scope("Truffle", new DebugDumpScope("Comparison: " + methodName))) {
            StructuredGraph graph = parseEager(methodName, AllowAssumptions.YES);
            compile(graph.method(), graph);
            return graph;
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }
}
