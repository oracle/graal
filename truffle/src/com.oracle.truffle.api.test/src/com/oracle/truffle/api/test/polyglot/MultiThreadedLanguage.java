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
package com.oracle.truffle.api.test.polyglot;

import java.util.function.Function;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.polyglot.MultiThreadedLanguage.LanguageContext;

@TruffleLanguage.Registration(id = MultiThreadedLanguage.ID, name = MultiThreadedLanguage.ID)
public class MultiThreadedLanguage extends TruffleLanguage<LanguageContext> {

    static final String ID = "MultiThreadedLanguage";

    static final ThreadLocal<Function<Env, Object>> runinside = new ThreadLocal<>();
    static volatile Function<ThreadRequest, Void> initializeThread;
    static volatile Function<ThreadRequest, Boolean> isThreadAccessAllowed;
    static volatile Function<ThreadRequest, Void> initializeMultiThreading;
    static volatile Function<ThreadRequest, Void> disposeThread;
    static volatile LanguageContext langContext;

    static class LanguageContext {

        int disposeCalled;
        final Env env;

        LanguageContext(Env env) {
            this.env = env;
        }

    }

    static class ThreadRequest {

        final LanguageContext context;
        final Thread thread;
        final boolean singleThreaded;

        ThreadRequest(LanguageContext context, Thread thread, boolean singleThreaded) {
            this.context = context;
            this.thread = thread;
            this.singleThreaded = singleThreaded;
        }

    }

    public static LanguageContext getContext() {
        return getCurrentContext(MultiThreadedLanguage.class);
    }

    @Override
    protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
        if (isThreadAccessAllowed != null) {
            return isThreadAccessAllowed.apply(new ThreadRequest(null, thread, singleThreaded));
        } else {
            return super.isThreadAccessAllowed(thread, singleThreaded);
        }
    }

    @Override
    protected void initializeMultiThreading(LanguageContext context) {
        if (initializeMultiThreading != null) {
            initializeMultiThreading.apply(new ThreadRequest(context, null, false));
        } else {
            super.initializeMultiThreading(context);
        }
    }

    @Override
    protected void initializeThread(LanguageContext context, Thread thread) {
        if (initializeThread != null) {
            initializeThread.apply(new ThreadRequest(context, thread, false));
        } else {
            super.initializeThread(context, thread);
        }
    }

    @Override
    protected void disposeThread(LanguageContext context, Thread thread) {
        if (disposeThread != null) {
            disposeThread.apply(new ThreadRequest(context, thread, false));
        } else {
            super.disposeThread(context, thread);
        }
    }

    @Override
    protected CallTarget parse(ParsingRequest request) throws Exception {
        return Truffle.getRuntime().createCallTarget(new RootNode(this) {
            @Override
            public Object execute(VirtualFrame frame) {
                Object result = run();
                if (result == null) {
                    result = "null result";
                }
                return getContext().env.asGuestValue(result);
            }

            @TruffleBoundary
            private Object run() {
                if (runinside.get() != null) {
                    try {
                        return runinside.get().apply(getContext().env);
                    } finally {
                        runinside.set(null);
                    }
                }
                return "null result";
            }
        });
    }

    @Override
    protected LanguageContext createContext(Env env) {
        return langContext = new LanguageContext(env);
    }

    @Override
    protected void disposeContext(LanguageContext context) {
        context.disposeCalled++;
    }

    @Override
    protected boolean isObjectOfLanguage(Object object) {
        return false;
    }

}
