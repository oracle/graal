/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api;

import static com.oracle.truffle.api.TruffleLanguage.Env.engineToLanguageException;

import java.util.Objects;
import java.util.concurrent.ExecutorService;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * A builder for threads that have access to the appropriate {@link TruffleContext}. The context is
 * either the one that corresponds to the {@link TruffleLanguage.Env language environment} that was
 * used to {@link TruffleLanguage.Env#newTruffleThreadBuilder(Runnable) create} the builder, or the
 * one specified by {@link TruffleThreadBuilder#context(TruffleContext)}. The method
 * {@link TruffleLanguage.Env#newTruffleThreadBuilder(Runnable)} is the only way to create the
 * builder.
 * <p>
 * Threads without an associated context should be created by
 * {@link TruffleLanguage.Env#createSystemThread(Runnable)}.
 *
 * @since 23.0
 */
public final class TruffleThreadBuilder {

    private final Object polyglotLanguageContext;
    private final Runnable runnable;
    private TruffleContext truffleContext;
    private ThreadGroup threadGroup;
    private long stackSize;
    private Runnable beforeEnter;
    private Runnable afterLeave;
    private boolean virtual;

    TruffleThreadBuilder(Object polyglotLanguageContext, Runnable runnable) {
        Objects.requireNonNull(runnable);
        this.polyglotLanguageContext = polyglotLanguageContext;
        this.runnable = runnable;
    }

    /**
     * Specifies {@link TruffleContext} for the threads created by the builder. It has to be an
     * inner context created by
     * {@link TruffleLanguage.Env#newInnerContextBuilder(String...)}.{@link TruffleContext.Builder#build()
     * build()}. If not specified, the context associated with the language environment that created
     * this builder is used ({@link TruffleLanguage.Env#getContext()}).
     * <p>
     * Threads without an associated context should be created by
     * {@link TruffleLanguage.Env#createSystemThread(Runnable)}.
     * 
     * @param innerContext {@link TruffleContext} for the threads created by this builder.
     * 
     * @since 23.1
     */
    public TruffleThreadBuilder context(TruffleContext innerContext) {
        this.truffleContext = innerContext;
        return this;
    }

    /**
     * Specifies thread group for the threads created by this builder.
     * 
     * @param g thread group for the threads created by this builder.
     *
     * @since 23.1
     */
    public TruffleThreadBuilder threadGroup(ThreadGroup g) {
        this.threadGroup = g;
        return this;
    }

    /**
     * Specifies stack size for the threads created by this builder. The default is 0 which means
     * that the parameter is ignored.
     * 
     * @param size stack size for the threads created by this builder.
     *
     * @since 23.1
     */
    public TruffleThreadBuilder stackSize(long size) {
        this.stackSize = size;
        return this;
    }

    /**
     * Specifies before enter notification for the threads created by this builder. The notification
     * runnable is invoked on the thread when the threads starts before the context is entered on
     * the thread.
     * <p>
     * The default value for the notification runnable is <code>null</code> which means that no
     * notification is executed.
     * <p>
     * If the notification runnable throws an exception, it is propagated up and can be handled by
     * an {@link Thread#setUncaughtExceptionHandler(java.lang.Thread.UncaughtExceptionHandler)
     * uncaught exception handler}
     * 
     * @param r before enter notification runnable for the threads created by this builder.
     *
     * @since 23.1
     */
    public TruffleThreadBuilder beforeEnter(Runnable r) {
        this.beforeEnter = r;
        return this;
    }

    /**
     * Specifies after leave notification for the threads created by this builder. The notification
     * runnable is invoked on the thread after the thread leaves the context for the last time, i.e.
     * the thread is about to finish.
     * <p>
     * The default value for the notification runnable is <code>null</code> which means that no
     * notification is executed.
     * <p>
     * If the notification runnable throws an exception, it is propagated up and can be handled by
     * an {@link Thread#setUncaughtExceptionHandler(java.lang.Thread.UncaughtExceptionHandler)
     * uncaught exception handler}
     *
     * @param r after leave notification runnable for the threads created by this builder.
     *
     * @since 23.1
     */
    public TruffleThreadBuilder afterLeave(Runnable r) {
        this.afterLeave = r;
        return this;
    }

    /**
     * Specifies whether to create a virtual thread (Thread#ofVirtual()) or a regular platform
     * thread (the default) for the threads created by this builder.
     *
     * @param v whether to create a virtual thread.
     *
     * @since 24.1
     */
    public TruffleThreadBuilder virtual(boolean v) {
        this.virtual = v;
        return this;
    }

    /**
     * Creates a new thread based on the parameters specified by this builder. The thread is
     * {@link TruffleLanguage#initializeThread(Object, Thread) initialized} when it is
     * {@link Thread#start() started}, and {@link TruffleLanguage#finalizeThread(Object, Thread)
     * finalized} and {@link TruffleLanguage#disposeThread(Object, Thread) disposed} when it
     * finishes its execution.
     * <p>
     * It is recommended to set an
     * {@link Thread#setUncaughtExceptionHandler(java.lang.Thread.UncaughtExceptionHandler) uncaught
     * exception handler} for the created thread. For example the thread can throw an uncaught
     * exception if one of the initialized language contexts don't support execution on this thread.
     * <p>
     * The language that created and started the thread is responsible to stop and join it during
     * the {@link TruffleLanguage#finalizeContext(Object) finalizeContext}, otherwise an internal
     * error is thrown. It's not safe to use the
     * {@link ExecutorService#awaitTermination(long, java.util.concurrent.TimeUnit)} to detect
     * Thread termination as the polyglot thread may be cancelled before executing the executor
     * worker. <br/>
     * A typical implementation looks like:
     * {@link TruffleLanguageSnippets.AsyncThreadLanguage#finalizeContext}
     *
     * @since 23.1
     */
    @TruffleBoundary
    public Thread build() {
        try {
            return LanguageAccessor.engineAccess().createThread(polyglotLanguageContext, runnable, truffleContext != null ? truffleContext.polyglotContext : null, threadGroup, stackSize, beforeEnter,
                            afterLeave, virtual);
        } catch (Throwable t) {
            throw engineToLanguageException(t);
        }
    }
}
