/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle;

import static org.graalvm.compiler.core.common.CompilationRequestIdentifier.asCompilationRequest;

import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.options.OptionValues;

import com.oracle.truffle.api.impl.TVMCI;
import com.oracle.truffle.api.nodes.RootNode;

import jdk.vm.ci.meta.ResolvedJavaMethod;

final class GraalTestTVMCI extends TVMCI.Test<OptimizedCallTarget> {

    private final GraalTruffleRuntime truffleRuntime;

    GraalTestTVMCI(GraalTruffleRuntime truffleRuntime) {
        this.truffleRuntime = truffleRuntime;
    }

    @Override
    public OptimizedCallTarget createTestCallTarget(RootNode testNode) {
        return (OptimizedCallTarget) truffleRuntime.createCallTarget(testNode);
    }

    @Override
    public void finishWarmup(OptimizedCallTarget callTarget, String testName) {
        OptionValues options = TruffleCompilerOptions.getOptions();
        DebugContext debug = DebugContext.create(options, DebugHandlersFactory.LOADER);
        TruffleCompiler compiler = truffleRuntime.getTruffleCompiler();
        ResolvedJavaMethod rootMethod = compiler.getPartialEvaluator().rootForCallTarget(callTarget);
        CompilationIdentifier compilationId = truffleRuntime.getCompilationIdentifier(callTarget, rootMethod, compiler.backend);
        StructuredGraph graph = partialEval(debug, callTarget, AllowAssumptions.YES, compilationId);
        truffleRuntime.getTruffleCompiler().compileMethodHelper(graph, testName, null, callTarget, asCompilationRequest(compilationId));
    }

    @SuppressWarnings("try")
    protected StructuredGraph partialEval(DebugContext debug, OptimizedCallTarget compilable, AllowAssumptions allowAssumptions, CompilationIdentifier compilationId) {
        try (DebugContext.Scope s = debug.scope("TruffleCompilation", new TruffleDebugJavaMethod(compilable))) {
            TruffleCompiler compiler = truffleRuntime.getTruffleCompiler();
            return compiler.getPartialEvaluator().createGraph(debug, compilable, new TruffleInlining(compilable, new DefaultInliningPolicy()), allowAssumptions, compilationId, null);
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }
}
