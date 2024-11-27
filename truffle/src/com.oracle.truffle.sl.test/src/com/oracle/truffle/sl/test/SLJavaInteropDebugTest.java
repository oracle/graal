/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.tck.DebuggerTester.getSourceImpl;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.function.Function;

import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.DebugException;
import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.DebugStackTraceElement;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.tck.DebuggerTester;

/**
 * Test of host interop in debugger: {@link DebuggerSession#setShowHostStackFrames(boolean)}.
 */
public class SLJavaInteropDebugTest extends AbstractSLTest {

    @BeforeClass
    public static void runWithWeakEncapsulationOnly() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

    private DebuggerTester tester;

    @Before
    public void before() {
        tester = new DebuggerTester(newContextBuilder().allowAllAccess(true));
    }

    @After
    public void dispose() {
        tester.close();
    }

    private static Source slCode(String code) {
        return Source.create("sl", code);
    }

    public static final class HostCalls implements Function<Function<Object, ?>, Object> {

        private int n = 0;

        public HostCalls(int n) {
            this.n = n;
        }

        @Override
        public Object apply(Function<Object, ?> f) {
            if (--n == 0) {
                return f.apply(0);
            } else {
                return f.apply(this);
            }
        }
    }

    @Test
    public void testStackAtGuest() {
        doTestStackAtGuest(false);
    }

    @Test
    public void testInteropStackAtGuest() {
        doTestStackAtGuest(true);
    }

    private void doTestStackAtGuest(boolean javaInterop) {
        Source sourceCode = slCode("function callback(hostCalls) {\n" +
                        "    if (hostCalls == 0) {\n" +
                        "        return done();\n" +
                        "    }\n" +
                        "    doCall(hostCalls);\n" +
                        "}\n" +
                        "function done() {\n" +
                        "    return 42;\n" +
                        "}\n" +
                        "function doCall(hostCalls) {\n" +
                        "    hostCalls(callback);\n" +
                        "}");
        try (DebuggerSession session = tester.startSession()) {
            session.setShowHostStackFrames(javaInterop);
            session.install(Breakpoint.newBuilder(getSourceImpl(sourceCode)).lineIs(8).build());

            tester.startEval(sourceCode);
            tester.expectDone();
            tester.startExecute(context -> context.getBindings("sl").getMember("doCall").execute(new HostCalls(3)));
            tester.expectSuspended((SuspendedEvent event) -> {
                assertEquals(8, event.getSourceSection().getStartLine());
                if (javaInterop) {
                    checkFrames(event, "done", "sl", 8, false, //
                                    "callback", "sl", 3, false, //
                                    "com.oracle.truffle.polyglot.PolyglotFunction.apply", null, null, true, //
                                    HostCalls.class.getName() + ".apply", null, null, true, //
                                    "doCall", "sl", 11, false, //
                                    "callback", "sl", 5, false, //
                                    "com.oracle.truffle.polyglot.PolyglotFunction.apply", null, null, true, //
                                    HostCalls.class.getName() + ".apply", null, null, true, //
                                    "doCall", "sl", 11, false, //
                                    "callback", "sl", 5, false, //
                                    "com.oracle.truffle.polyglot.PolyglotFunction.apply", null, null, true, //
                                    HostCalls.class.getName() + ".apply", null, null, true, //
                                    "doCall", "sl", 11, false, //
                                    Value.class.getName() + ".execute", null, null, true);
                } else {
                    checkFrames(event, "done", "sl", 8, false, //
                                    "callback", "sl", 3, false, //
                                    "doCall", "sl", 11, false, //
                                    "callback", "sl", 5, false, //
                                    "doCall", "sl", 11, false, //
                                    "callback", "sl", 5, false, //
                                    "doCall", "sl", 11, false);
                }
            });
            tester.expectDone();
            tester.startExecute(context -> context.getBindings("sl").getMember("doCall").execute(new HostCalls(1)));
            tester.expectSuspended((SuspendedEvent event) -> {
                assertEquals(8, event.getSourceSection().getStartLine());
                if (javaInterop) {
                    checkFrames(event, "done", "sl", 8, false, //
                                    "callback", "sl", 3, false, //
                                    "com.oracle.truffle.polyglot.PolyglotFunction.apply", null, null, true, //
                                    HostCalls.class.getName() + ".apply", null, null, true, //
                                    "doCall", "sl", 11, false, //
                                    Value.class.getName() + ".execute", null, null, true);
                } else {
                    checkFrames(event, "done", "sl", 8, false, //
                                    "callback", "sl", 3, false, //
                                    "doCall", "sl", 11, false);
                }
            });
            tester.expectDone();
            tester.startExecute(context -> context.getBindings("sl").getMember("callback").execute(0));
            tester.expectSuspended((SuspendedEvent event) -> {
                assertEquals(8, event.getSourceSection().getStartLine());
                checkFrames(event, "done", "sl", 8, false, //
                                "callback", "sl", 3, false);
            });
            tester.expectDone();
        }
    }

    // A list of stack frame information: root name, language id, line number, is host
    private static void checkFrames(SuspendedEvent event, Object... frameInfo) {
        int frameInfoIndex = 0;
        for (DebugStackFrame frame : event.getStackFrames()) {
            if (frameInfoIndex >= frameInfo.length) {
                assertTrue(frame.isHost()); // Further host frames from the test classes
                continue;
            }
            assertEquals(frameInfo[frameInfoIndex], frame.getName());
            String languageId = frame.getLanguage() != null ? frame.getLanguage().getId() : null;
            assertEquals(frameInfo[frameInfoIndex + 1], languageId);
            Integer line = (Integer) frameInfo[frameInfoIndex + 2];
            if (line != null) {
                assertEquals((int) line, frame.getSourceSection().getStartLine());
            } else {
                assertNull(frame.getSourceSection());
            }
            boolean isHost = (boolean) frameInfo[frameInfoIndex + 3];
            assertEquals(isHost, frame.isHost());
            StackTraceElement hostTraceElement = frame.getHostTraceElement();
            if (isHost) {
                assertNotNull(hostTraceElement);
            } else {
                assertNull(hostTraceElement);
            }
            frameInfoIndex += 4;
        }
        assertEquals(frameInfo.length, frameInfoIndex);
        checkException(event, frameInfo);
    }

    private static void checkException(SuspendedEvent event, Object... frameInfo) {
        try {
            event.getTopStackFrame().eval("something bad");
        } catch (DebugException de) {
            int frameInfoIndex = 0;
            for (DebugStackTraceElement element : de.getDebugStackTrace()) {
                if (frameInfoIndex >= frameInfo.length) {
                    assertTrue(element.isHost()); // Further host frames from the test classes
                    continue;
                }
                assertEquals(frameInfo[frameInfoIndex], element.getName());
                Integer line = (Integer) frameInfo[frameInfoIndex + 2];
                if (line != null && frameInfoIndex > 0) {
                    assertEquals("Invalid line in stack trace at index " + frameInfoIndex + ".", (int) line, element.getSourceSection().getStartLine());
                } else {
                    assertNull(element.getSourceSection());
                }
                boolean isHost = (boolean) frameInfo[frameInfoIndex + 3];
                assertEquals(isHost, element.isHost());
                StackTraceElement hostTraceElement = element.getHostTraceElement();
                if (isHost) {
                    assertNotNull(hostTraceElement);
                } else {
                    assertNull(hostTraceElement);
                }
                frameInfoIndex += 4;
            }
            assertEquals(frameInfo.length, frameInfoIndex);
        }
    }
}
