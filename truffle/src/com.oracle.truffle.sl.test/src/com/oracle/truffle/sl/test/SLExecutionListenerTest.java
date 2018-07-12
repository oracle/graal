/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.management.ExecutionEvent;
import org.graalvm.polyglot.management.ExecutionListener;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SLExecutionListenerTest {

    private Context context;
    private final Deque<ExecutionEvent> events = new ArrayDeque<>();

    private String expectedRootName;

    @Before
    public void setUp() {
        context = Context.create("sl");
    }

    @After
    public void tearDown() {
        assertTrue(events.isEmpty());
        context.close();
        context = null;
    }

    private void add(ExecutionEvent e) {
        events.add(e);
    }

    @Test
    public void testRootsAndStatements() {
        ExecutionListener.newBuilder().onEnter(this::add).onReturn(this::add).//
                        roots(true).statements(true).//
                        collectExceptions(true).collectInputValues(true).collectReturnValue(true).//
                        attach(context.getEngine());

        eval("return 2;");

        enterRoot(rootSourceSection("return 2;"));
        enterStatement("return 2");
        leaveStatement("return 2", null);
        leaveRoot(rootSourceSection("return 2;"), 2);
    }

    @Test
    public void testStatements() {
        ExecutionListener.newBuilder().onEnter(this::add).onReturn(this::add).//
                        statements(true).//
                        collectExceptions(true).collectInputValues(true).collectReturnValue(true).//
                        attach(context.getEngine());

        eval("2 + 3;");
        enterStatement("2 + 3");
        leaveStatement("2 + 3", 5);

        eval("2 + 3; 3 + 6;");
        enterStatement("2 + 3");
        leaveStatement("2 + 3", 5);
        enterStatement("3 + 6");
        leaveStatement("3 + 6", 9);
    }

    @Test
    public void testExpressions() {
        ExecutionListener.newBuilder().onEnter(this::add).onReturn(this::add).//
                        expressions(true).//
                        collectExceptions(true).collectInputValues(true).collectReturnValue(true).//
                        attach(context.getEngine());
        eval("2 + 3;");

        enterStatement("2 + 3");
        enterExpression("2");
        leaveExpression("2", 2);
        enterExpression("3");
        leaveExpression("3", 3);
        leaveStatement("2 + 3", 5, 2, 3);
    }

    @Test
    public void testRoots() {
        ExecutionListener.newBuilder().onEnter(this::add).onReturn(this::add).//
                        roots(true).//
                        collectExceptions(true).collectInputValues(true).collectReturnValue(true).//
                        attach(context.getEngine());

        eval("return 2;");

        enterRoot(rootSourceSection("return 2;"));
        leaveRoot(rootSourceSection("return 2;"), 2);
    }

    @Test
    public void testExpressionsStatementsAndRoots() {
        ExecutionListener.newBuilder().onEnter(this::add).onReturn(this::add).//
                        expressions(true).statements(true).//
                        collectExceptions(true).collectInputValues(true).collectReturnValue(true).//
                        attach(context.getEngine());

        eval("2 + 3;");

        enterStatement("2 + 3");
        enterExpression("2");
        leaveExpression("2", 2);
        enterExpression("3");
        leaveExpression("3", 3);
        leaveStatement("2 + 3", 5, 2, 3);
    }

    @Test
    public void testFactorial() {
        // @formatter:off
        String characters =
                        "fac(n) {" +
                        "  if (n <= 1) {" +
                        "    return 1;" +
                        "  }" +
                        "  return fac(n - 1) * n;" +
                        "}";
        // @formatter:on
        context.eval("sl", "function " + characters);
        Value factorial = context.getBindings("sl").getMember("fac");
        ExecutionListener.newBuilder().onReturn(this::add).onEnter(this::add).//
                        expressions(true).statements(true).roots(true).//
                        collectExceptions(true).collectInputValues(true).collectReturnValue(true).//
                        attach(context.getEngine());
        expectedRootName = "fac";
        assertEquals(0, events.size());
        for (int i = 0; i < 10; i++) {
            testFactorial(characters, factorial);
        }
    }

    private Value eval(String s) {
        expectedRootName = "wrapper";
        context.eval("sl", wrapInFunction(s));
        return context.getBindings("sl").getMember("wrapper").execute();
    }

    private static String wrapInFunction(String s) {
        return "function " + rootSourceSection(s);
    }

    private static String rootSourceSection(String s) {
        return "wrapper() {\n  " + s + " \n}";
    }

    private void testFactorial(String characters, Value factorial) {
        factorial.execute(3);
        enterRoot(characters);
        enterStatement("n <= 1");
        enterExpression("n");
        leaveExpression("n", 3);
        enterExpression("1");
        leaveExpression("1", 1);
        leaveStatement("n <= 1", false, 3, 1);
        enterStatement("return fac(n - 1) * n");
        enterExpression("fac(n - 1) * n");
        enterExpression("fac(n - 1)");
        enterExpression("fac");
        leaveExpression("fac", factorial);
        enterExpression("n - 1");
        enterExpression("n");
        leaveExpression("n", 3);
        enterExpression("1");
        leaveExpression("1", 1);
        leaveExpression("n - 1", 2, 3, 1);

        enterRoot(characters);
        enterStatement("n <= 1");
        enterExpression("n");
        leaveExpression("n", 2);
        enterExpression("1");
        leaveExpression("1", 1);
        leaveStatement("n <= 1", false, 2, 1);
        enterStatement("return fac(n - 1) * n");
        enterExpression("fac(n - 1) * n");
        enterExpression("fac(n - 1)");
        enterExpression("fac");
        leaveExpression("fac", factorial);
        enterExpression("n - 1");
        enterExpression("n");
        leaveExpression("n", 2);
        enterExpression("1");
        leaveExpression("1", 1);
        leaveExpression("n - 1", 1, 2, 1);

        enterRoot(characters);
        enterStatement("n <= 1");
        enterExpression("n");
        leaveExpression("n", 1);
        enterExpression("1");
        leaveExpression("1", 1);
        leaveStatement("n <= 1", true, 1, 1);
        enterStatement("return 1");
        enterExpression("1");
        leaveExpression("1", 1);
        leaveStatement("return 1", null, 1);
        leaveRoot(characters, 1);

        leaveExpression("fac(n - 1)", 1, factorial, 1);
        enterExpression("n");
        leaveExpression("n", 2);
        leaveExpression("fac(n - 1) * n", 2, 1, 2);
        leaveStatement("return fac(n - 1) * n", null, 2);
        leaveRoot(characters, 2);

        leaveExpression("fac(n - 1)", 2, factorial, 2);
        enterExpression("n");
        leaveExpression("n", 3);
        leaveExpression("fac(n - 1) * n", 6, 2, 3);
        leaveStatement("return fac(n - 1) * n", null, 6);
        leaveRoot(characters, 6);

        assertTrue(events.isEmpty());
    }

    private void enterExpression(String characters) {
        ExecutionEvent event = assertEvent(characters, null);
        assertTrue(event.isExpression());
        assertFalse(event.isStatement());
        assertFalse(event.isRoot());
    }

    private void enterStatement(String characters) {
        ExecutionEvent event = assertEvent(characters, null);
        assertTrue(event.isStatement());
        // statements are sometimes expressions
        assertFalse(event.isRoot());
    }

    private void enterRoot(String characters) {
        ExecutionEvent event = assertEvent(characters, null);
        assertTrue(event.isRoot());
        assertFalse(event.isStatement());
        assertFalse(event.isExpression());
    }

    private void leaveExpression(String characters, Object returnValue, Object... inputs) {
        ExecutionEvent event = assertEvent(characters, returnValue, inputs);
        assertTrue(event.isExpression());
        assertFalse(event.isStatement());
        assertFalse(event.isRoot());
    }

    private void leaveStatement(String characters, Object returnValue, Object... inputs) {
        ExecutionEvent event = assertEvent(characters, returnValue, inputs);
        assertTrue(event.isStatement());
        // statements are sometimes expressions
        assertFalse(event.isRoot());
    }

    private void leaveRoot(String characters, Object returnValue, Object... inputs) {
        ExecutionEvent event = assertEvent(characters, returnValue, inputs);
        assertTrue(event.isRoot());
        assertFalse(event.isStatement());
        assertFalse(event.isExpression());
    }

    private ExecutionEvent assertEvent(String characters, Object returnValue, Object... inputs) {
        ExecutionEvent event = events.pop();
        assertEquals(expectedRootName, event.getRootName());
        assertEquals(characters, event.getLocation().getCharacters());
        assertEquals(inputs.length, event.getInputValues().size());
        for (int i = 0; i < inputs.length; i++) {
            assertValue(inputs[i], event.getInputValues().get(i));
        }

        if (returnValue == null) {
            assertNull(event.getReturnValue());
        } else {
            assertValue(returnValue, event.getReturnValue());
        }

        assertNotNull(event.toString());
        return event;
    }

    private static void assertValue(Object expected, Value actual) throws AssertionError {
        if (actual.isNumber()) {
            assertEquals(expected, actual.asInt());
        } else if (actual.isBoolean()) {
            assertEquals(expected, actual.asBoolean());
        } else if (actual.canExecute()) {
            assertEquals(((Value) expected).getSourceLocation(), actual.getSourceLocation());
        } else {
            throw new AssertionError(expected.toString());
        }
    }

}
