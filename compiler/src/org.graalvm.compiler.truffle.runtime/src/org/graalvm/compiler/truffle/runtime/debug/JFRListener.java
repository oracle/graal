/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Iterator;

import org.graalvm.compiler.truffle.common.TruffleCompilerListener.CompilationResultInfo;
import org.graalvm.compiler.truffle.common.TruffleCompilerListener.GraphInfo;
import org.graalvm.compiler.truffle.runtime.AbstractGraalTruffleRuntimeListener;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.TruffleInlining;
import org.graalvm.compiler.truffle.runtime.serviceprovider.TruffleRuntimeServices;
import org.graalvm.compiler.truffle.jfr.CompilationEvent;
import org.graalvm.compiler.truffle.jfr.EventFactory;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.Node;
import org.graalvm.compiler.truffle.jfr.DeoptimizationEvent;
import org.graalvm.compiler.truffle.jfr.InvalidationEvent;
import org.graalvm.compiler.truffle.runtime.OptimizedDirectCallNode;
import org.graalvm.nativeimage.ImageInfo;

/**
 * Traces Truffle Compilations using Java Flight Recorder events.
 */
public final class JFRListener extends AbstractGraalTruffleRuntimeListener {

    private static final EventFactory factory;
    static {
        if (ImageInfo.inImageCode()) {
            // Injected by Feature
            factory = null;
        } else {
            Iterator<EventFactory.Provider> it = TruffleRuntimeServices.load(EventFactory.Provider.class).iterator();
            EventFactory.Provider provider = it.hasNext() ? it.next() : null;
            factory = provider == null ? null : provider.getEventFactory();
        }
    }

    private final ThreadLocal<CompilationData> currentCompilation = new ThreadLocal<>();

    private JFRListener(GraalTruffleRuntime runtime) {
        super(runtime);
    }

    public static void install(GraalTruffleRuntime runtime) {
        if (factory != null) {
            runtime.addListener(new JFRListener(runtime));
        }
    }

    @Override
    public void onCompilationDequeued(OptimizedCallTarget target, Object source, CharSequence reason) {
        CompilationEvent event = getCurrentEvent();
        if (event != null) {
            handleFailedCompilation(event, reason, false);
        }
    }

    @Override
    public void onCompilationStarted(OptimizedCallTarget target) {
        if (factory != null) {
            CompilationEvent event = factory.createCompilationEvent();
            if (event.isEnabled()) {
                event.setSource(target);
                event.compilationStarted();
                currentCompilation.set(new CompilationData(event));
            }
        }
    }

    @Override
    public void onCompilationDeoptimized(OptimizedCallTarget target, Frame frame) {
        if (factory != null) {
            DeoptimizationEvent event = factory.createDeoptimizationEvent();
            if (event.isEnabled()) {
                event.setSource(target);
                event.publish();
            }
        }
    }

    @Override
    public void onCompilationTruffleTierFinished(OptimizedCallTarget target, TruffleInlining inliningDecision, GraphInfo graph) {
        CompilationData data = getCurrentData();
        if (data != null) {
            data.partialEvalNodeCount = graph.getNodeCount();
        }
    }

    @Override
    public void onCompilationFailed(OptimizedCallTarget target, String reason, boolean bailout, boolean permanentBailout) {
        CompilationEvent event = getCurrentEvent();
        if (event != null) {
            handleFailedCompilation(event, reason, isPermanentFailure(bailout, permanentBailout));
        }
    }

    @Override
    public void onCompilationSuccess(OptimizedCallTarget target, TruffleInlining inliningDecision, GraphInfo graph, CompilationResultInfo result) {
        CompilationData data = getCurrentData();
        if (data != null) {
            CompilationEvent event = data.event;
            event.succeeded();
            event.setCompiledCodeSize(result.getTargetCodeSize());
            if (target.getCodeAddress() != 0) {
                event.setCompiledCodeAddress(target.getCodeAddress());
            }

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
            event.setInlinedCalls(inlinedCalls);
            event.setDispatchedCalls(dispatchedCalls);
            event.setGraalNodeCount(graph.getNodeCount());
            event.setPartialEvaluationNodeCount(data.partialEvalNodeCount);
            event.publish();
            currentCompilation.remove();
        }
    }

    @Override
    public void onCompilationInvalidated(OptimizedCallTarget target, Object source, CharSequence reason) {
        if (factory != null) {
            InvalidationEvent event = factory.createInvalidationEvent();
            if (event.isEnabled()) {
                event.setSource(target);
                event.setReason(reason);
                event.publish();
            }
        }
    }

    private CompilationData getCurrentData() {
        return factory == null ? null : currentCompilation.get();
    }

    private CompilationEvent getCurrentEvent() {
        CompilationData data = getCurrentData();
        return data == null ? null : data.event;
    }

    private void handleFailedCompilation(CompilationEvent event, CharSequence message, boolean permanent) {
        event.failed(permanent, message);
        event.publish();
        currentCompilation.remove();
    }

    private static final class CompilationData {
        final CompilationEvent event;
        int partialEvalNodeCount;

        CompilationData(CompilationEvent event) {
            this.event = event;
        }
    }

    /**
     * Determines if a failure is permanent.
     */
    private static boolean isPermanentFailure(boolean bailout, boolean permanentBailout) {
        return !bailout || permanentBailout;
    }
}
