/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.test.common.AbstractExecutableTestLanguage.evalTestLanguage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.management.ExecutionListener;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextPolicy;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.GCUtils;
import com.oracle.truffle.api.test.SubprocessTestUtils;
import com.oracle.truffle.api.test.common.AbstractExecutableTestLanguage;
import com.oracle.truffle.api.test.common.TestUtils;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

/*
 * Please note that any OOME exceptions when running this test indicate memory leaks in Truffle.
 */
public class PolyglotCachingTest {

    @Registration
    public static class ParseCounterTestLanguage extends AbstractExecutableTestLanguage {
        public static final String ID = TestUtils.getDefaultLanguageId(ParseCounterTestLanguage.class);

        @Override
        protected void onParse(ParsingRequest request, Env env, Object[] contextArguments) throws Exception {
            Object parseCalled = contextArguments[0];
            InteropLibrary.getUncached().invokeMember(parseCalled, "incrementAndGet");
        }

        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            return "";
        }
    }

    @Test
    public void testDisableCaching() throws Exception {
        AtomicInteger parseCalled = new AtomicInteger(0);
        try (Context c = Context.newBuilder().allowHostAccess(HostAccess.ALL).build()) {
            Source cachedSource = Source.newBuilder(ParseCounterTestLanguage.ID, "testSourceInstanceIsEqual", "name").cached(true).build();
            Source uncachedSource = Source.newBuilder(ParseCounterTestLanguage.ID, "testSourceInstanceIsEqual", "name").cached(false).build();
            assertEquals(0, parseCalled.get());
            evalTestLanguage(c, ParseCounterTestLanguage.class, uncachedSource, parseCalled);
            assertEquals(1, parseCalled.get());
            evalTestLanguage(c, ParseCounterTestLanguage.class, uncachedSource, parseCalled);
            assertEquals(2, parseCalled.get());
            evalTestLanguage(c, ParseCounterTestLanguage.class, uncachedSource, parseCalled);
            assertEquals(3, parseCalled.get());

            evalTestLanguage(c, ParseCounterTestLanguage.class, cachedSource, parseCalled);
            assertEquals(4, parseCalled.get());
            evalTestLanguage(c, ParseCounterTestLanguage.class, cachedSource, parseCalled);
            assertEquals(4, parseCalled.get());
        }
    }

    /*
     * Tests that the outer source instance is never the same as the one passed in. That allows the
     * outer source instance to be collected while the inner one is still referenced strongly. The
     * garbage collection of the outer source instance will trigger cleanup the cached CallTargets.
     */
    @Test
    public void testLanguageSourceInstanceIsEqualToEmbedder() throws Exception {
        TruffleTestAssumptions.assumeWeakEncapsulation();

        AtomicReference<com.oracle.truffle.api.source.Source> innerSource = new AtomicReference<>(null);
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected CallTarget parse(ParsingRequest request) throws Exception {
                innerSource.set(request.getSource());
                return RootNode.createConstantNode("").getCallTarget();
            }
        });
        try (Context c = Context.create()) {
            Source source = Source.create(ProxyLanguage.ID, "testSourceInstanceIsEqual");
            c.eval(source);

            assertNotNull(innerSource.get());
            Field f = Source.class.getDeclaredField("receiver");
            f.setAccessible(true);

            assertEquals(f.get(source), innerSource.get());
            assertEquals(innerSource.get(), f.get(source));
            assertNotSame(innerSource.get(), f.get(source));
        }
    }

    /*
     * The purpose of this list is to store objects between calls using different contexts in a
     * polyglot isolate. This would otherwise be very hard to do. If polyglot isolate is not used,
     * we must clear the list after each test that uses it.
     */
    static final List<WeakReference<CallTarget>> weaklyStoredCallTargets = new ArrayList<>();

    @After
    public void clearWeaklyStoredCallTargets() {
        /*
         * The list is accessed only from a guest code, so if polyglot isolate is used, clearing the
         * list is not necessary as it stays empty on the host side. However, without polyglot
         * isolates it is important to clear it.
         */
        weaklyStoredCallTargets.clear();
    }

    @Registration(contextPolicy = TruffleLanguage.ContextPolicy.SHARED)
    @ProvidedTags({ExpressionTag.class})
    public static class CallTargetStoringTestLanguage extends TruffleLanguage<TruffleLanguage.Env> {
        public static final String ID = TestUtils.getDefaultLanguageId(CallTargetStoringTestLanguage.class);

        @Override
        protected Env createContext(Env env) {
            return env;
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            byte[] bytes = new byte[16 * 1024 * 1024 - Integer.parseInt(request.getSource().getCharacters().toString())];
            byte byteValue = (byte) 'a';
            Arrays.fill(bytes, byteValue);
            String testString = new String(bytes); // big string

            CallTarget callTarget = new RootNode(this) {
                @SuppressWarnings("unused") final com.oracle.truffle.api.source.Source source = request.getSource();

                @Child TestInstrumentableNode testNode = new TestInstrumentableNode(source);

                @SuppressWarnings("unused") final String bigString = testString;

                @Override
                public Object execute(VirtualFrame frame) {
                    return testNode.execute(frame);
                }
            }.getCallTarget();
            weaklyStoredCallTargets.add(new WeakReference<>(callTarget));
            System.gc();
            return callTarget;
        }
    }

    @Registration(contextPolicy = TruffleLanguage.ContextPolicy.SHARED)
    @ProvidedTags({ExpressionTag.class})
    public static class CallTargetStoringCopySourceTestLanguage extends TruffleLanguage<TruffleLanguage.Env> {
        public static final String ID = TestUtils.getDefaultLanguageId(CallTargetStoringCopySourceTestLanguage.class);

        @Override
        protected Env createContext(Env env) {
            return env;
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            byte[] bytes = new byte[16 * 1024 * 1024 - Integer.parseInt(request.getSource().getCharacters().toString())];
            byte byteValue = (byte) 'a';
            Arrays.fill(bytes, byteValue);
            String testString = new String(bytes); // big string

            CallTarget callTarget = new RootNode(this) {
                @SuppressWarnings("unused") final com.oracle.truffle.api.source.Source source = com.oracle.truffle.api.source.Source.newBuilder(request.getSource()).build();

                @Child TestInstrumentableNode testNode = new TestInstrumentableNode(source);

                @SuppressWarnings("unused") final String bigString = testString;

                @Override
                public Object execute(VirtualFrame frame) {
                    return testNode.execute(frame);
                }
            }.getCallTarget();
            weaklyStoredCallTargets.add(new WeakReference<>(callTarget));
            System.gc();
            return callTarget;
        }
    }

    @Registration
    public static class CallTargetsFreedAssertTestLanguage extends AbstractExecutableTestLanguage {

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            boolean assertFreed = (Boolean) contextArguments[0];
            if (assertFreed) {
                assertEquals(GCUtils.GC_TEST_ITERATIONS, weaklyStoredCallTargets.size());
                GCUtils.assertObjectsCollectible((iteration) -> weaklyStoredCallTargets.get(iteration).get());
            } else {
                assertEquals(1, weaklyStoredCallTargets.size());
                GCUtils.assertNotGc("CallTarget was freed unexpectedly", weaklyStoredCallTargets.get(0));
            }
            return "";
        }
    }

    /*
     * Test that CallTargets stay cached as long as their source instance is alive.
     */
    @Test
    public void testParsedASTIsNotCollectedIfSourceIsAlive() {
        Assume.assumeFalse("This test is too slow in fastdebug.", System.getProperty("java.vm.version").contains("fastdebug"));

        try (Context context = Context.create()) {
            // needs to stay alive
            Source source = Source.create(CallTargetStoringTestLanguage.ID, "0");

            context.eval(source);
            evalTestLanguage(context, CallTargetsFreedAssertTestLanguage.class, "", false);
            for (int i = 0; i < GCUtils.GC_TEST_ITERATIONS; i++) {
                assertEquals("foobar", context.eval(source).asString());
            }
            evalTestLanguage(context, CallTargetsFreedAssertTestLanguage.class, "", false);
        }
    }

    /*
     * Test that CallTargets can get collected when their source instance is not alive.
     */
    @Test
    public void testParsedASTIsCollectedIfSourceIsNotAlive() throws Exception {
        Assume.assumeFalse("This test is too slow in fastdebug.", System.getProperty("java.vm.version").contains("fastdebug"));
        runInSubprocess(() -> {
            try (Engine engine = Engine.create()) {
                for (int i = 0; i < GCUtils.GC_TEST_ITERATIONS; i++) {
                    try (Context context = Context.newBuilder().engine(engine).build()) {
                        Source source = Source.create(CallTargetStoringTestLanguage.ID, String.valueOf(i));
                        assertEquals("foobar", context.eval(source).asString());
                        assertEquals("foobar", context.eval(source).asString());
                        System.gc();
                    }
                }
                try (Context context = Context.newBuilder().engine(engine).build()) {
                    evalTestLanguage(context, CallTargetsFreedAssertTestLanguage.class, "", true);
                }
            }
        });
    }

    /*
     * Test that CallTargets can get collected when their source instance is not alive. Regression
     * test for GR-35371.
     */
    @Test
    public void testParsedASTIsCollectedIfSourceIsNotAliveWithInstrumentation() throws Exception {
        Assume.assumeFalse("This test is too slow in fastdebug.", System.getProperty("java.vm.version").contains("fastdebug"));
        runInSubprocess(() -> {
            AtomicBoolean entered = new AtomicBoolean();
            try (Engine engine = Engine.create()) {
                ExecutionListener.newBuilder().expressions(true).onEnter((event) -> {
                    // this makes sure even some lazy initialization of some event field causes
                    // leaks
                    event.getLocation();
                    event.getInputValues();
                    event.getReturnValue();
                    event.getRootName();
                    event.getException();
                    entered.set(true);

                }).collectExceptions(true).collectInputValues(true).collectReturnValue(true).attach(engine);
                for (int i = 0; i < GCUtils.GC_TEST_ITERATIONS; i++) {
                    try (Context context = Context.newBuilder().engine(engine).build()) {
                        Source source = Source.create(CallTargetStoringTestLanguage.ID, String.valueOf(i));
                        assertEquals("foobar", context.eval(source).asString());
                        assertEquals("foobar", context.eval(source).asString());
                        System.gc();
                    }
                }
                try (Context context = Context.newBuilder().engine(engine).build()) {
                    evalTestLanguage(context, CallTargetsFreedAssertTestLanguage.class, "", true);
                }
                assertTrue(entered.get());
            }
        });
    }

    /*
     * Test that CallTargets can be freed when the source is looked up again using source copying.
     * Regression test for GR-35420.
     */
    @Test
    public void testParsedASTIsCollectedIfSourceIsNotAliveWithCopySource() throws Exception {
        Assume.assumeFalse("This test is too slow in fastdebug.", System.getProperty("java.vm.version").contains("fastdebug"));
        runInSubprocess(() -> {
            AtomicBoolean entered = new AtomicBoolean();
            try (Engine engine = Engine.create()) {
                ExecutionListener.newBuilder().expressions(true).onEnter((event) -> {
                    // this makes sure even some lazy initialization of some event field causes
                    // leaks
                    event.getLocation();
                    event.getInputValues();
                    event.getReturnValue();
                    event.getRootName();
                    event.getException();
                    entered.set(true);

                }).collectExceptions(true).collectInputValues(true).collectReturnValue(true).attach(engine);
                for (int i = 0; i < GCUtils.GC_TEST_ITERATIONS; i++) {
                    try (Context context = Context.newBuilder().engine(engine).build()) {
                        Source source = Source.create(CallTargetStoringCopySourceTestLanguage.ID, String.valueOf(i));
                        assertEquals("foobar", context.eval(source).asString());
                        assertEquals("foobar", context.eval(source).asString());
                        System.gc();
                    }
                }
                try (Context context = Context.newBuilder().engine(engine).build()) {
                    evalTestLanguage(context, CallTargetsFreedAssertTestLanguage.class, "", true);
                }
                assertTrue(entered.get());
            }
        });
    }

    /*
     * Test that if the context is strongly referenced and the source reference is freed the GC can
     * collect the source together with the cached CallTargets.
     */
    @Test
    public void testSourceFreeContextStrong() throws Exception {
        Assume.assumeFalse("This test is too slow in fastdebug.", System.getProperty("java.vm.version").contains("fastdebug"));
        runInSubprocess(() -> {
            try (Context survivingContext = Context.create()) {
                for (int i = 0; i < GCUtils.GC_TEST_ITERATIONS; i++) {
                    Source source = Source.create(CallTargetStoringTestLanguage.ID, String.valueOf(i));
                    assertEquals("foobar", survivingContext.eval(source).asString());
                    assertEquals("foobar", survivingContext.eval(source).asString());
                    System.gc();
                }
                evalTestLanguage(survivingContext, CallTargetsFreedAssertTestLanguage.class, "", true);
            }
        });
    }

    /*
     * Test that if the context is freed and the source reference is still strong the GC can collect
     * the cached CallTargets.
     */
    @Test
    public void testSourceStrongContextFree() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
        Assume.assumeFalse("This test is too slow in fastdebug.", System.getProperty("java.vm.version").contains("fastdebug"));

        List<Source> survivingSources = new ArrayList<>();

        for (int i = 0; i < GCUtils.GC_TEST_ITERATIONS; i++) {
            try (Context context = Context.newBuilder().build()) {
                Source source = Source.create(CallTargetStoringCopySourceTestLanguage.ID, String.valueOf(i));
                assertEquals("foobar", context.eval(source).asString());
                assertEquals("foobar", context.eval(source).asString());
                survivingSources.add(source);
            }
        }

        try (Context context = Context.create()) {
            evalTestLanguage(context, CallTargetsFreedAssertTestLanguage.class, "", true);
        }
    }

    /**
     * Executes the provided {@code runnable}, potentially in a separate process. In HotSpot, the
     * {@code runnable} is executed in a separate JVM. In native-image, the {@code runnable} is
     * executed within the same process.
     */

    private static void runInSubprocess(Runnable runnable) throws IOException, InterruptedException {
        if (ImageInfo.inImageCode()) {
            runnable.run();
        } else {
            SubprocessTestUtils.newBuilder(PolyglotCachingTest.class, runnable).run();
        }
    }

    /*
     * The purpose of this set is to store objects between calls using different contexts in a
     * polyglot isolate. This would otherwise be very hard to do. If polyglot isolate is not used,
     * we must clear the set after each test that uses it.
     */
    static final HashSet<LanguageInstanceStoringTestLanguage> storedLanguageInstances = new HashSet<>();

    @After
    public void clearStoredLanguageInstances() {
        /*
         * The set is accessed only from a guest code, so if polyglot isolate is used, clearing the
         * list is not necessary as it stays empty on the host side. However, without polyglot
         * isolates it is important to clear it.
         */
        storedLanguageInstances.clear();
    }

    @Registration(contextPolicy = ContextPolicy.REUSE)
    public static class LanguageInstanceStoringTestLanguage extends TruffleLanguage<TruffleLanguage.Env> {
        public static final String ID = TestUtils.getDefaultLanguageId(LanguageInstanceStoringTestLanguage.class);

        @Override
        protected Env createContext(Env env) {
            return env;
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            storedLanguageInstances.add(this);
            CallTarget callTarget = new RootNode(this) {
                @SuppressWarnings("unused") final com.oracle.truffle.api.source.Source source = request.getSource();

                @Child TestInstrumentableNode testNode = new TestInstrumentableNode(source);

                @Override
                public Object execute(VirtualFrame frame) {
                    return testNode.execute(frame);
                }
            }.getCallTarget();
            return callTarget;
        }
    }

    @Registration
    public static class LanguageInstancesAssertTestLanguage extends AbstractExecutableTestLanguage {

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            Assert.assertTrue(String.valueOf(storedLanguageInstances.size()), storedLanguageInstances.size() < GCUtils.GC_TEST_ITERATIONS);
            return "";
        }
    }

    /*
     * Test that the language instance is correctly freed if a context is no longer referenced, but
     * was not closed.
     */
    @Test
    public void testEngineStrongContextFree() throws Exception {
        Assume.assumeFalse("This test is too slow in fastdebug.", System.getProperty("java.vm.version").contains("fastdebug"));
        runInSubprocess(() -> {
            try (Engine engine = Engine.create()) {
                GCUtils.assertObjectsCollectible((iteration) -> {
                    if (iteration != 0 && iteration % 4 == 0) {
                        /*
                         * GenerateGcPressure is expensive. We don't need to do it in every
                         * iteration.
                         */
                        GCUtils.generateGcPressure(0.7);
                    }
                    Context context = Context.newBuilder().engine(engine).build();
                    context.eval(LanguageInstanceStoringTestLanguage.ID, String.valueOf(iteration));
                    return context;
                });
                try (Context context = Context.newBuilder().engine(engine).build()) {
                    evalTestLanguage(context, LanguageInstancesAssertTestLanguage.class, "");
                }
            }
        });
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
}
