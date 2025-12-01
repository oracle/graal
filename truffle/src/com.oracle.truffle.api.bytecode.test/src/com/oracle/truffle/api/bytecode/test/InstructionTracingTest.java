/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.bytecode.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeDescriptor;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeTier;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.bytecode.InstructionTracer;
import com.oracle.truffle.api.bytecode.Instrumentation;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.debug.HistogramInstructionTracer;
import com.oracle.truffle.api.bytecode.debug.HistogramInstructionTracer.Histogram;
import com.oracle.truffle.api.bytecode.debug.PrintInstructionTracer;
import com.oracle.truffle.api.bytecode.test.InstructionTracingTest.InstructionTracingRootNode.OtherInstrument;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public class InstructionTracingTest extends AbstractInstructionTest {

    static final InstructionTracingRootNodeGen.Bytecode BYTECODE = InstructionTracingRootNodeGen.BYTECODE;

    @Test
    public void testLocalTracing() {
        InstructionTracingRootNode node = BYTECODE.create(null, BytecodeConfig.DEFAULT, (b) -> {
            b.beginRoot();
            b.beginReturn();
            b.beginOtherInstrument();
            b.emitLoadArgument(0);
            b.endOtherInstrument();
            b.endReturn();
            b.endRoot();
        }).getNode(0);

        List<Instruction> instructions = new ArrayList<>();
        InstructionTracer t = new InstructionTracer() {
            public void onInstructionEnter(InstructionAccess access, BytecodeNode bytecode, int bytecodeIndex, Frame frame) {
                instructions.add(access.getTracedInstruction(bytecode, bytecodeIndex));
            }
        };
        node.getRootNodes().addInstructionTracer(t);

        assertEquals(0, instructions.size());

        assertEquals(42, node.getCallTarget().call(42));

        assertEquals(2, instructions.size());
        assertEquals("load.argument", instructions.get(0).getName());
        assertEquals("return", instructions.get(1).getName());

        instructions.clear();

        assertEquals(42, node.getCallTarget().call(42));
        assertEquals(2, instructions.size());
        assertEquals("load.argument", instructions.get(0).getName());
        assertEquals("return", instructions.get(1).getName());

        instructions.clear();

        node.getRootNodes().removeInstructionTracer(t);
        assertEquals(42, node.getCallTarget().call(42));
        assertTrue(instructions.isEmpty());
    }

    private Context context;

    private InstructionTracingLanguage setupLanguage(Context.Builder b) {
        tearDownContext();
        context = b.build();
        context.initialize(InstructionTracingLanguage.ID);
        context.enter();
        return InstructionTracingLanguage.REF.get(null);
    }

    private InstructionTracingLanguage setupLanguage() {
        return setupLanguage(Context.newBuilder(InstructionTracingLanguage.ID));
    }

    @After
    public void tearDownContext() {
        if (context != null) {
            context.close();
            context = null;
        }
    }

    static final InstructionTracingDisabledRootNodeGen.Bytecode DISABLED_BYTECODE = InstructionTracingDisabledRootNodeGen.BYTECODE;

    @Test
    public void testInstructionTracingDisabled() {
        InstructionTracingLanguage language = setupLanguage();
        InstructionTracingDisabledRootNode node = DISABLED_BYTECODE.create(language, BytecodeConfig.DEFAULT, (b) -> {
            b.beginRoot();
            b.beginReturn();
            b.emitLoadArgument(0);
            b.endReturn();
            b.endRoot();
        }).getNode(0);

        InstructionTracer disabledTracer = new InstructionTracer() {
            @Override
            public void onInstructionEnter(InstructionAccess access, BytecodeNode bytecode, int bytecodeIndex, Frame frame) {
                throw new UnsupportedOperationException();
            }
        };

        assertFails(() -> {
            node.getRootNodes().addInstructionTracer(disabledTracer);
        }, UnsupportedOperationException.class, (e) -> {
            assertEquals("Instruction tracing is not enabled for this bytecode root node. Enable with @GenerateBytecode(enableInstructionTracing=true) to use instruction tracing.", e.getMessage());
        });

        assertFails(() -> {
            node.getRootNodes().removeInstructionTracer(disabledTracer);
        }, UnsupportedOperationException.class, (e) -> {
            assertEquals("Instruction tracing is not enabled for this bytecode root node. Enable with @GenerateBytecode(enableInstructionTracing=true) to use instruction tracing.", e.getMessage());
        });
        assertFails(() -> {
            DISABLED_BYTECODE.addInstructionTracer(language, disabledTracer);
        }, UnsupportedOperationException.class, (e) -> {
            assertEquals("Instruction tracing is not enabled for this bytecode root node. Enable with @GenerateBytecode(enableInstructionTracing=true) to use instruction tracing.", e.getMessage());
        });

        assertFails(() -> {
            DISABLED_BYTECODE.removeInstructionTracer(language, disabledTracer);
        }, UnsupportedOperationException.class, (e) -> {
            assertEquals("Instruction tracing is not enabled for this bytecode root node. Enable with @GenerateBytecode(enableInstructionTracing=true) to use instruction tracing.", e.getMessage());
        });

        // make sure interpreters without instruction tracers work
        assertEquals(42, node.getCallTarget().call(42));
    }

    /*
     * Create call target and then attach the instruction tracer.
     */
    @Test
    public void testLanguageTracing1() {
        InstructionTracingLanguage language = setupLanguage();
        InstructionTracingRootNode node = BYTECODE.create(language, BytecodeConfig.DEFAULT, (b) -> {
            b.beginRoot();
            b.beginReturn();
            b.beginOtherInstrument();
            b.emitLoadArgument(0);
            b.endOtherInstrument();
            b.endReturn();
            b.endRoot();
        }).getNode(0);

        assertInstructions(node,
                        "load.argument",
                        "return");

        List<Instruction> instructions = new ArrayList<>();
        InstructionTracer t = new InstructionTracer() {
            public void onInstructionEnter(InstructionAccess access, BytecodeNode bytecode, int bytecodeIndex, Frame frame) {
                instructions.add(access.getTracedInstruction(bytecode, bytecodeIndex));
            }
        };

        assertEquals(42, node.getCallTarget().call(42));

        assertEquals(0, instructions.size());
        BYTECODE.addInstructionTracer(language, t);

        assertInstructions(node, false,
                        "trace.instruction",
                        "load.argument",
                        "trace.instruction",
                        "return");

        assertEquals(42, node.getCallTarget().call(42));

        assertEquals(2, instructions.size());
        assertEquals("load.argument", instructions.get(0).getName());
        assertEquals("return", instructions.get(1).getName());

        instructions.clear();

        assertEquals(42, node.getCallTarget().call(42));

        assertEquals(2, instructions.size());
        assertEquals("load.argument", instructions.get(0).getName());
        assertEquals("return", instructions.get(1).getName());

        instructions.clear();

        BYTECODE.removeInstructionTracer(language, t);

        assertEquals(42, node.getCallTarget().call(42));
        assertEquals(0, instructions.size());
    }

    /*
     * Attach instruction tracer first and then create the call target.
     */
    @Test
    public void testLanguageTracing2() {
        List<Instruction> instructions = new ArrayList<>();
        InstructionTracer t = new InstructionTracer() {
            public void onInstructionEnter(InstructionAccess access, BytecodeNode bytecode, int bytecodeIndex, Frame frame) {
                instructions.add(access.getTracedInstruction(bytecode, bytecodeIndex));
            }
        };
        InstructionTracingLanguage language = setupLanguage();
        BYTECODE.addInstructionTracer(language, t);

        InstructionTracingRootNode node = BYTECODE.create(language, BytecodeConfig.DEFAULT, (b) -> {
            b.beginRoot();
            b.beginReturn();
            b.beginOtherInstrument();
            b.emitLoadArgument(0);
            b.endOtherInstrument();
            b.endReturn();
            b.endRoot();
        }).getNode(0);

        assertInstructions(node, false,
                        "trace.instruction",
                        "load.argument",
                        "trace.instruction",
                        "return");

        assertEquals(0, instructions.size());

        assertEquals(42, node.getCallTarget().call(42));

        assertEquals(2, instructions.size());
        assertEquals("load.argument", instructions.get(0).getName());
        assertEquals("return", instructions.get(1).getName());
        instructions.clear();

        assertEquals(42, node.getCallTarget().call(42));

        assertEquals(2, instructions.size());
        assertEquals("load.argument", instructions.get(0).getName());
        assertEquals("return", instructions.get(1).getName());

        instructions.clear();

        assertEquals(42, node.getCallTarget().call(42));

        assertEquals(2, instructions.size());
        assertEquals("load.argument", instructions.get(0).getName());
        assertEquals("return", instructions.get(1).getName());

        instructions.clear();

        BYTECODE.removeInstructionTracer(language, t);

        assertEquals(42, node.getCallTarget().call(42));
        assertEquals(0, instructions.size());
    }

    @Test
    public void testInstrumentation() {
        InstructionTracingLanguage language = setupLanguage();
        InstructionTracingRootNode node = BYTECODE.create(language, BytecodeConfig.DEFAULT, (b) -> {
            b.beginRoot();
            b.beginReturn();
            b.beginOtherInstrument();
            b.emitLoadArgument(0);
            b.endOtherInstrument();
            b.endReturn();
            b.endRoot();
        }).getNode(0);

        // we need to at least call getCallTarget() here
        assertEquals(42, node.getCallTarget().call(42));

        assertInstructions(node,
                        "load.argument",
                        "return");

        // add the instruc
        BYTECODE.update(language, BYTECODE.newConfigBuilder().addInstrumentation(OtherInstrument.class).build());

        assertInstructions(node,
                        "load.argument",
                        "c.OtherInstrument",
                        "return");

        assertEquals(43, node.getCallTarget().call(42));

        List<Instruction> instructions = new ArrayList<>();
        InstructionTracer t = new InstructionTracer() {
            public void onInstructionEnter(InstructionAccess access, BytecodeNode bytecode, int bytecodeIndex, Frame frame) {
                instructions.add(access.getTracedInstruction(bytecode, bytecodeIndex));
            }
        };
        BYTECODE.addInstructionTracer(language, t);

        assertInstructions(node, false,
                        "trace.instruction",
                        "load.argument",
                        "trace.instruction",
                        "c.OtherInstrument",
                        "trace.instruction",
                        "return");

        assertEquals(43, node.getCallTarget().call(42));

        assertEquals(3, instructions.size());
        assertEquals("load.argument", instructions.get(0).getName());
        assertEquals("c.OtherInstrument", instructions.get(1).getName());
        assertTrue(instructions.get(1).getDescriptor().isInstrumentation());
        assertEquals("return", instructions.get(2).getName());

        /*
         * Test new node now already has all config enabled on create without execution.
         */
        InstructionTracingRootNode newNode = BYTECODE.create(language, BytecodeConfig.DEFAULT, (b) -> {
            b.beginRoot();
            b.beginReturn();
            b.beginOtherInstrument();
            b.emitLoadArgument(0);
            b.endOtherInstrument();
            b.endReturn();
            b.endRoot();
        }).getNode(0);

        assertInstructions(newNode, false,
                        "trace.instruction",
                        "load.argument",
                        "trace.instruction",
                        "c.OtherInstrument",
                        "trace.instruction",
                        "return");
    }

    @Test
    public void testEngineTracing() {
        Context.Builder cb = Context.newBuilder(InstructionTracingLanguage.ID).option("engine.TraceBytecode", "true");
        List<String> messages = captureLog(cb);

        InstructionTracingLanguage language = setupLanguage(cb);
        InstructionTracingRootNode node = BYTECODE.create(language, BytecodeConfig.WITH_SOURCE, this::emitWhileLoopRoot).getNode(0);

        node.getCallTarget().call(42);

        boolean found = false;
        for (String string : messages) {
            if (string.startsWith("[bc]")) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }

    @Test
    public void testEngineTracingWithFilter1() {
        Context.Builder cb = Context.newBuilder(InstructionTracingLanguage.ID).//
                        option("engine.TraceBytecode", "true").//
                        option("engine.BytecodeMethodFilter", "while-loop");
        List<String> messages = captureLog(cb);

        InstructionTracingLanguage language = setupLanguage(cb);
        InstructionTracingRootNode node = BYTECODE.create(language, BytecodeConfig.WITH_SOURCE, this::emitWhileLoopRoot).getNode(0);
        node.getCallTarget().call(42);
        boolean found = false;
        for (String string : messages) {
            if (string.startsWith("[bc]")) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }

    @Test
    public void testEngineTracingWithFilter2() {
        Context.Builder cb = Context.newBuilder(InstructionTracingLanguage.ID).//
                        option("engine.TraceBytecode", "true").//
                        option("engine.BytecodeMethodFilter", "~while-loop");
        List<String> messages = captureLog(cb);

        InstructionTracingLanguage language = setupLanguage(cb);
        InstructionTracingRootNode node = BYTECODE.create(language, BytecodeConfig.WITH_SOURCE, this::emitWhileLoopRoot).getNode(0);
        node.getCallTarget().call(42);
        boolean found = false;
        for (String string : messages) {
            if (string.startsWith("[bc]")) {
                found = true;
                break;
            }
        }
        assertFalse(found);
    }

    @Test
    public void testEngineTracingWithFilter3() {
        Context.Builder cb = Context.newBuilder(InstructionTracingLanguage.ID).//
                        option("engine.TraceBytecode", "true").//
                        option("engine.BytecodeMethodFilter", "asdf");
        List<String> messages = captureLog(cb);

        InstructionTracingLanguage language = setupLanguage(cb);
        InstructionTracingRootNode node = BYTECODE.create(language, BytecodeConfig.WITH_SOURCE, this::emitWhileLoopRoot).getNode(0);
        node.getCallTarget().call(42);
        boolean found = false;
        for (String string : messages) {
            if (string.startsWith("[bc]")) {
                found = true;
                break;
            }
        }
        assertFalse(found);
    }

    @Test
    public void testEngineTracingWithFilter4() {
        Context.Builder cb = Context.newBuilder(InstructionTracingLanguage.ID).//
                        option("engine.TraceBytecode", "true").//
                        option("engine.BytecodeMethodFilter", "~asdf");
        List<String> messages = captureLog(cb);

        InstructionTracingLanguage language = setupLanguage(cb);
        InstructionTracingRootNode node = BYTECODE.create(language, BytecodeConfig.WITH_SOURCE, this::emitWhileLoopRoot).getNode(0);
        node.getCallTarget().call(42);
        boolean found = false;
        for (String string : messages) {
            if (string.startsWith("[bc]")) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }

    @Test
    public void testEngineHistogram() {
        Context.Builder cb = Context.newBuilder(InstructionTracingLanguage.ID).option("engine.BytecodeHistogram", "tier,root,thread");
        List<String> messages = captureLog(cb);

        InstructionTracingLanguage language = setupLanguage(cb);
        InstructionTracingRootNode node = BYTECODE.create(language, BytecodeConfig.DEFAULT, this::emitWhileLoopRoot).getNode(0);

        node.getCallTarget().call(100);

        tearDownContext(); // print histogram

        boolean found = false;
        for (String string : messages) {
            if (string.startsWith("[bc] Instruction histogram for")) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }

    @Test
    public void testEngineHistogramGrouping() {
        Context.Builder cb = Context.newBuilder(InstructionTracingLanguage.ID).option("engine.BytecodeHistogram", "source,root,tier,language,thread");
        List<String> messages = captureLog(cb);

        InstructionTracingLanguage language = setupLanguage(cb);
        InstructionTracingRootNode node = BYTECODE.create(language, BytecodeConfig.WITH_SOURCE, this::emitWhileLoopRoot).getNode(0);

        node.getCallTarget().call(42);

        tearDownContext(); // print histogram

        Thread t = Thread.currentThread();
        boolean found = false;
        for (String string : messages) {
            if (string.startsWith("[bc] Instruction histogram for")) {
                assertTrue(string.contains("\u25B6 Source: 0x420c5f39 " + whileLoopSource.getName()));
                assertTrue(string.contains("\u25B6 Root: " + whileLoopSource.getName()));
                assertTrue(string.contains("\u25B6 " + InstructionTracingLanguage.ID));
                assertTrue(string.contains("\u25B6 Tier 1: Profiled Interpreter"));
                assertTrue(string.contains("\u25B6 Tier 0: Unprofiled Interpreter"));
                assertTrue(string.contains("\u25B6 " + t.toString()));
                found = true;
                break;
            }
        }
        assertTrue(found);
    }

    /*
     * Tests that the bytecode histogram is printed asynchronously even if the context is not yet
     * closed.
     */
    @Test
    public void testEngineHistogramInterval() throws InterruptedException {
        Context.Builder cb = Context.newBuilder(InstructionTracingLanguage.ID).//
                        option("engine.BytecodeHistogram", "true").//
                        option("engine.BytecodeHistogramInterval", "1ms");
        List<String> messages = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        cb.logHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                synchronized (messages) {
                    if (record.getMessage().startsWith("[bc] Instruction histogram for")) {
                        messages.add(record.getMessage());
                        latch.countDown();
                    }
                }
            }

            @Override
            public void close() {
            }

            @Override
            public void flush() {
            }
        });

        InstructionTracingLanguage language = setupLanguage(cb);
        InstructionTracingRootNode node = BYTECODE.create(language, BytecodeConfig.WITH_SOURCE, this::emitWhileLoopRoot).getNode(0);

        node.getCallTarget().call(100);

        latch.await(20, TimeUnit.SECONDS);
        boolean found = false;
        synchronized (messages) {
            for (String string : messages) {
                if (string.startsWith("[bc] Instruction histogram for")) {
                    found = true;
                }
            }
        }
        assertTrue(found);
        tearDownContext();
    }

    @Test
    public void testExclusiveDescriptor() {
        Context.Builder cb = Context.newBuilder(InstructionTracingLanguage.ID);
        InstructionTracingLanguage language = setupLanguage(cb);
        InstructionTracingRootNode node = BYTECODE.create(language, BytecodeConfig.DEFAULT, this::emitWhileLoopRoot).getNode(0);
        InstructionTracer exclusiveTracer = new InstructionTracer() {
            public void onInstructionEnter(InstructionAccess access, BytecodeNode bytecode, int bytecodeIndex, Frame frame) {
            }

            public BytecodeDescriptor<?, ?, ?> getExclusiveBytecodeDescriptor() {
                return DISABLED_BYTECODE;
            }
        };

        assertFails(() -> {
            BYTECODE.addInstructionTracer(language, exclusiveTracer);
        }, IllegalArgumentException.class, (e) -> {
            assertEquals("The passed instruction tracer is exclusive to BytecodeDescriptor[InstructionTracingDisabledRootNodeGen], but it was installed for BytecodeDescriptor[InstructionTracingRootNodeGen].",
                            e.getMessage());
        });

        assertFails(() -> {
            BYTECODE.removeInstructionTracer(language, exclusiveTracer);
        }, IllegalArgumentException.class, (e) -> {
            assertEquals("The passed instruction tracer is exclusive to BytecodeDescriptor[InstructionTracingDisabledRootNodeGen], but it was installed for BytecodeDescriptor[InstructionTracingRootNodeGen].",
                            e.getMessage());
        });

        assertFails(() -> {
            node.getRootNodes().addInstructionTracer(exclusiveTracer);
        }, IllegalArgumentException.class, (e) -> {
            assertEquals("The passed instruction tracer is exclusive to BytecodeDescriptor[InstructionTracingDisabledRootNodeGen], but it was installed for BytecodeDescriptor[InstructionTracingRootNodeGen].",
                            e.getMessage());
        });

        assertFails(() -> {
            node.getRootNodes().removeInstructionTracer(exclusiveTracer);
        }, IllegalArgumentException.class, (e) -> {
            assertEquals("The passed instruction tracer is exclusive to BytecodeDescriptor[InstructionTracingDisabledRootNodeGen], but it was installed for BytecodeDescriptor[InstructionTracingRootNodeGen].",
                            e.getMessage());
        });

    }

    @Test
    public void testPrintTracing() {
        Context.Builder cb = Context.newBuilder(InstructionTracingLanguage.ID);
        InstructionTracingLanguage language = setupLanguage(cb);
        List<String> instructions = new ArrayList<>();
        PrintInstructionTracer tracer = PrintInstructionTracer.newBuilder((s) -> instructions.add(s)).filter((node) -> {
            return node.getTier() == BytecodeTier.UNCACHED;
        }).build();

        InstructionTracingRootNode node = BYTECODE.create(language, BytecodeConfig.WITH_SOURCE, this::emitWhileLoopRoot).getNode(0);
        node.getRootNodes().addInstructionTracer(tracer);

        node.getBytecodeNode().setUncachedThreshold(32);
        CallTarget target = node.getCallTarget();
        target.call(30);
        assertNotEquals(0, instructions.size());
        instructions.clear();

        // node no longer uncached filter triggers
        tracer.reset();
        target.call(32);

        assertEquals(0, instructions.size());
    }

    /*
     * Tests that histograms taken asynchronous are always consistent and does not loose any
     * instructions.
     */
    @Test
    public void testHistogramAsynchronousReset() throws InterruptedException {
        // we compute the number of expected instructions per invocation
        int argument = 100;
        int iterations = 10;
        long expectedInstructionCountPerInvocation = this.expectedInstructionCountPerInvocation(argument);

        Context.Builder cb = Context.newBuilder(InstructionTracingLanguage.ID);
        InstructionTracingLanguage language = setupLanguage(cb);
        HistogramInstructionTracer histogramTracer = HistogramInstructionTracer.newBuilder().build(BYTECODE);
        InstructionTracingRootNode node = BYTECODE.create(language, BytecodeConfig.WITH_SOURCE, this::emitWhileLoopRoot).getNode(0);
        node.getRootNodes().addInstructionTracer(histogramTracer);
        CallTarget target = node.getCallTarget();
        AtomicReference<Throwable> error = new AtomicReference<>();

        CountDownLatch startLatch = new CountDownLatch(2);
        Thread executorThread = new Thread(() -> {
            context.enter();
            try {
                startLatch.countDown();
                startLatch.await();
                for (int i = 0; i < iterations; i++) {
                    Thread.sleep(1);
                    target.call(argument);
                }
            } catch (Throwable t) {
                error.set(t);
            } finally {
                context.leave();
            }
        });
        AtomicReference<List<Histogram>> collectedHistograms = new AtomicReference<>();
        Thread histogramThread = new Thread(() -> {
            try {
                startLatch.countDown();
                startLatch.await();
                List<Histogram> histograms = new ArrayList<>();
                for (int i = 0; i < iterations; i++) {
                    Thread.sleep(1);
                    histograms.add(histogramTracer.getHistogramAndReset());
                }
                collectedHistograms.set(histograms);
            } catch (Throwable t) {
                error.set(t);
            }
        });

        executorThread.start();
        histogramThread.start();

        executorThread.join();
        histogramThread.join();

        Throwable t = error.get();
        if (t != null) {
            throw new AssertionError("Error encountered in thread", t);
        }

        List<Histogram> histograms = collectedHistograms.get();
        assertNotNull(histograms);

        // we add the remaining executed instructions
        histograms.add(histogramTracer.getHistogramAndReset());
        tearDownContext();

        // now we check we got consistent instruction counts
        long expectedExecutedInstructions = expectedInstructionCountPerInvocation * iterations;
        long actualExecutedInstructions = 0L;
        for (Histogram histogram : histograms) {
            actualExecutedInstructions += histogram.getInstructionsExecuted();
            assertHistogram(histogram);
        }

        // if these numbers don't match we had a race condition in the collection
        // of the histogram data.
        assertEquals(expectedExecutedInstructions, actualExecutedInstructions);
    }

    @Test
    public void testHistogramFilterAndGroup() {
        Context.Builder cb = Context.newBuilder(InstructionTracingLanguage.ID);
        InstructionTracingLanguage language = setupLanguage(cb);
        HistogramInstructionTracer histogramTracer = HistogramInstructionTracer.newBuilder().filter((node) -> {
            return node.getTier() == BytecodeTier.CACHED;
        }).groupBy((bytecodeNode, thread, compilationTier) -> {
            return bytecodeNode.getTier();
        }).groupBy((bytecodeNode, thread, compilationTier) -> {
            return thread;
        }).build(BYTECODE);

        InstructionTracingRootNode node = BYTECODE.create(language, BytecodeConfig.WITH_SOURCE, this::emitWhileLoopRoot).getNode(0);
        node.getRootNodes().addInstructionTracer(histogramTracer);

        CallTarget target = node.getCallTarget();
        target.call(100);

        Histogram histogram = histogramTracer.getHistogram();
        histogramTracer.reset();

        // this number might regularly change with further optimization.
        // just update it especially if its lower
        assertNotEquals(0L, histogram.getInstructionsExecuted());
        assertHistogram(histogram);

        Histogram uncached = histogram.getGroups().get(BytecodeTier.UNCACHED);
        assertHistogram(uncached);
        assertEquals(0L, uncached.getInstructionsExecuted());
        assertEquals(1, uncached.getGroups().size());

        uncached = uncached.getGroups().get(Thread.currentThread());
        assertHistogram(uncached);
        assertEquals(0L, uncached.getInstructionsExecuted());
        assertNull(uncached.getGroups());

        Histogram cached = histogram.getGroups().get(BytecodeTier.CACHED);
        assertHistogram(cached);
        assertNotEquals(0L, cached.getInstructionsExecuted());
        assertFalse(cached.getGroups().isEmpty());

        cached = cached.getGroups().get(Thread.currentThread());
        assertHistogram(cached);
        assertNotEquals(0L, cached.getInstructionsExecuted());
        assertNull(cached.getGroups());
    }

    private static void assertHistogram(Histogram histogram) {
        long localSum = 0;
        for (var entry : histogram.getCounters().entrySet()) {
            localSum += entry.getValue();
        }
        assertEquals(histogram.getInstructionsExecuted(), localSum);
        long localStatisticSum = 0;
        for (var entry : histogram.getStatistics().entrySet()) {
            localStatisticSum += entry.getValue().getSum();
        }
        assertEquals(localSum, localStatisticSum);
        assertEquals(localSum, histogram.getInstructionsExecuted());

        assertNotNull(histogram.dump());
        assertNotNull(histogram.toString());
        assertNotNull(histogram.getCounters());
        assertNotNull(histogram.getStatistics());
        histogram.getGroups();

    }

    private long expectedInstructionCountPerInvocation(int argument) {
        Context.Builder cb = Context.newBuilder(InstructionTracingLanguage.ID);
        InstructionTracingLanguage language = setupLanguage(cb);
        InstructionTracingRootNode node = BYTECODE.create(language, BytecodeConfig.WITH_SOURCE, this::emitWhileLoopRoot).getNode(0);
        AtomicLong instructionCounter = new AtomicLong();
        node.getRootNodes().addInstructionTracer(new InstructionTracer() {
            public void onInstructionEnter(InstructionAccess access, BytecodeNode bytecode, int bytecodeIndex, Frame frame) {
                instructionCounter.incrementAndGet();
            }
        });
        node.getCallTarget().call(argument);
        long counter = instructionCounter.get();
        node.getCallTarget().call(argument);

        // make sure the instruction counter is stable
        assertEquals(counter * 2, instructionCounter.get());
        return counter;
    }

    private static List<String> captureLog(Context.Builder cb) {
        List<String> messages = new ArrayList<>();
        cb.logHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                synchronized (messages) {
                    messages.add(record.getMessage());
                }
            }

            @Override
            public void close() {

            }

            @Override
            public void flush() {

            }
        });
        return messages;
    }

    private static final Source whileLoopSource = Source.newBuilder(InstructionTracingLanguage.ID, "source-code", "while-loop.demo").build();

    private void emitWhileLoopRoot(InstructionTracingRootNodeGen.Builder b) {
        // local = arguments[0]
        // while (local > 0) [
        // local = local -1;
        // }
        // return local;

        b.beginSource(whileLoopSource);
        b.beginSourceSection();
        b.beginRoot();
        BytecodeLocal local = b.createLocal();

        // local = arg0
        b.beginStoreLocal(local);
        b.emitLoadArgument(0);
        b.endStoreLocal();

        // while
        b.beginWhile();

        // local > 0
        b.beginGreaterZero();
        b.emitLoadLocal(local);
        b.endGreaterZero();

        // local = local - 1
        b.beginStoreLocal(local);
        b.beginDecrement();
        b.emitLoadLocal(local);
        b.endDecrement();
        b.endStoreLocal();

        b.endWhile();

        // return local
        b.beginReturn();
        b.emitLoadLocal(local);
        b.endReturn();

        b.endRoot();
        b.endSourceSection(0, whileLoopSource.getLength());
        b.endSource();
    }

    @GenerateBytecode(languageClass = InstructionTracingLanguage.class, //
                    enableYield = true, enableSerialization = true, //
                    enableUncachedInterpreter = true,  //
                    boxingEliminationTypes = {long.class, int.class, boolean.class})
    public abstract static class InstructionTracingRootNode extends RootNode implements BytecodeRootNode {

        protected InstructionTracingRootNode(InstructionTracingLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Override
        public String getName() {
            SourceSection sc = getSourceSection();
            if (sc != null) {
                return sc.getSource().getName();
            }
            return super.getName();
        }

        @Instrumentation
        static final class OtherInstrument {
            @Specialization
            public static int doDefault(int v) {
                return v + 1;
            }
        }

        @Operation
        static final class Invoke {
            @Specialization
            public static void doDefault() {
            }
        }

        @Operation
        static final class Decrement {
            @Specialization
            public static int doInt(int v) {
                return v - 1;
            }
        }

        @Operation
        static final class GreaterZero {
            @Specialization
            public static boolean doInt(int v) {
                return v > 0;
            }
        }

        @Operation
        static final class GreaterEqualZero {
            @Specialization
            public static boolean doInt(int v) {
                return v >= 0;
            }
        }

    }

    @GenerateBytecode(languageClass = InstructionTracingLanguage.class, //
                    enableYield = true, enableSerialization = true, //
                    enableUncachedInterpreter = true,  //
                    enableInstructionTracing = false, boxingEliminationTypes = {long.class, int.class, boolean.class})
    public abstract static class InstructionTracingDisabledRootNode extends RootNode implements BytecodeRootNode {

        protected InstructionTracingDisabledRootNode(InstructionTracingLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Instrumentation
        static final class OtherInstrument {
            @Specialization
            public static int doDefault(int v) {
                return v + 1;
            }
        }

    }

    @TruffleLanguage.Registration(id = InstructionTracingLanguage.ID)
    public static class InstructionTracingLanguage extends TruffleLanguage<Object> {
        public static final String ID = "InstructionTracingLanguage";

        @Override
        protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
            return true;
        }

        @Override
        protected Object createContext(Env env) {
            return new Object();
        }

        static final LanguageReference<InstructionTracingLanguage> REF = LanguageReference.create(InstructionTracingLanguage.class);
    }

}
