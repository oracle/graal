/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation.test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.test.polyglot.ProxyInstrument;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;

/**
 * We test that instrument exceptions do not affect application execution.
 */
public class InstrumentationNoExceptionsTest extends AbstractInstrumentationTest {

    @Before
    @Override
    public void setup() {
        Context.Builder builder = Context.newBuilder().allowAllAccess(true).option("engine.InstrumentExceptionsAreThrown", "false").out(out).err(err);
        setupEnv(builder.build(), new TestDecentInstrument());
        engine = context.getEngine();
    }

    @Test
    public void testInstrumentExceptionOnEnter() throws Exception {
        AtomicInteger returnedValue = new AtomicInteger();
        AtomicInteger returnedExceptional = new AtomicInteger();
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION).build(), new ExecutionEventListener() {

            @Override
            public void onEnter(EventContext ctx, VirtualFrame frame) {
                throw new MyInstrumentException();
            }

            @Override
            public void onReturnValue(EventContext ctx, VirtualFrame frame, Object result) {
                returnedValue.incrementAndGet();
            }

            @Override
            public void onReturnExceptional(EventContext ctx, VirtualFrame frame, Throwable exception) {
                returnedExceptional.incrementAndGet();
            }
        });
        run("ROOT(EXPRESSION)");
        String errOut = getErr();
        Assert.assertTrue(errOut, errOut.contains("MyInstrumentException"));
        Assert.assertEquals(1, returnedValue.get());
        Assert.assertEquals(0, returnedExceptional.get());
        TestDecentInstrument.assertHitOK(1);
    }

    @Test
    public void testInstrumentExceptionOnReturn() throws Exception {
        AtomicInteger entered = new AtomicInteger();
        AtomicInteger returnedExceptional = new AtomicInteger();
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION).build(), new ExecutionEventListener() {

            @Override
            public void onEnter(EventContext ctx, VirtualFrame frame) {
                entered.incrementAndGet();
            }

            @Override
            public void onReturnValue(EventContext ctx, VirtualFrame frame, Object result) {
                throw new MyInstrumentException();
            }

            @Override
            public void onReturnExceptional(EventContext ctx, VirtualFrame frame, Throwable exception) {
                returnedExceptional.incrementAndGet();
            }
        });
        run("ROOT(EXPRESSION)");
        String errOut = getErr();
        Assert.assertTrue(errOut, errOut.contains("MyInstrumentException"));
        Assert.assertEquals(1, entered.get());
        Assert.assertEquals(0, returnedExceptional.get());
        TestDecentInstrument.assertHitOK(1);
    }

    @Test
    public void testInstrumentExceptionOnReturnExceptional() throws Exception {
        AtomicInteger entered = new AtomicInteger();
        AtomicInteger returnedValue = new AtomicInteger();
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION).build(), new ExecutionEventListener() {

            @Override
            public void onEnter(EventContext ctx, VirtualFrame frame) {
                entered.incrementAndGet();
            }

            @Override
            public void onReturnValue(EventContext ctx, VirtualFrame frame, Object result) {
                returnedValue.incrementAndGet();
            }

            @Override
            public void onReturnExceptional(EventContext ctx, VirtualFrame frame, Throwable exception) {
                throw new MyInstrumentException();
            }
        });
        try {
            run("ROOT(EXPRESSION(THROW(test, test)))");
            Assert.fail();
        } catch (PolyglotException ex) {
            Assert.assertEquals("test", ex.getMessage());
        }
        String errOut = getErr();
        Assert.assertTrue(errOut, errOut.contains("MyInstrumentException"));
        Assert.assertEquals(1, entered.get());
        Assert.assertEquals(0, returnedValue.get());
        TestDecentInstrument.assertHitErr(1);
    }

    @Test
    public void testInstrumentExceptionOnUnwind() throws Exception {
        testExceptionInInstrument("ROOT(STATEMENT(EXPRESSION))", (ins) -> ins.attachExecutionEventListener(
                        SourceSectionFilter.ANY,
                        new ExecutionEventListener() {
                            @Override
                            public void onEnter(EventContext ctx, VirtualFrame frame) {
                                if (ctx.hasTag(StandardTags.ExpressionTag.class)) {
                                    throw ctx.createUnwind(ctx);
                                }
                            }

                            @Override
                            public void onReturnValue(EventContext ctx, VirtualFrame frame, Object result) {
                            }

                            @Override
                            public void onReturnExceptional(EventContext ctx, VirtualFrame frame, Throwable exception) {
                            }

                            @Override
                            public Object onUnwind(EventContext ctx, VirtualFrame frame, Object info) {
                                if (info == ctx) {
                                    throw new MyInstrumentException();
                                } else {
                                    return 1;
                                }
                            }
                        }), false);
    }

    @Test
    public void testExceptionInFactoryCreate() throws Exception {
        testExceptionInInstrument((ins) -> ins.attachExecutionEventFactory(
                        SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION).build(),
                        new ExecutionEventNodeFactory() {
                            @Override
                            public ExecutionEventNode create(EventContext ctx) {
                                throw new MyInstrumentException();
                            }
                        }));
    }

    @Test
    public void testInstrumentExceptionOnInputValue() throws Exception {
        SourceSectionFilter filter = SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION).build();
        testExceptionInInstrument("ROOT(EXPRESSION(EXPRESSION))", (ins) -> ins.attachExecutionEventFactory(filter, filter, new ExecutionEventNodeFactory() {

            @Override
            public ExecutionEventNode create(EventContext ctx) {
                return new ExecutionEventNode() {
                    @Override
                    protected void onInputValue(VirtualFrame frame, EventContext inputContext, int inputIndex, Object inputValue) {
                        throw new MyInstrumentException();
                    }
                };
            }

        }), false);
        TestDecentInstrument.assertHitOK(2);
    }

    @Test
    public void testInstrumentExceptionOnInputValueFilter() throws Exception {
        SourceSectionFilter filter = SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION).build();
        SourceSectionFilter buggySourceSectionFilter = SourceSectionFilter.newBuilder().sourceIs((s) -> {
            throw new MyInstrumentException();
        }).build();
        testExceptionInInstrument("ROOT(EXPRESSION(EXPRESSION))", (ins) -> ins.attachExecutionEventFactory(filter, buggySourceSectionFilter, new ExecutionEventNodeFactory() {
            @Override
            public ExecutionEventNode create(EventContext ctx) {
                return new ExecutionEventNode() {
                };
            }
        }), false);
        TestDecentInstrument.assertHitOK(2);
    }

    @Test
    public void testExceptionInLoadSource() throws Exception {
        testExceptionInInstrument((ins) -> ins.attachLoadSourceListener(SourceFilter.ANY, (s) -> {
            throw new MyInstrumentException();
        }, true));
    }

    @Test
    public void testExceptionInExecuteSource() throws Exception {
        testExceptionInInstrument((ins) -> ins.attachExecuteSourceListener(SourceFilter.ANY, (s) -> {
            throw new MyInstrumentException();
        }, true));
    }

    @Test
    public void testExceptionInLoadSourceFilterPredicate() throws Exception {
        SourceFilter buggySourceFilter = SourceFilter.newBuilder().sourceIs((s) -> {
            throw new MyInstrumentException();
        }).build();
        testExceptionInInstrument((ins) -> ins.attachLoadSourceListener(buggySourceFilter, (s) -> {
        }, true));
    }

    @Test
    public void testExceptionInExecuteSourceFilterPredicate() throws Exception {
        SourceFilter buggySourceFilter = SourceFilter.newBuilder().sourceIs((s) -> {
            throw new MyInstrumentException();
        }).build();
        testExceptionInInstrument((ins) -> ins.attachExecuteSourceListener(buggySourceFilter, (s) -> {
        }, true));
    }

    @Test
    public void testExceptionInLoadSourceSectionFilterPredicate1() throws Exception {
        SourceSectionFilter buggySourceSectionFilter = SourceSectionFilter.newBuilder().sourceIs((s) -> {
            throw new MyInstrumentException();
        }).build();
        testExceptionInInstrument((ins) -> ins.attachLoadSourceSectionListener(buggySourceSectionFilter, (s) -> {
        }, true));
    }

    @Test
    public void testExceptionInLoadSourceSectionFilterPredicate2() throws Exception {
        SourceSectionFilter buggySourceSectionFilter = SourceSectionFilter.newBuilder().rootNameIs((s) -> {
            throw new MyInstrumentException();
        }).build();
        testExceptionInInstrument((ins) -> ins.attachLoadSourceSectionListener(buggySourceSectionFilter, (s) -> {
        }, true));
    }

    @Test
    public void testExceptionInExecutionEventSectionFilterPredicate1() throws Exception {
        SourceSectionFilter buggySourceSectionFilter = SourceSectionFilter.newBuilder().sourceIs((s) -> {
            throw new MyInstrumentException();
        }).build();
        testExceptionInInstrument((ins) -> ins.attachExecutionEventListener(buggySourceSectionFilter, new ExecutionEventListener() {
            @Override
            public void onEnter(EventContext ctx, VirtualFrame frame) {
            }

            @Override
            public void onReturnValue(EventContext ctx, VirtualFrame frame, Object result) {
            }

            @Override
            public void onReturnExceptional(EventContext ctx, VirtualFrame frame, Throwable exception) {
            }
        }));
    }

    @Test
    public void testExceptionInExecutionEventSectionFilterPredicate2() throws Exception {
        SourceSectionFilter buggySourceSectionFilter = SourceSectionFilter.newBuilder().rootNameIs((s) -> {
            throw new MyInstrumentException();
        }).build();
        testExceptionInInstrument((ins) -> ins.attachExecutionEventListener(buggySourceSectionFilter, new ExecutionEventListener() {
            @Override
            public void onEnter(EventContext ctx, VirtualFrame frame) {
            }

            @Override
            public void onReturnValue(EventContext ctx, VirtualFrame frame, Object result) {
            }

            @Override
            public void onReturnExceptional(EventContext ctx, VirtualFrame frame, Throwable exception) {
            }
        }));
    }

    private void testExceptionInInstrument(Consumer<Instrumenter> attachInstrumentation) throws Exception {
        testExceptionInInstrument("ROOT(EXPRESSION)", attachInstrumentation, true);
    }

    private void testExceptionInInstrument(String code, Consumer<Instrumenter> attachInstrumentation, boolean assertDecentInstrument) throws Exception {
        attachInstrumentation.accept(instrumentEnv.getInstrumenter());
        run(code);
        String errOut = getErr();
        Assert.assertTrue(errOut, errOut.contains("MyInstrumentException"));
        if (assertDecentInstrument) {
            TestDecentInstrument.assertHitOK(1);
        }
    }

    public static class TestDecentInstrument extends ProxyInstrument {

        static int entered;
        static int returnedValue;
        static int returnedExceptional;

        TestDecentInstrument() {
            entered = 0;
            returnedValue = 0;
            returnedExceptional = 0;
        }

        static void assertHitOK(int count) {
            Assert.assertEquals(count, entered);
            Assert.assertEquals(count, returnedValue);
            Assert.assertEquals(0, returnedExceptional);
        }

        static void assertHitErr(int count) {
            Assert.assertEquals(count, entered);
            Assert.assertEquals(count - 1, returnedValue);
            Assert.assertEquals(1, returnedExceptional);
        }

        @Override
        protected void onCreate(Env env) {
            super.onCreate(env);
            env.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION).build(), new ExecutionEventListener() {

                @Override
                public void onEnter(EventContext ctx, VirtualFrame frame) {
                    entered++;
                }

                @Override
                public void onReturnValue(EventContext ctx, VirtualFrame frame, Object result) {
                    returnedValue++;
                }

                @Override
                public void onReturnExceptional(EventContext ctx, VirtualFrame frame, Throwable exception) {
                    returnedExceptional++;
                }
            });
        }
    }

    @SuppressWarnings("serial")
    private static class MyInstrumentException extends RuntimeException {
    }

}
