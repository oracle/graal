/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.sl.test.SLJavaInteropTest.toUnixString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.StandardTags.CallTag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.LibraryFactory;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.sl.runtime.SLBigNumber;
import com.oracle.truffle.tck.DebuggerTester;

/**
 * Test of SL instrumentation.
 */
public class SLInstrumentTest {

    static final InteropLibrary INTEROP = LibraryFactory.resolve(InteropLibrary.class).getUncached();

    @Test
    public void testLexicalScopes() throws Exception {
        String code = "function test(n) {\n" +
                        "  a = 1;\n" +          // 2
                        "  if (a > 0) {\n" +
                        "    b = 10;\n" +
                        "    println(b);\n" +   // 5
                        "  }\n" +
                        "  if (a == 1) {\n" +
                        "    b = 20;\n" +
                        "    a = 0;\n" +
                        "    c = 1;\n" +        // 10
                        "    if (b > 0) {\n" +
                        "      a = 4;\n" +
                        "      b = 5;\n" +
                        "      c = 6;\n" +
                        "      d = 7;\n" +      // 15
                        "      println(d);\n" +
                        "    }\n" +
                        "  }\n" +
                        "  println(b);\n" +
                        "  println(a);\n" +     // 20
                        "}\n" +
                        "function main() {\n" +
                        "  test(\"n_n\");\n" +
                        "}";
        Source source = Source.newBuilder("sl", code, "testing").build();
        List<Throwable> throwables;
        try (Engine engine = Engine.newBuilder().out(new java.io.OutputStream() {
            // null output stream
            @Override
            public void write(int b) throws IOException {
            }
        }).build()) {
            Instrument envInstr = engine.getInstruments().get("testEnvironmentHandlerInstrument");
            TruffleInstrument.Env env = envInstr.lookup(Environment.class).env;
            throwables = new ArrayList<>();
            env.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().lineIn(1, source.getLineCount()).build(), new ExecutionEventListener() {
                @Override
                public void onEnter(EventContext context, VirtualFrame frame) {
                    Node node = context.getInstrumentedNode();
                    Iterable<Scope> lexicalScopes = env.findLocalScopes(node, null);
                    Iterable<Scope> dynamicScopes = env.findLocalScopes(node, frame);
                    try {
                        verifyLexicalScopes(lexicalScopes, dynamicScopes, context.getInstrumentedSourceSection().getStartLine(), frame.materialize());
                    } catch (ThreadDeath t) {
                        throw t;
                    } catch (Throwable t) {
                        CompilerDirectives.transferToInterpreter();
                        PrintStream lsErr = System.err;
                        lsErr.println("Line = " + context.getInstrumentedSourceSection().getStartLine());
                        lsErr.println("Node = " + node + ", class = " + node.getClass().getName());
                        t.printStackTrace(lsErr);
                        throwables.add(t);
                    }
                }

                @Override
                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                }

                @Override
                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                }
            });
            Context.newBuilder().engine(engine).build().eval(source);
        }
        assertTrue(throwables.toString(), throwables.isEmpty());
    }

    @CompilerDirectives.TruffleBoundary
    private static void verifyLexicalScopes(Iterable<Scope> lexicalScopes, Iterable<Scope> dynamicScopes, int line, MaterializedFrame frame) {
        int depth = 0;
        switch (line) {
            case 1:
                break;
            case 2:
                for (Scope ls : lexicalScopes) {
                    // Test that ls.getNode() returns the current root node:
                    checkRootNode(ls, "test", frame);
                    TruffleObject arguments = (TruffleObject) ls.getArguments();
                    checkVars(arguments, "n", null);
                    TruffleObject variables = (TruffleObject) ls.getVariables();
                    checkVars(variables, "n", null);
                    depth++;
                }
                assertEquals("LexicalScope depth", 1, depth);
                depth = 0;
                for (Scope ls : dynamicScopes) {
                    // Test that ls.getNode() returns the current root node:
                    checkRootNode(ls, "test", frame);
                    TruffleObject arguments = (TruffleObject) ls.getArguments();
                    checkVars(arguments, "n", "n_n");
                    TruffleObject variables = (TruffleObject) ls.getVariables();
                    checkVars(variables, "n", "n_n");
                    depth++;
                }
                assertEquals("DynamicScope depth", 1, depth);
                break;
            case 3:
            case 7:
            case 19:
            case 20:
                for (Scope ls : lexicalScopes) {
                    checkRootNode(ls, "test", frame);
                    TruffleObject arguments = (TruffleObject) ls.getArguments();
                    checkVars(arguments, "n", null);
                    TruffleObject variables = (TruffleObject) ls.getVariables();
                    checkVars(variables, "n", null, "a", null);
                    depth++;
                }
                assertEquals("LexicalScope depth", 1, depth);
                depth = 0;
                for (Scope ls : dynamicScopes) {
                    checkRootNode(ls, "test", frame);
                    TruffleObject arguments = (TruffleObject) ls.getArguments();
                    checkVars(arguments, "n", "n_n");
                    TruffleObject variables = (TruffleObject) ls.getVariables();
                    long aVal = (line < 19) ? 1L : 4L;
                    checkVars(variables, "n", "n_n", "a", aVal);
                    depth++;
                }
                assertEquals("DynamicScope depth", 1, depth);
                break;
            case 4:
            case 8:
                for (Scope ls : lexicalScopes) {
                    if (depth == 0) {
                        checkBlock(ls);
                        TruffleObject variables = (TruffleObject) ls.getVariables();
                        checkVars(variables);
                        assertNull(ls.getArguments());
                    } else {
                        checkRootNode(ls, "test", frame);
                        TruffleObject variables = (TruffleObject) ls.getVariables();
                        checkVars(variables, "n", null, "a", null);
                        TruffleObject arguments = (TruffleObject) ls.getArguments();
                        checkVars(arguments, "n", null);
                    }
                    depth++;
                }
                assertEquals("LexicalScope depth", 2, depth);
                depth = 0;
                for (Scope ls : dynamicScopes) {
                    if (depth == 0) {
                        checkBlock(ls);
                        TruffleObject variables = (TruffleObject) ls.getVariables();
                        checkVars(variables);
                        assertNull(ls.getArguments());
                    } else {
                        checkRootNode(ls, "test", frame);
                        TruffleObject variables = (TruffleObject) ls.getVariables();
                        checkVars(variables, "n", "n_n", "a", 1L);
                        TruffleObject arguments = (TruffleObject) ls.getArguments();
                        checkVars(arguments, "n", "n_n");
                    }
                    depth++;
                }
                assertEquals("DynamicScope depth", 2, depth);
                break;
            case 5:
            case 9:
            case 10:
                for (Scope ls : lexicalScopes) {
                    if (depth == 0) {
                        checkBlock(ls);
                        TruffleObject variables = (TruffleObject) ls.getVariables();
                        checkVars(variables, "b", null);
                        assertNull(ls.getArguments());
                    } else {
                        checkRootNode(ls, "test", frame);
                        TruffleObject variables = (TruffleObject) ls.getVariables();
                        checkVars(variables, "n", null, "a", null);
                        TruffleObject arguments = (TruffleObject) ls.getArguments();
                        checkVars(arguments, "n", null);
                    }
                    depth++;
                }
                assertEquals("LexicalScope depth", 2, depth);
                depth = 0;
                for (Scope ls : dynamicScopes) {
                    if (depth == 0) {
                        checkBlock(ls);
                        TruffleObject variables = (TruffleObject) ls.getVariables();
                        long bVal = (line == 5) ? 10L : 20L;
                        checkVars(variables, "b", bVal);
                        assertNull(ls.getArguments());
                    } else {
                        checkRootNode(ls, "test", frame);
                        TruffleObject variables = (TruffleObject) ls.getVariables();
                        long aVal = (line == 10) ? 0L : 1L;
                        checkVars(variables, "n", "n_n", "a", aVal);
                        TruffleObject arguments = (TruffleObject) ls.getArguments();
                        checkVars(arguments, "n", "n_n");
                    }
                    depth++;
                }
                assertEquals("DynamicScope depth", 2, depth);
                break;
            case 11:
                for (Scope ls : lexicalScopes) {
                    if (depth == 0) {
                        checkBlock(ls);
                        TruffleObject variables = (TruffleObject) ls.getVariables();
                        checkVars(variables, "b", null, "c", null);
                        assertNull(ls.getArguments());
                    } else {
                        checkRootNode(ls, "test", frame);
                    }
                    depth++;
                }
                assertEquals("LexicalScope depth", 2, depth);
                depth = 0;
                for (Scope ls : dynamicScopes) {
                    if (depth == 0) {
                        checkBlock(ls);
                        TruffleObject variables = (TruffleObject) ls.getVariables();
                        checkVars(variables, "b", 20L, "c", 1L);
                        assertNull(ls.getArguments());
                    } else {
                        checkRootNode(ls, "test", frame);
                    }
                    depth++;
                }
                assertEquals("DynamicScope depth", 2, depth);
                break;
            case 12:
            case 13:
            case 14:
            case 15:
                for (Scope ls : lexicalScopes) {
                    if (depth == 0) {
                        checkBlock(ls);
                        TruffleObject variables = (TruffleObject) ls.getVariables();
                        checkVars(variables);
                        assertNull(ls.getArguments());
                    } else if (depth == 1) {
                        checkBlock(ls);
                        TruffleObject variables = (TruffleObject) ls.getVariables();
                        checkVars(variables, "b", null, "c", null);
                        assertNull(ls.getArguments());
                    } else {
                        checkRootNode(ls, "test", frame);
                        TruffleObject variables = (TruffleObject) ls.getVariables();
                        checkVars(variables, "n", null, "a", null);
                    }
                    depth++;
                }
                assertEquals("LexicalScope depth", 3, depth);
                depth = 0;
                for (Scope ls : dynamicScopes) {
                    if (depth == 0) {
                        checkBlock(ls);
                        TruffleObject variables = (TruffleObject) ls.getVariables();
                        checkVars(variables);
                        assertNull(ls.getArguments());
                    } else if (depth == 1) {
                        checkBlock(ls);
                        TruffleObject variables = (TruffleObject) ls.getVariables();
                        long bVal = (line < 14) ? 20L : 5L;
                        long cVal = (line < 15) ? 1L : 6L;
                        checkVars(variables, "b", bVal, "c", cVal);
                        assertNull(ls.getArguments());
                    } else {
                        checkRootNode(ls, "test", frame);
                        TruffleObject variables = (TruffleObject) ls.getVariables();
                        long aVal = (line == 12) ? 0L : 4L;
                        checkVars(variables, "n", "n_n", "a", aVal);
                    }
                    depth++;
                }
                assertEquals("DynamicScope depth", 3, depth);
                break;
            case 16:
                for (Scope ls : lexicalScopes) {
                    if (depth == 0) {
                        checkBlock(ls);
                        TruffleObject variables = (TruffleObject) ls.getVariables();
                        checkVars(variables, "d", null);
                        assertNull(ls.getArguments());
                    } else if (depth == 1) {
                        checkBlock(ls);
                        TruffleObject variables = (TruffleObject) ls.getVariables();
                        checkVars(variables, "b", null, "c", null);
                        assertNull(ls.getArguments());
                    } else {
                        checkRootNode(ls, "test", frame);
                        TruffleObject variables = (TruffleObject) ls.getVariables();
                        checkVars(variables, "n", null, "a", null);
                    }
                    depth++;
                }
                assertEquals("LexicalScope depth", 3, depth);
                depth = 0;
                for (Scope ls : dynamicScopes) {
                    if (depth == 0) {
                        checkBlock(ls);
                        TruffleObject variables = (TruffleObject) ls.getVariables();
                        checkVars(variables, "d", 7L);
                        assertNull(ls.getArguments());
                    } else if (depth == 1) {
                        checkBlock(ls);
                        TruffleObject variables = (TruffleObject) ls.getVariables();
                        checkVars(variables, "b", 5L, "c", 6L);
                        assertNull(ls.getArguments());
                    } else {
                        checkRootNode(ls, "test", frame);
                        TruffleObject variables = (TruffleObject) ls.getVariables();
                        checkVars(variables, "n", "n_n", "a", 4L);
                    }
                    depth++;
                }
                assertEquals("DynamicScope depth", 3, depth);
                break;
            case 22:
            case 23:
                for (Scope ls : lexicalScopes) {
                    checkRootNode(ls, "main", frame);
                    TruffleObject arguments = (TruffleObject) ls.getArguments();
                    checkVars(arguments);
                    TruffleObject variables = (TruffleObject) ls.getVariables();
                    checkVars(variables);
                    depth++;
                }
                assertEquals("LexicalScope depth", 1, depth);
                depth = 0;
                for (Scope ls : dynamicScopes) {
                    checkRootNode(ls, "main", frame);
                    TruffleObject arguments = (TruffleObject) ls.getArguments();
                    checkVars(arguments);
                    TruffleObject variables = (TruffleObject) ls.getVariables();
                    checkVars(variables);
                    depth++;
                }
                assertEquals("DynamicScope depth", 1, depth);
                break;
            default:
                fail("Untested line: " + line);
                break;
        }
    }

    private static void checkRootNode(Scope ls, String name, MaterializedFrame frame) {
        assertEquals(name, ls.getName());
        Node node = ls.getNode();
        assertTrue(node.getClass().getName(), node instanceof RootNode);
        assertEquals(name, ((RootNode) node).getName());
        assertEquals(frame.getFrameDescriptor(), ((RootNode) node).getFrameDescriptor());
    }

    private static void checkBlock(Scope ls) {
        assertEquals("block", ls.getName());
        // Test that ls.getNode() does not return the current root node, it ought to be a block node
        Node node = ls.getNode();
        assertNotNull(node);
        assertFalse(node.getClass().getName(), node instanceof RootNode);
    }

    private static boolean contains(TruffleObject vars, String key) {
        return INTEROP.isMemberExisting(vars, key);
    }

    private static Object read(TruffleObject vars, String key) {
        try {
            return INTEROP.readMember(vars, key);
        } catch (UnknownIdentifierException | UnsupportedMessageException e) {
            throw new AssertionError(e);
        }
    }

    private static boolean isNull(TruffleObject vars) {
        return INTEROP.isNull(vars);
    }

    private static int keySize(TruffleObject vars) {
        try {
            return (int) INTEROP.getArraySize(INTEROP.getMembers(vars));
        } catch (UnsupportedMessageException e) {
            throw new AssertionError(e);
        }
    }

    private static void checkVars(TruffleObject vars, Object... expected) {
        for (int i = 0; i < expected.length; i += 2) {
            String name = (String) expected[i];
            Object value = expected[i + 1];
            assertTrue(name, contains(vars, name));
            if (value != null) {
                assertEquals(name, value, read(vars, name));
            } else {
                assertTrue(isNull((TruffleObject) read(vars, name)));
            }
        }
        assertEquals(expected.length / 2, keySize(vars));
    }

    @Test
    public void testOutput() throws IOException {
        String code = "function main() {\n" +
                        "  f = fac(5);\n" +
                        "  println(f);\n" +
                        "}\n" +
                        "function fac(n) {\n" +
                        "  println(n);\n" +
                        "  if (n <= 1) {\n" +
                        "    return 1;\n" + // break
                        "  }\n" +
                        "  return n * fac(n - 1);\n" +
                        "}\n";
        String fullOutput = "5\n4\n3\n2\n1\n120\n";
        String fullLines = "[5, 4, 3, 2, 1, 120]";
        // Pure exec:
        Source source = Source.newBuilder("sl", code, "testing").build();
        ByteArrayOutputStream engineOut = new ByteArrayOutputStream();
        Engine engine = Engine.newBuilder().out(engineOut).build();
        Context context = Context.newBuilder().engine(engine).build();
        context.eval(source);
        String engineOutput = fullOutput;
        assertEquals(engineOutput, toUnixString(engineOut));

        // Check output
        Instrument outInstr = engine.getInstruments().get("testEnvironmentHandlerInstrument");
        TruffleInstrument.Env env = outInstr.lookup(Environment.class).env;
        ByteArrayOutputStream consumedOut = new ByteArrayOutputStream();
        EventBinding<ByteArrayOutputStream> outputConsumerBinding = env.getInstrumenter().attachOutConsumer(consumedOut);
        assertEquals(0, consumedOut.size());
        context.eval(source);
        BufferedReader fromOutReader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(consumedOut.toByteArray())));
        engineOutput = engineOutput + fullOutput;
        assertEquals(engineOutput, toUnixString(engineOut));
        assertTrue(fromOutReader.ready());
        assertEquals(fullLines, readLinesList(fromOutReader));

        // Check two output readers
        ByteArrayOutputStream consumedOut2 = new ByteArrayOutputStream();
        EventBinding<ByteArrayOutputStream> outputConsumerBinding2 = env.getInstrumenter().attachOutConsumer(consumedOut2);
        assertEquals(0, consumedOut2.size());
        context.eval(source);
        fromOutReader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(consumedOut.toByteArray())));
        BufferedReader fromOutReader2 = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(consumedOut2.toByteArray())));
        engineOutput = engineOutput + fullOutput;
        assertEquals(engineOutput, toUnixString(engineOut));
        assertTrue(fromOutReader.ready());
        assertTrue(fromOutReader2.ready());
        String fullLines2x = fullLines.substring(0, fullLines.length() - 1) + ", " + fullLines.substring(1);
        assertEquals(fullLines2x, readLinesList(fromOutReader));
        assertEquals(fullLines, readLinesList(fromOutReader2));

        // One output reader closes, the other still receives the output
        outputConsumerBinding.dispose();
        consumedOut.reset();
        consumedOut2.reset();
        context.eval(source);
        engineOutput = engineOutput + fullOutput;
        assertEquals(engineOutput, toUnixString(engineOut));
        assertEquals(0, consumedOut.size());
        assertTrue(consumedOut2.size() > 0);
        fromOutReader2 = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(consumedOut2.toByteArray())));
        assertEquals(fullLines, readLinesList(fromOutReader2));

        // Remaining closes and pure exec successful:
        consumedOut2.reset();
        outputConsumerBinding2.dispose();
        context.eval(source);
        engineOutput = engineOutput + fullOutput;
        assertEquals(engineOutput, toUnixString(engineOut));
        assertEquals(0, consumedOut.size());
        assertEquals(0, consumedOut2.size());

    }

    String readLinesList(BufferedReader br) throws IOException {
        List<String> lines = new ArrayList<>();
        while (br.ready()) {
            String line = br.readLine();
            if (line == null) {
                break;
            }
            lines.add(line);
        }
        return lines.toString();
    }

    /**
     * Test that we reenter a node whose execution was interrupted. Unwind just the one node off.
     */
    @Test
    public void testRedoIO() throws Throwable {
        String code = "function main() {\n" +
                        "  a = readln();\n" +
                        "  return a;\n" +
                        "}\n";
        final Source ioWait = Source.newBuilder("sl", code, "testing").build();
        final TestRedoIO[] redoIOPtr = new TestRedoIO[1];
        InputStream strIn = new ByteArrayInputStream("O.K.".getBytes());
        InputStream delegateInputStream = new InputStream() {
            @Override
            public int read() throws IOException {
                synchronized (SLInstrumentTest.class) {
                    // Block reading before we do unwind:
                    if (redoIOPtr[0].beforePop) {
                        redoIOPtr[0].inRead.release();
                        try {
                            SLInstrumentTest.class.wait();
                        } catch (InterruptedException ex) {
                            throw new RuntimeInterruptedException();
                        }
                    }
                }
                return strIn.read();
            }
        };
        Engine engine = Engine.newBuilder().in(delegateInputStream).build();
        TestRedoIO redoIO = engine.getInstruments().get("testRedoIO").lookup(TestRedoIO.class);
        redoIOPtr[0] = redoIO;
        redoIO.inRead.drainPermits();
        Context context = Context.newBuilder().engine(engine).build();
        Value ret = context.eval(ioWait);
        assertEquals("O.K.", ret.asString());
        assertFalse(redoIO.beforePop);
    }

    private static class RuntimeInterruptedException extends RuntimeException {
        private static final long serialVersionUID = -4735601164894088571L;
    }

    @TruffleInstrument.Registration(id = "testRedoIO", services = TestRedoIO.class)
    public static class TestRedoIO extends TruffleInstrument {

        boolean beforePop = true;
        Semaphore inRead = new Semaphore(1);

        @Override
        protected void onCreate(Env env) {
            env.registerService(env.getInstrumenter());
            env.registerService(this);
            env.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.ANY, new ExecutionEventListener() {
                @Override
                public void onEnter(EventContext context, VirtualFrame frame) {
                    if ("readln".equals(context.getInstrumentedSourceSection().getCharacters())) {
                        CompilerDirectives.transferToInterpreter();
                        // Interrupt the I/O
                        final Thread thread = Thread.currentThread();
                        new Thread() {
                            @Override
                            public void run() {
                                try {
                                    inRead.acquire();
                                } catch (InterruptedException ex) {
                                }
                                synchronized (SLInstrumentTest.class) {
                                    if (beforePop) {
                                        thread.interrupt();
                                    }
                                }
                            }
                        }.start();
                    }
                }

                @Override
                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                }

                @Override
                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                    if (exception instanceof RuntimeInterruptedException) {
                        CompilerDirectives.transferToInterpreter();
                        synchronized (SLInstrumentTest.class) {
                            beforePop = false;
                        }
                        throw context.createUnwind(null);
                    }
                }

                @Override
                public Object onUnwind(EventContext context, VirtualFrame frame, Object info) {
                    return ProbeNode.UNWIND_ACTION_REENTER;
                }
            });
        }

    }

    /**
     * Test that we can forcibly return early from call nodes with an arbitrary value.
     */
    @Test
    public void testEarlyReturn() throws Exception {
        String code = "function main() {\n" +
                        "  a = 10;\n" +
                        "  b = a;\n" +
                        "  // Let fce() warm up and specialize:\n" +
                        "  while (a == b && a < 100000) {\n" +
                        "    a = fce(a);\n" +
                        "    b = b + 1;\n" +
                        "  }\n" +
                        "  c = a;\n" +
                        "  // Run fce() and alter it's return type in an instrument:\n" +
                        "  c = fce(c);\n" +
                        "  return c;\n" +
                        "}\n" +
                        "function fce(x) {\n" +
                        "  return x + 1;\n" +
                        "}\n";
        final Source source = Source.newBuilder("sl", code, "testing").build();
        ByteArrayOutputStream engineOut = new ByteArrayOutputStream();
        Engine engine = Engine.newBuilder().err(engineOut).build();
        Context context = Context.newBuilder().engine(engine).build();
        // No instrument:
        Value ret = context.eval(source);
        assertTrue(ret.isNumber());
        assertEquals(100001L, ret.asLong());

        EarlyReturnInstrument earlyReturn = context.getEngine().getInstruments().get("testEarlyReturn").lookup(EarlyReturnInstrument.class);

        earlyReturn.fceCode = "fce(a)";
        earlyReturn.returnValue = 200000L;
        ret = context.eval(source);
        assertTrue(ret.isNumber());
        assertEquals(200001L, ret.asLong());

        earlyReturn.returnValue = "Hello!";
        ret = context.eval(source);
        assertFalse(ret.isNumber());
        assertTrue(ret.isString());
        assertEquals("Hello!1", ret.asString());

        // Specialize to long again:
        earlyReturn.fceCode = "<>";
        ret = context.eval(source);
        assertTrue(ret.isNumber());
        assertEquals(100001L, ret.asLong());

        earlyReturn.fceCode = "fce(a)";
        earlyReturn.returnValue = new BigInteger("-42");
        boolean interopFailure;
        try {
            context.eval(source);
            interopFailure = false;
        } catch (PolyglotException err) {
            interopFailure = true;
        }
        assertTrue(interopFailure);

        earlyReturn.returnValue = new SLBigNumber(new BigInteger("-42"));
        ret = context.eval(source);
        assertTrue(ret.isNumber());
        assertEquals(-41L, ret.asLong());

        earlyReturn.fceCode = "fce(c)";
        earlyReturn.returnValue = Boolean.TRUE;
        ret = context.eval(source);
        assertTrue(ret.isBoolean());
        assertEquals(Boolean.TRUE, ret.asBoolean());

        earlyReturn.fceCode = "fce(c)";
        earlyReturn.returnValue = -42.00;
        ret = context.eval(source);
        assertTrue(ret.isNumber());
        assertEquals(-42.0, ret.asDouble(), 1e-8);

        earlyReturn.fceCode = "fce(c)";
        earlyReturn.returnValue = "Hello!";
        ret = context.eval(source);
        assertTrue(ret.isString());
        assertEquals("Hello!", ret.asString());
    }

    @TruffleInstrument.Registration(id = "testEarlyReturn", services = EarlyReturnInstrument.class)
    public static class EarlyReturnInstrument extends TruffleInstrument {

        String fceCode;      // return when this code is hit
        Object returnValue;  // return this value

        @Override
        protected void onCreate(Env env) {
            env.registerService(this);
            env.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(CallTag.class).build(), new ExecutionEventListener() {
                @Override
                public void onEnter(EventContext context, VirtualFrame frame) {
                }

                @Override
                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                    if (fceCode.equals(context.getInstrumentedSourceSection().getCharacters())) {
                        CompilerDirectives.transferToInterpreter();
                        throw context.createUnwind(null);
                    }
                }

                @Override
                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                }

                @Override
                public Object onUnwind(EventContext context, VirtualFrame frame, Object info) {
                    return returnValue;
                }

            });
        }

    }

    /**
     * This test demonstrates that it's possible to easily replace a return value of any node using
     * {@link ExecutionEventListener#onUnwind(com.oracle.truffle.api.instrumentation.EventContext, com.oracle.truffle.api.frame.VirtualFrame, java.lang.Object)}
     * .
     */
    @Test
    public void testReplaceNodeReturnValue() throws Exception {
        if (System.getProperty("java.vm.name").contains("Graal:graal-enterprise")) {
            return; // GR-16755
        }
        String code = "function main() {\n" +
                        "  a = new();\n" +
                        "  b = a.rp1;\n" +
                        "  return b;\n" +
                        "}\n";
        final Source source = Source.newBuilder("sl", code, "testing").build();
        SourceSection ss = DebuggerTester.getSourceImpl(source).createSection(24, 5);
        Context context = Context.create();
        NewReplacedInstrument replaced = context.getEngine().getInstruments().get("testNewNodeReplaced").lookup(NewReplacedInstrument.class);
        replaced.attachAt(ss);

        Value ret = context.eval(source);
        assertEquals("Replaced Value", ret.toString());
    }

    @TruffleInstrument.Registration(id = "testNewNodeReplaced", services = NewReplacedInstrument.class)
    public static final class NewReplacedInstrument extends TruffleInstrument {

        private Env env;
        private final Object replacedValue = new ReplacedTruffleObject();

        @Override
        @SuppressWarnings("hiding")
        protected void onCreate(Env env) {
            this.env = env;
            env.registerService(this);
        }

        void attachAt(SourceSection ss) {
            env.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().sourceSectionEquals(ss).build(), new ExecutionEventListener() {
                @Override
                public void onEnter(EventContext context, VirtualFrame frame) {
                }

                @Override
                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                    if (result instanceof TruffleObject) {
                        CompilerDirectives.transferToInterpreter();
                        throw context.createUnwind(null);
                    }
                }

                @Override
                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                }

                @Override
                public Object onUnwind(EventContext context, VirtualFrame frame, Object info) {
                    return replacedValue;
                }

            });
        }

        @ExportLibrary(InteropLibrary.class)
        @SuppressWarnings("static-method")
        static class ReplacedTruffleObject implements TruffleObject {

            @ExportMessage
            final Object readMember(@SuppressWarnings("unused") String member) {
                return "Replaced Value";
            }

            @ExportMessage
            final boolean hasMembers() {
                return true;
            }

            @ExportMessage
            final Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
                return new KeysArray(new String[]{"rp1, rp2"});
            }

            @ExportMessage
            final boolean isMemberReadable(String member) {
                return member.equals("rp1") || member.equals("rp2");
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class KeysArray implements TruffleObject {

        private final String[] keys;

        KeysArray(String[] keys) {
            this.keys = keys;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return index >= 0 && index < keys.length;
        }

        @ExportMessage
        long getArraySize() {
            return keys.length;
        }

        @ExportMessage
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            try {
                return keys[(int) index];
            } catch (IndexOutOfBoundsException e) {
                CompilerDirectives.transferToInterpreter();
                throw InvalidArrayIndexException.create(index);
            }
        }

    }

    /**
     * Test that we can alter function arguments on reenter.
     */
    @Test
    public void testChangeArgumentsOnReenter() throws Exception {
        String code = "function main() {\n" +
                        "  y = fce(0, 10000);\n" +
                        "  return y;\n" +
                        "}\n" +
                        "function fce(x, z) {\n" +
                        "  y = 2 * x;\n" +
                        "  if (y < z) {\n" +
                        "    print(\"A bad error.\");\n" +
                        "    return 0 - 1;\n" +
                        "  } else {\n" +
                        "    return y;\n" +
                        "  }\n" +
                        "}\n";
        final Source source = Source.newBuilder("sl", code, "testing").build();
        Context context = Context.create();
        IncreaseArgOnErrorInstrument incOnError = context.getEngine().getInstruments().get("testIncreaseArgumentOnError").lookup(IncreaseArgOnErrorInstrument.class);
        incOnError.attachOn("A bad error");

        Value ret = context.eval(source);
        assertEquals(10000, ret.asInt());
    }

    @TruffleInstrument.Registration(id = "testIncreaseArgumentOnError", services = IncreaseArgOnErrorInstrument.class)
    public static final class IncreaseArgOnErrorInstrument extends TruffleInstrument {

        private Env env;
        @CompilationFinal private ThreadDeath unwind;

        @Override
        @SuppressWarnings("hiding")
        protected void onCreate(Env env) {
            this.env = env;
            env.registerService(this);
        }

        void attachOn(String error) {
            EventBinding<ExecutionEventListener> reenterBinding = env.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.RootTag.class).build(),
                            new ExecutionEventListener() {
                                @Override
                                public void onEnter(EventContext context, VirtualFrame frame) {
                                }

                                @Override
                                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                                }

                                @Override
                                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                                }

                                @Override
                                public Object onUnwind(EventContext context, VirtualFrame frame, Object info) {
                                    frame.getArguments()[0] = (Long) frame.getArguments()[0] + 1;
                                    return ProbeNode.UNWIND_ACTION_REENTER;
                                }

                            });
            env.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class).build(), new ExecutionEventListener() {
                @Override
                public void onEnter(EventContext context, VirtualFrame frame) {
                    SourceSection ss = context.getInstrumentedSourceSection();
                    if (ss.getCharacters().toString().contains(error)) {
                        if (unwind == null) {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            unwind = context.createUnwind(null, reenterBinding);
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

            });
        }
    }

    @TruffleInstrument.Registration(id = "testEnvironmentHandlerInstrument", services = Environment.class)
    public static class EnvironmentHandlerInstrument extends TruffleInstrument {

        @Override
        protected void onCreate(final TruffleInstrument.Env env) {
            env.registerService(new Environment(env));
        }
    }

    private static class Environment {

        TruffleInstrument.Env env;

        Environment(TruffleInstrument.Env env) {
            this.env = env;
        }
    }

}
