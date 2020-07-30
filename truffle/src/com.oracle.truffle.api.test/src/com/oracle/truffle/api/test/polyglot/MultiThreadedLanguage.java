/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

}
