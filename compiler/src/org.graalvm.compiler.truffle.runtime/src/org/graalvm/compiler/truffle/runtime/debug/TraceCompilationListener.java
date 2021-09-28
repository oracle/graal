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

    public static final String TIER_FORMAT = " Tier %d ";
    private static final String QUEUE_FORMAT = "Queue: Size %4d Change %c%-2d Scale %5.2f Elapsed %4dus ";
    private static final String TARGET_FORMAT = "id=%-5d %-50s ";
    // @formatter:off
    private static final String QUEUED_FORMAT   = "opt queued   " + TARGET_FORMAT + "|" + TIER_FORMAT + "| Count/Thres  %8d/%8d | "    + QUEUE_FORMAT + "| Src %s | Time %d ";
    private static final String UNQUEUED_FORMAT = "opt unqueued " + TARGET_FORMAT + "|" + TIER_FORMAT + "| Count/Thres  %8d/%8d | "    + QUEUE_FORMAT + "| Reason: %s | Src %s | Time %d ";
    private static final String START_FORMAT    = "opt start    " + TARGET_FORMAT + "|" + TIER_FORMAT + "| Weight %8d | Rate %.5f | " + QUEUE_FORMAT + "| Src %s | Time %d ";
    private static final String DONE_FORMAT     = "opt done     " + TARGET_FORMAT + "|" + TIER_FORMAT + "| Elapsed %22s | Src %s | Time %d ";
    private static final String FAILED_FORMAT   = "opt failed   " + TARGET_FORMAT + "|" + TIER_FORMAT + "| Elapsed %22s | Reason: %s | Src %s | Time %d ";
    private static final String INV_FORMAT      = "opt inv      " + TARGET_FORMAT + "| Reason %s | Src %s | Time %d ";
    private static final String DEOPT_FORMAT    = "opt deopt    " + TARGET_FORMAT + "| Src %s | Time %d ";
    // @formatter:on

    @Override
    public void onCompilationQueued(OptimizedCallTarget target, int tier) {
        if (target.engine.traceCompilationDetails) {
            int callAndLoopThreshold = tier == 1 ? target.engine.callAndLoopThresholdInInterpreter : target.engine.callAndLoopThresholdInFirstTier;
            int scale = runtime.compilationThresholdScale();
            log(target, String.format(QUEUED_FORMAT,
                            target.id,
                            target.getName(),
                            tier,
                            target.getCallAndLoopCount(),
                            FixedPointMath.multiply(scale, callAndLoopThreshold),
                            runtime.getCompilationQueueSize(),
                            '+',
                            1,
                            FixedPointMath.toDouble(scale),
                            0,
                            formatSourceSection(target.getRootNode().getSourceSection()),
                            System.nanoTime()));
        }
    }

    @Override
    public void onCompilationDequeued(OptimizedCallTarget target, Object source, CharSequence reason, int tier) {
        if (target.engine.traceCompilationDetails) {
            int callAndLoopThreshold = tier == 1 ? target.engine.callAndLoopThresholdInInterpreter : target.engine.callAndLoopThresholdInFirstTier;
            int scale = runtime.compilationThresholdScale();
            log(target, String.format(UNQUEUED_FORMAT,
                            target.id,
                            target.getName(),
                            tier,
                            target.getCallAndLoopCount(),
                            FixedPointMath.multiply(scale, callAndLoopThreshold),
                            runtime.getCompilationQueueSize(),
                            ' ',
                            0,
                            FixedPointMath.toDouble(scale),
                            0,
                            reason,
                            formatSourceSection(target.getRootNode().getSourceSection()),
                            System.nanoTime()));
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
                                target.getName(),
                                tier,
                                compilationTime(),
                                reason,
                                formatSourceSection(target.getRootNode().getSourceSection()),
                                System.nanoTime()));
            }
            currentCompilation.set(null);
        }
    }

    @Override
    public void onCompilationStarted(OptimizedCallTarget target, int tier, long time, double weight, double rate, int queueChange) {
        if (target.engine.traceCompilationDetails) {
            log(target, String.format(START_FORMAT,
                            target.id,
                            target.getName(),
                            tier,
                            (int) weight,
                            rate,
                            runtime.getCompilationQueueSize(),
                            queueChange >= 0 ? '+' : '-',
                            Math.abs(queueChange),
                            FixedPointMath.toDouble(runtime.compilationThresholdScale()),
                            time / 1000,
                            formatSourceSection(target.getRootNode().getSourceSection()),
                            System.nanoTime()));
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
                            target.getName(),
                            formatSourceSection(target.getRootNode().getSourceSection()),
                            System.nanoTime()));
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
        log(target, String.format(DONE_FORMAT,
                        target.id,
                        target.getName(),
                        tier,
                        compilationTime(),
                        formatSourceSection(target.getRootNode().getSourceSection()),
                        System.nanoTime()));
        currentCompilation.set(null);
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
                            target.getName(),
                            reason,
                            formatSourceSection(target.getRootNode().getSourceSection()),
                            System.nanoTime()));
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
