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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ContextPreInitializationTest {

    static final String FIRST = "ContextPreInitializationFirst";
    static final String SECOND = "ContextPreInitializationSecond";
    private static final String SYS_OPTION1_KEY = "polyglot." + FIRST + ".Option1";
    private static final String SYS_OPTION2_KEY = "polyglot." + FIRST + ".Option2";
    private static final List<CountingContext> emittedContexts = new ArrayList<>();
    private static final Set<String> patchableLanguages = new HashSet<>();

    @Before
    public void setUp() throws Exception {
        // Initialize IMPL
        Class.forName("org.graalvm.polyglot.Engine$ImplHolder", true, ContextPreInitializationTest.class.getClassLoader());
        resetSystemPropertiesOptions();
    }

    @After
    public void tearDown() throws Exception {
        resetSystemPropertiesOptions();
        patchableLanguages.clear();
        emittedContexts.clear();
    }

    @Test
    public void testNoLanguagePreInitialization() throws Exception {
        setPatchable();
        doContextPreinitialize();
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        assertEquals(0, contexts.size());
        final Context ctx = Context.create();
        final Value res = ctx.eval(Source.create(FIRST, "test"));
        assertEquals("test", res.asString());
        contexts = new ArrayList<>(emittedContexts);
        assertEquals(1, contexts.size());
        CountingContext context = findContext(FIRST, contexts);
        assertNotNull(context);
        assertEquals(1, context.createContext);
        assertEquals(1, context.initializeContext);
        assertEquals(0, context.patchContext);
        assertEquals(0, context.disposeContext);
        assertEquals(1, context.initializeThread);
        assertEquals(0, context.disposeThread);
        ctx.close();
        contexts = new ArrayList<>(emittedContexts);
        assertEquals(1, contexts.size());
        assertEquals(1, context.createContext);
        assertEquals(1, context.initializeContext);
        assertEquals(0, context.patchContext);
        assertEquals(1, context.disposeContext);
        assertEquals(1, context.initializeThread);
        assertEquals(1, context.disposeThread);
    }

    @Test
    public void testSingleLanguagePreInitialization() throws Exception {
        setPatchable(FIRST);
        doContextPreinitialize(FIRST);
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        assertEquals(1, contexts.size());
        final CountingContext firstLangCtx = findContext(FIRST, contexts);
        assertNotNull(firstLangCtx);
        assertEquals(1, firstLangCtx.createContext);
        assertEquals(1, firstLangCtx.initializeContext);
        assertEquals(0, firstLangCtx.patchContext);
        assertEquals(0, firstLangCtx.disposeContext);
        assertEquals(0, firstLangCtx.initializeThread);
        assertEquals(0, firstLangCtx.disposeThread);
        final Context ctx = Context.create();
        Value res = ctx.eval(Source.create(FIRST, "test"));
        assertEquals("test", res.asString());
        contexts = new ArrayList<>(emittedContexts);
        assertEquals(1, contexts.size());
        assertEquals(1, firstLangCtx.createContext);
        assertEquals(1, firstLangCtx.initializeContext);
        assertEquals(1, firstLangCtx.patchContext);
        assertEquals(0, firstLangCtx.disposeContext);
        assertEquals(1, firstLangCtx.initializeThread);
        assertEquals(0, firstLangCtx.disposeThread);
        res = ctx.eval(Source.create(SECOND, "test"));
        assertEquals("test", res.asString());
        contexts = new ArrayList<>(emittedContexts);
        assertEquals(2, contexts.size());
        final CountingContext secondLangCtx = findContext(SECOND, contexts);
        assertNotNull(secondLangCtx);
        assertEquals(1, firstLangCtx.createContext);
        assertEquals(1, firstLangCtx.initializeContext);
        assertEquals(1, firstLangCtx.patchContext);
        assertEquals(0, firstLangCtx.disposeContext);
        assertEquals(1, firstLangCtx.initializeThread);
        assertEquals(0, firstLangCtx.disposeThread);
        assertEquals(1, secondLangCtx.createContext);
        assertEquals(1, secondLangCtx.initializeContext);
        assertEquals(0, secondLangCtx.patchContext);
        assertEquals(0, secondLangCtx.disposeContext);
        assertEquals(1, secondLangCtx.initializeThread);
        assertEquals(0, secondLangCtx.disposeThread);
        ctx.close();
        contexts = new ArrayList<>(emittedContexts);
        assertEquals(2, contexts.size());
        assertEquals(1, firstLangCtx.createContext);
        assertEquals(1, firstLangCtx.initializeContext);
        assertEquals(1, firstLangCtx.patchContext);
        assertEquals(1, firstLangCtx.disposeContext);
        assertEquals(1, firstLangCtx.initializeThread);
        assertEquals(1, firstLangCtx.disposeThread);
        assertEquals(1, secondLangCtx.createContext);
        assertEquals(1, secondLangCtx.initializeContext);
        assertEquals(0, secondLangCtx.patchContext);
        assertEquals(1, secondLangCtx.disposeContext);
        assertEquals(1, secondLangCtx.initializeThread);
        assertEquals(1, secondLangCtx.disposeThread);
    }

    @Test
    public void testMoreLanguagesPreInitialization() throws Exception {
        setPatchable(FIRST, SECOND);
        doContextPreinitialize(FIRST, SECOND);
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        assertEquals(2, contexts.size());
        final CountingContext firstLangCtx = findContext(FIRST, contexts);
        assertNotNull(firstLangCtx);
        final CountingContext secondLangCtx = findContext(SECOND, contexts);
        assertNotNull(secondLangCtx);
        assertEquals(1, firstLangCtx.createContext);
        assertEquals(1, firstLangCtx.initializeContext);
        assertEquals(0, firstLangCtx.patchContext);
        assertEquals(0, firstLangCtx.disposeContext);
        assertEquals(0, firstLangCtx.initializeThread);
        assertEquals(0, firstLangCtx.disposeThread);
        assertEquals(1, secondLangCtx.createContext);
        assertEquals(1, secondLangCtx.initializeContext);
        assertEquals(0, secondLangCtx.patchContext);
        assertEquals(0, secondLangCtx.disposeContext);
        assertEquals(0, secondLangCtx.initializeThread);
        assertEquals(0, secondLangCtx.disposeThread);
        final Context ctx = Context.create();
        Value res = ctx.eval(Source.create(FIRST, "test"));
        assertEquals("test", res.asString());
        contexts = new ArrayList<>(emittedContexts);
        assertEquals(2, contexts.size());
        assertEquals(1, firstLangCtx.createContext);
        assertEquals(1, firstLangCtx.initializeContext);
        assertEquals(1, firstLangCtx.patchContext);
        assertEquals(0, firstLangCtx.disposeContext);
        assertEquals(1, firstLangCtx.initializeThread);
        assertEquals(0, firstLangCtx.disposeThread);
        assertEquals(1, secondLangCtx.createContext);
        assertEquals(1, secondLangCtx.initializeContext);
        assertEquals(1, secondLangCtx.patchContext);
        assertEquals(0, secondLangCtx.disposeContext);
        assertEquals(1, secondLangCtx.initializeThread);
        assertEquals(0, secondLangCtx.disposeThread);
        res = ctx.eval(Source.create(SECOND, "test"));
        assertEquals("test", res.asString());
        contexts = new ArrayList<>(emittedContexts);
        assertEquals(2, contexts.size());
        assertEquals(1, firstLangCtx.createContext);
        assertEquals(1, firstLangCtx.initializeContext);
        assertEquals(1, firstLangCtx.patchContext);
        assertEquals(0, firstLangCtx.disposeContext);
        assertEquals(1, firstLangCtx.initializeThread);
        assertEquals(0, firstLangCtx.disposeThread);
        assertEquals(1, secondLangCtx.createContext);
        assertEquals(1, secondLangCtx.initializeContext);
        assertEquals(1, secondLangCtx.patchContext);
        assertEquals(0, secondLangCtx.disposeContext);
        assertEquals(1, secondLangCtx.initializeThread);
        assertEquals(0, secondLangCtx.disposeThread);
        ctx.close();
        contexts = new ArrayList<>(emittedContexts);
        assertEquals(2, contexts.size());
        assertEquals(1, firstLangCtx.createContext);
        assertEquals(1, firstLangCtx.initializeContext);
        assertEquals(1, firstLangCtx.patchContext);
        assertEquals(1, firstLangCtx.disposeContext);
        assertEquals(1, firstLangCtx.initializeThread);
        assertEquals(1, firstLangCtx.disposeThread);
        assertEquals(1, secondLangCtx.createContext);
        assertEquals(1, secondLangCtx.initializeContext);
        assertEquals(1, secondLangCtx.patchContext);
        assertEquals(1, secondLangCtx.disposeContext);
        assertEquals(1, secondLangCtx.initializeThread);
        assertEquals(1, secondLangCtx.disposeThread);
    }

    @Test
    public void testMoreLanguagesPreInitializationFailedPatch() throws Exception {
        setPatchable(FIRST);
        doContextPreinitialize(FIRST, SECOND);
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        assertEquals(2, contexts.size());
        final CountingContext firstLangCtx = findContext(FIRST, contexts);
        assertNotNull(firstLangCtx);
        final CountingContext secondLangCtx = findContext(SECOND, contexts);
        assertNotNull(secondLangCtx);
        assertEquals(1, firstLangCtx.createContext);
        assertEquals(1, firstLangCtx.initializeContext);
        assertEquals(0, firstLangCtx.patchContext);
        assertEquals(0, firstLangCtx.disposeContext);
        assertEquals(0, firstLangCtx.initializeThread);
        assertEquals(0, firstLangCtx.disposeThread);
        assertEquals(1, secondLangCtx.createContext);
        assertEquals(1, secondLangCtx.initializeContext);
        assertEquals(0, secondLangCtx.patchContext);
        assertEquals(0, secondLangCtx.disposeContext);
        assertEquals(0, secondLangCtx.initializeThread);
        assertEquals(0, secondLangCtx.disposeThread);
        final Context ctx = Context.create();
        Value res = ctx.eval(Source.create(FIRST, "test"));
        assertEquals("test", res.asString());
        contexts = new ArrayList<>(emittedContexts);
        assertEquals(3, contexts.size());
        Collection<? extends CountingContext> firstLangCtxs = findContexts(FIRST, contexts);
        firstLangCtxs.remove(firstLangCtx);
        assertFalse(firstLangCtxs.isEmpty());
        final CountingContext firstLangCtx2 = firstLangCtxs.iterator().next();
        assertEquals(1, firstLangCtx.createContext);
        assertEquals(1, firstLangCtx.initializeContext);
        // The firstLangCtx.patchContext is undefined - depends on languages order
        assertEquals(1, firstLangCtx.disposeContext);
        assertEquals(1, firstLangCtx.initializeThread); // Close initializes thread
        assertEquals(1, firstLangCtx.disposeThread);    // Close initializes thread
        assertEquals(1, secondLangCtx.createContext);
        assertEquals(1, secondLangCtx.initializeContext);
        assertEquals(1, secondLangCtx.patchContext);
        assertEquals(1, secondLangCtx.disposeContext);
        assertEquals(1, secondLangCtx.initializeThread);    // Close initializes thread
        assertEquals(1, secondLangCtx.disposeThread);       // Close initializes thread
        assertEquals(1, firstLangCtx2.createContext);
        assertEquals(1, firstLangCtx2.initializeContext);
        assertEquals(0, firstLangCtx2.patchContext);
        assertEquals(0, firstLangCtx2.disposeContext);
        assertEquals(1, firstLangCtx2.initializeThread);
        assertEquals(0, firstLangCtx2.disposeThread);
        ctx.close();
        assertEquals(3, contexts.size());
        assertEquals(1, firstLangCtx.createContext);
        assertEquals(1, firstLangCtx.initializeContext);
        // The firstLangCtx.patchContext is undefined - depends on languages order
        assertEquals(1, firstLangCtx.disposeContext);
        assertEquals(1, firstLangCtx.initializeThread);
        assertEquals(1, firstLangCtx.disposeThread);
        assertEquals(1, secondLangCtx.createContext);
        assertEquals(1, secondLangCtx.initializeContext);
        assertEquals(1, secondLangCtx.patchContext);
        assertEquals(1, secondLangCtx.disposeContext);
        assertEquals(1, secondLangCtx.initializeThread);
        assertEquals(1, secondLangCtx.disposeThread);
        assertEquals(1, firstLangCtx2.createContext);
        assertEquals(1, firstLangCtx2.initializeContext);
        assertEquals(0, firstLangCtx2.patchContext);
        assertEquals(1, firstLangCtx.disposeContext);
        assertEquals(1, firstLangCtx.initializeThread);
        assertEquals(1, firstLangCtx.disposeThread);
    }

    @Test
    public void testSystemPropertiesOptionsSuccessfulPatch() throws Exception {
        System.setProperty(SYS_OPTION1_KEY, "true");
        setPatchable(FIRST);
        doContextPreinitialize(FIRST);
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        final CountingContext firstLangCtx = findContext(FIRST, contexts);
        assertNotNull(firstLangCtx);
        assertTrue(firstLangCtx.optionValues.get(FirstLanguage.Option1));
        assertFalse(firstLangCtx.optionValues.get(FirstLanguage.Option2));
        firstLangCtx.optionValues.clear();
        System.getProperties().remove(SYS_OPTION1_KEY);
        System.setProperty(SYS_OPTION2_KEY, "true");
        Context ctx = Context.create();
        Value res = ctx.eval(Source.create(FIRST, "test"));
        assertEquals("test", res.asString());
        assertTrue(firstLangCtx.optionValues.get(FirstLanguage.Option1));
        assertFalse(firstLangCtx.optionValues.get(FirstLanguage.Option2));
        ctx.close();
        ctx = Context.create();
        res = ctx.eval(Source.create(FIRST, "test"));
        assertEquals("test", res.asString());
        assertTrue(firstLangCtx.optionValues.get(FirstLanguage.Option1));
        assertFalse(firstLangCtx.optionValues.get(FirstLanguage.Option2));
        ctx.close();
    }

    @Test
    public void testSystemPropertiesOptionsFailedPatch() throws Exception {
        System.setProperty(SYS_OPTION1_KEY, "true");
        setPatchable();
        doContextPreinitialize(FIRST);
        List<CountingContext> contexts = new ArrayList<>(emittedContexts);
        final CountingContext firstLangCtx = findContext(FIRST, contexts);
        assertNotNull(firstLangCtx);
        assertTrue(firstLangCtx.optionValues.get(FirstLanguage.Option1));
        assertFalse(firstLangCtx.optionValues.get(FirstLanguage.Option2));
        firstLangCtx.optionValues.clear();
        System.getProperties().remove(SYS_OPTION1_KEY);
        System.setProperty(SYS_OPTION2_KEY, "true");
        final Context ctx = Context.create();
        Value res = ctx.eval(Source.create(FIRST, "test"));
        assertEquals("test", res.asString());
        contexts = new ArrayList<>(emittedContexts);
        contexts.remove(firstLangCtx);
        final CountingContext firstLangCtx2 = findContext(FIRST, contexts);
        assertNotNull(firstLangCtx2);
        assertTrue(firstLangCtx2.optionValues.get(FirstLanguage.Option1));
        assertFalse(firstLangCtx2.optionValues.get(FirstLanguage.Option2));
        ctx.close();

    }

    private static void resetSystemPropertiesOptions() throws ReflectiveOperationException {
        System.getProperties().remove("polyglot.engine.PreinitializeContexts");
        System.getProperties().remove(SYS_OPTION1_KEY);
        System.getProperties().remove(SYS_OPTION2_KEY);
        final Class<?> engineImplClz = Class.forName("com.oracle.truffle.api.vm.PolyglotEngineImpl", true, ContextPreInitializationTest.class.getClassLoader());
        final Field systemPropertiesOptions = engineImplClz.getDeclaredField("systemPropertiesOptions");
        systemPropertiesOptions.setAccessible(true);
        systemPropertiesOptions.set(null, null);
    }

    private static void doContextPreinitialize(String... languages) throws ReflectiveOperationException {
        final Class<?> holderClz = Class.forName("org.graalvm.polyglot.Engine$ImplHolder", true, ContextPreInitializationTest.class.getClassLoader());
        final Field implFld = holderClz.getDeclaredField("IMPL");
        implFld.setAccessible(true);
        final AbstractPolyglotImpl polyglot = (AbstractPolyglotImpl) implFld.get(null);
        assertNotNull(polyglot);
        final StringBuilder languagesOptionValue = new StringBuilder();
        for (String language : languages) {
            languagesOptionValue.append(language).append(',');
        }
        if (languagesOptionValue.length() > 0) {
            languagesOptionValue.replace(languagesOptionValue.length() - 1, languagesOptionValue.length(), "");
            System.setProperty(
                            "polyglot.engine.PreinitializeContexts",
                            languagesOptionValue.toString());
        }
        polyglot.preInitializeEngine();
    }

    private Collection<? extends CountingContext> findContexts(
                    final String languageId,
                    Collection<? extends CountingContext> contexts) {
        final Set<CountingContext> result = new HashSet<>();
        for (CountingContext context : contexts) {
            if (context.getLanguageId().equals(languageId)) {
                result.add(context);
            }
        }
        return result;
    }

    private CountingContext findContext(
                    final String languageId,
                    Collection<? extends CountingContext> contexts) {
        final Collection<? extends CountingContext> found = findContexts(languageId, contexts);
        return found.isEmpty() ? null : found.iterator().next();
    }

    static void setPatchable(String... languageIds) {
        patchableLanguages.clear();
        Collections.addAll(patchableLanguages, languageIds);
    }

    static class CountingContext {
        private final String id;
        int createContext = 0;
        int initializeContext = 0;
        int patchContext = 0;
        int disposeContext = 0;
        int initializeThread = 0;
        int disposeThread = 0;
        final Map<OptionKey<Boolean>, Boolean> optionValues;

        CountingContext(final String id) {
            this.id = id;
            this.optionValues = new HashMap<>();
        }

        String getLanguageId() {
            return id;
        }
    }

    static class BaseLanguage extends TruffleLanguage<CountingContext> {

        @Override
        protected CountingContext createContext(TruffleLanguage.Env env) {
            final String languageId = new RootNode(this) {
                @Override
                public Object execute(VirtualFrame frame) {
                    return null;
                }
            }.getLanguageInfo().getId();
            final CountingContext ctx = new CountingContext(languageId);
            ctx.createContext++;
            emittedContexts.add(ctx);
            return ctx;
        }

        @Override
        protected void initializeContext(CountingContext context) throws Exception {
            context.initializeContext++;
            super.initializeContext(context);
        }

        @Override
        protected boolean patchContext(CountingContext context, TruffleLanguage.Env newEnv) {
            context.patchContext++;
            return patchableLanguages.contains(context.getLanguageId());
        }

        @Override
        protected void disposeContext(CountingContext context) {
            context.disposeContext++;
        }

        @Override
        protected void initializeThread(CountingContext context, Thread thread) {
            context.initializeThread++;
        }

        @Override
        protected void disposeThread(CountingContext context, Thread thread) {
            context.disposeThread++;
        }

        @Override
        protected Object getLanguageGlobal(CountingContext context) {
            return null;
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            return false;
        }

        @Override
        protected CallTarget parse(TruffleLanguage.ParsingRequest request) throws Exception {
            final CharSequence result = request.getSource().getCharacters();
            return Truffle.getRuntime().createCallTarget(new RootNode(this) {
                @Override
                public Object execute(VirtualFrame frame) {
                    return result;
                }
            });
        }
    }

    @TruffleLanguage.Registration(id = FIRST, name = FIRST, version = "1.0", mimeType = FIRST)
    public static final class FirstLanguage extends BaseLanguage {
        @Option(category = OptionCategory.USER, help = "Option 1") public static final OptionKey<Boolean> Option1 = new OptionKey<>(false);
        @Option(category = OptionCategory.USER, help = "Option 2") public static final OptionKey<Boolean> Option2 = new OptionKey<>(false);

        @Override
        protected CountingContext createContext(Env env) {
            final CountingContext ctx = super.createContext(env);
            ctx.optionValues.put(Option1, env.getOptions().get(Option1));
            ctx.optionValues.put(Option2, env.getOptions().get(Option2));
            return ctx;
        }

        @Override
        protected boolean patchContext(CountingContext context, Env newEnv) {
            context.optionValues.put(Option1, newEnv.getOptions().get(Option1));
            context.optionValues.put(Option2, newEnv.getOptions().get(Option2));
            return super.patchContext(context, newEnv);
        }

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new FirstLanguageOptionDescriptors();
        }
    }

    @TruffleLanguage.Registration(id = SECOND, name = SECOND, version = "1.0", mimeType = SECOND)
    public static final class SecondLanguage extends BaseLanguage {
    }
}
