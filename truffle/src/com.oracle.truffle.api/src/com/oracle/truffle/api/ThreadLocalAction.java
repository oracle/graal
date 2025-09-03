/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.Future;
import java.util.function.Consumer;

import org.graalvm.polyglot.impl.AbstractPolyglotImpl;

import com.oracle.truffle.api.nodes.Node;

/**
 * Represents an action that is executed at a {@link TruffleSafepoint safepoint} location of the
 * guest language execution. Thread local actions can be submitted by
 * {@link TruffleLanguage.Env#submitThreadLocal(Thread[], ThreadLocalAction) languages} or
 * {@link com.oracle.truffle.api.instrumentation.TruffleInstrument.Env#submitThreadLocal(TruffleContext, Thread[], ThreadLocalAction)
 * instruments}. When an action is submitted it will be {@link #perform(Access) performed} locally
 * on the threads they are submitted to. After submitting a thread local action a {@link Future} is
 * returned that allows to wait for and cancel the submitted action.
 * <p>
 * Thread local actions can be configured to allow side-effects using the constructor of the action.
 * If side-effects are allowed (<code>true</code>), then the thread-local action is allowed to throw
 * non-internal guest language exceptions from the action and modify the observable guest
 * application state. Otherwise, the action is not allowed to modify the observable state, and any
 * non-internal guest language exception will be transformed to an internal error of type
 * {@link AssertionError}. Side-effects may be temporarily
 * {@link TruffleSafepoint#setAllowSideEffects(boolean) disabled} by the guest language.
 * <p>
 * Thread local actions can also be set to be executed in a synchronous or asynchronous way. A
 * submitted synchronous thread-local action waits until it is started on all threads that it was
 * submitted on. Then the action is {@link ThreadLocalAction#perform(Access) performed} on all
 * threads. After they were performed on a thread, the action waits for all threads to complete. No
 * synchronous thread-local actions can be submitted while performing synchronous actions, as this
 * may lead to deadlocks. If a synchronous event is submitted during a synchronous event, then a
 * {@link IllegalStateException} is thrown when the action is submitted. Asynchronous thread-local
 * actions might start and complete to perform independently of each other. There is no restriction
 * on how they may be submitted.
 * <p>
 * Thread local actions are guaranteed to be executed in the same order as they were submitted for a
 * context. If a context has pending thread-local actions, the actions may be canceled when the
 * context is canceled or closed invalid. Exceptions thrown by the action will be forwarded handled
 * by the guest language implementation. The only exception is for truffle exceptions that are
 * thrown for non-side-effecting events.
 * <p>
 * Notifications of blocking:
 * <p>
 * {@link #notifyBlocked(Access)} and {@link #notifyUnblocked(Access)} notify thread local actions
 * that their processing has been blocked/unblocked due to
 * {@link TruffleSafepoint#setBlockedFunction(Node, TruffleSafepoint.Interrupter, TruffleSafepoint.InterruptibleFunction, Object, Runnable, Consumer)
 * a blocked call}. {@link #notifyBlocked(Access)} is called for each pending action at the
 * beginning of a blocked call and for each of the continuations of the blocked call after it is
 * interrupted and thread local actions are processed.
 * {@link ThreadLocalAction#notifyUnblocked(Access)} is called for each pending action at the end of
 * a blocked call and also right after each of its interruptions, before thread local actions are
 * processed and the blocked call continues.
 * <p>
 * In case a thread local action is submitted during a blocked call, the call is interrupted and
 * {@link #notifyUnblocked(Access)} is called without a previous call to
 * {@link #notifyBlocked(Access)}. Recurring thread local actions do not repeatedly interrupt a
 * blocked call - a blocked call is not interrupted if all pending actions are recurring actions
 * submitted before the blocked call. New submissions of thread local actions still interrupt
 * blocked calls, no matter if the new thread local actions are recurring or not. When a blocked
 * call is interrupted, all pending actions are processed no matter if they are recurring or not.
 * <p>
 * The notifications of blocking are especially useful for recurring thread local actions as those
 * actions don't interrupt blocked calls and the notifications inform them about the potentially
 * long time intervals when those actions are not executed due to the blocked calls. Non-recurring
 * thread local actions mainly benefit from the {@link ThreadLocalAction#notifyUnblocked(Access)}
 * notification telling them that they interrupted a blocked call. For example, a safepoint sampler
 * might want to exclude the samples from blocked calls.
 *
 * <p>
 * Example Usage:
 *
 * <pre>
 * Env env; // language or instrument environment
 *
 * env.submitThreadLocal(null, new ThreadLocalAction(true, true) {
 *     &#64;Override
 *     protected void perform(Access access) {
 *         assert access.getThread() == Thread.currentThread();
 *     }
 *
 *     &#64;Override
 *     protected void notifyBlocked(Access access) {
 *         assert access.getThread() == Thread.currentThread();
 *     }
 *
 *     &#64;Override
 *     protected void notifyUnblocked(Access access) {
 *         assert access.getThread() == Thread.currentThread();
 *     }

 *     &#64;Override
 *     protected String name() {
 *        return "MyAction"
 *     }
 *
 * });
 *
 * </pre>
 *
 * <p>
 * Further information can be found in the
 * <a href="http://github.com/oracle/graal/blob/master/truffle/docs/Safepoints.md">safepoint
 * tutorial</a>.
 *
 * @see TruffleSafepoint
 * @see TruffleLanguage.Env#submitThreadLocal
 * @see com.oracle.truffle.api.instrumentation.TruffleInstrument.Env#submitThreadLocal
 * @since 21.1
 */
public abstract class ThreadLocalAction {

    private final boolean hasSideEffects;
    private final boolean synchronous;
    private final boolean recurring;

    /**
     * Creates a new thread local action.
     *
     * @param hasSideEffects true if the event may have side-effects else false.
     * @param synchronous true if the event should run synchronous else the event will run
     *            asynchronous.
     * @see ThreadLocalAction
     * @since 21.1
     */
    protected ThreadLocalAction(boolean hasSideEffects, boolean synchronous) {
        this(hasSideEffects, synchronous, false);
    }

    /**
     * Creates a new thread local action.
     *
     * @param hasSideEffects true if the event may have side-effects else false.
     * @param synchronous true if the event should run synchronous else the event will run
     *            asynchronous.
     * @param recurring true if the event should be rescheduled until cancelled, else false.
     * @see ThreadLocalAction
     * @since 21.1
     */
    protected ThreadLocalAction(boolean hasSideEffects, boolean synchronous, boolean recurring) {
        this.hasSideEffects = hasSideEffects;
        this.synchronous = synchronous;
        this.recurring = recurring;
    }

    final boolean isSynchronous() {
        return synchronous;
    }

    final boolean hasSideEffects() {
        return hasSideEffects;
    }

    final boolean isRecurring() {
        return recurring;
    }

    /**
     * Performs the thread local action on a given thread.
     *
     * @param access allows access to the current thread and the current code location.
     * @see ThreadLocalAction
     * @since 21.1
     */
    protected abstract void perform(Access access);

    /**
     * Callback for notifying the thread local action that its processing has been blocked due to
     * {@link TruffleSafepoint#setBlockedFunction(Node, TruffleSafepoint.Interrupter, TruffleSafepoint.InterruptibleFunction, Object, Runnable, Consumer)
     * a blocked call}.
     *
     * @param access allows access to the current thread and the current code location.
     * @see ThreadLocalAction
     * @since 24.2
     */
    protected void notifyBlocked(Access access) {

    }

    /**
     * Callback for notifying the thread local action that its processing has been unblocked during
     * or while leaving
     * {@link TruffleSafepoint#setBlockedFunction(Node, TruffleSafepoint.Interrupter, TruffleSafepoint.InterruptibleFunction, Object, Runnable, Consumer)
     * a blocked call}.
     *
     * @param access allows access to the current thread and the current code location.
     * @see ThreadLocalAction
     * @since 24.2
     */
    protected void notifyUnblocked(Access access) {

    }

    /**
     * Argument class for {@link ThreadLocalAction#perform(Access)}.
     *
     * @since 21.1
     */
    public abstract static class Access {

        /**
         * Constructor for framework use only.
         *
         * @since 23.1
         */
        protected Access(Object secret) {
            if (!LanguageAccessor.ENGINE.isPolyglotSecret(secret)) {
                throw new AssertionError("Constructor for framework use only.");
            }
        }

        /**
         * Constructor for framework use only.
         *
         * @since 21.1
         */
        protected Access(AbstractPolyglotImpl secret) {
            if (!LanguageAccessor.ENGINE.isPolyglotSecret(secret)) {
                throw new AssertionError("Constructor for framework use only.");
            }
        }

        /**
         * Returns the current node location executing on this thread. The return value is
         * guaranteed to be non-null.
         *
         * @since 21.1
         */
        public abstract Node getLocation();

        /**
         * Returns the thread where this thread local action is running on. Currently this always
         * returns {@link Thread#currentThread()}. See the
         * <a href="http://github.com/oracle/graal/blob/master/truffle/docs/Safepoints.md">safepoint
         * tutorial</a> for further details on our plans.
         *
         * @since 21.1
         */
        public abstract Thread getThread();

    }

}
