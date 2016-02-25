/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedList;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.ExecutionEvent;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.source.LineLocation;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.EventConsumer;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.api.vm.PolyglotEngine.Value;

public class SLDebugTest {
    private Debugger debugger;
    private final LinkedList<Runnable> run = new LinkedList<>();
    private SuspendedEvent suspendedEvent;
    private Throwable ex;
    private ExecutionEvent executionEvent;
    protected PolyglotEngine engine;
    protected final ByteArrayOutputStream out = new ByteArrayOutputStream();
    protected final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @Before
    public void before() {
        suspendedEvent = null;
        executionEvent = null;
        engine = PolyglotEngine.newBuilder().setOut(out).setErr(err).onEvent(new EventConsumer<ExecutionEvent>(ExecutionEvent.class) {
            @Override
            protected void on(ExecutionEvent event) {
                executionEvent = event;
                performWork();
                executionEvent = null;
            }
        }).onEvent(new EventConsumer<SuspendedEvent>(SuspendedEvent.class) {
            @Override
            protected void on(SuspendedEvent event) {
                suspendedEvent = event;
                performWork();
                suspendedEvent = null;
            }
        }).build();
        debugger = Debugger.find(engine);
        assertNotNull("Debugger found", debugger);
        run.clear();
    }

    private static Source createFactorial() {
        return Source.fromText("function main() {\n" +
                        "  res = fac(2);\n" + "  println(res);\n" +
                        "  return res;\n" +
                        "}\n" +
                        "function fac(n) {\n" +
                        "  if (n <= 1) {\n" +
                        "    return 1;\n" + "  }\n" +
                        "  nMinusOne = n - 1;\n" +
                        "  nMOFact = fac(nMinusOne);\n" +
                        "  res = n * nMOFact;\n" +
                        "  return res;\n" + "}\n",
                        "factorial.sl").withMimeType(
                        "application/x-sl");
    }

    protected final String getOut() {
        return new String(out.toByteArray());
    }

    protected final String getErr() {
        try {
            err.flush();
        } catch (IOException e) {
        }
        return new String(err.toByteArray());
    }

    @Test
    public void testBreakpoint() throws Throwable {
        final Source factorial = createFactorial();

        run.addLast(new Runnable() {
            @Override
            public void run() {
                try {
                    assertNull(suspendedEvent);
                    assertNotNull(executionEvent);
                    LineLocation nMinusOne = factorial.createLineLocation(8);
                    debugger.setLineBreakpoint(0, nMinusOne, false);
                    executionEvent.prepareContinue();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        engine.eval(factorial);
        assertExecutedOK();

        run.addLast(new Runnable() {
            @Override
            public void run() {
                // the breakpoint should hit instead
            }
        });
        assertLocation(8, "return 1",
                        "n", 1L,
                        "nMinusOne", null,
                        "nMOFact", null,
                        "res", null);
        continueExecution();

        Value value = engine.findGlobalSymbol("main").execute();
        assertExecutedOK();
        Assert.assertEquals("2\n", getOut());
        Number n = value.as(Number.class);
        assertNotNull(n);
        assertEquals("Factorial computed OK", 2, n.intValue());
    }

    @Test
    public void stepInStepOver() throws Throwable {
        final Source factorial = createFactorial();
        engine.eval(factorial);

        // @formatter:on
        run.addLast(new Runnable() {
            @Override
            public void run() {
                assertNull(suspendedEvent);
                assertNotNull(executionEvent);
                executionEvent.prepareStepInto();
            }
        });

        assertLocation(2, "res = fac(2)", "res", null);
        stepInto(1);
        assertLocation(7, "n <= 1",
                        "n", 2L,
                        "nMinusOne", null,
                        "nMOFact", null,
                        "res", null);
        stepOver(1);
        assertLocation(10, "nMinusOne = n - 1",
                        "n", 2L,
                        "nMinusOne", null,
                        "nMOFact", null,
                        "res", null);
        stepOver(1);
        assertLocation(11, "nMOFact = fac(nMinusOne)",
                        "n", 2L,
                        "nMinusOne", 1L,
                        "nMOFact", null,
                        "res", null);
        stepOver(1);
        assertLocation(12, "res = n * nMOFact",
                        "n", 2L, "nMinusOne", 1L,
                        "nMOFact", 1L,
                        "res", null);
        stepOver(1);
        assertLocation(13, "return res",
                        "n", 2L,
                        "nMinusOne", 1L,
                        "nMOFact", 1L,
                        "res", 2L);
        stepOver(1);
        assertLocation(2, "fac(2)", "res", null);
        stepOver(1);
        assertLocation(3, "println(res)", "res", 2L);
        stepOut();

        Value value = engine.findGlobalSymbol("main").execute();
        assertExecutedOK();

        Number n = value.as(Number.class);
        assertNotNull(n);
        assertEquals("Factorial computed OK", 2, n.intValue());
    }

    private void performWork() {
        try {
            if (ex == null && !run.isEmpty()) {
                Runnable c = run.removeFirst();
                c.run();
            }
        } catch (Throwable e) {
            ex = e;
        }
    }

    private void stepOver(final int size) {
        run.addLast(new Runnable() {
            public void run() {
                suspendedEvent.prepareStepOver(size);
            }
        });
    }

    private void stepOut() {
        run.addLast(new Runnable() {
            public void run() {
                suspendedEvent.prepareStepOut();
            }
        });
    }

    private void continueExecution() {
        run.addLast(new Runnable() {
            public void run() {
                suspendedEvent.prepareContinue();
            }
        });
    }

    private void stepInto(final int size) {
        run.addLast(new Runnable() {
            public void run() {
                suspendedEvent.prepareStepInto(size);
            }
        });
    }

    private void assertLocation(final int line, final String code, final Object... expectedFrame) {
        run.addLast(new Runnable() {
            public void run() {
                assertNotNull(suspendedEvent);
                Assert.assertEquals(line, suspendedEvent.getNode().getSourceSection().getLineLocation().getLineNumber());
                Assert.assertEquals(code, suspendedEvent.getNode().getSourceSection().getCode());
                final MaterializedFrame frame = suspendedEvent.getFrame();

                Assert.assertEquals(expectedFrame.length / 2, frame.getFrameDescriptor().getSize());

                for (int i = 0; i < expectedFrame.length; i = i + 2) {
                    String expectedIdentifier = (String) expectedFrame[i];
                    Object expectedValue = expectedFrame[i + 1];
                    FrameSlot slot = frame.getFrameDescriptor().findFrameSlot(expectedIdentifier);
                    Assert.assertNotNull(slot);
                    Assert.assertEquals(expectedValue, frame.getValue(slot));
                }
                run.removeFirst().run();
            }
        });
    }

    private void assertExecutedOK() throws Throwable {
        Assert.assertTrue(getErr(), getErr().isEmpty());
        if (ex != null) {
            if (ex instanceof AssertionError) {
                throw ex;
            } else {
                throw new AssertionError("Error during execution", ex);
            }
        }
        assertTrue("Assuming all requests processed: " + run, run.isEmpty());
    }
}
