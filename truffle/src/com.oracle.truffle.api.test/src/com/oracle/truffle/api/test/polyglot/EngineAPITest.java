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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Language;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.option.OptionProcessorTest.OptionTestInstrument1;
import org.graalvm.polyglot.Value;
import org.junit.Assume;

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
            Engine.newBuilder().err(null);
            fail();
        } catch (NullPointerException e) {
        }
        try {
            Engine.newBuilder().out(null);
            fail();
        } catch (NullPointerException e) {
        }
        try {
            Engine.newBuilder().in(null);
            fail();
        } catch (NullPointerException e) {
        }
        try {
            Engine.newBuilder().option(null, "");
            fail();
        } catch (NullPointerException e) {
        }
        try {
            Engine.newBuilder().option("", null);
            fail();
        } catch (NullPointerException e) {
        }

        try {
            Engine.newBuilder().options(null);
            fail();
        } catch (NullPointerException e) {
        }
        try {
            Map<String, String> options = new HashMap<>();
            options.put("", null);
            Engine.newBuilder().options(options);
            fail();
        } catch (NullPointerException e) {
        }
        Assert.assertNotNull(Engine.newBuilder().useSystemProperties(false).build());
    }

    @Test
    public void getGetLanguageUnknown() {
        Engine engine = Engine.create();

        assertNull(engine.getLanguages().get("someUnknownId"));
        assertFalse(engine.getLanguages().containsKey("someUnknownId"));

        engine.close();
    }

    @Test
    public void getLanguageMeta() {
        Engine engine = Engine.create();

        Language language = engine.getLanguages().get(EngineAPITestLanguage.ID);
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

        Language language = engine.getLanguages().get(EngineAPITestLanguage.ID);
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
    public void testStableOption() {
        try (Engine engine = Engine.newBuilder().option("optiontestinstr1.StringOption1", "Hello").build()) {
            try (Context context = Context.newBuilder().engine(engine).build()) {
                context.enter();
                try {
                    assertEquals("Hello", engine.getInstruments().get("optiontestinstr1").lookup(OptionValues.class).get(OptionTestInstrument1.StringOption1));
                } finally {
                    context.leave();
                }
            }
        }
    }

    @Test
    public void testExperimentalOption() {
        try (Engine engine = Engine.newBuilder().allowExperimentalOptions(true).option("optiontestinstr1.StringOption2", "Allow").build()) {
            try (Context context = Context.newBuilder().engine(engine).build()) {
                context.enter();
                try {
                    assertEquals("Allow", engine.getInstruments().get("optiontestinstr1").lookup(OptionValues.class).get(OptionTestInstrument1.StringOption2));
                } finally {
                    context.leave();
                }
            }
        }
    }

    @Test
    public void testExperimentalOptionException() {
        Assume.assumeFalse(Boolean.getBoolean("polyglot.engine.AllowExperimentalOptions"));
        AbstractPolyglotTest.assertFails(() -> Engine.newBuilder().option("optiontestinstr1.StringOption2", "Hello").build(), IllegalArgumentException.class, e -> {
            assertEquals("Option 'optiontestinstr1.StringOption2' is experimental and must be enabled with allowExperimentalOptions(). Do not use experimental options in production environments.",
                            e.getMessage());
        });
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
        Context context = Context.create(EngineAPITestLanguage.ID);
        Assert.assertTrue(context.initialize(EngineAPITestLanguage.ID));
        try {
            // not allowed to access
            Assert.assertTrue(context.initialize(LanguageSPITestLanguage.ID));
            fail();
        } catch (IllegalArgumentException e) {
        }
        context.close();
    }

    @Test
    public void testLanguageContextInitialize2() {
        Context context = Context.create();
        Assert.assertTrue(context.initialize(EngineAPITestLanguage.ID));
        Assert.assertTrue(context.initialize(LanguageSPITestLanguage.ID));
        context.close();
    }

    @Test
    public void testCreateContextWithAutomaticEngine() {
        Context context = Context.create();
        try {
            Context.newBuilder().engine(context.getEngine()).build();
            fail();
        } catch (IllegalArgumentException e) {

        }
    }

    @Test
    public void testEngineName() {
        Engine engine = Engine.create();
        String implName = engine.getImplementationName();
        assertEquals(Truffle.getRuntime().getName(), engine.getImplementationName());
        String name = Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(0)).getClass().getSimpleName();
        if (name.equals("DefaultCallTarget")) {
            assertEquals(implName, "Interpreted");
        } else if (name.endsWith("OptimizedCallTarget")) {
            assertTrue(implName, implName.equals("GraalVM EE") || implName.equals("GraalVM CE"));
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testListLanguagesDoesNotInvalidateSingleContext() {
        Object prev = resetSingleContextState(false);
        try {
            try (Engine engine = Engine.create()) {
                engine.getLanguages();
            }
            assertTrue(isSingleContextAssumptionValid());
            try (Engine engine = Engine.create()) {
                try (Context ctx = Context.newBuilder().engine(engine).build()) {
                    assertFalse(isSingleContextAssumptionValid());
                }
            }
        } finally {
            restoreSingleContextState(prev);
        }
    }

    @Test
    public void testListInstrumentsDoesNotInvalidateSingleContext() {
        Object prev = resetSingleContextState(false);
        try {
            try (Engine engine = Engine.create()) {
                engine.getInstruments();
            }
            assertTrue(isSingleContextAssumptionValid());
        } finally {
            restoreSingleContextState(prev);
        }
    }

    @Test
    public void testContextWithBoundEngineDoesNotInvalidateSingleContext() {
        Object prev = resetSingleContextState(false);
        try {
            try (Context ctx = Context.create()) {
                ctx.initialize(EngineAPITestLanguage.ID);
            }
            assertTrue(isSingleContextAssumptionValid());
        } finally {
            restoreSingleContextState(prev);
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testListLanguagesAndCreateBoundContextDoNotInvalidateSingleContext() {
        Object prev = resetSingleContextState(false);
        try {
            try (Engine engine = Engine.create()) {
                engine.getLanguages();
            }
            assertTrue(isSingleContextAssumptionValid());
            try (Context ctx = Context.create()) {
                assertTrue(isSingleContextAssumptionValid());
            }
        } finally {
            restoreSingleContextState(prev);
        }
    }

    @Test
    public void testPrepareContextAndUseItAfterReset() throws Exception {
        Object prev = resetSingleContextState(false);
        try {
            Context ctx = Context.newBuilder().build();
            Value mul = ctx.eval("sl", "" +
                            "function mul(a, b) {\n" +
                            "  return a * b;\n" +
                            "}\n" +
                            "function main() {\n" +
                            "  return mul;\n" +
                            "}\n");
            assertFalse(mul.isNull());

            assertEquals(42, mul.execute(7, 6).asInt());

            resetSingleContextState(true);

            assertEquals(72, mul.execute(3, 24).asInt());
        } finally {
            restoreSingleContextState(prev);
        }
    }

    public static Object resetSingleContextState(boolean reuse) {
        try {
            Class<?> c = Class.forName("com.oracle.truffle.polyglot.PolyglotContextImpl");
            Method m = c.getDeclaredMethod("resetSingleContextState", boolean.class);
            m.setAccessible(true);
            return m.invoke(null, reuse);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static void restoreSingleContextState(Object state) {
        try {
            Class<?> c = Class.forName("com.oracle.truffle.polyglot.PolyglotContextImpl");
            Method m = c.getDeclaredMethod("restoreSingleContextState", Object.class);
            m.setAccessible(true);
            m.invoke(null, state);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static boolean isSingleContextAssumptionValid() {
        try {
            Class<?> c = Class.forName("com.oracle.truffle.polyglot.PolyglotContextImpl");
            Method m = c.getDeclaredMethod("isSingleContextAssumptionValid");
            m.setAccessible(true);
            return (Boolean) m.invoke(null);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
