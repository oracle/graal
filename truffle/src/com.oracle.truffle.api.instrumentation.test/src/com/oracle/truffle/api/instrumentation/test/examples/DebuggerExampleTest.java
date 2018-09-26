/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation.test.examples;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.test.AbstractInstrumentationTest;
import com.oracle.truffle.api.instrumentation.test.examples.DebuggerController.Callback;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Source;

public final class DebuggerExampleTest extends AbstractInstrumentationTest {
    private DebuggerController debugger;

    @Before
    public void setupDebugger() throws IOException {
        // BEGIN: DebuggerExampleTest
        Instrument instrument = engine.getInstruments().get(DebuggerExample.ID);
        assert !isCreated(instrument) : "Not enabled yet";
        debugger = instrument.lookup(DebuggerController.class);
        assert isCreated(instrument) : "Got enabled";
        assert debugger != null : "We can control the debugger";
        // END: DebuggerExampleTest
        assertTrue("Enabled by requesting registered services class", isCreated(instrument));
        assertNotNull("Debugger interface found", debugger);
        DebuggerExample itself = instrument.lookup(DebuggerExample.class);
        assertNull("Debugger instrument itself isn't found", itself);
        TruffleInstrument instr = instrument.lookup(TruffleInstrument.class);
        assertNull("Instrument itself isn't found", instr);
        assertEvalOut("", ""); // ensure debugger gets loaded
    }

    @Test
    public void testBreakpoint() throws IOException {
        final AtomicBoolean breakpointHit = new AtomicBoolean();
        debugger.installBreakpoint(1, new Callback() {
            @Override
            public void halted(DebuggerController d, EventContext haltedAt) {
                Assert.assertEquals(1, haltedAt.getInstrumentedSourceSection().getStartLine());
                breakpointHit.set(true);
            }
        });
        run(lines("BLOCK(STATEMENT,", "STATEMENT,", "STATEMENT)"));
        Assert.assertTrue(breakpointHit.get());
    }

    @Test
    @SuppressWarnings("hiding")
    public void testStepInto() throws IOException {
        Source source = lines("ROOT(", // 1
                        "DEFINE(foo,STATEMENT),", // 2
                        "DEFINE(bar", // 3
                        /**/",STATEMENT", // 4
                        /**/",STATEMENT(CALL(foo))", // 5
                        /**/",STATEMENT),", // 6
                        "STATEMENT(CALL(bar)))"); // 7

        final AtomicBoolean allStepped = new AtomicBoolean();
        debugger.installBreakpoint(7, new Callback() {
            public void halted(DebuggerController debugger, EventContext haltedAt) {
                assertLineAt(haltedAt, 7);
                debugger.stepInto(new Callback() {
                    public void halted(DebuggerController debugger, EventContext haltedAt) {
                        assertLineAt(haltedAt, 4);
                        debugger.stepInto(new Callback() {
                            public void halted(DebuggerController debugger, EventContext haltedAt) {
                                assertLineAt(haltedAt, 5);
                                debugger.stepInto(new Callback() {
                                    public void halted(DebuggerController debugger, EventContext haltedAt) {
                                        assertLineAt(haltedAt, 2);
                                        debugger.stepInto(new Callback() {
                                            public void halted(DebuggerController debugger, EventContext haltedAt) {
                                                assertLineAt(haltedAt, 6);
                                                debugger.stepInto(new Callback() {
                                                    public void halted(DebuggerController debugger, EventContext haltedAt) {
                                                        throw new AssertionError();
                                                    }
                                                });
                                                allStepped.set(true);
                                            }
                                        });
                                    }
                                });
                            }
                        });
                    }
                });
            }

        });
        run(source);
        Assert.assertTrue(allStepped.get());
    }

    private static void assertLineAt(EventContext haltedAt, int index) {
        Assert.assertEquals(index, haltedAt.getInstrumentedSourceSection().getStartLine());
    }

    @Test
    @SuppressWarnings("hiding")
    public void testStepOver() throws IOException {
        Source source = lines("ROOT(", // 1
                        "DEFINE(foo,STATEMENT),", // 2
                        "DEFINE(bar", // 3
                        /**/",STATEMENT", // 4
                        /**/",STATEMENT(CALL(foo))", // 5
                        /**/",STATEMENT),", // 6
                        "STATEMENT(CALL(bar)))"); // 7

        final AtomicBoolean allStepped = new AtomicBoolean();
        debugger.installBreakpoint(4, new Callback() {
            public void halted(DebuggerController debugger, EventContext haltedAt) {
                assertLineAt(haltedAt, 4);
                debugger.stepOver(new Callback() {
                    public void halted(DebuggerController debugger, EventContext haltedAt) {
                        assertLineAt(haltedAt, 5);
                        debugger.stepOver(new Callback() {
                            public void halted(DebuggerController debugger, EventContext haltedAt) {
                                assertLineAt(haltedAt, 6);
                                allStepped.set(true);
                                debugger.stepOver(new Callback() {
                                    public void halted(DebuggerController debugger, EventContext haltedAt) {
                                        throw new AssertionError();
                                    }
                                });
                            }
                        });
                    }
                });
            }
        });
        run(source);
        Assert.assertTrue(allStepped.get());
    }

    @Test
    @SuppressWarnings("hiding")
    public void testStepOut() throws IOException {
        Source source = lines("ROOT(", // 1
                        "DEFINE(foo,STATEMENT),", // 2
                        "DEFINE(bar", // 3
                        /**/",STATEMENT", // 4
                        /**/",STATEMENT(CALL(foo))", // 5
                        /**/",STATEMENT),", // 6
                        "STATEMENT(CALL(bar)))"); // 7

        final AtomicBoolean allStepped = new AtomicBoolean();
        debugger.installBreakpoint(2, new Callback() {
            @Override
            public void halted(DebuggerController debugger, EventContext haltedAt) {
                assertLineAt(haltedAt, 2);
                debugger.stepOut(new Callback() {
                    @Override
                    public void halted(DebuggerController debugger, EventContext haltedAt) {
                        assertLineAt(haltedAt, 6);
                        debugger.stepOver(new Callback() {
                            @Override
                            public void halted(DebuggerController debugger, EventContext haltedAt) {
                                throw new AssertionError();
                            }
                        });
                        allStepped.set(true);
                    }
                });
            }
        });
        run(source);
        Assert.assertTrue(allStepped.get());
    }

}
