/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.DebugException;
import com.oracle.truffle.api.debug.DebugScope;
import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SourceElement;
import com.oracle.truffle.api.debug.StepConfig;
import com.oracle.truffle.api.debug.SuspendAnchor;
import com.oracle.truffle.api.debug.SuspendedCallback;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.tck.DebuggerTester;
import org.graalvm.polyglot.HostAccess;

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
        return Source.create("sl", code);
    }

    private DebuggerSession startSession() {
        return tester.startSession();
    }

    private DebuggerSession startSession(SourceElement... sourceElements) {
        return tester.startSession(sourceElements);
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
        final String actualCode = suspendedEvent.getSourceSection().getCharacters().toString();
        Assert.assertEquals(expectedCode, actualCode);
        final boolean actualIsBefore = (suspendedEvent.getSuspendAnchor() == SuspendAnchor.BEFORE);
        Assert.assertEquals(expectedIsBefore, actualIsBefore);

        checkStack(suspendedEvent.getTopStackFrame(), name, expectedFrame);
        return suspendedEvent;
    }

    protected void checkStack(DebugStackFrame frame, String name, String... expectedFrame) {
        assertEquals(name, frame.getName());
        checkDebugValues("variables", frame.getScope(), expectedFrame);
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

    private static void checkDebugValues(String msg, DebugScope scope, String... expected) {
        Map<String, DebugValue> valMap = new HashMap<>();
        DebugScope currentScope = scope;
        while (currentScope != null) {
            for (DebugValue value : currentScope.getDeclaredValues()) {
                valMap.put(value.getName(), value);
            }
            currentScope = currentScope.getParent();
        }
        checkDebugValues(msg, valMap, expected);
    }

    private static void checkDebugValues(String msg, Iterable<DebugValue> values, String... expected) {
        Map<String, DebugValue> valMap = new HashMap<>();
        for (DebugValue value : values) {
            valMap.put(value.getName(), value);
        }
        checkDebugValues(msg, valMap, expected);
    }

    private static void checkDebugValues(String msg, Map<String, DebugValue> valMap, String... expected) {
        String message = String.format("Frame %s expected %s got %s", msg, Arrays.toString(expected), valMap.toString());
        Assert.assertEquals(message, expected.length / 2, valMap.size());
        for (int i = 0; i < expected.length; i = i + 2) {
            String expectedIdentifier = expected[i];
            String expectedValue = expected[i + 1];
            DebugValue value = valMap.get(expectedIdentifier);
            Assert.assertNotNull(value);
            Assert.assertEquals(expectedValue, value.toDisplayString());
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
            Breakpoint breakpoint = session.install(Breakpoint.newBuilder(getSourceImpl(factorial)).lineIs(6).build());

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
                assertEquals("1", event.getReturnValue().toDisplayString());
                assertTrue(event.getBreakpoints().isEmpty());
                event.prepareStepOut(1);
            });

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, "fac", 8, false, "fac(n - 1)", "n", "3");
                assertEquals("2", event.getReturnValue().toDisplayString());
                assertTrue(event.getBreakpoints().isEmpty());
                event.prepareStepOut(1);
            });

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, "fac", 8, false, "fac(n - 1)", "n", "4");
                assertEquals("6", event.getReturnValue().toDisplayString());
                assertTrue(event.getBreakpoints().isEmpty());
                event.prepareStepOut(1);
            });

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, "fac", 8, false, "fac(n - 1)", "n", "5");
                assertEquals("24", event.getReturnValue().toDisplayString());
                assertTrue(event.getBreakpoints().isEmpty());
                event.prepareStepOut(1);
            });

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, "main", 2, false, "fac(5)");
                checkArgs(event.getTopStackFrame());
                assertEquals("120", event.getReturnValue().toDisplayString());
                assertTrue(event.getBreakpoints().isEmpty());
                event.prepareStepOut(1);
            });

            assertEquals("120", expectDone());
        }
    }

    @Test
    public void testGuestFunctionBreakpoints() throws Throwable {
        final Source functions = slCode("function main() {\n" +
                        "  a = fac;\n" +
                        "  return fac(5);\n" +
                        "}\n" +
                        "function fac(n) {\n" +
                        "  if (n <= 1) {\n" +
                        "    return 1;\n" + // break
                        "  }\n" +
                        "  return n * facMin1(n);\n" +
                        "}\n" +
                        "function facMin1(n) {\n" +
                        "  m = n - 1;\n" +
                        "  return fac(m);\n" +
                        "}\n");
        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();
            startEval(functions);
            Breakpoint[] functionBreakpoint = new Breakpoint[]{null};

            expectSuspended((SuspendedEvent event) -> {
                event.prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                DebugValue fac = event.getTopStackFrame().getScope().getDeclaredValue("a");
                // Breakpoint in "fac" will not suspend in "facMin1".
                Breakpoint breakpoint = Breakpoint.newBuilder(fac.getSourceLocation()).sourceElements(SourceElement.ROOT).rootInstance(fac).build();
                session.install(breakpoint);
                functionBreakpoint[0] = breakpoint;
                event.prepareContinue();
            });
            expectSuspended((SuspendedEvent event) -> {
                Assert.assertEquals(5, event.getSourceSection().getStartLine());
                Assert.assertEquals(5, event.getTopStackFrame().getScope().getDeclaredValue("n").asInt());
                event.prepareContinue();
            });
            expectSuspended((SuspendedEvent event) -> {
                Assert.assertEquals(5, event.getSourceSection().getStartLine());
                Assert.assertEquals(4, event.getTopStackFrame().getScope().getDeclaredValue("n").asInt());
                functionBreakpoint[0].dispose();
                event.prepareContinue();
            });
            expectDone();
        }
    }

    @Test
    public void testBuiltInFunctionBreakpoints() throws Throwable {
        final Source functions = slCode("function main() {\n" +
                        "  a = isNull;\n" +
                        "  b = nanoTime;\n" +
                        "  isNull(a);\n" +
                        "  isExecutable(a);\n" +
                        "  isNull(b);\n" +
                        "  nanoTime();\n" +
                        "  isNull(a);\n" +
                        "  nanoTime();\n" +
                        "}\n");
        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();
            startEval(functions);
            Breakpoint[] functionBreakpoint = new Breakpoint[]{null};

            expectSuspended((SuspendedEvent event) -> {
                event.prepareStepOver(2);
            });
            expectSuspended((SuspendedEvent event) -> {
                DebugValue isNull = event.getTopStackFrame().getScope().getDeclaredValue("a");
                Breakpoint breakpoint = Breakpoint.newBuilder((URI) null).sourceElements(SourceElement.ROOT).rootInstance(isNull).build();
                session.install(breakpoint);
                functionBreakpoint[0] = breakpoint;
                event.prepareContinue();
            });
            expectSuspended((SuspendedEvent event) -> {
                Assert.assertFalse(event.getSourceSection().isAvailable());
                Assert.assertEquals("isNull", event.getTopStackFrame().getName());
                Iterator<DebugStackFrame> frames = event.getStackFrames().iterator();
                frames.next(); // Skip the top one
                DebugStackFrame mainFrame = frames.next();
                Assert.assertEquals(4, mainFrame.getSourceSection().getStartLine());
                // Dispose the breakpoint on isNull() and create one on nanoTime() instead:
                functionBreakpoint[0].dispose();
                DebugValue nanoTime = mainFrame.getScope().getDeclaredValue("b");
                // Breakpoint in "fac" will not suspend in "facMin1".
                Breakpoint breakpoint = Breakpoint.newBuilder(nanoTime.getSourceLocation()).sourceElements(SourceElement.ROOT).rootInstance(nanoTime).build();
                session.install(breakpoint);
                functionBreakpoint[0] = breakpoint;
                event.prepareContinue();
            });
            expectSuspended((SuspendedEvent event) -> {
                Assert.assertFalse(event.getSourceSection().isAvailable());
                Assert.assertEquals("nanoTime", event.getTopStackFrame().getName());
                Iterator<DebugStackFrame> frames = event.getStackFrames().iterator();
                frames.next(); // Skip the top one
                DebugStackFrame mainFrame = frames.next();
                Assert.assertEquals(7, mainFrame.getSourceSection().getStartLine());
                functionBreakpoint[0].dispose();
                event.prepareContinue();
            });
            expectDone();
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
                assertEquals("120", event.getReturnValue().toDisplayString());
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

    @Test
    public void testTimeboxing() throws Throwable {
        final Source endlessLoop = slCode("function main() {\n" +
                        "  i = 1; \n" +
                        "  while(i > 0) {\n" +
                        "    i = i + 1;\n" +
                        "  }\n" +
                        "  return i; \n" +
                        "}\n");

        final Context context = Context.create("sl");
        Debugger debugger = context.getEngine().getInstruments().get("debugger").lookup(Debugger.class);
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                debugger.startSession(new SuspendedCallback() {
                    public void onSuspend(SuspendedEvent event) {
                        event.prepareKill();
                    }
                }).suspendNextExecution();
            }
        }, 0, 10);

        try {
            context.eval(endlessLoop); // throws KillException, wrapped by PolyglotException
            Assert.fail();
        } catch (PolyglotException pex) {
            Assert.assertTrue(pex.isCancelled());
        }
        timer.cancel();
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
            session.install(Breakpoint.newBuilder(getSourceImpl(varsSource)).lineIs(10).build());
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
                assertEquals("10", c.toDisplayString());
                assertNull(c.getArray());
                assertNull(c.getProperties());

                DebugValue d = scope.getDeclaredValue("d");
                assertFalse(d.isArray());
                assertEquals("str", d.toDisplayString());
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
                assertEquals("1", p1.toDisplayString());
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
                assertEquals("21", p21.toDisplayString());
                assertNull(p21.getScope());
                assertFalse(propertiesIt.hasNext());

                DebugValue ep1 = e.getProperty("p1");
                assertEquals("1", ep1.toDisplayString());
                ep1.set(p21);
                assertEquals("21", ep1.toDisplayString());
                assertNull(e.getProperty("NonExisting"));
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
            session.install(Breakpoint.newBuilder(getSourceImpl(varsSource)).lineIs(9).build());
            startEval(varsSource);

            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();

                DebugScope scope = frame.getScope();
                DebugValue v = scope.getDeclaredValue("a");
                assertEquals("NULL", v.getMetaObject().toDisplayString());
                v = scope.getDeclaredValue("b");
                assertEquals("Boolean", v.getMetaObject().toDisplayString());
                v = scope.getDeclaredValue("c");
                assertEquals("Number", v.getMetaObject().toDisplayString());
                v = scope.getDeclaredValue("cBig");
                assertEquals("Number", v.getMetaObject().toDisplayString());
                v = scope.getDeclaredValue("d");
                assertEquals("String", v.getMetaObject().toDisplayString());
                v = scope.getDeclaredValue("e");
                assertEquals("Object", v.getMetaObject().toDisplayString());
                v = scope.getDeclaredValue("f");
                assertEquals("Function", v.getMetaObject().toDisplayString());
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
            session.install(Breakpoint.newBuilder(getSourceImpl(varsSource)).lineIs(7).build());
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
                assertEquals("doNull() {}", sourceLocation.getCharacters());
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
            session.install(Breakpoint.newBuilder(getSourceImpl(stackSource)).lineIs(6).build());
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

                // skip internal frames
                while (sfIt.hasNext()) {
                    dsf = sfIt.next();
                    assertTrue(dsf.isInternal());
                }
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

        Context context = Context.create("sl");
        context.eval(stackSource);
        Value fac = context.getBindings("sl").getMember("fac");
        Object multiply = new Multiply();
        Debugger debugger = context.getEngine().getInstruments().get("debugger").lookup(Debugger.class);
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
                boolean inFac = dsf.getName() != null && !dsf.isInternal();
                if (inFac) {
                    // Frame in fac function
                    assertEquals("fac", dsf.getName());
                    assertEquals(6, dsf.getSourceSection().getStartLine());
                    assertFalse(dsf.isInternal());
                    i++;
                } else {
                    // Frame in an interop method, internal
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
                assertNull(dsf.getSourceSection());
                assertTrue(dsf.isInternal());
            }
            done[0] = true;
        })) {
            Assert.assertNotNull(session);
            Value ret = fac.execute(10, multiply);
            assertNumber(ret.asLong(), 3628800L);
        }

        assertTrue(done[0]);
    }

    @Test
    public void testUnwindAndReenter() {
        final Source source = slCode("function main() {\n" +
                        "  return fac(10);\n" +
                        "}\n" +
                        "function fac(n) {\n" +
                        "  if (n <= 1) {\n" +
                        "    return 1;\n" + // break
                        "  }\n" +
                        "  return n * fac(n - 1);\n" +
                        "}\n");

        try (DebuggerSession session = startSession()) {
            session.install(Breakpoint.newBuilder(getSourceImpl(source)).lineIs(6).build());
            startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                assertEquals(6, event.getTopStackFrame().getSourceSection().getStartLine());
                Iterator<DebugStackFrame> frames = event.getStackFrames().iterator();
                for (int i = 0; i < 5; i++) {
                    frames.next();
                }
                event.prepareUnwindFrame(frames.next());
            });
            expectSuspended((SuspendedEvent event) -> {
                assertEquals(8, event.getTopStackFrame().getSourceSection().getStartLine());
                assertEquals("7", event.getTopStackFrame().getScope().getDeclaredValue("n").toDisplayString());
                event.prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                assertEquals(5, event.getTopStackFrame().getSourceSection().getStartLine());
                assertEquals("6", event.getTopStackFrame().getScope().getDeclaredValue("n").toDisplayString());
                event.prepareContinue();
            });
            expectSuspended((SuspendedEvent event) -> {
                // The breakpoint hit again
                assertEquals(6, event.getTopStackFrame().getSourceSection().getStartLine());
            });
            expectDone();
        }
    }

    @Test
    public void testArgumentsAndValues() throws Throwable {
        // Test that after a re-enter, arguments are kept and variables are cleared.
        final Source source = slCode("function main() {\n" +
                        "  i = 10;\n" +
                        "  return fnc(i = i + 1, 20);\n" +
                        "}\n" +
                        "function fnc(n, m) {\n" +
                        "  x = n + m;\n" +
                        "  n = m - n;\n" +
                        "  m = m / 2;\n" +
                        "  x = x + n * m;\n" +
                        "  return x;\n" +
                        "}\n");

        try (DebuggerSession session = startSession()) {
            session.install(Breakpoint.newBuilder(getSourceImpl(source)).lineIs(6).build());
            startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                assertEquals(6, frame.getSourceSection().getStartLine());
                checkArgs(frame, "n", "11", "m", "20");
                checkStack(frame, "fnc", "n", "11", "m", "20");
                event.prepareStepOver(4);
            });
            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                assertEquals(10, frame.getSourceSection().getStartLine());
                checkArgs(frame, "n", "11", "m", "20");
                checkStack(frame, "fnc", "n", "9", "m", "10", "x", "121");
                event.prepareUnwindFrame(frame);
            });
            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                assertEquals(3, frame.getSourceSection().getStartLine());
                checkArgs(frame);
                checkStack(frame, "main", "i", "11");
            });
            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                assertEquals(6, frame.getSourceSection().getStartLine());
                checkArgs(frame, "n", "11", "m", "20");
                checkStack(frame, "fnc", "n", "11", "m", "20");
            });
            assertEquals("121", expectDone());
        }
    }

    @Test
    public void testMisplacedLineBreakpoints() throws Throwable {
        final String sourceStr = "// A comment\n" +              // 1
                        "function invocable(n) {\n" +
                        "  if (R3_n <= 1) {\n" +
                        "    R4-6_one \n" +
                        "        =\n" +                 // 5
                        "          1;\n" +
                        "    R7-9_return\n" +
                        "        one;\n" +
                        "  } else {\n" +
                        "    // A comment\n" +          // 10
                        "    while (\n" +
                        "        R10-12_n > 0\n" +
                        "          ) { \n" +
                        "      R13-16_one \n" +
                        "          = \n" +              // 15
                        "            2;\n" +
                        "      R17-20_n = n -\n" +
                        "          one *\n" +
                        "          one;\n" +
                        "    }\n" +                     // 20
                        "    R21_n =\n" +
                        "        n - 1; R22_n = n + 1;\n" +
                        "    R23-27_return\n" +
                        "        n * n;\n" +
                        "    \n" +                      // 25
                        "  }\n" +
                        "}\n" +
                        "\n" +
                        "function\n" +
                        "   main()\n" +                 // 30
                        "         {\n" +
                        "  R31-33_return invocable(1) + invocable(2);\n" +
                        "}\n" +
                        "\n";
        tester.assertLineBreakpointsResolution(sourceStr, new DebuggerTester.PositionPredicate() {
            @Override
            public boolean testLine(int line) {
                return 3 <= line && line <= 27 || 31 <= line && line <= 33;
            }

            @Override
            public boolean testLineColumn(int line, int column) {
                return testLine(line);
            }
        }, "R", "sl");
    }

    @Test
    public void testMisplacedColumnBreakpoints() throws Throwable {
        final String sourceStr = "// A comment\n" +              // 1
                        "function invocable(B3_n) {\n" +
                        "  if (R3_n <= 1) B4_ B5_{B6_\n" +
                        "    R4-7_one \n" +
                        "        =\n" +                 // 5
                        "          B7_1;\n" +
                        "    R8_return\n" +
                        "        one;\n" +
                        "  B8_}B9_ else B10_ {\n" +
                        "    // A commentB11_\n" +          // 10
                        "    while (\n" +
                        "        R9-11_n > 0\n" +
                        "          ) B12_ { \n" +
                        "      R12_one \n" +
                        "          = \n" +              // 15
                        "            2;\n" +
                        "      R13-14_n = n -\n" +
                        "          one *\n" +
                        "          one;\n" +
                        "   B13_ B14_}B15_\n" +                    // 20
                        "    R15-16_return\n" +
                        "        n * n;\n" +
                        "    \n" +
                        "  }B16_\n" +
                        "}\n" +                         // 25
                        "\n" +
                        "function\n" +
                        "   main()\n" +
                        "         {\n" +
                        "  return invocable(1) + invocable(2);\n" +
                        "}\n" +
                        "\n";
        tester.assertColumnBreakpointsResolution(sourceStr, "B", "R", "sl");
    }

    @Test
    public void testBreakpointEverywhereBreaks() throws Throwable {
        final String sourceCode = "// A comment\n" +              // 1
                        "function invocable(n) {\n" +
                        "  if (n <= 1) {\n" +
                        "    one \n" +
                        "        =\n" +                 // 5
                        "          1;\n" +
                        "    return\n" +
                        "        one;\n" +
                        "  } else {\n" +
                        "    // A comment\n" +          // 10
                        "    while (\n" +
                        "        n > 0\n" +
                        "          ) { \n" +
                        "      one \n" +
                        "          = \n" +              // 15
                        "            2;\n" +
                        "      n = n -\n" +
                        "          one *\n" +
                        "          one;\n" +
                        "    }\n" +                    // 20
                        "    return\n" +
                        "        n * n;\n" +
                        "    \n" +
                        "  }\n" +
                        "}\n" +                         // 25
                        "\n" +
                        "function\n" +
                        "   main()\n" +
                        "         {\n" +
                        "  return invocable(1) + invocable(2);\n" +
                        "}\n" +
                        "\n";
        Source source = Source.newBuilder("sl", sourceCode, "testBreakpointsAnywhere.sl").build();
        tester.assertBreakpointsBreakEverywhere(source, new DebuggerTester.PositionPredicate() {
            @Override
            public boolean testLine(int line) {
                return 3 <= line && line <= 25 || 29 <= line && line <= 31;
            }

            @Override
            public boolean testLineColumn(int line, int column) {
                return 3 <= line && line <= 24 || line == 25 && column == 1 || 29 <= line && line <= 30 || line == 31 && column == 1;
            }
        });
    }

    private enum StepDepth {
        INTO,
        OVER,
        OUT
    }

    private void checkExpressionStepPositions(String stepPositions, boolean includeStatements, StepDepth... steps) {
        Source source = slCode("function main() {\n" +
                        "  x = 2;\n" +
                        "  while (x >= 0 && 5 >= 0) {\n" +
                        "    a = 2 * x;\n" +
                        "    b = (a * a) / (x * x + 1);\n" +
                        "    x = x - transform(a, b);\n" +
                        "  }\n" +
                        "  return x / 1;\n" +
                        "}\n" +
                        "function transform(a, b) {\n" +
                        "  return (1 + 1) * (a + b);\n" +
                        "}\n");
        SourceElement[] elements;
        if (includeStatements) {
            elements = new SourceElement[]{SourceElement.EXPRESSION, SourceElement.STATEMENT};
        } else {
            elements = new SourceElement[]{SourceElement.EXPRESSION};
        }
        try (DebuggerSession session = startSession(elements)) {
            session.suspendNextExecution();
            startEval(source);

            // Step through the program
            StepDepth lastStep = steps[0];
            int stepIndex = 0;
            StepConfig expressionStepConfig = StepConfig.newBuilder().sourceElements(elements).build();
            for (String stepPos : stepPositions.split("\n")) {
                if (stepIndex < steps.length) {
                    lastStep = steps[stepIndex++];
                }
                final StepDepth stepDepth = lastStep;
                expectSuspended((SuspendedEvent event) -> {
                    if (!includeStatements) {
                        assertTrue("Needs to be an expression", event.hasSourceElement(SourceElement.EXPRESSION));
                    } else {
                        assertTrue("Needs to be an expression or statement",
                                        event.hasSourceElement(SourceElement.EXPRESSION) || event.hasSourceElement(SourceElement.STATEMENT));
                    }
                    SourceSection ss = event.getSourceSection();
                    DebugValue[] inputValues = event.getInputValues();
                    String input = "";
                    if (inputValues != null) {
                        StringBuilder inputBuilder = new StringBuilder("(");
                        for (DebugValue v : inputValues) {
                            if (inputBuilder.length() > 1) {
                                inputBuilder.append(',');
                            }
                            if (v != null) {
                                inputBuilder.append(v.toDisplayString());
                            } else {
                                inputBuilder.append("null");
                            }
                        }
                        inputBuilder.append(") ");
                        input = inputBuilder.toString();
                    }
                    DebugValue returnValue = event.getReturnValue();
                    String ret = (returnValue != null) ? returnValue.toDisplayString() : "<none>";

                    String actualPos = "<" + ss.getStartLine() + ":" + ss.getStartColumn() + " - " + ss.getEndLine() + ":" + ss.getEndColumn() + "> " + input + ret;
                    assertEquals(stepPos, actualPos);
                    switch (stepDepth) {
                        case INTO:
                            event.prepareStepInto(expressionStepConfig);
                            break;
                        case OVER:
                            event.prepareStepOver(expressionStepConfig);
                            break;
                        case OUT:
                            event.prepareStepOut(expressionStepConfig);
                            break;
                    }
                });
            }
            expectDone();
        }
    }

    @Test
    public void testExpressionStepInto() {
        final String stepIntoPositions = "<2:3 - 2:7> <none>\n" +
                        "<2:7 - 2:7> <none>\n" +
                        "<2:7 - 2:7> () 2\n" +
                        "<2:3 - 2:7> (2) 2\n" +
                        "<3:10 - 3:25> <none>\n" +
                        "<3:10 - 3:15> <none>\n" +
                        "<3:10 - 3:10> <none>\n" +
                        "<3:10 - 3:10> () 2\n" +
                        "<3:15 - 3:15> <none>\n" +
                        "<3:15 - 3:15> () 0\n" +
                        "<3:10 - 3:15> (2,0) true\n" +
                        "<3:20 - 3:25> <none>\n" +
                        "<3:20 - 3:20> <none>\n" +
                        "<3:20 - 3:20> () 5\n" +
                        "<3:25 - 3:25> <none>\n" +
                        "<3:25 - 3:25> () 0\n" +
                        "<3:20 - 3:25> (5,0) true\n" +
                        "<3:10 - 3:25> (true,true) true\n" +
                        "<4:5 - 4:13> <none>\n" +
                        "<4:9 - 4:13> <none>\n" +
                        "<4:9 - 4:9> <none>\n" +
                        "<4:9 - 4:9> () 2\n" +
                        "<4:13 - 4:13> <none>\n" +
                        "<4:13 - 4:13> () 2\n" +
                        "<4:9 - 4:13> (2,2) 4\n" +
                        "<4:5 - 4:13> (4) 4\n" +
                        "<5:5 - 5:29> <none>\n" +
                        "<5:9 - 5:29> <none>\n" +
                        "<5:10 - 5:14> <none>\n" +
                        "<5:10 - 5:10> <none>\n" +
                        "<5:10 - 5:10> () 4\n" +
                        "<5:14 - 5:14> <none>\n" +
                        "<5:14 - 5:14> () 4\n" +
                        "<5:10 - 5:14> (4,4) 16\n" +
                        "<5:20 - 5:28> <none>\n" +
                        "<5:20 - 5:24> <none>\n" +
                        "<5:20 - 5:20> <none>\n" +
                        "<5:20 - 5:20> () 2\n" +
                        "<5:24 - 5:24> <none>\n" +
                        "<5:24 - 5:24> () 2\n" +
                        "<5:20 - 5:24> (2,2) 4\n" +
                        "<5:28 - 5:28> <none>\n" +
                        "<5:28 - 5:28> () 1\n" +
                        "<5:20 - 5:28> (4,1) 5\n" +
                        "<5:9 - 5:29> () 3\n" +
                        "<5:5 - 5:29> (3) 3\n" +
                        "<6:5 - 6:27> <none>\n" +
                        "<6:9 - 6:27> <none>\n" +
                        "<6:9 - 6:9> <none>\n" +
                        "<6:9 - 6:9> () 2\n" +
                        "<6:13 - 6:27> <none>\n" +
                        "<6:13 - 6:21> <none>\n" +
                        "<6:13 - 6:21> () transform\n" +
                        "<6:23 - 6:23> <none>\n" +
                        "<6:23 - 6:23> () 4\n" +
                        "<6:26 - 6:26> <none>\n" +
                        "<6:26 - 6:26> () 3\n" +
                        "<11:10 - 11:26> <none>\n" +
                        "<11:11 - 11:15> <none>\n" +
                        "<11:11 - 11:11> <none>\n" +
                        "<11:11 - 11:11> () 1\n" +
                        "<11:15 - 11:15> <none>\n" +
                        "<11:15 - 11:15> () 1\n" +
                        "<11:11 - 11:15> (1,1) 2\n" +
                        "<11:21 - 11:25> <none>\n" +
                        "<11:21 - 11:21> <none>\n" +
                        "<11:21 - 11:21> () 4\n" +
                        "<11:25 - 11:25> <none>\n" +
                        "<11:25 - 11:25> () 3\n" +
                        "<11:21 - 11:25> (4,3) 7\n" +
                        "<11:10 - 11:26> () 14\n" +
                        "<6:13 - 6:27> (transform,4,3) 14\n" +
                        "<6:9 - 6:27> (2,14) -12\n" +
                        "<6:5 - 6:27> (-12) -12\n" +
                        "<3:10 - 3:25> <none>\n" +
                        "<3:10 - 3:15> <none>\n" +
                        "<3:10 - 3:10> <none>\n" +
                        "<3:10 - 3:10> () -12\n" +
                        "<3:15 - 3:15> <none>\n" +
                        "<3:15 - 3:15> () 0\n" +
                        "<3:10 - 3:15> (-12,0) false\n" +
                        "<3:10 - 3:25> (false,null) false\n" +
                        "<8:10 - 8:14> <none>\n" +
                        "<8:10 - 8:10> <none>\n" +
                        "<8:10 - 8:10> () -12\n" +
                        "<8:14 - 8:14> <none>\n" +
                        "<8:14 - 8:14> () 1\n" +
                        "<8:10 - 8:14> (-12,1) -12";
        checkExpressionStepPositions(stepIntoPositions, false, StepDepth.INTO);
    }

    @Test
    public void testExpressionStepOver() {
        final String stepOverPositions = "<2:3 - 2:7> <none>\n" +
                        "<2:7 - 2:7> <none>\n" +
                        "<2:7 - 2:7> () 2\n" +
                        "<2:3 - 2:7> (2) 2\n" +
                        "<3:10 - 3:25> <none>\n" +
                        "<3:10 - 3:25> (true,true) true\n" +
                        "<4:5 - 4:13> <none>\n" +
                        "<4:5 - 4:13> (4) 4\n" +
                        "<5:5 - 5:29> <none>\n" +
                        "<5:5 - 5:29> (3) 3\n" +
                        "<6:5 - 6:27> <none>\n" +
                        "<6:5 - 6:27> (-12) -12\n" +
                        "<3:10 - 3:25> <none>\n" +
                        "<3:10 - 3:25> (false,null) false\n" +
                        "<8:10 - 8:14> <none>\n" +
                        "<8:10 - 8:14> (-12,1) -12";
        checkExpressionStepPositions(stepOverPositions, false, StepDepth.INTO, StepDepth.OVER);
    }

    @Test
    public void testExpressionStepOut() {
        final String stepOutPositions = "<2:3 - 2:7> <none>\n" +
                        "<2:7 - 2:7> <none>\n" +
                        "<2:7 - 2:7> () 2\n" +
                        "<2:3 - 2:7> (2) 2\n" +
                        "<3:10 - 3:25> <none>\n" +
                        "<3:10 - 3:15> <none>\n" +
                        "<3:10 - 3:15> (2,0) true\n" +
                        "<3:10 - 3:25> (true,true) true\n";
        checkExpressionStepPositions(stepOutPositions, false, StepDepth.INTO, StepDepth.OVER, StepDepth.OVER,
                        StepDepth.INTO, StepDepth.INTO, StepDepth.OUT);
    }

    @Test
    public void testStatementAndExpressionStepOver() {
        final String stepOverPositions = "<2:3 - 2:7> <none>\n" +
                        "<2:7 - 2:7> <none>\n" +
                        "<2:7 - 2:7> () 2\n" +
                        "<2:3 - 2:7> (2) 2\n" +
                        "<3:10 - 3:25> <none>\n" +
                        "<3:10 - 3:25> (true,true) true\n" +
                        "<4:5 - 4:13> <none>\n" +
                        "<4:5 - 4:13> (4) 4\n" +
                        "<5:5 - 5:29> <none>\n" +
                        "<5:5 - 5:29> (3) 3\n" +
                        "<6:5 - 6:27> <none>\n" +
                        "<6:5 - 6:27> (-12) -12\n" +
                        "<3:10 - 3:25> <none>\n" +
                        "<3:10 - 3:25> (false,null) false\n" +
                        "<8:3 - 8:14> <none>\n" +
                        "<8:10 - 8:14> <none>\n" +
                        "<8:10 - 8:14> (-12,1) -12\n";
        checkExpressionStepPositions(stepOverPositions, true, StepDepth.INTO, StepDepth.OVER);
    }

    @Test
    public void testExceptions() {
        final Source source = slCode("function main() {\n" +
                        "  i = \"0\";\n" +
                        "  return invert(i);\n" +
                        "}\n" +
                        "function invert(n) {\n" +
                        "  x = 10 / n;\n" +
                        "  return x;\n" +
                        "}\n");
        try (DebuggerSession session = startSession()) {
            Breakpoint excBreakpoint = Breakpoint.newExceptionBuilder(true, true).build();
            session.install(excBreakpoint);
            startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                DebugException exception = event.getException();
                Assert.assertNotNull(exception);
                assertTrue(exception.getMessage(), exception.getMessage().startsWith("Type error"));
                SourceSection throwLocation = exception.getThrowLocation();
                assertEquals(6, throwLocation.getStartLine());
                // Repair the 'n' argument and rewind
                event.getTopStackFrame().getScope().getArguments().iterator().next().set(event.getTopStackFrame().eval("function main() {return 2;}"));
                event.prepareUnwindFrame(event.getTopStackFrame());
            });
            expectSuspended((SuspendedEvent event) -> {
                assert event != null;
                // Continue after unwind
            });
            assertEquals("5", expectDone());
        }
    }

    private static void assertNumber(Object real, double expected) {
        if (real instanceof Number) {
            assertEquals(expected, ((Number) real).doubleValue(), 0.1);
        } else {
            fail("Expecting a number " + real);
        }
    }

    public static class Multiply {
        @HostAccess.Export
        public long multiply(long n, Fac fce, long i) {
            return n * fce.fac(i, this);
        }
    }

    @FunctionalInterface
    public interface Fac {
        @HostAccess.Export
        long fac(long n, Multiply multiply);
    }
}
