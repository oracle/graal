/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.StandardTags.CallTag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.NodeLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.LibraryFactory;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.sl.runtime.SLBigInteger;
import com.oracle.truffle.sl.runtime.SLFunction;
import com.oracle.truffle.tck.DebuggerTester;

/**
 * Test of SL instrumentation.
 */
public class SLInstrumentTest {

    static final InteropLibrary INTEROP = LibraryFactory.resolve(InteropLibrary.class).getUncached();

    @BeforeClass
    public static void runWithWeakEncapsulationOnly() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

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
                        "    e = 30;\n" +
                        "  }\n" +
                        "  f = 40;\n" +         // 20
                        "  println(b);\n" +
                        "  println(a);\n" +
                        "}\n" +
                        "function main() {\n" +
                        "  test(\"n_n\");\n" +  // 25
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
                    verifyScopes(context, frame, true);
                }

                @Override
                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                    if (context.hasTag(StandardTags.StatementTag.class)) {
                        verifyScopes(context, frame, false);
                    }
                }

                @Override
                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                }

                private void verifyScopes(EventContext context, VirtualFrame frame, boolean onEnter) {
                    Node node = context.getInstrumentedNode();
                    assertTrue(NodeLibrary.getUncached().hasScope(node, null));
                    assertTrue(NodeLibrary.getUncached().hasScope(node, frame));
                    assertFalse(NodeLibrary.getUncached().hasReceiverMember(node, frame));
                    assertTrue(NodeLibrary.getUncached().hasRootInstance(node, frame));
                    try {
                        verifyRootInstance(node, NodeLibrary.getUncached().getRootInstance(node, frame));
                        Object lexicalScope = NodeLibrary.getUncached().getScope(node, null, onEnter);
                        Object dynamicScope = NodeLibrary.getUncached().getScope(node, frame, onEnter);
                        Object lexicalArguments = findArguments(node, null);
                        Object dynamicArguments = findArguments(node, frame);
                        verifyLexicalScopes(onEnter, new Object[]{lexicalScope, dynamicScope}, new Object[]{lexicalArguments, dynamicArguments},
                                        context.getInstrumentedSourceSection().getStartLine(), node, frame.materialize());
                    } catch (ThreadDeath t) {
                        throw t;
                    } catch (Throwable t) {
                        CompilerDirectives.transferToInterpreter();
                        PrintStream lsErr = System.err;
                        lsErr.println("Line = " + context.getInstrumentedSourceSection().getStartLine() + " onEnter = " + onEnter);
                        lsErr.println("Node = " + node + ", class = " + node.getClass().getName());
                        t.printStackTrace(lsErr);
                        throwables.add(t);
                    }
                }

                private void verifyRootInstance(Node node, Object rootInstance) throws UnsupportedMessageException {
                    assertNotNull(rootInstance);
                    SLFunction function = (SLFunction) rootInstance;
                    assertEquals(node.getRootNode().getName(), InteropLibrary.getUncached().asString(function.getName()));
                }

                private Object findArguments(Node node, VirtualFrame frame) throws UnsupportedMessageException {
                    Node rootTagNode = node;
                    while (rootTagNode != null) {
                        if (rootTagNode instanceof InstrumentableNode && ((InstrumentableNode) rootTagNode).hasTag(StandardTags.RootTag.class)) {
                            break;
                        }
                        rootTagNode = rootTagNode.getParent();
                    }
                    if (rootTagNode == null) {
                        return null;
                    }
                    return NodeLibrary.getUncached().getScope(rootTagNode, frame, true);
                }
            });
            Context.newBuilder().engine(engine).build().eval(source);
        }
        assertTrue(throwables.toString(), throwables.isEmpty());
    }

    @CompilerDirectives.TruffleBoundary
    private static void verifyLexicalScopes(boolean onEnter, Object[] scopes, Object[] arguments,
                    int line, Node node, MaterializedFrame frame) throws UnsupportedMessageException, InvalidArrayIndexException {
        switch (line) {
            case 1:
                break;
            case 2:
                checkRootNode(scopes, "test", node, frame);
                checkVars(arguments, "n", "n_n");
                if (onEnter) {
                    checkVars(scopes, "n", "n_n");
                } else {
                    checkVars(scopes, "n", "n_n", "a", 1L);
                }
                assertFalse(getParentScopes(arguments));
                assertFalse(getParentScopes(scopes));
                break;
            case 3:
            case 7:
                checkRootNode(scopes, "test", node, frame);
                checkVars(arguments, "n", "n_n");
                checkVars(scopes, "n", "n_n", "a", 1L);
                assertFalse(getParentScopes(arguments));
                assertFalse(getParentScopes(scopes));
                break;
            case 4:
            case 8:
                checkBlock(scopes, node);
                checkVars(arguments, "n", "n_n");
                long bVal = (line == 4) ? 10L : 20L;
                if (onEnter) {
                    checkVars(scopes, "n", "n_n", "a", 1L);
                } else {
                    checkVars(scopes, "b", bVal, "n", "n_n", "a", 1L);
                }
                assertFalse(getParentScopes(arguments));
                assertTrue(getParentScopes(scopes));

                checkRootNode(scopes, "test", node, frame);
                checkVars(scopes, "n", "n_n", "a", 1L);
                assertFalse(getParentScopes(scopes));
                break;
            case 5:
            case 9:
            case 10:
                checkBlock(scopes, node);
                checkVars(arguments, "n", "n_n");
                long aVal = (line == 10 || line == 9 && !onEnter) ? 0L : 1L;
                bVal = (line == 5) ? 10L : 20L;
                if (onEnter || line != 10) {
                    checkVars(scopes, "b", bVal, "n", "n_n", "a", aVal);
                } else {
                    checkVars(scopes, "b", bVal, "c", 1L, "n", "n_n", "a", aVal);
                }
                assertFalse(getParentScopes(arguments));
                assertTrue(getParentScopes(scopes));

                checkRootNode(scopes, "test", node, frame);
                checkVars(scopes, "n", "n_n", "a", aVal);
                assertFalse(getParentScopes(scopes));
                break;
            case 11:
                checkBlock(scopes, node);
                checkVars(arguments, "n", "n_n");
                checkVars(scopes, "b", 20L, "c", 1L, "n", "n_n", "a", 0L);
                assertFalse(getParentScopes(arguments));
                assertTrue(getParentScopes(scopes));

                checkRootNode(scopes, "test", node, frame);
                checkVars(scopes, "n", "n_n", "a", 0L);
                assertFalse(getParentScopes(scopes));
                break;
            case 12:
            case 13:
            case 14:
            case 15:
                checkBlock(scopes, node);
                checkVars(arguments, "n", "n_n");
                aVal = (line == 12 && onEnter) ? 0L : 4L;
                bVal = (line < 13 || line == 13 && onEnter) ? 20L : 5L;
                long cVal = (line < 14 || line == 14 && onEnter) ? 1L : 6L;
                if (onEnter || line != 15) {
                    checkVars(scopes, "b", bVal, "c", cVal, "n", "n_n", "a", aVal);
                } else {
                    checkVars(scopes, "d", 7L, "b", bVal, "c", cVal, "n", "n_n", "a", aVal);
                }
                assertFalse(getParentScopes(arguments));
                assertTrue(getParentScopes(scopes));

                checkBlock(scopes, node);
                checkVars(scopes, "b", bVal, "c", cVal, "n", "n_n", "a", aVal);
                assertTrue(getParentScopes(scopes));

                checkRootNode(scopes, "test", node, frame);
                checkVars(scopes, "n", "n_n", "a", aVal);
                assertFalse(getParentScopes(scopes));
                break;
            case 16:
                checkBlock(scopes, node);
                checkVars(arguments, "n", "n_n");
                checkVars(scopes, "d", 7L, "b", 5L, "c", 6L, "n", "n_n", "a", 4L);
                assertFalse(getParentScopes(arguments));
                assertTrue(getParentScopes(scopes));

                checkBlock(scopes, node);
                checkVars(scopes, "b", 5L, "c", 6L, "n", "n_n", "a", 4L);
                assertTrue(getParentScopes(scopes));

                checkRootNode(scopes, "test", node, frame);
                checkVars(scopes, "n", "n_n", "a", 4L);
                assertFalse(getParentScopes(scopes));
                break;
            case 18:
                checkBlock(scopes, node);
                checkVars(arguments, "n", "n_n");
                if (onEnter) {
                    checkVars(scopes, "b", 5L, "c", 6L, "n", "n_n", "a", 4L);
                } else {
                    checkVars(scopes, "b", 5L, "c", 6L, "e", 30L, "n", "n_n", "a", 4L);
                }
                assertFalse(getParentScopes(arguments));
                assertTrue(getParentScopes(scopes));

                checkRootNode(scopes, "test", node, frame);
                checkVars(scopes, "n", "n_n", "a", 4L);
                assertFalse(getParentScopes(scopes));
                break;
            case 20:
            case 21:
            case 22:
                checkRootNode(scopes, "test", node, frame);
                checkVars(arguments, "n", "n_n");
                if (line == 20 && onEnter) {
                    checkVars(scopes, "n", "n_n", "a", 4L);
                } else {
                    checkVars(scopes, "n", "n_n", "a", 4L, "f", 40L);
                }
                assertFalse(getParentScopes(arguments));
                assertFalse(getParentScopes(scopes));
                break;
            case 24:
            case 25:
                checkRootNode(scopes, "main", node, frame);
                checkVars(arguments);
                checkVars(scopes);
                assertFalse(getParentScopes(arguments));
                assertFalse(getParentScopes(scopes));
                break;
            default:
                fail("Untested line: " + line);
                break;
        }
    }

    private static void checkRootNode(Object[] scopes, String name, Node node, MaterializedFrame frame) throws UnsupportedMessageException {
        for (Object scope : scopes) {
            checkRootNode(scope, name, node, frame);
        }
    }

    private static void checkRootNode(Object scope, String name, Node node, MaterializedFrame frame) throws UnsupportedMessageException {
        assertEquals(name, InteropLibrary.getUncached().asString(InteropLibrary.getUncached().toDisplayString(scope)));
        assertTrue(InteropLibrary.getUncached().hasSourceLocation(scope));
        SourceSection section = InteropLibrary.getUncached().getSourceLocation(scope);
        Node scopeNode = findScopeNode(node, section);
        assertTrue(scopeNode.getClass().getName(), scopeNode instanceof RootNode);
        assertEquals(name, ((RootNode) scopeNode).getName());
        assertEquals(frame.getFrameDescriptor(), ((RootNode) scopeNode).getFrameDescriptor());
    }

    private static void checkBlock(Object[] scopes, Node node) throws UnsupportedMessageException {
        for (Object scope : scopes) {
            checkBlock(scope, node);
        }
    }

    private static void checkBlock(Object scope, Node node) throws UnsupportedMessageException {
        assertEquals("block", InteropLibrary.getUncached().toDisplayString(scope));
        assertTrue(InteropLibrary.getUncached().hasSourceLocation(scope));
        SourceSection section = InteropLibrary.getUncached().getSourceLocation(scope);
        // Test that ls.getNode() does not return the current root node, it ought to be a block node
        Node scopeNode = findScopeNode(node, section);
        assertFalse(scopeNode.getClass().getName(), scopeNode instanceof RootNode);
    }

    private static Node findScopeNode(Node node, SourceSection section) {
        RootNode root = node.getRootNode();
        if (section.equals(root.getSourceSection())) {
            return root;
        }
        Node scopeNode = node.getParent();
        while (scopeNode != null) {
            if (section.equals(scopeNode.getSourceSection())) {
                break;
            }
            scopeNode = scopeNode.getParent();
        }
        assertNotNull(scopeNode);
        return scopeNode;
    }

    private static boolean contains(Object vars, String key) {
        return INTEROP.isMemberExisting(vars, key);
    }

    private static Object read(Object vars, String key) {
        try {
            return INTEROP.readMember(vars, key);
        } catch (UnknownIdentifierException | UnsupportedMessageException e) {
            throw new AssertionError(e);
        }
    }

    private static boolean isNull(Object vars) {
        return INTEROP.isNull(vars);
    }

    private static void checkVars(Object[] scopes, Object... expected) throws UnsupportedMessageException, InvalidArrayIndexException {
        for (int s = 0; s < scopes.length; s++) {
            boolean lexical = s < scopes.length / 2;
            Object vars = scopes[s];
            Object members = INTEROP.getMembers(vars);
            int numMembers = (int) INTEROP.getArraySize(members);
            List<String> memberNamesList = new ArrayList<>(numMembers);
            for (int i = 0; i < numMembers; i++) {
                memberNamesList.add(INTEROP.asString(INTEROP.readArrayElement(members, i)));
            }
            String memberNames = memberNamesList.toString();
            assertEquals(memberNames, expected.length / 2, numMembers);
            for (int i = 0; i < expected.length; i += 2) {
                String name = (String) expected[i];
                assertTrue(name + " not in " + memberNames, contains(vars, name));
                Object member = INTEROP.readArrayElement(members, i / 2);
                assertEquals(memberNames, name, INTEROP.asString(member));
                assertTrue(INTEROP.hasSourceLocation(member));
                if (lexical) {
                    assertFalse(INTEROP.isMemberWritable(vars, name));
                    assertTrue(isNull(read(vars, name)));
                } else {
                    Object value = expected[i + 1];
                    if (value instanceof String) {
                        assertEquals(name, value, InteropLibrary.getUncached().asString(read(vars, name)));
                    } else {
                        assertEquals(name, value, read(vars, name));
                    }
                    assertTrue(INTEROP.isMemberWritable(vars, name));
                }
            }
        }
    }

    private static boolean getParentScopes(Object[] scopes) throws UnsupportedMessageException {
        boolean haveParent = false;
        for (int s = 0; s < scopes.length; s++) {
            if (InteropLibrary.getUncached().hasScopeParent(scopes[s])) {
                haveParent = true;
                scopes[s] = InteropLibrary.getUncached().getScopeParent(scopes[s]);
            } else {
                scopes[s] = null;
            }
        }
        return haveParent;
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

    @TruffleBoundary
    private static CharSequence getSourceSectionCharacters(SourceSection section) {
        return section.getCharacters();
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
                    if ("readln".equals(getSourceSectionCharacters(context.getInstrumentedSourceSection()))) {
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

        earlyReturn.returnValue = new SLBigInteger(new BigInteger("-42"));
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
                    if (fceCode.equals(getSourceSectionCharacters(context.getInstrumentedSourceSection()))) {
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
                    if (getSourceSectionCharacters(ss).toString().contains(error)) {
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
