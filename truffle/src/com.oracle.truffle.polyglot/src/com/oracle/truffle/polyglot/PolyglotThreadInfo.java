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

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.utilities.TruffleWeakReference;

final class PolyglotThreadInfo {

    static final PolyglotThreadInfo NULL = new PolyglotThreadInfo(null, null);
    private static final Object NULL_CLASS_LOADER = new Object();

    private final PolyglotContextImpl context;
    private final TruffleWeakReference<Thread> thread;

    private int enteredCount;
    final LinkedList<Object> explicitContextStack = new LinkedList<>();
    volatile boolean cancelled;
    private volatile long lastEntered;
    private volatile long timeExecuted;
    private boolean deprioritized;
    private Object originalContextClassLoader = NULL_CLASS_LOADER;
    private ClassLoaderEntry prevContextClassLoader;

    private static volatile ThreadMXBean threadBean;

    PolyglotThreadInfo(PolyglotContextImpl context, Thread thread) {
        this.context = context;
        this.thread = new TruffleWeakReference<>(thread);
        this.deprioritized = false;
    }

    Thread getThread() {
        return thread.get();
    }

    boolean isCurrent() {
        return getThread() == Thread.currentThread();
    }

    void enter(PolyglotEngineImpl engine) {
        assert Thread.currentThread() == getThread();
        if (!engine.noPriorityChangeNeeded.isValid() && !deprioritized) {
            lowerPriority();
            deprioritized = true;
        }
        int count = ++enteredCount;
        if (!engine.noThreadTimingNeeded.isValid() && count == 1) {
            lastEntered = getTime();
        }
        if (!engine.customHostClassLoader.isValid()) {
            setContextClassLoader();
        }
    }

    @TruffleBoundary
    private void lowerPriority() {
        getThread().setPriority(Thread.MIN_PRIORITY);
    }

    @TruffleBoundary
    private void raisePriority() {
        // this will be ineffective unless the JVM runs with corresponding system privileges
        getThread().setPriority(Thread.NORM_PRIORITY);
    }

    void resetTiming() {
        if (enteredCount > 0) {
            lastEntered = getTime();
        }
        this.timeExecuted = 0;
    }

    long getTimeExecuted() {
        long totalTime = timeExecuted;
        long last = this.lastEntered;
        if (last > 0) {
            totalTime += getTime() - last;
        }
        return totalTime;
    }

    @TruffleBoundary
    private long getTime() {
        Thread t = getThread();
        if (t == null) {
            return timeExecuted;
        }
        ThreadMXBean bean = threadBean;
        if (bean == null) {
            /*
             * getThreadMXBean is synchronized so better cache in a local volatile field to avoid
             * contention.
             */
            threadBean = bean = ManagementFactory.getThreadMXBean();
        }
        long time = bean.getThreadCpuTime(t.getId());
        if (time == -1) {
            return TimeUnit.MILLISECONDS.convert(System.currentTimeMillis(), TimeUnit.NANOSECONDS);
        }
        return time;
    }

    boolean isPolyglotThread(PolyglotContextImpl c) {
        if (getThread() instanceof PolyglotThread) {
            return ((PolyglotThread) getThread()).isOwner(c);
        }
        return false;
    }

    void leave(PolyglotEngineImpl engine) {
        assert Thread.currentThread() == getThread();
        int count = --enteredCount;
        if (!engine.customHostClassLoader.isValid()) {
            restoreContextClassLoader();
        }
        if (!engine.noThreadTimingNeeded.isValid() && count == 0) {
            long last = this.lastEntered;
            this.lastEntered = 0;
            this.timeExecuted += getTime() - last;
        }
        if (!engine.noPriorityChangeNeeded.isValid() && deprioritized && count == 0) {
            raisePriority();
            deprioritized = false;
        }

    }

    boolean isLastActive() {
        assert Thread.currentThread() == getThread();
        return getThread() != null && enteredCount == 1 && !cancelled;
    }

    boolean isActive() {
        return getThread() != null && enteredCount > 0 && !cancelled;
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
}
