/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.chromeinspector.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.regex.Pattern;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.After;
import org.junit.Test;

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.sl.SLLanguage;
import com.oracle.truffle.tools.chromeinspector.InspectorDebugger;

public class SLInspectDebugTest {

    private static final String FACTORIAL = "function factorial(n) {\n" +
                    "  f = 1;\n" +
                    "  i = 2;\n" +
                    "  while (i <= n) {\n" +
                    "    f2 = f * i;\n" +
                    "    i = i + 1;\n" +
                    "    f = f2;\n" +
                    "  }\n" +
                    "  return f;\n" +
                    "}";
    private static final String CODE1 = "function main() {\n" +
                    "  a = 10;\n" +
                    "  b = factorial(a/2) / 60;\n" +
                    "  while (b > 0) {\n" +
                    "    c = a + b;\n" +
                    "    b = b - c/10;\n" +
                    "  }\n" +
                    "  return b;\n" +
                    "}\n" + FACTORIAL;
    private static final String CODE2 = "function main() {\n" +
                    "  n = 10;\n" +
                    "  i = 0;\n" +
                    "  while (i < n) {\n" +
                    "    fceWithBP(i);\n" +
                    "    i = i + 1;\n" +
                    "  }\n" +
                    "}\n" +
                    "function fceWithBP(i) {\n" +
                    "  i2 = i*i;\n" +
                    "  return i2;\n" +
                    "}";
    private static final String CODE3 = "function main() {\n" +
                    "  n = 3;\n" +
                    "  factorial(n);\n" +
                    "}\n" +
                    "function factorial(n) {\n" +
                    "  if (n <= 1) {\n" +
                    "    return 1;\n" +
                    "  } else {\n" +
                    "    f = n * factorial(n - 1);\n" +
                    "    return f;\n" +
                    "  }\n" +
                    "}";
    private static final String CODE4 = "function main() {\n" +
                    "  n = 10;\n" +
                    "  testLocations(n);\n" +
                    "}\n" +
                    "function testLocations(n) {\n" +
                    "  \n" +
                    "  x =\n" +
                    "    n * n;\n" +
                    "  y =\n" +
                    "    n / 2;\n" +
                    "  \n" +
                    "  x = x + y; y = x / y; return x * y;\n" +
                    "  \n" +
                    "}";
    private static final String CODE_RET_VAL = "function main() {\n" +
                    "  a = addThem(1, 2);\n" +
                    "  println(a);\n" +
                    "}\n" +
                    "function addThem(a, b) {\n" +
                    "  a = fn(a);\n" +
                    "  b = fn(b);\n" +
                    "  return a + b;\n" +
                    "}\n" +
                    "\n" +
                    "function fn(n) {\n" +
                    "  return n;\n" +
                    "}\n";
    private static final String CODE_THROW = "function main() {\n" +
                    "  i = \"0\";\n" +
                    "  return invert(i);\n" +
                    "}\n" +
                    "function invert(n) {\n" +
                    "  x = 10 / n;\n" +
                    "  return x;\n" +
                    "}\n";
    private static final String CODE_VARS = "function main() {\n" +
                    "  n = 2;\n" +
                    "  m = 2 * n;\n" +
                    "  b = n > 0;\n" +
                    "  bb = m > 0;\n" +
                    "  big = 12345678901234567890;\n" +
                    "  str = \"A String\";\n" +
                    "  //obj = new();\n" +
                    "  f = fn;\n" +
                    "  f2 = 0;\n" +
                    "  while (b) {\n" +
                    "    n = n - 1;\n" +
                    "    //obj.a = n;\n" +
                    "    big = big * big;\n" +
                    "    b = n > 0;\n" +
                    "    b;\n" +
                    "  }\n" +
                    "  return b;\n" +
                    "}\n" +
                    "\n" +
                    "function fn() {\n" +
                    "  return 2;\n" +
                    "}\n";
    private static final String CODE_MEMBERS = "function main() {\n" +
                    "  obj = new();\n" +
                    "  obj.a = 1;\n" +
                    "  obj.b = 2;\n" +
                    "  obj.c = obj;\n" +
                    "  debugger;\n" +
                    "  return obj;\n" +
                    "}\n";
    private static final String CODE_OBJECT_GROUPS = "function main() {\n" +
                    "  obj = new();\n" +
                    "  obj.a = new();\n" +
                    "  obj.b = obj;\n" +
                    "  debugger;\n" +
                    "  obj.c = obj;\n" +
                    "  obj.a.a = new();\n" +
                    "  return obj;\n" +
                    "}\n";
    private static final String GUEST_FUNCTIONS = "function main() {\n" +
                    "  foo0();\n" +
                    "  foo1();\n" +
                    "  foo0();\n" +
                    "  foo1();\n" +
                    "}\n" +
                    "function foo0() {\n" +
                    "  n = 0;" +
                    "}\n" +
                    "function foo1() {\n" +
                    "  n = 1;" +
                    "}\n";
    private static final String BUILTIN_FUNCTIONS = "function main() {\n" +
                    "  isExecutable(a);\n" +
                    "  nanoTime();\n" +
                    "  isNull(a);\n" +
                    "  isExecutable(a);\n" +
                    "  isNull(b);\n" +
                    "  nanoTime();\n" +
                    "}\n";
    private static final String SL_BUILTIN_URI = "SL builtin";

    private InspectorTester tester;

    @After
    public void tearDown() {
        tester = null;
    }

    // @formatter:off   The default formatting makes unnecessarily big indents and illogical line breaks
    // CheckStyle: stop line length check
    @Test
    public void testInitialSuspendAndSource() throws Exception {
        tester = InspectorTester.start(true);
        Source source = Source.newBuilder("sl", CODE1, "SLTest.sl").build();
        String slTestURI = InspectorTester.getStringURI(source.getURI());
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        assertEquals("{\"result\":{},\"id\":1}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":2,\"method\":\"Debugger.enable\"}");
        assertEquals("{\"result\":{},\"id\":2}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":3,\"method\":\"Runtime.runIfWaitingForDebugger\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":3}\n" +
                        "{\"method\":\"Runtime.executionContextCreated\",\"params\":{\"context\":{\"origin\":\"\",\"name\":\"test\",\"id\":1}}}\n"));
        tester.eval(source);
        long id = tester.getContextId();
        // Suspend at the beginning of the script:
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":0,\"scriptId\":\"0\",\"endColumn\":0,\"startColumn\":0,\"startLine\":0,\"length\":0,\"executionContextId\":" + id + ",\"url\":\"" + SL_BUILTIN_URI + "\",\"hash\":\"ffffffffffffffffffffffffffffffffffffffff\"}}\n" +
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":18,\"scriptId\":\"1\",\"endColumn\":1,\"startColumn\":0,\"startLine\":0,\"length\":245,\"executionContextId\":" + id + ",\"url\":\"" + slTestURI + "\",\"hash\":\"f8058ed0f3c2f0acf3e37e59f953127afdba90e5\"}}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"1\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"2\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"3\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":1}," +
                                                 "\"url\":\"" + slTestURI + "\"}]}}\n"));

        // Get the script code:
        tester.sendMessage("{\"id\":3,\"method\":\"Debugger.getScriptSource\",\"params\":{\"scriptId\":\"1\"}}");
        String scriptSourceMessage = "{\"result\":{\"scriptSource\":\"" + source.getCharacters().toString().replace("\n", "\\n") + "\"},\"id\":3}";
        String messages = tester.getMessages(true).trim();
        assertEquals(scriptSourceMessage, messages);
        tester.sendMessage("{\"id\":4,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":4}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"
        ));
        tester.finish();
    }

    @Test
    public void testStepping() throws Exception {
        tester = InspectorTester.start(true);
        Source source = Source.newBuilder("sl", CODE1, "SLTest.sl").build();
        String slTestURI = InspectorTester.getStringURI(source.getURI());
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        tester.sendMessage("{\"id\":2,\"method\":\"Debugger.enable\"}");
        tester.sendMessage("{\"id\":3,\"method\":\"Runtime.runIfWaitingForDebugger\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":1}\n" +
                        "{\"result\":{},\"id\":2}\n" +
                        "{\"result\":{},\"id\":3}\n" +
                        "{\"method\":\"Runtime.executionContextCreated\",\"params\":{\"context\":{\"origin\":\"\",\"name\":\"test\",\"id\":1}}}\n"));
        tester.eval(source);
        long id = tester.getContextId();
        // Suspend at the beginning of the script:
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":0,\"scriptId\":\"0\",\"endColumn\":0,\"startColumn\":0,\"startLine\":0,\"length\":0,\"executionContextId\":" + id + ",\"url\":\"" + SL_BUILTIN_URI + "\",\"hash\":\"ffffffffffffffffffffffffffffffffffffffff\"}}\n" +
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":18,\"scriptId\":\"1\",\"endColumn\":1,\"startColumn\":0,\"startLine\":0,\"length\":245,\"executionContextId\":" + id + ",\"url\":\"" + slTestURI + "\",\"hash\":\"f8058ed0f3c2f0acf3e37e59f953127afdba90e5\"}}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"1\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"2\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"3\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":1}," +
                                                 "\"url\":\"" + slTestURI + "\"}]}}\n"
        ));

        // Step over:
        tester.sendMessage("{\"id\":3,\"method\":\"Debugger.stepOver\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":3}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"4\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"5\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"6\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":2}," +
                                                 "\"url\":\"" + slTestURI + "\"}]}}\n"
        ));

        // Step into:
        tester.sendMessage("{\"id\":4,\"method\":\"Debugger.stepInto\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":4}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"factorial\"," +
                                                 "\"scopeChain\":[{\"name\":\"factorial\",\"type\":\"local\",\"object\":{\"description\":\"factorial\",\"type\":\"object\",\"objectId\":\"7\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"8\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"9\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":9}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":10}," +
                                                 "\"url\":\"" + slTestURI + "\"}," +
                                                "{\"callFrameId\":\"1\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"10\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"11\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"12\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":6,\"lineNumber\":2}," +
                                                 "\"url\":\"" + slTestURI + "\"}]}}\n"
        ));

        // Step out:
        tester.sendMessage("{\"id\":5,\"method\":\"Debugger.stepOut\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":5}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"13\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"14\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"15\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":19,\"lineNumber\":2}," +
                                                 "\"url\":\"" + slTestURI + "\"}]}}\n"
        ));
        tester.sendMessage("{\"id\":100,\"method\":\"Debugger.stepOut\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":100}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"
        ));
        tester.finish();
    }

    @Test
    public void testBreakpoints() throws Exception {
        tester = InspectorTester.start(false);
        Source source = Source.newBuilder("sl", CODE1, "SLTest.sl").build();
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        tester.sendMessage("{\"id\":2,\"method\":\"Debugger.enable\"}");
        tester.sendMessage("{\"id\":3,\"method\":\"Runtime.runIfWaitingForDebugger\"}");
        String slTestURI = InspectorTester.getStringURI(source.getURI());
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":1}\n" +
                        "{\"result\":{},\"id\":2}\n" +
                        "{\"result\":{},\"id\":3}\n" +
                        "{\"method\":\"Runtime.executionContextCreated\",\"params\":{\"context\":{\"origin\":\"\",\"name\":\"test\",\"id\":1}}}\n"));

        tester.sendMessage("{\"id\":3,\"method\":\"Debugger.setBreakpointByUrl\",\"params\":{\"lineNumber\":11,\"url\":\"" + slTestURI + "\",\"columnNumber\":0,\"condition\":\"\"}}");

        assertEquals("{\"result\":{\"breakpointId\":\"1\",\"locations\":[]},\"id\":3}", tester.getMessages(true).trim());
        tester.eval(source);
        long id = tester.getContextId();
        // Suspend at the breakpoint:
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":0,\"scriptId\":\"0\",\"endColumn\":0,\"startColumn\":0,\"startLine\":0,\"length\":0,\"executionContextId\":" + id + ",\"url\":\"" + SL_BUILTIN_URI + "\",\"hash\":\"ffffffffffffffffffffffffffffffffffffffff\"}}\n" +
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":18,\"scriptId\":\"1\",\"endColumn\":1,\"startColumn\":0,\"startLine\":0,\"length\":245,\"executionContextId\":" + id + ",\"url\":\"" + slTestURI + "\",\"hash\":\"f8058ed0f3c2f0acf3e37e59f953127afdba90e5\"}}\n" +
                        "{\"method\":\"Debugger.breakpointResolved\",\"params\":{\"breakpointId\":\"1\",\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":11}}}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[\"1\"]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"factorial\"," +
                                                 "\"scopeChain\":[{\"name\":\"factorial\",\"type\":\"local\",\"object\":{\"description\":\"factorial\",\"type\":\"object\",\"objectId\":\"1\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"2\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"3\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":9}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":11}," +
                                                 "\"url\":\"" + slTestURI + "\"}," +
                                                "{\"callFrameId\":\"1\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"4\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"5\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"6\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":6,\"lineNumber\":2}," +
                                                 "\"url\":\"" + slTestURI + "\"}]}}\n"));
        // Continue to a location:
        tester.sendMessage("{\"id\":20,\"method\":\"Debugger.continueToLocation\",\"params\":{\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":5}}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":20}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"block\",\"type\":\"block\",\"object\":{\"description\":\"block\",\"type\":\"object\",\"objectId\":\"7\"}}," +
                                                                 "{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"8\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"9\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"10\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":4,\"lineNumber\":5}," +
                                                 "\"url\":\"" + slTestURI + "\"}]}}\n"));
        // Breakpoint on script ID:
        tester.sendMessage("{\"id\":25,\"method\":\"Debugger.setBreakpoint\",\"params\":{\"location\":{\"scriptId\":\"1\",\"columnNumber\":0,\"lineNumber\":7}}}");
        tester.sendMessage("{\"id\":26,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"breakpointId\":\"2\",\"actualLocation\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":7}},\"id\":25}\n" +
                        "{\"result\":{},\"id\":26}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[\"2\"]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"11\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"12\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"13\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":7}," +
                                                 "\"url\":\"" + slTestURI + "\"}]}}\n"));
        // Resume to finish:
        tester.sendMessage("{\"id\":35,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":35}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        tester.finish();
    }

    @Test
    public void testBreakpointDeactivation() throws Exception {
        tester = InspectorTester.start(true);
        Source source = Source.newBuilder("sl", CODE2, "SLTest.sl").build();
        String slTestURI = InspectorTester.getStringURI(source.getURI());
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        tester.sendMessage("{\"id\":2,\"method\":\"Debugger.enable\"}");
        tester.sendMessage("{\"id\":3,\"method\":\"Runtime.runIfWaitingForDebugger\"}");
        String srcURL = InspectorTester.getStringURI(source.getURI());
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":1}\n" +
                        "{\"result\":{},\"id\":2}\n" +
                        "{\"result\":{},\"id\":3}\n" +
                        "{\"method\":\"Runtime.executionContextCreated\",\"params\":{\"context\":{\"origin\":\"\",\"name\":\"test\",\"id\":1}}}\n"));
        tester.sendMessage("{\"id\":3,\"method\":\"Debugger.setBreakpointByUrl\",\"params\":{\"lineNumber\":9,\"url\":\"" + srcURL + "\",\"columnNumber\":0,\"condition\":\"\"}}");
        assertEquals("{\"result\":{\"breakpointId\":\"1\",\"locations\":[]},\"id\":3}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":4,\"method\":\"Debugger.setBreakpointByUrl\",\"params\":{\"lineNumber\":10,\"url\":\"" + srcURL + "\",\"columnNumber\":0,\"condition\":\"\"}}");
        assertEquals("{\"result\":{\"breakpointId\":\"2\",\"locations\":[]},\"id\":4}", tester.getMessages(true).trim());
        // Deactivate the breakpoints:
        tester.sendMessage("{\"id\":10,\"method\":\"Debugger.setBreakpointsActive\",\"params\":{\"active\":false}}");
        assertEquals("{\"result\":{},\"id\":10}", tester.getMessages(true).trim());
        tester.eval(source);

        // Suspend at the beginning of the script:
        long id = tester.getContextId();
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":0,\"scriptId\":\"0\",\"endColumn\":0,\"startColumn\":0,\"startLine\":0,\"length\":0,\"executionContextId\":" + id + ",\"url\":\"" + SL_BUILTIN_URI + "\",\"hash\":\"ffffffffffffffffffffffffffffffffffffffff\"}}\n" +
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":11,\"scriptId\":\"1\",\"endColumn\":1,\"startColumn\":0,\"startLine\":0,\"length\":144,\"executionContextId\":" + id + ",\"url\":\"" + slTestURI + "\",\"hash\":\"ee148976fc7d6f36fc01da4bfba1c3f3ff485978\"}}\n" +
                        "{\"method\":\"Debugger.breakpointResolved\",\"params\":{\"breakpointId\":\"1\",\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":9}}}\n" +
                        "{\"method\":\"Debugger.breakpointResolved\",\"params\":{\"breakpointId\":\"2\",\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":10}}}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"1\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"2\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"3\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":1}," +
                                                 "\"url\":\"" + srcURL + "\"}]}}\n"
        ));
        // Step over to while cycle and over the method with breakpoints:
        for (int numStep = 0; numStep < 4; numStep++) {
            tester.sendMessage("{\"id\":" + (20 + numStep) + ",\"method\":\"Debugger.stepOver\"}");
            int colNum = (numStep == 1) ? 9 : (numStep >= 2) ? 4 : 2;
            assertTrue(tester.compareReceivedMessages(
                            "{\"result\":{},\"id\":" + (20 + numStep) + "}\n" +
                            "{\"method\":\"Debugger.resumed\"}\n" +
                            "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                    "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"main\"," +
                                                     "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"" + (4 + 3 * numStep) + "\"}}," +
                                                                     "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"" + (5 + 3 * numStep) + "\"}}]," +
                                                     "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"" + (6 + 3 * numStep) + "\"}," +
                                                     "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                     "\"location\":{\"scriptId\":\"1\",\"columnNumber\":" + colNum + ",\"lineNumber\":" + (2 + numStep) + "}," +
                                                     "\"url\":\"" + srcURL + "\"}]}}\n"
            ));
        }
        // Step to while again
        tester.sendMessage("{\"id\":28,\"method\":\"Debugger.stepOver\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":28}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"16\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"17\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"18\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":3}," +
                                                 "\"url\":\"" + srcURL + "\"}]}}\n"
        ));
        // Step to the method call with breakpoints again:
        tester.sendMessage("{\"id\":29,\"method\":\"Debugger.stepOver\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":29}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"19\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"20\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"21\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":4,\"lineNumber\":4}," +
                                                 "\"url\":\"" + srcURL + "\"}]}}\n"
        ));
        // Activate the breakpoints:
        tester.sendMessage("{\"id\":30,\"method\":\"Debugger.setBreakpointsActive\",\"params\":{\"active\":true}}");
        assertEquals("{\"result\":{},\"id\":30}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":31,\"method\":\"Debugger.stepOver\"}");
        // Step over not finished, the first breakpoint is hit:
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":31}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[\"1\"]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"fceWithBP\"," +
                                                 "\"scopeChain\":[{\"name\":\"fceWithBP\",\"type\":\"local\",\"object\":{\"description\":\"fceWithBP\",\"type\":\"object\",\"objectId\":\"22\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"23\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"24\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":8}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":9}," +
                                                 "\"url\":\"" + srcURL + "\"}," +
                                                "{\"callFrameId\":\"1\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"25\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"26\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"27\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":4,\"lineNumber\":4}," +
                                                 "\"url\":\"" + srcURL + "\"}]}}\n"
        ));
        tester.sendMessage("{\"id\":32,\"method\":\"Debugger.stepOut\"}");
        // Step out not finished, the second breakpoint is hit:
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":32}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[\"2\"]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"fceWithBP\"," +
                                                 "\"scopeChain\":[{\"name\":\"fceWithBP\",\"type\":\"local\",\"object\":{\"description\":\"fceWithBP\",\"type\":\"object\",\"objectId\":\"28\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"29\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"30\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":8}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":10}," +
                                                 "\"url\":\"" + srcURL + "\"}," +
                                                "{\"callFrameId\":\"1\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"31\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"32\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"33\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":4,\"lineNumber\":4}," +
                                                 "\"url\":\"" + srcURL + "\"}]}}\n"
        ));
        tester.sendMessage("{\"id\":33,\"method\":\"Debugger.stepOut\"}");
        // Step out finished now:
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":33}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"34\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"35\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"36\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":15,\"lineNumber\":4}," +
                                                 "\"url\":\"" + srcURL + "\"}]}}\n"
        ));
        // Deactivate the breakpoints again:
        tester.sendMessage("{\"id\":40,\"method\":\"Debugger.setBreakpointsActive\",\"params\":{\"active\":false}}");
        assertEquals("{\"result\":{},\"id\":40}", tester.getMessages(true).trim());
        // Resume to finish:
        tester.sendMessage("{\"id\":45,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":45}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        tester.finish();
    }

    @Test
    public void testGuestFunctionBreakpoints() throws Exception {
        testGuestFunctionBreakpoints(false);
    }

    private void testGuestFunctionBreakpoints(boolean useConsoleUtilities) throws Exception {
        tester = InspectorTester.start(true);
        Source source = Source.newBuilder("sl", GUEST_FUNCTIONS, "SLTest.sl").build();
        String slTestURI = InspectorTester.getStringURI(source.getURI());
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        tester.sendMessage("{\"id\":2,\"method\":\"Debugger.enable\"}");
        tester.sendMessage("{\"id\":3,\"method\":\"Runtime.runIfWaitingForDebugger\"}");
        assertTrue(tester.compareReceivedMessages("{\"result\":{},\"id\":1}\n" +
                        "{\"result\":{},\"id\":2}\n" +
                        "{\"result\":{},\"id\":3}\n" +
                        "{\"method\":\"Runtime.executionContextCreated\",\"params\":{\"context\":{\"origin\":\"\",\"name\":\"test\",\"id\":1}}}\n"));
        tester.eval(source);
        long id = tester.getContextId();

        // Suspend at the beginning of the script:
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":0,\"scriptId\":\"0\",\"endColumn\":0,\"startColumn\":0,\"startLine\":0,\"length\":0,\"executionContextId\":" + id + ",\"url\":\"" + SL_BUILTIN_URI + "\",\"hash\":\"ffffffffffffffffffffffffffffffffffffffff\"}}\n" +
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":9,\"scriptId\":\"1\",\"endColumn\":9,\"startColumn\":0,\"startLine\":0,\"length\":116,\"executionContextId\":" + id + ",\"url\":\"" + slTestURI + "\",\"hash\":\"e5d2cd9aefc7cdf3fc01da4bfe94b4d3ff485978\"}}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"1\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"2\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"3\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":1}," +
                                                 "\"url\":\"" + slTestURI + "\"}]}}\n"));
        int objectId = 4;
        if (useConsoleUtilities) {
            tester.sendMessage("{\"id\":6,\"method\":\"Debugger.evaluateOnCallFrame\",\"params\":{\"callFrameId\":\"0\",\"expression\":\"debug(foo0)\",\"objectGroup\":\"console\",\"includeCommandLineAPI\":true,\"silent\":false,\"returnByValue\":false,\"generatePreview\":true}}");
            assertTrue(tester.compareReceivedMessages(
                            "{\"result\":{},\"id\":6}\n"));
        } else {
            tester.sendMessage("{\"id\":5,\"method\":\"Runtime.evaluate\",\"params\":{\"expression\":\"foo0\"}}");
            assertTrue(tester.compareReceivedMessages(
                            "{\"result\":{\"result\":{\"description\":\"foo0() {\\n  n = 0;}\",\"className\":\"Function\",\"type\":\"function\",\"objectId\":\"4\"}},\"id\":5}\n"));
            tester.sendMessage("{\"id\":6,\"method\":\"Debugger.setBreakpointOnFunctionCall\",\"params\":{\"objectId\":\"" + (objectId++) + "\"}}");
            assertTrue(tester.compareReceivedMessages(
                            "{\"result\":{\"breakpointId\":\"1\"},\"id\":6}\n"));
        }
        tester.sendMessage("{\"id\":7,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":7}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[\"1\"]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"foo0\"," +
                                                 "\"scopeChain\":[{\"name\":\"foo0\",\"type\":\"local\",\"object\":{\"description\":\"foo0\",\"type\":\"object\",\"objectId\":\"" + (objectId++) + "\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"" + (objectId++) + "\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"" + (objectId++) + "\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":6}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":6}," +
                                                 "\"url\":\"" + slTestURI + "\"}," +
                                                 "{\"callFrameId\":\"1\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"" + (objectId++) + "\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"" + (objectId++) + "\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"" + (objectId++) + "\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":1}," +
                                                 "\"url\":\"" + slTestURI + "\"}]}}\n"));
        if (useConsoleUtilities) {
            tester.sendMessage("{\"id\":8,\"method\":\"Debugger.evaluateOnCallFrame\",\"params\":{\"callFrameId\":\"0\",\"expression\":\"undebug(foo0)\",\"objectGroup\":\"console\",\"includeCommandLineAPI\":true,\"silent\":false,\"returnByValue\":false,\"generatePreview\":true}}");
            assertTrue(tester.compareReceivedMessages(
                            "{\"result\":{},\"id\":8}\n"));
        } else {
            tester.sendMessage("{\"id\":8,\"method\":\"Debugger.removeBreakpoint\",\"params\":{\"breakpointId\":\"1\"}}");
            assertTrue(tester.compareReceivedMessages(
                            "{\"result\":{},\"id\":8}\n"));
        }
        // Resume to finish:
        tester.sendMessage("{\"id\":10,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":10}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        tester.finish();
    }

    @Test
    public void testBuiltInFunctionBreakpoints() throws Exception {
        testBuiltInFunctionBreakpoints(false);
    }

    private void testBuiltInFunctionBreakpoints(boolean useConsoleUtilities) throws Exception {
        tester = InspectorTester.start(true);
        Source source = Source.newBuilder("sl", BUILTIN_FUNCTIONS, "SLTest.sl").build();
        String slTestURI = InspectorTester.getStringURI(source.getURI());
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        tester.sendMessage("{\"id\":2,\"method\":\"Debugger.enable\"}");
        tester.sendMessage("{\"id\":3,\"method\":\"Runtime.runIfWaitingForDebugger\"}");
        assertTrue(tester.compareReceivedMessages("{\"result\":{},\"id\":1}\n" +
                        "{\"result\":{},\"id\":2}\n" +
                        "{\"result\":{},\"id\":3}\n" +
                        "{\"method\":\"Runtime.executionContextCreated\",\"params\":{\"context\":{\"origin\":\"\",\"name\":\"test\",\"id\":1}}}\n"));
        tester.eval(source);
        long id = tester.getContextId();

        // Suspend at the beginning of the script:
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":0,\"scriptId\":\"0\",\"endColumn\":0,\"startColumn\":0,\"startLine\":0,\"length\":0,\"executionContextId\":" + id + ",\"url\":\"" + SL_BUILTIN_URI + "\",\"hash\":\"ffffffffffffffffffffffffffffffffffffffff\"}}\n" +
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":7,\"scriptId\":\"1\",\"endColumn\":1,\"startColumn\":0,\"startLine\":0,\"length\":112,\"executionContextId\":" + id + ",\"url\":\"" + slTestURI + "\",\"hash\":\"da38785ae156af96f047f02ffe94b4d3ff485978\"}}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"1\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"2\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"3\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":1}," +
                                                 "\"url\":\"" + slTestURI + "\"}]}}\n"));
        int objectId = 4;
        if (useConsoleUtilities) {
            tester.sendMessage("{\"id\":6,\"method\":\"Debugger.evaluateOnCallFrame\",\"params\":{\"callFrameId\":\"0\",\"expression\":\"debug(isNull)\",\"objectGroup\":\"console\",\"includeCommandLineAPI\":true,\"silent\":false,\"returnByValue\":false,\"generatePreview\":true}}");
            assertTrue(tester.compareReceivedMessages(
                            "{\"result\":{},\"id\":6}\n"));
        } else {
            tester.sendMessage("{\"id\":5,\"method\":\"Runtime.evaluate\",\"params\":{\"expression\":\"isNull\"}}");
            assertTrue(tester.compareReceivedMessages(
                            "{\"result\":{\"result\":{\"description\":\"Function isNull\",\"className\":\"Function\",\"type\":\"function\",\"objectId\":\"" + objectId + "\"}},\"id\":5}\n"));
            tester.sendMessage("{\"id\":6,\"method\":\"Debugger.setBreakpointOnFunctionCall\",\"params\":{\"objectId\":\"" + (objectId++) + "\"}}");
            assertTrue(tester.compareReceivedMessages(
                            "{\"result\":{\"breakpointId\":\"1\"},\"id\":6}\n"));
        }
        tester.sendMessage("{\"id\":7,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":7}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[\"1\"]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"" + (objectId++) + "\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"" + (objectId++) + "\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"" + (objectId++) + "\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":3}," +
                                                 "\"url\":\"" + slTestURI + "\"}]}}\n"));
        if (useConsoleUtilities) {
            tester.sendMessage("{\"id\":8,\"method\":\"Debugger.evaluateOnCallFrame\",\"params\":{\"callFrameId\":\"0\",\"expression\":\"undebug(isNull)\",\"objectGroup\":\"console\",\"includeCommandLineAPI\":true,\"silent\":false,\"returnByValue\":false,\"generatePreview\":true}}");
            assertTrue(tester.compareReceivedMessages(
                            "{\"result\":{},\"id\":8}\n"));
        } else {
            tester.sendMessage("{\"id\":8,\"method\":\"Debugger.removeBreakpoint\",\"params\":{\"breakpointId\":\"1\"}}");
            assertTrue(tester.compareReceivedMessages(
                            "{\"result\":{},\"id\":8}\n"));
        }
        // Resume to finish:
        tester.sendMessage("{\"id\":10,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":10}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        tester.finish();
    }

    @Test
    public void testConsoleUtilitiesDebugUndebug() throws Exception {
        testGuestFunctionBreakpoints(true);
        testBuiltInFunctionBreakpoints(true);
    }

    @Test
    public void testScopes() throws Exception {
        tester = InspectorTester.start(true);
        Source source = Source.newBuilder("sl", CODE1, "SLTest.sl").build();
        String slTestURI = InspectorTester.getStringURI(source.getURI());
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        tester.sendMessage("{\"id\":2,\"method\":\"Debugger.enable\"}");
        tester.sendMessage("{\"id\":3,\"method\":\"Runtime.runIfWaitingForDebugger\"}");
        assertTrue(tester.compareReceivedMessages("{\"result\":{},\"id\":1}\n" +
                        "{\"result\":{},\"id\":2}\n" +
                        "{\"result\":{},\"id\":3}\n" +
                        "{\"method\":\"Runtime.executionContextCreated\",\"params\":{\"context\":{\"origin\":\"\",\"name\":\"test\",\"id\":1}}}\n"));
        tester.eval(source);
        long id = tester.getContextId();

        // Suspend at the beginning of the script:
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":0,\"scriptId\":\"0\",\"endColumn\":0,\"startColumn\":0,\"startLine\":0,\"length\":0,\"executionContextId\":" + id + ",\"url\":\"" + SL_BUILTIN_URI + "\",\"hash\":\"ffffffffffffffffffffffffffffffffffffffff\"}}\n" +
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":18,\"scriptId\":\"1\",\"endColumn\":1,\"startColumn\":0,\"startLine\":0,\"length\":245,\"executionContextId\":" + id + ",\"url\":\"" + slTestURI + "\",\"hash\":\"f8058ed0f3c2f0acf3e37e59f953127afdba90e5\"}}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"1\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"2\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"3\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":1}," +
                                                 "\"url\":\"" + slTestURI + "\"}]}}\n"));
        // Ask for the local scope variables at the beginning of main:
        tester.sendMessage("{\"id\":5,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"1\"}}");
        assertEquals("{\"result\":{\"result\":[],\"internalProperties\":[]},\"id\":5}", tester.getMessages(true).trim());

        // Continue to line 4:
        tester.sendMessage("{\"id\":6,\"method\":\"Debugger.continueToLocation\",\"params\":{\"location\":{\"scriptId\":\"1\",\"lineNumber\":4,\"columnNumber\":0}}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":6}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"4\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"5\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"6\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":4,\"lineNumber\":4}," +
                                                 "\"url\":\"" + slTestURI + "\"}]}}\n"));
        tester.sendMessage("{\"id\":7,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"4\"}}");
        assertEquals("{\"result\":{\"result\":[{\"isOwn\":true,\"enumerable\":true,\"name\":\"a\",\"value\":{\"description\":\"10\",\"type\":\"number\",\"value\":10},\"configurable\":true,\"writable\":true}," +
                                              "{\"isOwn\":true,\"enumerable\":true,\"name\":\"b\",\"value\":{\"description\":\"2\",\"type\":\"number\",\"value\":2},\"configurable\":true,\"writable\":true}],\"internalProperties\":[]},\"id\":7}",
                        tester.getMessages(true).trim());
        // Step over:
        tester.sendMessage("{\"id\":8,\"method\":\"Debugger.stepOver\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":8}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"block\",\"type\":\"block\",\"object\":{\"description\":\"block\",\"type\":\"object\",\"objectId\":\"7\"}}," +
                                                                 "{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"8\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"9\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"10\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":4,\"lineNumber\":5}," +
                                                 "\"url\":\"" + slTestURI + "\"}]}}\n"));
        tester.sendMessage("{\"id\":9,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"7\"}}");
        tester.sendMessage("{\"id\":10,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"8\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":[{\"isOwn\":true,\"enumerable\":true,\"name\":\"c\",\"value\":{\"description\":\"12\",\"type\":\"number\",\"value\":12},\"configurable\":true,\"writable\":true}],\"internalProperties\":[]},\"id\":9}\n" +
                        "{\"result\":{\"result\":[{\"isOwn\":true,\"enumerable\":true,\"name\":\"a\",\"value\":{\"description\":\"10\",\"type\":\"number\",\"value\":10},\"configurable\":true,\"writable\":true}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"b\",\"value\":{\"description\":\"2\",\"type\":\"number\",\"value\":2},\"configurable\":true,\"writable\":true}],\"internalProperties\":[]},\"id\":10}\n"));

        // Resume to finish:
        tester.sendMessage("{\"id\":45,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":45}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        tester.finish();
    }

    @Test
    public void testNotSuspended() throws Exception {
        tester = InspectorTester.start(false);
        Source source = Source.newBuilder("sl", CODE1, "SLTest.sl").build();
        String slTestURI = InspectorTester.getStringURI(source.getURI());
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        tester.sendMessage("{\"id\":2,\"method\":\"Debugger.enable\"}");
        tester.sendMessage("{\"id\":3,\"method\":\"Runtime.runIfWaitingForDebugger\"}");
        assertTrue(tester.compareReceivedMessages("{\"result\":{},\"id\":1}\n" +
                        "{\"result\":{},\"id\":2}\n" +
                        "{\"result\":{},\"id\":3}\n" +
                        "{\"method\":\"Runtime.executionContextCreated\",\"params\":{\"context\":{\"origin\":\"\",\"name\":\"test\",\"id\":1}}}\n"));
        tester.eval(source);
        long id = tester.getContextId();

        // Executed, no suspend
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":0,\"scriptId\":\"0\",\"endColumn\":0,\"startColumn\":0,\"startLine\":0,\"length\":0,\"executionContextId\":" + id + ",\"url\":\"" + SL_BUILTIN_URI + "\",\"hash\":\"ffffffffffffffffffffffffffffffffffffffff\"}}\n" +
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":18,\"scriptId\":\"1\",\"endColumn\":1,\"startColumn\":0,\"startLine\":0,\"length\":245,\"executionContextId\":" + id + ",\"url\":\"" + slTestURI + "\",\"hash\":\"f8058ed0f3c2f0acf3e37e59f953127afdba90e5\"}}\n"));
        // Try to compile a script:
        tester.sendMessage("{\"id\":3,\"method\":\"Runtime.compileScript\",\"params\":{\"expression\":\"app\",\"sourceURL\":\"\",\"persistScript\":false,\"executionContextId\":" + id + "}}");
        assertEquals("{\"result\":{\"exceptionDetails\":{\"exception\":{\"description\":\"<Not suspended>\",\"type\":\"string\",\"value\":\"<Not suspended>\"},\"exceptionId\":1,\"executionContextId\":1,\"text\":\"Caught\"}},\"id\":3}", tester.getMessages(true).trim());
        // Try to evaluate:
        tester.sendMessage("{\"id\":4,\"method\":\"Runtime.evaluate\",\"params\":{\"expression\":\"app\",\"objectGroup\":\"watch-group\",\"includeCommandLineAPI\":false,\"silent\":true,\"contextId\":" + id + "}}");
        assertEquals("{\"result\":{\"exceptionDetails\":{\"exception\":{\"description\":\"<Not suspended>\",\"type\":\"string\",\"value\":\"<Not suspended>\"},\"exceptionId\":2,\"executionContextId\":1,\"text\":\"Caught\"}},\"id\":4}", tester.getMessages(true).trim());
        tester.finish();
    }

    @Test
    public void testNoInternalSources() throws Exception {
        tester = InspectorTester.start(true);
        String internFunction = "function intern(n) {\n" +
                        "  if (n > 0) {\n" +
                        "    return public(n);\n" +
                        "  } else {\n" +
                        "    if (n == 0) {\n" +
                        "      return 42;\n" +
                        "    } else {\n" +
                        "      return intern(0 - n);\n" +
                        "    }\n" +
                        "  }\n" +
                        "}\n";
        Source internSource = Source.newBuilder("sl", internFunction, "InternalFunc.sl").internal(true).build();
        String publicFunction = "function public(n) {\n" +
                        "  if (n > 0) {\n" +
                        "    return 2 * n;\n" +
                        "  } else {\n" +
                        "    if (n == 0) {\n" +
                        "      return public(0 - 42);\n" +
                        "    } else {\n" +
                        "      return intern(10 * n);\n" +
                        "    }\n" +
                        "  }\n" +
                        "}\n";
        Source publicSource = Source.newBuilder("sl", publicFunction, "PublicFunc.sl").internal(false).build();
        String mainFunction = "function main() {\n" +
                        "  a = intern(1);\n" +
                        "  b = public(0 - 1);\n" +
                        "  c = intern(0);\n" +
                        "}\n";
        Source publicMain = Source.newBuilder("sl", mainFunction, "PublicMain.sl").internal(false).build();
        Source internMain = Source.newBuilder("sl", mainFunction, "InternMain.sl").internal(true).build();
        String publicSourceURI = InspectorTester.getStringURI(publicSource.getURI());
        String publicMainURI = InspectorTester.getStringURI(publicMain.getURI());
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        tester.sendMessage("{\"id\":2,\"method\":\"Debugger.enable\"}");
        tester.sendMessage("{\"id\":3,\"method\":\"Runtime.runIfWaitingForDebugger\"}");
        assertTrue(tester.compareReceivedMessages("{\"result\":{},\"id\":1}\n" +
                        "{\"result\":{},\"id\":2}\n" +
                        "{\"result\":{},\"id\":3}\n" +
                        "{\"method\":\"Runtime.executionContextCreated\",\"params\":{\"context\":{\"origin\":\"\",\"name\":\"test\",\"id\":1}}}\n"));
        long id = tester.getContextId();
        tester.eval(internSource);
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":0,\"scriptId\":\"0\",\"endColumn\":0,\"startColumn\":0,\"startLine\":0,\"length\":0,\"executionContextId\":" + id + ",\"url\":\"" + SL_BUILTIN_URI + "\",\"hash\":\"ffffffffffffffffffffffffffffffffffffffff\"}}\n"));
        // No scriptParsed message for the interanl source
        tester.eval(publicSource);
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":10,\"scriptId\":\"1\",\"endColumn\":1,\"startColumn\":0,\"startLine\":0,\"length\":166,\"executionContextId\":" + id + ",\"url\":\"" + publicSourceURI + "\",\"hash\":\"f16f032ee222dcfdfc01da4bfd731e49fc671217\"}}\n"));
        tester.eval(internMain);
        // at public:1 (main -> intern -> public)
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"public\"," +
                                                 "\"scopeChain\":[{\"name\":\"public\",\"type\":\"local\",\"object\":{\"description\":\"public\",\"type\":\"object\",\"objectId\":\"1\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"2\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"3\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":6,\"lineNumber\":1}," +
                                                 "\"url\":\"" + publicSourceURI + "\"}]}}\n"));
        tester.sendMessage("{\"id\":4,\"method\":\"Debugger.stepOver\"}");
        // at public:2
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":4}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"public\"," +
                                                 "\"scopeChain\":[{\"name\":\"public\",\"type\":\"local\",\"object\":{\"description\":\"public\",\"type\":\"object\",\"objectId\":\"4\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"5\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"6\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":4,\"lineNumber\":2}," +
                                                 "\"url\":\"" + publicSourceURI + "\"}]}}\n"));
        tester.sendMessage("{\"id\":5,\"method\":\"Debugger.stepOver\"}");
        // at return from public:10
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":5}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"returnValue\":{\"description\":\"2\",\"type\":\"number\",\"value\":2},\"functionName\":\"public\"," +
                                                 "\"scopeChain\":[{\"name\":\"public\",\"type\":\"local\",\"object\":{\"description\":\"public\",\"type\":\"object\",\"objectId\":\"7\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"8\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"9\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":0,\"lineNumber\":10}," +
                                                 "\"url\":\"" + publicSourceURI + "\"}]}}\n"));
        tester.sendMessage("{\"id\":51,\"method\":\"Debugger.stepOver\"}");
        // at public:1 again (main -> public)
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":51}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"public\"," +
                                                 "\"scopeChain\":[{\"name\":\"public\",\"type\":\"local\",\"object\":{\"description\":\"public\",\"type\":\"object\",\"objectId\":\"10\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"11\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"12\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":6,\"lineNumber\":1}," +
                                                 "\"url\":\"" + publicSourceURI + "\"}]}}\n"));
        tester.sendMessage("{\"id\":6,\"method\":\"Debugger.stepOver\"}");
        // at public:4
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":6}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"public\"," +
                                                 "\"scopeChain\":[{\"name\":\"public\",\"type\":\"local\",\"object\":{\"description\":\"public\",\"type\":\"object\",\"objectId\":\"13\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"14\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"15\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":8,\"lineNumber\":4}," +
                                                 "\"url\":\"" + publicSourceURI + "\"}]}}\n"));
        tester.sendMessage("{\"id\":7,\"method\":\"Debugger.stepOver\"}");
        // at public:7
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":7}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"public\"," +
                                                 "\"scopeChain\":[{\"name\":\"public\",\"type\":\"local\",\"object\":{\"description\":\"public\",\"type\":\"object\",\"objectId\":\"16\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"17\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"18\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":6,\"lineNumber\":7}," +
                                                 "\"url\":\"" + publicSourceURI + "\"}]}}\n"));
        tester.sendMessage("{\"id\":8,\"method\":\"Debugger.stepInto\"}");
        // at public:1 (main -> public -> intern -> intern -> public)
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":8}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"public\"," +
                                                 "\"scopeChain\":[{\"name\":\"public\",\"type\":\"local\",\"object\":{\"description\":\"public\",\"type\":\"object\",\"objectId\":\"19\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"20\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"21\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":6,\"lineNumber\":1}," +
                                                 "\"url\":\"" + publicSourceURI + "\"}," +
                                                "{\"callFrameId\":\"1\",\"functionName\":\"public\"," +
                                                 "\"scopeChain\":[{\"name\":\"public\",\"type\":\"local\",\"object\":{\"description\":\"public\",\"type\":\"object\",\"objectId\":\"22\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"23\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"24\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":13,\"lineNumber\":7}," +
                                                 "\"url\":\"" + publicSourceURI + "\"}]}}\n"));
        tester.sendMessage("{\"id\":9,\"method\":\"Debugger.stepOver\"}");
        // at public:2
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":9}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"public\"," +
                                                 "\"scopeChain\":[{\"name\":\"public\",\"type\":\"local\",\"object\":{\"description\":\"public\",\"type\":\"object\",\"objectId\":\"25\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"26\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"27\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":4,\"lineNumber\":2}," +
                                                 "\"url\":\"" + publicSourceURI + "\"}," +
                                                "{\"callFrameId\":\"1\",\"functionName\":\"public\"," +
                                                 "\"scopeChain\":[{\"name\":\"public\",\"type\":\"local\",\"object\":{\"description\":\"public\",\"type\":\"object\",\"objectId\":\"28\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"29\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"30\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":13,\"lineNumber\":7}," +
                                                 "\"url\":\"" + publicSourceURI + "\"}]}}\n"));
        tester.sendMessage("{\"id\":10,\"method\":\"Debugger.stepOver\"}");
        // at return from public:10
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":10}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"returnValue\":{\"description\":\"20\",\"type\":\"number\",\"value\":20},\"functionName\":\"public\"," +
                                                 "\"scopeChain\":[{\"name\":\"public\",\"type\":\"local\",\"object\":{\"description\":\"public\",\"type\":\"object\",\"objectId\":\"31\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"32\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"33\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":0,\"lineNumber\":10}," +
                                                 "\"url\":\"" + publicSourceURI + "\"}," +
                                                "{\"callFrameId\":\"1\",\"functionName\":\"public\"," +
                                                 "\"scopeChain\":[{\"name\":\"public\",\"type\":\"local\",\"object\":{\"description\":\"public\",\"type\":\"object\",\"objectId\":\"34\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"35\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"36\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":13,\"lineNumber\":7}," +
                                                 "\"url\":\"" + publicSourceURI + "\"}]}}\n"));
        tester.sendMessage("{\"id\":11,\"method\":\"Debugger.stepOver\"}");
        // back at public:7 after call to intern()
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":11}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"public\"," +
                                                 "\"scopeChain\":[{\"name\":\"public\",\"type\":\"local\",\"object\":{\"description\":\"public\",\"type\":\"object\",\"objectId\":\"37\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"38\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"39\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":26,\"lineNumber\":7}," +
                                                 "\"url\":\"" + publicSourceURI + "\"}," +
                                                "{\"callFrameId\":\"1\",\"functionName\":\"public\"," +
                                                 "\"scopeChain\":[{\"name\":\"public\",\"type\":\"local\",\"object\":{\"description\":\"public\",\"type\":\"object\",\"objectId\":\"40\"}}," +
                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"41\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"42\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":13,\"lineNumber\":7}," +
                                                 "\"url\":\"" + publicSourceURI + "\"}]}}\n"));
        tester.sendMessage("{\"id\":12,\"method\":\"Debugger.stepInto\"}");
        // at return from public:10
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":12}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"returnValue\":{\"description\":\"20\",\"type\":\"number\",\"value\":20},\"functionName\":\"public\"," +
                                                 "\"scopeChain\":[{\"name\":\"public\",\"type\":\"local\",\"object\":{\"description\":\"public\",\"type\":\"object\",\"objectId\":\"43\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"44\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"45\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":0,\"lineNumber\":10}," +
                                                 "\"url\":\"" + publicSourceURI + "\"}]}}\n"));
        tester.sendMessage("{\"id\":13,\"method\":\"Debugger.stepInto\"}");
        // No more suspension (we're in internal sources)
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":13}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        assertEquals("", tester.getMessages(false));

        tester.eval(publicMain);
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":4,\"scriptId\":\"2\",\"endColumn\":1,\"startColumn\":0,\"startLine\":0,\"length\":75,\"executionContextId\":" + id + ",\"url\":\"" + publicMainURI + "\",\"hash\":\"f9120a07f176a91df047f02ffe94b4d3ff485978\"}}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"46\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"47\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"48\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"2\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"2\",\"columnNumber\":2,\"lineNumber\":1}," +
                                                 "\"url\":\"" + publicMainURI + "\"}]}}\n"));
        tester.sendMessage("{\"id\":14,\"method\":\"Debugger.stepInto\"}");
        // at public:1 (main -> internal -> public)
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":14}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"public\"," +
                                                 "\"scopeChain\":[{\"name\":\"public\",\"type\":\"local\",\"object\":{\"description\":\"public\",\"type\":\"object\",\"objectId\":\"49\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"50\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"51\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":6,\"lineNumber\":1}," +
                                                 "\"url\":\"" + publicSourceURI + "\"}," +
                                                "{\"callFrameId\":\"1\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"52\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"53\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"54\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"2\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"2\",\"columnNumber\":6,\"lineNumber\":1}," +
                                                 "\"url\":\"" + publicMainURI + "\"}]}}\n"));

        // Resume to finish:
        tester.sendMessage("{\"id\":45,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":45}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        tester.finish();
    }

    @Test
    public void testNoBlackboxedSources() throws Exception {
        tester = InspectorTester.start(true);
        String blackboxedFunction = "function black(n) {\n" +
                        "  if (n > 0) {\n" +
                        "    return public(n);\n" +
                        "  } else {\n" +
                        "    if (n == 0) {\n" +
                        "      return 42;\n" +
                        "    } else {\n" +
                        "      return black(0 - n);\n" +
                        "    }\n" +
                        "  }\n" +
                        "}\n";
        Source blackboxedSource = Source.newBuilder("sl", blackboxedFunction, "BlackboxedFunc.sl").internal(false).build();
        String publicFunction = "function public(n) {\n" +
                        "  if (n > 0) {\n" +
                        "    return 2 * n;\n" +
                        "  } else {\n" +
                        "    if (n == 0) {\n" +
                        "      return public(0 - 42);\n" +
                        "    } else {\n" +
                        "      return black(10 * n);\n" +
                        "    }\n" +
                        "  }\n" +
                        "}\n";
        Source publicSource = Source.newBuilder("sl", publicFunction, "PublicFunc.sl").internal(false).build();
        String mainFunction = "function main() {\n" +
                        "  a = black(1);\n" +
                        "  b = public(0 - 1);\n" +
                        "  c = black(0);\n" +
                        "}\n";
        Source publicMain = Source.newBuilder("sl", mainFunction, "PublicMain.sl").internal(false).build();
        String blackboxedSourceURI = InspectorTester.getStringURI(blackboxedSource.getURI());
        String publicSourceURI = InspectorTester.getStringURI(publicSource.getURI());
        String publicMainURI = InspectorTester.getStringURI(publicMain.getURI());
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        tester.sendMessage("{\"id\":2,\"method\":\"Debugger.enable\"}");
        tester.sendMessage("{\"id\":3,\"method\":\"Debugger.setBlackboxPatterns\",\"params\":{\"patterns\":[\"BlackboxedFunc.sl\"]}}");
        tester.sendMessage("{\"id\":4,\"method\":\"Runtime.runIfWaitingForDebugger\"}");
        assertTrue(tester.compareReceivedMessages("{\"result\":{},\"id\":1}\n" +
                        "{\"result\":{},\"id\":2}\n" +
                        "{\"result\":{},\"id\":3}\n" +
                        "{\"result\":{},\"id\":4}\n" +
                        "{\"method\":\"Runtime.executionContextCreated\",\"params\":{\"context\":{\"origin\":\"\",\"name\":\"test\",\"id\":1}}}\n"));
        long id = tester.getContextId();
        tester.eval(blackboxedSource);
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":0,\"scriptId\":\"0\",\"endColumn\":0,\"startColumn\":0,\"startLine\":0,\"length\":0,\"executionContextId\":" + id + ",\"url\":\"" + SL_BUILTIN_URI + "\",\"hash\":\"ffffffffffffffffffffffffffffffffffffffff\"}}\n" +
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":10,\"scriptId\":\"1\",\"endColumn\":1,\"startColumn\":0,\"startLine\":0,\"length\":155,\"executionContextId\":" + id + ",\"url\":\"" + blackboxedSourceURI + "\",\"hash\":\"e6563ff5f01769c8f4f70d27f779b564fdaf5f20\"}}\n"));
        tester.eval(publicSource);
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":10,\"scriptId\":\"2\",\"endColumn\":1,\"startColumn\":0,\"startLine\":0,\"length\":165,\"executionContextId\":" + id + ",\"url\":\"" + publicSourceURI + "\",\"hash\":\"f16f032ee222dcfdfc01da4bfd731e49fc671217\"}}\n"));
        tester.eval(publicMain);
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":4,\"scriptId\":\"3\",\"endColumn\":1,\"startColumn\":0,\"startLine\":0,\"length\":73,\"executionContextId\":" + id + ",\"url\":\"" + publicMainURI + "\",\"hash\":\"f9120a07f176a91df047f02ffe94b4d3ff485978\"}}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"1\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"2\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"3\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"3\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"3\",\"columnNumber\":2,\"lineNumber\":1}," +
                                                 "\"url\":\"" + publicMainURI + "\"}]}}\n"));
        tester.sendMessage("{\"id\":4,\"method\":\"Debugger.stepInto\"}");
        // at public:1 (main -> black -> public)
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":4}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"public\"," +
                                                 "\"scopeChain\":[{\"name\":\"public\",\"type\":\"local\",\"object\":{\"description\":\"public\",\"type\":\"object\",\"objectId\":\"4\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"5\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"6\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"2\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"2\",\"columnNumber\":6,\"lineNumber\":1}," +
                                                 "\"url\":\"" + publicSourceURI + "\"}," +
                                                "{\"callFrameId\":\"1\",\"functionName\":\"black\"," +
                                                 "\"scopeChain\":[{\"name\":\"black\",\"type\":\"local\",\"object\":{\"description\":\"black\",\"type\":\"object\",\"objectId\":\"7\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"8\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"9\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":11,\"lineNumber\":2}," +
                                                 "\"url\":\"" + blackboxedSourceURI + "\"}," +
                                                "{\"callFrameId\":\"2\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"10\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"11\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"12\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"3\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"3\",\"columnNumber\":6,\"lineNumber\":1}," +
                                                 "\"url\":\"" + publicMainURI + "\"}]}}\n"));

        // Resume to finish:
        tester.sendMessage("{\"id\":45,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":45}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        tester.eval(publicMain);
        tester.finish();
    }

    @Test
    public void testSourceMatchesBlackboxPatterns() throws Exception {
        File tmp = Files.createTempDirectory("test").toFile();
        tmp.deleteOnExit();
        File parent = new File(tmp, "blackbox");
        parent.mkdir();
        parent.deleteOnExit();
        File file = new File(parent, "BlackboxTest.sl");
        file.createNewFile();
        file.deleteOnExit();

        Context context = Context.newBuilder().allowIO(true).build();
        context.initialize("sl");
        context.enter();
        TruffleFile truffleFile = SLLanguage.getCurrentContext().getEnv().getPublicTruffleFile(file.toPath().toString());
        com.oracle.truffle.api.source.Source source = com.oracle.truffle.api.source.Source.newBuilder("sl", truffleFile).build();

        tester = InspectorTester.start(false);
        InspectorDebugger debugger = (InspectorDebugger) tester.getDebugger();
        debugger.enable();
        // Test name of a file
        assertTrue(debugger.sourceMatchesBlackboxPatterns(source, new Pattern[] {Pattern.compile("BlackboxTest.sl")}));
        assertFalse(debugger.sourceMatchesBlackboxPatterns(source, new Pattern[] {Pattern.compile("Test.sl")}));

        // Test regular expression that contain a specific name
        assertTrue(debugger.sourceMatchesBlackboxPatterns(source, new Pattern[] {Pattern.compile("Test\\.sl$")}));
        assertFalse(debugger.sourceMatchesBlackboxPatterns(source, new Pattern[] {Pattern.compile("Fest\\.sl$")}));

        // Test regular expression that contain certain types of files
        assertTrue(debugger.sourceMatchesBlackboxPatterns(source, new Pattern[] {Pattern.compile("\\.sl$")}));
        assertFalse(debugger.sourceMatchesBlackboxPatterns(source, new Pattern[] {Pattern.compile("\\.ssl$")}));

        // Test entire folder that contains scripts you to blackbox
        assertTrue(debugger.sourceMatchesBlackboxPatterns(source, new Pattern[] {Pattern.compile("blackbox")}));
        assertFalse(debugger.sourceMatchesBlackboxPatterns(source, new Pattern[] {Pattern.compile("tmp")}));

        // Test regular expression produced by Chrome Inspector's 'Blackbox Script' action.
        assertTrue(debugger.sourceMatchesBlackboxPatterns(source, new Pattern[] {Pattern.compile("^file://.*/BlackboxTest\\.sl$")}));
        debugger.disable();
        debugger = null;

        truffleFile = null;
        context.leave();
        context.close();
        context = null;
        tester.finish();
    }

    @Test
    public void testRestartFrame() throws Exception {
        tester = InspectorTester.start(false);
        Source source = Source.newBuilder("sl", CODE3, "SLTest.sl").build();
        String slTestURI = InspectorTester.getStringURI(source.getURI());
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        tester.sendMessage("{\"id\":2,\"method\":\"Debugger.enable\"}");
        tester.sendMessage("{\"id\":3,\"method\":\"Runtime.runIfWaitingForDebugger\"}");
        String srcURL = InspectorTester.getStringURI(source.getURI());
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":1}\n" +
                        "{\"result\":{},\"id\":2}\n" +
                        "{\"result\":{},\"id\":3}\n" +
                        "{\"method\":\"Runtime.executionContextCreated\",\"params\":{\"context\":{\"origin\":\"\",\"name\":\"test\",\"id\":1}}}\n"));
        tester.sendMessage("{\"id\":3,\"method\":\"Debugger.setBreakpointByUrl\",\"params\":{\"lineNumber\":6,\"url\":\"" + srcURL + "\",\"columnNumber\":0,\"condition\":\"\"}}");
        assertEquals("{\"result\":{\"breakpointId\":\"1\",\"locations\":[]},\"id\":3}", tester.getMessages(true).trim());

        tester.eval(source);
        long id = tester.getContextId();
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":0,\"scriptId\":\"0\",\"endColumn\":0,\"startColumn\":0,\"startLine\":0,\"length\":0,\"executionContextId\":" + id + ",\"url\":\"" + SL_BUILTIN_URI + "\",\"hash\":\"ffffffffffffffffffffffffffffffffffffffff\"}}\n" +
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":11,\"scriptId\":\"1\",\"endColumn\":1,\"startColumn\":0,\"startLine\":0,\"length\":159,\"executionContextId\":" + id + ",\"url\":\"" + slTestURI + "\",\"hash\":\"fb16cf53fe350d97fc01da4bfc63d942ff7395eb\"}}\n" +
                        "{\"method\":\"Debugger.breakpointResolved\",\"params\":{\"breakpointId\":\"1\",\"location\":{\"scriptId\":\"1\",\"columnNumber\":4,\"lineNumber\":6}}}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[\"1\"]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"factorial\"," +
                                                 "\"scopeChain\":[{\"name\":\"factorial\",\"type\":\"local\",\"object\":{\"description\":\"factorial\",\"type\":\"object\",\"objectId\":\"1\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"2\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"3\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":4}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":4,\"lineNumber\":6}," +
                                                 "\"url\":\"" + srcURL + "\"}," +
                                                "{\"callFrameId\":\"1\",\"functionName\":\"factorial\"," +
                                                 "\"scopeChain\":[{\"name\":\"factorial\",\"type\":\"local\",\"object\":{\"description\":\"factorial\",\"type\":\"object\",\"objectId\":\"4\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"5\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"6\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":4}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":12,\"lineNumber\":8}," +
                                                 "\"url\":\"" + srcURL + "\"}," +
                                                "{\"callFrameId\":\"2\",\"functionName\":\"factorial\"," +
                                                 "\"scopeChain\":[{\"name\":\"factorial\",\"type\":\"local\",\"object\":{\"description\":\"factorial\",\"type\":\"object\",\"objectId\":\"7\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"8\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"9\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":4}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":12,\"lineNumber\":8}," +
                                                 "\"url\":\"" + srcURL + "\"}," +
                                                "{\"callFrameId\":\"3\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"10\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"11\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"12\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":2}," +
                                                 "\"url\":\"" + srcURL + "\"}]}}\n"));
        // Restart frame
        tester.sendMessage("{\"id\":4,\"method\":\"Debugger.restartFrame\",\"params\":{\"callFrameId\":\"0\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{" +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"factorial\"," +
                                                 "\"scopeChain\":[{\"name\":\"factorial\",\"type\":\"local\",\"object\":{\"description\":\"factorial\",\"type\":\"object\",\"objectId\":\"13\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"14\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"15\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":4}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":27,\"lineNumber\":8}," +
                                                 "\"url\":\"" + srcURL + "\"}," +
                                                "{\"callFrameId\":\"1\",\"functionName\":\"factorial\"," +
                                                 "\"scopeChain\":[{\"name\":\"factorial\",\"type\":\"local\",\"object\":{\"description\":\"factorial\",\"type\":\"object\",\"objectId\":\"16\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"17\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"18\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":4}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":12,\"lineNumber\":8}," +
                                                 "\"url\":\"" + srcURL + "\"}," +
                                                "{\"callFrameId\":\"2\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"19\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"20\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"21\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":2}," +
                                                 "\"url\":\"" + srcURL + "\"}]}," +
                        "\"id\":4}\n"));
        tester.sendMessage("{\"id\":5,\"method\":\"Debugger.restartFrame\",\"params\":{\"callFrameId\":\"1\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{" +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"22\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"23\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"24\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":13,\"lineNumber\":2}," +
                                                 "\"url\":\"" + srcURL + "\"}]}," +
                        "\"id\":5}\n"));
        tester.sendMessage("{\"id\":6,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":6}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        // Breakpoint hit again:
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[\"1\"]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"factorial\"," +
                                                 "\"scopeChain\":[{\"name\":\"factorial\",\"type\":\"local\",\"object\":{\"description\":\"factorial\",\"type\":\"object\",\"objectId\":\"25\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"26\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"27\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":4}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":4,\"lineNumber\":6}," +
                                                 "\"url\":\"" + srcURL + "\"}," +
                                                "{\"callFrameId\":\"1\",\"functionName\":\"factorial\"," +
                                                 "\"scopeChain\":[{\"name\":\"factorial\",\"type\":\"local\",\"object\":{\"description\":\"factorial\",\"type\":\"object\",\"objectId\":\"28\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"29\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"30\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":4}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":12,\"lineNumber\":8}," +
                                                 "\"url\":\"" + srcURL + "\"}," +
                                                "{\"callFrameId\":\"2\",\"functionName\":\"factorial\"," +
                                                 "\"scopeChain\":[{\"name\":\"factorial\",\"type\":\"local\",\"object\":{\"description\":\"factorial\",\"type\":\"object\",\"objectId\":\"31\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"32\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"33\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":4}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":12,\"lineNumber\":8}," +
                                                 "\"url\":\"" + srcURL + "\"}," +
                                                "{\"callFrameId\":\"3\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"34\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"35\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"36\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":2}," +
                                                 "\"url\":\"" + srcURL + "\"}]}}\n"));
        // Resume to finish:
        tester.sendMessage("{\"id\":10,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":10}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        tester.finish();
    }

    @Test
    public void testReturnValue() throws Exception {
        tester = InspectorTester.start(true);
        Source source = Source.newBuilder("sl", CODE_RET_VAL, "SLTest.sl").build();
        String slTestURI = InspectorTester.getStringURI(source.getURI());
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        tester.sendMessage("{\"id\":2,\"method\":\"Debugger.enable\"}");
        tester.sendMessage("{\"id\":3,\"method\":\"Runtime.runIfWaitingForDebugger\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":1}\n" +
                        "{\"result\":{},\"id\":2}\n" +
                        "{\"result\":{},\"id\":3}\n" +
                        "{\"method\":\"Runtime.executionContextCreated\",\"params\":{\"context\":{\"origin\":\"\",\"name\":\"test\",\"id\":1}}}\n"));
        tester.eval(source);
        long id = tester.getContextId();
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":0,\"scriptId\":\"0\",\"endColumn\":0,\"startColumn\":0,\"startLine\":0,\"length\":0,\"executionContextId\":" + id + ",\"url\":\"" + SL_BUILTIN_URI + "\",\"hash\":\"ffffffffffffffffffffffffffffffffffffffff\"}}\n" +
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":12,\"scriptId\":\"1\",\"endColumn\":1,\"startColumn\":0,\"startLine\":0,\"length\":156,\"executionContextId\":" + id + ",\"url\":\"" + slTestURI + "\",\"hash\":\"f93e5981e515882df4d95e82ff610573fd2458f0\"}}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"1\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"2\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"3\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":1}," +
                                                 "\"url\":\"" + slTestURI + "\"}]}}\n"));
        // at main:1
        tester.sendMessage("{\"id\":4,\"method\":\"Debugger.stepInto\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":4}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"addThem\"," +
                                                 "\"scopeChain\":[{\"name\":\"addThem\",\"type\":\"local\",\"object\":{\"description\":\"addThem\",\"type\":\"object\",\"objectId\":\"4\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"5\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"6\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":4}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":5}," +
                                                 "\"url\":\"" + slTestURI + "\"}," +
                                                "{\"callFrameId\":\"1\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"7\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"8\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"9\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":6,\"lineNumber\":1}," +
                                                 "\"url\":\"" + slTestURI + "\"}]}}\n"));
        // at addThem:4
        tester.sendMessage("{\"id\":5,\"method\":\"Debugger.stepInto\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":5}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"fn\"," +
                                                 "\"scopeChain\":[{\"name\":\"fn\",\"type\":\"local\",\"object\":{\"description\":\"fn\",\"type\":\"object\",\"objectId\":\"10\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"11\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"12\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":10}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":11}," +
                                                 "\"url\":\"" + slTestURI + "\"}," +
                                                "{\"callFrameId\":\"1\",\"functionName\":\"addThem\"," +
                                                 "\"scopeChain\":[{\"name\":\"addThem\",\"type\":\"local\",\"object\":{\"description\":\"addThem\",\"type\":\"object\",\"objectId\":\"13\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"14\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"15\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":4}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":6,\"lineNumber\":5}," +
                                                 "\"url\":\"" + slTestURI + "\"}," +
                                                "{\"callFrameId\":\"2\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"16\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"17\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"18\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":6,\"lineNumber\":1}," +
                                                 "\"url\":\"" + slTestURI + "\"}]}}\n"));
        // at fn:10, step into steps to the end of fn and a return value is accessible
        tester.sendMessage("{\"id\":6,\"method\":\"Debugger.stepInto\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":6}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"returnValue\":{\"description\":\"1\",\"type\":\"number\",\"value\":1},\"functionName\":\"fn\"," +
                                                 "\"scopeChain\":[{\"name\":\"fn\",\"type\":\"local\",\"object\":{\"description\":\"fn\",\"type\":\"object\",\"objectId\":\"19\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"20\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"21\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":10}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":0,\"lineNumber\":12}," +
                                                 "\"url\":\"" + slTestURI + "\"}," +
                                                "{\"callFrameId\":\"1\",\"functionName\":\"addThem\"," +
                                                 "\"scopeChain\":[{\"name\":\"addThem\",\"type\":\"local\",\"object\":{\"description\":\"addThem\",\"type\":\"object\",\"objectId\":\"22\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"23\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"24\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":4}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":6,\"lineNumber\":5}," +
                                                 "\"url\":\"" + slTestURI + "\"}," +
                                                "{\"callFrameId\":\"2\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"25\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"26\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"27\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":6,\"lineNumber\":1}," +
                                                 "\"url\":\"" + slTestURI + "\"}]}}\n"));
        // at fn:10, step into steps out from fn
        tester.sendMessage("{\"id\":7,\"method\":\"Debugger.stepInto\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":7}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"addThem\"," +
                                                 "\"scopeChain\":[{\"name\":\"addThem\",\"type\":\"local\",\"object\":{\"description\":\"addThem\",\"type\":\"object\",\"objectId\":\"28\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"29\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"30\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":4}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":10,\"lineNumber\":5}," +
                                                 "\"url\":\"" + slTestURI + "\"}," +
                                                "{\"callFrameId\":\"1\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"31\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"32\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"33\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":6,\"lineNumber\":1}," +
                                                 "\"url\":\"" + slTestURI + "\"}]}}\n"));
        // at addThem:4, change return value from 1 to 10_000_000_000 (it must be long because of SL)
        tester.sendMessage("{\"id\":8,\"method\":\"Debugger.setReturnValue\",\"params\":{\"newValue\":{\"type\":\"number\",\"value\":10000000000,\"description\":\"10\"}}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":8}\n"));
        tester.sendMessage("{\"id\":9,\"method\":\"Debugger.stepInto\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":9}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"addThem\"," +
                                                 "\"scopeChain\":[{\"name\":\"addThem\",\"type\":\"local\",\"object\":{\"description\":\"addThem\",\"type\":\"object\",\"objectId\":\"34\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"35\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"36\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":4}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":6}," +
                                                 "\"url\":\"" + slTestURI + "\"}," +
                                                "{\"callFrameId\":\"1\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"37\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"38\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"39\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":6,\"lineNumber\":1}," +
                                                 "\"url\":\"" + slTestURI + "\"}]}}\n"));
        // at addThem:5, check that `a` is 10_000_000_000
        tester.sendMessage("{\"id\":10,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"34\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":[{\"isOwn\":true,\"enumerable\":true,\"name\":\"a\",\"value\":{\"description\":\"10000000000\",\"type\":\"number\",\"value\":10000000000},\"configurable\":true,\"writable\":true}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"b\",\"value\":{\"description\":\"2\",\"type\":\"number\",\"value\":2},\"configurable\":true,\"writable\":true}],\"internalProperties\":[]},\"id\":10}\n"));
        tester.sendMessage("{\"id\":10,\"method\":\"Debugger.stepInto\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":10}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"fn\"," +
                                                 "\"scopeChain\":[{\"name\":\"fn\",\"type\":\"local\",\"object\":{\"description\":\"fn\",\"type\":\"object\",\"objectId\":\"40\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"41\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"42\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":10}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":11}," +
                                                 "\"url\":\"" + slTestURI + "\"}," +
                                                "{\"callFrameId\":\"1\",\"functionName\":\"addThem\"," +
                                                 "\"scopeChain\":[{\"name\":\"addThem\",\"type\":\"local\",\"object\":{\"description\":\"addThem\",\"type\":\"object\",\"objectId\":\"43\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"44\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"45\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":4}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":6,\"lineNumber\":6}," +
                                                 "\"url\":\"" + slTestURI + "\"}," +
                                                "{\"callFrameId\":\"2\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"46\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"47\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"48\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":6,\"lineNumber\":1}," +
                                                 "\"url\":\"" + slTestURI + "\"}]}}\n"));
        // at fn:10, step out from fn
        tester.sendMessage("{\"id\":11,\"method\":\"Debugger.stepOut\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":11}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"addThem\"," +
                                                 "\"scopeChain\":[{\"name\":\"addThem\",\"type\":\"local\",\"object\":{\"description\":\"addThem\",\"type\":\"object\",\"objectId\":\"49\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"50\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"51\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":4}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":10,\"lineNumber\":6}," +
                                                 "\"url\":\"" + slTestURI + "\"}," +
                                                "{\"callFrameId\":\"1\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"52\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"53\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"54\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":6,\"lineNumber\":1}," +
                                                 "\"url\":\"" + slTestURI + "\"}]}}\n"));
        // at addThem:5, change return value from 2 to 20_000_000_000 (it must be long because of SL)
        tester.sendMessage("{\"id\":12,\"method\":\"Debugger.setReturnValue\",\"params\":{\"newValue\":{\"type\":\"number\",\"value\":20000000000,\"description\":\"20000000000\"}}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":12}\n"));
        // Resume to finish:
        tester.sendMessage("{\"id\":10,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":10}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        // And check the result value printed out
        tester.receiveMessages(
                        "{\"method\":\"Runtime.consoleAPICalled\",\"params\":{\"args\":[{\"type\":\"string\",\"value\":\"30000000000\"}],\"executionContextId\":" + id + ",\"type\":\"log\",\"timestamp\":",
                        "}}\n");
        tester.finish();
    }

    @Test
    public void testBreakpointCorrections() throws Exception {
        tester = InspectorTester.start(true);
        Source source = Source.newBuilder("sl", CODE4, "SLTest.sl").build();
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        tester.sendMessage("{\"id\":2,\"method\":\"Debugger.enable\"}");
        tester.sendMessage("{\"id\":3,\"method\":\"Runtime.runIfWaitingForDebugger\"}");
        String srcURL = InspectorTester.getStringURI(source.getURI());
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":1}\n" +
                        "{\"result\":{},\"id\":2}\n" +
                        "{\"result\":{},\"id\":3}\n" +
                        "{\"method\":\"Runtime.executionContextCreated\",\"params\":{\"context\":{\"origin\":\"\",\"name\":\"test\",\"id\":1}}}\n"));

        tester.eval(source);
        long id = tester.getContextId();
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":0,\"scriptId\":\"0\",\"endColumn\":0,\"startColumn\":0,\"startLine\":0,\"length\":0,\"executionContextId\":" + id + ",\"url\":\"" + SL_BUILTIN_URI + "\",\"hash\":\"ffffffffffffffffffffffffffffffffffffffff\"}}\n" +
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":13,\"scriptId\":\"1\",\"endColumn\":1,\"startColumn\":0,\"startLine\":0,\"length\":160,\"executionContextId\":" + id + ",\"url\":\"" + srcURL + "\",\"hash\":\"ee148976fc7d6f36fc01da4bff17f9a1fcb5f8ed\"}}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"1\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"2\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"3\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":1}," +
                                                 "\"url\":\"" + srcURL + "\"}]}}\n"));

        // Breakpoint before any statements moves to the first statement
        tester.sendMessage("{\"id\":3,\"method\":\"Debugger.setBreakpointByUrl\",\"params\":{\"lineNumber\":5,\"url\":\"" + srcURL + "\",\"columnNumber\":0,\"condition\":\"\"}}");
        assertEquals("{\"result\":{\"breakpointId\":\"1\",\"locations\":[{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":6}]},\"id\":3}", tester.getMessages(true).trim());
        // Breakpoint at the second line of a statement moves to the first line of the statement
        tester.sendMessage("{\"id\":4,\"method\":\"Debugger.setBreakpointByUrl\",\"params\":{\"lineNumber\":7,\"url\":\"" + srcURL + "\",\"columnNumber\":0,\"condition\":\"\"}}");
        assertEquals("{\"result\":{\"breakpointId\":\"2\",\"locations\":[{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":6}]},\"id\":4}", tester.getMessages(true).trim());
        // Breakpoint at the second line of a statement moves to the first line of the statement
        tester.sendMessage("{\"id\":5,\"method\":\"Debugger.setBreakpointByUrl\",\"params\":{\"lineNumber\":9,\"url\":\"" + srcURL + "\",\"columnNumber\":0,\"condition\":\"\"}}");
        assertEquals("{\"result\":{\"breakpointId\":\"3\",\"locations\":[{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":8}]},\"id\":5}", tester.getMessages(true).trim());
        // Breakpoint on an empty line moves to the next statement
        tester.sendMessage("{\"id\":6,\"method\":\"Debugger.setBreakpointByUrl\",\"params\":{\"lineNumber\":10,\"url\":\"" + srcURL + "\",\"columnNumber\":0,\"condition\":\"\"}}");
        assertEquals("{\"result\":{\"breakpointId\":\"4\",\"locations\":[{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":11}]},\"id\":6}", tester.getMessages(true).trim());
        // Breakpoint on a last empty line moves to the last statement
        tester.sendMessage("{\"id\":7,\"method\":\"Debugger.setBreakpointByUrl\",\"params\":{\"lineNumber\":12,\"url\":\"" + srcURL + "\",\"columnNumber\":0,\"condition\":\"\"}}");
        assertEquals("{\"result\":{\"breakpointId\":\"5\",\"locations\":[{\"scriptId\":\"1\",\"columnNumber\":24,\"lineNumber\":11}]},\"id\":7}", tester.getMessages(true).trim());

        // Remove some breakpoints
        tester.sendMessage("{\"id\":8,\"method\":\"Debugger.removeBreakpoint\",\"params\":{\"breakpointId\":\"3\"}}");
        assertEquals("{\"result\":{},\"id\":8}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":9,\"method\":\"Debugger.removeBreakpoint\",\"params\":{\"breakpointId\":\"4\"}}");
        assertEquals("{\"result\":{},\"id\":9}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":10,\"method\":\"Debugger.removeBreakpoint\",\"params\":{\"breakpointId\":\"5\"}}");
        assertEquals("{\"result\":{},\"id\":10}", tester.getMessages(true).trim());

        // Resume to hit some breakpoints
        tester.sendMessage("{\"id\":12,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":12}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[\"1\",\"2\"]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"testLocations\"," +
                                                 "\"scopeChain\":[{\"name\":\"testLocations\",\"type\":\"local\",\"object\":{\"description\":\"testLocations\",\"type\":\"object\",\"objectId\":\"4\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"5\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"6\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":4}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":6}," +
                                                 "\"url\":\"" + srcURL + "\"}," +
                                                "{\"callFrameId\":\"1\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"7\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"8\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"9\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":2}," +
                                                 "\"url\":\"" + srcURL + "\"}]}}\n"));

        // Resume to finish:
        tester.sendMessage("{\"id\":20,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":20}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        tester.finish();
    }

    @Test
    public void testPossibleBreakpoints() throws Exception {
        tester = InspectorTester.start(true);
        Source source = Source.newBuilder("sl", CODE4, "SLTest.sl").build();
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        tester.sendMessage("{\"id\":2,\"method\":\"Debugger.enable\"}");
        tester.sendMessage("{\"id\":3,\"method\":\"Runtime.runIfWaitingForDebugger\"}");
        String srcURL = InspectorTester.getStringURI(source.getURI());
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":1}\n" +
                        "{\"result\":{},\"id\":2}\n" +
                        "{\"result\":{},\"id\":3}\n" +
                        "{\"method\":\"Runtime.executionContextCreated\",\"params\":{\"context\":{\"origin\":\"\",\"name\":\"test\",\"id\":1}}}\n"));

        tester.eval(source);
        long id = tester.getContextId();
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":0,\"scriptId\":\"0\",\"endColumn\":0,\"startColumn\":0,\"startLine\":0,\"length\":0,\"executionContextId\":" + id + ",\"url\":\"" + SL_BUILTIN_URI + "\",\"hash\":\"ffffffffffffffffffffffffffffffffffffffff\"}}\n" +
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":13,\"scriptId\":\"1\",\"endColumn\":1,\"startColumn\":0,\"startLine\":0,\"length\":160,\"executionContextId\":" + id + ",\"url\":\"" + srcURL + "\",\"hash\":\"ee148976fc7d6f36fc01da4bff17f9a1fcb5f8ed\"}}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"1\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"2\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"3\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":1}," +
                                                 "\"url\":\"" + srcURL + "\"}]}}\n"));

        // Moves from an empty line to a statement location
        tester.sendMessage("{\"id\":3,\"method\":\"Debugger.getPossibleBreakpoints\",\"params\":{\"start\":{\"scriptId\":\"1\",\"lineNumber\":5,\"columnNumber\":0},\"end\":{\"scriptId\":\"1\",\"lineNumber\":5,\"columnNumber\":2},\"restrictToFunction\":false}}");
        assertEquals("{\"result\":{\"locations\":[{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":6}]},\"id\":3}", tester.getMessages(true).trim());
        // Provides statement location when only beginning is included
        tester.sendMessage("{\"id\":4,\"method\":\"Debugger.getPossibleBreakpoints\",\"params\":{\"start\":{\"scriptId\":\"1\",\"lineNumber\":8,\"columnNumber\":0},\"end\":{\"scriptId\":\"1\",\"lineNumber\":8,\"columnNumber\":5},\"restrictToFunction\":false}}");
        assertEquals("{\"result\":{\"locations\":[{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":8}]},\"id\":4}", tester.getMessages(true).trim());
        // Provides statement location when only end is included
        tester.sendMessage("{\"id\":5,\"method\":\"Debugger.getPossibleBreakpoints\",\"params\":{\"start\":{\"scriptId\":\"1\",\"lineNumber\":9,\"columnNumber\":0},\"end\":{\"scriptId\":\"1\",\"lineNumber\":9,\"columnNumber\":10},\"restrictToFunction\":false}}");
        assertEquals("{\"result\":{\"locations\":[{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":8}]},\"id\":5}", tester.getMessages(true).trim());
        // Provides all statement locations on a line
        tester.sendMessage("{\"id\":6,\"method\":\"Debugger.getPossibleBreakpoints\",\"params\":{\"start\":{\"scriptId\":\"1\",\"lineNumber\":11,\"columnNumber\":0},\"end\":{\"scriptId\":\"1\",\"lineNumber\":11,\"columnNumber\":37},\"restrictToFunction\":false}}");
        assertEquals("{\"result\":{\"locations\":[{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":11},{\"scriptId\":\"1\",\"columnNumber\":13,\"lineNumber\":11},{\"scriptId\":\"1\",\"columnNumber\":24,\"lineNumber\":11}]},\"id\":6}", tester.getMessages(true).trim());

        // When only start location is provided:
        tester.sendMessage("{\"id\":3,\"method\":\"Debugger.getPossibleBreakpoints\",\"params\":{\"start\":{\"scriptId\":\"1\",\"lineNumber\":5,\"columnNumber\":0},\"restrictToFunction\":false}}");
        assertEquals("{\"result\":{\"locations\":[{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":6}]},\"id\":3}", tester.getMessages(true).trim());
        // Provides statement location when only beginning is included
        tester.sendMessage("{\"id\":4,\"method\":\"Debugger.getPossibleBreakpoints\",\"params\":{\"start\":{\"scriptId\":\"1\",\"lineNumber\":8,\"columnNumber\":2},\"restrictToFunction\":false}}");
        assertEquals("{\"result\":{\"locations\":[{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":8}]},\"id\":4}", tester.getMessages(true).trim());
        // Provides statement location when only end is included
        tester.sendMessage("{\"id\":5,\"method\":\"Debugger.getPossibleBreakpoints\",\"params\":{\"start\":{\"scriptId\":\"1\",\"lineNumber\":9,\"columnNumber\":8},\"restrictToFunction\":false}}");
        assertEquals("{\"result\":{\"locations\":[{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":8}]},\"id\":5}", tester.getMessages(true).trim());
        // Provides all statement locations on a line
        tester.sendMessage("{\"id\":6,\"method\":\"Debugger.getPossibleBreakpoints\",\"params\":{\"start\":{\"scriptId\":\"1\",\"lineNumber\":11,\"columnNumber\":0},\"restrictToFunction\":false}}");
        assertEquals("{\"result\":{\"locations\":[{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":11},{\"scriptId\":\"1\",\"columnNumber\":13,\"lineNumber\":11},{\"scriptId\":\"1\",\"columnNumber\":24,\"lineNumber\":11}]},\"id\":6}", tester.getMessages(true).trim());

        // Test location after file length:
        tester.sendMessage("{\"id\":7,\"method\":\"Debugger.getPossibleBreakpoints\",\"params\":{\"start\":{\"scriptId\":\"1\",\"lineNumber\":14,\"columnNumber\":0},\"end\":{\"scriptId\":\"1\",\"lineNumber\":14,\"columnNumber\":0},\"restrictToFunction\":false}}");
        assertEquals("{\"result\":{\"locations\":[{\"scriptId\":\"1\",\"columnNumber\":24,\"lineNumber\":11}]},\"id\":7}", tester.getMessages(true).trim());

        // Resume to finish:
        tester.sendMessage("{\"id\":20,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":20}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        tester.finish();
    }

    @Test
    public void testThrown() throws Exception {
        tester = InspectorTester.start(false);
        Source source = Source.newBuilder("sl", CODE_THROW, "SLThrow.sl").build();
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        tester.sendMessage("{\"id\":2,\"method\":\"Debugger.enable\"}");
        tester.sendMessage("{\"id\":3,\"method\":\"Runtime.runIfWaitingForDebugger\"}");
        String srcURL = InspectorTester.getStringURI(source.getURI());
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":1}\n" +
                        "{\"result\":{},\"id\":2}\n" +
                        "{\"result\":{},\"id\":3}\n" +
                        "{\"method\":\"Runtime.executionContextCreated\",\"params\":{\"context\":{\"origin\":\"\",\"name\":\"test\",\"id\":1}}}\n"));
        tester.sendMessage("{\"id\":3,\"method\":\"Debugger.setPauseOnExceptions\",\"params\":{\"state\":\"uncaught\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":3}\n"));

        tester.eval(source);
        long id = tester.getContextId();
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":0,\"scriptId\":\"0\",\"endColumn\":0,\"startColumn\":0,\"startLine\":0,\"length\":0,\"executionContextId\":" + id + ",\"url\":\"" + SL_BUILTIN_URI + "\",\"hash\":\"ffffffffffffffffffffffffffffffffffffffff\"}}\n" +
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":7,\"scriptId\":\"1\",\"endColumn\":1,\"startColumn\":0,\"startLine\":0,\"length\":100,\"executionContextId\":" + id + ",\"url\":\"" + srcURL + "\",\"hash\":\"da38785af1cf0829f047f02ffe94b4d3ff485978\"}}\n"));
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"exception\",\"data\":{\"uncaught\":true},\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"invert\"," +
                                                 "\"scopeChain\":[{\"name\":\"invert\",\"type\":\"local\",\"object\":{\"description\":\"invert\",\"type\":\"object\",\"objectId\":\"1\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"2\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"3\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":4}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":11,\"lineNumber\":5}," +
                                                 "\"url\":\"" + srcURL + "\"}," +
                                                "{\"callFrameId\":\"1\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"4\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"5\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"6\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":2}," +
                                                 "\"url\":\"" + srcURL + "\"}" +
                                            "]}}\n"));
        // Resume to finish:
        tester.sendMessage("{\"id\":10,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":10}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        String error = tester.finishErr();
        assertTrue(error, error.startsWith("Type error at SLThrow.sl"));
    }

    @Test
    public void testSetVariableValue() throws Exception {
        tester = InspectorTester.start(false);
        Source source = Source.newBuilder("sl", CODE_VARS, "SLVars.sl").build();
        String slTestURI = InspectorTester.getStringURI(source.getURI());
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        tester.sendMessage("{\"id\":2,\"method\":\"Debugger.enable\"}");
        tester.sendMessage("{\"id\":3,\"method\":\"Runtime.runIfWaitingForDebugger\"}");
        String srcURL = InspectorTester.getStringURI(source.getURI());
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":1}\n" +
                        "{\"result\":{},\"id\":2}\n" +
                        "{\"result\":{},\"id\":3}\n" +
                        "{\"method\":\"Runtime.executionContextCreated\",\"params\":{\"context\":{\"origin\":\"\",\"name\":\"test\",\"id\":1}}}\n"));
        int suspendLine = 15;
        tester.sendMessage("{\"id\":4,\"method\":\"Debugger.setBreakpointByUrl\",\"params\":{\"lineNumber\":" + suspendLine + ",\"url\":\"" + srcURL + "\",\"columnNumber\":0,\"condition\":\"\"}}");
        assertEquals("{\"result\":{\"breakpointId\":\"1\",\"locations\":[]},\"id\":4}", tester.getMessages(true).trim());

        tester.eval(source);
        long id = tester.getContextId();
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":0,\"scriptId\":\"0\",\"endColumn\":0,\"startColumn\":0,\"startLine\":0,\"length\":0,\"executionContextId\":" + id + ",\"url\":\"" + SL_BUILTIN_URI + "\",\"hash\":\"ffffffffffffffffffffffffffffffffffffffff\"}}\n" +
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":22,\"scriptId\":\"1\",\"endColumn\":1,\"startColumn\":0,\"startLine\":0,\"length\":" + CODE_VARS.length() + ",\"executionContextId\":" + id + ",\"url\":\"" + slTestURI + "\",\"hash\":\"f3cc2fb0fc8f5c66f8f54ddcfad016c1fe35faef\"}}\n" +
                        "{\"method\":\"Debugger.breakpointResolved\",\"params\":{\"breakpointId\":\"1\",\"location\":{\"scriptId\":\"1\",\"columnNumber\":4,\"lineNumber\":" + suspendLine + "}}}\n"));
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[\"1\"]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"1\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"2\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"3\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":4,\"lineNumber\":" + suspendLine + "}," +
                                                 "\"url\":\"" + srcURL + "\"}]}}\n"));
        tester.sendMessage("{\"id\":5,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"1\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":[{\"isOwn\":true,\"enumerable\":true,\"name\":\"n\",\"value\":{\"description\":\"1\",\"type\":\"number\",\"value\":1},\"configurable\":true,\"writable\":true}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"m\",\"value\":{\"description\":\"4\",\"type\":\"number\",\"value\":4},\"configurable\":true,\"writable\":true}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"b\",\"value\":{\"description\":\"true\",\"type\":\"boolean\",\"value\":true},\"configurable\":true,\"writable\":true}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"bb\",\"value\":{\"description\":\"true\",\"type\":\"boolean\",\"value\":true},\"configurable\":true,\"writable\":true}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"big\",\"value\":{\"description\":\"152415787532388367501905199875019052100\",\"type\":\"number\",\"value\":\"152415787532388367501905199875019052100\"},\"configurable\":true,\"writable\":true}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"str\",\"value\":{\"description\":\"A String\",\"type\":\"string\",\"value\":\"A String\"},\"configurable\":true,\"writable\":true}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"f\",\"value\":{\"description\":\"fn() {\\n  return 2;\\n}\",\"className\":\"Function\",\"type\":\"function\",\"objectId\":\"4\"},\"configurable\":true,\"writable\":true}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"f2\",\"value\":{\"description\":\"0\",\"type\":\"number\",\"value\":0},\"configurable\":true,\"writable\":true}],\"internalProperties\":[]},\"id\":5}\n"));

        tester.sendMessage("{\"id\":6,\"method\":\"Debugger.setVariableValue\",\"params\":{\"scopeNumber\":0,\"variableName\":\"m\",\"newValue\":{\"value\":1000},\"callFrameId\":\"0\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":6}\n"));
        tester.sendMessage("{\"id\":7,\"method\":\"Debugger.setVariableValue\",\"params\":{\"scopeNumber\":0,\"variableName\":\"bb\",\"newValue\":{\"value\":false},\"callFrameId\":\"0\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":7}\n"));
        tester.sendMessage("{\"id\":8,\"method\":\"Debugger.setVariableValue\",\"params\":{\"scopeNumber\":0,\"variableName\":\"str\",\"newValue\":{\"value\":\"A Different String\"},\"callFrameId\":\"0\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":8}\n"));
        tester.sendMessage("{\"id\":9,\"method\":\"Debugger.setVariableValue\",\"params\":{\"scopeNumber\":0,\"variableName\":\"f2\",\"newValue\":{\"objectId\":\"4\"},\"callFrameId\":\"0\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":9}\n"));

        tester.sendMessage("{\"id\":10,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":10}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[\"1\"]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"5\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"6\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"7\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":4,\"lineNumber\":" + suspendLine + "}," +
                                                 "\"url\":\"" + srcURL + "\"}]}}\n"));
        tester.sendMessage("{\"id\":11,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"5\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":[{\"isOwn\":true,\"enumerable\":true,\"name\":\"n\",\"value\":{\"description\":\"0\",\"type\":\"number\",\"value\":0},\"configurable\":true,\"writable\":true}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"m\",\"value\":{\"description\":\"1000\",\"type\":\"number\",\"value\":1000},\"configurable\":true,\"writable\":true}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"b\",\"value\":{\"description\":\"false\",\"type\":\"boolean\",\"value\":false},\"configurable\":true,\"writable\":true}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"bb\",\"value\":{\"description\":\"false\",\"type\":\"boolean\",\"value\":false},\"configurable\":true,\"writable\":true}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"big\",\"value\":{\"description\":\"23230572289118153328333583928030329684079829544396666111742077337982514410000\",\"type\":\"number\",\"value\":\"23230572289118153328333583928030329684079829544396666111742077337982514410000\"},\"configurable\":true,\"writable\":true}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"str\",\"value\":{\"description\":\"A Different String\",\"type\":\"string\",\"value\":\"A Different String\"},\"configurable\":true,\"writable\":true}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"f\",\"value\":{\"description\":\"fn() {\\n  return 2;\\n}\",\"className\":\"Function\",\"type\":\"function\",\"objectId\":\"8\"},\"configurable\":true,\"writable\":true}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"f2\",\"value\":{\"description\":\"fn() {\\n  return 2;\\n}\",\"className\":\"Function\",\"type\":\"function\",\"objectId\":\"9\"},\"configurable\":true,\"writable\":true}],\"internalProperties\":[]},\"id\":11}\n"));

        // Resume to finish:
        tester.sendMessage("{\"id\":20,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":20}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        tester.finish();
    }

    @Test
    public void testMemberCompletionChrome() throws Exception {
        tester = InspectorTester.start(false);
        Source source = Source.newBuilder("sl", CODE_MEMBERS, "SLMembers.sl").build();
        String slTestURI = InspectorTester.getStringURI(source.getURI());
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        tester.sendMessage("{\"id\":2,\"method\":\"Debugger.enable\"}");
        tester.sendMessage("{\"id\":3,\"method\":\"Runtime.runIfWaitingForDebugger\"}");
        String srcURL = InspectorTester.getStringURI(source.getURI());
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":1}\n" +
                        "{\"result\":{},\"id\":2}\n" +
                        "{\"result\":{},\"id\":3}\n" +
                        "{\"method\":\"Runtime.executionContextCreated\",\"params\":{\"context\":{\"origin\":\"\",\"name\":\"test\",\"id\":1}}}\n"));
        tester.eval(source);
        long id = tester.getContextId();
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":0,\"scriptId\":\"0\",\"endColumn\":0,\"startColumn\":0,\"startLine\":0,\"length\":0,\"executionContextId\":" + id + ",\"url\":\"" + SL_BUILTIN_URI + "\",\"hash\":\"ffffffffffffffffffffffffffffffffffffffff\"}}\n" +
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":7,\"scriptId\":\"1\",\"endColumn\":1,\"startColumn\":0,\"startLine\":0,\"length\":" + CODE_MEMBERS.length() + ",\"executionContextId\":" + id + ",\"url\":\"" + slTestURI + "\",\"hash\":\"fdcfbca4f86efacaef3d0f34fe94b4d3ff485978\"}}\n"));
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"1\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"2\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"3\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":5}," +
                                                 "\"url\":\"" + srcURL + "\"}]}}\n"));
        tester.sendMessage("{\"id\":5,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"1\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":[{\"isOwn\":true,\"enumerable\":true,\"name\":\"obj\",\"value\":{\"description\":\"Object\",\"className\":\"Object\",\"type\":\"object\",\"objectId\":\"4\"},\"configurable\":true,\"writable\":true}],\"internalProperties\":[]},\"id\":5}\n"));

        tester.sendMessage("{\"id\":6,\"method\":\"Runtime.callFunctionOn\",\"params\":{\"objectId\":\"4\",\"functionDeclaration\":\"function getCompletions(type){let object;if(type==='string')\\nobject=new String('');else if(type==='number')\\nobject=new Number(0);else if(type==='bigint')\\nobject=Object(BigInt(0));else if(type==='boolean')\\nobject=new Boolean(false);else\\nobject=this;const result=[];try{for(let o=object;o;o=Object.getPrototypeOf(o)){if((type==='array'||type==='typedarray')&&o===object&&o.length>9999)\\ncontinue;const group={items:[],__proto__:null};try{if(typeof o==='object'&&Object.prototype.hasOwnProperty.call(o,'constructor')&&o.constructor&&o.constructor.name)\\ngroup.title=o.constructor.name;}catch(ee){}\\nresult[result.length]=group;const names=Object.getOwnPropertyNames(o);const isArray=Array.isArray(o);for(let i=0;i<names.length&&group.items.length<10000;++i){if(isArray&&/^[0-9]/.test(names[i]))\\ncontinue;group.items[group.items.length]=names[i];}}}catch(e){}\\nreturn result;}\",\"arguments\":[{}],\"silent\":true,\"returnByValue\":true}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":{\"type\":\"object\",\"value\":[{\"items\":[\"a\",\"b\",\"c\"]}]}},\"id\":6}\n"));

        // Resume to finish:
        tester.sendMessage("{\"id\":20,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":20}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        tester.finish();
    }

    @Test
    public void testMemberCompletionVSCode() throws Exception {
        tester = InspectorTester.start(false);
        Source source = Source.newBuilder("sl", CODE_MEMBERS, "SLMembers.sl").build();
        String slTestURI = InspectorTester.getStringURI(source.getURI());
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        tester.sendMessage("{\"id\":2,\"method\":\"Debugger.enable\"}");
        tester.sendMessage("{\"id\":3,\"method\":\"Runtime.runIfWaitingForDebugger\"}");
        String srcURL = InspectorTester.getStringURI(source.getURI());
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":1}\n" +
                        "{\"result\":{},\"id\":2}\n" +
                        "{\"result\":{},\"id\":3}\n" +
                        "{\"method\":\"Runtime.executionContextCreated\",\"params\":{\"context\":{\"origin\":\"\",\"name\":\"test\",\"id\":1}}}\n"));
        tester.eval(source);
        long id = tester.getContextId();
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":0,\"scriptId\":\"0\",\"endColumn\":0,\"startColumn\":0,\"startLine\":0,\"length\":0,\"executionContextId\":" + id + ",\"url\":\"" + SL_BUILTIN_URI + "\",\"hash\":\"ffffffffffffffffffffffffffffffffffffffff\"}}\n" +
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":7,\"scriptId\":\"1\",\"endColumn\":1,\"startColumn\":0,\"startLine\":0,\"length\":" + CODE_MEMBERS.length() + ",\"executionContextId\":" + id + ",\"url\":\"" + slTestURI + "\",\"hash\":\"fdcfbca4f86efacaef3d0f34fe94b4d3ff485978\"}}\n"));
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"1\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"2\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"3\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":5}," +
                                                 "\"url\":\"" + srcURL + "\"}]}}\n"));
        tester.sendMessage("{\"id\":6,\"method\":\"Debugger.evaluateOnCallFrame\",\"params\":{\"callFrameId\":\"0\",\"expression\":\"(function(x){var a=[];for(var o=x;o!==null&&typeof o !== 'undefined';o=o.__proto__){a.push(Object.getOwnPropertyNames(o))};return a})(obj)\",\"silent\":true,\"includeCommandLineAPI\":true,\"objectGroup\":\"console\",\"returnByValue\":true}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":{\"type\":\"object\",\"value\":[[\"a\",\"b\",\"c\"]]}},\"id\":6}\n"));

        // Resume to finish:
        tester.sendMessage("{\"id\":20,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":20}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        tester.finish();
    }

    @Test
    public void testCompletionUpdate() throws Exception {
        tester = InspectorTester.start(true);
        Source source = Source.newBuilder("sl", CODE1, "Code1Compl.sl").build();
        String slTestURI = InspectorTester.getStringURI(source.getURI());
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        tester.sendMessage("{\"id\":2,\"method\":\"Debugger.enable\"}");
        tester.sendMessage("{\"id\":3,\"method\":\"Runtime.runIfWaitingForDebugger\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":1}\n" +
                        "{\"result\":{},\"id\":2}\n" +
                        "{\"result\":{},\"id\":3}\n" +
                        "{\"method\":\"Runtime.executionContextCreated\",\"params\":{\"context\":{\"origin\":\"\",\"name\":\"test\",\"id\":1}}}\n"));
        tester.eval(source);
        long id = tester.getContextId();
        // Suspend at the beginning of the script:
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":0,\"scriptId\":\"0\",\"endColumn\":0,\"startColumn\":0,\"startLine\":0,\"length\":0,\"executionContextId\":" + id + ",\"url\":\"" + SL_BUILTIN_URI + "\",\"hash\":\"ffffffffffffffffffffffffffffffffffffffff\"}}\n" +
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":18,\"scriptId\":\"1\",\"endColumn\":1,\"startColumn\":0,\"startLine\":0,\"length\":245,\"executionContextId\":" + id + ",\"url\":\"" + slTestURI + "\",\"hash\":\"f8058ed0f3c2f0acf3e37e59f953127afdba90e5\"}}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"1\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"2\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"3\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":1}," +
                                                 "\"url\":\"" + slTestURI + "\"}]}}\n"));
        // Get global completion:
        tester.sendMessage("{\"id\":6,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"2\",\"ownProperties\":false,\"accessorPropertiesOnly\":false,\"generatePreview\":false}}");
        String functionDescription = FACTORIAL.replace("\n", "\\n").substring("function ".length());
        String globals = tester.receiveMessages(true,
                        "{\"result\":{\"result\":[",
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"factorial\",\"value\":{\"description\":\"" + functionDescription + "\",\"className\":\"Function\",\"type\":\"function\"",
                                                 "]},\"id\":6}\n");
        assertFalse(globals.contains("foo0") || globals.contains("foo1"));
        tester.sendMessage("{\"id\":7,\"method\":\"Debugger.evaluateOnCallFrame\",\"params\":{\"callFrameId\":\"0\",\"expression\":\"function foo0() {n = 0;} function foo1() {n = 1;}\",\"silent\":true,\"includeCommandLineAPI\":true,\"objectGroup\":\"console\",\"returnByValue\":true}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"value\":null}},\"id\":7}\n"));

        // Get new global completion:
        tester.sendMessage("{\"id\":8,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"2\",\"ownProperties\":false,\"accessorPropertiesOnly\":false,\"generatePreview\":false}}");
        tester.receiveMessages(true,
                        "{\"result\":{\"result\":[",
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"factorial\",\"value\":{\"description\":\"" + functionDescription + "\",\"className\":\"Function\",\"type\":\"function\"",
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"foo0\",\"value\":{\"description\":\"foo0() {n = 0;}\",\"className\":\"Function\",\"type\":\"function\"",
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"foo1\",\"value\":{\"description\":\"foo1() {n = 1;}\",\"className\":\"Function\",\"type\":\"function\"",
                                                 "]},\"id\":8}\n");

        // Resume to finish:
        tester.sendMessage("{\"id\":20,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":20}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        tester.finish();
    }

    @Test
    public void testObjectGroups() throws Exception {
        tester = InspectorTester.start(false);
        Source source = Source.newBuilder("sl", CODE_OBJECT_GROUPS, "SLObjectGroups.sl").build();
        String slTestURI = InspectorTester.getStringURI(source.getURI());
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        tester.sendMessage("{\"id\":2,\"method\":\"Debugger.enable\"}");
        tester.sendMessage("{\"id\":3,\"method\":\"Runtime.runIfWaitingForDebugger\"}");
        String srcURL = InspectorTester.getStringURI(source.getURI());
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":1}\n" +
                        "{\"result\":{},\"id\":2}\n" +
                        "{\"result\":{},\"id\":3}\n" +
                        "{\"method\":\"Runtime.executionContextCreated\",\"params\":{\"context\":{\"origin\":\"\",\"name\":\"test\",\"id\":1}}}\n"));
        tester.eval(source);
        long id = tester.getContextId();
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":0,\"scriptId\":\"0\",\"endColumn\":0,\"startColumn\":0,\"startLine\":0,\"length\":0,\"executionContextId\":" + id + ",\"url\":\"" + SL_BUILTIN_URI + "\",\"hash\":\"ffffffffffffffffffffffffffffffffffffffff\"}}\n" +
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":8,\"scriptId\":\"1\",\"endColumn\":1,\"startColumn\":0,\"startLine\":0,\"length\":" + CODE_OBJECT_GROUPS.length() + ",\"executionContextId\":" + id + ",\"url\":\"" + slTestURI + "\",\"hash\":\"fdcfbca4f86efacaf153f7f0fe92832bff485978\"}}\n"));
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"main\"," +
                                                 "\"scopeChain\":[{\"name\":\"main\",\"type\":\"local\",\"object\":{\"description\":\"main\",\"type\":\"object\",\"objectId\":\"1\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"2\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"NULL\",\"type\":\"object\",\"objectId\":\"3\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":9,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":4}," +
                                                 "\"url\":\"" + srcURL + "\"}]}}\n"));
        assertEquals("[1, 2]", tester.getInspectorContext().getRemoteObjectsHandler().getRegisteredIDs().toString());
        tester.sendMessage("{\"id\":5,\"method\":\"Runtime.evaluate\",\"params\":{\"expression\":\"obj\",\"objectGroup\":\"testGroup\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":{\"description\":\"Object\",\"className\":\"Object\",\"type\":\"object\",\"objectId\":\"4\"}},\"id\":5}\n"));
        assertEquals("[1, 2, 4]", tester.getInspectorContext().getRemoteObjectsHandler().getRegisteredIDs().toString());
        // Release the test object group:
        tester.sendMessage("{\"id\":6,\"method\":\"Runtime.releaseObjectGroup\",\"params\":{\"objectGroup\":\"testGroup\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":6}\n"));
        assertEquals("[1, 2]", tester.getInspectorContext().getRemoteObjectsHandler().getRegisteredIDs().toString());

        tester.sendMessage("{\"id\":10,\"method\":\"Runtime.evaluate\",\"params\":{\"expression\":\"obj\",\"objectGroup\":\"testGroup2\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":{\"description\":\"Object\",\"className\":\"Object\",\"type\":\"object\",\"objectId\":\"5\"}},\"id\":10}\n"));
        tester.sendMessage("{\"id\":11,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"5\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":[{\"isOwn\":true,\"enumerable\":true,\"name\":\"a\",\"value\":{\"description\":\"Object\",\"className\":\"Object\",\"type\":\"object\",\"objectId\":\"6\"},\"configurable\":true,\"writable\":true}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"b\",\"value\":{\"description\":\"Object\",\"className\":\"Object\",\"type\":\"object\",\"objectId\":\"7\"},\"configurable\":true,\"writable\":true}]," +
                                                "\"internalProperties\":[]},\"id\":11}\n"));
        assertEquals("[1, 2, 5, 6, 7]", tester.getInspectorContext().getRemoteObjectsHandler().getRegisteredIDs().toString());
        // Release an object
        tester.sendMessage("{\"id\":15,\"method\":\"Runtime.releaseObject\",\"params\":{\"objectId\":\"2\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":15}\n"));
        assertEquals("[1, 5, 6, 7]", tester.getInspectorContext().getRemoteObjectsHandler().getRegisteredIDs().toString());
        // Release the test object group that covers the object properties:
        tester.sendMessage("{\"id\":16,\"method\":\"Runtime.releaseObjectGroup\",\"params\":{\"objectGroup\":\"testGroup2\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":16}\n"));
        assertEquals("[1]", tester.getInspectorContext().getRemoteObjectsHandler().getRegisteredIDs().toString());

        // Resume to finish:
        tester.sendMessage("{\"id\":20,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":20}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        tester.finish();
    }

    // @formatter:on
    // CheckStyle: resume line length check
}
