/*
 * Copyright (c) 2014, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime.debug;

import org.graalvm.compiler.truffle.common.TruffleCompilationTask;
import org.graalvm.compiler.truffle.common.TruffleCompilerListener.CompilationResultInfo;
import org.graalvm.compiler.truffle.common.TruffleCompilerListener.GraphInfo;
import org.graalvm.compiler.truffle.runtime.AbstractGraalTruffleRuntimeListener;
import org.graalvm.compiler.truffle.runtime.FixedPointMath;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntimeListener;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.OptimizedDirectCallNode;
import org.graalvm.compiler.truffle.runtime.TruffleInlining;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Traces AST-level compilation events with a detailed log message sent to the Truffle log stream
 * for each event.
 */
public final class TraceCompilationListener extends AbstractGraalTruffleRuntimeListener {

    private final ThreadLocal<Times> currentCompilation = new ThreadLocal<>();

    private TraceCompilationListener(GraalTruffleRuntime runtime) {
        super(runtime);
    }

    public static void install(GraalTruffleRuntime runtime) {
        runtime.addListener(new TraceCompilationListener(runtime));
    }

    public static final String TIER_FORMAT = "Tier %d";
    private static final String QUEUE_FORMAT = "Queue: Size %4d Change %c%-2d Load %5.2f Time %5dus                    ";
    private static final String TARGET_FORMAT = "id=%-5d %-50s ";
    public static final String COUNT_THRESHOLD_FORMAT = "Count/Thres  %9d/%9d";
    // @formatter:off
    private static final String QUEUED_FORMAT   = "opt queued " + TARGET_FORMAT + "|" + TIER_FORMAT + "|" + COUNT_THRESHOLD_FORMAT + "|" + QUEUE_FORMAT + "|Timestamp %d|Src %s";
    private static final String UNQUEUED_FORMAT = "opt unque. " + TARGET_FORMAT + "|" + TIER_FORMAT + "|" + COUNT_THRESHOLD_FORMAT + "|" + QUEUE_FORMAT + "|Timestamp %d|Src %s|Reason %s";
    private static final String START_FORMAT    = "opt start  " + TARGET_FORMAT + "|" + TIER_FORMAT + "|Priority %9d|Rate %.6f|"         + QUEUE_FORMAT + "|Timestamp %d|Src %s";
    private static final String DONE_FORMAT     = "opt done   " + TARGET_FORMAT + "|" + TIER_FORMAT + "|Time %18s|AST %4d|Inlined %3dY %3dN|IR %6d/%6d|CodeSize %7d|Addr %7s|Timestamp %d|Src %s";
    private static final String FAILED_FORMAT   = "opt failed " + TARGET_FORMAT + "|" + TIER_FORMAT + "|Time %18s|Reason: %s|Timestamp %d|Src %s";
    private static final String INV_FORMAT      = "opt inval. " + TARGET_FORMAT + "                                                                                                                |Timestamp %d|Src %s|Reason %s";
    private static final String DEOPT_FORMAT    = "opt deopt  " + TARGET_FORMAT + "|                                                                                                               |Timestamp %d|Src %s";
    // @formatter:on

    @Override
    public void onCompilationQueued(OptimizedCallTarget target, int tier) {
        if (target.engine.traceCompilationDetails) {
            int callAndLoopThreshold = (target.engine.multiTier && tier == 2) ? target.engine.callAndLoopThresholdInFirstTier : target.engine.callAndLoopThresholdInInterpreter;
            int scale = runtime.compilationThresholdScale();
            log(target, String.format(QUEUED_FORMAT,
                            target.id,
                            safeTargetName(target),
                            tier,
                            target.getCallAndLoopCount(),
                            OptimizedCallTarget.scaledThreshold(callAndLoopThreshold),
                            runtime.getCompilationQueueSize(),
                            '+',
                            1,
                            FixedPointMath.toDouble(scale),
                            0,
                            System.nanoTime(),
                            formatSourceSection(safeSourceSection(target))));
        }
    }

    @Override
    public void onCompilationDequeued(OptimizedCallTarget target, Object source, CharSequence reason, int tier) {
        if (target.engine.traceCompilationDetails) {
            int callAndLoopThreshold = tier == 1 ? target.engine.callAndLoopThresholdInInterpreter : target.engine.callAndLoopThresholdInFirstTier;
            int scale = runtime.compilationThresholdScale();
            log(target, String.format(UNQUEUED_FORMAT,
                            target.id,
                            safeTargetName(target),
                            tier,
                            target.getCallAndLoopCount(),
                            FixedPointMath.multiply(scale, callAndLoopThreshold),
                            runtime.getCompilationQueueSize(),
                            ' ',
                            0,
                            FixedPointMath.toDouble(scale),
                            0,
                            System.nanoTime(),
                            formatSourceSection(safeSourceSection(target)),
                            reason));
        }
    }

    @Override
    public void onCompilationFailed(OptimizedCallTarget target, String reason, boolean bailout, boolean permanentBailout, int tier) {
        if (target.engine.traceCompilation || target.engine.traceCompilationDetails) {
            if (!isPermanentFailure(bailout, permanentBailout)) {
                onCompilationDequeued(target, null, "Non permanent bailout: " + reason, tier);
            } else {
                log(target, String.format(FAILED_FORMAT,
                                target.id,
                                safeTargetName(target),
                                tier,
                                compilationTime(),
                                reason,
                                System.nanoTime(),
                                formatSourceSection(safeSourceSection(target))));
            }
            currentCompilation.remove();
        }
    }

    @Override
    public void onCompilationStarted(OptimizedCallTarget target, TruffleCompilationTask task) {
        if (target.engine.traceCompilationDetails) {
            log(target, String.format(START_FORMAT,
                            target.id,
                            safeTargetName(target),
                            task.tier(),
                            (int) task.weight(),
                            task.rate(),
                            runtime.getCompilationQueueSize(),
                            task.queueChange() >= 0 ? '+' : '-',
                            Math.abs(task.queueChange()),
                            FixedPointMath.toDouble(runtime.compilationThresholdScale()),
                            task.time() / 1000,
                            System.nanoTime(),
                            formatSourceSection(safeSourceSection(target))));
        }

        if (target.engine.traceCompilation || target.engine.traceCompilationDetails) {
            currentCompilation.set(new Times());
        }
    }

    private void log(OptimizedCallTarget target, String message) {
        runtime.log(target, message);
    }

    @Override
    public void onCompilationDeoptimized(OptimizedCallTarget target, Frame frame) {
        if (target.engine.traceCompilation || target.engine.traceCompilationDetails) {
            log(target, String.format(DEOPT_FORMAT,
                            target.id,
                            safeTargetName(target),
                            System.nanoTime(),
                            formatSourceSection(safeSourceSection(target))));
        }
    }

    @Override
    public void onCompilationTruffleTierFinished(OptimizedCallTarget target, TruffleInlining inliningDecision, GraphInfo graph) {
        if (target.engine.traceCompilation || target.engine.traceCompilationDetails) {
            final Times current = currentCompilation.get();
            current.timePartialEvaluationFinished = System.nanoTime();
            current.nodeCountPartialEval = graph.getNodeCount();
        }
    }

    @Override
    public void onCompilationSuccess(OptimizedCallTarget target, TruffleInlining inliningDecision, GraphInfo graph, CompilationResultInfo result, int tier) {
        if (!target.engine.traceCompilation && !target.engine.traceCompilationDetails) {
            return;
        }
        Times compilation = currentCompilation.get();
        int[] inlinedAndDispatched = inlinedAndDispatched(target, inliningDecision);
        log(target, String.format(DONE_FORMAT,
                        target.id,
                        safeTargetName(target),
                        tier,
                        compilationTime(),
                        target.getNonTrivialNodeCount(),
                        inlinedAndDispatched[0],
                        inlinedAndDispatched[1],
                        compilation.nodeCountPartialEval,
                        graph == null ? 0 : graph.getNodeCount(),
                        result == null ? 0 : result.getTargetCodeSize(),
                        "0x" + Long.toHexString(target.getCodeAddress()),
                        System.nanoTime(),
                        formatSourceSection(safeSourceSection(target))));
        currentCompilation.remove();
    }

    private SourceSection safeSourceSection(OptimizedCallTarget target) {
        try {
            return target.getRootNode().getSourceSection();
        } catch (Throwable throwable) {
            log(target, "Failed to call RootNode.getSourceSection(): " + throwable);
            return null;
        }
    }

    private String safeTargetName(OptimizedCallTarget target) {
        try {
            return target.getName();
        } catch (Throwable throwable) {
            log(target, "Failed to call RootNode.getName(): " + throwable);
            return null;
        }
    }

    private int[] inlinedAndDispatched(OptimizedCallTarget target, TruffleInlining inliningDecision) {
        try {
            int calls = 0;
            int inlinedCalls;
            if (inliningDecision == null) {
                CallCountVisitor visitor = new CallCountVisitor();
                target.accept(visitor);
                calls = visitor.calls;
                inlinedCalls = 0;
            } else {
                calls = inliningDecision.countCalls();
                inlinedCalls = inliningDecision.countInlinedCalls();
            }
            int dispatchedCalls = calls - inlinedCalls;
            int[] inlinedAndDispatched = new int[2];
            inlinedAndDispatched[0] = inlinedCalls;
            inlinedAndDispatched[1] = dispatchedCalls;
            return inlinedAndDispatched;
        } catch (Throwable throwable) {
            log(target, "Failed to inlined and dispatched counts: " + throwable);
            return null;
        }
    }

    private String compilationTime() {
        long timeCompilationFinished = System.nanoTime();
        Times compilation = currentCompilation.get();
        return String.format("%4.0f(%4.0f+%-4.0f)ms", //
                        (timeCompilationFinished - compilation.timeCompilationStarted) / 1e6, //
                        (compilation.timePartialEvaluationFinished - compilation.timeCompilationStarted) / 1e6, //
                        (timeCompilationFinished - compilation.timePartialEvaluationFinished) / 1e6);
    }

    private static String formatSourceSection(SourceSection sourceSection) {
        if (sourceSection == null || sourceSection.getSource() == null) {
            return "n/a";
        }
        return String.format("%s:%d", sourceSection.getSource().getName(), sourceSection.getStartLine());
    }

    @Override
    public void onCompilationInvalidated(OptimizedCallTarget target, Object source, CharSequence reason) {
        if (target.engine.traceCompilation || target.engine.traceCompilationDetails) {
            log(target, String.format(INV_FORMAT,
                            target.id,
                            safeTargetName(target),
                            System.nanoTime(),
                            formatSourceSection(safeSourceSection(target)),
                            reason));
        }
    }

    /**
     * Determines if a failure is permanent.
     *
     * @see GraalTruffleRuntimeListener#onCompilationFailed(OptimizedCallTarget, String, boolean,
     *      boolean, int)
     */
    private static boolean isPermanentFailure(boolean bailout, boolean permanentBailout) {
        return !bailout || permanentBailout;
    }

    private static final class Times {
        final long timeCompilationStarted = System.nanoTime();
        long timePartialEvaluationFinished;
        long nodeCountPartialEval;
    }

    static final class CallCountVisitor implements NodeVisitor {

        int calls = 0;

        @Override
        public boolean visit(Node node) {
            if (node instanceof OptimizedDirectCallNode) {
                calls++;
            }
            return true;
        }
    }
}
