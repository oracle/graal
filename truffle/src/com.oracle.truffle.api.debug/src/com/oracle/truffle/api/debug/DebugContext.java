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

import java.io.IOException;
import java.util.function.Supplier;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.source.Source;

/**
 * Representation of a polyglot context in a guest language execution.
 *
 * @since 0.30
 */
public final class DebugContext {

    private final DebuggerExecutionLifecycle executionLifecycle;
    private final TruffleContext context;

    DebugContext(DebuggerExecutionLifecycle executionLifecycle, TruffleContext context) {
        this.executionLifecycle = executionLifecycle;
        this.context = context;
    }

    /**
     * Evaluate the given code in this context.
     *
     * @param code the code to evaluate
     * @param languageId the language to evaluate the code in
     * @return result of the evaluation
     * @since 0.30
     */
    public DebugValue evaluate(String code, String languageId) {
        assert code != null;
        Object prevContext = context.enter();
        try {
            Debugger debugger = executionLifecycle.getDebugger();
            CallTarget target = debugger.getEnv().parse(Source.newBuilder(languageId, code, "eval").build());
            Object result = target.call();
            LanguageInfo languageInfo = debugger.getEnv().getLanguages().get(languageId);
            return new DebugValue.HeapValue(debugger, languageInfo, null, result);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        } finally {
            context.leave(prevContext);
        }
    }

    /**
     * Run supplied operations in this context. Use it to call methods on {@link DebugValue} that
     * was obtained from {@link #evaluate(java.lang.String, java.lang.String)}.
     *
     * @param <T> a type of the return value of the supplier
     * @param run a supplier representing operations to be run in this context
     * @return the supplier's return value
     * @since 0.30
     */
    public <T> T runInContext(Supplier<T> run) {
        assert run != null;
        Object prevContext = context.enter();
        try {
            T ret = run.get();
            return ret;
        } finally {
            context.leave(prevContext);
        }
    }

    /**
     * Get a parent context of this context, if any. This provides the hierarchy of inner contexts.
     *
     * @return a parent context, or <code>null</code> if there is no parent
     * @since 0.30
     */
    public DebugContext getParent() {
        TruffleContext parent = context.getParent();
        if (parent == null) {
            return null;
        }
        return executionLifecycle.getCachedDebugContext(parent);
    }
}
