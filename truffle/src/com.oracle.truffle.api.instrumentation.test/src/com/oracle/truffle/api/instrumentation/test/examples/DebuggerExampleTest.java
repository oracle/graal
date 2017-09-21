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
