/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test.builtins;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.truffle.GraalTruffleCompilationListener;
import org.graalvm.compiler.truffle.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.OptimizedCallTarget;
import org.graalvm.compiler.truffle.OptimizedDirectCallNode;
import org.graalvm.compiler.truffle.TruffleInlining;
import org.graalvm.compiler.truffle.TruffleInlining.CallTreeNodeVisitor;
import org.graalvm.compiler.truffle.TruffleInliningDecision;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.sl.runtime.SLFunction;

/**
 * Returns <code>true</code> if a function got inlined for all calls from a given {@link SLFunction}
 * . If no direct calls to the given {@link SLFunction} could be resolved or the call got inlined
 * for some callsites and for some not then an {@link AssertionError} is thrown.
 */
@NodeInfo(shortName = "isInlined")
public abstract class SLIsInlinedBuiltin extends SLGraalRuntimeBuiltin {

    private static final Map<OptimizedCallTarget, TruffleInlining> inliningDecisions = Collections.synchronizedMap(new WeakHashMap<>());

    static {
        ((GraalTruffleRuntime) Truffle.getRuntime()).addCompilationListener(new GraalTruffleCompilationListener() {

            @Override
            public void notifyStartup(GraalTruffleRuntime runtime) {

            }

            @Override
            public void notifyShutdown(GraalTruffleRuntime runtime) {

            }

            @Override
            public void notifyCompilationTruffleTierFinished(OptimizedCallTarget target, TruffleInlining inliningDecision, StructuredGraph graph) {
            }

            @Override
            public void notifyCompilationDeoptimized(OptimizedCallTarget target, Frame frame) {
            }

            @Override
            public void notifyCompilationSuccess(OptimizedCallTarget target, TruffleInlining inliningDecision, StructuredGraph graph, CompilationResult result) {
                inliningDecisions.put(target, inliningDecision);
            }

            @Override
            public void notifyCompilationStarted(OptimizedCallTarget target) {

            }

            @Override
            public void notifyCompilationSplit(OptimizedDirectCallNode callNode) {

            }

            @Override
            public void notifyCompilationQueued(OptimizedCallTarget target) {

            }

            @Override
            public void notifyCompilationInvalidated(OptimizedCallTarget target, Object source, CharSequence reason) {

            }

            @Override
            public void notifyCompilationGraalTierFinished(OptimizedCallTarget target, StructuredGraph graph) {

            }

            @Override
            public void notifyCompilationFailed(OptimizedCallTarget target, StructuredGraph graph, Throwable t) {

            }

            @Override
            public void notifyCompilationDequeued(OptimizedCallTarget target, Object source, CharSequence reason) {

            }
        });
    }

    @Specialization
    @TruffleBoundary
    public Object isInlined(SLFunction rootFunction, SLFunction parentFunction, SLFunction inlinedFunction) {
        InliningTrace trace = new InliningTrace();

        for (OptimizedCallTarget target : findDuplicateCallTargets((OptimizedCallTarget) rootFunction.getCallTarget())) {
            if (target.isValid()) {
                searchInlined(trace, target, parentFunction, inlinedFunction);
            }
        }

        if (trace.allFalse && trace.allTrue) {
            throw new AssertionError(String.format("No optimized calls found from %s to %s .", parentFunction, inlinedFunction));
        } else if (!trace.allFalse && !trace.allTrue) {
            throw new AssertionError(String.format("Some optimized calls from %s to %s are inlined and some are not.", parentFunction, inlinedFunction));
        }
        if (trace.allTrue) {
            return true;
        } else {
            return false;
        }
    }

    private static void searchInlined(InliningTrace trace, OptimizedCallTarget rootTarget, SLFunction parent, SLFunction inlinedFunction) {
        rootTarget.accept(new CallTreeNodeVisitor() {

            @Override
            public boolean visit(List<TruffleInlining> decisionStack, Node node) {
                if (node instanceof OptimizedDirectCallNode) {
                    OptimizedDirectCallNode callNode = (OptimizedDirectCallNode) node;
                    if (callNode.getRootNode().getCallTarget() == parent.getCallTarget() && callNode.getCallTarget() == inlinedFunction.getCallTarget()) {
                        TruffleInliningDecision decision = (TruffleInliningDecision) decisionStack.get(decisionStack.size() - 1);
                        if (decision.isInline()) {
                            trace.allFalse = false;
                        } else {
                            trace.allTrue = false;
                        }
                    }
                }
                return true;
            }

        }, inliningDecisions.get(rootTarget));
    }

    private static final class InliningTrace {
        boolean allFalse = true;
        boolean allTrue = true;
    }
}
