/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot;

import static com.oracle.truffle.polyglot.EngineAccessor.LANGUAGE;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.dsl.SpecializationStatistics;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.utilities.TruffleWeakReference;
import org.graalvm.polyglot.Context;

// Information attached by context to each thread which entered the context
final class PolyglotThreadInfo {

    static final PolyglotThreadInfo NULL = new PolyglotThreadInfo(null, null, null);
    private static final Object NULL_CLASS_LOADER = new Object();

    final PolyglotContextImpl context;
    @CompilationFinal private final TruffleWeakReference<Thread> thread;
    private final boolean polyglotThread;
    /**
     * Only true if the thread was created "inside" exitContext, i.e. created from the thread
     * running exitContext(), or transitively from such a thread created inside exitContext.
     */
    final boolean createdInExitContext;

    /*
     * Only modify if Thread.currentThread() == thread.get().
     */
    private volatile int enteredCount;
    private volatile TruffleSafepoint.Interrupter leaveAndEnterInterrupter;
    final LinkedList<Object[]> explicitContextStack = new LinkedList<>();
    boolean interruptSent;
    volatile boolean cancelled;
    volatile boolean leaveAndEnterInterrupted;
    private Object originalContextClassLoader = NULL_CLASS_LOADER;
    private ClassLoaderEntry prevContextClassLoader;
    private SpecializationStatisticsEntry executionStatisticsEntry;

    private boolean safepointActive; // only accessed from current thread
    @CompilationFinal(dimensions = 1) Object[] contextThreadLocals;

    // only accessed from PolyglotFastThreadLocals
    final Object[] fastThreadLocals;
    final EncapsulatingNodeReference encapsulatingNodeReference;

    private final BitSet initializedLanguageContexts;
    private final BitSet initializingLanguageContexts;
    private boolean finalizationComplete;

    private final List<ProbeNode> probesEnterList;
    /**
     * Field holding the Context instance to prevent it from being collected while there is an
     * explicitly entered thread.
     */
    volatile Context explicitEnterAnchor;

    /*
     * Set only for dead embedder threads (Thread#isAlive() == false) to claim the finalization of
     * the dead embedder threads by another embedder thread that is just entering the context.
     */
    boolean finalizingDeadThread;

    private static final boolean ASSERT_ENTER_RETURN_PARITY;

    static {
        boolean assertsOn = false;
        assert !!(assertsOn = true);
        ASSERT_ENTER_RETURN_PARITY = assertsOn;
    }

    PolyglotThreadInfo(PolyglotContextImpl context, Thread thread, PolyglotThreadTask polyglotThreadTask) {
        this.context = context;
        this.thread = new TruffleWeakReference<>(thread);
        this.polyglotThread = polyglotThreadTask != null;
        this.createdInExitContext = isCreatedInExitContext(context, polyglotThreadTask);
        if (context == null) {
            this.encapsulatingNodeReference = null;
            this.fastThreadLocals = null;
        } else {
            this.encapsulatingNodeReference = EngineAccessor.NODES.createEncapsulatingNodeReference(thread);
            this.fastThreadLocals = PolyglotFastThreadLocals.createFastThreadLocals(this);
        }
        if (context != null) {
            initializingLanguageContexts = new BitSet(context.contexts.length);
            initializedLanguageContexts = new BitSet(context.contexts.length);
        } else {
            initializingLanguageContexts = null;
            initializedLanguageContexts = null;
        }
        this.probesEnterList = initProbesEnterList(context);
    }

    private static boolean isCreatedInExitContext(PolyglotContextImpl context, PolyglotThreadTask polyglotThreadTask) {
        if (polyglotThreadTask == null || polyglotThreadTask == PolyglotThreadTask.ISOLATE_POLYGLOT_THREAD) {
            return false;
        }
        Thread parentThread = polyglotThreadTask.parentThread;
        Thread hardExitTriggeringThread = context.closeExitedTriggerThread;
        if (hardExitTriggeringThread != null) {
            if (hardExitTriggeringThread == parentThread) {
                return true;
            } else {
                PolyglotThreadInfo parentInfo = context.getThreadInfo(parentThread);
                return parentInfo.isPolyglotThread() && parentInfo.createdInExitContext;
            }
        }
        return false;
    }

    private static List<ProbeNode> initProbesEnterList(PolyglotContextImpl context) {
        boolean assertProbes = context != null && context.engine.probeAssertionsEnabled;
        if (assertProbes) {
            return new ArrayList<>();
        } else {
            return null;
        }
    }

    Thread getThread() {
        return thread.get();
    }

    boolean isFinalizingDeadThread() {
        assert Thread.holdsLock(context);
        return finalizingDeadThread;
    }

    void setFinalizingDeadThread() {
        assert Thread.holdsLock(context);
        this.finalizingDeadThread = true;
    }

    boolean isLanguageContextInitialized(PolyglotLanguage language) {
        assert Thread.holdsLock(context);
        return initializedLanguageContexts.get(language.engineIndex);
    }

    void setLanguageContextInitializing(PolyglotLanguageContext languageContext) {
        assert Thread.holdsLock(context);
        assert !finalizationComplete;
        initializingLanguageContexts.set(languageContext.language.engineIndex);
    }

    void clearLanguageContextInitializing(PolyglotLanguageContext languageContext) {
        assert Thread.holdsLock(context);
        initializingLanguageContexts.clear(languageContext.language.engineIndex);
    }

    boolean isLanguageContextInitializing(PolyglotLanguage language) {
        assert Thread.holdsLock(context);
        return initializingLanguageContexts.get(language.engineIndex);
    }

    void setLanguageContextInitialized(PolyglotLanguageContext languageContext) {
        assert Thread.holdsLock(context);
        assert !finalizationComplete;
        assert initializingLanguageContexts.get(languageContext.language.engineIndex);
        initializedLanguageContexts.set(languageContext.language.engineIndex);
    }

    void initializeLanguageContext(PolyglotLanguageContext languageContext) {
        LANGUAGE.initializeThread(languageContext.env, getThread());
    }

    int initializedLanguageContextsCount() {
        assert Thread.holdsLock(context);
        return initializedLanguageContexts.cardinality();
    }

    boolean isFinalizationComplete() {
        assert Thread.holdsLock(context);
        return finalizationComplete;
    }

    void setFinalizationComplete(PolyglotEngineImpl engine, boolean mustSucceed) {
        assert Thread.holdsLock(context);
        this.finalizationComplete = true;
        // Assert only when !mustSucceed, partity might not be met on cancellation.
        if (ASSERT_ENTER_RETURN_PARITY && !mustSucceed && engine.probeAssertionsEnabled) {
            assertProbeThreadFinalized();
        }
    }

    boolean isSafepointActive() {
        assert isCurrent();
        return safepointActive;
    }

    void setSafepointActive(boolean safepointActive) {
        assert isCurrent();
        this.safepointActive = safepointActive;
    }

    public Object[] getContextThreadLocals() {
        assert Thread.holdsLock(context);
        return contextThreadLocals;
    }

    public void setContextThreadLocals(Object[] contextThreadLocals) {
        assert Thread.holdsLock(context);
        this.contextThreadLocals = contextThreadLocals;
        this.fastThreadLocals[PolyglotFastThreadLocals.CONTEXT_THREAD_LOCALS_INDEX] = contextThreadLocals;
    }

    boolean isCurrent() {
        return getThread() == Thread.currentThread();
    }

    TruffleSafepoint.Interrupter getLeaveAndEnterInterrupter() {
        return leaveAndEnterInterrupter;
    }

    boolean isInLeaveAndEnter() {
        return leaveAndEnterInterrupter != null;
    }

    void setLeaveAndEnterInterrupter(TruffleSafepoint.Interrupter interrupter) {
        this.leaveAndEnterInterrupter = interrupter;
    }

    /**
     * Not to be used directly. Use
     * {@link PolyglotEngineImpl#enter(PolyglotContextImpl, boolean, Node, boolean)} instead.
     */
    @SuppressFBWarnings("VO_VOLATILE_INCREMENT")
    Object[] enterInternal() {
        Object[] prev = PolyglotFastThreadLocals.enter(this);
        assert Thread.currentThread() == getThread() : "Volatile increment is safe on a single thread only.";
        enteredCount++;
        return prev;
    }

    int getEnteredCount() {
        assert Thread.currentThread() == thread.get();
        return enteredCount;
    }

    /**
     * Not to be used directly. Use
     * {@link PolyglotEngineImpl#leave(PolyglotContextImpl, PolyglotContextImpl)} instead.
     */
    @SuppressFBWarnings("VO_VOLATILE_INCREMENT")
    void leaveInternal(Object[] prev) {
        assert Thread.currentThread() == getThread() : "Volatile decrement is safe on a single thread only.";
        enteredCount--;
        PolyglotFastThreadLocals.leave(prev);
    }

    void notifyEnter(PolyglotEngineImpl engine, PolyglotContextImpl profiledContext) {
        if (!engine.customHostClassLoader.isValid()) {
            setContextClassLoader();
        }

        EngineAccessor.INSTRUMENT.notifyEnter(engine.instrumentationHandler, profiledContext.getCreatorTruffleContext());

        if (engine.specializationStatistics != null) {
            enterStatistics(engine.specializationStatistics);
        }
    }

    /**
     * Returns true if and only if the thread is a polyglot thread created by {@link #context}. For
     * example it is false if context 1 created this polyglot thread but we are calling this method
     * in an inner context 2. {@link PolyglotThreadInfo} are stored per context in
     * {@code PolyglotContextImpl#threads}.
     */
    boolean isPolyglotThread() {
        return polyglotThread;
    }

    void notifyLeave(PolyglotEngineImpl engine, PolyglotContextImpl profiledContext) {
        assert Thread.currentThread() == getThread();

        /*
         * Notify might be false if the context was closed already on a second thread.
         */
        try {
            EngineAccessor.INSTRUMENT.notifyLeave(engine.instrumentationHandler, profiledContext.getCreatorTruffleContext());
        } finally {
            if (!engine.customHostClassLoader.isValid()) {
                restoreContextClassLoader();
            }
            if (engine.specializationStatistics != null) {
                leaveStatistics(engine.specializationStatistics);
            }
        }
    }

    @TruffleBoundary
    private void assertProbeThreadFinalized() {
        if (probesEnterList != null) {
            assert probesEnterList.isEmpty() : getEnteredProbesMessage(probesEnterList);
        }
    }

    private static String getEnteredProbesMessage(List<ProbeNode> probes) {
        StringBuilder sb = new StringBuilder("Found entered probes without return: ");
        sb.append(probes);
        sb.append("\nSpecifically, a call to ProbeNode.onEnter()/onResume() does not have a corresponding call to ProbeNode.onReturnValue()/onReturnExceptionalOrUnwind()/onYield().");
        for (ProbeNode probe : probes) {
            sb.append("\n  probe ");
            sb.append(probe);
            sb.append(" with parent node ");
            sb.append(probe.getParent().getClass());
        }
        sb.append('\n');
        return sb.toString();
    }

    @TruffleBoundary
    void assertProbeEntered(ProbeNode probe) {
        Objects.requireNonNull(probe);
        probesEnterList.add(probe);
    }

    @TruffleBoundary
    void assertProbeReturned(ProbeNode probe) {
        assert !probesEnterList.isEmpty() : "ProbeNode " + probe + " with parent " + probe.getParent().getClass() + " exited without enter";
        ProbeNode lastProbe = probesEnterList.remove(probesEnterList.size() - 1);
        assert probe == lastProbe : "Entered probe " + lastProbe + " with parent " + lastProbe.getParent().getClass() + " differs from the returned probe " +
                        probe + " with parent " + probe.getParent().getClass() + "\n" +
                        "Specifically, a call to onEnter()/onResume() on " + lastProbe + " was not followed by a call to onReturnValue()/onReturnExceptionalOrUnwind()/onYield() on the same probe, " +
                        "but on " + probe + " instead.";
    }

    Object[] getThreadLocals(PolyglotEngineImpl e) {
        CompilerAsserts.partialEvaluationConstant(e);
        Object[] locals = this.contextThreadLocals;
        assert locals != null : "thread local not initialized.";
        if (CompilerDirectives.inCompiledCode()) {
            // get rid of the null check.
            locals = EngineAccessor.RUNTIME.unsafeCast(locals, Object[].class, true, true, true);
        }
        return locals;
    }

    @TruffleBoundary
    private void enterStatistics(SpecializationStatistics statistics) {
        SpecializationStatistics prev = statistics.enter();
        if (prev != null || this.executionStatisticsEntry != null) {
            executionStatisticsEntry = new SpecializationStatisticsEntry(prev, executionStatisticsEntry);
        }
    }

    @TruffleBoundary
    private void leaveStatistics(SpecializationStatistics statistics) {
        SpecializationStatisticsEntry entry = this.executionStatisticsEntry;
        if (entry == null) {
            statistics.leave(null);
        } else {
            statistics.leave(entry.statistics);
            this.executionStatisticsEntry = entry.next;
        }
    }

    boolean isActiveNotCancelled() {
        return getThread() != null && enteredCount > 0 && !cancelled;
    }

    boolean isActive() {
        return getThread() != null && enteredCount > 0;
    }

    @Override
    public String toString() {
        return super.toString() + "[thread=" + getThread() + ", enteredCount=" + enteredCount + ", cancelled=" + cancelled +
                        ", leaveAndEnterInterrupted=" + leaveAndEnterInterrupted + "]";
    }

    @TruffleBoundary
    private void setContextClassLoader() {
        ClassLoader hostClassLoader = context.config.hostClassLoader;
        if (hostClassLoader != null) {
            Thread t = getThread();
            ClassLoader original = t.getContextClassLoader();
            assert originalContextClassLoader != NULL_CLASS_LOADER || prevContextClassLoader == null;
            if (originalContextClassLoader != NULL_CLASS_LOADER) {
                prevContextClassLoader = new ClassLoaderEntry((ClassLoader) originalContextClassLoader, prevContextClassLoader);
            }
            originalContextClassLoader = original;
            t.setContextClassLoader(hostClassLoader);
        }
    }

    @TruffleBoundary
    private void restoreContextClassLoader() {
        if (originalContextClassLoader != NULL_CLASS_LOADER) {
            assert context.config.hostClassLoader != null;
            Thread t = getThread();
            t.setContextClassLoader((ClassLoader) originalContextClassLoader);
            if (prevContextClassLoader != null) {
                originalContextClassLoader = prevContextClassLoader.classLoader;
                prevContextClassLoader = prevContextClassLoader.next;
            } else {
                originalContextClassLoader = NULL_CLASS_LOADER;
            }
        }
    }

    private static final class ClassLoaderEntry {
        final ClassLoader classLoader;
        final ClassLoaderEntry next;

        ClassLoaderEntry(ClassLoader classLoader, ClassLoaderEntry next) {
            this.classLoader = classLoader;
            this.next = next;
        }
    }

    private static final class SpecializationStatisticsEntry {
        final SpecializationStatistics statistics;
        final SpecializationStatisticsEntry next;

        SpecializationStatisticsEntry(SpecializationStatistics statistics, SpecializationStatisticsEntry next) {
            this.statistics = statistics;
            this.next = next;
        }
    }

}
