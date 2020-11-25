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
import java.util.concurrent.atomic.AtomicLong;

import org.graalvm.compiler.truffle.common.TruffleCompilerListener.CompilationResultInfo;
import org.graalvm.compiler.truffle.common.TruffleCompilerListener.GraphInfo;
import org.graalvm.compiler.truffle.jfr.CompilationEvent;
import org.graalvm.compiler.truffle.jfr.CompilationStatisticsEvent;
import org.graalvm.compiler.truffle.jfr.DeoptimizationEvent;
import org.graalvm.compiler.truffle.jfr.EventFactory;
import org.graalvm.compiler.truffle.jfr.InvalidationEvent;
import org.graalvm.compiler.truffle.runtime.AbstractGraalTruffleRuntimeListener;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.TruffleInlining;
import org.graalvm.compiler.truffle.runtime.serviceprovider.TruffleRuntimeServices;
import org.graalvm.nativeimage.ImageInfo;

import com.oracle.truffle.api.frame.Frame;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.UnresolvedJavaType;

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

    // Support for JFRListener#isInstrumented
    private static final Set<InstrumentedMethodPattern> instrumentedMethodPatterns = createInstrumentedPatterns();
    private static final AtomicReference<InstrumentedFilterState> instrumentedFilterState = new AtomicReference<>(InstrumentedFilterState.NEW);
    private static volatile Class<? extends Annotation> requiredAnnotation;
    private static volatile ResolvedJavaType resolvedJfrEventClass;

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

    public static boolean isInstrumented(ResolvedJavaMethod method) {
        // Initialization must be deferred into the image executtion time
        InstrumentedFilterState currentState = instrumentedFilterState.get();
        if (currentState == InstrumentedFilterState.INACTIVE) {
            return false;
        }
        return isInstrumentedImpl(method, currentState);
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
                TraceCompilationListener.CallCountVisitor visitor = new TraceCompilationListener.CallCountVisitor();
                target.accept(visitor);
                calls = visitor.calls;
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

    // Support for JFRListener#isInstrumented
    private static boolean isInstrumentedImpl(ResolvedJavaMethod method, InstrumentedFilterState state) {

        InstrumentedFilterState currentState = state;
        if (currentState == InstrumentedFilterState.NEW) {
            currentState = initializeInstrumentedFilter();
        }

        // If JFR is not active or we are in the image build time return false
        if (currentState == InstrumentedFilterState.NEW || currentState == InstrumentedFilterState.INACTIVE) {
            return false;
        }

        // Fast check, the JFR instrumented methods are marked as synthetic.
        if (!method.isSynthetic() || method.isBridge() || method.isStatic()) {
            return false;
        }

        ResolvedJavaType methodOwner = method.getDeclaringClass();
        if (getAnnotation(requiredAnnotation, methodOwner) == null) {
            return false;
        }

        if (!instrumentedMethodPatterns.contains(new InstrumentedMethodPattern(method))) {
            return false;
        }
        ResolvedJavaType patternOwner = getJFREventClass(methodOwner);
        return patternOwner != null && patternOwner.isAssignableFrom(methodOwner);
    }

    private static InstrumentedFilterState initializeInstrumentedFilter() {
        // Do not initialize during image building.
        if (!ImageInfo.inImageBuildtimeCode()) {
            if (factory != null) {
                requiredAnnotation = factory.getRequiredAnnotation();
                factory.addInitializationListener(() -> {
                    instrumentedFilterState.set(InstrumentedFilterState.ACTIVE);
                });
                InstrumentedFilterState currentState = factory.isInitialized() ? InstrumentedFilterState.ACTIVE : InstrumentedFilterState.INACTIVE;
                instrumentedFilterState.compareAndSet(InstrumentedFilterState.NEW, currentState);
            } else {
                instrumentedFilterState.set(InstrumentedFilterState.INACTIVE);
            }
        }
        return instrumentedFilterState.get();
    }

    private static ResolvedJavaType getJFREventClass(ResolvedJavaType accessingClass) {
        if (resolvedJfrEventClass == null) {
            try {
                resolvedJfrEventClass = UnresolvedJavaType.create("Ljdk/jfr/Event;").resolve(accessingClass);
            } catch (LinkageError e) {
                // May happen when declaringClass is not accessible from accessingClass
            }
        }
        return resolvedJfrEventClass;
    }

    private static <T extends Annotation> T getAnnotation(Class<T> annotationClass, AnnotatedElement element) {
        try {
            return annotationClass.cast(element.getAnnotation(annotationClass));
        } catch (NoClassDefFoundError e) {
            return null;
        }
    }

    private static Set<InstrumentedMethodPattern> createInstrumentedPatterns() {
        Set<InstrumentedMethodPattern> patterns = new HashSet<>();
        patterns.add(new InstrumentedMethodPattern("begin", "()V"));
        patterns.add(new InstrumentedMethodPattern("commit", "()V"));
        patterns.add(new InstrumentedMethodPattern("end", "()V"));
        patterns.add(new InstrumentedMethodPattern("isEnabled", "()Z"));
        patterns.add(new InstrumentedMethodPattern("set", "(ILjava/lang/Object;)V"));
        patterns.add(new InstrumentedMethodPattern("shouldCommit", "()Z"));
        return patterns;
    }

    private enum InstrumentedFilterState {
        NEW,
        ACTIVE,
        INACTIVE
    }

    private static final class InstrumentedMethodPattern {

        private final String name;
        private final String signature;

        private InstrumentedMethodPattern(ResolvedJavaMethod method) {
            this(method.getName(), method.getSignature().toMethodDescriptor());
        }

        private InstrumentedMethodPattern(String name, String signature) {
            this.name = name;
            this.signature = signature;
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            final InstrumentedMethodPattern otherPattern = (InstrumentedMethodPattern) other;
            return name.equals(otherPattern.name) && signature.equals(otherPattern.signature);
        }
    }
}
