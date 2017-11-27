/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.debug;

import com.oracle.truffle.api.nodes.LanguageInfo;

/**
 * Listener to be notified about changes of contexts in guest language application.
 * <p>
 * Use
 * {@link DebuggerSession#setContextsListener(com.oracle.truffle.api.debug.DebugContextsListener, boolean)}
 * to register an implementation of this listener.
 * <p>
 * The listener gets called when a new {@link DebugContext context} is created or disposed and when
 * individual languages are initialized or disposed in that context.
 *
 * @see DebuggerSession#setContextsListener(com.oracle.truffle.api.debug.DebugContextsListener,
 *      boolean)
 * @since 0.30
 */
public interface DebugContextsListener {

    /**
     * Notifies about creation of a new polyglot context.
     *
     * @since 0.30
     */
    void contextCreated(DebugContext context);

    /**
     * Notifies about creation of a language-specific context in an existing polyglot context.
     *
     * @param context the polyglot context
     * @param language the language for which a language-specific context was created
     * @since 0.30
     */
    void languageContextCreated(DebugContext context, LanguageInfo language);

    /**
     * Notifies about initialization of a language-specific context in an existing polyglot context.
     *
     * @param context the polyglot context
     * @param language the language for which a language-specific context was initialized
     * @since 0.30
     */
    void languageContextInitialized(DebugContext context, LanguageInfo language);

    /**
     * Notifies about finalization of a language-specific context in an existing polyglot context.
     *
     * @param context the polyglot context
     * @param language the language for which a language-specific context was finalized
     * @since 0.30
     */
    void languageContextFinalized(DebugContext context, LanguageInfo language);

    /**
     * Notifies about disposal of a language-specific context in an existing polyglot context.
     *
     * @param context the polyglot context
     * @param language the language for which a language-specific context was disposed
     * @since 0.30
     */
    void languageContextDisposed(DebugContext context, LanguageInfo language);

    /**
     * Notifies about close of a polyglot context.
     *
     * @since 0.30
     */
    void contextClosed(DebugContext context);

}
