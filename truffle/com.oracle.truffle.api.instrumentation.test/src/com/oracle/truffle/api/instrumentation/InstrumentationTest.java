/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrument.Visualizer;
import com.oracle.truffle.api.instrument.WrapperNode;
import com.oracle.truffle.api.instrumentation.InstrumentationTestLanguage.BaseNode;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine.Instrument;

public class InstrumentationTest extends AbstractInstrumentationTest {

    /*
     * Test that instrumentations are invoked at startup once.
     */
    @Test
    public void testAutostart() throws IOException {
        Assert.assertTrue(engine.getInstruments().get("testAutostart").isEnabled());
        assertEnabledInstrument("testAutostart");
        AutostartInstrumentation.count = 0;
        // we assume lazy start here
        run("STATEMENT");
        Assert.assertEquals(1, AutostartInstrumentation.count);
        run("EXPRESSION");
        Assert.assertEquals(1, AutostartInstrumentation.count);
    }

    @Registration(name = "testAutostart", version = "", autostart = true, id = "testAutostart")
    public static class AutostartInstrumentation extends TruffleInstrument {

        private static int count;

        @Override
        protected void onCreate(Env env, Instrumenter instrumenter) {
            count++;
        }
    }

    /*
     * Test that metadata is properly propagated to InstrumenationDescriptor objects.
     */
    @Test
    public void testMetadata() {
        Instrument descriptor1 = engine.getInstruments().get("testMetadataType1");

        Assert.assertEquals("name", descriptor1.getName());
        Assert.assertEquals("version", descriptor1.getVersion());
        Assert.assertEquals("testMetadataType1", descriptor1.getId());
        Assert.assertFalse(descriptor1.isEnabled());
    }

    @Registration(name = "name", version = "version", id = "testMetadataType1")
    public static class MetadataInstrumentation extends TruffleInstrument {
        @Override
        protected void onCreate(Env env, Instrumenter instrumenter) {
        }
    }

    /*
     * Test that metadata is properly propagated to InstrumenationDescriptor objects.
     */
    @Test
    public void testDefaultId() {
        Instrument descriptor1 = engine.getInstruments().get(MetadataInstrumentation2.class.getName());
        Assert.assertEquals("", descriptor1.getName());
        Assert.assertEquals("", descriptor1.getVersion());
        Assert.assertEquals(MetadataInstrumentation2.class.getName(), descriptor1.getId());
        Assert.assertFalse(descriptor1.isEnabled());
    }

    @Registration
    public static class MetadataInstrumentation2 extends TruffleInstrument {
        @Override
        protected void onCreate(Env env, Instrumenter instrumenter) {
        }
    }

    /*
     * Test onCreate and onDispose invocations for multiple instrumentation instances.
     */
    @Test
    public void testMultipleInstruments() throws IOException {
        run(""); // initialize

        MultipleInstrumentsInstrumentation.onCreateCounter = 0;
        MultipleInstrumentsInstrumentation.onDisposeCounter = 0;
        MultipleInstrumentsInstrumentation.constructor = 0;
        Instrument instrument1 = engine.getInstruments().get("testMultipleInstruments");
        instrument1.setEnabled(true);
        Assert.assertEquals(1, MultipleInstrumentsInstrumentation.constructor);
        Assert.assertEquals(1, MultipleInstrumentsInstrumentation.onCreateCounter);
        Assert.assertEquals(0, MultipleInstrumentsInstrumentation.onDisposeCounter);

        Instrument instrument = engine.getInstruments().get("testMultipleInstruments");
        instrument.setEnabled(true);
        Assert.assertEquals(1, MultipleInstrumentsInstrumentation.constructor);
        Assert.assertEquals(1, MultipleInstrumentsInstrumentation.onCreateCounter);
        Assert.assertEquals(0, MultipleInstrumentsInstrumentation.onDisposeCounter);

        instrument.setEnabled(false);
        Assert.assertEquals(1, MultipleInstrumentsInstrumentation.constructor);
        Assert.assertEquals(1, MultipleInstrumentsInstrumentation.onCreateCounter);
        Assert.assertEquals(1, MultipleInstrumentsInstrumentation.onDisposeCounter);

        instrument.setEnabled(true);
        Assert.assertEquals(2, MultipleInstrumentsInstrumentation.constructor);
        Assert.assertEquals(2, MultipleInstrumentsInstrumentation.onCreateCounter);
        Assert.assertEquals(1, MultipleInstrumentsInstrumentation.onDisposeCounter);

        instrument.setEnabled(false);
        Assert.assertEquals(2, MultipleInstrumentsInstrumentation.constructor);
        Assert.assertEquals(2, MultipleInstrumentsInstrumentation.onCreateCounter);
        Assert.assertEquals(2, MultipleInstrumentsInstrumentation.onDisposeCounter);
    }

    @Registration(id = "testMultipleInstruments")
    public static class MultipleInstrumentsInstrumentation extends TruffleInstrument {

        private static int onCreateCounter = 0;
        private static int onDisposeCounter = 0;
        private static int constructor = 0;

        public MultipleInstrumentsInstrumentation() {
            constructor++;
        }

        @Override
        protected void onCreate(Env env, Instrumenter instrumenter) {
            onCreateCounter++;
        }

        @Override
        protected void onDispose(Env env) {
            onDisposeCounter++;
        }
    }

    /*
     * Test exceptions from language instrumentations are not wrapped into
     * InstrumentationExceptions. Test that one language cannot instrument another.
     */
    @Test
    public void testLanguageInstrumentationAndExceptions() throws IOException {
        TestLanguageInstrumentationLanguage.installInstrumentationsCounter = 0;
        TestLanguageInstrumentationLanguage.createContextCounter = 0;
        try {
            engine.eval(Source.fromText("ROOT(EXPRESSION)", null).withMimeType("testLanguageInstrumentation"));
            Assert.fail("expected exception");
        } catch (IOException e) {
            // we assert that MyLanguageException is not wrapped into
            // InstrumentationException.
            if (!(e.getCause() instanceof MyLanguageException)) {
                Assert.fail(String.format("expected MyLanguageException but was %s in %s", e.getCause(), e));
            }
        }
        Assert.assertEquals(1, TestLanguageInstrumentationLanguage.installInstrumentationsCounter);
        Assert.assertEquals(1, TestLanguageInstrumentationLanguage.createContextCounter);

        // this should run isolated from the language instrumentations.
        run("STATEMENT");
    }

    @SuppressWarnings("serial")
    private static class MyLanguageException extends RuntimeException {

    }

    @TruffleLanguage.Registration(name = "", version = "", mimeType = "testLanguageInstrumentation")
    public static class TestLanguageInstrumentationLanguage extends TruffleLanguage<Void> implements InstrumentationLanguage<Void> {

        public static final TestLanguageInstrumentationLanguage INSTANCE = new TestLanguageInstrumentationLanguage();

        static int installInstrumentationsCounter = 0;
        static int createContextCounter = 0;

        @Override
        public void installInstrumentations(Void env, Instrumenter instrumenter) {
            installInstrumentationsCounter++;
            instrumenter.attachListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION).build(), new EventListener() {
                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                }

                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                }

                public void onEnter(EventContext context, VirtualFrame frame) {
                    // since we are a language instrumentation we can throw exceptions
                    // without getting wrapped into Instrumentation exception.
                    throw new MyLanguageException();
                }
            });

            instrumenter.attachListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.STATEMENT).build(), new EventListener() {
                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                    throw new AssertionError();
                }

                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                    throw new AssertionError();
                }

                public void onEnter(EventContext context, VirtualFrame frame) {
                    throw new AssertionError();
                }
            });
        }

        @Override
        protected Void createContext(com.oracle.truffle.api.TruffleLanguage.Env env) {
            createContextCounter++;
            return null;
        }

        @Override
        protected CallTarget parse(final Source code, Node context, String... argumentNames) throws IOException {
            return Truffle.getRuntime().createCallTarget(new RootNode(TestLanguageInstrumentationLanguage.class, null, null) {

                @Child private BaseNode base = InstrumentationTestLanguage.parse(code);

                @Override
                public Object execute(VirtualFrame frame) {
                    return base.execute(frame);
                }
            });
        }

        @Override
        protected Object findExportedSymbol(Void context, String globalName, boolean onlyExplicit) {
            return null;
        }

        @Override
        protected Object getLanguageGlobal(Void context) {
            return null;
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            return false;
        }

        @Override
        protected Visualizer getVisualizer() {
            return null;
        }

        @Override
        protected boolean isInstrumentable(Node node) {
            return false;
        }

        @Override
        protected WrapperNode createWrapperNode(Node node) {
            return null;
        }

        @Override
        protected Object evalInContext(Source source, Node node, MaterializedFrame mFrame) throws IOException {
            return null;
        }

    }

    @Test
    public void testInstrumentationException1() throws IOException {
        engine.getInstruments().get("testInstrumentationException1").setEnabled(true);
        run("");

        Assert.assertTrue(getErr().contains("MyLanguageException"));

    }

    @Registration(name = "", version = "", id = "testInstrumentationException1")
    public static class TestInstrumentationException1 extends TruffleInstrument {

        @Override
        protected void onCreate(Env env, Instrumenter instrumenter) {
            throw new MyLanguageException();
        }

        @Override
        protected void onDispose(Env env) {
        }
    }

    /*
     * We test that instrumentation exceptions are wrapped, onReturnExceptional is invoked properly
     * and not onReturnValue,
     */
    @Test
    public void testInstrumentationException2() throws IOException {
        TestInstrumentationException2.returnedExceptional = 0;
        TestInstrumentationException2.returnedValue = 0;
        engine.getInstruments().get("testInstrumentationException2").setEnabled(true);
        run("ROOT(EXPRESSION)");
        Assert.assertTrue(getErr().contains("MyLanguageException"));

        Assert.assertEquals(0, TestInstrumentationException2.returnedExceptional);
        Assert.assertEquals(1, TestInstrumentationException2.returnedValue);
    }

    @Registration(name = "", version = "", id = "testInstrumentationException2")
    public static class TestInstrumentationException2 extends TruffleInstrument {

        static int returnedExceptional = 0;
        static int returnedValue = 0;

        @Override
        protected void onCreate(Env env, Instrumenter instrumenter) {
            instrumenter.attachListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION).build(), new EventListener() {

                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                    returnedValue++;
                }

                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                    returnedExceptional++;
                }

                public void onEnter(EventContext context, VirtualFrame frame) {
                    throw new MyLanguageException();
                }
            });
        }

        @Override
        protected void onDispose(Env env) {
        }
    }

    /*
     * Test that instrumentation exceptions in the onReturnExceptional are attached as suppressed
     * exceptions.
     */
    @Test
    public void testInstrumentationException3() throws IOException {
        TestInstrumentationException3.returnedExceptional = 0;
        TestInstrumentationException3.onEnter = 0;
        engine.getInstruments().get("testInstrumentationException3").setEnabled(true);
        run("ROOT(EXPRESSION)");
        Assert.assertTrue(getErr().contains("MyLanguageException"));
        Assert.assertEquals(0, TestInstrumentationException3.returnedExceptional);
        Assert.assertEquals(1, TestInstrumentationException3.onEnter);
    }

    @Registration(name = "", version = "", id = "testInstrumentationException3")
    public static class TestInstrumentationException3 extends TruffleInstrument {

        static int returnedExceptional = 0;
        static int onEnter = 0;

        @Override
        protected void onCreate(Env env, Instrumenter instrumenter) {
            instrumenter.attachListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION).build(), new EventListener() {

                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                    throw new MyLanguageException();
                }

                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                    returnedExceptional++;
                }

                public void onEnter(EventContext context, VirtualFrame frame) {
                    onEnter++;
                }
            });
        }

    }

    /*
     * Test that event nodes are created lazily on first execution.
     */
    @Test
    public void testLazyProbe1() throws IOException {
        TestLazyProbe1.createCalls = 0;
        TestLazyProbe1.onEnter = 0;
        TestLazyProbe1.onReturnValue = 0;
        TestLazyProbe1.onReturnExceptional = 0;

        engine.getInstruments().get("testLazyProbe1").setEnabled(true);
        run("ROOT(DEFINE(foo, EXPRESSION))");
        run("ROOT(DEFINE(bar, ROOT(EXPRESSION,EXPRESSION)))");

        Assert.assertEquals(0, TestLazyProbe1.createCalls);
        Assert.assertEquals(0, TestLazyProbe1.onEnter);
        Assert.assertEquals(0, TestLazyProbe1.onReturnValue);
        Assert.assertEquals(0, TestLazyProbe1.onReturnExceptional);

        run("ROOT(CALL(foo))");

        Assert.assertEquals(1, TestLazyProbe1.createCalls);
        Assert.assertEquals(1, TestLazyProbe1.onEnter);
        Assert.assertEquals(1, TestLazyProbe1.onReturnValue);
        Assert.assertEquals(0, TestLazyProbe1.onReturnExceptional);

        run("ROOT(CALL(bar))");

        Assert.assertEquals(3, TestLazyProbe1.createCalls);
        Assert.assertEquals(3, TestLazyProbe1.onEnter);
        Assert.assertEquals(3, TestLazyProbe1.onReturnValue);
        Assert.assertEquals(0, TestLazyProbe1.onReturnExceptional);

        run("ROOT(CALL(bar))");

        Assert.assertEquals(3, TestLazyProbe1.createCalls);
        Assert.assertEquals(5, TestLazyProbe1.onEnter);
        Assert.assertEquals(5, TestLazyProbe1.onReturnValue);
        Assert.assertEquals(0, TestLazyProbe1.onReturnExceptional);

        run("ROOT(CALL(foo))");

        Assert.assertEquals(3, TestLazyProbe1.createCalls);
        Assert.assertEquals(6, TestLazyProbe1.onEnter);
        Assert.assertEquals(6, TestLazyProbe1.onReturnValue);
        Assert.assertEquals(0, TestLazyProbe1.onReturnExceptional);

    }

    @Registration(name = "", version = "", id = "testLazyProbe1")
    public static class TestLazyProbe1 extends TruffleInstrument {

        static int createCalls = 0;
        static int onEnter = 0;
        static int onReturnValue = 0;
        static int onReturnExceptional = 0;

        @Override
        protected void onCreate(Env env, Instrumenter instrumenter) {
            instrumenter.attachFactory(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION).build(), new EventNodeFactory() {
                public EventNode create(EventContext context) {
                    createCalls++;
                    return new EventNode() {
                        @Override
                        public void onReturnValue(VirtualFrame frame, Object result) {
                            onReturnValue++;
                        }

                        @Override
                        public void onReturnExceptional(VirtualFrame frame, Throwable exception) {
                            onReturnExceptional++;
                        }

                        @Override
                        public void onEnter(VirtualFrame frame) {
                            onEnter++;
                        }
                    };
                }
            });
        }
    }

    /*
     * Test that parsing and executing foreign languages work.
     */
    @Test
    public void testEnvParse1() throws IOException {
        TestEnvParse1.onExpression = 0;
        TestEnvParse1.onStatement = 0;

        engine.getInstruments().get("testEnvParse1").setEnabled(true);
        run("STATEMENT");

        Assert.assertEquals(1, TestEnvParse1.onExpression);
        Assert.assertEquals(1, TestEnvParse1.onStatement);

        run("STATEMENT");

        Assert.assertEquals(2, TestEnvParse1.onExpression);
        Assert.assertEquals(2, TestEnvParse1.onStatement);
    }

    @Registration(name = "", version = "", id = "testEnvParse1")
    public static class TestEnvParse1 extends TruffleInstrument {

        static int onExpression = 0;
        static int onStatement = 0;

        @Override
        protected void onCreate(final Env env, Instrumenter instrumenter) {
            instrumenter.attachFactory(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.STATEMENT).build(), new EventNodeFactory() {
                public EventNode create(EventContext context) {

                    final CallTarget target;
                    try {
                        target = env.parse(Source.fromText("EXPRESSION", null).withMimeType(InstrumentationTestLanguage.MIME_TYPE));
                    } catch (IOException e) {
                        throw new AssertionError();
                    }

                    return new EventNode() {
                        @Child private DirectCallNode directCall = Truffle.getRuntime().createDirectCallNode(target);

                        @Override
                        public void onEnter(VirtualFrame frame) {
                            onStatement++;
                            directCall.call(frame, new Object[0]);
                        }

                    };
                }
            });

            instrumenter.attachListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION).build(), new EventListener() {

                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                }

                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                }

                public void onEnter(EventContext context, VirtualFrame frame) {
                    onExpression++;
                }
            });

        }
    }

    /*
     * Test that parsing and executing foreign languages with context work.
     */
    @Test
    public void testEnvParse2() throws IOException {
        TestEnvParse2.onExpression = 0;
        TestEnvParse2.onStatement = 0;

        engine.getInstruments().get("testEnvParse2").setEnabled(true);
        run("STATEMENT");

        Assert.assertEquals(1, TestEnvParse2.onExpression);
        Assert.assertEquals(1, TestEnvParse2.onStatement);

        run("STATEMENT");

        Assert.assertEquals(2, TestEnvParse2.onExpression);
        Assert.assertEquals(2, TestEnvParse2.onStatement);
    }

    @Registration(name = "", version = "", id = "testEnvParse2")
    public static class TestEnvParse2 extends TruffleInstrument {

        static int onExpression = 0;
        static int onStatement = 0;

        @Override
        protected void onCreate(final Env env, Instrumenter instrumenter) {
            instrumenter.attachFactory(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.STATEMENT).build(), new EventNodeFactory() {
                public EventNode create(EventContext context) {

                    final CallTarget target;
                    try {
                        target = context.parseInContext(Source.fromText("EXPRESSION", null).withMimeType(InstrumentationTestLanguage.MIME_TYPE));
                    } catch (IOException e) {
                        throw new AssertionError();
                    }

                    return new EventNode() {
                        @Child private DirectCallNode directCall = Truffle.getRuntime().createDirectCallNode(target);

                        @Override
                        public void onEnter(VirtualFrame frame) {
                            onStatement++;
                            directCall.call(frame, new Object[0]);
                        }

                    };
                }
            });

            instrumenter.attachListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION).build(), new EventListener() {

                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                }

                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                }

                public void onEnter(EventContext context, VirtualFrame frame) {
                    onExpression++;
                }
            });

        }
    }

    /*
     * Test instrument all with any filter. Ensure that root nodes are not tried to be instrumented.
     */
    @Test
    public void testInstrumentAll() throws IOException {
        TestInstrumentAll1.onStatement = 0;

        engine.getInstruments().get("testInstrumentAll").setEnabled(true);
        run("STATEMENT");

        Assert.assertEquals(1, TestInstrumentAll1.onStatement);
    }

    @Registration(id = "testInstrumentAll")
    public static class TestInstrumentAll1 extends TruffleInstrument {

        static int onStatement = 0;

        @Override
        protected void onCreate(final Env env, Instrumenter instrumenter) {
            instrumenter.attachListener(SourceSectionFilter.newBuilder().build(), new EventListener() {
                public void onEnter(EventContext context, VirtualFrame frame) {
                    onStatement++;
                }

                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                }

                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                }
            });
        }
    }

    /*
     * Define is not instrumentable but has a source section.
     */
    @Test
    public void testInstrumentNonInstrumentable() throws IOException {
        TestInstrumentNonInstrumentable1.onStatement = 0;

        engine.getInstruments().get("testInstrumentNonInstrumentable").setEnabled(true);
        run("DEFINE(foo, ROOT())");

        Assert.assertEquals(0, TestInstrumentNonInstrumentable1.onStatement);
    }

    @Registration(id = "testInstrumentNonInstrumentable")
    public static class TestInstrumentNonInstrumentable1 extends TruffleInstrument {

        static int onStatement = 0;

        @Override
        protected void onCreate(final Env env, Instrumenter instrumenter) {
            instrumenter.attachListener(SourceSectionFilter.newBuilder().build(), new EventListener() {
                public void onEnter(EventContext context, VirtualFrame frame) {
                    onStatement++;
                }

                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                }

                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                }
            });
        }
    }

}
