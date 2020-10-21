/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.SpecializationStatistics;
import com.oracle.truffle.api.utilities.TruffleWeakReference;

final class PolyglotThreadInfo {

    static final PolyglotThreadInfo NULL = new PolyglotThreadInfo(null, null);
    private static final Object NULL_CLASS_LOADER = new Object();

    private final PolyglotContextImpl context;
    private final TruffleWeakReference<Thread> thread;

    /*
     * Only modify if Thread.currentThread() == thread.get().
     */
    private volatile int enteredCount;
    final LinkedList<PolyglotContextImpl> explicitContextStack = new LinkedList<>();
    volatile boolean cancelled;
    private Object originalContextClassLoader = NULL_CLASS_LOADER;
    private ClassLoaderEntry prevContextClassLoader;
    private SpecializationStatisticsEntry executionStatisticsEntry;

    private volatile Object[] contextThreadLocals;

    PolyglotThreadInfo(PolyglotContextImpl context, Thread thread) {
        this.context = context;
        this.thread = new TruffleWeakReference<>(thread);
    }

    Thread getThread() {
        return thread.get();
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

    /*
     * Volatile increment is safe if only one thread does it.
     */
    @SuppressFBWarnings("VO_VOLATILE_INCREMENT")
    void enter(PolyglotEngineImpl engine, PolyglotContextImpl profiledContext) {
        assert Thread.currentThread() == getThread();
        enteredCount++;
        if (CompilerDirectives.injectBranchProbability(CompilerDirectives.SLOWPATH_PROBABILITY, profiledContext.closed)) {
            /*
             * This event should be very rare. The context was closed between the volatile read of
             * the cached thread info read and the entered count increment. Maybe we should change
             * this to an assumption check to speed it up.
             */
            CompilerDirectives.transferToInterpreter();
            enteredCount--;
            profiledContext.checkClosed();
            assert false : "checkClosed must throw";
        }
        if (!engine.customHostClassLoader.isValid()) {
            setContextClassLoader();
        }
        try {
            EngineAccessor.INSTRUMENT.notifyEnter(engine.instrumentationHandler, profiledContext.creatorTruffleContext);
        } catch (Throwable t) {
            enteredCount--;
            throw t;
        }
        if (engine.specializationStatistics != null) {
            engine.specializationStatistics.enter();
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
    void leave(PolyglotEngineImpl engine, PolyglotContextImpl profiledContext) {
        assert Thread.currentThread() == getThread();
        /*
         * Notify might be false if the context was closed already on a second thread.
         */
        try {
            EngineAccessor.INSTRUMENT.notifyLeave(engine.instrumentationHandler, profiledContext.creatorTruffleContext);
        } finally {
            enteredCount--;
            if (!engine.customHostClassLoader.isValid()) {
                restoreContextClassLoader();
            }
            if (engine.specializationStatistics != null) {
                leaveStatistics(engine.specializationStatistics);
            }
        }

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

    boolean isLastActive() {
        return getThread() != null && enteredCount == 1 && !cancelled;
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
