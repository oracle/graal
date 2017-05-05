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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.impl.TVMCI;
import com.oracle.truffle.api.nodes.RootNode;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import static org.graalvm.compiler.core.common.CompilationRequestIdentifier.asCompilationRequest;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.Debug.Scope;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;

final class GraalTestTVMCI extends TVMCI.Test<GraalTestTVMCI.TestCallTarget> {

    private final GraalTruffleRuntime truffleRuntime;

    static final class TestCallTarget implements CallTarget {

        private final String testName;
        private final OptimizedCallTarget optimizedCallTarget;

        private TestCallTarget(String testName, OptimizedCallTarget optimizedCallTarget) {
            this.testName = testName;
            this.optimizedCallTarget = optimizedCallTarget;
        }

        @Override
        public Object call(Object... arguments) {
            return optimizedCallTarget.call(arguments);
        }
    }

    GraalTestTVMCI(GraalTruffleRuntime truffleRuntime) {
        this.truffleRuntime = truffleRuntime;
    }

    @Override
    public TestCallTarget createTestCallTarget(String testName, RootNode testNode) {
        return new TestCallTarget(testName, (OptimizedCallTarget) truffleRuntime.createCallTarget(testNode));
    }

    @Override
    public void finishWarmup(TestCallTarget callTarget) {
        TruffleCompiler compiler = truffleRuntime.getTruffleCompiler();
        ResolvedJavaMethod rootMethod = compiler.getPartialEvaluator().rootForCallTarget(callTarget.optimizedCallTarget);
        CompilationIdentifier compilationId = truffleRuntime.getCompilationIdentifier(callTarget.optimizedCallTarget, rootMethod, compiler.backend);
        StructuredGraph graph = partialEval(callTarget.optimizedCallTarget, AllowAssumptions.YES, compilationId);
        truffleRuntime.getTruffleCompiler().compileMethodHelper(graph, callTarget.testName, null, callTarget.optimizedCallTarget, asCompilationRequest(compilationId));
    }

    @SuppressWarnings("try")
    protected StructuredGraph partialEval(OptimizedCallTarget compilable, AllowAssumptions allowAssumptions, CompilationIdentifier compilationId) {
        try (Scope s = Debug.scope("TruffleCompilation", new TruffleDebugJavaMethod(compilable))) {
            TruffleCompiler compiler = truffleRuntime.getTruffleCompiler();
            return compiler.getPartialEvaluator().createGraph(compilable, new TruffleInlining(compilable, new DefaultInliningPolicy()), allowAssumptions, compilationId, null);
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }
}
