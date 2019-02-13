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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.instrumentation.ContextsListener;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.ThreadsListener;
import com.oracle.truffle.api.nodes.LanguageInfo;

final class DebuggerExecutionLifecycle implements ContextsListener, ThreadsListener {

    private final DebuggerSession session;
    private final Instrumenter lifecycleInstrumenter;
    private EventBinding<ContextsListener> contextsBinding;
    private volatile DebugContextsListener contextsListener;
    private EventBinding<ThreadsListener> threadsBinding;
    private volatile DebugThreadsListener threadsListener;
    private final Map<TruffleContext, DebugContext> contextMap = new ConcurrentHashMap<>();

    DebuggerExecutionLifecycle(DebuggerSession session) {
        this.session = session;
        this.lifecycleInstrumenter = session.getDebugger().getEnv().getInstrumenter();
    }

    synchronized void setContextsListener(DebugContextsListener listener, boolean includeExistingContexts) {
        if (contextsBinding != null) {
            contextsBinding.dispose();
        }
        contextsListener = listener;
        if (listener != null) {
            contextsBinding = lifecycleInstrumenter.attachContextsListener(this, includeExistingContexts);
        } else {
            contextsBinding = null;
            if (threadsBinding == null) {
                contextMap.clear();
            }
        }
    }

    synchronized void setThreadsListener(DebugThreadsListener listener, boolean includeExistingThreads) {
        if (threadsBinding != null) {
            threadsBinding.dispose();
        }
        this.threadsListener = listener;
        if (listener != null) {
            threadsBinding = lifecycleInstrumenter.attachThreadsListener(this, includeExistingThreads);
        } else {
            threadsBinding = null;
            if (contextsBinding == null) {
                contextMap.clear();
            }
        }
    }

    DebugContext getCachedDebugContext(TruffleContext context) {
        return contextMap.computeIfAbsent(context, new Function<TruffleContext, DebugContext>() {
            @Override
            public DebugContext apply(TruffleContext c) {
                return new DebugContext(DebuggerExecutionLifecycle.this, c);
            }
        });
    }

    Debugger getDebugger() {
        return session.getDebugger();
    }

    DebuggerSession getSession() {
        return session;
    }

    @Override
    public void onContextCreated(TruffleContext context) {
        DebugContextsListener l = contextsListener;
        if (l != null) {
            DebugContext dc = getCachedDebugContext(context);
            l.contextCreated(dc);
        }
    }

    @Override
    public void onLanguageContextCreated(TruffleContext context, LanguageInfo language) {
        DebugContextsListener l = contextsListener;
        if (l != null) {
            DebugContext dc = getCachedDebugContext(context);
            l.languageContextCreated(dc, language);
        }
    }

    @Override
    public void onLanguageContextInitialized(TruffleContext context, LanguageInfo language) {
        DebugContextsListener l = contextsListener;
        if (l != null) {
            DebugContext dc = getCachedDebugContext(context);
            l.languageContextInitialized(dc, language);
        }
    }

    @Override
    public void onLanguageContextFinalized(TruffleContext context, LanguageInfo language) {
        DebugContextsListener l = contextsListener;
        if (l != null) {
            DebugContext dc = getCachedDebugContext(context);
            l.languageContextFinalized(dc, language);
        }
    }

    @Override
    public void onLanguageContextDisposed(TruffleContext context, LanguageInfo language) {
        DebugContextsListener l = contextsListener;
        if (l != null) {
            DebugContext dc = getCachedDebugContext(context);
            l.languageContextDisposed(dc, language);
        }
    }

    @Override
    public void onContextClosed(TruffleContext context) {
        DebugContextsListener l = contextsListener;
        if (l != null) {
            DebugContext dc = getCachedDebugContext(context);
            l.contextClosed(dc);
        }
    }

    @Override
    public void onThreadInitialized(TruffleContext context, Thread thread) {
        DebugThreadsListener l = threadsListener;
        if (l != null) {
            DebugContext dc = getCachedDebugContext(context);
            l.threadInitialized(dc, thread);
        }
    }

    @Override
    public void onThreadDisposed(TruffleContext context, Thread thread) {
        DebugThreadsListener l = threadsListener;
        if (l != null) {
            DebugContext dc = getCachedDebugContext(context);
            l.threadDisposed(dc, thread);
        }
    }

}
