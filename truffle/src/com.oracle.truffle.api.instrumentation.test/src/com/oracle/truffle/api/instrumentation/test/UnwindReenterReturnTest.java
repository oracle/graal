/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.locks.ReentrantLock;

import org.graalvm.polyglot.PolyglotException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.instrumentation.test.UnwindReenterReturnTest.TestControlFlow.CodeAction;
import com.oracle.truffle.api.nodes.ExplodeLoop;

import org.graalvm.polyglot.Value;

/**
 * Test of {@link EventBinding#throwUnwind()}, followed by reenter or return.
 */
public class UnwindReenterReturnTest extends AbstractInstrumentationTest {

    private static final String CODE1 = "ROOT(DEFINE(a, ROOT(\n" +
                    "  STATEMENT()\n" +
                    ")), CALL(a))\n";
    private static final String CODE1_EXC = "ROOT(DEFINE(a, ROOT(\n" +
                    "  DEFINE(a, ROOT())\n" + // IllegalArgumentException
                    ")), CALL(a))\n";
    private static final String CODE2 = "ROOT(DEFINE(a, ROOT(\n" +
                    "  DEFINE(b, ROOT(\n" +
                    "    STATEMENT()\n" +
                    "  )),\n" +
                    "  CALL(b)\n" +
                    ")), CALL(a))\n";
    private static final String CODE2_EXC = "ROOT(DEFINE(a, ROOT(\n" +
                    "  DEFINE(b, ROOT(\n" +
                    "    STATEMENT(DEFINE(a, ROOT()))\n" + // IllegalArgumentException
                    "  )),\n" +
                    "  CALL(b)\n" +
                    ")), CALL(a))\n";

    TestControlFlow testControlFlow;

    private static String createDeepCode(int n) {
        StringBuilder code = new StringBuilder("ROOT(");
        createRecursiveCalls(code, 'a', n);
        code.append("CALL(a))\n");
        return code.toString();
    }

    private static void createRecursiveCalls(StringBuilder code, char name, int n) {
        for (int i = 0; i < n; i++) {
            // adds a code like: "DEFINE(a, ROOT(CALL(b))),\n"
            code.append("DEFINE(");
            code.append((char) (name + i));
            code.append(", ROOT(CALL(");
            code.append((char) (name + i + 1));
            code.append("))),\n");
        }
        code.append("DEFINE(");
        code.append((char) (name + n));
        code.append(", ROOT(STATEMENT())),\n");
    }

    @Before
    @Override
    public void setup() {
        super.setup();
        testControlFlow = engine.getInstruments().get("testControlFlow").lookup(TestControlFlow.class);
        testControlFlow.submitExecutionListener();
    }

    @After
    @Override
    public void teardown() {
        testControlFlow.actions.clear();
        super.teardown();
    }

    @Test
    public void testReenterSimpleOnEnter() throws Exception {
        List<CodeAction> actionsEnter = new ArrayList<>();
        List<CodeAction> actionsUnwind = new ArrayList<>();

        // Throw unwind from enter and do reenter:
        actionsEnter.add(new CodeAction("CALL(a)", TestControlFlow.ACTION.UNWIND));
        testControlFlow.actions.put(TestControlFlow.WHERE.ENTER, actionsEnter);
        actionsUnwind.add(new CodeAction("CALL(a)", TestControlFlow.ACTION.REENTER));
        testControlFlow.actions.put(TestControlFlow.WHERE.UNWIND, actionsUnwind);
        run(CODE1);
        assertEquals(0, actionsEnter.size());
        assertEquals(0, actionsUnwind.size());
        assertEquals("[CALL(a), CALL(a), STATEMENT()]", testControlFlow.nodesEntered.toString());
        assertEquals("[CALL(a), STATEMENT(), CALL(a)]", testControlFlow.nodesReturned.toString());
        assertEquals("[CALL(a)]", testControlFlow.nodesUnwound.toString());
        assertEquals("[null]", testControlFlow.unwoundInfos.toString());
        assertNotNull(testControlFlow.returnValuesExceptions.get(0));
    }

    @Test
    public void testReenterSimpleOnReturn() throws Exception {
        List<CodeAction> actionsReturn = new ArrayList<>();
        List<CodeAction> actionsUnwind = new ArrayList<>();

        // Throw unwind from return value and do reenter:
        actionsReturn.add(new CodeAction("CALL(a)", TestControlFlow.ACTION.UNWIND, "CA"));
        testControlFlow.actions.put(TestControlFlow.WHERE.RETURN_VALUE, actionsReturn);
        actionsUnwind.add(new CodeAction("CALL(a)", TestControlFlow.ACTION.REENTER));
        testControlFlow.actions.put(TestControlFlow.WHERE.UNWIND, actionsUnwind);
        run(CODE1);

        assertEquals(0, actionsReturn.size());
        assertEquals(0, actionsUnwind.size());
        assertEquals("[CALL(a), STATEMENT(), CALL(a), STATEMENT()]", testControlFlow.nodesEntered.toString());
        assertEquals("[STATEMENT(), CALL(a), STATEMENT(), CALL(a)]", testControlFlow.nodesReturned.toString());
        assertEquals("[Null, Null, Null, Null]", testControlFlow.returnValuesExceptions.toString());
        assertEquals("[CALL(a)]", testControlFlow.nodesUnwound.toString());
        assertEquals("[CA]", testControlFlow.unwoundInfos.toString());
    }

    @Test
    public void testReenterSimpleOnException() throws Exception {
        List<CodeAction> actionsExc = new ArrayList<>();
        List<CodeAction> actionsUnwind = new ArrayList<>();

        // Throw unwind from return exception and do reenter:
        actionsExc.add(new CodeAction("CALL(a)", TestControlFlow.ACTION.UNWIND));
        testControlFlow.actions.put(TestControlFlow.WHERE.RETURN_EXCEPTIONAL, actionsExc);
        actionsUnwind.add(new CodeAction("CALL(a)", TestControlFlow.ACTION.REENTER));
        testControlFlow.actions.put(TestControlFlow.WHERE.UNWIND, actionsUnwind);
        String message = null;
        try {
            run(CODE1_EXC);
            fail();
        } catch (PolyglotException pe) {
            message = pe.getMessage();
            assertTrue(message, message.contains("Identifier redefinition not supported"));
        }

        assertEquals(0, actionsExc.size());
        assertEquals(0, actionsUnwind.size());
        assertEquals("[CALL(a), CALL(a)]", testControlFlow.nodesEntered.toString());
        assertEquals("[CALL(a), CALL(a)]", testControlFlow.nodesReturned.toString());
        assertEquals("[" + message + ", " + message + "]", testControlFlow.returnValuesExceptions.toString());
        assertEquals("[CALL(a)]", testControlFlow.nodesUnwound.toString());
        assertEquals("[null]", testControlFlow.unwoundInfos.toString());
    }

    @Test
    public void testReturnSimpleOnEnter() throws Exception {
        List<CodeAction> actionsEnter = new ArrayList<>();
        List<CodeAction> actionsUnwind = new ArrayList<>();

        // Throw unwind from enter and do early return:
        actionsEnter.add(new CodeAction("CALL(a)", TestControlFlow.ACTION.UNWIND));
        testControlFlow.actions.put(TestControlFlow.WHERE.ENTER, actionsEnter);
        actionsUnwind.add(new CodeAction("CALL(a)", TestControlFlow.ACTION.RETURN, 10));
        testControlFlow.actions.put(TestControlFlow.WHERE.UNWIND, actionsUnwind);
        int ret = context.eval(lines(CODE1)).asInt();
        assertEquals(10, ret);
        assertEquals(0, actionsEnter.size());
        assertEquals(0, actionsUnwind.size());
        assertEquals("[CALL(a)]", testControlFlow.nodesEntered.toString());
        assertEquals("[CALL(a)]", testControlFlow.nodesReturned.toString());
        // assertEquals("[10]", testControlFlow.returnValuesExceptions.toString());
        assertEquals("[com.oracle.truffle.api.instrumentation.UnwindException]", testControlFlow.returnValuesExceptions.toString());
        assertEquals("[CALL(a)]", testControlFlow.nodesUnwound.toString());
        assertEquals("[null]", testControlFlow.unwoundInfos.toString());
    }

    @Test
    public void testReturnSimpleOnReturn() throws Exception {
        List<CodeAction> actionsReturn = new ArrayList<>();
        List<CodeAction> actionsUnwind = new ArrayList<>();

        // Throw unwind from return value and do early return:
        actionsReturn.add(new CodeAction("CALL(a)", TestControlFlow.ACTION.UNWIND, "CA"));
        testControlFlow.actions.put(TestControlFlow.WHERE.RETURN_VALUE, actionsReturn);
        actionsUnwind.add(new CodeAction("CALL(a)", TestControlFlow.ACTION.RETURN, 10));
        testControlFlow.actions.put(TestControlFlow.WHERE.UNWIND, actionsUnwind);
        int ret = context.eval(lines(CODE1)).asInt();
        assertEquals(10, ret);
        assertEquals(0, actionsReturn.size());
        assertEquals(0, actionsUnwind.size());
        assertEquals("[CALL(a), STATEMENT()]", testControlFlow.nodesEntered.toString());
        assertEquals("[STATEMENT(), CALL(a)]", testControlFlow.nodesReturned.toString());
        assertEquals("[Null, Null]", testControlFlow.returnValuesExceptions.toString());
        assertEquals("[CALL(a)]", testControlFlow.nodesUnwound.toString());
        assertEquals("[CA]", testControlFlow.unwoundInfos.toString());
    }

    @Test
    public void testReturnSimpleOnException() throws Exception {
        List<CodeAction> actionsExc = new ArrayList<>();
        List<CodeAction> actionsUnwind = new ArrayList<>();

        // Throw unwind from return exception and do early return:
        actionsExc.add(new CodeAction("CALL(a)", TestControlFlow.ACTION.UNWIND));
        testControlFlow.actions.put(TestControlFlow.WHERE.RETURN_EXCEPTIONAL, actionsExc);
        actionsUnwind.add(new CodeAction("CALL(a)", TestControlFlow.ACTION.RETURN, 10));
        testControlFlow.actions.put(TestControlFlow.WHERE.UNWIND, actionsUnwind);
        int ret = context.eval(lines(CODE1_EXC)).asInt();
        assertEquals(10, ret);

        assertEquals(0, actionsExc.size());
        assertEquals(0, actionsUnwind.size());
        assertEquals("[CALL(a)]", testControlFlow.nodesEntered.toString());
        assertEquals("[CALL(a)]", testControlFlow.nodesReturned.toString());
        assertTrue(testControlFlow.returnValuesExceptions.get(0) instanceof IllegalArgumentException);
        assertEquals("[CALL(a)]", testControlFlow.nodesUnwound.toString());
        assertEquals("[null]", testControlFlow.unwoundInfos.toString());
    }

    @Test
    public void testUnwindReenterOnEnter() throws Exception {
        List<CodeAction> actionsUnwind = new ArrayList<>();
        List<CodeAction> actionsReenter = new ArrayList<>();
        // Unwind at STATEMENT() and Reenter after pop from CALL(a):
        actionsUnwind.add(new CodeAction("STATEMENT()", TestControlFlow.ACTION.UNWIND, "3uw"));
        actionsReenter.add(new CodeAction("CALL(a)", TestControlFlow.ACTION.REENTER));
        testControlFlow.actions.put(TestControlFlow.WHERE.ENTER, actionsUnwind);
        testControlFlow.actions.put(TestControlFlow.WHERE.UNWIND, actionsReenter);
        run(CODE2);

        assertEquals(0, actionsUnwind.size());
        assertEquals(0, actionsReenter.size());
        assertEquals("[CALL(a), CALL(b), STATEMENT(), CALL(a), CALL(b), STATEMENT()]", testControlFlow.nodesEntered.toString());
        assertEquals("[STATEMENT(), CALL(b), CALL(a), STATEMENT(), CALL(b), CALL(a)]", testControlFlow.nodesReturned.toString());
        String uw = "com.oracle.truffle.api.instrumentation.UnwindException";
        assertEquals("[" + uw + ", " + uw + ", " + uw + ", Null, Null, Null]", testControlFlow.returnValuesExceptions.toString());
        assertEquals("[STATEMENT(), CALL(b), CALL(a)]", testControlFlow.nodesUnwound.toString());
        assertEquals("[3uw, 3uw, 3uw]", testControlFlow.unwoundInfos.toString());
    }

    @Test
    public void testUnwindReenterOnReturn() throws Exception {
        List<CodeAction> actionsUnwind = new ArrayList<>();
        List<CodeAction> actionsReenter = new ArrayList<>();
        // Unwind at STATEMENT() and Reenter after pop from CALL(a):
        actionsUnwind.add(new CodeAction("STATEMENT()", TestControlFlow.ACTION.UNWIND, "3uw"));
        actionsReenter.add(new CodeAction("CALL(a)", TestControlFlow.ACTION.REENTER));
        testControlFlow.actions.put(TestControlFlow.WHERE.RETURN_VALUE, actionsUnwind);
        testControlFlow.actions.put(TestControlFlow.WHERE.UNWIND, actionsReenter);
        run(CODE2);

        assertEquals(0, actionsUnwind.size());
        assertEquals(0, actionsReenter.size());
        assertEquals("[CALL(a), CALL(b), STATEMENT(), CALL(a), CALL(b), STATEMENT()]", testControlFlow.nodesEntered.toString());
        assertEquals("[STATEMENT(), CALL(b), CALL(a), STATEMENT(), CALL(b), CALL(a)]", testControlFlow.nodesReturned.toString());
        String uw = "com.oracle.truffle.api.instrumentation.UnwindException";
        assertEquals("[Null, " + uw + ", " + uw + ", Null, Null, Null]", testControlFlow.returnValuesExceptions.toString());
        assertEquals("[STATEMENT(), CALL(b), CALL(a)]", testControlFlow.nodesUnwound.toString());
        assertEquals("[3uw, 3uw, 3uw]", testControlFlow.unwoundInfos.toString());
    }

    @Test
    public void testUnwindReenterOnException() throws Exception {
        List<CodeAction> actionsUnwind = new ArrayList<>();
        List<CodeAction> actionsReenter = new ArrayList<>();
        // Unwind at DEFINE() and Reenter after pop from CALL(a):
        actionsUnwind.add(new CodeAction("STATEMENT(DEFINE(a, ROOT()))", TestControlFlow.ACTION.UNWIND, "3uw"));
        actionsReenter.add(new CodeAction("CALL(a)", TestControlFlow.ACTION.REENTER));
        testControlFlow.actions.put(TestControlFlow.WHERE.RETURN_EXCEPTIONAL, actionsUnwind);
        testControlFlow.actions.put(TestControlFlow.WHERE.UNWIND, actionsReenter);
        String message = null;
        try {
            run(CODE2_EXC);
            fail();
        } catch (PolyglotException pe) {
            message = pe.getMessage();
            assertTrue(message, message.contains("Identifier redefinition not supported"));
        }

        assertEquals(0, actionsUnwind.size());
        assertEquals(0, actionsReenter.size());
        assertEquals("[CALL(a), CALL(b), STATEMENT(DEFINE(a, ROOT())), CALL(a), CALL(b), STATEMENT(DEFINE(a, ROOT()))]", testControlFlow.nodesEntered.toString());
        assertEquals("[STATEMENT(DEFINE(a, ROOT())), CALL(b), CALL(a), STATEMENT(DEFINE(a, ROOT())), CALL(b), CALL(a)]", testControlFlow.nodesReturned.toString());
        String uw = "com.oracle.truffle.api.instrumentation.UnwindException";
        assertEquals("[" + message + ", " + uw + ", " + uw + ", " + message + ", " + message + ", " + message + "]", testControlFlow.returnValuesExceptions.toString());
        assertEquals("[STATEMENT(DEFINE(a, ROOT())), CALL(b), CALL(a)]", testControlFlow.nodesUnwound.toString());
        assertEquals("[3uw, 3uw, 3uw]", testControlFlow.unwoundInfos.toString());
    }

    @Test
    public void testUnwindReturnOnEnter() throws Exception {
        List<CodeAction> actionsUnwind = new ArrayList<>();
        List<CodeAction> actionsReenter = new ArrayList<>();
        // Unwind at STATEMENT() and Return after pop from CALL(a):
        actionsUnwind.add(new CodeAction("STATEMENT()", TestControlFlow.ACTION.UNWIND, "3uw"));
        actionsReenter.add(new CodeAction("CALL(a)", TestControlFlow.ACTION.RETURN, 42));
        testControlFlow.actions.put(TestControlFlow.WHERE.ENTER, actionsUnwind);
        testControlFlow.actions.put(TestControlFlow.WHERE.UNWIND, actionsReenter);
        int ret = context.eval(lines(CODE2)).asInt();
        assertEquals(42, ret);

        assertEquals(0, actionsUnwind.size());
        assertEquals(0, actionsReenter.size());
        assertEquals("[CALL(a), CALL(b), STATEMENT()]", testControlFlow.nodesEntered.toString());
        assertEquals("[STATEMENT(), CALL(b), CALL(a)]", testControlFlow.nodesReturned.toString());
        String uw = "com.oracle.truffle.api.instrumentation.UnwindException";
        assertEquals("[" + uw + ", " + uw + ", " + uw + "]", testControlFlow.returnValuesExceptions.toString());
        assertEquals("[STATEMENT(), CALL(b), CALL(a)]", testControlFlow.nodesUnwound.toString());
        assertEquals("[3uw, 3uw, 3uw]", testControlFlow.unwoundInfos.toString());
    }

    @Test
    public void testUnwindReturnOnReturn() throws Exception {
        List<CodeAction> actionsUnwind = new ArrayList<>();
        List<CodeAction> actionsReenter = new ArrayList<>();
        // Unwind at STATEMENT() and Return after pop from CALL(a):
        actionsUnwind.add(new CodeAction("STATEMENT()", TestControlFlow.ACTION.UNWIND, "3uw"));
        actionsReenter.add(new CodeAction("CALL(a)", TestControlFlow.ACTION.RETURN, 42));
        testControlFlow.actions.put(TestControlFlow.WHERE.RETURN_VALUE, actionsUnwind);
        testControlFlow.actions.put(TestControlFlow.WHERE.UNWIND, actionsReenter);
        int ret = context.eval(lines(CODE2)).asInt();
        assertEquals(42, ret);

        assertEquals(0, actionsUnwind.size());
        assertEquals(0, actionsReenter.size());
        assertEquals("[CALL(a), CALL(b), STATEMENT()]", testControlFlow.nodesEntered.toString());
        assertEquals("[STATEMENT(), CALL(b), CALL(a)]", testControlFlow.nodesReturned.toString());
        String uw = "com.oracle.truffle.api.instrumentation.UnwindException";
        assertEquals("[Null, " + uw + ", " + uw + "]", testControlFlow.returnValuesExceptions.toString());
        assertEquals("[STATEMENT(), CALL(b), CALL(a)]", testControlFlow.nodesUnwound.toString());
        assertEquals("[3uw, 3uw, 3uw]", testControlFlow.unwoundInfos.toString());
    }

    @Test
    public void testUnwindReturnOnException() throws Exception {
        List<CodeAction> actionsUnwind = new ArrayList<>();
        List<CodeAction> actionsReenter = new ArrayList<>();
        // Unwind at DEFINE() and Return after pop from CALL(a):
        actionsUnwind.add(new CodeAction("STATEMENT(DEFINE(a, ROOT()))", TestControlFlow.ACTION.UNWIND, "3uw"));
        actionsReenter.add(new CodeAction("CALL(a)", TestControlFlow.ACTION.RETURN, 42));
        testControlFlow.actions.put(TestControlFlow.WHERE.RETURN_EXCEPTIONAL, actionsUnwind);
        testControlFlow.actions.put(TestControlFlow.WHERE.UNWIND, actionsReenter);
        int ret = context.eval(lines(CODE2_EXC)).asInt();
        assertEquals(42, ret);

        String message = "java.lang.IllegalArgumentException: Identifier redefinition not supported.";

        assertEquals(0, actionsUnwind.size());
        assertEquals(0, actionsReenter.size());
        assertEquals("[CALL(a), CALL(b), STATEMENT(DEFINE(a, ROOT()))]", testControlFlow.nodesEntered.toString());
        assertEquals("[STATEMENT(DEFINE(a, ROOT())), CALL(b), CALL(a)]", testControlFlow.nodesReturned.toString());
        String uw = "com.oracle.truffle.api.instrumentation.UnwindException";
        assertEquals("[" + message + ", " + uw + ", " + uw + "]", testControlFlow.returnValuesExceptions.toString());
        assertEquals("[STATEMENT(DEFINE(a, ROOT())), CALL(b), CALL(a)]", testControlFlow.nodesUnwound.toString());
        assertEquals("[3uw, 3uw, 3uw]", testControlFlow.unwoundInfos.toString());
    }

    @Test
    public void testUnwindRepeated() throws Exception {
        UnwindRepeated unwindRepeated = engine.getInstruments().get("testUnwindRepeated").lookup(UnwindRepeated.class);
        int repeats = 1000;
        unwindRepeated.submit("STATEMENT()", "CALL(a)", repeats);
        int ret = context.eval(lines(CODE2)).asInt();
        assertEquals(4242, ret);

        assertEquals(3 * repeats + 3, testControlFlow.nodesEntered.size());
        for (int i = 0; i <= repeats; i++) {
            assertEquals("CALL(a)", testControlFlow.nodesEntered.get(3 * i));
            assertEquals("CALL(b)", testControlFlow.nodesEntered.get(3 * i + 1));
            assertEquals("STATEMENT()", testControlFlow.nodesEntered.get(3 * i + 2));
        }
        assertEquals(3 * repeats + 3, testControlFlow.nodesReturned.size());
        for (int i = 0; i <= repeats; i++) {
            assertEquals("STATEMENT()", testControlFlow.nodesReturned.get(3 * i));
            assertEquals("CALL(b)", testControlFlow.nodesReturned.get(3 * i + 1));
            assertEquals("CALL(a)", testControlFlow.nodesReturned.get(3 * i + 2));
        }
    }

    @Test
    public void testMultiUnwind() throws Exception {
        UnwindMultiple unwindMultiple = engine.getInstruments().get("testUnwindMultiple").lookup(UnwindMultiple.class);
        // Unwind on enter: g -> a, e -> d (up to b due to second unwind below), l -> i
        unwindMultiple.submit(true, "CALL(g)", "CALL(a)", "CALL(e)", "CALL(d)", "CALL(l)", "CALL(i)");
        // Unwind on return: g -> c (runs in parralel with g -> a above,
        // e -> c (runs in parralel with e -> d (up to b)) above
        // k -> i (runs right after early return from k below
        unwindMultiple.submit(false, "CALL(g)", "CALL(c)", "CALL(e)", "CALL(c)", "CALL(k)", "CALL(i)");
        List<CodeAction> actionsUnwindE = new ArrayList<>();
        List<CodeAction> actionsUnwindR = new ArrayList<>();
        List<CodeAction> actionsOnUnwind = new ArrayList<>();
        actionsUnwindE.add(new CodeAction("CALL(e)", TestControlFlow.ACTION.UNWIND, "e->b"));
        actionsOnUnwind.add(new CodeAction("CALL(b)", TestControlFlow.ACTION.REENTER));
        actionsUnwindR.add(new CodeAction("CALL(k)", TestControlFlow.ACTION.UNWIND, "k->j"));
        actionsOnUnwind.add(new CodeAction("CALL(j)", TestControlFlow.ACTION.REENTER));
        actionsUnwindE.add(new CodeAction("STATEMENT()", TestControlFlow.ACTION.UNWIND, "s->k"));
        actionsOnUnwind.add(new CodeAction("CALL(k)", TestControlFlow.ACTION.RETURN, 42));
        testControlFlow.actions.put(TestControlFlow.WHERE.ENTER, actionsUnwindE);
        testControlFlow.actions.put(TestControlFlow.WHERE.RETURN_EXCEPTIONAL, actionsUnwindR);
        testControlFlow.actions.put(TestControlFlow.WHERE.UNWIND, actionsOnUnwind);

        String code = createDeepCode(12);
        int ret = context.eval(lines(code)).asInt();
        assertEquals(42, ret);
        assertEquals(0, actionsUnwindE.size());
        assertEquals(0, actionsUnwindR.size());
        assertEquals(0, actionsOnUnwind.size());
        assertEquals("[CALL(a), CALL(b), CALL(c), CALL(d), CALL(e), " + // unwind to b
                        "CALL(b), CALL(c), CALL(d), CALL(e), CALL(f), CALL(g), " + // unwind to a
                        "CALL(a), CALL(b), CALL(c), CALL(d), CALL(e), CALL(f), CALL(g), CALL(h), CALL(i), CALL(j), CALL(k), CALL(l), " +
                        // unwound to i
                        "CALL(i), CALL(j), CALL(k), CALL(l), CALL(m), STATEMENT()]",
                        testControlFlow.nodesEntered.toString());
        assertEquals("[CALL(e), CALL(d), CALL(c), CALL(b), " + // unwind e -> b
                        "CALL(g), CALL(f), CALL(e), CALL(d), CALL(c), CALL(b), CALL(a), " +
                        // unwound g -> a
                        "CALL(l), CALL(k), CALL(j), CALL(i), " + // unwind l -> i
                        "STATEMENT(), CALL(m), CALL(l), CALL(k), CALL(j), CALL(i), CALL(h), CALL(g), CALL(f), CALL(e), CALL(d), CALL(c), CALL(b), CALL(a)]",
                        testControlFlow.nodesReturned.toString());
        assertEquals(testControlFlow.nodesEntered.size(), testControlFlow.nodesReturned.size());
        String uw = "com.oracle.truffle.api.instrumentation.UnwindException";
        StringBuilder retVals = new StringBuilder("[");
        for (int i = 0; i < 19; i++) {
            retVals.append(uw);
            retVals.append(", ");
        }
        for (int i = 0; i < 10; i++) {
            retVals.append("42, ");
        }
        retVals.replace(retVals.length() - 2, retVals.length(), "]");
        assertEquals(retVals.toString(), testControlFlow.returnValuesExceptions.toString());
        assertEquals(testControlFlow.nodesEntered.size(), testControlFlow.returnValuesExceptions.size());
        assertEquals("[CALL(e), CALL(d), CALL(c), CALL(b), CALL(k), CALL(j), STATEMENT(), CALL(m), CALL(l), CALL(k)]", testControlFlow.nodesUnwound.toString());
        assertEquals("[e->b, e->b, e->b, e->b, k->j, k->j, s->k, s->k, s->k, s->k]", testControlFlow.unwoundInfos.toString());
    }

    @Test
    public void testParallelUnwindOneForAll() throws Exception {
        cleanup();
        context = newContext();
        // Throw a single UnwindException in multiple threads and test that it fails.
        UnwindParallel unwindParallel = engine.getInstruments().get("testUnwindParallel").lookup(UnwindParallel.class);
        int n = 5;
        StringBuilder codeBuilder = new StringBuilder("ROOT(");
        createRecursiveCalls(codeBuilder, 'a', n);
        createRecursiveCalls(codeBuilder, 'g', n);
        createRecursiveCalls(codeBuilder, 'm', n);
        codeBuilder.append("SPAWN(a),\n");
        codeBuilder.append("SPAWN(g),\n");
        codeBuilder.append("SPAWN(m),\n");
        codeBuilder.append("JOIN())\n");
        String code = codeBuilder.toString();

        final CompletableFuture<Throwable> failure = new CompletableFuture<>();
        unwindParallel.submitOneForAll(failure);
        try {
            run(code);
        } catch (Exception ex) {
        }
        context.close(true);
        context = null;
        String message = failure.get().getMessage();
        assertTrue(message, message.contains("A single instance of UnwindException thrown in two threads"));
    }

    @Test
    public void testParallelUnwindConcurrent() throws Exception {
        doParallelUnwind(true);
    }

    @Test
    public void testParallelUnwindSequential() throws Exception {
        doParallelUnwind(false);
    }

    @Test
    public void testUnexpectedTypeAndUnwind() throws Exception {
        UnexpectedTypeAndUnwind unwindThrows = engine.getInstruments().get("testUnexpectedTypeAndUnwind").lookup(UnexpectedTypeAndUnwind.class);
        int expectedResult = 43;
        unwindThrows.submit(expectedResult);
        Value value = context.eval(lines("EXPRESSION(UNEXPECTED_RESULT(42))"));

        assertTrue(value.isNumber());
        assertEquals(expectedResult, value.asInt());
        assertEquals("[UNEXPECTED_RESULT(42), UnexpectedResultException(42)]", testControlFlow.nodesEntered.toString());
        assertEquals("[UnexpectedResultException(42), UNEXPECTED_RESULT(42)]", testControlFlow.nodesReturned.toString());
        assertEquals("[42, 42]", testControlFlow.returnValuesExceptions.toString());
    }

    private void doParallelUnwind(boolean concurrent) throws Exception {
        cleanup();
        context = newContext();
        UnwindParallel unwindParallel = engine.getInstruments().get("testUnwindParallel").lookup(UnwindParallel.class);
        int n = 5;
        StringBuilder codeBuilder = new StringBuilder("ROOT(");
        createRecursiveCalls(codeBuilder, 'a', n);
        createRecursiveCalls(codeBuilder, 'g', n);
        createRecursiveCalls(codeBuilder, 'm', n);
        codeBuilder.append("SPAWN(a),\n");
        codeBuilder.append("SPAWN(g),\n");
        codeBuilder.append("SPAWN(m),\n");
        codeBuilder.append("JOIN())\n");
        String code = codeBuilder.toString();

        if (concurrent) {
            unwindParallel.submitConcurrent();
        } else {
            unwindParallel.submitSequential();
        }
        run(code);

        assertEquals(6 * 3, testControlFlow.nodesEntered.size());
        assertEquals(6 * 3, testControlFlow.nodesReturned.size());
        assertEquals(6 * 3, testControlFlow.returnValuesExceptions.size());
        String uw = "com.oracle.truffle.api.instrumentation.UnwindException";
        int numUnwinds = 0;
        int numOnes = 0;
        for (Object rv : testControlFlow.returnValuesExceptions) {
            if (rv.getClass().getName().equals(uw)) {
                numUnwinds++;
            } else if (Integer.valueOf(1).equals(rv)) {
                numOnes++;
            } else {
                throw new AssertionError(rv);
            }
        }
        assertEquals(3, numUnwinds);
        assertEquals(5 * 3, numOnes);
        context.close();
        context = null;
    }

    @Registration(id = "testControlFlow", services = TestControlFlow.class)
    public static class TestControlFlow extends TruffleInstrument {

        enum WHERE {
            ENTER,
            RETURN_VALUE,
            RETURN_EXCEPTIONAL,
            UNWIND
        }

        enum ACTION {
            UNWIND,
            RETURN,
            REENTER
        }

        static class CodeAction {

            final String code;
            final ACTION action;
            final Object value;

            CodeAction(String code, ACTION action) {
                this.code = code;
                this.action = action;
                this.value = (action == ACTION.RETURN) ? Boolean.TRUE : null;
            }

            CodeAction(String code, Object returnValue) {
                this.code = code;
                this.action = ACTION.RETURN;
                if (returnValue == null) {
                    this.value = Boolean.TRUE;
                } else {
                    this.value = returnValue;
                }
            }

            CodeAction(String code, ACTION action, Object info) {
                this.code = code;
                this.action = action;
                if (action == ACTION.RETURN && info == null) {
                    this.value = Boolean.TRUE;
                } else {
                    this.value = info;
                }
            }
        }

        final Map<WHERE, List<CodeAction>> actions = Collections.synchronizedMap(new HashMap<>());
        final List<String> nodesEntered = Collections.synchronizedList(new ArrayList<>());
        final List<String> nodesReturned = Collections.synchronizedList(new ArrayList<>());
        final List<String> nodesUnwound = Collections.synchronizedList(new ArrayList<>());
        final List<String> unwoundInfos = Collections.synchronizedList(new ArrayList<>());
        final List<Object> returnValuesExceptions = Collections.synchronizedList(new ArrayList<>());
        private Env env;
        private EventBinding<? extends ExecutionEventListener> bindingExec;

        @Override
        @SuppressWarnings("hiding")
        protected void onCreate(Env env) {
            this.env = env;
            env.registerService(this);
        }

        @Override
        @SuppressWarnings("hiding")
        protected void onDispose(Env env) {
            if (bindingExec != null) {
                bindingExec.dispose();
                bindingExec = null;
            }
            super.onDispose(env);
        }

        void submitExecutionListener() {
            bindingExec = env.getInstrumenter().attachExecutionEventListener(
                            SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class, StandardTags.CallTag.class).build(),
                            new ExecutionEventListener() {

                                @Override
                                public void onEnter(EventContext context, VirtualFrame frame) {
                                    onEnter(context);
                                }

                                @TruffleBoundary
                                private void onEnter(EventContext context) {
                                    String code = context.getInstrumentedSourceSection().getCharacters().toString();
                                    nodesEntered.add(code);
                                    doAction(actions.get(WHERE.ENTER), code, context);
                                }

                                @Override
                                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                                    onReturnValue(context, result);
                                }

                                @TruffleBoundary
                                private void onReturnValue(EventContext context, Object result) {
                                    String code = context.getInstrumentedSourceSection().getCharacters().toString();
                                    nodesReturned.add(code);
                                    returnValuesExceptions.add(result);
                                    doAction(actions.get(WHERE.RETURN_VALUE), code, context);
                                }

                                @Override
                                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                                    onReturnExceptional(context, exception);
                                }

                                @TruffleBoundary
                                private void onReturnExceptional(EventContext context, Throwable exception) {
                                    String code = context.getInstrumentedSourceSection().getCharacters().toString();
                                    nodesReturned.add(code);
                                    returnValuesExceptions.add(exception);
                                    doAction(actions.get(WHERE.RETURN_EXCEPTIONAL), code, context);
                                }

                                @Override
                                public Object onUnwind(EventContext context, VirtualFrame frame, Object info) {
                                    return onUnwind(context, info);
                                }

                                @TruffleBoundary
                                private Object onUnwind(EventContext context, Object info) {
                                    String code = context.getInstrumentedSourceSection().getCharacters().toString();
                                    nodesUnwound.add(code);
                                    unwoundInfos.add(Objects.toString(info));
                                    return TestControlFlow.doAction(actions.get(WHERE.UNWIND), code, context);
                                }

                                private void doAction(List<CodeAction> codeActions, String code, EventContext context) {
                                    TestControlFlow.doAction(codeActions, code, context);
                                }
                            });
        }

        private static Object doAction(List<CodeAction> codeActions, String code, EventContext context) {
            if (codeActions != null && !codeActions.isEmpty()) {
                if (code.equals(codeActions.get(0).code)) {
                    CodeAction action = codeActions.remove(0);
                    switch (action.action) {
                        case UNWIND:
                            throw context.createUnwind(action.value);
                        case REENTER:
                            return ProbeNode.UNWIND_ACTION_REENTER;
                        case RETURN:
                            return action.value;
                    }
                }
            }
            return null;
        }
    }

    @Registration(id = "testUnwindRepeated", services = UnwindRepeated.class)
    public static class UnwindRepeated extends TruffleInstrument {

        private Env env;
        private EventBinding<? extends ExecutionEventListener> bindingExec;

        @Override
        @SuppressWarnings("hiding")
        protected void onCreate(Env env) {
            this.env = env;
            env.registerService(this);
        }

        @Override
        @SuppressWarnings("hiding")
        protected void onDispose(Env env) {
            if (bindingExec != null) {
                bindingExec.dispose();
                bindingExec = null;
            }
            super.onDispose(env);
        }

        void submit(String fromCode, String toCode, int repeats) {
            bindingExec = env.getInstrumenter().attachExecutionEventListener(
                            SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class, StandardTags.CallTag.class).build(),
                            new ExecutionEventListener() {

                                @CompilationFinal ThreadDeath unwind;
                                private int numRepeat = 0;

                                @Override
                                public void onEnter(EventContext context, VirtualFrame frame) {
                                    String code = context.getInstrumentedSourceSection().getCharacters().toString();
                                    if (fromCode.equals(code)) {
                                        if (unwind == null) {
                                            CompilerDirectives.transferToInterpreterAndInvalidate();
                                            if (unwind == null) {
                                                unwind = context.createUnwind(toCode);
                                            }
                                        }
                                        throw unwind;
                                    }
                                }

                                @Override
                                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                                }

                                @Override
                                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                                }

                                @Override
                                public Object onUnwind(EventContext context, VirtualFrame frame, Object info) {
                                    String code = context.getInstrumentedSourceSection().getCharacters().toString();
                                    if (code.equals(info)) {
                                        if (numRepeat++ < repeats) {
                                            return ProbeNode.UNWIND_ACTION_REENTER;
                                        } else {
                                            return 4242;
                                        }
                                    } else {
                                        return null;
                                    }
                                }

                            });
        }
    }

    @Registration(id = "testUnwindMultiple", services = UnwindMultiple.class)
    public static class UnwindMultiple extends TruffleInstrument {

        private Env env;
        private EventBinding<? extends ExecutionEventListener> bindingExec;

        @Override
        @SuppressWarnings("hiding")
        protected void onCreate(Env env) {
            this.env = env;
            env.registerService(this);
        }

        @Override
        @SuppressWarnings("hiding")
        protected void onDispose(Env env) {
            if (bindingExec != null) {
                bindingExec.dispose();
                bindingExec = null;
            }
            super.onDispose(env);
        }

        void submit(final boolean enter, final String... fromToCode) {
            bindingExec = env.getInstrumenter().attachExecutionEventListener(
                            SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class, StandardTags.CallTag.class).build(),
                            new ExecutionEventListener() {

                                @CompilationFinal(dimensions = 1) private String[] fromTo = fromToCode;
                                private final boolean[] unwound = new boolean[fromToCode.length / 2];

                                @Override
                                @ExplodeLoop
                                public void onEnter(EventContext context, VirtualFrame frame) {
                                    if (enter) {
                                        doUnwind(context);
                                    }
                                }

                                @Override
                                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                                    if (!enter) {
                                        doUnwind(context);
                                    }
                                }

                                @Override
                                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                                    if (!enter) {
                                        doUnwind(context);
                                    }
                                }

                                private void doUnwind(EventContext context) {
                                    String code = context.getInstrumentedSourceSection().getCharacters().toString();
                                    int i = 0;
                                    while (i < fromTo.length && !fromTo[i].equals(code)) {
                                        i += 2;
                                    }
                                    if (i < fromTo.length) {
                                        CompilerDirectives.transferToInterpreter();
                                        if (!unwound[i / 2]) {
                                            unwound[i / 2] = true;
                                            throw context.createUnwind(fromTo[i + 1]);
                                        }
                                    }
                                }

                                @Override
                                public Object onUnwind(EventContext context, VirtualFrame frame, Object info) {
                                    String code = context.getInstrumentedSourceSection().getCharacters().toString();
                                    if (code.equals(info)) {
                                        return ProbeNode.UNWIND_ACTION_REENTER;
                                    } else {
                                        return null;
                                    }
                                }

                            });
        }
    }

    @Registration(id = "testUnwindParallel", services = UnwindParallel.class)
    public static class UnwindParallel extends TruffleInstrument {

        private Env env;
        private EventBinding<? extends ExecutionEventListener> bindingExec;

        @Override
        @SuppressWarnings("hiding")
        protected void onCreate(Env env) {
            this.env = env;
            env.registerService(this);
        }

        @Override
        @SuppressWarnings("hiding")
        protected void onDispose(Env env) {
            if (bindingExec != null) {
                bindingExec.dispose();
                bindingExec = null;
            }
            super.onDispose(env);
        }

        void submitOneForAll(CompletableFuture<Throwable> exception) {
            final ThreadDeath[] unwindPtr = new ThreadDeath[1];
            CyclicBarrier enterBarrier = new CyclicBarrier(3);
            CyclicBarrier unwindBarrier = new CyclicBarrier(3);
            bindingExec = env.getInstrumenter().attachExecutionEventListener(
                            SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class, StandardTags.CallTag.class).build(),
                            new ExecutionEventListener() {

                                private final ThreadLocal<Boolean> failed = new ThreadLocal<>();

                                @Override
                                public void onEnter(EventContext context, VirtualFrame frame) {
                                    onEnter(context);
                                }

                                @TruffleBoundary
                                private void onEnter(EventContext context) {
                                    if ("STATEMENT()".equals(context.getInstrumentedSourceSection().getCharacters().toString())) {
                                        synchronized (unwindPtr) {
                                            if (unwindPtr[0] == null) {
                                                unwindPtr[0] = context.createUnwind("one");
                                            }
                                        }
                                        try {
                                            enterBarrier.await();
                                        } catch (InterruptedException | BrokenBarrierException ex) {
                                            throw new AssertionError(ex.getLocalizedMessage(), ex);
                                        }
                                        throw unwindPtr[0];
                                    }
                                }

                                @Override
                                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                                }

                                @Override
                                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable ex) {
                                    onReturnExceptional(ex);
                                }

                                @TruffleBoundary
                                private void onReturnExceptional(Throwable ex) {
                                    if (!ex.getClass().getName().endsWith("UnwindException")) {
                                        exception.complete(ex);
                                        Boolean hasFailed = failed.get();
                                        if (hasFailed == null || !hasFailed) {
                                            failed.set(Boolean.TRUE);
                                            gatherUnwindThreads();
                                        }
                                    }
                                }

                                @Override
                                public Object onUnwind(EventContext context, VirtualFrame frame, Object info) {
                                    gatherUnwindThreads();
                                    return 1;
                                }

                                @TruffleBoundary
                                private void gatherUnwindThreads() {
                                    try {
                                        unwindBarrier.await();
                                    } catch (InterruptedException | BrokenBarrierException ex) {
                                        throw new AssertionError(ex.getLocalizedMessage(), ex);
                                    }
                                }
                            });
        }

        void submitConcurrent() {
            CyclicBarrier unwindBarrier = new CyclicBarrier(3);
            bindingExec = env.getInstrumenter().attachExecutionEventListener(
                            SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class, StandardTags.CallTag.class).build(),
                            new ExecutionEventListener() {
                                @Override
                                public void onEnter(EventContext context, VirtualFrame frame) {
                                    onEnter(context);
                                }

                                @TruffleBoundary
                                private void onEnter(EventContext context) {
                                    if ("STATEMENT()".equals(context.getInstrumentedSourceSection().getCharacters().toString())) {
                                        CompilerDirectives.transferToInterpreter();
                                        throw context.createUnwind("Unwind on " + Thread.currentThread().getName());
                                    }
                                }

                                @Override
                                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                                }

                                @Override
                                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable ex) {
                                }

                                @Override
                                public Object onUnwind(EventContext context, VirtualFrame frame, Object info) {
                                    return onUnwind();
                                }

                                @TruffleBoundary
                                private Object onUnwind() {
                                    try {
                                        unwindBarrier.await();
                                    } catch (InterruptedException | BrokenBarrierException ex) {
                                        throw new AssertionError(ex.getLocalizedMessage(), ex);
                                    }
                                    return 1;
                                }
                            });
        }

        void submitSequential() {
            final ThreadDeath[] unwindPtr = new ThreadDeath[1];
            ReentrantLock lock = new ReentrantLock();
            bindingExec = env.getInstrumenter().attachExecutionEventListener(
                            SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class, StandardTags.CallTag.class).build(),
                            new ExecutionEventListener() {

                                boolean unwound = false;

                                @Override
                                public void onEnter(EventContext context, VirtualFrame frame) {
                                    onEnter(context);
                                }

                                @TruffleBoundary
                                private void onEnter(EventContext context) {
                                    if ("STATEMENT()".equals(context.getInstrumentedSourceSection().getCharacters().toString())) {
                                        synchronized (unwindPtr) {
                                            if (unwindPtr[0] == null) {
                                                unwindPtr[0] = context.createUnwind("one");
                                            }
                                        }
                                        lock.lock();
                                        throw unwindPtr[0];
                                    }
                                }

                                @Override
                                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                                    onReturnValue();
                                }

                                @TruffleBoundary
                                private void onReturnValue() {
                                    if (unwound && lock.isHeldByCurrentThread()) {
                                        unwound = false;
                                        lock.unlock();
                                    }
                                }

                                @Override
                                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable ex) {
                                }

                                @Override
                                public Object onUnwind(EventContext context, VirtualFrame frame, Object info) {
                                    unwound = true;
                                    return 1;
                                }
                            });
        }
    }

    @Registration(id = "testUnexpectedTypeAndUnwind", services = UnexpectedTypeAndUnwind.class)
    public static class UnexpectedTypeAndUnwind extends TruffleInstrument {

        private Env env;
        private EventBinding<? extends ExecutionEventListener> bindingExec;

        @Override
        @SuppressWarnings("hiding")
        protected void onCreate(Env env) {
            this.env = env;
            env.registerService(this);
        }

        @Override
        @SuppressWarnings("hiding")
        protected void onDispose(Env env) {
            if (bindingExec != null) {
                bindingExec.dispose();
                bindingExec = null;
            }
            super.onDispose(env);
        }

        void submit(final Object unwindValue) {
            bindingExec = env.getInstrumenter().attachExecutionEventListener(
                            SourceSectionFilter.newBuilder().tagIs(ExpressionTag.class).build(),
                            new ExecutionEventListener() {

                                @Override
                                public void onEnter(EventContext context, VirtualFrame frame) {
                                }

                                @Override
                                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                                    throw context.createUnwind(unwindValue);
                                }

                                @Override
                                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                                    throw new AssertionError("No exception expected. Got: " + exception);
                                }

                                @Override
                                public Object onUnwind(EventContext context, VirtualFrame frame, Object info) {
                                    return info;
                                }
                            });
        }
    }

}
