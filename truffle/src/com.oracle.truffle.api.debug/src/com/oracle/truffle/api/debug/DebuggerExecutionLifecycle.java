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

    private final Debugger debugger;
    private final Instrumenter lifecycleInstrumenter;
    private EventBinding<ContextsListener> contextsBinding;
    private volatile DebugContextsListener contextsListener;
    private EventBinding<ThreadsListener> threadsBinding;
    private volatile DebugThreadsListener threadsListener;
    private final Map<TruffleContext, DebugContext> contextMap = new ConcurrentHashMap<>();

    DebuggerExecutionLifecycle(Debugger debugger) {
        this.debugger = debugger;
        this.lifecycleInstrumenter = debugger.getEnv().getInstrumenter();
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
        return debugger;
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
