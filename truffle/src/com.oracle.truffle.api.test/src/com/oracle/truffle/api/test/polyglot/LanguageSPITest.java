/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

public class LanguageSPITest {

    private static final String LANGUAGE_SPI_TEST = "LanguageSPITest";

    static LanguageContext langContext;

    @Test
    public void testContextClose() {
        langContext = null;
        Engine engine = Engine.create();

        Context context = engine.getLanguage(LANGUAGE_SPI_TEST).createContext();
        assertNotNull(langContext);
        assertEquals(0, langContext.disposeCalled);
        context.close();
        engine.close();
        assertEquals(1, langContext.disposeCalled);
    }

    @Test
    public void testPolyglotClose() {
        langContext = null;
        Engine engine = Engine.create();

        Context context = engine.createContext();
        context.initialize(LANGUAGE_SPI_TEST);

        assertNotNull(langContext);

        assertEquals(0, langContext.disposeCalled);
        context.close();
        engine.close();
        assertEquals(1, langContext.disposeCalled);
    }

    @Test
    public void testImplicitClose() {
        Engine engine = Engine.create();
        langContext = null;
        Context c = engine.createContext();
        c.initialize(LANGUAGE_SPI_TEST);
        LanguageContext context1 = langContext;

        engine.createContext().initialize(LANGUAGE_SPI_TEST);
        LanguageContext context2 = langContext;

        c.close();
        engine.close();
        assertEquals(1, context1.disposeCalled);
        assertEquals(1, context2.disposeCalled);
    }

    @Test
    public void testImplicitCloseFromOtherThread() throws InterruptedException {
        Engine engine = Engine.create();
        Context context = engine.createContext();
        langContext = null;
        context.initialize(LANGUAGE_SPI_TEST);

        Thread t = new Thread(new Runnable() {
            public void run() {
                engine.close();
            }
        });
        t.start();
        t.join(10000);
        assertEquals(1, langContext.disposeCalled);
    }

    @Test
    public void testCreateFromOtherThreadAndCloseFromMain() throws InterruptedException {
        Engine engine = Engine.create();
        langContext = null;
        Thread t = new Thread(new Runnable() {
            public void run() {
                Context context = engine.createContext();
                context.initialize(LANGUAGE_SPI_TEST);
            }
        });
        t.start();
        t.join(10000);
        engine.close();
        assertEquals(1, langContext.disposeCalled);
    }

    private static class LanguageContext {

        int disposeCalled;

    }

    @TruffleLanguage.Registration(id = LANGUAGE_SPI_TEST, name = LANGUAGE_SPI_TEST, version = "1.0", mimeType = LANGUAGE_SPI_TEST)
    public static class LanguageSPITestLanguage extends TruffleLanguage<LanguageContext> {

        public static LanguageContext getContext() {
            return getCurrentContext(LanguageSPITestLanguage.class);
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            return null;
        }

        @Override
        protected LanguageContext createContext(Env env) {
            langContext = new LanguageContext();
            return langContext;
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
        protected Object lookupSymbol(LanguageContext context, String symbolName) {
            return super.lookupSymbol(context, symbolName);
        }

        @Override
        protected Object getLanguageGlobal(LanguageContext context) {
            return null;
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            return false;
        }

    }

}
