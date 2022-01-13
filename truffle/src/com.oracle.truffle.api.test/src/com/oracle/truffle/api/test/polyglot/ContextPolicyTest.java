/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextPolicy;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.CompileImmediatelyCheck;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

public class ContextPolicyTest {

    private static final String EXCLUSIVE0 = "ExclusiveLanguage0";
    private static final String REUSE0 = "ReuseLanguage0";
    private static final String SHARED0 = "SharedLanguage0";

    private static final String EXCLUSIVE1 = "ExclusiveLanguage1";
    private static final String REUSE1 = "ReuseLanguage1";
    private static final String SHARED1 = "SharedLanguage1";

    private static final String RUN_INNER_CONTEXT = "runInInnerContext";

    static List<TruffleLanguage<?>> languageInstances = new ArrayList<>();
    static List<TruffleLanguage<?>> contextCreate = new ArrayList<>();
    static List<TruffleLanguage<?>> contextDispose = new ArrayList<>();
    static List<TruffleLanguage<?>> parseRequest = new ArrayList<>();

    @BeforeClass
    public static void runWithWeakEncapsulationOnly() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

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
        Source source0 = Source.create(EXCLUSIVE0, "s0");
        Source source1 = Source.create(EXCLUSIVE0, "s1");

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
        Source source0 = Source.create(SHARED0, "s0");
        Source source1 = Source.create(SHARED0, "s1");

        // same options parse caching enabled
        Engine engine = Engine.create();
        Context context0 = Context.newBuilder().engine(engine).allowExperimentalOptions(true).option(SHARED0 + ".Dummy", "1").build();
        context0.eval(source0);
        context0.eval(source1);
        assertEquals(2, parseRequest.size());
        context0.close();

        Context context1 = Context.newBuilder().engine(engine).allowExperimentalOptions(true).option(SHARED0 + ".Dummy", "1").build();
        context1.eval(source0);
        context1.eval(source1);
        assertEquals(2, parseRequest.size());
        engine.close();

        // different options parse caching disabled
        cleanup();
        engine = Engine.create();
        context0 = Context.newBuilder().engine(engine).allowExperimentalOptions(true).option(SHARED0 + ".Dummy", "1").build();
        context0.eval(source0);
        context0.eval(source1);
        assertEquals(2, parseRequest.size());
        context0.close();

        context1 = Context.newBuilder().engine(engine).allowExperimentalOptions(true).option(SHARED0 + ".Dummy", "2").build();
        context1.eval(source0);
        context1.eval(source1);
        assertEquals(4, parseRequest.size());
        engine.close();
    }

    @Test
    public void testOneReuseLanguageASTParsing() {
        Source source0 = Source.create(REUSE0, "s0");
        Source source1 = Source.create(REUSE0, "s1");

        Engine engine = Engine.create();
        Context context0 = Context.newBuilder().engine(engine).allowExperimentalOptions(true).option(REUSE0 + ".Dummy", "1").build();
        context0.eval(source0);
        context0.eval(source1);
        assertEquals(2, parseRequest.size());
        context0.close();

        Context context1 = Context.newBuilder().engine(engine).allowExperimentalOptions(true).option(REUSE0 + ".Dummy", "1").build();
        context1.eval(source0);
        context1.eval(source1);
        assertEquals(2, parseRequest.size());
        context1.close();

        Context context2 = Context.newBuilder().engine(engine).allowExperimentalOptions(true).option(REUSE0 + ".Dummy", "2").build();
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
        engine.getLanguages().get(EXCLUSIVE0).getOptions();
        assertEquals(1, languageInstances.size());

        Context.newBuilder().engine(engine).build().initialize(EXCLUSIVE0);
        assertEquals(1, languageInstances.size());

        Context.newBuilder().engine(engine).build().initialize(EXCLUSIVE0);
        assertEquals(2, languageInstances.size());

        engine.close();

        // test ONE_REUSE

        cleanup();
        engine = Engine.create();
        assertEquals(0, languageInstances.size());
        engine.getLanguages().get(REUSE0).getOptions();
        assertEquals(1, languageInstances.size());

        Context context0 = Context.newBuilder().engine(engine).build();
        context0.initialize(REUSE0);
        assertEquals(1, languageInstances.size());
        context0.close();

        Context.newBuilder().engine(engine).build().initialize(REUSE0);
        assertEquals(1, languageInstances.size());

        Context.newBuilder().engine(engine).build().initialize(REUSE0);
        assertEquals(2, languageInstances.size());

        engine.close();

        // test MANY
        cleanup();
        engine = Engine.create();
        assertEquals(0, languageInstances.size());
        engine.getLanguages().get(SHARED0).getOptions();
        assertEquals(1, languageInstances.size());

        Context.newBuilder().engine(engine).build().initialize(SHARED0);
        assertEquals(1, languageInstances.size());

        Context.newBuilder().engine(engine).build().initialize(SHARED0);
        assertEquals(1, languageInstances.size());

        engine.close();
    }

    @Test
    public void testOneContext() {
        Engine engine = Engine.create();

        Source source0 = Source.create(EXCLUSIVE0, "s0");
        Source source1 = Source.create(EXCLUSIVE0, "s1");

        Context context0 = Context.newBuilder().engine(engine).build();
        assertEmpty();
        context0.initialize(EXCLUSIVE0);
        assertEquals(1, languageInstances.size());
        assertEquals(1, contextCreate.size());
        assertEquals(0, contextDispose.size());
        assertEquals(0, parseRequest.size());
        assertSame(languageInstances.get(0), contextCreate.get(0));

        Context context1 = Context.newBuilder().engine(engine).build();
        context1.initialize(EXCLUSIVE0);
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
    public void testOneReuseContext() {
        Engine engine = Engine.create();

        Source source0 = Source.create(REUSE0, "s0");
        Source source1 = Source.create(REUSE0, "s1");

        Context context0 = Context.newBuilder().engine(engine).build();
        assertEmpty();
        context0.initialize(REUSE0);
        assertEquals(1, languageInstances.size());
        assertEquals(1, contextCreate.size());
        assertEquals(0, contextDispose.size());
        assertEquals(0, parseRequest.size());
        assertSame(languageInstances.get(0), contextCreate.get(0));

        Context context1 = Context.newBuilder().engine(engine).build();
        context1.initialize(REUSE0);
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
        context2.initialize(REUSE0);
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
        context3.initialize(REUSE0);

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

        Source source0 = Source.create(SHARED0, "s0");
        Source source1 = Source.create(SHARED0, "s1");

        Context context0 = Context.newBuilder().engine(engine).build();
        Context context1 = Context.newBuilder().engine(engine).build();

        assertEmpty();
        context0.initialize(SHARED0);
        assertEquals(1, languageInstances.size());
        assertEquals(1, contextCreate.size());
        assertEquals(0, contextDispose.size());
        assertEquals(0, parseRequest.size());
        assertSame(languageInstances.get(0), contextCreate.get(0));

        context1.initialize(SHARED0);
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

    @Test
    public void testSharingLayersLazyInit() {
        Engine engine = Engine.create();
        Context c0 = Context.newBuilder().engine(engine).build();
        c0.initialize(SHARED0);

        // cannot initialize an exclusive language lazily for a sharing layer.
        AbstractPolyglotTest.assertFails(() -> {
            c0.initialize(EXCLUSIVE0);
        }, PolyglotException.class, (e) -> assertTrue(e.getMessage(),
                        e.getMessage().contains("The context was configured with a shared engine but lazily initialized language 'ExclusiveLanguage0' does not support sharing.")));
        c0.close();

        // initializing the exclusive language first works
        Context c1 = Context.newBuilder().engine(engine).build();
        c1.initialize(EXCLUSIVE0);
        c1.initialize(SHARED0);
        c1.close();

        // also specify them during creation does the trick
        Context c2 = Context.newBuilder(EXCLUSIVE0, SHARED0).engine(engine).build();
        c2.initialize(SHARED0);
        c2.initialize(EXCLUSIVE0);
        c2.close();

        // or specify an option eagerly
        Context c3 = Context.newBuilder().allowExperimentalOptions(true).engine(engine).option(EXCLUSIVE1 + ".Dummy", "42").build();
        c3.initialize(SHARED0);
        c3.initialize(EXCLUSIVE0);
        c3.close();

        engine.close();
    }

    /*
     * Tests invalid sharing detection for single engine multi layer case.
     */
    @Test
    public void testDisableSharing() {
        Engine engine = Engine.newBuilder().allowExperimentalOptions(true).option("engine.DisableCodeSharing", "true").build();
        Context c0 = Context.newBuilder().engine(engine).build();
        c0.initialize(SHARED0);

        // with sharing enabled this would fail.
        c0.initialize(EXCLUSIVE0);

        c0.close();
        engine.close();
    }

    /*
     * Tests invalid sharing detection for single engine multi layer case.
     */
    @Test
    public void testTraceCacing() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Engine engine = Engine.newBuilder().allowExperimentalOptions(true).option("engine.TraceCodeSharing", "true").logHandler(out).build();
        Context c0 = Context.newBuilder().engine(engine).build();
        c0.initialize(SHARED0);
        c0.close();

        Context c1 = Context.newBuilder().engine(engine).build();
        c1.initialize(EXCLUSIVE0);
        c1.close();

        String output = out.toString();
        assertEquals(3, countOccurences(output, " claiming "));
        assertEquals(1, countOccurences(output, " failed to claim "));
        assertEquals(2, countOccurences(output, " claimed "));
        assertEquals(2, countOccurences(output, " compatible "));
        assertEquals(1, countOccurences(output, " incompatible "));

        engine.close();
    }

    private static int countOccurences(String s, String search) {
        int count = 0;
        int index = 0;
        while ((index = s.indexOf(search, index)) != -1) {
            index += search.length();
            count++;
        }
        return count;
    }

    /*
     * Tests invalid sharing detection for single engine multi layer case.
     */
    @Test
    public void testInvalidSharingLayer() {
        Engine engine = Engine.create();
        Context c1 = Context.newBuilder().engine(engine).build();
        c1.initialize(EXCLUSIVE0);
        c1.initialize(EXCLUSIVE1);

        Context c2 = Context.newBuilder().engine(engine).build();
        c2.initialize(EXCLUSIVE0);
        c2.initialize(EXCLUSIVE1);

        c1.enter();
        CallTarget t1 = new RootNode(ExclusiveLanguage0.REFERENCE.get(null)) {
            @Override
            public Object execute(VirtualFrame frame) {
                return ExclusiveLanguage0.REFERENCE.get(this);
            }
        }.getCallTarget();
        CallTarget t2 = new RootNode(ExclusiveLanguage1.REFERENCE.get(null)) {
            @Override
            public Object execute(VirtualFrame frame) {
                return ExclusiveLanguage1.REFERENCE.get(this);
            }
        }.getCallTarget();

        CallTarget t3 = new RootNode(ExclusiveLanguage0.REFERENCE.get(null)) {
            @Override
            public Object execute(VirtualFrame frame) {
                return ExclusiveLanguage0.CONTEXT_REF.get(this);
            }
        }.getCallTarget();
        CallTarget t4 = new RootNode(ExclusiveLanguage1.REFERENCE.get(null)) {
            @Override
            public Object execute(VirtualFrame frame) {
                return ExclusiveLanguage1.CONTEXT_REF.get(this);
            }
        }.getCallTarget();
        c1.leave();

        c2.enter();

        AbstractPolyglotTest.assertFails(() -> {
            t1.call();
        }, AssertionError.class, (e) -> {
            assertTrue(e.getMessage(), e.getMessage().contains("Invalid sharing of AST nodes detected."));
            assertTrue(e.getMessage(), e.getMessage().contains("Sharing Layer Change"));
        });

        AbstractPolyglotTest.assertFails(() -> {
            t2.call();
        }, AssertionError.class, (e) -> {
            assertTrue(e.getMessage(), e.getMessage().contains("Invalid sharing of AST nodes detected."));
            assertTrue(e.getMessage(), e.getMessage().contains("Sharing Layer Change"));
        });

        AbstractPolyglotTest.assertFails(() -> {
            t3.call();
        }, AssertionError.class, (e) -> {
            assertTrue(e.getMessage(), e.getMessage().contains("Invalid sharing of AST nodes detected."));
            assertTrue(e.getMessage(), e.getMessage().contains("Sharing Layer Change"));
        });

        AbstractPolyglotTest.assertFails(() -> {
            t4.call();
        }, AssertionError.class, (e) -> {
            assertTrue(e.getMessage(), e.getMessage().contains("Invalid sharing of AST nodes detected."));
            assertTrue(e.getMessage(), e.getMessage().contains("Sharing Layer Change"));
        });

        c2.leave();

        c1.close();
        c2.close();
    }

    @Test
    public void testGuestToHostCodeCache() {
        Engine engine = Engine.create();
        try (Context c1 = Context.newBuilder().engine(engine).allowExperimentalOptions(true).option(String.format("%s.Dummy", SHARED0), "1").build()) {
            c1.initialize(SHARED0);
            try (Context c2 = Context.newBuilder().engine(engine).allowExperimentalOptions(true).option(String.format("%s.Dummy", SHARED0), "2").build()) {
                c2.initialize(SHARED0);
                ProxyObject proxy = ProxyObject.fromMap(Collections.singletonMap("key", "value"));
                c1.enter();
                assertEquals("value", c1.asValue(proxy).getMember("key").asString());
                c1.leave();
                c2.enter();
                assertEquals("key", c2.asValue(proxy).getMemberKeys().iterator().next());
                c2.leave();
            }
        }
    }

    private static void assertEmpty() {
        assertEquals(0, languageInstances.size());
        assertEquals(0, contextCreate.size());
        assertEquals(0, contextDispose.size());
        assertEquals(0, parseRequest.size());
    }

    @Test
    public void testReferencesWithCodeMixing() {
        // compile immediately is too much for this test.
        Assume.assumeFalse(CompileImmediatelyCheck.isCompileImmediately());

        testReferenceMixing(EXCLUSIVE0, EXCLUSIVE1);
        testReferenceMixing(EXCLUSIVE0, SHARED1);
        testReferenceMixing(EXCLUSIVE0, REUSE1);

        testReferenceMixing(REUSE0, EXCLUSIVE1);
        testReferenceMixing(REUSE0, REUSE1);
        testReferenceMixing(REUSE0, SHARED1);

        testReferenceMixing(SHARED0, EXCLUSIVE1);
        testReferenceMixing(SHARED0, REUSE1);
        testReferenceMixing(SHARED0, SHARED1);
    }

    private static void testReferenceMixing(String language0, String language1) {
        testReferenceMixingWithSourcesAndCompatibility(language0, language1, true, true);
        testReferenceMixingWithSourcesAndCompatibility(language0, language1, false, true);
        testReferenceMixingWithSourcesAndCompatibility(language0, language1, true, false);
        testReferenceMixingWithSourcesAndCompatibility(language0, language1, false, false);
    }

    private static void testReferenceMixingWithSourcesAndCompatibility(String language0, String language1,
                    boolean language0Compatible,
                    boolean language1Compatible) {

        // run without inner contexts

        // test with both sources cached
        testReferenceMixingWithSources(
                        Source.newBuilder(language0, "s0", "").cached(true).buildLiteral(),
                        Source.newBuilder(language1, "s0", "").cached(true).buildLiteral(),
                        language0Compatible, language1Compatible);

        // test with one source uncached
        testReferenceMixingWithSources(
                        Source.newBuilder(language0, "s1", "").cached(false).buildLiteral(),
                        Source.newBuilder(language1, "s1", "").cached(true).buildLiteral(),
                        language0Compatible, language1Compatible);

        // test with both uncached
        testReferenceMixingWithSources(
                        Source.newBuilder(language0, "s2", "").cached(false).buildLiteral(),
                        Source.newBuilder(language1, "s2", "").cached(false).buildLiteral(),
                        language0Compatible, language1Compatible);

        // run with inner contexts

        // test with both sources cached
        testReferenceMixingWithSources(
                        Source.newBuilder(language0, "s0", RUN_INNER_CONTEXT).cached(true).buildLiteral(),
                        Source.newBuilder(language1, "s0", RUN_INNER_CONTEXT).cached(true).buildLiteral(),
                        language0Compatible, language1Compatible);

        // test with one source uncached
        testReferenceMixingWithSources(
                        Source.newBuilder(language0, "s1", RUN_INNER_CONTEXT).cached(false).buildLiteral(),
                        Source.newBuilder(language1, "s1", RUN_INNER_CONTEXT).cached(true).buildLiteral(),
                        language0Compatible, language1Compatible);

        // test with both uncached
        testReferenceMixingWithSources(
                        Source.newBuilder(language0, "s2", RUN_INNER_CONTEXT).cached(false).buildLiteral(),
                        Source.newBuilder(language1, "s2", RUN_INNER_CONTEXT).cached(false).buildLiteral(),
                        language0Compatible, language1Compatible);
    }

    private static void testReferenceMixingWithSources(Source sl0, Source sl1, boolean language0Compatible,
                    boolean language1Compatible) {
        testReferenceMixingWithSourcesBound(sl0, sl1, language0Compatible, language1Compatible, false, false);
        testReferenceMixingWithSourcesBound(sl0, sl1, language0Compatible, language1Compatible, true, false);
        testReferenceMixingWithSourcesBound(sl0, sl1, language0Compatible, language1Compatible, false, true);
        testReferenceMixingWithSourcesBound(sl0, sl1, language0Compatible, language1Compatible, true, true);
    }

    private static void testReferenceMixingWithSourcesBound(Source sl0, Source sl1, boolean language0Compatible, boolean language1Compatible,
                    boolean sharedContext1,
                    boolean sharedContext2) {

        // opening contexts consecutively
        try (Engine engine = Engine.create()) {
            Context.Builder b0 = Context.newBuilder(sl0.getLanguage(), sl1.getLanguage()).allowExperimentalOptions(true);
            if (!sharedContext1) {
                b0.engine(engine);
            }
            if (!language0Compatible) {
                b0.option(sl0.getLanguage() + ".Dummy", "0");
            }

            try (Context c0 = b0.build()) {
                Value v0l0 = c0.eval(sl0);
                Value v0l1 = c0.eval(sl1);

                v0l1.execute(v0l1, v0l0, v0l0);
                v0l1.execute(v0l0, v0l1, v0l1);
                v0l0.execute(v0l1, v0l0);
                v0l0.execute(v0l0, v0l1);
            }

            Context.Builder b1 = Context.newBuilder(sl0.getLanguage(), sl1.getLanguage()).allowExperimentalOptions(true);
            if (!sharedContext2) {
                b1.engine(engine);
            }
            if (!language1Compatible) {
                b1.option(sl1.getLanguage() + ".Dummy", "1");
            }
            try (Context c1 = b1.build()) {
                Value v1l0 = c1.eval(sl0);
                Value v1l1 = c1.eval(sl1);

                v1l1.execute(v1l1, v1l0, v1l0);
                v1l1.execute(v1l0, v1l1, v1l1);

                v1l0.execute(v1l1, v1l0);
                v1l0.execute(v1l0, v1l1);
            }
        }

        // opening two contexts at the same time
        try (Engine engine = Engine.create()) {
            Context.Builder b0 = Context.newBuilder(sl0.getLanguage(), sl1.getLanguage()).allowExperimentalOptions(true);
            if (!sharedContext1) {
                b0.engine(engine);
            }
            if (!language0Compatible) {
                b0.option(sl0.getLanguage() + ".Dummy", "0");
            }
            Context.Builder b1 = Context.newBuilder(sl0.getLanguage(), sl1.getLanguage()).allowExperimentalOptions(true);
            if (!sharedContext2) {
                b1.engine(engine);
            }
            if (!language1Compatible) {
                b1.option(sl1.getLanguage() + ".Dummy", "1");
            }
            try (Context c0 = b0.build();
                            Context c1 = b1.build()) {
                Value v0l0 = c0.eval(sl0);
                Value v0l1 = c0.eval(sl1);

                Value v1l0 = c1.eval(sl0);
                Value v1l1 = c1.eval(sl1);

                v0l1.execute(v0l1, v0l0, v0l0);
                v0l1.execute(v0l0, v0l1, v0l1);

                v1l1.execute(v1l1, v1l0, v1l0);
                v1l1.execute(v1l0, v1l1, v1l1);

                v0l0.execute(v0l1, v0l0);
                v0l0.execute(v0l0, v0l1);
                v1l0.execute(v1l1, v1l0);
                v1l0.execute(v1l0, v1l1);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static class SharedObject implements TruffleObject {

        final TruffleContext context;
        final BaseLanguage expectedLanguage;
        final Env expectedEnvironment;
        final CallTarget target;

        @TruffleBoundary
        SharedObject(TruffleContext context, BaseLanguage language, Env env) {
            this.context = context;
            this.expectedLanguage = language;
            this.expectedEnvironment = env;
            this.target = new RootNode(language) {
                @Child InteropLibrary library = InteropLibrary.getFactory().createDispatched(5);

                @SuppressWarnings("unchecked")
                @Override
                public Object execute(VirtualFrame frame) {
                    Object[] args = frame.getArguments();

                    doAssertions();

                    if (args.length > 0) {
                        try {
                            return library.execute(args[0], Arrays.copyOfRange(args, 1, args.length));
                        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                            CompilerDirectives.transferToInterpreter();
                            throw new AssertionError();
                        }
                    } else {
                        return "done";
                    }
                }
            }.getCallTarget();
        }

        @ExportMessage
        boolean accepts(@Cached(value = "this.expectedLanguage.getClass()", allowUncached = true) Class<?> cachedLanguageclass) {
            /*
             * This is needed to make sure we don't try to compare cached suppliers from different
             * languages. This is necessary because we use the expectedLanguage class to lookup the
             * suppliers.
             */
            return expectedLanguage.getClass() == cachedLanguageclass;
        }

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @SuppressWarnings("unchecked")
        @ExportMessage
        Object execute(Object[] args,
                        @CachedLibrary(limit = "5") InteropLibrary library)
                        throws UnsupportedTypeException, ArityException, UnsupportedMessageException {

            Object prev = enterInner();
            try {
                doAssertions();

                target.call(args);

                if (args.length > 0) {
                    return library.execute(args[0], Arrays.copyOfRange(args, 1, args.length));
                } else {
                    return "done";
                }
            } finally {
                leaveInner(prev);
            }
        }

        @TruffleBoundary
        private void doAssertions() {
            assertSame(expectedLanguage, expectedLanguage.getLanguageReference().get(null));
            assertSame(expectedEnvironment, expectedLanguage.getContextReference0().get(null).env);
        }

        @TruffleBoundary
        private void leaveInner(Object prev) {
            if (context != null) {
                context.leave(null, prev);
            }
        }

        @TruffleBoundary
        @SuppressWarnings("unchecked")
        private Object enterInner() {
            Object prev = null;
            if (context != null) {
                prev = context.enter(null);
                doAssertions();
            }
            return prev;
        }
    }

    static class LanguageRootNode extends RootNode {

        private final BaseLanguage language;

        private final boolean innerContext;

        @SuppressWarnings("unchecked")
        protected LanguageRootNode(BaseLanguage language, boolean innerContext) {
            super(language);
            this.language = language;
            this.innerContext = innerContext;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            LangContext langContext = language.getContextReference0().get(this);
            TruffleContext context = null;
            if (innerContext) {
                if (langContext.innerContext == null) {
                    context = langContext.env.newContextBuilder().build();
                    langContext.innerContext = context;
                } else {
                    context = langContext.innerContext;
                }
            }
            Object prev = null;
            if (context != null) {
                prev = context.enter(this);
            }
            try {
                SharedObject obj = new SharedObject(
                                context,
                                language.getLanguageReference().get(null),
                                language.getContextReference0().get(null).env);
                return obj;
            } finally {
                if (context != null) {
                    context.leave(this, prev);
                }
            }

        }
    }

    static class LangContext {
        final Env env;
        TruffleContext innerContext;

        LangContext(Env env) {
            this.env = env;
        }
    }

    abstract static class BaseLanguage extends TruffleLanguage<LangContext> {

        abstract LanguageReference<? extends BaseLanguage> getLanguageReference();

        abstract ContextReference<LangContext> getContextReference0();
    }

    @Registration(id = EXCLUSIVE0, name = EXCLUSIVE0, contextPolicy = ContextPolicy.EXCLUSIVE)
    public static class ExclusiveLanguage0 extends BaseLanguage {

        public ExclusiveLanguage0() {
            languageInstances.add(this);
        }

        @Option(help = "", category = OptionCategory.INTERNAL) //
        static final OptionKey<Integer> Dummy = new OptionKey<>(0);

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new ExclusiveLanguage0OptionDescriptors();
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            parseRequest.add(this);
            boolean innerContext = request.getSource().getName().equals(RUN_INNER_CONTEXT);
            return new LanguageRootNode(this, innerContext).getCallTarget();
        }

        @Override
        protected LangContext createContext(Env env) {
            contextCreate.add(this);
            return new LangContext(env);
        }

        @Override
        protected void finalizeContext(LangContext context) {
            if (context.innerContext != null) {
                context.innerContext.close();
            }
        }

        @Override
        protected void disposeContext(LangContext context) {
            contextDispose.add(this);
        }

        @Override
        ContextReference<LangContext> getContextReference0() {
            return CONTEXT_REF;
        }

        @Override
        LanguageReference<? extends BaseLanguage> getLanguageReference() {
            return REFERENCE;
        }

        static final ContextReference<LangContext> CONTEXT_REF = ContextReference.create(ExclusiveLanguage0.class);
        static final LanguageReference<ExclusiveLanguage0> REFERENCE = LanguageReference.create(ExclusiveLanguage0.class);

    }

    @Registration(id = EXCLUSIVE1, name = EXCLUSIVE1, contextPolicy = ContextPolicy.SHARED)
    public static final class ExclusiveLanguage1 extends ExclusiveLanguage0 {
        @Option(help = "", category = OptionCategory.INTERNAL) //
        static final OptionKey<Integer> Dummy = new OptionKey<>(0);

        @Override
        protected boolean areOptionsCompatible(OptionValues firstOptions, OptionValues newOptions) {
            // this effectively makes it EXCLUSIVE
            return false;
        }

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new ExclusiveLanguage1OptionDescriptors();
        }

        @Override
        ContextReference<LangContext> getContextReference0() {
            return CONTEXT_REF;
        }

        @Override
        LanguageReference<? extends BaseLanguage> getLanguageReference() {
            return REFERENCE;
        }

        static final ContextReference<LangContext> CONTEXT_REF = ContextReference.create(ExclusiveLanguage1.class);
        static final LanguageReference<ExclusiveLanguage1> REFERENCE = LanguageReference.create(ExclusiveLanguage1.class);

    }

    @Registration(id = REUSE0, name = REUSE0, contextPolicy = ContextPolicy.REUSE)
    public static class ReuseLanguage0 extends ExclusiveLanguage0 {
        @Option(help = "", category = OptionCategory.INTERNAL) //
        static final OptionKey<Integer> Dummy = new OptionKey<>(0);

        @Override
        protected boolean areOptionsCompatible(OptionValues firstOptions, OptionValues newOptions) {
            return firstOptions.get(Dummy).equals(newOptions.get(Dummy));
        }

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new ReuseLanguage0OptionDescriptors();
        }

        @Override
        ContextReference<LangContext> getContextReference0() {
            return CONTEXT_REF;
        }

        @Override
        LanguageReference<? extends BaseLanguage> getLanguageReference() {
            return REFERENCE;
        }

        static final ContextReference<LangContext> CONTEXT_REF = ContextReference.create(ReuseLanguage0.class);
        static final LanguageReference<ReuseLanguage0> REFERENCE = LanguageReference.create(ReuseLanguage0.class);
    }

    @Registration(id = REUSE1, name = REUSE1, contextPolicy = ContextPolicy.REUSE)
    public static class ReuseLanguage1 extends ExclusiveLanguage0 {
        @Option(help = "", category = OptionCategory.INTERNAL) //
        static final OptionKey<Integer> Dummy = new OptionKey<>(0);

        @Override
        protected boolean areOptionsCompatible(OptionValues firstOptions, OptionValues newOptions) {
            return firstOptions.get(Dummy).equals(newOptions.get(Dummy));
        }

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new ReuseLanguage1OptionDescriptors();
        }

        @Override
        ContextReference<LangContext> getContextReference0() {
            return CONTEXT_REF;
        }

        @Override
        LanguageReference<? extends BaseLanguage> getLanguageReference() {
            return REFERENCE;
        }

        static final ContextReference<LangContext> CONTEXT_REF = ContextReference.create(ReuseLanguage1.class);
        static final LanguageReference<ReuseLanguage1> REFERENCE = LanguageReference.create(ReuseLanguage1.class);
    }

    @Registration(id = SHARED0, name = SHARED0, contextPolicy = ContextPolicy.SHARED)
    public static class SharedLanguage0 extends ExclusiveLanguage0 {
        @Option(help = "", category = OptionCategory.INTERNAL) //
        static final OptionKey<Integer> Dummy = new OptionKey<>(0);

        @Override
        protected boolean areOptionsCompatible(OptionValues firstOptions, OptionValues newOptions) {
            return firstOptions.get(Dummy).equals(newOptions.get(Dummy));
        }

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new SharedLanguage0OptionDescriptors();
        }

        @Override
        ContextReference<LangContext> getContextReference0() {
            return CONTEXT_REF;
        }

        @Override
        LanguageReference<? extends BaseLanguage> getLanguageReference() {
            return REFERENCE;
        }

        static final ContextReference<LangContext> CONTEXT_REF = ContextReference.create(SharedLanguage0.class);
        static final LanguageReference<SharedLanguage0> REFERENCE = LanguageReference.create(SharedLanguage0.class);
    }

    @Registration(id = SHARED1, name = SHARED1, contextPolicy = ContextPolicy.SHARED)
    public static class SharedLanguage1 extends ExclusiveLanguage0 {
        @Option(help = "", category = OptionCategory.INTERNAL) //
        static final OptionKey<Integer> Dummy = new OptionKey<>(0);

        @Override
        protected boolean areOptionsCompatible(OptionValues firstOptions, OptionValues newOptions) {
            return firstOptions.get(Dummy).equals(newOptions.get(Dummy));
        }

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new SharedLanguage1OptionDescriptors();
        }

        @Override
        ContextReference<LangContext> getContextReference0() {
            return CONTEXT_REF;
        }

        @Override
        LanguageReference<? extends BaseLanguage> getLanguageReference() {
            return REFERENCE;
        }

        static final ContextReference<LangContext> CONTEXT_REF = ContextReference.create(SharedLanguage1.class);
        static final LanguageReference<SharedLanguage1> REFERENCE = LanguageReference.create(SharedLanguage1.class);
    }

}
