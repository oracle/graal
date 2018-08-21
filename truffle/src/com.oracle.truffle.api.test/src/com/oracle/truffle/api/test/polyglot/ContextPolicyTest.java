/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextPolicy;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.nodes.RootNode;

public class ContextPolicyTest {

    private static final String SINGLE_LANGUAGE = "OneContextPolicy";
    private static final String SINGLE_REUSE_LANGUAGE = "OneReuseContextPolicy";
    private static final String MULTIPLE_LANGUAGE = "ManyContextPolicy";

    static List<TruffleLanguage<?>> languageInstances = new ArrayList<>();
    static List<TruffleLanguage<?>> contextCreate = new ArrayList<>();
    static List<TruffleLanguage<?>> contextDispose = new ArrayList<>();
    static List<TruffleLanguage<?>> parseRequest = new ArrayList<>();

    @After
    @Before
    public void cleanup() {
        languageInstances.clear();
        contextCreate.clear();
        contextDispose.clear();
        parseRequest.clear();
    }

    @Test
    public void testOneLanguageASTParsing() {
        Source source0 = Source.create(SINGLE_LANGUAGE, "s0");
        Source source1 = Source.create(SINGLE_LANGUAGE, "s1");

        Engine engine = Engine.create();
        Context context0 = Context.newBuilder().engine(engine).build();
        context0.eval(source0);
        context0.eval(source1);
        assertEquals(2, parseRequest.size());
        context0.close();

        Context context1 = Context.newBuilder().engine(engine).build();
        context1.eval(source0);
        context1.eval(source1);
        assertEquals(4, parseRequest.size());

        engine.close();
    }

    @Test
    public void testManyLanguageASTParsing() {
        Source source0 = Source.create(MULTIPLE_LANGUAGE, "s0");
        Source source1 = Source.create(MULTIPLE_LANGUAGE, "s1");

        // same options parse caching enabled
        Engine engine = Engine.create();
        Context context0 = Context.newBuilder().engine(engine).option(MULTIPLE_LANGUAGE + ".Dummy", "1").build();
        context0.eval(source0);
        context0.eval(source1);
        assertEquals(2, parseRequest.size());
        context0.close();

        Context context1 = Context.newBuilder().engine(engine).option(MULTIPLE_LANGUAGE + ".Dummy", "1").build();
        context1.eval(source0);
        context1.eval(source1);
        assertEquals(2, parseRequest.size());
        engine.close();

        // different options parse caching disabled
        cleanup();
        engine = Engine.create();
        context0 = Context.newBuilder().engine(engine).option(MULTIPLE_LANGUAGE + ".Dummy", "1").build();
        context0.eval(source0);
        context0.eval(source1);
        assertEquals(2, parseRequest.size());
        context0.close();

        context1 = Context.newBuilder().engine(engine).option(MULTIPLE_LANGUAGE + ".Dummy", "2").build();
        context1.eval(source0);
        context1.eval(source1);
        assertEquals(4, parseRequest.size());
        engine.close();
    }

    @Test
    public void testOneReuseLanguageASTParsing() {
        Source source0 = Source.create(SINGLE_REUSE_LANGUAGE, "s0");
        Source source1 = Source.create(SINGLE_REUSE_LANGUAGE, "s1");

        Engine engine = Engine.create();
        Context context0 = Context.newBuilder().engine(engine).option(SINGLE_REUSE_LANGUAGE + ".Dummy", "1").build();
        context0.eval(source0);
        context0.eval(source1);
        assertEquals(2, parseRequest.size());
        context0.close();

        Context context1 = Context.newBuilder().engine(engine).option(SINGLE_REUSE_LANGUAGE + ".Dummy", "1").build();
        context1.eval(source0);
        context1.eval(source1);
        assertEquals(2, parseRequest.size());
        context1.close();

        Context context2 = Context.newBuilder().engine(engine).option(SINGLE_REUSE_LANGUAGE + ".Dummy", "2").build();
        context2.eval(source0);
        context2.eval(source1);
        assertEquals(4, parseRequest.size());
        context1.close();

        engine.close();
    }

    @Test
    public void testOptionDescriptorContextReuse() {
        Engine engine = Engine.create();

        // test ONE

        assertEquals(0, languageInstances.size());
        engine.getLanguages().get(SINGLE_LANGUAGE).getOptions();
        assertEquals(1, languageInstances.size());

        Context.newBuilder().engine(engine).build().initialize(SINGLE_LANGUAGE);
        assertEquals(1, languageInstances.size());

        Context.newBuilder().engine(engine).build().initialize(SINGLE_LANGUAGE);
        assertEquals(2, languageInstances.size());

        engine.close();

        // test ONE_REUSE

        cleanup();
        engine = Engine.create();
        assertEquals(0, languageInstances.size());
        engine.getLanguages().get(SINGLE_REUSE_LANGUAGE).getOptions();
        assertEquals(1, languageInstances.size());

        Context context0 = Context.newBuilder().engine(engine).build();
        context0.initialize(SINGLE_REUSE_LANGUAGE);
        assertEquals(1, languageInstances.size());
        context0.close();

        Context.newBuilder().engine(engine).build().initialize(SINGLE_REUSE_LANGUAGE);
        assertEquals(1, languageInstances.size());

        Context.newBuilder().engine(engine).build().initialize(SINGLE_REUSE_LANGUAGE);
        assertEquals(2, languageInstances.size());

        engine.close();

        // test MANY
        cleanup();
        engine = Engine.create();
        assertEquals(0, languageInstances.size());
        engine.getLanguages().get(MULTIPLE_LANGUAGE).getOptions();
        assertEquals(1, languageInstances.size());

        Context.newBuilder().engine(engine).build().initialize(MULTIPLE_LANGUAGE);
        assertEquals(1, languageInstances.size());

        Context.newBuilder().engine(engine).build().initialize(MULTIPLE_LANGUAGE);
        assertEquals(1, languageInstances.size());

        engine.close();
    }

    @Test
    public void testOneContext() {
        Engine engine = Engine.create();

        Source source0 = Source.create(SINGLE_LANGUAGE, "s0");
        Source source1 = Source.create(SINGLE_LANGUAGE, "s1");

        Context context0 = Context.newBuilder().engine(engine).build();
        assertEmpty();
        context0.initialize(SINGLE_LANGUAGE);
        assertEquals(1, languageInstances.size());
        assertEquals(1, contextCreate.size());
        assertEquals(0, contextDispose.size());
        assertEquals(0, parseRequest.size());
        assertSame(languageInstances.get(0), contextCreate.get(0));

        Context context1 = Context.newBuilder().engine(engine).build();
        context1.initialize(SINGLE_LANGUAGE);
        assertEquals(2, languageInstances.size());
        assertEquals(2, contextCreate.size());
        assertEquals(0, contextDispose.size());
        assertEquals(0, parseRequest.size());
        assertSame(languageInstances.get(1), contextCreate.get(1));

        context0.eval(source0);
        assertEquals(1, parseRequest.size());
        assertSame(languageInstances.get(0), parseRequest.get(0));

        context0.eval(source0);
        assertEquals(1, parseRequest.size());
        assertSame(languageInstances.get(0), parseRequest.get(0));

        context1.eval(source0);
        assertEquals(2, parseRequest.size());
        assertSame(languageInstances.get(1), parseRequest.get(1));

        context1.eval(source0);
        assertEquals(2, parseRequest.size());
        assertSame(languageInstances.get(1), parseRequest.get(1));

        context0.eval(source1);
        assertEquals(3, parseRequest.size());
        assertSame(languageInstances.get(0), parseRequest.get(2));

        context0.eval(source1);
        assertEquals(3, parseRequest.size());
        assertSame(languageInstances.get(0), parseRequest.get(2));

        context1.eval(source1);
        assertEquals(4, parseRequest.size());
        assertSame(languageInstances.get(1), parseRequest.get(3));

        context1.eval(source1);
        assertEquals(4, parseRequest.size());
        assertSame(languageInstances.get(1), parseRequest.get(3));

        assertEquals(0, contextDispose.size());

        context0.close();
        assertEquals(1, contextDispose.size());
        assertSame(languageInstances.get(0), contextDispose.get(0));

        context1.close();
        assertEquals(2, contextDispose.size());
        assertSame(languageInstances.get(1), contextDispose.get(1));

        assertEquals(2, languageInstances.size());
        assertEquals(2, contextCreate.size());

        engine.close();
    }

    @Test
    public void testOneParseCaching() {

    }

    @Test
    public void testOneReuseContext() {
        Engine engine = Engine.create();

        Source source0 = Source.create(SINGLE_REUSE_LANGUAGE, "s0");
        Source source1 = Source.create(SINGLE_REUSE_LANGUAGE, "s1");

        Context context0 = Context.newBuilder().engine(engine).build();
        assertEmpty();
        context0.initialize(SINGLE_REUSE_LANGUAGE);
        assertEquals(1, languageInstances.size());
        assertEquals(1, contextCreate.size());
        assertEquals(0, contextDispose.size());
        assertEquals(0, parseRequest.size());
        assertSame(languageInstances.get(0), contextCreate.get(0));

        Context context1 = Context.newBuilder().engine(engine).build();
        context1.initialize(SINGLE_REUSE_LANGUAGE);
        assertEquals(2, languageInstances.size());
        assertEquals(2, contextCreate.size());
        assertEquals(0, contextDispose.size());
        assertEquals(0, parseRequest.size());
        assertSame(languageInstances.get(1), contextCreate.get(1));

        context0.eval(source0);
        assertEquals(1, parseRequest.size());
        assertSame(languageInstances.get(0), parseRequest.get(0));

        context0.eval(source0);
        assertEquals(1, parseRequest.size());
        assertSame(languageInstances.get(0), parseRequest.get(0));

        context1.eval(source1);
        assertEquals(2, parseRequest.size());
        assertSame(languageInstances.get(1), parseRequest.get(1));

        context1.eval(source1);
        assertEquals(2, parseRequest.size());
        assertSame(languageInstances.get(1), parseRequest.get(1));

        // reuse context1 in context2
        context1.close();
        Context context2 = Context.newBuilder().engine(engine).build();
        context2.initialize(SINGLE_REUSE_LANGUAGE);
        assertEquals(2, languageInstances.size());
        assertEquals(3, contextCreate.size());
        assertEquals(1, contextDispose.size());
        assertEquals(2, parseRequest.size());

        context2.eval(source1);
        assertEquals(2, parseRequest.size());

        context2.eval(source0);
        assertEquals(3, parseRequest.size());
        assertSame(languageInstances.get(1), parseRequest.get(2));

        // reuse context0 in context3
        Context context3 = Context.newBuilder().engine(engine).build();
        context0.close();
        // code is shared on initialization
        context3.initialize(SINGLE_REUSE_LANGUAGE);

        context3.eval(source0);
        assertEquals(3, parseRequest.size());

        context3.close();
        context2.close();
        assertEquals(2, languageInstances.size());
        assertEquals(4, contextCreate.size());
        assertEquals(4, contextDispose.size());

        engine.close();
    }

    @Test
    public void testManyContext() {
        Engine engine = Engine.create();

        Source source0 = Source.create(MULTIPLE_LANGUAGE, "s0");
        Source source1 = Source.create(MULTIPLE_LANGUAGE, "s1");

        Context context0 = Context.newBuilder().engine(engine).build();
        Context context1 = Context.newBuilder().engine(engine).build();

        assertEmpty();
        context0.initialize(MULTIPLE_LANGUAGE);
        assertEquals(1, languageInstances.size());
        assertEquals(1, contextCreate.size());
        assertEquals(0, contextDispose.size());
        assertEquals(0, parseRequest.size());
        assertSame(languageInstances.get(0), contextCreate.get(0));

        context1.initialize(MULTIPLE_LANGUAGE);
        assertEquals(1, languageInstances.size());
        assertEquals(2, contextCreate.size());
        assertEquals(0, contextDispose.size());
        assertEquals(0, parseRequest.size());
        assertSame(languageInstances.get(0), contextCreate.get(1));

        context0.eval(source0);
        assertEquals(1, parseRequest.size());
        assertSame(languageInstances.get(0), parseRequest.get(0));

        context0.eval(source0);
        assertEquals(1, parseRequest.size());

        context1.eval(source0);
        assertEquals(1, parseRequest.size());

        context1.eval(source0);
        assertEquals(1, parseRequest.size());

        context0.eval(source1);
        assertEquals(2, parseRequest.size());
        assertSame(languageInstances.get(0), parseRequest.get(1));

        context0.eval(source1);
        assertEquals(2, parseRequest.size());

        context1.eval(source1);
        assertEquals(2, parseRequest.size());

        context1.eval(source1);
        assertEquals(2, parseRequest.size());

        assertEquals(0, contextDispose.size());

        context0.close();
        assertEquals(1, contextDispose.size());
        assertSame(languageInstances.get(0), contextDispose.get(0));

        context1.close();
        assertEquals(1, languageInstances.size());
        assertEquals(2, contextCreate.size());
        assertEquals(2, contextDispose.size());
        assertSame(languageInstances.get(0), contextDispose.get(0));

        engine.close();
    }

    private static void assertEmpty() {
        assertEquals(0, languageInstances.size());
        assertEquals(0, contextCreate.size());
        assertEquals(0, contextDispose.size());
        assertEquals(0, parseRequest.size());
    }

    @Registration(id = SINGLE_LANGUAGE, name = SINGLE_LANGUAGE, contextPolicy = ContextPolicy.EXCLUSIVE)
    public static class SingleContextPolicyLanguage extends TruffleLanguage<Env> {

        public SingleContextPolicyLanguage() {
            languageInstances.add(this);
        }

        @Option(help = "", category = OptionCategory.DEBUG) //
        static final OptionKey<Integer> Dummy = new OptionKey<>(0);

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new SingleContextPolicyLanguageOptionDescriptors();
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            parseRequest.add(this);
            return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(42));
        }

        @Override
        protected Env createContext(Env env) {
            contextCreate.add(this);
            return env;
        }

        @Override
        protected void disposeContext(Env context) {
            contextDispose.add(this);
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            return false;
        }

    }

    @Registration(id = SINGLE_REUSE_LANGUAGE, name = SINGLE_REUSE_LANGUAGE, contextPolicy = ContextPolicy.REUSE)
    public static class SingleReusePolicyLanguage extends SingleContextPolicyLanguage {
        @Option(help = "", category = OptionCategory.DEBUG) //
        static final OptionKey<Integer> Dummy = new OptionKey<>(0);

        @Override
        protected boolean areOptionsCompatible(OptionValues firstOptions, OptionValues newOptions) {
            return firstOptions.get(Dummy).equals(newOptions.get(Dummy));
        }

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new SingleReusePolicyLanguageOptionDescriptors();
        }
    }

    @Registration(id = MULTIPLE_LANGUAGE, name = MULTIPLE_LANGUAGE, contextPolicy = ContextPolicy.SHARED)
    public static class MultipleContextPolicyLanguage extends SingleContextPolicyLanguage {
        @Option(help = "", category = OptionCategory.DEBUG) //
        static final OptionKey<Integer> Dummy = new OptionKey<>(0);

        @Override
        protected boolean areOptionsCompatible(OptionValues firstOptions, OptionValues newOptions) {
            return firstOptions.get(Dummy).equals(newOptions.get(Dummy));
        }

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new MultipleContextPolicyLanguageOptionDescriptors();
        }
    }

}
