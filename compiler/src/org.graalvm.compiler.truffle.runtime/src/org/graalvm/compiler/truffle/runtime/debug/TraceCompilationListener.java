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

import java.util.LinkedHashMap;
import java.util.Map;

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
    private long startTime = System.nanoTime();

    private TraceCompilationListener(GraalTruffleRuntime runtime) {
        super(runtime);
    }

    public static void install(GraalTruffleRuntime runtime) {
        runtime.addListener(new TraceCompilationListener(runtime));
    }

    private static Map<String, Object> defaultProperties(OptimizedCallTarget target) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.putAll(target.getDebugProperties());
        properties.put("Src", formatSourceSection(target.getRootNode().getSourceSection()));
        return properties;
    }

    @Override
    public void onCompilationQueued(OptimizedCallTarget target, int tier) {
        if (target.engine.traceCompilationDetails) {
            runtime.logEvent(target, 0, "opt queued", queueProperties(target, tier));
        }
    }

    @Override
    public void onCompilationDequeued(OptimizedCallTarget target, Object source, CharSequence reason, int tier) {
        if (target.engine.traceCompilationDetails) {
            Map<String, Object> properties = queueProperties(target, tier);
            properties.put("Reason", reason);
            runtime.logEvent(target, 0, "opt unqueued", properties);
        }
    }

    private Map<String, Object> queueProperties(OptimizedCallTarget target, int tier) {
        Map<String, Object> properties = new LinkedHashMap<>();
        GraalTruffleRuntimeListener.addASTSizeProperty(target, properties);
        properties.put("Tier", Integer.toString(tier)); // to avoid padding
        int callThreshold = tier == 1 ? target.engine.callThresholdInInterpreter : target.engine.callThresholdInFirstTier;
        int scale = runtime.compilationThresholdScale();
        properties.put("Calls/Thres", String.format("%7d/%5d", target.getCallCount(), FixedPointMath.multiply(scale, callThreshold)));
        int callAndLoopThreshold = tier == 1 ? target.engine.callAndLoopThresholdInInterpreter : target.engine.callAndLoopThresholdInFirstTier;
        properties.put("CallsAndLoop/Thres", String.format("%7d/%5d", target.getCallAndLoopCount(), FixedPointMath.multiply(scale, callAndLoopThreshold)));
        properties.put("Src", formatSourceSection(target.getRootNode().getSourceSection()));
        properties.put("QueueSize", runtime.getCompilationQueueSize());
        properties.put("Time", System.nanoTime() - startTime);
        properties.put("Scale", scale);
        return properties;
    }

    @Override
    public void onCompilationFailed(OptimizedCallTarget target, String reason, boolean bailout, boolean permanentBailout, int tier) {
        if (target.engine.traceCompilation || target.engine.traceCompilationDetails) {
            if (!isPermanentFailure(bailout, permanentBailout)) {
                onCompilationDequeued(target, null, "Non permanent bailout: " + reason, tier);
            } else {
                Map<String, Object> properties = compilationEndProperties(target, null, null, null, tier);
                properties.put("Reason", reason);
                runtime.logEvent(target, 0, "opt failed", properties);
            }
            currentCompilation.set(null);
        }
    }

    @Override
    public void onCompilationStarted(OptimizedCallTarget target, int tier) {
        if (target.engine.traceCompilationDetails) {
            runtime.logEvent(target, 0, "opt start", queueProperties(target, tier));
        }

        if (target.engine.traceCompilation || target.engine.traceCompilationDetails) {
            currentCompilation.set(new Times());
        }
    }

    @Override
    public void onCompilationDeoptimized(OptimizedCallTarget target, Frame frame) {
        if (target.engine.traceCompilation || target.engine.traceCompilationDetails) {
            Map<String, Object> properties = new LinkedHashMap<>();
            GraalTruffleRuntimeListener.addASTSizeProperty(target, properties);
            properties.put("Src", formatSourceSection(target.getRootNode().getSourceSection()));
            runtime.logEvent(target, 0, "opt deopt", properties);
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

        Map<String, Object> properties = compilationEndProperties(target, inliningDecision, graph, result, tier);

        runtime.logEvent(target, 0, "opt done", properties);

        currentCompilation.set(null);
    }

    private Map<String, Object> compilationEndProperties(OptimizedCallTarget target, TruffleInlining inliningDecision, GraphInfo graph, CompilationResultInfo result, int tier) {
        long timeCompilationFinished = System.nanoTime();
        int nodeCountLowered = graph == null ? 0 : graph.getNodeCount();
        Times compilation = currentCompilation.get();

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
        Map<String, Object> properties = new LinkedHashMap<>();
        GraalTruffleRuntimeListener.addASTSizeProperty(target, properties);
        properties.put("Tier", Integer.toString(tier)); // to avoid padding
        properties.put("Time", String.format("%4.0f(%4.0f+%-4.0f)ms", //
                        (timeCompilationFinished - compilation.timeCompilationStarted) / 1e6, //
                        (compilation.timePartialEvaluationFinished - compilation.timeCompilationStarted) / 1e6, //
                        (timeCompilationFinished - compilation.timePartialEvaluationFinished) / 1e6));
        properties.put("Inlined", String.format("%3dY %3dN", inlinedCalls, dispatchedCalls));
        properties.put("IR", String.format("%5d/%5d", compilation.nodeCountPartialEval, nodeCountLowered));
        properties.put("CodeSize", result == null ? 0 : result.getTargetCodeSize());
        if (target.getCodeAddress() != 0) {
            properties.put("Addr", "0x" + Long.toHexString(target.getCodeAddress()));
        } else {
            properties.put("Addr", "N/A");
        }
        properties.put("Src", formatSourceSection(target.getRootNode().getSourceSection()));
        return properties;
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
            Map<String, Object> properties = defaultProperties(target);
            properties.put("Reason", reason);
            runtime.logEvent(target, 0, "opt inv.", properties);
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
