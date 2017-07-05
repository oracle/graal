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

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.junit.Test;

import com.oracle.truffle.api.test.polyglot.LanguageSPITestLanguage.LanguageContext;

public class LanguageSPITest {

    static LanguageContext langContext;

    @Test
    public void testContextClose() {
        langContext = null;
        Engine engine = Engine.create();

        Context context = engine.getLanguage(LanguageSPITestLanguage.ID).createContext();
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
        context.initialize(LanguageSPITestLanguage.ID);

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
        c.initialize(LanguageSPITestLanguage.ID);
        LanguageContext context1 = langContext;

        engine.createContext().initialize(LanguageSPITestLanguage.ID);
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
        context.initialize(LanguageSPITestLanguage.ID);

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
                context.initialize(LanguageSPITestLanguage.ID);
            }
        });
        t.start();
        t.join(10000);
        engine.close();
        assertEquals(1, langContext.disposeCalled);
    }

}
