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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.graalvm.options.OptionDescriptor;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Language;
import org.junit.Assert;
import org.junit.Test;

public class EngineAPITest {

    @Test
    public void testCreateAndDispose() {
        Engine engine = Engine.create();
        engine.close();
    }

    @Test
    public void testBuilder() {
        assertNotNull(Engine.newBuilder().build());
        try {
            Engine.newBuilder().setErr(null);
            fail();
        } catch (NullPointerException e) {
        }
        try {
            Engine.newBuilder().setOut(null);
            fail();
        } catch (NullPointerException e) {
        }
        try {
            Engine.newBuilder().setIn(null);
            fail();
        } catch (NullPointerException e) {
        }
        try {
            Engine.newBuilder().setOption(null, "");
            fail();
        } catch (NullPointerException e) {
        }
        try {
            Engine.newBuilder().setOption("", null);
            fail();
        } catch (NullPointerException e) {
        }

        try {
            Engine.newBuilder().setOptions(null);
            fail();
        } catch (NullPointerException e) {
        }
        try {
            Map<String, String> options = new HashMap<>();
            options.put("", null);
            Engine.newBuilder().setOptions(options);
            fail();
        } catch (NullPointerException e) {
        }
        Assert.assertNotNull(Engine.newBuilder().setUseSystemProperties(false).build());
    }

    @Test
    public void getGetLanguageUnknown() {
        Engine engine = Engine.create();

        try {
            engine.getLanguage("someUnknownId");
            fail();
        } catch (IllegalArgumentException e) {
        }

        assertNull(engine.getLanguages().get("someUnknownId"));
        assertFalse(engine.getLanguages().containsKey("someUnknownId"));

        engine.close();
    }

    @Test
    public void getLanguageMeta() {
        Engine engine = Engine.create();

        Language language = engine.getLanguage(EngineAPITestLanguage.ID);
        assertNotNull(language);
        assertEquals(EngineAPITestLanguage.ID, language.getId());
        assertEquals(EngineAPITestLanguage.NAME, language.getName());
        assertEquals(EngineAPITestLanguage.VERSION, language.getVersion());
        assertEquals(EngineAPITestLanguage.IMPL_NAME, language.getImplementationName());

        assertSame(language, engine.getLanguages().get(EngineAPITestLanguage.ID));

        engine.close();
    }

    @Test
    public void getLanguageOptions() {
        Engine engine = Engine.create();

        Language language = engine.getLanguage(EngineAPITestLanguage.ID);
        OptionDescriptor descriptor1 = language.getOptions().get(EngineAPITestLanguage.Option1_NAME);
        OptionDescriptor descriptor2 = language.getOptions().get(EngineAPITestLanguage.Option2_NAME);
        OptionDescriptor descriptor3 = language.getOptions().get(EngineAPITestLanguage.Option3_NAME);

        assertSame(EngineAPITestLanguage.Option1, descriptor1.getKey());
        assertEquals(EngineAPITestLanguage.Option1_NAME, descriptor1.getName());
        assertEquals(EngineAPITestLanguage.Option1_CATEGORY, descriptor1.getCategory());
        assertEquals(EngineAPITestLanguage.Option1_DEPRECATED, descriptor1.isDeprecated());
        assertEquals(EngineAPITestLanguage.Option1_HELP, descriptor1.getHelp());

        assertSame(EngineAPITestLanguage.Option2, descriptor2.getKey());
        assertEquals(EngineAPITestLanguage.Option2_NAME, descriptor2.getName());
        assertEquals(EngineAPITestLanguage.Option2_CATEGORY, descriptor2.getCategory());
        assertEquals(EngineAPITestLanguage.Option2_DEPRECATED, descriptor2.isDeprecated());
        assertEquals(EngineAPITestLanguage.Option2_HELP, descriptor2.getHelp());

        assertSame(EngineAPITestLanguage.Option3, descriptor3.getKey());
        assertEquals(EngineAPITestLanguage.Option3_NAME, descriptor3.getName());
        assertEquals(EngineAPITestLanguage.Option3_CATEGORY, descriptor3.getCategory());
        assertEquals(EngineAPITestLanguage.Option3_DEPRECATED, descriptor3.isDeprecated());
        assertEquals(EngineAPITestLanguage.Option3_HELP, descriptor3.getHelp());

        engine.close();
    }

    @Test
    public void testEngineCloseAsnyc() throws InterruptedException, ExecutionException {
        Engine engine = Engine.create();
        ExecutorService executor = Executors.newFixedThreadPool(1);
        executor.submit(new Callable<Void>() {
            public Void call() throws Exception {
                engine.close();
                return null;
            }
        }).get();
    }

    @Test
    public void testLanguageContextInitialize1() {
        Engine engine = Engine.create();
        Context context = engine.getLanguage(EngineAPITestLanguage.ID).createContext();
        Assert.assertFalse(context.initialize(EngineAPITestLanguage.ID));
        try {
            // not allowed to access
            Assert.assertTrue(context.initialize(LanguageSPITestLanguage.ID));
            fail();
        } catch (IllegalStateException e) {
        }
        engine.close();
    }

    @Test
    public void testLanguageContextInitialize2() {
        Engine engine = Engine.create();
        Context context = engine.getLanguage(EngineAPITestLanguage.ID).createPolyglotContext();
        Assert.assertFalse(context.initialize(EngineAPITestLanguage.ID));
        Assert.assertTrue(context.initialize(LanguageSPITestLanguage.ID));
        engine.close();
    }

    @Test
    public void testLanguageContextInitialize3() {
        Engine engine = Engine.create();
        Context context = engine.getLanguage(EngineAPITestLanguage.ID).createContextBuilder().setPolyglot(true).build();
        Assert.assertFalse(context.initialize(EngineAPITestLanguage.ID));
        Assert.assertTrue(context.initialize(LanguageSPITestLanguage.ID));
        engine.close();
    }

    @Test
    public void testEngineContextInitialize1() {
        Engine engine = Engine.create();
        Context context = engine.createContext();
        Assert.assertFalse(context.initialize(EngineAPITestLanguage.ID));
        try {
            // not allowed to access
            Assert.assertTrue(context.initialize(LanguageSPITestLanguage.ID));
            fail();
        } catch (IllegalStateException e) {
        }
        engine.close();
    }

    @Test
    public void testEngineContextInitialize2() {
        Engine engine = Engine.create();
        Context context = engine.getLanguage(EngineAPITestLanguage.ID).createPolyglotContext();
        Assert.assertFalse(context.initialize(EngineAPITestLanguage.ID));
        Assert.assertTrue(context.initialize(LanguageSPITestLanguage.ID));
        engine.close();
    }

    @Test
    public void testEngineContextInitialize3() {
        Engine engine = Engine.create();
        Context context = engine.getLanguage(EngineAPITestLanguage.ID).createContextBuilder().setPolyglot(true).build();
        Assert.assertFalse(context.initialize(EngineAPITestLanguage.ID));
        Assert.assertTrue(context.initialize(LanguageSPITestLanguage.ID));
        engine.close();
    }

}
