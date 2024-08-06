/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test;

import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.truffle.PartialEvaluator;
import jdk.graal.compiler.truffle.PostPartialEvaluationSuite;
import jdk.graal.compiler.truffle.TruffleCompilerImpl;
import jdk.graal.compiler.truffle.TruffleTierContext;
import jdk.graal.compiler.truffle.phases.inlining.AgnosticInliningPhase;
import org.graalvm.polyglot.Context;
import org.junit.Before;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.compiler.TruffleCompilationTask;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import com.oracle.truffle.runtime.OptimizedDirectCallNode;

public class AgnosticInliningPhaseTest extends PartialEvaluationTest {

    protected final TruffleRuntime runtime = Truffle.getRuntime();

    @Before
    public void before() {
        setupContext(Context.newBuilder().allowAllAccess(true).allowExperimentalOptions(true).option("compiler.LanguageAgnosticInlining", Boolean.TRUE.toString()).option(
                        "compiler.InliningInliningBudget",
                        "1").build());
    }

    protected StructuredGraph runLanguageAgnosticInliningPhase(OptimizedCallTarget callTarget) {
        TruffleCompilerImpl compiler = getTruffleCompiler(callTarget);
        final PartialEvaluator partialEvaluator = compiler.getPartialEvaluator();
        final CompilationIdentifier compilationIdentifier = new CompilationIdentifier() {
            @Override
            public String toString(Verbosity verbosity) {
                return "";
            }
        };
        final TruffleTierContext context = new TruffleTierContext(partialEvaluator,
                        compiler.getOrCreateCompilerOptions(callTarget),
                        getDebugContext(), callTarget, partialEvaluator.rootForCallTarget(callTarget),
                        compilationIdentifier, getSpeculationLog(),
                        new TruffleCompilationTask() {

                            @Override
                            public boolean isCancelled() {
                                return false;
                            }

                            @Override
                            public boolean isLastTier() {
                                return true;
                            }

                            @Override
                            public boolean hasNextTier() {
                                return false;
                            }
                        }, null);
        final AgnosticInliningPhase agnosticInliningPhase = new AgnosticInliningPhase(partialEvaluator,
                        new PostPartialEvaluationSuite(compiler.getOrCreateCompilerOptions(callTarget), false));
        agnosticInliningPhase.apply(context.graph, context);
        return context.graph;
    }

    protected static final OptimizedCallTarget createDummyNode() {
        return (OptimizedCallTarget) new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                return null;
            }
        }.getCallTarget();
    }

    protected class CallsInnerNodeTwice extends RootNode {

        @Child private OptimizedDirectCallNode callNode1;
        @Child private OptimizedDirectCallNode callNode2;

        public CallsInnerNodeTwice(RootCallTarget toCall) {
            super(null);
            this.callNode1 = (OptimizedDirectCallNode) runtime.createDirectCallNode(toCall);
            this.callNode2 = (OptimizedDirectCallNode) runtime.createDirectCallNode(toCall);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            callNode1.call(frame.getArguments());
            return callNode2.call(12345);
        }
    }
}
