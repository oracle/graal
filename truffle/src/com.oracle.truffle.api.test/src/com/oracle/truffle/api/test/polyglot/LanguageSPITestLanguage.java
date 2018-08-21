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
    protected void disposeContext(LanguageContext context) {
        assertSame(getContext(), context);
        assertSame(context, getContextReference().get());

        assertSame(context, new RootNode(this) {
            @Override
            public Object execute(VirtualFrame frame) {
                return null;
            }
        }.getLanguage(LanguageSPITestLanguage.class).getContextReference().get());

        context.disposeCalled++;
    }

    @Override
    protected boolean isObjectOfLanguage(Object object) {
        return false;
    }

}
