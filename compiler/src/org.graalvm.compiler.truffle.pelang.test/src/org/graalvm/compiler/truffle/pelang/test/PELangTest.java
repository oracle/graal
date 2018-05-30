/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.pelang.test;

import static org.graalvm.compiler.core.common.CompilationRequestIdentifier.asCompilationRequest;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.truffle.common.TruffleCompilerOptions;
import org.graalvm.compiler.truffle.runtime.DefaultInliningPolicy;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.TruffleInlining;
import org.graalvm.compiler.truffle.test.PartialEvaluationTest;
import org.junit.Assert;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.RootNode;

import jdk.vm.ci.meta.SpeculationLog;

public abstract class PELangTest extends PartialEvaluationTest {

    protected OptimizedCallTarget createCallTarget(RootNode rootNode) {
        return (OptimizedCallTarget) Truffle.getRuntime().createCallTarget(rootNode);
    }

    protected void warmupCallTarget(CallTarget callTarget) {
        // run call target so that all classes are loaded and initialized
        callTarget.call();
        callTarget.call();
        callTarget.call();
    }

    protected void warmupCallTarget(CallTarget callTarget, Object[] arguments) {
        // run call target so that all classes are loaded and initialized
        callTarget.call(arguments);
        callTarget.call(arguments);
        callTarget.call(arguments);
    }

    protected StructuredGraph partiallyEvaluate(OptimizedCallTarget callTarget) {
        CompilationIdentifier compilationId = truffleCompiler.getCompilationIdentifier(callTarget);

        OptionValues options = TruffleCompilerOptions.getOptions();
        TruffleInlining inliningDecision = new TruffleInlining(callTarget, new DefaultInliningPolicy());
        SpeculationLog speculationLog = callTarget.getSpeculationLog();
        return truffleCompiler.getPartialEvaluator().createGraph(getDebugContext(options), callTarget, inliningDecision, AllowAssumptions.YES, compilationId, speculationLog, null);
    }

    protected CompilationResult compileGraph(StructuredGraph graph, OptimizedCallTarget callTarget) {
        CompilationResult result = truffleCompiler.compilePEGraph(graph, callTarget.toString(), null, callTarget, asCompilationRequest(graph.compilationId()), null);
        removeFrameStates(graph);
        return result;
    }

    protected void assertCallResultEquals(Object expected, CallTarget callTarget) {
        Assert.assertEquals(expected, callTarget.call());
    }

    protected void assertCallResultEquals(Object expected, CallTarget callTarget, Object[] argumentValues) {
        Assert.assertEquals(expected, callTarget.call(argumentValues));
    }

    protected void assertGraphEquals(String methodName, StructuredGraph graph) {
        StructuredGraph expected = parseForComparison(methodName, graph.getDebug());
        Assert.assertEquals(getCanonicalGraphString(expected, true, true), getCanonicalGraphString(graph, true, true));
    }

}
