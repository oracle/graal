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

import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.ExecutionEvent;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.source.LineLocation;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.vm.EventConsumer;
import com.oracle.truffle.api.vm.PolyglotEngine;
import java.io.ByteArrayOutputStream;
import java.util.LinkedList;
import java.util.concurrent.Callable;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SLDebugTest {
    private Source factorial;
    private Debugger debugger;
    private final LinkedList<Callable<?>> run = new LinkedList<>();
    private SuspendedEvent suspendedEvent;
    private Throwable ex;
    private ExecutionEvent executionEvent;

    @Test
    public void stepInStepOver() throws Throwable {
        // @formatter:off
        factorial = Source.fromText(
            "function main() {\n" +
            "  res = fac(2);\n" +
            "  println(res);\n" +
            "  return res;\n" +
            "}\n" +
            "function fac(n) {\n" +
            "  if (n <= 1) {\n" +
            "    return 1;\n" +
            "  }\n" +
            "  nMinusOne = n - 1;\n" +
            "  nMOFact = fac(nMinusOne);\n" +
            "  res = n * nMOFact;\n" +
            "  return res;\n" +
            "}\n" +
             "", "factorial.sl"
        ).withMimeType("application/x-sl");
        // @formatter:on

        ByteArrayOutputStream os = new ByteArrayOutputStream();

        PolyglotEngine engine = PolyglotEngine.newBuilder().onEvent(new EventConsumer<ExecutionEvent>(ExecutionEvent.class) {
            @Override
            protected void on(ExecutionEvent event) {
                executionEvent = event;
                performWork();
            }
        }).onEvent(new EventConsumer<SuspendedEvent>(SuspendedEvent.class) {
            @Override
            protected void on(SuspendedEvent event) {
                suspendedEvent = event;
                performWork();
            }
        }).setOut(os).build();
        debugger = Debugger.find(engine);
        assertNotNull("Debugger found", debugger);

        onEvent(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                LineLocation nMinusOne = factorial.createLineLocation(7);
                debugger.setLineBreakpoint(0, nMinusOne, true);
                executionEvent.prepareContinue();
                return null;
            }
        });

        PolyglotEngine.Value value = engine.eval(factorial);

        assertNull("Parsing done", value.get());

        assertExecutedOK();

        run(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                assertNull(suspendedEvent);
                executionEvent.prepareStepInto();
                return null;
            }
        });

        run(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                assertNotNull(suspendedEvent);
                final MaterializedFrame frame = suspendedEvent.getFrame();
                assertEquals("No arguments", 0, frame.getArguments().length);
                assertEquals("one var slot", 1, frame.getFrameDescriptor().getSlots().size());
                Object resName = frame.getFrameDescriptor().getSlots().get(0).getFrameDescriptor().getIdentifiers().iterator().next();
                assertEquals("res", resName);
                suspendedEvent.prepareStepInto(1);
                suspendedEvent = null;
                return null;
            }
        });

        run(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                assertLine(7);

                final MaterializedFrame frame = suspendedEvent.getFrame();
                assertEquals("One argument", 1, frame.getArguments().length);
                assertEquals("One argument value 2", 2L, frame.getArguments()[0]);
                suspendedEvent.prepareStepOver(1);
                suspendedEvent = null;
                return null;
            }
        });

        run(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                assertNotNull(suspendedEvent);
                assertLine(10);
                suspendedEvent.prepareContinue();
                suspendedEvent = null;
                return null;
            }
        });

        PolyglotEngine.Value main = engine.findGlobalSymbol("main");
        value = main.execute();

        // assertExecutedOK();

        Number n = value.as(Number.class);
        assertNotNull(n);
        assertEquals("Factorial computed OK", 2, n.intValue());
    }

    private void run(Callable<?> callable) throws Throwable {
        if (ex != null) {
            throw ex;
        }
        if (callable == null) {
            assertTrue("Assuming all requests processed: " + run, run.isEmpty());
            return;
        }
        run.addLast(callable);
        if (ex != null) {
            throw ex;
        }
    }

    private void performWork() {
        Callable<?> c = run.removeFirst();
        try {
            c.call();
        } catch (Exception e) {
            this.ex = e;
        }
    }

    private void assertExecutedOK() throws Throwable {
        run(null);
    }

    void assertLine(int line) {
        assertNotNull(suspendedEvent);
        final SourceSection expLoc = factorial.createSection("Line " + line, line);
        final SourceSection sourceLoc = suspendedEvent.getNode().getEncapsulatingSourceSection();
        assertEquals("Exp\n" + expLoc + "\nbut was\n" + sourceLoc, line, sourceLoc.getLineLocation().getLineNumber());
    }

    private void onEvent(Callable<Void> callable) throws Throwable {
        run(callable);
    }
}
