/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.runtime.debug;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.compiler.TruffleCompilerListener.CompilationResultInfo;
import com.oracle.truffle.compiler.TruffleCompilerListener.GraphInfo;
import com.oracle.truffle.runtime.AbstractCompilationTask;
import com.oracle.truffle.runtime.AbstractGraalTruffleRuntimeListener;
import com.oracle.truffle.runtime.CompilationTask;
import com.oracle.truffle.runtime.FixedPointMath;
import com.oracle.truffle.runtime.OptimizedTruffleRuntime;
import com.oracle.truffle.runtime.OptimizedTruffleRuntimeListener;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import com.oracle.truffle.runtime.OptimizedDirectCallNode;

import java.util.function.Supplier;

/**
 * Traces AST-level compilation events with a detailed log message sent to the Truffle log stream
 * for each event.
 */
public final class TraceCompilationListener extends AbstractGraalTruffleRuntimeListener {

    private final ThreadLocal<Times> currentCompilation = new ThreadLocal<>();

    private TraceCompilationListener(OptimizedTruffleRuntime runtime) {
        super(runtime);
    }

    public static void install(OptimizedTruffleRuntime runtime) {
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
    public void onCompilationFailed(OptimizedCallTarget target, String reason, boolean bailout, boolean permanentBailout, int tier, Supplier<String> serializedException) {
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
    public void onCompilationStarted(OptimizedCallTarget target, AbstractCompilationTask task) {
        if (target.engine.traceCompilationDetails) {
            double weight;
            long time;
            double rate;
            int queueChange;
            if (task instanceof CompilationTask t) {
                weight = t.weight();
                time = t.time();
                rate = t.rate();
                queueChange = t.queueChange();
            } else {
                weight = 0.0d;
                time = 0;
                rate = Double.NaN;
                queueChange = 0;
            }
            log(target, String.format(START_FORMAT,
                            target.id,
                            safeTargetName(target),
                            task.tier(),
                            (int) weight,
                            rate,
                            runtime.getCompilationQueueSize(),
                            queueChange >= 0 ? '+' : '-',
                            Math.abs(queueChange),
                            FixedPointMath.toDouble(runtime.compilationThresholdScale()),
                            time / 1000,
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
    public void onCompilationTruffleTierFinished(OptimizedCallTarget target, AbstractCompilationTask task, GraphInfo graph) {
        if (target.engine.traceCompilation || target.engine.traceCompilationDetails) {
            final Times current = currentCompilation.get();
            current.timePartialEvaluationFinished = System.nanoTime();
            current.nodeCountPartialEval = graph.getNodeCount();
        }
    }

    @Override
    public void onCompilationSuccess(OptimizedCallTarget target, AbstractCompilationTask task, GraphInfo graph, CompilationResultInfo result) {
        if (!target.engine.traceCompilation && !target.engine.traceCompilationDetails) {
            return;
        }
        Times compilation = currentCompilation.get();
        int[] inlinedAndDispatched = inlinedAndDispatched(target, task);
        log(target, String.format(DONE_FORMAT,
                        target.id,
                        safeTargetName(target),
                        task.tier(),
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

    private int[] inlinedAndDispatched(OptimizedCallTarget target, AbstractCompilationTask task) {
        try {
            int calls = 0;
            int inlinedCalls;
            if (task == null) {
                CallCountVisitor visitor = new CallCountVisitor();
                target.accept(visitor);
                calls = visitor.calls;
                inlinedCalls = 0;
            } else {
                calls = task.countCalls();
                inlinedCalls = task.countInlinedCalls();
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
     * @see OptimizedTruffleRuntimeListener#onCompilationFailed(OptimizedCallTarget, String,
     *      boolean, boolean, int, Supplier)
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
