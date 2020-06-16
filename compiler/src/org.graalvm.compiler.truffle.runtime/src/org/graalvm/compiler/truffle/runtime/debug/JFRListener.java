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
import java.util.concurrent.atomic.AtomicLong;
import org.graalvm.compiler.truffle.jfr.CompilationStatisticsEvent;
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
            if (provider == null) {
                factory = null;
            } else {
                CompilerDebugAccessor.jdkServicesAccessor().exportTo(provider.getClass());
                factory = provider == null ? null : provider.getEventFactory();
            }
        }
    }

    private final ThreadLocal<CompilationData> currentCompilation = new ThreadLocal<>();
    private final Statistics statistics;

    private JFRListener(GraalTruffleRuntime runtime) {
        super(runtime);
        statistics = new Statistics();
        factory.addPeriodicEvent(CompilationStatisticsEvent.class, statistics);
    }

    public static void install(GraalTruffleRuntime runtime) {
        if (factory != null) {
            runtime.addListener(new JFRListener(runtime));
        }
    }

    @Override
    public void onCompilationStarted(OptimizedCallTarget target) {
        CompilationEvent event = null;
        if (factory != null) {
            event = factory.createCompilationEvent();
            if (event.isEnabled()) {
                event.setRootFunction(target);
                event.compilationStarted();
            } else {
                event = null;
            }
        }
        currentCompilation.set(new CompilationData(event));
    }

    @Override
    public void onCompilationDeoptimized(OptimizedCallTarget target, Frame frame) {
        if (factory != null) {
            DeoptimizationEvent event = factory.createDeoptimizationEvent();
            if (event.isEnabled()) {
                event.setRootFunction(target);
                event.publish();
            }
        }
    }

    @Override
    public void onCompilationTruffleTierFinished(OptimizedCallTarget target, TruffleInlining inliningDecision, GraphInfo graph) {
        CompilationData data = getCurrentData();
        if (data.event != null) {
            data.partialEvalNodeCount = graph.getNodeCount();
        }
    }

    @Override
    public void onCompilationFailed(OptimizedCallTarget target, String reason, boolean bailout, boolean permanentBailout) {
        CompilationData data = getCurrentData();
        statistics.finishCompilation(data.finish(), bailout, 0);
        if (data.event != null) {
            data.event.failed(isPermanentFailure(bailout, permanentBailout), reason);
            data.event.publish();
        }
        currentCompilation.remove();
    }

    @Override
    public void onCompilationSuccess(OptimizedCallTarget target, TruffleInlining inliningDecision, GraphInfo graph, CompilationResultInfo result) {
        CompilationData data = getCurrentData();
        int compiledCodeSize = result.getTargetCodeSize();
        statistics.finishCompilation(data.finish(), false, compiledCodeSize);
        if (data.event != null) {
            CompilationEvent event = data.event;
            event.succeeded();
            event.setCompiledCodeSize(compiledCodeSize);
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
        statistics.invalidations.incrementAndGet();
        if (factory != null) {
            InvalidationEvent event = factory.createInvalidationEvent();
            if (event.isEnabled()) {
                event.setRootFunction(target);
                event.setReason(reason);
                event.publish();
            }
        }
    }

    private CompilationData getCurrentData() {
        return currentCompilation.get();
    }

    private static final class CompilationData {
        final CompilationEvent event;
        final long startTime;
        int partialEvalNodeCount;

        CompilationData(CompilationEvent event) {
            this.event = event;
            this.startTime = System.nanoTime();
        }

        int finish() {
            return (int) (System.nanoTime() - startTime) / 1_000_000;
        }
    }

    private static final class Statistics implements Runnable {

        private long compiledMethods;
        private long bailouts;
        private long compiledCodeSize;
        private long totalTime;
        private int peakTime;
        final AtomicLong invalidations = new AtomicLong();

        Statistics() {
        }

        synchronized void finishCompilation(int time, boolean bailout, int codeSize) {
            compiledMethods++;
            if (bailout) {
                bailouts++;
            }
            compiledCodeSize += codeSize;
            totalTime += time;
            peakTime = Math.max(peakTime, time);
        }

        @Override
        public void run() {
            CompilationStatisticsEvent event = factory.createCompilationStatisticsEvent();
            if (event.isEnabled()) {
                synchronized (this) {
                    event.setCompiledMethods(compiledMethods);
                    event.setBailouts(bailouts);
                    event.setInvalidations(invalidations.get());
                    event.setCompiledCodeSize(compiledCodeSize);
                    event.setTotalTime(totalTime);
                    event.setPeakTime(peakTime);
                    event.publish();
                }
            }
        }
    }

    /**
     * Determines if a failure is permanent.
     */
    private static boolean isPermanentFailure(boolean bailout, boolean permanentBailout) {
        return !bailout || permanentBailout;
    }
}
