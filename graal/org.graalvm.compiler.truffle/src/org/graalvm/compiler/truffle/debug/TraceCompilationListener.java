/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.debug;

import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TraceTruffleCompilation;
import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TraceTruffleCompilationDetails;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.truffle.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.OptimizedCallTarget;
import org.graalvm.compiler.truffle.OptimizedDirectCallNode;
import org.graalvm.compiler.truffle.TruffleCompilerOptions;
import org.graalvm.compiler.truffle.TruffleInlining;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

public final class TraceCompilationListener extends AbstractDebugCompilationListener {

    private final Map<OptimizedCallTarget, LocalCompilation> compilationMap = new ConcurrentHashMap<>();

    private TraceCompilationListener() {
    }

    public static void install(GraalTruffleRuntime runtime) {
        if (TruffleCompilerOptions.getValue(TraceTruffleCompilation) || TruffleCompilerOptions.getValue(TraceTruffleCompilationDetails)) {
            runtime.addCompilationListener(new TraceCompilationListener());
        }
    }

    @Override
    public void notifyCompilationQueued(OptimizedCallTarget target) {
        if (TruffleCompilerOptions.getValue(TraceTruffleCompilationDetails)) {
            log(0, "opt queued", target.toString(), target.getDebugProperties(null));
        }
    }

    @Override
    public void notifyCompilationDequeued(OptimizedCallTarget target, Object source, CharSequence reason) {
        if (TruffleCompilerOptions.getValue(TraceTruffleCompilationDetails)) {
            Map<String, Object> properties = new LinkedHashMap<>();
            addSourceInfo(properties, source);
            properties.put("Reason", reason);
            log(0, "opt unqueued", target.toString(), properties);
        }
    }

    @Override
    public void notifyCompilationFailed(OptimizedCallTarget target, StructuredGraph graph, Throwable t, Map<OptimizedCallTarget, Object> compilationMap) {
        super.notifyCompilationFailed(target, graph, t, compilationMap);
        if (!TraceCompilationFailureListener.isPermanentBailout(t)) {
            notifyCompilationDequeued(target, null, "Non permanent bailout: " + t.toString());
        }
        compilationMap.remove(target);

    }

    @Override
    public void notifyCompilationStarted(OptimizedCallTarget target, Map<OptimizedCallTarget, Object> compilationMap) {
        if (TruffleCompilerOptions.getValue(TraceTruffleCompilationDetails)) {
            log(0, "opt start", target.toString(), target.getDebugProperties(null));
        }
        LocalCompilation compilation = new LocalCompilation();
        compilation.timeCompilationStarted = System.nanoTime();

        compilationMap.put(target, compilation);
    }

    @Override
    public void notifyCompilationDeoptimized(OptimizedCallTarget target, Frame frame) {
        log(0, "opt deopt", target.toString(), target.getDebugProperties(null));
    }

    @Override
    public void notifyCompilationTruffleTierFinished(OptimizedCallTarget target, TruffleInlining inliningDecision, StructuredGraph graph, Map<OptimizedCallTarget, Object> compilationMap) {
        super.notifyCompilationTruffleTierFinished(target, inliningDecision, graph, compilationMap);
        LocalCompilation compilation = (LocalCompilation) compilationMap.get(target);
        compilation.timePartialEvaluationFinished = System.nanoTime();
        compilation.nodeCountPartialEval = graph.getNodeCount();
    }

    @Override
    public void notifyCompilationSuccess(OptimizedCallTarget target, TruffleInlining inliningDecision, StructuredGraph graph, CompilationResult result, Map<OptimizedCallTarget, Object> compilationMap) {
        long timeCompilationFinished = System.nanoTime();
        int nodeCountLowered = graph.getNodeCount();
        LocalCompilation compilation = (LocalCompilation) compilationMap.get(target);

        int calls = 0;
        int inlinedCalls;
        if (inliningDecision == null) {

            for (Node node : target.nodeIterable(null)) {
                if (node instanceof OptimizedDirectCallNode) {
                    calls++;
                }
            }

            inlinedCalls = 0;
        } else {
            calls = inliningDecision.countCalls();
            inlinedCalls = inliningDecision.countInlinedCalls();
        }

        int dispatchedCalls = calls - inlinedCalls;
        Map<String, Object> properties = new LinkedHashMap<>();
        addASTSizeProperty(target, inliningDecision, properties);
        properties.put("Time", String.format("%5.0f(%4.0f+%-4.0f)ms", //
                        (timeCompilationFinished - compilation.timeCompilationStarted) / 1e6, //
                        (compilation.timePartialEvaluationFinished - compilation.timeCompilationStarted) / 1e6, //
                        (timeCompilationFinished - compilation.timePartialEvaluationFinished) / 1e6));
        properties.put("DirectCallNodes", String.format("I %4d/D %4d", inlinedCalls, dispatchedCalls));
        properties.put("GraalNodes", String.format("%5d/%5d", compilation.nodeCountPartialEval, nodeCountLowered));
        properties.put("CodeSize", result.getTargetCodeSize());
        properties.put("Source", formatSourceSection(target.getRootNode().getSourceSection()));

        log(0, "opt done", target.toString(), properties);
        super.notifyCompilationSuccess(target, inliningDecision, graph, result, compilationMap);

        compilationMap.remove(target);
    }

    private static String formatSourceSection(SourceSection sourceSection) {
        if (sourceSection == null || sourceSection.getSource() == null) {
            return "n/a";
        }
        return String.format("%s:%d", sourceSection.getSource().getName(), sourceSection.getStartLine());
    }

    @Override
    public void notifyCompilationInvalidated(OptimizedCallTarget target, Object source, CharSequence reason) {
        Map<String, Object> properties = new LinkedHashMap<>();
        addSourceInfo(properties, source);
        properties.put("Reason", reason);
        log(0, "opt invalidated", target.toString(), properties);
    }

    private static void addSourceInfo(Map<String, Object> properties, Object source) {
        if (source != null) {
            properties.put("SourceClass", source.getClass().getSimpleName());
            properties.put("Source", source);
        }
    }

    private static final class LocalCompilation {
        long timeCompilationStarted;
        long timePartialEvaluationFinished;
        long nodeCountPartialEval;
    }

}
