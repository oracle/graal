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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.management.ExecutionListener;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextPolicy;
import com.oracle.truffle.api.TruffleLanguage.ParsingRequest;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.GCUtils;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

/*
 * Please note that any OOME exceptions when running this test indicate memory leaks in Truffle.
 */
public class PolyglotCachingTest {

    @BeforeClass
    public static void runWithWeakEncapsulationOnly() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

    @Test
    public void testDisableCaching() throws Exception {
        AtomicInteger parseCalled = new AtomicInteger(0);
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected CallTarget parse(ParsingRequest request) throws Exception {
                parseCalled.incrementAndGet();
                return RootNode.createConstantNode("").getCallTarget();
            }
        });
        Context c = Context.create();
        Source cachedSource = Source.newBuilder(ProxyLanguage.ID, "testSourceInstanceIsEqual", "name").cached(true).build();
        Source uncachedSource = Source.newBuilder(ProxyLanguage.ID, "testSourceInstanceIsEqual", "name").cached(false).build();
        assertEquals(0, parseCalled.get());
        c.eval(uncachedSource);
        assertEquals(1, parseCalled.get());
        c.eval(uncachedSource);
        assertEquals(2, parseCalled.get());
        c.eval(uncachedSource);
        assertEquals(3, parseCalled.get());

        c.eval(cachedSource);
        assertEquals(4, parseCalled.get());
        c.eval(cachedSource);
        assertEquals(4, parseCalled.get());
    }

    /*
     * Tests that the outer source instance is never the same as the one passed in. That allows the
     * outer source instance to be collected while the inner one is still referenced strongly. The
     * garbage collection of the outer source instance will trigger cleanup the cached CallTargets.
     */
    @Test
    public void testLanguageSourceInstanceIsEqualToEmbedder() throws Exception {
        AtomicReference<com.oracle.truffle.api.source.Source> innerSource = new AtomicReference<>(null);
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected CallTarget parse(ParsingRequest request) throws Exception {
                innerSource.set(request.getSource());
                return RootNode.createConstantNode("").getCallTarget();
            }
        });
        Context c = Context.create();
        Source source = Source.create(ProxyLanguage.ID, "testSourceInstanceIsEqual");
        c.eval(source);

        assertNotNull(innerSource.get());
        Field f = Source.class.getDeclaredField("receiver");
        f.setAccessible(true);

        assertEquals(f.get(source), innerSource.get());
        assertEquals(innerSource.get(), f.get(source));
        assertNotSame(innerSource.get(), f.get(source));
    }

    /*
     * Test that CallTargets stay cached as long as their source instance is alive.
     */
    @Test
    public void testParsedASTIsNotCollectedIfSourceIsAlive() {
        Assume.assumeFalse("This test is too slow in fastdebug.", System.getProperty("java.vm.version").contains("fastdebug"));
        setupTestLang(false, false);

        Context context = Context.create();
        Source source = Source.create(ProxyLanguage.ID, "0"); // needs to stay alive

        WeakReference<CallTarget> parsedRef = new WeakReference<>(assertParsedEval(context, source));
        for (int i = 0; i < GCUtils.GC_TEST_ITERATIONS; i++) {
            // cache should stay valid and never be collected as long as the source is alive.
            assertCachedEval(context, source);
            System.gc();
        }
        assertNotNull(parsedRef.get());
        context.close();
    }

    /*
     * Test that CallTargets can get collected as long as their source instance is not alive.
     */
    @Test
    public void testParsedASTIsCollectedIfSourceIsNotAlive() {
        Assume.assumeFalse("This test is too slow in fastdebug.", System.getProperty("java.vm.version").contains("fastdebug"));
        setupTestLang(false, false);

        Engine engine = Engine.create();

        GCUtils.assertObjectsCollectible((iteration) -> {
            Context context = Context.newBuilder().engine(engine).build();
            Source source = Source.create(ProxyLanguage.ID, String.valueOf(iteration));
            CallTarget target = assertParsedEval(context, source);
            assertCachedEval(context, source);
            return target;
        });

        engine.close();
    }

    /*
     * Test that CallTargets can get collected as long as their source instance is not alive.
     * Regression test for GR-35371.
     */
    @Test
    public void testParsedASTIsCollectedIfSourceIsNotAliveWithInstrumentation() {
        Assume.assumeFalse("This test is too slow in fastdebug.", System.getProperty("java.vm.version").contains("fastdebug"));
        setupTestLang(false, false);

        AtomicBoolean entered = new AtomicBoolean();
        Engine engine = Engine.create();
        ExecutionListener.newBuilder().expressions(true).onEnter((event) -> {
            // this makes sure even some lazy initialization of some event field causes leaks
            event.getLocation();
            event.getInputValues();
            event.getReturnValue();
            event.getRootName();
            event.getException();
            entered.set(true);

        }).collectExceptions(true).collectInputValues(true).collectReturnValue(true).attach(engine);

        GCUtils.assertObjectsCollectible((iteration) -> {
            Context context = Context.newBuilder().engine(engine).build();
            Source source = Source.create(ProxyLanguage.ID, String.valueOf(iteration));
            CallTarget target = assertParsedEval(context, source);
            assertCachedEval(context, source);
            return target;
        });

        // make sure we actually entered and event and instrumentation was successful
        assertTrue(entered.get());

        engine.close();
    }

    /*
     * Test that CallTargets can be freed when the source is looked up again using source copying.
     * Regression test for GR-35420.
     */
    @Test
    public void testParsedASTIsCollectedIfSourceIsNotAliveWithCopySource() {
        Assume.assumeFalse("This test is too slow in fastdebug.", System.getProperty("java.vm.version").contains("fastdebug"));

        setupTestLang(false, true);

        AtomicBoolean entered = new AtomicBoolean();
        Engine engine = Engine.create();
        ExecutionListener.newBuilder().expressions(true).onEnter((event) -> {
            // this makes sure even some lazy initialization of some event field causes leaks
            event.getLocation();
            event.getInputValues();
            event.getReturnValue();
            event.getRootName();
            event.getException();
            entered.set(true);

        }).collectExceptions(true).collectInputValues(true).collectReturnValue(true).attach(engine);

        GCUtils.assertObjectsCollectible((iteration) -> {
            Context context = Context.newBuilder().engine(engine).build();
            Source source = Source.create(ProxyLanguage.ID, String.valueOf(iteration));
            CallTarget target = assertParsedEval(context, source);
            assertCachedEval(context, source);
            return target;
        });

        // make sure we actually entered and event and instrumentation was successful
        assertTrue(entered.get());

        engine.close();
    }

    /*
     * Test that if the context is strongly referenced and the source reference is freed the GC can
     * collect the source together with the cached CallTargets.
     */
    @Test
    public void testSourceFreeContextStrong() {
        Assume.assumeFalse("This test is too slow in fastdebug.", System.getProperty("java.vm.version").contains("fastdebug"));
        setupTestLang(false, false);

        Context survivingContext = Context.create();
        GCUtils.assertObjectsCollectible((iteration) -> {
            Source source = Source.create(ProxyLanguage.ID, String.valueOf(iteration));
            CallTarget target = assertParsedEval(survivingContext, source);
            assertCachedEval(survivingContext, source);
            return target;
        });
        survivingContext.close();
    }

    /*
     * Test that if the context is freed and the source reference is still strong the GC can collect
     * the cached CallTargets.
     */
    @Test
    public void testSourceStrongContextFree() {
        Assume.assumeFalse("This test is too slow in fastdebug.", System.getProperty("java.vm.version").contains("fastdebug"));
        setupTestLang(false, false);

        List<Source> survivingSources = new ArrayList<>();

        GCUtils.assertObjectsCollectible((iteration) -> {
            Context context = Context.create();
            Source source = Source.create(ProxyLanguage.ID, String.valueOf(iteration));
            CallTarget parsedAST = assertParsedEval(context, source);
            assertCachedEval(context, source);
            survivingSources.add(source);
            context.close();
            return parsedAST;
        });
    }

    /*
     * Test that the language instance is correctly freed if a context is no longer referenced, but
     * was not closed.
     */
    @Test
    public void testEngineStrongContextFree() {
        Assume.assumeFalse("This test is too slow in fastdebug.", System.getProperty("java.vm.version").contains("fastdebug"));
        setupTestLang(true, false);

        Engine engine = Engine.create();
        Set<ProxyLanguage> usedInstances = new HashSet<>();
        GCUtils.assertObjectsCollectible((iteration) -> {
            Context context = Context.newBuilder().engine(engine).build();
            context.eval(ReuseLanguage.ID, String.valueOf(iteration));
            usedInstances.add(lastLanguage);
            return context;
        });
        // we should at least once reuse a language instance
        Assert.assertTrue(String.valueOf(usedInstances.size()), usedInstances.size() < GCUtils.GC_TEST_ITERATIONS);
        engine.close();
    }

    long parseCount;
    CallTarget lastParsedTarget;
    ProxyLanguage lastLanguage;

    private void setupTestLang(boolean reuse, boolean copySource) {
        byte[] bytes = new byte[16 * 1024 * 1024];
        byte byteValue = (byte) 'a';
        Arrays.fill(bytes, byteValue);
        String testString = new String(bytes); // big string

        if (reuse) {
            ProxyLanguage.setDelegate(new ReuseLanguage() {
                @Override
                protected CallTarget parse(ParsingRequest request) throws Exception {
                    return PolyglotCachingTest.this.parse(languageInstance, testString, request, copySource);
                }
            });
        } else {
            ProxyLanguage.setDelegate(new ProxyLanguage() {
                @Override
                protected CallTarget parse(ParsingRequest request) throws Exception {
                    return PolyglotCachingTest.this.parse(languageInstance, testString, request, copySource);
                }
            });
        }
    }

    private CallTarget parse(ProxyLanguage languageInstance, String testString, ParsingRequest request, boolean copySource) {
        int index = Integer.parseInt(request.getSource().getCharacters().toString());
        parseCount++;
        lastLanguage = languageInstance;
        lastParsedTarget = new RootNode(languageInstance) {
            /*
             * Typical root nodes have a strong reference to source. We need to ensure that we can
             * still collect the cache if that happens.
             */
            @SuppressWarnings("unused") final com.oracle.truffle.api.source.Source source = copySource ? com.oracle.truffle.api.source.Source.newBuilder(request.getSource()).build()
                            : request.getSource();

            @SuppressWarnings("unused") final String bigString = testString.substring(index, testString.length());

            @Child TestInstrumentableNode testNode = new TestInstrumentableNode(source);

            @Override
            public Object execute(VirtualFrame frame) {
                return testNode.execute(frame);
            }
        }.getCallTarget();
        return lastParsedTarget;
    }

    @GenerateWrapper
    static class TestInstrumentableNode extends Node implements InstrumentableNode {

        private SourceSection sourceSection;

        TestInstrumentableNode() {
            sourceSection = null;
        }

        TestInstrumentableNode(com.oracle.truffle.api.source.Source source) {
            sourceSection = source.createSection(1);
        }

        public Object execute(@SuppressWarnings("unused") VirtualFrame frame) {
            return "foobar";
        }

        public boolean isInstrumentable() {
            return true;
        }

        public WrapperNode createWrapper(ProbeNode probe) {
            return new TestInstrumentableNodeWrapper(this, probe);
        }

        public boolean hasTag(Class<? extends Tag> tag) {
            // any tag, we want it all
            return tag == ExpressionTag.class;
        }

        @Override
        public SourceSection getSourceSection() {
            return sourceSection;
        }

    }

    private CallTarget assertParsedEval(Context context, Source source) {
        this.parseCount = 0;
        assertEquals("foobar", context.eval(source).asString());
        assertEquals(1, this.parseCount);
        CallTarget parsed = this.lastParsedTarget;
        assertNotNull(parsed);
        this.lastParsedTarget = null;
        return parsed;
    }

    private void assertCachedEval(Context context, Source source) {
        this.parseCount = 0;
        this.lastParsedTarget = null;
        assertEquals("foobar", context.eval(source).asString());
        assertEquals(0, this.parseCount);
        assertNull(lastParsedTarget);
    }

    @TruffleLanguage.Registration(id = ReuseLanguage.ID, name = ReuseLanguage.ID, contextPolicy = ContextPolicy.REUSE)
    public static class ReuseLanguage extends ProxyLanguage {
        public static final String ID = "ReuseLanguage";

    }

}
