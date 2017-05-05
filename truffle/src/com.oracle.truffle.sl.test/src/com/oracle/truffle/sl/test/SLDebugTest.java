/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.DebugScope;
import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SuspendedCallback;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.sl.SLLanguage;
import com.oracle.truffle.tck.DebuggerTester;
import java.util.Collection;
import java.util.Iterator;
import static org.junit.Assert.fail;

public class SLDebugTest {

    private DebuggerTester tester;

    @Before
    public void before() {
        tester = new DebuggerTester();
    }

    @After
    public void dispose() {
        tester.close();
    }

    private void startEval(Source code) {
        tester.startEval(code);
    }

    private static Source slCode(String code) {
        return Source.newBuilder(code).name("testing").mimeType(SLLanguage.MIME_TYPE).build();
    }

    private DebuggerSession startSession() {
        return tester.startSession();
    }

    private String expectDone() {
        return tester.expectDone();
    }

    private void expectSuspended(SuspendedCallback callback) {
        tester.expectSuspended(callback);
    }

    protected SuspendedEvent checkState(SuspendedEvent suspendedEvent, String name, final int expectedLineNumber, final boolean expectedIsBefore, final String expectedCode,
                    final String... expectedFrame) {
        final int actualLineNumber = suspendedEvent.getSourceSection().getStartLine();
        Assert.assertEquals(expectedLineNumber, actualLineNumber);
        final String actualCode = suspendedEvent.getSourceSection().getCode();
        Assert.assertEquals(expectedCode, actualCode);
        final boolean actualIsBefore = suspendedEvent.isHaltedBefore();
        Assert.assertEquals(expectedIsBefore, actualIsBefore);

        checkStack(suspendedEvent.getTopStackFrame(), name, expectedFrame);
        return suspendedEvent;
    }

    protected void checkStack(DebugStackFrame frame, String name, String... expectedFrame) {
        assertEquals(name, frame.getName());
        checkDebugValues("variables", frame, expectedFrame);
    }

    protected void checkArgs(DebugStackFrame frame, String... expectedArgs) {
        Iterable<DebugValue> arguments = null;
        DebugScope scope = frame.getScope();
        while (scope != null) {
            if (scope.isFunctionScope()) {
                arguments = scope.getArguments();
                break;
            }
            scope = scope.getParent();
        }
        checkDebugValues("arguments", arguments, expectedArgs);
    }

    private void checkDebugValues(String msg, Iterable<DebugValue> values, String... expected) {
        Map<String, DebugValue> valMap = new HashMap<>();
        for (DebugValue value : values) {
            valMap.put(value.getName(), value);
        }
        String message = String.format("Frame %s expected %s got %s", msg, Arrays.toString(expected), valMap.toString());
        Assert.assertEquals(message, expected.length / 2, valMap.size());
        for (int i = 0; i < expected.length; i = i + 2) {
            String expectedIdentifier = expected[i];
            String expectedValue = expected[i + 1];
            DebugValue value = valMap.get(expectedIdentifier);
            Assert.assertNotNull(value);
            Assert.assertEquals(expectedValue, value.as(String.class));
        }
    }

    @Test
    public void testBreakpoint() throws Throwable {
        /*
         * Wrappers need to remain inserted for recursive functions to work for debugging. Like in
         * this test case when the breakpoint is in the exit condition and we want to step out.
         */
        final Source factorial = slCode("function main() {\n" +
                        "  return fac(5);\n" +
                        "}\n" +
                        "function fac(n) {\n" +
                        "  if (n <= 1) {\n" +
                        "    return 1;\n" + // break
                        "  }\n" +
                        "  return n * fac(n - 1);\n" +
                        "}\n");

        try (DebuggerSession session = startSession()) {

            startEval(factorial);
            Breakpoint breakpoint = session.install(Breakpoint.newBuilder(factorial).lineIs(6).build());

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, "fac", 6, true, "return 1", "n", "1");
                checkArgs(event.getTopStackFrame(), "n", "1");
                Iterator<DebugStackFrame> sfi = event.getStackFrames().iterator();
                for (int i = 1; i <= 5; i++) {
                    checkArgs(sfi.next(), "n", Integer.toString(i));
                }
                checkArgs(sfi.next()); // main
                assertSame(breakpoint, event.getBreakpoints().iterator().next());
                event.prepareStepOver(1);
            });

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, "fac", 8, false, "fac(n - 1)", "n", "2");
                checkArgs(event.getTopStackFrame(), "n", "2");
                assertEquals("1", event.getReturnValue().as(String.class));
                assertTrue(event.getBreakpoints().isEmpty());
                event.prepareStepOut();
            });

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, "fac", 8, false, "fac(n - 1)", "n", "3");
                assertEquals("2", event.getReturnValue().as(String.class));
                assertTrue(event.getBreakpoints().isEmpty());
                event.prepareStepOut();
            });

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, "fac", 8, false, "fac(n - 1)", "n", "4");
                assertEquals("6", event.getReturnValue().as(String.class));
                assertTrue(event.getBreakpoints().isEmpty());
                event.prepareStepOut();
            });

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, "fac", 8, false, "fac(n - 1)", "n", "5");
                assertEquals("24", event.getReturnValue().as(String.class));
                assertTrue(event.getBreakpoints().isEmpty());
                event.prepareStepOut();
            });

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, "main", 2, false, "fac(5)");
                checkArgs(event.getTopStackFrame());
                assertEquals("120", event.getReturnValue().as(String.class));
                assertTrue(event.getBreakpoints().isEmpty());
                event.prepareStepOut();
            });

            assertEquals("120", expectDone());
        }
    }

    @Test
    public void testStepInOver() throws Throwable {
        /*
         * For recursive function we want to ensure that we don't step when we step over a function.
         */
        final Source factorial = slCode("function main() {\n" +
                        "  return fac(5);\n" +
                        "}\n" +
                        "function fac(n) {\n" +
                        "  if (n <= 1) {\n" +
                        "    return 1;\n" + // break
                        "  }\n" +
                        "  return n * fac(n - 1);\n" +
                        "}\n");

        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();
            startEval(factorial);
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, "main", 2, true, "return fac(5)").prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, "fac", 5, true, "n <= 1", "n", "5").prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, "fac", 8, true, "return n * fac(n - 1)", "n", "5").prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, "main", 2, false, "fac(5)").prepareStepInto(1);
                assertEquals("120", event.getReturnValue().as(String.class));
            });

            expectDone();
        }
    }

    @Test
    public void testDebugger() throws Throwable {
        /*
         * Test AlwaysHalt is working.
         */
        final Source factorial = slCode("function main() {\n" +
                        "  return fac(5);\n" +
                        "}\n" +
                        "function fac(n) {\n" +
                        "  if (n <= 1) {\n" +
                        "    debugger; return 1;\n" + // // break
                        "  }\n" +
                        "  return n * fac(n - 1);\n" +
                        "}\n");

        try (DebuggerSession session = startSession()) {
            startEval(factorial);

            // make javac happy and use the session
            session.getBreakpoints();

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, "fac", 6, true, "debugger", "n", "1").prepareContinue();
            });

            expectDone();
        }
    }

    @Test(expected = ThreadDeath.class)
    public void testTimeboxing() throws Throwable {
        final Source endlessLoop = slCode("function main() {\n" +
                        "  i = 1; \n" +
                        "  while(i > 0) {\n" +
                        "    i = i + 1;\n" +
                        "  }\n" +
                        "  return i; \n" +
                        "}\n");

        final PolyglotEngine engine = PolyglotEngine.newBuilder().build();

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                Debugger.find(engine).startSession(new SuspendedCallback() {
                    public void onSuspend(SuspendedEvent event) {
                        event.prepareKill();
                    }
                }).suspendNextExecution();
            }
        }, 1000);

        engine.eval(endlessLoop);
    }

    @Test
    public void testNull() throws Throwable {
        final Source factorial = slCode("function main() {\n" +
                        "  res = doNull();\n" +
                        "  return res;\n" +
                        "}\n" +
                        "function doNull() {}\n");

        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();
            startEval(factorial);

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, "main", 2, true, "res = doNull()").prepareStepInto(1);
            });

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, "main", 3, true, "return res", "res", "NULL").prepareContinue();
            });

            assertEquals("NULL", expectDone());
        }
    }

    @Test
    public void testDebugValue() throws Throwable {
        final Source varsSource = slCode("function main() {\n" +
                        "  a = doNull();\n" +
                        "  b = 10 == 10;\n" +
                        "  c = 10;\n" +
                        "  d = \"str\";\n" +
                        "  e = new();\n" +
                        "  e.p1 = 1;\n" +
                        "  e.p2 = new();\n" +
                        "  e.p2.p21 = 21;\n" +
                        "  return;\n" +
                        "}\n" +
                        "function doNull() {}\n");

        try (DebuggerSession session = startSession()) {
            session.install(Breakpoint.newBuilder(varsSource).lineIs(10).build());
            startEval(varsSource);

            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();

                DebugScope scope = frame.getScope();
                DebugValue a = scope.getDeclaredValue("a");
                assertFalse(a.isArray());
                assertNull(a.getArray());
                assertNull(a.getProperties());

                DebugValue b = scope.getDeclaredValue("b");
                assertFalse(b.isArray());
                assertNull(b.getArray());
                assertNull(b.getProperties());

                DebugValue c = scope.getDeclaredValue("c");
                assertFalse(c.isArray());
                assertEquals("10", c.as(String.class));
                assertNull(c.getArray());
                assertNull(c.getProperties());

                DebugValue d = scope.getDeclaredValue("d");
                assertFalse(d.isArray());
                assertEquals("str", d.as(String.class));
                assertNull(d.getArray());
                assertNull(d.getProperties());

                DebugValue e = scope.getDeclaredValue("e");
                assertFalse(e.isArray());
                assertNull(e.getArray());
                assertEquals(scope, e.getScope());
                Collection<DebugValue> propertyValues = e.getProperties();
                assertEquals(2, propertyValues.size());
                Iterator<DebugValue> propertiesIt = propertyValues.iterator();
                assertTrue(propertiesIt.hasNext());
                DebugValue p1 = propertiesIt.next();
                assertEquals("p1", p1.getName());
                assertEquals("1", p1.as(String.class));
                assertNull(p1.getScope());
                assertTrue(propertiesIt.hasNext());
                DebugValue p2 = propertiesIt.next();
                assertEquals("p2", p2.getName());
                assertNull(p2.getScope());
                assertFalse(propertiesIt.hasNext());

                propertyValues = p2.getProperties();
                assertEquals(1, propertyValues.size());
                propertiesIt = propertyValues.iterator();
                assertTrue(propertiesIt.hasNext());
                DebugValue p21 = propertiesIt.next();
                assertEquals("p21", p21.getName());
                assertEquals("21", p21.as(String.class));
                assertNull(p21.getScope());
                assertFalse(propertiesIt.hasNext());
            });

            expectDone();
        }
    }

    @Test
    public void testValuesScope() throws Throwable {
        final Source varsSource = slCode("function main() {\n" +
                        "  a = 1;\n" +
                        "  if (a > 0) {\n" +
                        "    b = 10;\n" +
                        "    println(b);\n" +
                        "  }\n" +
                        "  println(b);\n" +
                        "  println(a);\n" +
                        "  println(\"END.\");\n" +
                        "}");

        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();
            startEval(varsSource);

            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                // No variables first:
                assertFalse(frame.getScope().getDeclaredValues().iterator().hasNext());
                event.prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                // "a" only:
                DebugScope scope = frame.getScope();
                Iterator<DebugValue> varIt = scope.getDeclaredValues().iterator();
                assertTrue(varIt.hasNext());
                DebugValue a = varIt.next();
                assertEquals("a", a.getName());
                assertEquals(scope, a.getScope());
                assertFalse(varIt.hasNext());
                event.prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                // "a" only:
                DebugScope scope = frame.getScope();
                Iterator<DebugValue> varIt = scope.getParent().getDeclaredValues().iterator();
                assertTrue(varIt.hasNext());
                DebugValue a = varIt.next();
                assertEquals("a", a.getName());
                assertEquals(scope.getParent(), a.getScope());
                assertFalse(varIt.hasNext());
                event.prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                // "a" and "b":
                DebugScope scope = frame.getScope();
                Iterator<DebugValue> varIt = scope.getDeclaredValues().iterator();
                assertTrue(varIt.hasNext());
                DebugValue b = varIt.next();
                assertEquals("b", b.getName());
                assertEquals(scope, b.getScope());
                // "a" is in the parent:
                assertFalse(varIt.hasNext());
                varIt = scope.getParent().getDeclaredValues().iterator();
                assertTrue(varIt.hasNext());
                DebugValue a = varIt.next();
                assertEquals("a", a.getName());
                assertEquals(scope.getParent(), a.getScope());
                assertFalse(varIt.hasNext());
                event.prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                // "a" only again:
                DebugScope scope = frame.getScope();
                Iterator<DebugValue> varIt = scope.getDeclaredValues().iterator();
                assertTrue(varIt.hasNext());
                DebugValue a = varIt.next();
                assertEquals("a", a.getName());
                assertEquals(scope, a.getScope());
                assertFalse(varIt.hasNext());
                event.prepareContinue();
            });

            expectDone();
        }
    }

    @Test
    public void testMetaObjects() {
        final Source varsSource = slCode("function main() {\n" +
                        "  a = doNull();\n" +
                        "  b = 10 == 10;\n" +
                        "  c = 10;\n" +
                        "  cBig = 1000000000*1000000000*1000000000*1000000000;\n" +
                        "  d = \"str\";\n" +
                        "  e = new();\n" +
                        "  f = doNull;\n" +
                        "  return;\n" +
                        "}\n" +
                        "function doNull() {}\n");

        try (DebuggerSession session = startSession()) {
            session.install(Breakpoint.newBuilder(varsSource).lineIs(9).build());
            startEval(varsSource);

            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();

                DebugScope scope = frame.getScope();
                DebugValue v = scope.getDeclaredValue("a");
                assertEquals("Null", v.getMetaObject().as(String.class));
                v = scope.getDeclaredValue("b");
                assertEquals("Boolean", v.getMetaObject().as(String.class));
                v = scope.getDeclaredValue("c");
                assertEquals("Number", v.getMetaObject().as(String.class));
                v = scope.getDeclaredValue("cBig");
                assertEquals("Number", v.getMetaObject().as(String.class));
                v = scope.getDeclaredValue("d");
                assertEquals("String", v.getMetaObject().as(String.class));
                v = scope.getDeclaredValue("e");
                assertEquals("Object", v.getMetaObject().as(String.class));
                v = scope.getDeclaredValue("f");
                assertEquals("Function", v.getMetaObject().as(String.class));
            });

            expectDone();
        }
    }

    @Test
    public void testSourceLocation() {
        final Source varsSource = slCode("function main() {\n" +
                        "  a = doNull();\n" +
                        "  c = 10;\n" +
                        "  d = \"str\";\n" +
                        "  e = new();\n" +
                        "  f = doNull;\n" +
                        "  return;\n" +
                        "}\n" +
                        "function doNull() {}\n");

        try (DebuggerSession session = startSession()) {
            session.install(Breakpoint.newBuilder(varsSource).lineIs(7).build());
            startEval(varsSource);

            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();

                DebugScope scope = frame.getScope();
                DebugValue v = scope.getDeclaredValue("a");
                assertNull(v.getSourceLocation());
                v = scope.getDeclaredValue("c");
                assertNull(v.getSourceLocation());
                v = scope.getDeclaredValue("d");
                assertNull(v.getSourceLocation());
                v = scope.getDeclaredValue("e");
                assertNull(v.getSourceLocation());
                v = scope.getDeclaredValue("f");
                SourceSection sourceLocation = v.getSourceLocation();
                Assert.assertNotNull(sourceLocation);
                assertEquals(9, sourceLocation.getStartLine());
                assertEquals(9, sourceLocation.getEndLine());
                assertEquals("doNull() {}", sourceLocation.getCode());
            });

            expectDone();
        }
    }

    @Test
    public void testStack() {
        final Source stackSource = slCode("function main() {\n" +
                        "  return fac(10);\n" +
                        "}\n" +
                        "function fac(n) {\n" +
                        "  if (n <= 1) {\n" +
                        "    return 1;\n" + // break
                        "  }\n" +
                        "  return n * fac(n - 1);\n" +
                        "}\n");

        try (DebuggerSession session = startSession()) {
            session.install(Breakpoint.newBuilder(stackSource).lineIs(6).build());
            startEval(stackSource);

            expectSuspended((SuspendedEvent event) -> {
                Iterator<DebugStackFrame> sfIt = event.getStackFrames().iterator();
                assertTrue(sfIt.hasNext());
                DebugStackFrame dsf = sfIt.next();
                assertEquals("fac", dsf.getName());
                assertEquals(6, dsf.getSourceSection().getStartLine());
                assertFalse(dsf.isInternal());
                int numStacksAt8 = 10 - 1;
                for (int i = 0; i < numStacksAt8; i++) {
                    assertTrue(sfIt.hasNext());
                    dsf = sfIt.next();
                    assertEquals("fac", dsf.getName());
                    assertEquals(8, dsf.getSourceSection().getStartLine());
                    assertFalse(dsf.isInternal());
                }
                assertTrue(sfIt.hasNext());
                dsf = sfIt.next();
                assertEquals("main", dsf.getName());
                assertEquals(2, dsf.getSourceSection().getStartLine());
                assertFalse(dsf.isInternal());
                assertFalse(sfIt.hasNext());
            });
            expectDone();
        }
    }

    @Test
    public void testStackInterop() {
        final Source stackSource = slCode("function fac(n, multiply) {\n" +
                        "  if (n <= 1) {\n" +
                        "    debugger;\n" +
                        "    return 1;\n" +
                        "  }\n" +
                        "  return multiply.multiply(n, fac, n - 1);\n" +
                        "}\n");

        PolyglotEngine engine = PolyglotEngine.newBuilder().setOut(System.out).setErr(System.err).build();
        engine.eval(stackSource);
        PolyglotEngine.Value fac = engine.findGlobalSymbol("fac");
        Object multiply = new Multiply();
        Debugger debugger = Debugger.find(engine);
        boolean[] done = new boolean[1];
        try (DebuggerSession session = debugger.startSession((event) -> {
            Iterator<DebugStackFrame> sfIt = event.getStackFrames().iterator();
            assertTrue(sfIt.hasNext());
            DebugStackFrame dsf = sfIt.next();
            assertEquals("fac", dsf.getName());
            assertEquals(3, dsf.getSourceSection().getStartLine());
            assertFalse(dsf.isInternal());
            int numStacksAt6 = 10 - 1;
            int numInteropStacks = 0;
            for (int i = 0; i < numStacksAt6;) {
                assertTrue(sfIt.hasNext());
                dsf = sfIt.next();
                boolean inFac = dsf.getName() != null;
                if (inFac) {
                    // Frame in fac function
                    assertEquals("fac", dsf.getName());
                    assertEquals(6, dsf.getSourceSection().getStartLine());
                    assertFalse(dsf.isInternal());
                    i++;
                } else {
                    // Frame in an interop method, internal
                    assertEquals(null, dsf.getName());
                    assertNull(dsf.getSourceSection());
                    assertTrue(dsf.isInternal());
                    numInteropStacks++;
                }
            }
            // There were at least as many interop internal frames as frames in fac function:
            assertTrue("numInteropStacks = " + numInteropStacks, numInteropStacks >= numStacksAt6);
            // Some more internal frames remain
            while (sfIt.hasNext()) {
                dsf = sfIt.next();
                assertEquals(null, dsf.getName());
                assertNull(dsf.getSourceSection());
                assertTrue(dsf.isInternal());
            }
            done[0] = true;
        })) {
            Assert.assertNotNull(session);
            PolyglotEngine.Value ret = fac.execute(new Object[]{10, multiply});
            assertNumber(ret.get(), 3628800L);
        }
        assertTrue(done[0]);
    }

    private static void assertNumber(Object real, double expected) {
        if (real instanceof Number) {
            assertEquals(expected, ((Number) real).doubleValue(), 0.1);
        } else {
            fail("Expecting a number " + real);
        }
    }

    public static class Multiply {
        public long multiply(long n, Fac fce, long i) {
            return n * fce.fac(i, this);
        }
    }

    public interface Fac {
        long fac(long n, Multiply multiply);
    }
}
