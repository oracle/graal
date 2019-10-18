/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess.Export;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.PolyglotException.StackFrame;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class SLExceptionTest {

    private Context ctx;

    @Before
    public void setUp() {
        this.ctx = Context.create("sl");
    }

    @After
    public void tearDown() {
        this.ctx.close();
    }

    @Test
    public void testExceptions() {
        assertException(true, "function main() { x = 1 / (1 == 1); }", "main");
        assertException(true, "function foo() { return 1 / \"foo\"; } function main() { foo(); }", "foo", "main");
        assertException(true, "function foo() { bar(); } function bar() { return 1 / \"foo\"; } function main() { foo(); }", "bar", "foo", "main");
        assertException(true, "function foo() { bar1(); bar2(); } function bar1() { return 1; } function bar2() { return \"foo\" / 1; } function main() { foo(); }", "bar2", "foo", "main");
    }

    @Test
    public void testNonMain() {
        assertException(false, "function foo(z) { x = 1 / (1==1); } function main() { return foo; }", "foo");
    }

    @Test
    public void testThroughProxy() {
        assertException(false, "function bar() { x = 1 / (1==1); } function foo(z) { z(bar); } function main() { return foo; }", "bar", null, null, "foo");
    }

    @Test
    public void testHostException() {
        assertHostException("function foo(z) { z(1); } function main() { return foo; }", null, "foo");
    }

    private void assertException(boolean failImmediately, String source, String... expectedFrames) {
        boolean initialExecute = true;
        try {
            Value value = ctx.eval("sl", source);
            initialExecute = false;
            if (failImmediately) {
                Assert.fail("Should not reach here.");
            }
            ProxyExecutable proxy = (args) -> args[0].execute();
            value.execute(proxy);
            Assert.fail("Should not reach here.");
        } catch (PolyglotException e) {
            Assert.assertEquals(failImmediately, initialExecute);
            assertFrames(failImmediately, e, expectedFrames);
        }
    }

    private static void assertFrames(boolean isEval, PolyglotException e, String... expectedFrames) {
        int i = 0;
        boolean firstHostFrame = false;
        // Expected exception
        for (StackFrame frame : e.getPolyglotStackTrace()) {
            if (i < expectedFrames.length && expectedFrames[i] != null) {
                Assert.assertTrue(frame.isGuestFrame());
                Assert.assertEquals("sl", frame.getLanguage().getId());
                Assert.assertEquals(expectedFrames[i], frame.getRootName());
                Assert.assertTrue(frame.getSourceLocation() != null);
                firstHostFrame = true;
            } else {
                Assert.assertTrue(frame.isHostFrame());
                if (firstHostFrame) {
                    Assert.assertEquals(isEval ? "org.graalvm.polyglot.Context.eval" : "org.graalvm.polyglot.Value.execute", frame.getRootName());
                    firstHostFrame = false;
                }
            }
            i++;
        }
    }

    private void assertHostException(String source, String... expectedFrames) {
        boolean initialExecute = true;
        RuntimeException[] exception = new RuntimeException[1];
        try {
            Value value = ctx.eval("sl", source);
            initialExecute = false;
            ProxyExecutable proxy = (args) -> {
                throw exception[0] = new RuntimeException();
            };
            value.execute(proxy);
            Assert.fail("Should not reach here.");
        } catch (PolyglotException e) {
            Assert.assertFalse(initialExecute);
            Assert.assertTrue(e.asHostException() == exception[0]);
            assertFrames(false, e, expectedFrames);
        }
    }

    @Test
    public void testGuestLanguageError() {
        try {
            String source = "function bar() { x = 1 / \"asdf\"; }\n" +
                            "function foo() { return bar(); }\n" +
                            "function main() { foo(); }";
            ctx.eval(Source.newBuilder("sl", source, "script.sl").buildLiteral());
            fail();
        } catch (PolyglotException e) {
            assertTrue(e.isGuestException());

            Iterator<StackFrame> frames = e.getPolyglotStackTrace().iterator();
            assertGuestFrame(frames, "sl", "bar", "script.sl", 21, 31);
            assertGuestFrame(frames, "sl", "foo", "script.sl", 59, 64);
            assertGuestFrame(frames, "sl", "main", "script.sl", 86, 91);
            assertHostFrame(frames, Context.class.getName(), "eval");
            assertHostFrame(frames, SLExceptionTest.class.getName(), "testGuestLanguageError");

            // only host frames trailing
            while (frames.hasNext()) {
                assertTrue(frames.next().isHostFrame());
            }
        }
    }

    private static class TestProxy implements ProxyExecutable {
        private int depth;
        private final Value f;
        final List<PolyglotException> seenExceptions = new ArrayList<>();

        RuntimeException thrownException;

        TestProxy(int depth, Value f) {
            this.depth = depth;
            this.f = f;
        }

        public Object execute(Value... t) {
            depth--;
            if (depth > 0) {
                try {
                    return f.execute(this);
                } catch (PolyglotException e) {
                    assertProxyException(this, e);
                    seenExceptions.add(e);
                    throw e;
                }
            } else {
                thrownException = new RuntimeException("Error in proxy test.");
                throw thrownException;
            }
        }
    }

    @Test
    public void testProxyGuestLanguageStack() {
        Value bar = ctx.eval("sl", "function foo(f) { f(); } function bar(f) { return foo(f); } function main() { return bar; }");

        TestProxy proxy = new TestProxy(3, bar);
        try {
            bar.execute(proxy);
            fail();
        } catch (PolyglotException e) {
            assertProxyException(proxy, e);

            for (PolyglotException seenException : proxy.seenExceptions) {
                // exceptions are unwrapped and wrapped again
                assertNotSame(e, seenException);
                assertSame(e.asHostException(), seenException.asHostException());
            }
        }
    }

    private static void assertProxyException(TestProxy proxy, PolyglotException e) {
        assertTrue(e.isHostException());
        if (e.asHostException() instanceof AssertionError) {
            throw (AssertionError) e.asHostException();
        }
        assertSame(proxy.thrownException, e.asHostException());

        Iterator<StackFrame> frames = e.getPolyglotStackTrace().iterator();
        assertHostFrame(frames, TestProxy.class.getName(), "execute");
        for (int i = 0; i < 2; i++) {
            assertGuestFrame(frames, "sl", "foo", "Unnamed", 18, 21);
            assertGuestFrame(frames, "sl", "bar", "Unnamed", 50, 56);

            assertHostFrame(frames, Value.class.getName(), "execute");
            assertHostFrame(frames, TestProxy.class.getName(), "execute");
        }

        assertGuestFrame(frames, "sl", "foo", "Unnamed", 18, 21);
        assertGuestFrame(frames, "sl", "bar", "Unnamed", 50, 56);

        assertHostFrame(frames, Value.class.getName(), "execute");
        assertHostFrame(frames, SLExceptionTest.class.getName(), "testProxyGuestLanguageStack");

        while (frames.hasNext()) {
            // skip unit test frames.
            assertTrue(frames.next().isHostFrame());
        }
    }

    private static void assertHostFrame(Iterator<StackFrame> frames, String className, String methodName) {
        assertTrue(frames.hasNext());
        StackFrame frame = frames.next();
        assertTrue(frame.isHostFrame());
        assertFalse(frame.isGuestFrame());
        assertEquals("host", frame.getLanguage().getId());
        assertEquals("Host", frame.getLanguage().getName());
        assertEquals(className + "." + methodName, frame.getRootName());
        assertNull(frame.getSourceLocation());
        assertNotNull(frame.toString());

        StackTraceElement hostFrame = frame.toHostFrame();
        assertEquals(className, hostFrame.getClassName());
        assertEquals(methodName, hostFrame.getMethodName());
        assertNotNull(hostFrame.toString());
        assertTrue(hostFrame.equals(hostFrame));
        assertNotEquals(0, hostFrame.hashCode());
    }

    private static void assertGuestFrame(Iterator<StackFrame> frames, String languageId, String rootName, String fileName, int charIndex, int endIndex) {
        assertTrue(frames.hasNext());
        StackFrame frame = frames.next();
        assertTrue(frame.toString(), frame.isGuestFrame());
        assertEquals(languageId, frame.getLanguage().getId());
        assertEquals(rootName, frame.getRootName());
        assertNotNull(frame.getSourceLocation());
        assertNotNull(frame.getSourceLocation().getSource());
        assertEquals(fileName, frame.getSourceLocation().getSource().getName());
        assertEquals(charIndex, frame.getSourceLocation().getCharIndex());
        assertEquals(endIndex, frame.getSourceLocation().getCharEndIndex());

        StackTraceElement hostFrame = frame.toHostFrame();
        assertEquals("<" + languageId + ">", hostFrame.getClassName());
        assertEquals(rootName, hostFrame.getMethodName());
        assertEquals(frame.getSourceLocation().getStartLine(), hostFrame.getLineNumber());
        assertEquals(fileName, hostFrame.getFileName());
        assertNotNull(hostFrame.toString());
        assertTrue(hostFrame.equals(hostFrame));
        assertNotEquals(0, hostFrame.hashCode());
    }

    @Export
    public String methodThatTakesFunction(Function<String, String> s) {
        return s.apply("t");
    }

    @Test
    public void testGuestOverHostPropagation() {
        Context context = Context.newBuilder("sl").allowAllAccess(true).build();
        String code = "" +
                        "function other(x) {" +
                        "   return invalidFunction();" +
                        "}" +
                        "" +
                        "function f(test) {" +
                        "test.methodThatTakesFunction(other);" +
                        "}";

        context.eval("sl", code);
        try {
            context.getBindings("sl").getMember("f").execute(this);
            fail();
        } catch (PolyglotException e) {
            assertFalse(e.isHostException());
            assertTrue(e.isGuestException());
            Iterator<StackFrame> frames = e.getPolyglotStackTrace().iterator();
            assertTrue(frames.next().isGuestFrame());
            assertGuestFrame(frames, "sl", "other", "Unnamed", 29, 46);
            assertHostFrame(frames, "com.oracle.truffle.polyglot.PolyglotFunction", "apply");
            assertHostFrame(frames, "com.oracle.truffle.sl.test.SLExceptionTest", "methodThatTakesFunction");
            assertGuestFrame(frames, "sl", "f", "Unnamed", 66, 101);

            // rest is just unit test host frames
            while (frames.hasNext()) {
                assertTrue(frames.next().isHostFrame());
            }
        }
    }

}
