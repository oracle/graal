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

import static org.junit.Assert.assertSame;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextPolicy;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.polyglot.LanguageSPITestLanguage.LanguageContext;

@TruffleLanguage.Registration(id = LanguageSPITestLanguage.ID, name = LanguageSPITestLanguage.ID, version = "1.0", contextPolicy = ContextPolicy.SHARED)
public class LanguageSPITestLanguage extends TruffleLanguage<LanguageContext> {

    static final String ID = "LanguageSPITest";

    static Function<Env, Object> runinside;

    static final AtomicInteger instanceCount = new AtomicInteger();

    static class LanguageContext {
        private volatile boolean initialized;
        int disposeCalled;
        Env env;
        Map<String, Object> config;

    }

    public LanguageSPITestLanguage() {
        instanceCount.incrementAndGet();
    }

    public static LanguageContext getContext() {
        return getCurrentContext(LanguageSPITestLanguage.class);
    }

    @Override
    protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
        return true;
    }

    @Override
    protected CallTarget parse(ParsingRequest request) throws Exception {
        return Truffle.getRuntime().createCallTarget(new RootNode(this) {
            @Override
            public Object execute(VirtualFrame frame) {
                getContext(); // We must have the context here
                Object result = "null result";
                if (runinside != null) {
                    try {
                        result = runinside.apply(getContext().env);
                    } finally {
                        runinside = null;
                    }
                }
                if (result == null) {
                    result = "null result";
                }
                return result;
            }
        });
    }

    @Override
    protected LanguageContext createContext(Env env) {
        LanguageSPITest.langContext = new LanguageContext();
        LanguageSPITest.langContext.env = env;
        LanguageSPITest.langContext.config = env.getConfig();
        return LanguageSPITest.langContext;
    }

    @Override
    protected void initializeContext(LanguageContext context) throws Exception {
        context.initialized = true;
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void disposeContext(LanguageContext context) {
        if (context.initialized) {
            assertSame(getContext(), context);
            assertSame(context, getContextReference().get());

            assertSame(context, new RootNode(this) {
                @Override
                public Object execute(VirtualFrame frame) {
                    return lookupContextReference(LanguageSPITestLanguage.class).get();
                }
            }.execute(null));
        }

        context.disposeCalled++;
    }

}
