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
            return new DebugValue.HeapValue(executionLifecycle.getSession(), languageInfo, null, result);
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
