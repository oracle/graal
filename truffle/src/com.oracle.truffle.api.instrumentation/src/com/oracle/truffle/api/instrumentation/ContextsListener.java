/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.nodes.LanguageInfo;

/**
 * Listener to be notified about changes of contexts in guest language application.
 * <p>
 * Use
 * {@link Instrumenter#attachContextsListener(com.oracle.truffle.api.instrumentation.ContextsListener, boolean)}
 * to register an implementation of this listener. Use {@link EventBinding#dispose()} to unregister.
 * <p>
 * The listener gets called when a new {@link TruffleContext context} is created or disposed and
 * when individual languages are initialized or disposed in that context.
 *
 * @see Instrumenter#attachContextsListener(com.oracle.truffle.api.instrumentation.ContextsListener,
 *      boolean)
 * @since 0.30
 */
public interface ContextsListener {

    /**
     * Notifies about creation of a new polyglot context.
     *
     * @since 0.30
     */
    void onContextCreated(TruffleContext context);

    /**
     * Notifies about creation of a language-specific context in an existing polyglot context.
     *
     * @param context the polyglot context
     * @param language the language for which a language-specific context was created
     * @since 0.30
     */
    void onLanguageContextCreated(TruffleContext context, LanguageInfo language);

    /**
     * Notifies about initialization of a language-specific context in an existing polyglot context.
     *
     * @param context the polyglot context
     * @param language the language for which a language-specific context was initialized
     * @since 0.30
     */
    void onLanguageContextInitialized(TruffleContext context, LanguageInfo language);

    /**
     * Notifies about finalization of a language-specific context in an existing polyglot context.
     *
     * @param context the polyglot context
     * @param language the language for which a language-specific context was finalized
     * @since 0.30
     */
    void onLanguageContextFinalized(TruffleContext context, LanguageInfo language);

    /**
     * Notifies about disposal of a language-specific context in an existing polyglot context.
     *
     * @param context the polyglot context
     * @param language the language for which a language-specific context was disposed
     * @since 0.30
     */
    void onLanguageContextDisposed(TruffleContext context, LanguageInfo language);

    /**
     * Notifies about close of a polyglot context.
     *
     * @since 0.30
     */
    void onContextClosed(TruffleContext context);
}
