/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.LinkedList;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.SpecializationStatistics;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.utilities.TruffleWeakReference;
import com.oracle.truffle.polyglot.PolyglotLocals.LocalLocation;

final class PolyglotThreadInfo {

    static final PolyglotThreadInfo NULL = new PolyglotThreadInfo(null, null);
    private static final Object NULL_CLASS_LOADER = new Object();

    final PolyglotContextImpl context;
    @CompilationFinal private final TruffleWeakReference<Thread> thread;

    /*
     * Only modify if Thread.currentThread() == thread.get().
     */
    private volatile int enteredCount;
    final LinkedList<Object[]> explicitContextStack = new LinkedList<>();
    volatile boolean cancelled;
    private Object originalContextClassLoader = NULL_CLASS_LOADER;
    private ClassLoaderEntry prevContextClassLoader;
    private SpecializationStatisticsEntry executionStatisticsEntry;

    private boolean safepointActive; // only accessed from current thread
    @CompilationFinal(dimensions = 1) Object[] contextThreadLocals;

    // only accessed from PolyglotFastThreadLocals
    final Object[] fastThreadLocals;

    PolyglotThreadInfo(PolyglotContextImpl context, Thread thread) {
        this.context = context;
        this.thread = new TruffleWeakReference<>(thread);
        this.fastThreadLocals = context == null ? null : PolyglotFastThreadLocals.createFastThreadLocals(this);
    }

    Thread getThread() {
        return thread.get();
    }

    boolean isSafepointActive() {
        assert isCurrent();
        return safepointActive;
    }

    public void setSafepointActive(boolean safepointActive) {
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
    }

    boolean isCurrent() {
        return getThread() == Thread.currentThread();
    }

    /**
     * Not to be used directly. Use
     * {@link PolyglotEngineImpl#enter(PolyglotContextImpl, boolean, Node, boolean)} instead.
     */
    @SuppressFBWarnings("VO_VOLATILE_INCREMENT")
    Object[] enterInternal() {
        Object[] prev = PolyglotFastThreadLocals.enter(this);
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
        enteredCount--;
        PolyglotFastThreadLocals.leave(prev);
    }

    void notifyEnter(PolyglotEngineImpl engine, PolyglotContextImpl profiledContext) {
        if (!engine.customHostClassLoader.isValid()) {
            setContextClassLoader();
        }

        EngineAccessor.INSTRUMENT.notifyEnter(engine.instrumentationHandler, profiledContext.creatorTruffleContext);

        if (engine.specializationStatistics != null) {
            enterStatistics(engine.specializationStatistics);
        }
    }

    boolean isPolyglotThread(PolyglotContextImpl c) {
        if (getThread() instanceof PolyglotThread) {
            return ((PolyglotThread) getThread()).isOwner(c);
        }
        return false;
    }

    /*
     * Volatile decrement is safe if only one thread does it.
     */
    @SuppressFBWarnings("VO_VOLATILE_INCREMENT")
    void notifyLeave(PolyglotEngineImpl engine, PolyglotContextImpl profiledContext) {
        assert Thread.currentThread() == getThread();

        /*
         * Notify might be false if the context was closed already on a second thread.
         */
        try {
            EngineAccessor.INSTRUMENT.notifyLeave(engine.instrumentationHandler, profiledContext.creatorTruffleContext);
        } finally {
            if (!engine.customHostClassLoader.isValid()) {
                restoreContextClassLoader();
            }
            if (engine.specializationStatistics != null) {
                leaveStatistics(engine.specializationStatistics);
            }
        }
    }

    Object getThreadLocal(LocalLocation l) {
        // thread id is guaranteed to be unique
        return l.readLocal(this.context, getThreadLocals(l.engine), true);
    }

    private Object[] getThreadLocals(PolyglotEngineImpl e) {
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
        return super.toString() + "[thread=" + getThread() + ", enteredCount=" + enteredCount + ", cancelled=" + cancelled + "]";
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
