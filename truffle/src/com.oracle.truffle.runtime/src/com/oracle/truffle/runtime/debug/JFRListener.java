/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.compiler.TruffleCompilerListener.CompilationResultInfo;
import com.oracle.truffle.compiler.TruffleCompilerListener.GraphInfo;
import com.oracle.truffle.runtime.AbstractCompilationTask;
import com.oracle.truffle.runtime.AbstractGraalTruffleRuntimeListener;
import com.oracle.truffle.runtime.OptimizedTruffleRuntime;
import com.oracle.truffle.runtime.ModuleUtil;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import com.oracle.truffle.runtime.jfr.CompilationEvent;
import com.oracle.truffle.runtime.jfr.CompilationStatisticsEvent;
import com.oracle.truffle.runtime.jfr.DeoptimizationEvent;
import com.oracle.truffle.runtime.jfr.EventFactory;
import com.oracle.truffle.runtime.jfr.InvalidationEvent;
import com.oracle.truffle.runtime.serviceprovider.TruffleRuntimeServices;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.UnresolvedJavaType;

/**
 * Traces Truffle Compilations using Java Flight Recorder events.
 */
public final class JFRListener extends AbstractGraalTruffleRuntimeListener {

    private static final EventFactory factory = lookupFactory();

    // Support for JFRListener#isInstrumented
    private static final Set<InstrumentedMethodPattern> instrumentedMethodPatterns = createInstrumentedPatterns();
    private static final AtomicReference<InstrumentedFilterState> instrumentedFilterState = new AtomicReference<>(InstrumentedFilterState.NEW);
    private static volatile Class<? extends Annotation> requiredAnnotation;
    private static volatile ResolvedJavaType resolvedJfrEventClass;

    private final ThreadLocal<CompilationData> currentCompilation = new ThreadLocal<>();
    private final Statistics statistics;

    private JFRListener(OptimizedTruffleRuntime runtime) {
        super(runtime);
        statistics = new Statistics();
        factory.addPeriodicEvent(CompilationStatisticsEvent.class, statistics);
    }

    public static void install(OptimizedTruffleRuntime runtime) {
        if (factory != null) {
            runtime.addListener(new JFRListener(runtime));
        }
    }

    public static boolean isInstrumented(ResolvedJavaMethod method) {
        // Initialization must be deferred into the image execution time
        InstrumentedFilterState currentState = instrumentedFilterState.get();
        if (currentState == InstrumentedFilterState.INACTIVE) {
            return false;
        }
        return isInstrumentedImpl(method, currentState);
    }

    @Override
    public void onCompilationStarted(OptimizedCallTarget target, AbstractCompilationTask task) {
        CompilationEvent event = factory.createCompilationEvent();
        if (event.isEnabled()) {
            event.setRootFunction(target);
            event.compilationStarted();
        } else {
            event = null;
        }
        currentCompilation.set(new CompilationData(event));
    }

    @Override
    public void onCompilationDeoptimized(OptimizedCallTarget target, Frame frame) {
        DeoptimizationEvent event = factory.createDeoptimizationEvent();
        if (event.isEnabled()) {
            event.setRootFunction(target);
            event.publish();
        }
    }

    @Override
    public void onCompilationTruffleTierFinished(OptimizedCallTarget target, AbstractCompilationTask task, GraphInfo graph) {
        CompilationData data = getCurrentData();
        if (data.event != null) {
            data.partialEvalNodeCount = graph.getNodeCount();
            data.timePartialEvaluationFinished = System.nanoTime();
        }
    }

    @Override
    public void onCompilationFailed(OptimizedCallTarget target, String reason, boolean bailout, boolean permanentBailout, int tier, Supplier<String> serializedException) {
        CompilationData data = getCurrentData();
        statistics.finishCompilation(data.finish(), bailout, 0);
        if (data.event != null) {
            data.event.failed(tier, isPermanentFailure(bailout, permanentBailout), reason, serializedException);
            data.event.publish();
        }
        currentCompilation.remove();
    }

    @Override
    public void onCompilationSuccess(OptimizedCallTarget target, AbstractCompilationTask task, GraphInfo graph, CompilationResultInfo result) {
        CompilationData data = getCurrentData();
        int compiledCodeSize = result.getTargetCodeSize();
        statistics.finishCompilation(data.finish(), false, compiledCodeSize);
        if (data.event != null) {
            CompilationEvent event = data.event;
            event.succeeded(task.tier());
            event.setCompiledCodeSize(compiledCodeSize);
            if (target.getCodeAddress() != 0) {
                event.setCompiledCodeAddress(target.getCodeAddress());
            }

            int calls = task.countCalls();
            int inlinedCalls = task.countInlinedCalls();
            int dispatchedCalls = calls - inlinedCalls;
            event.setInlinedCalls(inlinedCalls);
            event.setDispatchedCalls(dispatchedCalls);
            event.setGraalNodeCount(graph.getNodeCount());
            event.setPartialEvaluationNodeCount(data.partialEvalNodeCount);
            event.setPartialEvaluationTime((data.timePartialEvaluationFinished - data.timeCompilationStarted) / 1_000_000);
            event.publish();
            currentCompilation.remove();
        }
    }

    @Override
    public void onCompilationInvalidated(OptimizedCallTarget target, Object source, CharSequence reason) {
        statistics.invalidations.incrementAndGet();
        InvalidationEvent event = factory.createInvalidationEvent();
        if (event.isEnabled()) {
            event.setRootFunction(target);
            event.setReason(reason);
            event.publish();
        }
    }

    private CompilationData getCurrentData() {
        return currentCompilation.get();
    }

    private static final class CompilationData {
        final CompilationEvent event;
        final long timeCompilationStarted;
        int partialEvalNodeCount;
        long timePartialEvaluationFinished;

        CompilationData(CompilationEvent event) {
            this.event = event;
            this.timeCompilationStarted = System.nanoTime();
        }

        int finish() {
            return (int) (System.nanoTime() - timeCompilationStarted) / 1_000_000;
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

    private static EventFactory lookupFactory() {
        if (ImageInfo.inImageCode()) {
            return ImageSingletons.contains(EventFactory.class) ? ImageSingletons.lookup(EventFactory.class) : null;
        } else {
            Iterator<EventFactory.Provider> it = TruffleRuntimeServices.load(EventFactory.Provider.class).iterator();
            EventFactory.Provider provider = it.hasNext() ? it.next() : null;
            if (provider != null) {
                ModuleUtil.exportTo(provider.getClass());
                return provider.getEventFactory();
            } else {
                return null;
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

        if (("traceThrowable".equals(method.getName()) || "traceError".equals(method.getName())) && "Ljdk/jfr/internal/instrument/ThrowableTracer;".equals(method.getDeclaringClass().getName())) {
            return true;
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
