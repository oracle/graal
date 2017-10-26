/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.api.vm.PolyglotRuntime;
import com.oracle.truffle.sl.SLLanguage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;

/**
 * Test of SL instrumentation.
 */
public class SLInstrumentTest {

    @Test
    public void testLexicalScopes() {
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
        Source source = Source.newBuilder(code).name("testing").mimeType(SLLanguage.MIME_TYPE).build();
        PolyglotEngine engine = PolyglotEngine.newBuilder().setOut(new java.io.OutputStream() {
            // null output stream
            @Override
            public void write(int b) throws IOException {
            }
        }).build();
        PolyglotRuntime.Instrument envInstr = engine.getRuntime().getInstruments().get("testEnvironmentHandlerInstrument");
        envInstr.setEnabled(true);
        TruffleInstrument.Env env = envInstr.lookup(Environment.class).env;
        List<Throwable> throwables = new ArrayList<>();
        env.getInstrumenter().attachListener(SourceSectionFilter.newBuilder().lineIn(1, source.getLineCount()).build(), new ExecutionEventListener() {
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
        engine.eval(source);
        Assert.assertTrue(throwables.toString(), throwables.isEmpty());
    }

    @CompilerDirectives.TruffleBoundary
    private static void verifyLexicalScopes(Iterable<Scope> lexicalScopes, Iterable<Scope> dynamicScopes, int line, MaterializedFrame frame) {
        int depth = 0;
        switch (line) {
            case 1:
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

    @SuppressWarnings("rawtypes")
    private static void checkVars(TruffleObject vars, Object... expected) {
        Map map = JavaInterop.asJavaObject(Map.class, vars);
        for (int i = 0; i < expected.length; i += 2) {
            String name = (String) expected[i];
            Object value = expected[i + 1];
            assertTrue(name, map.containsKey(name));
            if (value != null) {
                assertEquals(name, value, map.get(name));
            } else {
                try {
                    map.get(name);
                    fail(name + " should not allow to read the value.");
                } catch (Exception ex) {
                    if (ex instanceof UnsupportedMessageException) {
                        // O.K.
                    } else {
                        throw new RuntimeException(ex);
                    }
                }
            }
        }
        assertEquals(map.keySet().toString(), expected.length / 2, map.size());
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
        Source source = Source.newBuilder(code).name("testing").mimeType(SLLanguage.MIME_TYPE).build();
        ByteArrayOutputStream engineOut = new ByteArrayOutputStream();
        PolyglotEngine engine = PolyglotEngine.newBuilder().setOut(engineOut).build();
        engine.eval(source);
        String engineOutput = fullOutput;
        Assert.assertEquals(engineOutput, engineOut.toString());

        // Check output
        PolyglotRuntime.Instrument outInstr = engine.getRuntime().getInstruments().get("testEnvironmentHandlerInstrument");
        outInstr.setEnabled(true);
        TruffleInstrument.Env env = outInstr.lookup(Environment.class).env;
        ByteArrayOutputStream consumedOut = new ByteArrayOutputStream();
        EventBinding<ByteArrayOutputStream> outputConsumerBinding = env.getInstrumenter().attachOutConsumer(consumedOut);
        Assert.assertEquals(0, consumedOut.size());
        engine.eval(source);
        BufferedReader fromOutReader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(consumedOut.toByteArray())));
        engineOutput = engineOutput + fullOutput;
        Assert.assertEquals(engineOutput, engineOut.toString());
        Assert.assertTrue(fromOutReader.ready());
        Assert.assertEquals(fullLines, readLinesList(fromOutReader));

        // Check two output readers
        ByteArrayOutputStream consumedOut2 = new ByteArrayOutputStream();
        EventBinding<ByteArrayOutputStream> outputConsumerBinding2 = env.getInstrumenter().attachOutConsumer(consumedOut2);
        Assert.assertEquals(0, consumedOut2.size());
        engine.eval(source);
        fromOutReader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(consumedOut.toByteArray())));
        BufferedReader fromOutReader2 = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(consumedOut2.toByteArray())));
        engineOutput = engineOutput + fullOutput;
        Assert.assertEquals(engineOutput, engineOut.toString());
        Assert.assertTrue(fromOutReader.ready());
        Assert.assertTrue(fromOutReader2.ready());
        String fullLines2x = fullLines.substring(0, fullLines.length() - 1) + ", " + fullLines.substring(1);
        Assert.assertEquals(fullLines2x, readLinesList(fromOutReader));
        Assert.assertEquals(fullLines, readLinesList(fromOutReader2));

        // One output reader closes, the other still receives the output
        outputConsumerBinding.dispose();
        consumedOut.reset();
        consumedOut2.reset();
        engine.eval(source);
        engineOutput = engineOutput + fullOutput;
        Assert.assertEquals(engineOutput, engineOut.toString());
        Assert.assertEquals(0, consumedOut.size());
        Assert.assertTrue(consumedOut2.size() > 0);
        fromOutReader2 = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(consumedOut2.toByteArray())));
        Assert.assertEquals(fullLines, readLinesList(fromOutReader2));

        // Remaining closes and pure exec successful:
        consumedOut2.reset();
        outputConsumerBinding2.dispose();
        engine.eval(source);
        engineOutput = engineOutput + fullOutput;
        Assert.assertEquals(engineOutput, engineOut.toString());
        Assert.assertEquals(0, consumedOut.size());
        Assert.assertEquals(0, consumedOut2.size());

        // Add a reader again and disable the instrument:
        env.getInstrumenter().attachOutConsumer(consumedOut);
        outInstr.setEnabled(false);
        engine.eval(source);
        engineOutput = engineOutput + fullOutput;
        Assert.assertEquals(engineOutput, engineOut.toString());
        Assert.assertEquals(0, consumedOut.size());
        Assert.assertEquals(0, consumedOut2.size());
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

    @TruffleInstrument.Registration(id = "testEnvironmentHandlerInstrument")
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
