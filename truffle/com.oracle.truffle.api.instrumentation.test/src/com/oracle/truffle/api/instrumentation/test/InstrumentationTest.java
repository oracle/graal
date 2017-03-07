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
package com.oracle.truffle.api.instrumentation.test;

import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.Reader;
import java.nio.CharBuffer;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage.BaseNode;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine.Instrument;

public class InstrumentationTest extends AbstractInstrumentationTest {

    /*
     * Test that metadata is properly propagated to Instrument handles.
     */
    @Test
    public void testMetadata() {
        Instrument instrumentHandle1 = engine.getInstruments().get("testMetadataType1");

        Assert.assertEquals("name", instrumentHandle1.getName());
        Assert.assertEquals("version", instrumentHandle1.getVersion());
        Assert.assertEquals("testMetadataType1", instrumentHandle1.getId());
        Assert.assertFalse(instrumentHandle1.isEnabled());
    }

    @Registration(name = "name", version = "version", id = "testMetadataType1")
    public static class MetadataInstrument extends TruffleInstrument {
        @Override
        protected void onCreate(Env env) {
        }
    }

    /*
     * Test that metadata is properly propagated to Instrument handles.
     */
    @Test
    public void testDefaultId() {
        Instrument descriptor1 = engine.getInstruments().get(MetadataInstrument2.class.getName());
        Assert.assertEquals("", descriptor1.getName());
        Assert.assertEquals("", descriptor1.getVersion());
        Assert.assertEquals(MetadataInstrument2.class.getName(), descriptor1.getId());
        Assert.assertFalse(descriptor1.isEnabled());
    }

    @Registration
    public static class MetadataInstrument2 extends TruffleInstrument {
        @Override
        protected void onCreate(Env env) {
        }
    }

    /*
     * Test onCreate and onDispose invocations for multiple instrument instances.
     */
    @Test
    public void testMultipleInstruments() throws IOException {
        run(""); // initialize

        MultipleInstanceInstrument.onCreateCounter = 0;
        MultipleInstanceInstrument.onDisposeCounter = 0;
        MultipleInstanceInstrument.constructor = 0;
        Instrument instrument1 = engine.getInstruments().get("testMultipleInstruments");
        instrument1.setEnabled(true);
        Assert.assertEquals(1, MultipleInstanceInstrument.constructor);
        Assert.assertEquals(1, MultipleInstanceInstrument.onCreateCounter);
        Assert.assertEquals(0, MultipleInstanceInstrument.onDisposeCounter);

        Instrument instrument = engine.getInstruments().get("testMultipleInstruments");
        instrument.setEnabled(true);
        Assert.assertEquals(1, MultipleInstanceInstrument.constructor);
        Assert.assertEquals(1, MultipleInstanceInstrument.onCreateCounter);
        Assert.assertEquals(0, MultipleInstanceInstrument.onDisposeCounter);

        instrument.setEnabled(false);
        Assert.assertEquals(1, MultipleInstanceInstrument.constructor);
        Assert.assertEquals(1, MultipleInstanceInstrument.onCreateCounter);
        Assert.assertEquals(1, MultipleInstanceInstrument.onDisposeCounter);

        instrument.setEnabled(true);
        Assert.assertEquals(2, MultipleInstanceInstrument.constructor);
        Assert.assertEquals(2, MultipleInstanceInstrument.onCreateCounter);
        Assert.assertEquals(1, MultipleInstanceInstrument.onDisposeCounter);

        instrument.setEnabled(false);
        Assert.assertEquals(2, MultipleInstanceInstrument.constructor);
        Assert.assertEquals(2, MultipleInstanceInstrument.onCreateCounter);
        Assert.assertEquals(2, MultipleInstanceInstrument.onDisposeCounter);
    }

    @Registration(id = "testMultipleInstruments")
    public static class MultipleInstanceInstrument extends TruffleInstrument {

        private static int onCreateCounter = 0;
        private static int onDisposeCounter = 0;
        private static int constructor = 0;

        public MultipleInstanceInstrument() {
            constructor++;
        }

        @Override
        protected void onCreate(Env env) {
            onCreateCounter++;
        }

        @Override
        protected void onDispose(Env env) {
            onDisposeCounter++;
        }
    }

    /*
     * Test exceptions from language instrumentation are not wrapped into InstrumentationExceptions.
     * Test that one language cannot instrument another.
     */
    @Test
    public void testLanguageInstrumentationAndExceptions() throws IOException {
        TestLanguageInstrumentationLanguage.installInstrumentsCounter = 0;
        TestLanguageInstrumentationLanguage.createContextCounter = 0;
        try {
            engine.eval(Source.newBuilder("ROOT(EXPRESSION)").name("unknown").mimeType("testLanguageInstrumentation").build());
            Assert.fail("expected exception");
        } catch (MyLanguageException e) {
            // we assert that MyLanguageException is not wrapped
        }
        Assert.assertEquals(1, TestLanguageInstrumentationLanguage.installInstrumentsCounter);
        Assert.assertEquals(1, TestLanguageInstrumentationLanguage.createContextCounter);

        // this should run isolated from the language instrumentation.
        run("STATEMENT");
    }

    @SuppressWarnings("serial")
    private static class MyLanguageException extends RuntimeException {

    }

    @TruffleLanguage.Registration(name = "", version = "", mimeType = "testLanguageInstrumentation")
    @ProvidedTags({InstrumentationTestLanguage.ExpressionNode.class, StandardTags.StatementTag.class})
    public static class TestLanguageInstrumentationLanguage extends TruffleLanguage<Void> {

        public static final TestLanguageInstrumentationLanguage INSTANCE = new TestLanguageInstrumentationLanguage();

        static int installInstrumentsCounter = 0;
        static int createContextCounter = 0;

        private static void installInstruments(Instrumenter instrumenter) {
            installInstrumentsCounter++;
            instrumenter.attachListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION).build(), new ExecutionEventListener() {
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

            instrumenter.attachListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.STATEMENT).build(), new ExecutionEventListener() {
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
            Instrumenter instrumenter = env.lookup(Instrumenter.class);
            Assert.assertNotNull("Instrumenter found", instrumenter);
            installInstruments(instrumenter);
            return null;
        }

        @Override
        protected CallTarget parse(ParsingRequest request) {
            return Truffle.getRuntime().createCallTarget(new RootNode(TestLanguageInstrumentationLanguage.class, null, null) {

                @Child private BaseNode base = InstrumentationTestLanguage.parse(request.getSource());

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

    }

    @Test
    public void testInstrumentException1() {
        engine.getInstruments().get("testInstrumentException1").setEnabled(true);

        Assert.assertTrue(getErr().contains("MyLanguageException"));
    }

    @Registration(name = "", version = "", id = "testInstrumentException1")
    public static class TestInstrumentException1 extends TruffleInstrument {

        @Override
        protected void onCreate(Env env) {
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
    public void testInstrumentException2() throws IOException {
        TestInstrumentException2.returnedExceptional = 0;
        TestInstrumentException2.returnedValue = 0;
        engine.getInstruments().get("testInstrumentException2").setEnabled(true);
        run("ROOT(EXPRESSION)");
        Assert.assertTrue(getErr().contains("MyLanguageException"));

        Assert.assertEquals(0, TestInstrumentException2.returnedExceptional);
        Assert.assertEquals(1, TestInstrumentException2.returnedValue);
    }

    @Registration(name = "", version = "", id = "testInstrumentException2")
    public static class TestInstrumentException2 extends TruffleInstrument {

        static int returnedExceptional = 0;
        static int returnedValue = 0;

        @Override
        protected void onCreate(Env env) {
            env.getInstrumenter().attachListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION).build(), new ExecutionEventListener() {

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
    public void testInstrumentException3() throws IOException {
        TestInstrumentException3.returnedExceptional = 0;
        TestInstrumentException3.onEnter = 0;
        engine.getInstruments().get("testInstrumentException3").setEnabled(true);
        run("ROOT(EXPRESSION)");
        Assert.assertTrue(getErr().contains("MyLanguageException"));
        Assert.assertEquals(0, TestInstrumentException3.returnedExceptional);
        Assert.assertEquals(1, TestInstrumentException3.onEnter);
    }

    @Registration(name = "", version = "", id = "testInstrumentException3")
    public static class TestInstrumentException3 extends TruffleInstrument {

        static int returnedExceptional = 0;
        static int onEnter = 0;

        @Override
        protected void onCreate(Env env) {
            env.getInstrumenter().attachListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION).build(), new ExecutionEventListener() {

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
        protected void onCreate(Env env) {
            env.getInstrumenter().attachFactory(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION).build(), new ExecutionEventNodeFactory() {
                public ExecutionEventNode create(EventContext context) {
                    createCalls++;
                    return new ExecutionEventNode() {
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
        protected void onCreate(final Env env) {
            env.getInstrumenter().attachFactory(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.STATEMENT).build(), new ExecutionEventNodeFactory() {
                public ExecutionEventNode create(EventContext context) {

                    final CallTarget target;
                    try {
                        target = env.parse(Source.newBuilder("EXPRESSION").name("unknown").mimeType(InstrumentationTestLanguage.MIME_TYPE).build());
                    } catch (IOException e) {
                        throw new AssertionError();
                    }

                    return new ExecutionEventNode() {
                        @Child private DirectCallNode directCall = Truffle.getRuntime().createDirectCallNode(target);

                        @Override
                        public void onEnter(VirtualFrame frame) {
                            onStatement++;
                            directCall.call(new Object[0]);
                        }

                    };
                }
            });

            env.getInstrumenter().attachListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION).build(), new ExecutionEventListener() {

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
        protected void onCreate(final Env env) {
            env.getInstrumenter().attachFactory(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.STATEMENT).build(), new ExecutionEventNodeFactory() {
                public ExecutionEventNode create(EventContext context) {

                    final CallTarget target;
                    try {
                        target = context.parseInContext(Source.newBuilder("EXPRESSION").name("unknown").mimeType(InstrumentationTestLanguage.MIME_TYPE).build());
                    } catch (IOException e) {
                        throw new AssertionError();
                    }

                    return new ExecutionEventNode() {
                        @Child private DirectCallNode directCall = Truffle.getRuntime().createDirectCallNode(target);

                        @Override
                        public void onEnter(VirtualFrame frame) {
                            onStatement++;
                            directCall.call(new Object[0]);
                        }

                    };
                }
            });

            env.getInstrumenter().attachListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION).build(), new ExecutionEventListener() {

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
        protected void onCreate(final Env env) {
            env.getInstrumenter().attachListener(SourceSectionFilter.ANY, new ExecutionEventListener() {
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
        protected void onCreate(final Env env) {
            env.getInstrumenter().attachListener(SourceSectionFilter.ANY, new ExecutionEventListener() {
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

    @Test
    public void testOutputConsumer() throws IOException {
        // print without instruments
        String rout = run("PRINT(OUT, InitialToStdOut)");
        Assert.assertEquals("InitialToStdOut", rout);
        run("PRINT(ERR, InitialToStdErr)");
        Assert.assertEquals("InitialToStdErr", err.toString());
        err.reset();

        // turn instruments on
        engine.getInstruments().get("testOutputConsumerArray").setEnabled(true);
        engine.getInstruments().get("testOutputConsumerPiped").setEnabled(true);
        engine.eval(lines("PRINT(OUT, OutputToStdOut)"));
        engine.eval(lines("PRINT(ERR, OutputToStdErr)"));
        // test that the output goes eveywhere
        Assert.assertEquals("OutputToStdOut", getOut());
        Assert.assertEquals("OutputToStdOut", TestOutputConsumerArray.getOut());
        Assert.assertEquals("OutputToStdErr", getErr());
        Assert.assertEquals("OutputToStdErr", TestOutputConsumerArray.getErr());
        CharBuffer buff = CharBuffer.allocate(100);
        TestOutputConsumerPiped.fromOut.read(buff);
        buff.flip();
        Assert.assertEquals("OutputToStdOut", buff.toString());
        buff.rewind();
        TestOutputConsumerPiped.fromErr.read(buff);
        buff.flip();
        Assert.assertEquals("OutputToStdErr", buff.toString());
        buff.rewind();

        // close piped err stream and test that print still works
        TestOutputConsumerPiped.fromErr.close();
        engine.eval(lines("PRINT(OUT, MoreOutputToStdOut)"));
        engine.eval(lines("PRINT(ERR, MoreOutputToStdErr)"));
        Assert.assertEquals("OutputToStdOutMoreOutputToStdOut", out.toString());
        Assert.assertEquals("OutputToStdOutMoreOutputToStdOut", TestOutputConsumerArray.getOut());
        String errorMsg = "java.lang.Exception: Output operation write(B[II) failed for java.io.PipedOutputStream";
        Assert.assertTrue(err.toString(), err.toString().startsWith("OutputToStdErr" + errorMsg));
        Assert.assertTrue(err.toString(), err.toString().endsWith("MoreOutputToStdErr"));
        Assert.assertEquals("OutputToStdErrMoreOutputToStdErr", TestOutputConsumerArray.getErr());
        buff.limit(buff.capacity());
        TestOutputConsumerPiped.fromOut.read(buff);
        buff.flip();
        Assert.assertEquals("MoreOutputToStdOut", buff.toString());
        out.reset();
        err.reset();

        // the I/O error is not printed again
        engine.eval(lines("PRINT(ERR, EvenMoreOutputToStdErr)"));
        Assert.assertEquals("EvenMoreOutputToStdErr", err.toString());
        Assert.assertEquals("OutputToStdErrMoreOutputToStdErrEvenMoreOutputToStdErr", TestOutputConsumerArray.getErr());

        // instruments disabled
        engine.getInstruments().get("testOutputConsumerArray").setEnabled(false);
        engine.getInstruments().get("testOutputConsumerPiped").setEnabled(false);
        out.reset();
        err.reset();
        engine.eval(lines("PRINT(OUT, FinalOutputToStdOut)"));
        engine.eval(lines("PRINT(ERR, FinalOutputToStdErr)"));
        Assert.assertEquals("FinalOutputToStdOut", out.toString());
        Assert.assertEquals("FinalOutputToStdErr", err.toString());
        // nothing more printed to the disabled instrument
        Assert.assertEquals("OutputToStdOutMoreOutputToStdOut", TestOutputConsumerArray.getOut());
        Assert.assertEquals("OutputToStdErrMoreOutputToStdErrEvenMoreOutputToStdErr", TestOutputConsumerArray.getErr());
    }

    @Registration(id = "testOutputConsumerArray")
    public static class TestOutputConsumerArray extends TruffleInstrument {

        static ByteArrayOutputStream out = new ByteArrayOutputStream();
        static ByteArrayOutputStream err = new ByteArrayOutputStream();

        @Override
        protected void onCreate(Env env) {
            env.getInstrumenter().attachOutConsumer(out);
            env.getInstrumenter().attachErrConsumer(err);
        }

        static String getOut() {
            return new String(out.toByteArray());
        }

        static String getErr() {
            return new String(err.toByteArray());
        }
    }

    @Registration(id = "testOutputConsumerPiped")
    public static class TestOutputConsumerPiped extends TruffleInstrument {

        static PipedOutputStream out = new PipedOutputStream();
        static Reader fromOut;
        static PipedOutputStream err = new PipedOutputStream();
        static Reader fromErr;

        public TestOutputConsumerPiped() throws IOException {
            fromOut = new InputStreamReader(new PipedInputStream(out));
            fromErr = new InputStreamReader(new PipedInputStream(err));
        }

        @Override
        protected void onCreate(Env env) {
            env.getInstrumenter().attachOutConsumer(out);
            env.getInstrumenter().attachErrConsumer(err);
        }

        Reader fromOut() {
            return fromOut;
        }

        Reader fromErr() {
            return fromErr;
        }
    }

    /*
     * Tests for debugger or any other clients that cancel execution while halted
     */

    @Test
    public void testKillExceptionOnEnter() throws IOException {
        engine.getInstruments().get("testKillQuitException").setEnabled(true);
        TestKillQuitException.exceptionOnEnter = new MyKillException();
        TestKillQuitException.exceptionOnReturnValue = null;
        TestKillQuitException.returnExceptionalCount = 0;
        try {
            run("STATEMENT");
            Assert.fail("KillException in onEnter() cancels engine execution");
        } catch (MyKillException ex) {
        }
        Assert.assertEquals("KillException is not an execution event", 0, TestKillQuitException.returnExceptionalCount);
    }

    @Test
    public void testKillExceptionOnReturnValue() throws IOException {
        engine.getInstruments().get("testKillQuitException").setEnabled(true);
        TestKillQuitException.exceptionOnEnter = null;
        TestKillQuitException.exceptionOnReturnValue = new MyKillException();
        TestKillQuitException.returnExceptionalCount = 0;
        try {
            run("STATEMENT");
            Assert.fail("KillException in onReturnValue() cancels engine execution");
        } catch (MyKillException ex) {
        }
        Assert.assertEquals("KillException is not an execution event", 0, TestKillQuitException.returnExceptionalCount);
    }

    @Registration(id = "testKillQuitException")
    public static class TestKillQuitException extends TruffleInstrument {

        static Error exceptionOnEnter = null;
        static Error exceptionOnReturnValue = null;
        static int returnExceptionalCount = 0;

        @Override
        protected void onCreate(final Env env) {
            env.getInstrumenter().attachListener(SourceSectionFilter.ANY, new ExecutionEventListener() {
                public void onEnter(EventContext context, VirtualFrame frame) {
                    if (exceptionOnEnter != null) {
                        throw exceptionOnEnter;
                    }
                }

                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                    if (exceptionOnReturnValue != null) {
                        throw exceptionOnReturnValue;
                    }
                }

                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                    returnExceptionalCount++;
                }
            });
        }
    }

    /*
     * Use tags that are not declarded as required.
     */
    @Test
    public void testUsedTagNotRequired1() throws IOException {
        TestInstrumentNonInstrumentable1.onStatement = 0;

        engine.getInstruments().get("testUsedTagNotRequired1").setEnabled(true);
        run("ROOT()");

        Assert.assertEquals(0, TestInstrumentNonInstrumentable1.onStatement);
    }

    @Registration(id = "testUsedTagNotRequired1")
    public static class TestUsedTagNotRequired1 extends TruffleInstrument {

        private static class Foobar {

        }

        @Override
        protected void onCreate(final Env env) {
            try {
                env.getInstrumenter().attachListener(SourceSectionFilter.newBuilder().tagIs(Foobar.class).build(), new ExecutionEventListener() {
                    public void onEnter(EventContext context, VirtualFrame frame) {
                    }

                    public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                    }

                    public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                    }
                });
                Assert.fail();
            } catch (IllegalArgumentException e) {
                Assert.assertEquals(
                                "The attached filter SourceSectionFilter[tag is one of [foobar0]] references the " +
                                                "following tags [foobar0] which are not declared as required by the instrument. To fix " +
                                                "this annotate the instrument class com.oracle.truffle.api.instrumentation." +
                                                "InstrumentationTest$TestUsedTagNotRequired1 with @RequiredTags({foobar0}).",
                                e.getMessage());
            }
        }
    }

    /*
     * Test behavior of queryTags when used with instruments
     */
    @Test
    public void testQueryTags1() throws IOException {
        Instrument instrument = engine.getInstruments().get("testIsNodeTaggedWith1");
        instrument.setEnabled(true);
        Instrumenter instrumenter = instrument.lookup(Instrumenter.class);

        TestIsNodeTaggedWith1.expressionNode = null;
        TestIsNodeTaggedWith1.statementNode = null;

        Assert.assertTrue(instrumenter.queryTags(new Node() {
        }).isEmpty());

        run("STATEMENT(EXPRESSION)");

        assertTags(instrumenter.queryTags(TestIsNodeTaggedWith1.expressionNode), InstrumentationTestLanguage.EXPRESSION);
        assertTags(instrumenter.queryTags(TestIsNodeTaggedWith1.statementNode), InstrumentationTestLanguage.STATEMENT);

        try {
            instrumenter.queryTags(null);
            Assert.fail();
        } catch (NullPointerException e) {
        }
    }

    private static void assertTags(Set<Class<?>> tags, Class<?>... expectedTags) {
        Assert.assertEquals(expectedTags.length, tags.size());
        for (Class<?> clazz : expectedTags) {
            Assert.assertTrue("Tag: " + clazz, tags.contains(clazz));
        }
    }

    /*
     * Test behavior of queryTags when used with languages
     */
    @Test
    public void testQueryTags2() throws IOException {
        Instrument instrument = engine.getInstruments().get("testIsNodeTaggedWith1");
        instrument.setEnabled(true);
        TestIsNodeTaggedWith1.expressionNode = null;
        TestIsNodeTaggedWith1.statementNode = null;
        TestIsNodeTaggedWith1Language.instrumenter = null;

        Source otherLanguageSource = Source.newBuilder("STATEMENT(EXPRESSION)").name("unknown").mimeType("testIsNodeTaggedWith1").build();
        run(otherLanguageSource);

        Instrumenter instrumenter = TestIsNodeTaggedWith1Language.instrumenter;

        Node languageExpression = TestIsNodeTaggedWith1.expressionNode;
        Node languageStatement = TestIsNodeTaggedWith1.statementNode;

        assertTags(instrumenter.queryTags(languageExpression), InstrumentationTestLanguage.EXPRESSION);
        assertTags(instrumenter.queryTags(languageStatement), InstrumentationTestLanguage.STATEMENT);

        TestIsNodeTaggedWith1.expressionNode = null;
        TestIsNodeTaggedWith1.statementNode = null;

        run("EXPRESSION");

        // fail if called with nodes from a different language
        Node otherLanguageExpression = TestIsNodeTaggedWith1.expressionNode;
        try {
            instrumenter.queryTags(otherLanguageExpression);
            Assert.fail();
        } catch (IllegalArgumentException e) {
        }

    }

    @TruffleLanguage.Registration(name = "", version = "", mimeType = "testIsNodeTaggedWith1")
    @ProvidedTags({InstrumentationTestLanguage.ExpressionNode.class, StandardTags.StatementTag.class})
    public static class TestIsNodeTaggedWith1Language extends TruffleLanguage<Void> {

        public static final TestIsNodeTaggedWith1Language INSTANCE = new TestIsNodeTaggedWith1Language();

        static Instrumenter instrumenter;

        @Override
        protected Void createContext(com.oracle.truffle.api.TruffleLanguage.Env env) {
            instrumenter = env.lookup(Instrumenter.class);
            return null;
        }

        @Override
        protected CallTarget parse(ParsingRequest request) {
            return Truffle.getRuntime().createCallTarget(new RootNode(TestIsNodeTaggedWith1Language.class, null, null) {

                @Child private BaseNode base = InstrumentationTestLanguage.parse(request.getSource());

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

    }

    @Registration(id = "testIsNodeTaggedWith1")
    public static class TestIsNodeTaggedWith1 extends TruffleInstrument {

        static Node expressionNode;
        static Node statementNode;

        @Override
        protected void onCreate(final Env env) {
            env.registerService(env.getInstrumenter());
            env.getInstrumenter().attachFactory(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION).build(), new ExecutionEventNodeFactory() {

                public ExecutionEventNode create(EventContext context) {
                    expressionNode = context.getInstrumentedNode();
                    return new ExecutionEventNode() {
                    };
                }
            });

            env.getInstrumenter().attachFactory(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.STATEMENT).build(), new ExecutionEventNodeFactory() {

                public ExecutionEventNode create(EventContext context) {
                    statementNode = context.getInstrumentedNode();
                    return new ExecutionEventNode() {
                    };
                }
            });
        }
    }

    private static final class MyKillException extends ThreadDeath {
        static final long serialVersionUID = 1;
    }
}
