/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.TruffleContext;

/**
 * Listener that allows to be notified when threads become active and deactivated. This operation
 * may called more frequently than the thread initialization with {@link ThreadsListener}, therefore
 * the implementation of the threads activation listener needs to be designed for compilation. Care
 * needs to be taken to not unnecessarily slow down thread activation.
 * <p>
 * The event notification starts immediately after the listener is registered. It is therefore
 * possible that {@link #onLeaveThread(TruffleContext)} is called without
 * {@link #onEnterThread(TruffleContext)} ever being invoked, if the listener is attached in
 * parallel. When any of the listener methods are executed then the
 * {@link ThreadsActivationListener} is guaranteed to be a
 * {@link CompilerAsserts#partialEvaluationConstant(Object) partial evaluation constant}. The
 * provided {@link TruffleContext} may be a PE constant, but it is not guaranteed.
 * <p>
 * Common use-cases for threads activation include capturing the time the context is active,
 * changing thread priorities or measuring thread allocated bytes of a context.
 *
 * @see Instrumenter#attachThreadsActivationListener(ThreadsActivationListener) to register a
 *      listener
 * @since 20.3
 */
public interface ThreadsActivationListener {

    /**
     * Notified when a context is entered on the {@link Thread#currentThread() current thread}.
     * Entering a thread indicates that the context is currently active. A context maybe entered
     * multiple times per thread. This method is executed frequently and must be designed for
     * compilation. If this method throws an
     * {@link com.oracle.truffle.api.exception.AbstractTruffleException} the exception interop
     * messages may be executed without a context being entered.
     *
     * @param context the context being entered on the current thread
     * @since 20.3
     */
    void onEnterThread(TruffleContext context);

    /**
     * Notified when a context is entered on the {@link Thread#currentThread() current thread}.
     * Leaving a thread indicates that the context is no longer active on that thread. A context
     * maybe left multiple times per thread. This method is executed frequently and must be designed
     * for compilation.
     *
     * @param context the context being left on the current thread
     * @since 20.3
     */
    void onLeaveThread(TruffleContext context);

}
