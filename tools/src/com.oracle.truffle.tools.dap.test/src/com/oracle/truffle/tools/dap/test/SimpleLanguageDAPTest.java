/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.dap.test;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

import org.graalvm.polyglot.Source;

import org.junit.After;
import org.junit.Test;

import static com.oracle.truffle.tools.dap.test.DAPTester.getFilePath;

public final class SimpleLanguageDAPTest {

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

    private static final URI testURI = URI.create("file:///test/SLTest.sl");
    private static final File testFile = new File(testURI);
    private static final String testFilePath = getFilePath(testFile);
    private static final String NL = replaceNewLines(System.getProperty("line.separator"));

    private DAPTester tester;

    @After
    public void tearDown() {
        tester = null;
    }

    // @formatter:off   The default formatting makes unnecessarily big indents and illogical line breaks
    // CheckStyle: stop line length check
    @Test
    public void testInitialSuspendAndSource() throws Exception {
        tester = DAPTester.start(true);
        Source source = Source.newBuilder("sl", CODE1, "SLTest.sl").uri(testURI).build();
        tester.sendMessage("{\"command\":\"initialize\",\"arguments\":{\"clientID\":\"DAPTester\",\"clientName\":\"DAP Tester\",\"adapterID\":\"graalvm\",\"pathFormat\":\"path\",\"linesStartAt1\":true,\"columnsStartAt1\":true,\"supportsVariableType\":true,\"supportsVariablePaging\":true,\"supportsRunInTerminalRequest\":true,\"locale\":\"en-us\",\"supportsProgressReporting\":true},\"type\":\"request\",\"seq\":1}");
        tester.compareReceivedMessages(
                "{\"event\":\"initialized\",\"type\":\"event\"}",
                "{\"success\":true,\"type\":\"response\",\"body\":{\"supportsConditionalBreakpoints\":true,\"supportsLoadedSourcesRequest\":true,\"supportsFunctionBreakpoints\":true,\"supportsExceptionInfoRequest\":true,\"supportsBreakpointLocationsRequest\":true,\"supportsHitConditionalBreakpoints\":true,\"supportsLogPoints\":true,\"supportsSetVariable\":true,\"supportsConfigurationDoneRequest\":true,\"exceptionBreakpointFilters\":[{\"filter\":\"all\",\"label\":\"All Exceptions\"},{\"filter\":\"uncaught\",\"label\":\"Uncaught Exceptions\"}]},\"request_seq\":1,\"command\":\"initialize\"}"
        );
        tester.sendMessage("{\"command\":\"attach\",\"arguments\":{\"type\":\"graalvm\",\"request\":\"attach\",\"name\":\"Attach\",\"port\":9229,\"protocol\":\"chromeDevTools\"},\"type\":\"request\",\"seq\":2}");
        tester.compareReceivedMessages("{\"event\":\"output\",\"body\":{\"output\":\"Debugger attached.\",\"category\":\"stderr\"},\"type\":\"event\"}", "{\"success\":true,\"type\":\"response\",\"request_seq\":2,\"command\":\"attach\"}");
        tester.sendMessage("{\"command\":\"loadedSources\",\"type\":\"request\",\"seq\":3}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"sources\":[]},\"type\":\"response\",\"request_seq\":3,\"command\":\"loadedSources\",\"seq\":5}");
        tester.sendMessage("{\"command\":\"configurationDone\",\"type\":\"request\",\"seq\":4}");
        tester.compareReceivedMessages("{\"success\":true,\"type\":\"response\",\"request_seq\":4,\"command\":\"configurationDone\",\"seq\":6}");
        tester.eval(source);
        tester.compareReceivedMessages("{\"event\":\"thread\",\"body\":{\"threadId\":1,\"reason\":\"started\"},\"type\":\"event\",\"seq\":7}");
        tester.compareReceivedMessages(
                "{\"event\":\"loadedSource\",\"body\":{\"reason\":\"new\",\"source\":{\"sourceReference\":1,\"name\":\"SL builtin\"}},\"type\":\"event\",\"seq\":8}",
                "{\"event\":\"loadedSource\",\"body\":{\"reason\":\"new\",\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}},\"type\":\"event\",\"seq\":9}"
        );
        // Suspend at the beginning of the script:
        tester.compareReceivedMessages("{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"debugger_statement\",\"description\":\"Paused on debugger statement\"},\"type\":\"event\",\"seq\":10}");
        tester.sendMessage("{\"command\":\"threads\",\"type\":\"request\",\"seq\":5}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"threads\":[{\"name\":\"testRunner\",\"id\":1}]},\"type\":\"response\",\"request_seq\":5,\"command\":\"threads\",\"seq\":11}");
        tester.sendMessage("{\"command\":\"stackTrace\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":6}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"stackFrames\":[{\"line\":2,\"name\":\"main\",\"column\":3,\"id\":1,\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}}],\"totalFrames\":1},\"type\":\"response\",\"request_seq\":6,\"command\":\"stackTrace\",\"seq\":12}");
        // Get loaded sources
        tester.sendMessage("{\"command\":\"loadedSources\",\"type\":\"request\",\"seq\":7}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"sources\":[{\"sourceReference\":1,\"name\":\"SL builtin\"},{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}]},\"type\":\"response\",\"request_seq\":7,\"command\":\"loadedSources\",\"seq\":13}");
        // Get the script code:
        tester.sendMessage("{\"command\":\"source\",\"arguments\":{\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"},\"sourceReference\":2},\"type\":\"request\",\"seq\":8}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"content\":\"" + replaceNewLines(source.getCharacters().toString()) + "\"},\"type\":\"response\",\"request_seq\":8,\"command\":\"source\",\"seq\":14}");
        // Continue to finish
        tester.sendMessage("{\"command\":\"continue\",\"arguments\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"request\",\"seq\":9}");
        tester.compareReceivedMessages(
                "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                "{\"success\":true,\"body\":{\"allThreadsContinued\":false},\"type\":\"response\",\"request_seq\":9,\"command\":\"continue\"}"
        );
        tester.finish();
    }

    @Test
    public void testStepping() throws Exception {
        tester = DAPTester.start(true);
        Source source = Source.newBuilder("sl", CODE1, "SLTest.sl").uri(testURI).build();
        tester.sendMessage("{\"command\":\"initialize\",\"arguments\":{\"clientID\":\"DAPTester\",\"clientName\":\"DAP Tester\",\"adapterID\":\"graalvm\",\"pathFormat\":\"path\",\"linesStartAt1\":true,\"columnsStartAt1\":true,\"supportsVariableType\":true,\"supportsVariablePaging\":true,\"supportsRunInTerminalRequest\":true,\"locale\":\"en-us\",\"supportsProgressReporting\":true},\"type\":\"request\",\"seq\":1}");
        tester.compareReceivedMessages(
                "{\"event\":\"initialized\",\"type\":\"event\"}",
                "{\"success\":true,\"type\":\"response\",\"body\":{\"supportsConditionalBreakpoints\":true,\"supportsLoadedSourcesRequest\":true,\"supportsFunctionBreakpoints\":true,\"supportsExceptionInfoRequest\":true,\"supportsBreakpointLocationsRequest\":true,\"supportsHitConditionalBreakpoints\":true,\"supportsLogPoints\":true,\"supportsSetVariable\":true,\"supportsConfigurationDoneRequest\":true,\"exceptionBreakpointFilters\":[{\"filter\":\"all\",\"label\":\"All Exceptions\"},{\"filter\":\"uncaught\",\"label\":\"Uncaught Exceptions\"}]},\"request_seq\":1,\"command\":\"initialize\"}"
        );
        tester.sendMessage("{\"command\":\"attach\",\"arguments\":{\"type\":\"graalvm\",\"request\":\"attach\",\"name\":\"Attach\",\"port\":9229,\"protocol\":\"chromeDevTools\"},\"type\":\"request\",\"seq\":2}");
        tester.compareReceivedMessages("{\"event\":\"output\",\"body\":{\"output\":\"Debugger attached.\",\"category\":\"stderr\"},\"type\":\"event\"}", "{\"success\":true,\"type\":\"response\",\"request_seq\":2,\"command\":\"attach\"}");
        tester.sendMessage("{\"command\":\"loadedSources\",\"type\":\"request\",\"seq\":3}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"sources\":[]},\"type\":\"response\",\"request_seq\":3,\"command\":\"loadedSources\",\"seq\":5}");
        tester.sendMessage("{\"command\":\"configurationDone\",\"type\":\"request\",\"seq\":4}");
        tester.compareReceivedMessages("{\"success\":true,\"type\":\"response\",\"request_seq\":4,\"command\":\"configurationDone\",\"seq\":6}");
        tester.eval(source);
        tester.compareReceivedMessages("{\"event\":\"thread\",\"body\":{\"threadId\":1,\"reason\":\"started\"},\"type\":\"event\",\"seq\":7}");
        tester.compareReceivedMessages(
                "{\"event\":\"loadedSource\",\"body\":{\"reason\":\"new\",\"source\":{\"sourceReference\":1,\"name\":\"SL builtin\"}},\"type\":\"event\",\"seq\":8}",
                "{\"event\":\"loadedSource\",\"body\":{\"reason\":\"new\",\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}},\"type\":\"event\",\"seq\":9}"
        );
        // Suspend at the beginning of the script:
        tester.compareReceivedMessages("{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"debugger_statement\",\"description\":\"Paused on debugger statement\"},\"type\":\"event\",\"seq\":10}");
        tester.sendMessage("{\"command\":\"threads\",\"type\":\"request\",\"seq\":5}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"threads\":[{\"name\":\"testRunner\",\"id\":1}]},\"type\":\"response\",\"request_seq\":5,\"command\":\"threads\",\"seq\":11}");
        tester.sendMessage("{\"command\":\"stackTrace\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":6}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"stackFrames\":[{\"line\":2,\"name\":\"main\",\"column\":3,\"id\":1,\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}}],\"totalFrames\":1},\"type\":\"response\",\"request_seq\":6,\"command\":\"stackTrace\",\"seq\":12}");
        // Next:
        tester.sendMessage("{\"command\":\"next\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":7}");
        tester.compareReceivedMessages(
                "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                "{\"success\":true,\"type\":\"response\",\"request_seq\":7,\"command\":\"next\"}",
                "{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"debugger_statement\",\"description\":\"Paused on debugger statement\"},\"type\":\"event\"}"
        );
        tester.sendMessage("{\"command\":\"threads\",\"type\":\"request\",\"seq\":8}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"threads\":[{\"name\":\"testRunner\",\"id\":1}]},\"type\":\"response\",\"request_seq\":8,\"command\":\"threads\",\"seq\":16}");
        tester.sendMessage("{\"command\":\"stackTrace\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":9}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"stackFrames\":[{\"line\":3,\"name\":\"main\",\"column\":3,\"id\":1,\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}}],\"totalFrames\":1},\"type\":\"response\",\"request_seq\":9,\"command\":\"stackTrace\",\"seq\":17}");
        // Step in:
        tester.sendMessage("{\"command\":\"stepIn\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":10}");
        tester.compareReceivedMessages(
                "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                "{\"success\":true,\"type\":\"response\",\"request_seq\":10,\"command\":\"stepIn\"}",
                "{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"debugger_statement\",\"description\":\"Paused on debugger statement\"},\"type\":\"event\"}"
        );
        tester.sendMessage("{\"command\":\"threads\",\"type\":\"request\",\"seq\":11}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"threads\":[{\"name\":\"testRunner\",\"id\":1}]},\"type\":\"response\",\"request_seq\":11,\"command\":\"threads\",\"seq\":21}");
        tester.sendMessage("{\"command\":\"stackTrace\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":12}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"stackFrames\":[{\"line\":11,\"name\":\"factorial\",\"column\":3,\"id\":1,\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}},{\"line\":3,\"name\":\"main\",\"column\":7,\"id\":2,\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}}],\"totalFrames\":2},\"type\":\"response\",\"request_seq\":12,\"command\":\"stackTrace\",\"seq\":22}");
        // Step out:
        tester.sendMessage("{\"command\":\"stepOut\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":13}");
        tester.compareReceivedMessages(
                "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                "{\"success\":true,\"type\":\"response\",\"request_seq\":13,\"command\":\"stepOut\"}",
                "{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"debugger_statement\",\"description\":\"Paused on debugger statement\"},\"type\":\"event\"}"
        );
        tester.sendMessage("{\"command\":\"threads\",\"type\":\"request\",\"seq\":14}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"threads\":[{\"name\":\"testRunner\",\"id\":1}]},\"type\":\"response\",\"request_seq\":14,\"command\":\"threads\",\"seq\":26}");
        tester.sendMessage("{\"command\":\"stackTrace\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":15}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"stackFrames\":[{\"line\":3,\"name\":\"main\",\"column\":21,\"id\":1,\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}}],\"totalFrames\":1},\"type\":\"response\",\"request_seq\":15,\"command\":\"stackTrace\",\"seq\":27}");
        // Step out to finish:
        tester.sendMessage("{\"command\":\"stepOut\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":16}");
        tester.compareReceivedMessages(
                "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                "{\"success\":true,\"type\":\"response\",\"request_seq\":16,\"command\":\"stepOut\"}"
        );
        tester.finish();
    }

    @Test
    public void testBreakpoints() throws Exception {
        tester = DAPTester.start(false);
        File sourceFile = createTemporaryFile(CODE1);
        Source source = Source.newBuilder("sl", sourceFile).build();
        String sourceJson = "{\"name\":\"" + sourceFile.getName() + "\",\"path\":\"" + getFilePath(sourceFile) + "\"}";
        tester.sendMessage("{\"command\":\"initialize\",\"arguments\":{\"clientID\":\"DAPTester\",\"clientName\":\"DAP Tester\",\"adapterID\":\"graalvm\",\"pathFormat\":\"path\",\"linesStartAt1\":true,\"columnsStartAt1\":true,\"supportsVariableType\":true,\"supportsVariablePaging\":true,\"supportsRunInTerminalRequest\":true,\"locale\":\"en-us\",\"supportsProgressReporting\":true},\"type\":\"request\",\"seq\":1}");
        tester.compareReceivedMessages(
                "{\"event\":\"initialized\",\"type\":\"event\"}",
                "{\"success\":true,\"type\":\"response\",\"body\":{\"supportsConditionalBreakpoints\":true,\"supportsLoadedSourcesRequest\":true,\"supportsFunctionBreakpoints\":true,\"supportsExceptionInfoRequest\":true,\"supportsBreakpointLocationsRequest\":true,\"supportsHitConditionalBreakpoints\":true,\"supportsLogPoints\":true,\"supportsSetVariable\":true,\"supportsConfigurationDoneRequest\":true,\"exceptionBreakpointFilters\":[{\"filter\":\"all\",\"label\":\"All Exceptions\"},{\"filter\":\"uncaught\",\"label\":\"Uncaught Exceptions\"}]},\"request_seq\":1,\"command\":\"initialize\"}"
        );
        tester.sendMessage("{\"command\":\"attach\",\"arguments\":{\"type\":\"graalvm\",\"request\":\"attach\",\"name\":\"Attach\",\"port\":9229,\"protocol\":\"chromeDevTools\"},\"type\":\"request\",\"seq\":2}");
        tester.compareReceivedMessages("{\"event\":\"output\",\"body\":{\"output\":\"Debugger attached.\",\"category\":\"stderr\"},\"type\":\"event\"}", "{\"success\":true,\"type\":\"response\",\"request_seq\":2,\"command\":\"attach\"}");
        tester.sendMessage("{\"command\":\"loadedSources\",\"type\":\"request\",\"seq\":3}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"sources\":[]},\"type\":\"response\",\"request_seq\":3,\"command\":\"loadedSources\",\"seq\":5}");
        tester.sendMessage("{\"command\":\"setBreakpoints\",\"arguments\":{\"source\":" + sourceJson + ",\"lines\":[12],\"breakpoints\":[{\"line\":12}],\"sourceModified\":false},\"type\":\"request\",\"seq\":4}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"breakpoints\":[{\"line\":12,\"verified\":false,\"id\":1}]},\"type\":\"response\",\"request_seq\":4,\"command\":\"setBreakpoints\",\"seq\":6}");
        tester.sendMessage("{\"command\":\"configurationDone\",\"type\":\"request\",\"seq\":5}");
        tester.compareReceivedMessages("{\"success\":true,\"type\":\"response\",\"request_seq\":5,\"command\":\"configurationDone\",\"seq\":7}");
        tester.eval(source);
        tester.compareReceivedMessages("{\"event\":\"thread\",\"body\":{\"threadId\":1,\"reason\":\"started\"},\"type\":\"event\",\"seq\":8}");
        tester.compareReceivedMessages(
                "{\"event\":\"loadedSource\",\"body\":{\"reason\":\"new\",\"source\":{\"sourceReference\":1,\"name\":\"SL builtin\"}},\"type\":\"event\",\"seq\":9}",
                "{\"event\":\"loadedSource\",\"body\":{\"reason\":\"new\",\"source\":" + sourceJson + "},\"type\":\"event\",\"seq\":10}"
        );
        tester.compareReceivedMessages("{\"event\":\"breakpoint\",\"body\":{\"reason\":\"changed\",\"breakpoint\":{\"endLine\":12,\"endColumn\":7,\"line\":12,\"verified\":true,\"column\":3,\"id\":1}},\"type\":\"event\",\"seq\":11}");
        // Suspend at the breakpoint:
        tester.compareReceivedMessages("{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"breakpoint\",\"description\":\"Paused on breakpoint\"},\"type\":\"event\",\"seq\":12}");
        tester.sendMessage("{\"command\":\"threads\",\"type\":\"request\",\"seq\":6}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"threads\":[{\"name\":\"testRunner\",\"id\":1}]},\"type\":\"response\",\"request_seq\":6,\"command\":\"threads\",\"seq\":13}");
        tester.sendMessage("{\"command\":\"stackTrace\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":7}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"stackFrames\":[{\"line\":12,\"name\":\"factorial\",\"column\":3,\"id\":1,\"source\":" + sourceJson + "},{\"line\":3,\"name\":\"main\",\"column\":7,\"id\":2,\"source\":" + sourceJson + "}],\"totalFrames\":2},\"type\":\"response\",\"request_seq\":7,\"command\":\"stackTrace\",\"seq\":14}");
        // Set additional breakpoint:
        tester.sendMessage("{\"command\":\"setBreakpoints\",\"arguments\":{\"source\":" + sourceJson + ",\"lines\":[8,12],\"breakpoints\":[{\"line\":8},{\"line\":12}],\"sourceModified\":false},\"type\":\"request\",\"seq\":8}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"breakpoints\":[{\"endLine\":8,\"endColumn\":10,\"line\":8,\"verified\":true,\"column\":3,\"id\":2},{\"endLine\":12,\"endColumn\":7,\"line\":12,\"verified\":true,\"column\":3,\"id\":1}]},\"type\":\"response\",\"request_seq\":8,\"command\":\"setBreakpoints\",\"seq\":15}");
        // Continue
        tester.sendMessage("{\"command\":\"continue\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":9}");
        tester.compareReceivedMessages(
                "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                "{\"success\":true,\"body\":{\"allThreadsContinued\":false},\"type\":\"response\",\"request_seq\":9,\"command\":\"continue\"}",
                "{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"breakpoint\",\"description\":\"Paused on breakpoint\"},\"type\":\"event\"}"
        );
        tester.sendMessage("{\"command\":\"threads\",\"type\":\"request\",\"seq\":10}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"threads\":[{\"name\":\"testRunner\",\"id\":1}]},\"type\":\"response\",\"request_seq\":10,\"command\":\"threads\",\"seq\":19}");
        tester.sendMessage("{\"command\":\"stackTrace\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":11}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"stackFrames\":[{\"line\":8,\"name\":\"main\",\"column\":3,\"id\":1,\"source\":" + sourceJson + "}],\"totalFrames\":1},\"type\":\"response\",\"request_seq\":11,\"command\":\"stackTrace\",\"seq\":20}");
        // Continue to finish:
        tester.sendMessage("{\"command\":\"continue\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":12}");
        tester.compareReceivedMessages(
                "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                "{\"success\":true,\"body\":{\"allThreadsContinued\":false},\"type\":\"response\",\"request_seq\":12,\"command\":\"continue\"}"
        );
        tester.finish();
    }

    @Test
    public void testBreakpointsBySourceReferences() throws Exception {
        tester = DAPTester.start(true);
        Source source = Source.newBuilder("sl", CODE1, "SLTest.sl").build();
        tester.sendMessage("{\"command\":\"initialize\",\"arguments\":{\"clientID\":\"DAPTester\",\"clientName\":\"DAP Tester\",\"adapterID\":\"graalvm\",\"pathFormat\":\"path\",\"linesStartAt1\":true,\"columnsStartAt1\":true,\"supportsVariableType\":true,\"supportsVariablePaging\":true,\"supportsRunInTerminalRequest\":true,\"locale\":\"en-us\",\"supportsProgressReporting\":true},\"type\":\"request\",\"seq\":1}");
        tester.compareReceivedMessages(
                "{\"event\":\"initialized\",\"type\":\"event\"}",
                "{\"success\":true,\"type\":\"response\",\"body\":{\"supportsConditionalBreakpoints\":true,\"supportsLoadedSourcesRequest\":true,\"supportsFunctionBreakpoints\":true,\"supportsExceptionInfoRequest\":true,\"supportsBreakpointLocationsRequest\":true,\"supportsHitConditionalBreakpoints\":true,\"supportsLogPoints\":true,\"supportsSetVariable\":true,\"supportsConfigurationDoneRequest\":true,\"exceptionBreakpointFilters\":[{\"filter\":\"all\",\"label\":\"All Exceptions\"},{\"filter\":\"uncaught\",\"label\":\"Uncaught Exceptions\"}]},\"request_seq\":1,\"command\":\"initialize\"}"
        );
        tester.sendMessage("{\"command\":\"attach\",\"arguments\":{\"type\":\"graalvm\",\"request\":\"attach\",\"name\":\"Attach\",\"port\":9229,\"protocol\":\"chromeDevTools\"},\"type\":\"request\",\"seq\":2}");
        tester.compareReceivedMessages("{\"event\":\"output\",\"body\":{\"output\":\"Debugger attached.\",\"category\":\"stderr\"},\"type\":\"event\"}", "{\"success\":true,\"type\":\"response\",\"request_seq\":2,\"command\":\"attach\"}");
        tester.sendMessage("{\"command\":\"loadedSources\",\"type\":\"request\",\"seq\":3}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"sources\":[]},\"type\":\"response\",\"request_seq\":3,\"command\":\"loadedSources\",\"seq\":5}");
        tester.sendMessage("{\"command\":\"configurationDone\",\"type\":\"request\",\"seq\":4}");
        tester.compareReceivedMessages("{\"success\":true,\"type\":\"response\",\"request_seq\":4,\"command\":\"configurationDone\",\"seq\":6}");
        tester.eval(source);
        tester.compareReceivedMessages("{\"event\":\"thread\",\"body\":{\"threadId\":1,\"reason\":\"started\"},\"type\":\"event\",\"seq\":7}");
        tester.compareReceivedMessages(
                "{\"event\":\"loadedSource\",\"body\":{\"reason\":\"new\",\"source\":{\"sourceReference\":1,\"name\":\"SL builtin\"}},\"type\":\"event\",\"seq\":8}",
                "{\"event\":\"loadedSource\",\"body\":{\"reason\":\"new\",\"source\":{\"sourceReference\":2,\"name\":\"SLTest.sl\"}},\"type\":\"event\",\"seq\":9}"
        );
        // Suspend at the beginning of the script:
        tester.compareReceivedMessages("{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"debugger_statement\",\"description\":\"Paused on debugger statement\"},\"type\":\"event\",\"seq\":10}");
        tester.sendMessage("{\"command\":\"threads\",\"type\":\"request\",\"seq\":5}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"threads\":[{\"name\":\"testRunner\",\"id\":1}]},\"type\":\"response\",\"request_seq\":5,\"command\":\"threads\",\"seq\":11}");
        tester.sendMessage("{\"command\":\"stackTrace\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":6}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"stackFrames\":[{\"line\":2,\"name\":\"main\",\"column\":3,\"id\":1,\"source\":{\"sourceReference\":2,\"name\":\"SLTest.sl\"}}],\"totalFrames\":1},\"type\":\"response\",\"request_seq\":6,\"command\":\"stackTrace\",\"seq\":12}");
        // Set breakpoint:
        tester.sendMessage("{\"command\":\"setBreakpoints\",\"arguments\":{\"source\":{\"name\":\"SLTest.sl\",\"sourceReference\":2},\"lines\":[12],\"breakpoints\":[{\"line\":12}],\"sourceModified\":false},\"type\":\"request\",\"seq\":7}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"breakpoints\":[{\"endLine\":12,\"endColumn\":7,\"line\":12,\"verified\":true,\"column\":3,\"id\":1}]},\"type\":\"response\",\"request_seq\":7,\"command\":\"setBreakpoints\",\"seq\":13}");
        // Continue and suspend at the breakpoint:
        tester.sendMessage("{\"command\":\"continue\",\"arguments\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"request\",\"seq\":8}");
        tester.compareReceivedMessages(
                "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                "{\"success\":true,\"body\":{\"allThreadsContinued\":false},\"type\":\"response\",\"request_seq\":8,\"command\":\"continue\"}",
                "{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"breakpoint\",\"description\":\"Paused on breakpoint\"},\"type\":\"event\"}"
        );
        tester.sendMessage("{\"command\":\"threads\",\"type\":\"request\",\"seq\":9}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"threads\":[{\"name\":\"testRunner\",\"id\":1}]},\"type\":\"response\",\"request_seq\":9,\"command\":\"threads\",\"seq\":17}");
        tester.sendMessage("{\"command\":\"stackTrace\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":10}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"stackFrames\":[{\"line\":12,\"name\":\"factorial\",\"column\":3,\"id\":1,\"source\":{\"sourceReference\":2,\"name\":\"SLTest.sl\"}},{\"line\":3,\"name\":\"main\",\"column\":7,\"id\":2,\"source\":{\"sourceReference\":2,\"name\":\"SLTest.sl\"}}],\"totalFrames\":2},\"type\":\"response\",\"request_seq\":10,\"command\":\"stackTrace\",\"seq\":18}");
        // Set additional breakpoint:
        tester.sendMessage("{\"command\":\"setBreakpoints\",\"arguments\":{\"source\":{\"name\":\"SLTest.sl\",\"sourceReference\":2},\"lines\":[8,12],\"breakpoints\":[{\"line\":8},{\"line\":12}],\"sourceModified\":false},\"type\":\"request\",\"seq\":11}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"breakpoints\":[{\"endLine\":8,\"endColumn\":10,\"line\":8,\"verified\":true,\"column\":3,\"id\":2},{\"endLine\":12,\"endColumn\":7,\"line\":12,\"verified\":true,\"column\":3,\"id\":1}]},\"type\":\"response\",\"request_seq\":11,\"command\":\"setBreakpoints\",\"seq\":19}");
        // Continue
        tester.sendMessage("{\"command\":\"continue\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":12}");
        tester.compareReceivedMessages(
                "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                "{\"success\":true,\"body\":{\"allThreadsContinued\":false},\"type\":\"response\",\"request_seq\":12,\"command\":\"continue\"}",
                "{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"breakpoint\",\"description\":\"Paused on breakpoint\"},\"type\":\"event\"}"
        );
        tester.sendMessage("{\"command\":\"threads\",\"type\":\"request\",\"seq\":13}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"threads\":[{\"name\":\"testRunner\",\"id\":1}]},\"type\":\"response\",\"request_seq\":13,\"command\":\"threads\",\"seq\":23}");
        tester.sendMessage("{\"command\":\"stackTrace\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":14}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"stackFrames\":[{\"line\":8,\"name\":\"main\",\"column\":3,\"id\":1,\"source\":{\"sourceReference\":2,\"name\":\"SLTest.sl\"}}],\"totalFrames\":1},\"type\":\"response\",\"request_seq\":14,\"command\":\"stackTrace\",\"seq\":24}");
        // Continue to finish:
        tester.sendMessage("{\"command\":\"continue\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":15}");
        tester.compareReceivedMessages(
                "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                "{\"success\":true,\"body\":{\"allThreadsContinued\":false},\"type\":\"response\",\"request_seq\":15,\"command\":\"continue\"}"
        );
        tester.finish();
    }

    @Test
    public void testBreakpointRemoval() throws Exception {
        tester = DAPTester.start(false);
        File sourceFile = createTemporaryFile(CODE2);
        Source source = Source.newBuilder("sl", sourceFile).build();
        String sourceJson = "{\"name\":\"" + sourceFile.getName() + "\",\"path\":\"" + getFilePath(sourceFile) + "\"}";
        tester.sendMessage("{\"command\":\"initialize\",\"arguments\":{\"clientID\":\"DAPTester\",\"clientName\":\"DAP Tester\",\"adapterID\":\"graalvm\",\"pathFormat\":\"path\",\"linesStartAt1\":true,\"columnsStartAt1\":true,\"supportsVariableType\":true,\"supportsVariablePaging\":true,\"supportsRunInTerminalRequest\":true,\"locale\":\"en-us\",\"supportsProgressReporting\":true},\"type\":\"request\",\"seq\":1}");
        tester.compareReceivedMessages(
                "{\"event\":\"initialized\",\"type\":\"event\"}",
                "{\"success\":true,\"type\":\"response\",\"body\":{\"supportsConditionalBreakpoints\":true,\"supportsLoadedSourcesRequest\":true,\"supportsFunctionBreakpoints\":true,\"supportsExceptionInfoRequest\":true,\"supportsBreakpointLocationsRequest\":true,\"supportsHitConditionalBreakpoints\":true,\"supportsLogPoints\":true,\"supportsSetVariable\":true,\"supportsConfigurationDoneRequest\":true,\"exceptionBreakpointFilters\":[{\"filter\":\"all\",\"label\":\"All Exceptions\"},{\"filter\":\"uncaught\",\"label\":\"Uncaught Exceptions\"}]},\"request_seq\":1,\"command\":\"initialize\"}"
        );
        tester.sendMessage("{\"command\":\"attach\",\"arguments\":{\"type\":\"graalvm\",\"request\":\"attach\",\"name\":\"Attach\",\"port\":9229,\"protocol\":\"chromeDevTools\"},\"type\":\"request\",\"seq\":2}");
        tester.compareReceivedMessages("{\"event\":\"output\",\"body\":{\"output\":\"Debugger attached.\",\"category\":\"stderr\"},\"type\":\"event\"}", "{\"success\":true,\"type\":\"response\",\"request_seq\":2,\"command\":\"attach\"}");
        tester.sendMessage("{\"command\":\"loadedSources\",\"type\":\"request\",\"seq\":3}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"sources\":[]},\"type\":\"response\",\"request_seq\":3,\"command\":\"loadedSources\",\"seq\":5}");
        tester.sendMessage("{\"command\":\"setBreakpoints\",\"arguments\":{\"source\":" + sourceJson + ",\"lines\":[4,10],\"breakpoints\":[{\"line\":4},{\"line\":10}],\"sourceModified\":false},\"type\":\"request\",\"seq\":4}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"breakpoints\":[{\"line\":4,\"verified\":false,\"id\":1},{\"line\":10,\"verified\":false,\"id\":2}]},\"type\":\"response\",\"request_seq\":4,\"command\":\"setBreakpoints\",\"seq\":6}");
        tester.sendMessage("{\"command\":\"configurationDone\",\"type\":\"request\",\"seq\":5}");
        tester.compareReceivedMessages("{\"success\":true,\"type\":\"response\",\"request_seq\":5,\"command\":\"configurationDone\",\"seq\":7}");
        tester.eval(source);
        tester.compareReceivedMessages("{\"event\":\"thread\",\"body\":{\"threadId\":1,\"reason\":\"started\"},\"type\":\"event\",\"seq\":8}");
        tester.compareReceivedMessages(
                "{\"event\":\"loadedSource\",\"body\":{\"reason\":\"new\",\"source\":{\"sourceReference\":1,\"name\":\"SL builtin\"}},\"type\":\"event\",\"seq\":9}",
                "{\"event\":\"loadedSource\",\"body\":{\"reason\":\"new\",\"source\":" + sourceJson + "},\"type\":\"event\",\"seq\":10}"
        );
        tester.compareReceivedMessages(
                "{\"event\":\"breakpoint\",\"body\":{\"reason\":\"changed\",\"breakpoint\":{\"endLine\":4,\"endColumn\":14,\"line\":4,\"verified\":true,\"column\":10,\"id\":1}},\"type\":\"event\"}",
                "{\"event\":\"breakpoint\",\"body\":{\"reason\":\"changed\",\"breakpoint\":{\"endLine\":10,\"endColumn\":10,\"line\":10,\"verified\":true,\"column\":3,\"id\":2}},\"type\":\"event\"}");
        // Suspend at the first breakpoint:
        tester.compareReceivedMessages("{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"breakpoint\",\"description\":\"Paused on breakpoint\"},\"type\":\"event\",\"seq\":13}");
        tester.sendMessage("{\"command\":\"threads\",\"type\":\"request\",\"seq\":6}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"threads\":[{\"name\":\"testRunner\",\"id\":1}]},\"type\":\"response\",\"request_seq\":6,\"command\":\"threads\",\"seq\":14}");
        tester.sendMessage("{\"command\":\"stackTrace\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":7}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"stackFrames\":[{\"line\":4,\"name\":\"main\",\"column\":10,\"id\":1,\"source\":" + sourceJson + "}],\"totalFrames\":1},\"type\":\"response\",\"request_seq\":7,\"command\":\"stackTrace\",\"seq\":15}");
        // Step over to while cycle:
        tester.sendMessage("{\"command\":\"next\",\"arguments\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"request\",\"seq\":8}");
        tester.compareReceivedMessages(
                "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                "{\"success\":true,\"type\":\"response\",\"request_seq\":8,\"command\":\"next\"}",
                "{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"debugger_statement\",\"description\":\"Paused on debugger statement\"},\"type\":\"event\"}"
        );
        tester.sendMessage("{\"command\":\"threads\",\"type\":\"request\",\"seq\":9}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"threads\":[{\"name\":\"testRunner\",\"id\":1}]},\"type\":\"response\",\"request_seq\":9,\"command\":\"threads\",\"seq\":19}");
        tester.sendMessage("{\"command\":\"stackTrace\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":10}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"stackFrames\":[{\"line\":5,\"name\":\"main\",\"column\":5,\"id\":1,\"source\":" + sourceJson + "}],\"totalFrames\":1},\"type\":\"response\",\"request_seq\":10,\"command\":\"stackTrace\",\"seq\":20}");
        // Step over the method with breakpoint, step over not finished, the second breakpoint is hit:
        tester.sendMessage("{\"command\":\"next\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":11}");
        tester.compareReceivedMessages(
                "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                "{\"success\":true,\"type\":\"response\",\"request_seq\":11,\"command\":\"next\"}",
                "{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"breakpoint\",\"description\":\"Paused on breakpoint\"},\"type\":\"event\"}"
        );
        tester.sendMessage("{\"command\":\"threads\",\"type\":\"request\",\"seq\":12}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"threads\":[{\"name\":\"testRunner\",\"id\":1}]},\"type\":\"response\",\"request_seq\":12,\"command\":\"threads\",\"seq\":24}");
        tester.sendMessage("{\"command\":\"stackTrace\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":13}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"stackFrames\":[{\"line\":10,\"name\":\"fceWithBP\",\"column\":3,\"id\":1,\"source\":" + sourceJson + "},{\"line\":5,\"name\":\"main\",\"column\":5,\"id\":2,\"source\":" + sourceJson + "}],\"totalFrames\":2},\"type\":\"response\",\"request_seq\":13,\"command\":\"stackTrace\",\"seq\":25}");
        // Remove the second breakpoint
        tester.sendMessage("{\"command\":\"setBreakpoints\",\"arguments\":{\"source\":" + sourceJson + ",\"lines\":[4],\"breakpoints\":[{\"line\":4}],\"sourceModified\":false},\"type\":\"request\",\"seq\":14}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"breakpoints\":[{\"endLine\":4,\"endColumn\":14,\"line\":4,\"verified\":true,\"column\":10,\"id\":1}]},\"type\":\"response\",\"request_seq\":14,\"command\":\"setBreakpoints\",\"seq\":26}");
        // Continue, the first breakpoint is hit again:
        tester.sendMessage("{\"command\":\"continue\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":15}");
        tester.compareReceivedMessages(
                "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                "{\"success\":true,\"body\":{\"allThreadsContinued\":false},\"type\":\"response\",\"request_seq\":15,\"command\":\"continue\"}",
                "{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"breakpoint\",\"description\":\"Paused on breakpoint\"},\"type\":\"event\"}"
        );
        tester.sendMessage("{\"command\":\"threads\",\"type\":\"request\",\"seq\":16}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"threads\":[{\"name\":\"testRunner\",\"id\":1}]},\"type\":\"response\",\"request_seq\":16,\"command\":\"threads\",\"seq\":30}");
        tester.sendMessage("{\"command\":\"stackTrace\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":17}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"stackFrames\":[{\"line\":4,\"name\":\"main\",\"column\":10,\"id\":1,\"source\":" + sourceJson + "}],\"totalFrames\":1},\"type\":\"response\",\"request_seq\":17,\"command\":\"stackTrace\",\"seq\":31}");
        // Step over to while cycle:
        tester.sendMessage("{\"command\":\"next\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":18}");
        tester.compareReceivedMessages(
                "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                "{\"success\":true,\"type\":\"response\",\"request_seq\":18,\"command\":\"next\"}",
                "{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"debugger_statement\",\"description\":\"Paused on debugger statement\"},\"type\":\"event\"}"
        );
        tester.sendMessage("{\"command\":\"threads\",\"type\":\"request\",\"seq\":19}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"threads\":[{\"name\":\"testRunner\",\"id\":1}]},\"type\":\"response\",\"request_seq\":19,\"command\":\"threads\",\"seq\":35}");
        tester.sendMessage("{\"command\":\"stackTrace\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":20}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"stackFrames\":[{\"line\":5,\"name\":\"main\",\"column\":5,\"id\":1,\"source\":" + sourceJson + "}],\"totalFrames\":1},\"type\":\"response\",\"request_seq\":20,\"command\":\"stackTrace\",\"seq\":36}");
        // Step over the method with removed breakpoint:
        tester.sendMessage("{\"command\":\"next\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":21}");
        tester.compareReceivedMessages(
                "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                "{\"success\":true,\"type\":\"response\",\"request_seq\":21,\"command\":\"next\"}",
                "{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"debugger_statement\",\"description\":\"Paused on debugger statement\"},\"type\":\"event\"}"
        );
        tester.sendMessage("{\"command\":\"threads\",\"type\":\"request\",\"seq\":22}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"threads\":[{\"name\":\"testRunner\",\"id\":1}]},\"type\":\"response\",\"request_seq\":22,\"command\":\"threads\",\"seq\":40}");
        tester.sendMessage("{\"command\":\"stackTrace\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":23}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"stackFrames\":[{\"line\":6,\"name\":\"main\",\"column\":5,\"id\":1,\"source\":" + sourceJson + "}],\"totalFrames\":1},\"type\":\"response\",\"request_seq\":23,\"command\":\"stackTrace\",\"seq\":41}");
        // Remove the firts breakpoint:
        tester.sendMessage("{\"command\":\"setBreakpoints\",\"arguments\":{\"source\":" + sourceJson + ",\"lines\":[],\"breakpoints\":[],\"sourceModified\":false},\"type\":\"request\",\"seq\":24}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"breakpoints\":[]},\"type\":\"response\",\"request_seq\":24,\"command\":\"setBreakpoints\",\"seq\":42}");
        // Continue to finish:
        tester.sendMessage("{\"command\":\"continue\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":25}");
        tester.compareReceivedMessages(
                "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                "{\"success\":true,\"body\":{\"allThreadsContinued\":false},\"type\":\"response\",\"request_seq\":25,\"command\":\"continue\"}"
        );
        tester.finish();
    }

    @Test
    public void testGuestFunctionBreakpoints() throws Exception {
        tester = DAPTester.start(true);
        Source source = Source.newBuilder("sl", GUEST_FUNCTIONS, "SLTest.sl").uri(testURI).build();
        tester.sendMessage("{\"command\":\"initialize\",\"arguments\":{\"clientID\":\"DAPTester\",\"clientName\":\"DAP Tester\",\"adapterID\":\"graalvm\",\"pathFormat\":\"path\",\"linesStartAt1\":true,\"columnsStartAt1\":true,\"supportsVariableType\":true,\"supportsVariablePaging\":true,\"supportsRunInTerminalRequest\":true,\"locale\":\"en-us\",\"supportsProgressReporting\":true},\"type\":\"request\",\"seq\":1}");
        tester.compareReceivedMessages(
                "{\"event\":\"initialized\",\"type\":\"event\"}",
                "{\"success\":true,\"type\":\"response\",\"body\":{\"supportsConditionalBreakpoints\":true,\"supportsLoadedSourcesRequest\":true,\"supportsFunctionBreakpoints\":true,\"supportsExceptionInfoRequest\":true,\"supportsBreakpointLocationsRequest\":true,\"supportsHitConditionalBreakpoints\":true,\"supportsLogPoints\":true,\"supportsSetVariable\":true,\"supportsConfigurationDoneRequest\":true,\"exceptionBreakpointFilters\":[{\"filter\":\"all\",\"label\":\"All Exceptions\"},{\"filter\":\"uncaught\",\"label\":\"Uncaught Exceptions\"}]},\"request_seq\":1,\"command\":\"initialize\"}"
        );
        tester.sendMessage("{\"command\":\"attach\",\"arguments\":{\"type\":\"graalvm\",\"request\":\"attach\",\"name\":\"Attach\",\"port\":9229,\"protocol\":\"chromeDevTools\"},\"type\":\"request\",\"seq\":2}");
        tester.compareReceivedMessages("{\"event\":\"output\",\"body\":{\"output\":\"Debugger attached.\",\"category\":\"stderr\"},\"type\":\"event\"}", "{\"success\":true,\"type\":\"response\",\"request_seq\":2,\"command\":\"attach\"}");
        tester.sendMessage("{\"command\":\"loadedSources\",\"type\":\"request\",\"seq\":3}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"sources\":[]},\"type\":\"response\",\"request_seq\":3,\"command\":\"loadedSources\",\"seq\":5}");
        tester.sendMessage("{\"command\":\"configurationDone\",\"type\":\"request\",\"seq\":4}");
        tester.compareReceivedMessages("{\"success\":true,\"type\":\"response\",\"request_seq\":4,\"command\":\"configurationDone\",\"seq\":6}");
        tester.eval(source);
        tester.compareReceivedMessages("{\"event\":\"thread\",\"body\":{\"threadId\":1,\"reason\":\"started\"},\"type\":\"event\",\"seq\":7}");
        tester.compareReceivedMessages(
                "{\"event\":\"loadedSource\",\"body\":{\"reason\":\"new\",\"source\":{\"sourceReference\":1,\"name\":\"SL builtin\"}},\"type\":\"event\",\"seq\":8}",
                "{\"event\":\"loadedSource\",\"body\":{\"reason\":\"new\",\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}},\"type\":\"event\",\"seq\":9}"
        );
        // Suspend at the beginning of the script:
        tester.compareReceivedMessages("{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"debugger_statement\",\"description\":\"Paused on debugger statement\"},\"type\":\"event\",\"seq\":10}");
        tester.sendMessage("{\"command\":\"threads\",\"type\":\"request\",\"seq\":5}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"threads\":[{\"name\":\"testRunner\",\"id\":1}]},\"type\":\"response\",\"request_seq\":5,\"command\":\"threads\",\"seq\":11}");
        tester.sendMessage("{\"command\":\"stackTrace\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":6}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"stackFrames\":[{\"line\":2,\"name\":\"main\",\"column\":3,\"id\":1,\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}}],\"totalFrames\":1},\"type\":\"response\",\"request_seq\":6,\"command\":\"stackTrace\",\"seq\":12}");
        // Set function breakpoint:
        tester.sendMessage("{\"command\":\"setFunctionBreakpoints\",\"arguments\":{\"breakpoints\":[{\"name\":\"foo0\"}]},\"type\":\"request\",\"seq\":7}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"breakpoints\":[{\"verified\":false,\"id\":1}]},\"type\":\"response\",\"request_seq\":7,\"command\":\"setFunctionBreakpoints\",\"seq\":13}");
        // Continue and suspend at the breakpoint:
        tester.sendMessage("{\"command\":\"continue\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":8}");
        tester.compareReceivedMessages(
                "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                "{\"success\":true,\"body\":{\"allThreadsContinued\":false},\"type\":\"response\",\"request_seq\":8,\"command\":\"continue\"}",
                "{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"breakpoint\",\"description\":\"Paused on breakpoint\"},\"type\":\"event\"}"
        );
        tester.sendMessage("{\"command\":\"threads\",\"type\":\"request\",\"seq\":9}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"threads\":[{\"name\":\"testRunner\",\"id\":1}]},\"type\":\"response\",\"request_seq\":9,\"command\":\"threads\",\"seq\":17}");
        tester.sendMessage("{\"command\":\"stackTrace\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":10}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"stackFrames\":[{\"line\":7,\"name\":\"foo0\",\"column\":10,\"id\":1,\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}},{\"line\":2,\"name\":\"main\",\"column\":3,\"id\":2,\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}}],\"totalFrames\":2},\"type\":\"response\",\"request_seq\":10,\"command\":\"stackTrace\",\"seq\":18}");
        // Remove the breakpoint:
        tester.sendMessage("{\"command\":\"setFunctionBreakpoints\",\"arguments\":{\"breakpoints\":[]},\"type\":\"request\",\"seq\":11}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"breakpoints\":[]},\"type\":\"response\",\"request_seq\":11,\"command\":\"setFunctionBreakpoints\",\"seq\":19}");
        // Continue to finish:
        tester.sendMessage("{\"command\":\"continue\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":12}");
        tester.compareReceivedMessages(
                "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                "{\"success\":true,\"body\":{\"allThreadsContinued\":false},\"type\":\"response\",\"request_seq\":12,\"command\":\"continue\"}"
        );
        tester.finish();
    }

    @Test
    public void testBuiltInFunctionBreakpoints() throws Exception {
        tester = DAPTester.start(true);
        Source source = Source.newBuilder("sl", BUILTIN_FUNCTIONS, "SLTest.sl").uri(testURI).build();
        tester.sendMessage("{\"command\":\"initialize\",\"arguments\":{\"clientID\":\"DAPTester\",\"clientName\":\"DAP Tester\",\"adapterID\":\"graalvm\",\"pathFormat\":\"path\",\"linesStartAt1\":true,\"columnsStartAt1\":true,\"supportsVariableType\":true,\"supportsVariablePaging\":true,\"supportsRunInTerminalRequest\":true,\"locale\":\"en-us\",\"supportsProgressReporting\":true},\"type\":\"request\",\"seq\":1}");
        tester.compareReceivedMessages(
                "{\"event\":\"initialized\",\"type\":\"event\"}",
                "{\"success\":true,\"type\":\"response\",\"body\":{\"supportsConditionalBreakpoints\":true,\"supportsLoadedSourcesRequest\":true,\"supportsFunctionBreakpoints\":true,\"supportsExceptionInfoRequest\":true,\"supportsBreakpointLocationsRequest\":true,\"supportsHitConditionalBreakpoints\":true,\"supportsLogPoints\":true,\"supportsSetVariable\":true,\"supportsConfigurationDoneRequest\":true,\"exceptionBreakpointFilters\":[{\"filter\":\"all\",\"label\":\"All Exceptions\"},{\"filter\":\"uncaught\",\"label\":\"Uncaught Exceptions\"}]},\"request_seq\":1,\"command\":\"initialize\"}"
        );
        tester.sendMessage("{\"command\":\"attach\",\"arguments\":{\"type\":\"graalvm\",\"request\":\"attach\",\"name\":\"Attach\",\"port\":9229,\"protocol\":\"chromeDevTools\"},\"type\":\"request\",\"seq\":2}");
        tester.compareReceivedMessages("{\"event\":\"output\",\"body\":{\"output\":\"Debugger attached.\",\"category\":\"stderr\"},\"type\":\"event\"}", "{\"success\":true,\"type\":\"response\",\"request_seq\":2,\"command\":\"attach\"}");
        tester.sendMessage("{\"command\":\"loadedSources\",\"type\":\"request\",\"seq\":3}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"sources\":[]},\"type\":\"response\",\"request_seq\":3,\"command\":\"loadedSources\",\"seq\":5}");
        tester.sendMessage("{\"command\":\"configurationDone\",\"type\":\"request\",\"seq\":4}");
        tester.compareReceivedMessages("{\"success\":true,\"type\":\"response\",\"request_seq\":4,\"command\":\"configurationDone\",\"seq\":6}");
        tester.eval(source);
        tester.compareReceivedMessages("{\"event\":\"thread\",\"body\":{\"threadId\":1,\"reason\":\"started\"},\"type\":\"event\",\"seq\":7}");
        tester.compareReceivedMessages(
                "{\"event\":\"loadedSource\",\"body\":{\"reason\":\"new\",\"source\":{\"sourceReference\":1,\"name\":\"SL builtin\"}},\"type\":\"event\",\"seq\":8}",
                "{\"event\":\"loadedSource\",\"body\":{\"reason\":\"new\",\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}},\"type\":\"event\",\"seq\":9}"
        );
        // Suspend at the beginning of the script:
        tester.compareReceivedMessages("{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"debugger_statement\",\"description\":\"Paused on debugger statement\"},\"type\":\"event\",\"seq\":10}");
        tester.sendMessage("{\"command\":\"threads\",\"type\":\"request\",\"seq\":5}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"threads\":[{\"name\":\"testRunner\",\"id\":1}]},\"type\":\"response\",\"request_seq\":5,\"command\":\"threads\",\"seq\":11}");
        tester.sendMessage("{\"command\":\"stackTrace\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":6}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"stackFrames\":[{\"line\":2,\"name\":\"main\",\"column\":3,\"id\":1,\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}}],\"totalFrames\":1},\"type\":\"response\",\"request_seq\":6,\"command\":\"stackTrace\",\"seq\":12}");
        // Set function breakpoint:
        tester.sendMessage("{\"command\":\"setFunctionBreakpoints\",\"arguments\":{\"breakpoints\":[{\"name\":\"isNull\"}]},\"type\":\"request\",\"seq\":7}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"breakpoints\":[{\"verified\":false,\"id\":1}]},\"type\":\"response\",\"request_seq\":7,\"command\":\"setFunctionBreakpoints\",\"seq\":13}");
        // Continue and suspend at the breakpoint:
        tester.sendMessage("{\"command\":\"continue\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":8}");
        tester.compareReceivedMessages(
                "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                "{\"success\":true,\"body\":{\"allThreadsContinued\":false},\"type\":\"response\",\"request_seq\":8,\"command\":\"continue\"}",
                "{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"breakpoint\",\"description\":\"Paused on breakpoint\"},\"type\":\"event\"}"
        );
        tester.sendMessage("{\"command\":\"threads\",\"type\":\"request\",\"seq\":9}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"threads\":[{\"name\":\"testRunner\",\"id\":1}]},\"type\":\"response\",\"request_seq\":9,\"command\":\"threads\",\"seq\":17}");
        tester.sendMessage("{\"command\":\"stackTrace\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":10}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"stackFrames\":[{\"line\":4,\"name\":\"main\",\"column\":3,\"id\":1,\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}}],\"totalFrames\":1},\"type\":\"response\",\"request_seq\":10,\"command\":\"stackTrace\",\"seq\":18}");
        // Remove the breakpoint:
        tester.sendMessage("{\"command\":\"setFunctionBreakpoints\",\"arguments\":{\"breakpoints\":[]},\"type\":\"request\",\"seq\":11}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"breakpoints\":[]},\"type\":\"response\",\"request_seq\":11,\"command\":\"setFunctionBreakpoints\",\"seq\":19}");
        // Continue to finish:
        tester.sendMessage("{\"command\":\"continue\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":12}");
        tester.compareReceivedMessages(
                "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                "{\"success\":true,\"body\":{\"allThreadsContinued\":false},\"type\":\"response\",\"request_seq\":12,\"command\":\"continue\"}"
        );
        tester.finish();
    }

    @Test
    public void testScopes() throws Exception {
        tester = DAPTester.start(true);
        Source source = Source.newBuilder("sl", CODE1, "SLTest.sl").uri(testURI).build();
        tester.sendMessage("{\"command\":\"initialize\",\"arguments\":{\"clientID\":\"DAPTester\",\"clientName\":\"DAP Tester\",\"adapterID\":\"graalvm\",\"pathFormat\":\"path\",\"linesStartAt1\":true,\"columnsStartAt1\":true,\"supportsVariableType\":true,\"supportsVariablePaging\":true,\"supportsRunInTerminalRequest\":true,\"locale\":\"en-us\",\"supportsProgressReporting\":true},\"type\":\"request\",\"seq\":1}");
        tester.compareReceivedMessages(
                "{\"event\":\"initialized\",\"type\":\"event\"}",
                "{\"success\":true,\"type\":\"response\",\"body\":{\"supportsConditionalBreakpoints\":true,\"supportsLoadedSourcesRequest\":true,\"supportsFunctionBreakpoints\":true,\"supportsExceptionInfoRequest\":true,\"supportsBreakpointLocationsRequest\":true,\"supportsHitConditionalBreakpoints\":true,\"supportsLogPoints\":true,\"supportsSetVariable\":true,\"supportsConfigurationDoneRequest\":true,\"exceptionBreakpointFilters\":[{\"filter\":\"all\",\"label\":\"All Exceptions\"},{\"filter\":\"uncaught\",\"label\":\"Uncaught Exceptions\"}]},\"request_seq\":1,\"command\":\"initialize\"}"
        );
        tester.sendMessage("{\"command\":\"attach\",\"arguments\":{\"type\":\"graalvm\",\"request\":\"attach\",\"name\":\"Attach\",\"port\":9229,\"protocol\":\"chromeDevTools\"},\"type\":\"request\",\"seq\":2}");
        tester.compareReceivedMessages("{\"event\":\"output\",\"body\":{\"output\":\"Debugger attached.\",\"category\":\"stderr\"},\"type\":\"event\"}", "{\"success\":true,\"type\":\"response\",\"request_seq\":2,\"command\":\"attach\"}");
        tester.sendMessage("{\"command\":\"loadedSources\",\"type\":\"request\",\"seq\":3}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"sources\":[]},\"type\":\"response\",\"request_seq\":3,\"command\":\"loadedSources\",\"seq\":5}");
        tester.sendMessage("{\"command\":\"configurationDone\",\"type\":\"request\",\"seq\":4}");
        tester.compareReceivedMessages("{\"success\":true,\"type\":\"response\",\"request_seq\":4,\"command\":\"configurationDone\",\"seq\":6}");
        tester.eval(source);
        tester.compareReceivedMessages("{\"event\":\"thread\",\"body\":{\"threadId\":1,\"reason\":\"started\"},\"type\":\"event\",\"seq\":7}");
        tester.compareReceivedMessages(
                "{\"event\":\"loadedSource\",\"body\":{\"reason\":\"new\",\"source\":{\"sourceReference\":1,\"name\":\"SL builtin\"}},\"type\":\"event\",\"seq\":8}",
                "{\"event\":\"loadedSource\",\"body\":{\"reason\":\"new\",\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}},\"type\":\"event\",\"seq\":9}"
        );
        // Suspend at the beginning of the script:
        tester.compareReceivedMessages("{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"debugger_statement\",\"description\":\"Paused on debugger statement\"},\"type\":\"event\",\"seq\":10}");
        tester.sendMessage("{\"command\":\"threads\",\"type\":\"request\",\"seq\":5}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"threads\":[{\"name\":\"testRunner\",\"id\":1}]},\"type\":\"response\",\"request_seq\":5,\"command\":\"threads\",\"seq\":11}");
        tester.sendMessage("{\"command\":\"stackTrace\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":6}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"stackFrames\":[{\"line\":2,\"name\":\"main\",\"column\":3,\"id\":1,\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}}],\"totalFrames\":1},\"type\":\"response\",\"request_seq\":6,\"command\":\"stackTrace\",\"seq\":12}");
        // Ask for the local scope variables at the beginning of main:
        tester.sendMessage("{\"command\":\"scopes\",\"arguments\":{\"frameId\":1},\"type\":\"request\",\"seq\":7}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"scopes\":[{\"name\":\"Local\",\"variablesReference\":2,\"expensive\":false},{\"name\":\"Global\",\"variablesReference\":3,\"expensive\":true}]},\"type\":\"response\",\"request_seq\":7,\"command\":\"scopes\",\"seq\":13}");
        tester.sendMessage("{\"command\":\"variables\",\"arguments\":{\"variablesReference\":2},\"type\":\"request\",\"seq\":8}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"variables\":[]},\"type\":\"response\",\"request_seq\":8,\"command\":\"variables\",\"seq\":14}");
        // Continue to the breakpoint set at line 5:
        tester.sendMessage("{\"command\":\"setBreakpoints\",\"arguments\":{\"source\":{\"name\":\"SLTest.sl\",\"sourceReference\":2},\"lines\":[5],\"breakpoints\":[{\"line\":5}],\"sourceModified\":false},\"type\":\"request\",\"seq\":9}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"breakpoints\":[{\"endLine\":5,\"endColumn\":13,\"line\":5,\"verified\":true,\"column\":5,\"id\":1}]},\"type\":\"response\",\"request_seq\":9,\"command\":\"setBreakpoints\",\"seq\":15}");
        tester.sendMessage("{\"command\":\"continue\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":10}");
        tester.compareReceivedMessages(
                "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                "{\"success\":true,\"body\":{\"allThreadsContinued\":false},\"type\":\"response\",\"request_seq\":10,\"command\":\"continue\"}",
                "{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"breakpoint\",\"description\":\"Paused on breakpoint\"},\"type\":\"event\"}"
        );
        tester.sendMessage("{\"command\":\"threads\",\"type\":\"request\",\"seq\":11}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"threads\":[{\"name\":\"testRunner\",\"id\":1}]},\"type\":\"response\",\"request_seq\":11,\"command\":\"threads\",\"seq\":19}");
        tester.sendMessage("{\"command\":\"stackTrace\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":12}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"stackFrames\":[{\"line\":5,\"name\":\"main\",\"column\":5,\"id\":1,\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}}],\"totalFrames\":1},\"type\":\"response\",\"request_seq\":12,\"command\":\"stackTrace\",\"seq\":20}");
        // Ask for the local scope variables at line 5:
        tester.sendMessage("{\"command\":\"scopes\",\"arguments\":{\"frameId\":1},\"type\":\"request\",\"seq\":13}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"scopes\":[{\"name\":\"Local\",\"variablesReference\":2,\"expensive\":false},{\"name\":\"Global\",\"variablesReference\":3,\"expensive\":true}]},\"type\":\"response\",\"request_seq\":13,\"command\":\"scopes\",\"seq\":21}");
        tester.sendMessage("{\"command\":\"variables\",\"arguments\":{\"variablesReference\":2},\"type\":\"request\",\"seq\":14}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"variables\":[{\"name\":\"a\",\"variablesReference\":0,\"type\":\"Number\",\"value\":\"10\"},{\"name\":\"b\",\"variablesReference\":0,\"type\":\"Number\",\"value\":\"2\"}]},\"type\":\"response\",\"request_seq\":14,\"command\":\"variables\",\"seq\":22}");
        // Step over:
        tester.sendMessage("{\"command\":\"next\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":15}");
        tester.compareReceivedMessages(
                "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                "{\"success\":true,\"type\":\"response\",\"request_seq\":15,\"command\":\"next\"}",
                "{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"debugger_statement\",\"description\":\"Paused on debugger statement\"},\"type\":\"event\"}"
        );
        tester.sendMessage("{\"command\":\"threads\",\"type\":\"request\",\"seq\":16}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"threads\":[{\"name\":\"testRunner\",\"id\":1}]},\"type\":\"response\",\"request_seq\":16,\"command\":\"threads\",\"seq\":26}");
        tester.sendMessage("{\"command\":\"stackTrace\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":17}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"stackFrames\":[{\"line\":6,\"name\":\"main\",\"column\":5,\"id\":1,\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}}],\"totalFrames\":1},\"type\":\"response\",\"request_seq\":17,\"command\":\"stackTrace\",\"seq\":27}");
        // Ask for the local scope variables at line 6:
        tester.sendMessage("{\"command\":\"scopes\",\"arguments\":{\"frameId\":1},\"type\":\"request\",\"seq\":18}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"scopes\":[{\"name\":\"Block\",\"variablesReference\":2,\"expensive\":false},{\"name\":\"Local\",\"variablesReference\":3,\"expensive\":false},{\"name\":\"Global\",\"variablesReference\":4,\"expensive\":true}]},\"type\":\"response\",\"request_seq\":18,\"command\":\"scopes\",\"seq\":28}");
        tester.sendMessage("{\"command\":\"variables\",\"arguments\":{\"variablesReference\":2},\"type\":\"request\",\"seq\":19}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"variables\":[{\"name\":\"c\",\"variablesReference\":0,\"type\":\"Number\",\"value\":\"12\"}]},\"type\":\"response\",\"request_seq\":19,\"command\":\"variables\",\"seq\":29}");
        tester.sendMessage("{\"command\":\"variables\",\"arguments\":{\"variablesReference\":3},\"type\":\"request\",\"seq\":20}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"variables\":[{\"name\":\"a\",\"variablesReference\":0,\"type\":\"Number\",\"value\":\"10\"},{\"name\":\"b\",\"variablesReference\":0,\"type\":\"Number\",\"value\":\"2\"}]},\"type\":\"response\",\"request_seq\":20,\"command\":\"variables\",\"seq\":30}");
        // Remove the breakpoint:
        tester.sendMessage("{\"command\":\"setBreakpoints\",\"arguments\":{\"source\":{\"name\":\"SLTest.sl\",\"sourceReference\":2},\"lines\":[],\"breakpoints\":[],\"sourceModified\":false},\"type\":\"request\",\"seq\":21}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"breakpoints\":[]},\"type\":\"response\",\"request_seq\":21,\"command\":\"setBreakpoints\",\"seq\":31}");
        // Continue to finish:
        tester.sendMessage("{\"command\":\"continue\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":22}");
        tester.compareReceivedMessages(
                "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                "{\"success\":true,\"body\":{\"allThreadsContinued\":false},\"type\":\"response\",\"request_seq\":22,\"command\":\"continue\"}"
        );
        tester.finish();
    }

    @Test
    public void testNotSuspended() throws Exception {
        tester = DAPTester.start(true);
        Source source = Source.newBuilder("sl", CODE1, "SLTest.sl").uri(testURI).build();
        tester.sendMessage("{\"command\":\"initialize\",\"arguments\":{\"clientID\":\"DAPTester\",\"clientName\":\"DAP Tester\",\"adapterID\":\"graalvm\",\"pathFormat\":\"path\",\"linesStartAt1\":true,\"columnsStartAt1\":true,\"supportsVariableType\":true,\"supportsVariablePaging\":true,\"supportsRunInTerminalRequest\":true,\"locale\":\"en-us\",\"supportsProgressReporting\":true},\"type\":\"request\",\"seq\":1}");
        tester.compareReceivedMessages(
                "{\"event\":\"initialized\",\"type\":\"event\"}",
                "{\"success\":true,\"type\":\"response\",\"body\":{\"supportsConditionalBreakpoints\":true,\"supportsLoadedSourcesRequest\":true,\"supportsFunctionBreakpoints\":true,\"supportsExceptionInfoRequest\":true,\"supportsBreakpointLocationsRequest\":true,\"supportsHitConditionalBreakpoints\":true,\"supportsLogPoints\":true,\"supportsSetVariable\":true,\"supportsConfigurationDoneRequest\":true,\"exceptionBreakpointFilters\":[{\"filter\":\"all\",\"label\":\"All Exceptions\"},{\"filter\":\"uncaught\",\"label\":\"Uncaught Exceptions\"}]},\"request_seq\":1,\"command\":\"initialize\"}"
        );
        tester.sendMessage("{\"command\":\"attach\",\"arguments\":{\"type\":\"graalvm\",\"request\":\"attach\",\"name\":\"Attach\",\"port\":9229,\"protocol\":\"chromeDevTools\"},\"type\":\"request\",\"seq\":2}");
        tester.compareReceivedMessages("{\"event\":\"output\",\"body\":{\"output\":\"Debugger attached.\",\"category\":\"stderr\"},\"type\":\"event\"}", "{\"success\":true,\"type\":\"response\",\"request_seq\":2,\"command\":\"attach\"}");
        tester.sendMessage("{\"command\":\"loadedSources\",\"type\":\"request\",\"seq\":3}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"sources\":[]},\"type\":\"response\",\"request_seq\":3,\"command\":\"loadedSources\",\"seq\":5}");
        tester.sendMessage("{\"command\":\"configurationDone\",\"type\":\"request\",\"seq\":4}");
        tester.compareReceivedMessages("{\"success\":true,\"type\":\"response\",\"request_seq\":4,\"command\":\"configurationDone\",\"seq\":6}");
        tester.eval(source);
        tester.compareReceivedMessages("{\"event\":\"thread\",\"body\":{\"threadId\":1,\"reason\":\"started\"},\"type\":\"event\",\"seq\":7}");
        tester.compareReceivedMessages(
                "{\"event\":\"loadedSource\",\"body\":{\"reason\":\"new\",\"source\":{\"sourceReference\":1,\"name\":\"SL builtin\"}},\"type\":\"event\",\"seq\":8}",
                "{\"event\":\"loadedSource\",\"body\":{\"reason\":\"new\",\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}},\"type\":\"event\",\"seq\":9}"
        );
        // Suspend at the beginning of the script:
        tester.compareReceivedMessages("{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"debugger_statement\",\"description\":\"Paused on debugger statement\"},\"type\":\"event\",\"seq\":10}");
        tester.sendMessage("{\"command\":\"threads\",\"type\":\"request\",\"seq\":5}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"threads\":[{\"name\":\"testRunner\",\"id\":1}]},\"type\":\"response\",\"request_seq\":5,\"command\":\"threads\",\"seq\":11}");
        tester.sendMessage("{\"command\":\"stackTrace\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":6}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"stackFrames\":[{\"line\":2,\"name\":\"main\",\"column\":3,\"id\":1,\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}}],\"totalFrames\":1},\"type\":\"response\",\"request_seq\":6,\"command\":\"stackTrace\",\"seq\":12}");
        // Continue:
        tester.sendMessage("{\"command\":\"continue\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":7}");
        tester.compareReceivedMessages(
                "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                "{\"success\":true,\"body\":{\"allThreadsContinued\":false},\"type\":\"response\",\"request_seq\":7,\"command\":\"continue\"}"
        );
        // Try to evaluate not suspended:
        tester.sendMessage("{\"command\":\"evaluate\",\"arguments\":{\"expression\":\"app\",\"frameId\":2},\"type\":\"request\",\"seq\":8}");
        tester.compareReceivedMessages("{\"success\":false,\"body\":{\"error\":{\"format\":\"Stack frame not valid\",\"id\":2020}},\"type\":\"response\",\"message\":\"Stack frame not valid\",\"request_seq\":8,\"command\":\"evaluate\",\"seq\":15}");
        tester.finish();
    }

    @Test
    public void testReturnValue() throws Exception {
        tester = DAPTester.start(true);
        Source source = Source.newBuilder("sl", CODE_RET_VAL, "SLTest.sl").uri(testURI).build();
        tester.sendMessage("{\"command\":\"initialize\",\"arguments\":{\"clientID\":\"DAPTester\",\"clientName\":\"DAP Tester\",\"adapterID\":\"graalvm\",\"pathFormat\":\"path\",\"linesStartAt1\":true,\"columnsStartAt1\":true,\"supportsVariableType\":true,\"supportsVariablePaging\":true,\"supportsRunInTerminalRequest\":true,\"locale\":\"en-us\",\"supportsProgressReporting\":true},\"type\":\"request\",\"seq\":1}");
        tester.compareReceivedMessages(
                "{\"event\":\"initialized\",\"type\":\"event\"}",
                "{\"success\":true,\"type\":\"response\",\"body\":{\"supportsConditionalBreakpoints\":true,\"supportsLoadedSourcesRequest\":true,\"supportsFunctionBreakpoints\":true,\"supportsExceptionInfoRequest\":true,\"supportsBreakpointLocationsRequest\":true,\"supportsHitConditionalBreakpoints\":true,\"supportsLogPoints\":true,\"supportsSetVariable\":true,\"supportsConfigurationDoneRequest\":true,\"exceptionBreakpointFilters\":[{\"filter\":\"all\",\"label\":\"All Exceptions\"},{\"filter\":\"uncaught\",\"label\":\"Uncaught Exceptions\"}]},\"request_seq\":1,\"command\":\"initialize\"}"
        );
        tester.sendMessage("{\"command\":\"attach\",\"arguments\":{\"type\":\"graalvm\",\"request\":\"attach\",\"name\":\"Attach\",\"port\":9229,\"protocol\":\"chromeDevTools\"},\"type\":\"request\",\"seq\":2}");
        tester.compareReceivedMessages("{\"event\":\"output\",\"body\":{\"output\":\"Debugger attached.\",\"category\":\"stderr\"},\"type\":\"event\"}", "{\"success\":true,\"type\":\"response\",\"request_seq\":2,\"command\":\"attach\"}");
        tester.sendMessage("{\"command\":\"loadedSources\",\"type\":\"request\",\"seq\":3}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"sources\":[]},\"type\":\"response\",\"request_seq\":3,\"command\":\"loadedSources\",\"seq\":5}");
        tester.sendMessage("{\"command\":\"configurationDone\",\"type\":\"request\",\"seq\":4}");
        tester.compareReceivedMessages("{\"success\":true,\"type\":\"response\",\"request_seq\":4,\"command\":\"configurationDone\",\"seq\":6}");
        tester.eval(source);
        tester.compareReceivedMessages("{\"event\":\"thread\",\"body\":{\"threadId\":1,\"reason\":\"started\"},\"type\":\"event\",\"seq\":7}");
        tester.compareReceivedMessages(
                "{\"event\":\"loadedSource\",\"body\":{\"reason\":\"new\",\"source\":{\"sourceReference\":1,\"name\":\"SL builtin\"}},\"type\":\"event\",\"seq\":8}",
                "{\"event\":\"loadedSource\",\"body\":{\"reason\":\"new\",\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}},\"type\":\"event\",\"seq\":9}"
        );
        // Suspend at the beginning of the script:
        tester.compareReceivedMessages("{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"debugger_statement\",\"description\":\"Paused on debugger statement\"},\"type\":\"event\",\"seq\":10}");
        tester.sendMessage("{\"command\":\"threads\",\"type\":\"request\",\"seq\":5}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"threads\":[{\"name\":\"testRunner\",\"id\":1}]},\"type\":\"response\",\"request_seq\":5,\"command\":\"threads\",\"seq\":11}");
        tester.sendMessage("{\"command\":\"stackTrace\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":6}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"stackFrames\":[{\"line\":2,\"name\":\"main\",\"column\":3,\"id\":1,\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}}],\"totalFrames\":1},\"type\":\"response\",\"request_seq\":6,\"command\":\"stackTrace\",\"seq\":12}");
        // at main:2
        tester.sendMessage("{\"command\":\"stepIn\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":7}");
        tester.compareReceivedMessages(
                "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                "{\"success\":true,\"type\":\"response\",\"request_seq\":7,\"command\":\"stepIn\"}",
                "{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"debugger_statement\",\"description\":\"Paused on debugger statement\"},\"type\":\"event\"}"
        );
        tester.sendMessage("{\"command\":\"threads\",\"type\":\"request\",\"seq\":8}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"threads\":[{\"name\":\"testRunner\",\"id\":1}]},\"type\":\"response\",\"request_seq\":8,\"command\":\"threads\",\"seq\":16}");
        tester.sendMessage("{\"command\":\"stackTrace\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":9}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"stackFrames\":[{\"line\":6,\"name\":\"addThem\",\"column\":3,\"id\":1,\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}},{\"line\":2,\"name\":\"main\",\"column\":7,\"id\":2,\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}}],\"totalFrames\":2},\"type\":\"response\",\"request_seq\":9,\"command\":\"stackTrace\",\"seq\":17}");
        // at addThem:6
        tester.sendMessage("{\"command\":\"stepIn\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":10}");
        tester.compareReceivedMessages(
                "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                "{\"success\":true,\"type\":\"response\",\"request_seq\":10,\"command\":\"stepIn\"}",
                "{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"debugger_statement\",\"description\":\"Paused on debugger statement\"},\"type\":\"event\"}"
        );
        tester.sendMessage("{\"command\":\"threads\",\"type\":\"request\",\"seq\":11}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"threads\":[{\"name\":\"testRunner\",\"id\":1}]},\"type\":\"response\",\"request_seq\":11,\"command\":\"threads\",\"seq\":21}");
        tester.sendMessage("{\"command\":\"stackTrace\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":12}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"stackFrames\":[{\"line\":12,\"name\":\"fn\",\"column\":3,\"id\":1,\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}},{\"line\":6,\"name\":\"addThem\",\"column\":7,\"id\":2,\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}},{\"line\":2,\"name\":\"main\",\"column\":7,\"id\":3,\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}}],\"totalFrames\":3},\"type\":\"response\",\"request_seq\":12,\"command\":\"stackTrace\",\"seq\":22}");
        // at fn:12, step into steps to the end of fn and a return value is accessible
        tester.sendMessage("{\"command\":\"stepIn\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":13}");
        tester.compareReceivedMessages(
                "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                "{\"success\":true,\"type\":\"response\",\"request_seq\":13,\"command\":\"stepIn\"}",
                "{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"debugger_statement\",\"description\":\"Paused on debugger statement\"},\"type\":\"event\"}"
        );
        tester.sendMessage("{\"command\":\"threads\",\"type\":\"request\",\"seq\":14}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"threads\":[{\"name\":\"testRunner\",\"id\":1}]},\"type\":\"response\",\"request_seq\":14,\"command\":\"threads\",\"seq\":26}");
        tester.sendMessage("{\"command\":\"stackTrace\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":15}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"stackFrames\":[{\"line\":13,\"name\":\"fn\",\"column\":2,\"id\":1,\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}},{\"line\":6,\"name\":\"addThem\",\"column\":7,\"id\":2,\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}},{\"line\":2,\"name\":\"main\",\"column\":7,\"id\":3,\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}}],\"totalFrames\":3},\"type\":\"response\",\"request_seq\":15,\"command\":\"stackTrace\",\"seq\":27}");
        tester.sendMessage("{\"command\":\"scopes\",\"arguments\":{\"frameId\":1},\"type\":\"request\",\"seq\":16}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"scopes\":[{\"name\":\"Local\",\"variablesReference\":4,\"expensive\":false},{\"name\":\"Global\",\"variablesReference\":5,\"expensive\":true}]},\"type\":\"response\",\"request_seq\":16,\"command\":\"scopes\",\"seq\":28}");
        tester.sendMessage("{\"command\":\"variables\",\"arguments\":{\"variablesReference\":4},\"type\":\"request\",\"seq\":17}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"variables\":[{\"name\":\"Return value\",\"variablesReference\":0,\"type\":\"Number\",\"value\":\"1\"},{\"name\":\"n\",\"variablesReference\":0,\"type\":\"Number\",\"value\":\"1\"}]},\"type\":\"response\",\"request_seq\":17,\"command\":\"variables\",\"seq\":29}");
        // change return value from 1 to 10_000_000_000 (it must be long because of SL)
        tester.sendMessage("{\"command\":\"setVariable\",\"arguments\":{\"variablesReference\":4,\"name\":\"Return value\",\"value\":\"10000000000\"},\"type\":\"request\",\"seq\":18}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"type\":\"Number\",\"variablesReference\":0,\"value\":\"10000000000\"},\"type\":\"response\",\"request_seq\":18,\"command\":\"setVariable\",\"seq\":30}");
        // at fn:13, step into steps out from fn
        tester.sendMessage("{\"command\":\"stepIn\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":19}");
        tester.compareReceivedMessages(
                "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                "{\"success\":true,\"type\":\"response\",\"request_seq\":19,\"command\":\"stepIn\"}",
                "{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"debugger_statement\",\"description\":\"Paused on debugger statement\"},\"type\":\"event\"}"
        );
        tester.sendMessage("{\"command\":\"threads\",\"type\":\"request\",\"seq\":20}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"threads\":[{\"name\":\"testRunner\",\"id\":1}]},\"type\":\"response\",\"request_seq\":20,\"command\":\"threads\",\"seq\":34}");
        tester.sendMessage("{\"command\":\"stackTrace\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":21}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"stackFrames\":[{\"line\":6,\"name\":\"addThem\",\"column\":12,\"id\":1,\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}},{\"line\":2,\"name\":\"main\",\"column\":7,\"id\":2,\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}}],\"totalFrames\":2},\"type\":\"response\",\"request_seq\":21,\"command\":\"stackTrace\",\"seq\":35}");
        tester.sendMessage("{\"command\":\"scopes\",\"arguments\":{\"frameId\":1},\"type\":\"request\",\"seq\":22}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"scopes\":[{\"name\":\"Local\",\"variablesReference\":3,\"expensive\":false},{\"name\":\"Global\",\"variablesReference\":4,\"expensive\":true}]},\"type\":\"response\",\"request_seq\":22,\"command\":\"scopes\",\"seq\":36}");
        tester.sendMessage("{\"command\":\"variables\",\"arguments\":{\"variablesReference\":3},\"type\":\"request\",\"seq\":23}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"variables\":[{\"name\":\"a\",\"variablesReference\":0,\"type\":\"Number\",\"value\":\"1\"},{\"name\":\"b\",\"variablesReference\":0,\"type\":\"Number\",\"value\":\"2\"}]},\"type\":\"response\",\"request_seq\":23,\"command\":\"variables\",\"seq\":37}");
        // at addThem:6, step into steps to the next line and check that `a` is 10_000_000_000
        tester.sendMessage("{\"command\":\"stepIn\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":24}");
        tester.compareReceivedMessages(
                "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                "{\"success\":true,\"type\":\"response\",\"request_seq\":24,\"command\":\"stepIn\"}",
                "{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"debugger_statement\",\"description\":\"Paused on debugger statement\"},\"type\":\"event\"}"
        );
        tester.sendMessage("{\"command\":\"threads\",\"type\":\"request\",\"seq\":25}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"threads\":[{\"name\":\"testRunner\",\"id\":1}]},\"type\":\"response\",\"request_seq\":25,\"command\":\"threads\",\"seq\":41}");
        tester.sendMessage("{\"command\":\"stackTrace\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":26}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"stackFrames\":[{\"line\":7,\"name\":\"addThem\",\"column\":3,\"id\":1,\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}},{\"line\":2,\"name\":\"main\",\"column\":7,\"id\":2,\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}}],\"totalFrames\":2},\"type\":\"response\",\"request_seq\":26,\"command\":\"stackTrace\",\"seq\":42}");
        tester.sendMessage("{\"command\":\"scopes\",\"arguments\":{\"frameId\":1},\"type\":\"request\",\"seq\":27}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"scopes\":[{\"name\":\"Local\",\"variablesReference\":3,\"expensive\":false},{\"name\":\"Global\",\"variablesReference\":4,\"expensive\":true}]},\"type\":\"response\",\"request_seq\":27,\"command\":\"scopes\",\"seq\":43}");
        tester.sendMessage("{\"command\":\"variables\",\"arguments\":{\"variablesReference\":3},\"type\":\"request\",\"seq\":28}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"variables\":[{\"name\":\"a\",\"variablesReference\":0,\"type\":\"Number\",\"value\":\"10000000000\"},{\"name\":\"b\",\"variablesReference\":0,\"type\":\"Number\",\"value\":\"2\"}]},\"type\":\"response\",\"request_seq\":28,\"command\":\"variables\",\"seq\":44}");
        // at addThem:7
        tester.sendMessage("{\"command\":\"stepIn\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":29}");
        tester.compareReceivedMessages(
                "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                "{\"success\":true,\"type\":\"response\",\"request_seq\":29,\"command\":\"stepIn\"}",
                "{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"debugger_statement\",\"description\":\"Paused on debugger statement\"},\"type\":\"event\"}"
        );
        tester.sendMessage("{\"command\":\"threads\",\"type\":\"request\",\"seq\":30}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"threads\":[{\"name\":\"testRunner\",\"id\":1}]},\"type\":\"response\",\"request_seq\":30,\"command\":\"threads\",\"seq\":48}");
        tester.sendMessage("{\"command\":\"stackTrace\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":31}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"stackFrames\":[{\"line\":12,\"name\":\"fn\",\"column\":3,\"id\":1,\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}},{\"line\":7,\"name\":\"addThem\",\"column\":7,\"id\":2,\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}},{\"line\":2,\"name\":\"main\",\"column\":7,\"id\":3,\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}}],\"totalFrames\":3},\"type\":\"response\",\"request_seq\":31,\"command\":\"stackTrace\",\"seq\":49}");
        // at fn:10, step out from fn
        tester.sendMessage("{\"command\":\"stepOut\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":32}");
        tester.compareReceivedMessages(
                "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                "{\"success\":true,\"type\":\"response\",\"request_seq\":32,\"command\":\"stepOut\"}",
                "{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"debugger_statement\",\"description\":\"Paused on debugger statement\"},\"type\":\"event\"}"
        );
        tester.sendMessage("{\"command\":\"threads\",\"type\":\"request\",\"seq\":33}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"threads\":[{\"name\":\"testRunner\",\"id\":1}]},\"type\":\"response\",\"request_seq\":33,\"command\":\"threads\",\"seq\":53}");
        tester.sendMessage("{\"command\":\"stackTrace\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":34}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"stackFrames\":[{\"line\":7,\"name\":\"addThem\",\"column\":12,\"id\":1,\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}},{\"line\":2,\"name\":\"main\",\"column\":7,\"id\":2,\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}}],\"totalFrames\":2},\"type\":\"response\",\"request_seq\":34,\"command\":\"stackTrace\",\"seq\":54}");
        tester.sendMessage("{\"command\":\"scopes\",\"arguments\":{\"frameId\":1},\"type\":\"request\",\"seq\":35}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"scopes\":[{\"name\":\"Local\",\"variablesReference\":3,\"expensive\":false},{\"name\":\"Global\",\"variablesReference\":4,\"expensive\":true}]},\"type\":\"response\",\"request_seq\":35,\"command\":\"scopes\",\"seq\":55}");
        tester.sendMessage("{\"command\":\"variables\",\"arguments\":{\"variablesReference\":3},\"type\":\"request\",\"seq\":36}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"variables\":[{\"name\":\"a\",\"variablesReference\":0,\"type\":\"Number\",\"value\":\"10000000000\"},{\"name\":\"b\",\"variablesReference\":0,\"type\":\"Number\",\"value\":\"2\"}]},\"type\":\"response\",\"request_seq\":36,\"command\":\"variables\",\"seq\":56}");
        // Resume to finish:
        tester.sendMessage("{\"command\":\"continue\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":37}");
        tester.compareReceivedMessages(
                "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                "{\"success\":true,\"body\":{\"allThreadsContinued\":false},\"type\":\"response\",\"request_seq\":37,\"command\":\"continue\"}",
                "{\"event\":\"output\",\"body\":{\"output\":\"10000000002" + NL + "\",\"category\":\"stdout\"},\"type\":\"event\",\"seq\":59}"
        );
        tester.finish();
    }

    @Test
    public void testBreakpointCorrections() throws Exception {
        tester = DAPTester.start(true);
        Source source = Source.newBuilder("sl", CODE3, "SLTest.sl").uri(testURI).build();
        tester.sendMessage("{\"command\":\"initialize\",\"arguments\":{\"clientID\":\"DAPTester\",\"clientName\":\"DAP Tester\",\"adapterID\":\"graalvm\",\"pathFormat\":\"path\",\"linesStartAt1\":true,\"columnsStartAt1\":true,\"supportsVariableType\":true,\"supportsVariablePaging\":true,\"supportsRunInTerminalRequest\":true,\"locale\":\"en-us\",\"supportsProgressReporting\":true},\"type\":\"request\",\"seq\":1}");
        tester.compareReceivedMessages(
                "{\"event\":\"initialized\",\"type\":\"event\"}",
                "{\"success\":true,\"type\":\"response\",\"body\":{\"supportsConditionalBreakpoints\":true,\"supportsLoadedSourcesRequest\":true,\"supportsFunctionBreakpoints\":true,\"supportsExceptionInfoRequest\":true,\"supportsBreakpointLocationsRequest\":true,\"supportsHitConditionalBreakpoints\":true,\"supportsLogPoints\":true,\"supportsSetVariable\":true,\"supportsConfigurationDoneRequest\":true,\"exceptionBreakpointFilters\":[{\"filter\":\"all\",\"label\":\"All Exceptions\"},{\"filter\":\"uncaught\",\"label\":\"Uncaught Exceptions\"}]},\"request_seq\":1,\"command\":\"initialize\"}"
        );
        tester.sendMessage("{\"command\":\"attach\",\"arguments\":{\"type\":\"graalvm\",\"request\":\"attach\",\"name\":\"Attach\",\"port\":9229,\"protocol\":\"chromeDevTools\"},\"type\":\"request\",\"seq\":2}");
        tester.compareReceivedMessages("{\"event\":\"output\",\"body\":{\"output\":\"Debugger attached.\",\"category\":\"stderr\"},\"type\":\"event\"}", "{\"success\":true,\"type\":\"response\",\"request_seq\":2,\"command\":\"attach\"}");
        tester.sendMessage("{\"command\":\"loadedSources\",\"type\":\"request\",\"seq\":3}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"sources\":[]},\"type\":\"response\",\"request_seq\":3,\"command\":\"loadedSources\",\"seq\":5}");
        tester.sendMessage("{\"command\":\"configurationDone\",\"type\":\"request\",\"seq\":4}");
        tester.compareReceivedMessages("{\"success\":true,\"type\":\"response\",\"request_seq\":4,\"command\":\"configurationDone\",\"seq\":6}");
        tester.eval(source);
        tester.compareReceivedMessages("{\"event\":\"thread\",\"body\":{\"threadId\":1,\"reason\":\"started\"},\"type\":\"event\",\"seq\":7}");
        tester.compareReceivedMessages(
                "{\"event\":\"loadedSource\",\"body\":{\"reason\":\"new\",\"source\":{\"sourceReference\":1,\"name\":\"SL builtin\"}},\"type\":\"event\",\"seq\":8}",
                "{\"event\":\"loadedSource\",\"body\":{\"reason\":\"new\",\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}},\"type\":\"event\",\"seq\":9}"
        );
        // Suspend at the beginning of the script:
        tester.compareReceivedMessages("{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"debugger_statement\",\"description\":\"Paused on debugger statement\"},\"type\":\"event\",\"seq\":10}");
        tester.sendMessage("{\"command\":\"threads\",\"type\":\"request\",\"seq\":5}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"threads\":[{\"name\":\"testRunner\",\"id\":1}]},\"type\":\"response\",\"request_seq\":5,\"command\":\"threads\",\"seq\":11}");
        tester.sendMessage("{\"command\":\"stackTrace\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":6}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"stackFrames\":[{\"line\":2,\"name\":\"main\",\"column\":3,\"id\":1,\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}}],\"totalFrames\":1},\"type\":\"response\",\"request_seq\":6,\"command\":\"stackTrace\",\"seq\":12}");
        // Breakpoint before any statements moves to the first statement
        tester.sendMessage("{\"command\":\"setBreakpoints\",\"arguments\":{\"source\":{\"name\":\"SLTest.sl\",\"sourceReference\":2},\"lines\":[6],\"breakpoints\":[{\"line\":6}],\"sourceModified\":false},\"type\":\"request\",\"seq\":7}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"breakpoints\":[{\"endLine\":8,\"endColumn\":9,\"line\":7,\"verified\":true,\"column\":3,\"id\":1}]},\"type\":\"response\",\"request_seq\":7,\"command\":\"setBreakpoints\",\"seq\":13}");
        // Breakpoint at the second line of a statement moves to the first line of the statement
        tester.sendMessage("{\"command\":\"setBreakpoints\",\"arguments\":{\"source\":{\"name\":\"SLTest.sl\",\"sourceReference\":2},\"lines\":[6,10],\"breakpoints\":[{\"line\":6},{\"line\":10}],\"sourceModified\":false},\"type\":\"request\",\"seq\":8}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"breakpoints\":[{\"endLine\":8,\"endColumn\":9,\"line\":7,\"verified\":true,\"column\":3,\"id\":1},{\"endLine\":10,\"endColumn\":9,\"line\":9,\"verified\":true,\"column\":3,\"id\":2}]},\"type\":\"response\",\"request_seq\":8,\"command\":\"setBreakpoints\",\"seq\":14}");
        // Breakpoint on an empty line moves to the next statement
        tester.sendMessage("{\"command\":\"setBreakpoints\",\"arguments\":{\"source\":{\"name\":\"SLTest.sl\",\"sourceReference\":2},\"lines\":[6,10,11],\"breakpoints\":[{\"line\":6},{\"line\":10},{\"line\":11}],\"sourceModified\":false},\"type\":\"request\",\"seq\":9}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"breakpoints\":[{\"endLine\":8,\"endColumn\":9,\"line\":7,\"verified\":true,\"column\":3,\"id\":1},{\"endLine\":10,\"endColumn\":9,\"line\":9,\"verified\":true,\"column\":3,\"id\":2},{\"endLine\":12,\"endColumn\":11,\"line\":12,\"verified\":true,\"column\":3,\"id\":3}]},\"type\":\"response\",\"request_seq\":9,\"command\":\"setBreakpoints\",\"seq\":15}");
        // Breakpoint on a last empty line moves to the last statement
        tester.sendMessage("{\"command\":\"setBreakpoints\",\"arguments\":{\"source\":{\"name\":\"SLTest.sl\",\"sourceReference\":2},\"lines\":[6,10,11,13],\"breakpoints\":[{\"line\":6},{\"line\":10},{\"line\":11},{\"line\":13}],\"sourceModified\":false},\"type\":\"request\",\"seq\":10}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"breakpoints\":[{\"endLine\":8,\"endColumn\":9,\"line\":7,\"verified\":true,\"column\":3,\"id\":1},{\"endLine\":10,\"endColumn\":9,\"line\":9,\"verified\":true,\"column\":3,\"id\":2},{\"endLine\":12,\"endColumn\":11,\"line\":12,\"verified\":true,\"column\":3,\"id\":3},{\"endLine\":12,\"endColumn\":36,\"line\":12,\"verified\":true,\"column\":25,\"id\":4}]},\"type\":\"response\",\"request_seq\":10,\"command\":\"setBreakpoints\",\"seq\":16}");
        // Remove some breakpoints
        tester.sendMessage("{\"command\":\"setBreakpoints\",\"arguments\":{\"source\":{\"name\":\"SLTest.sl\",\"sourceReference\":2},\"lines\":[6],\"breakpoints\":[{\"line\":6}],\"sourceModified\":false},\"type\":\"request\",\"seq\":11}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"breakpoints\":[{\"endLine\":8,\"endColumn\":9,\"line\":7,\"verified\":true,\"column\":3,\"id\":1}]},\"type\":\"response\",\"request_seq\":11,\"command\":\"setBreakpoints\",\"seq\":17}");
        // Continue to hit some breakpoints
        tester.sendMessage("{\"command\":\"continue\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":12}");
        tester.compareReceivedMessages(
                "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                "{\"success\":true,\"body\":{\"allThreadsContinued\":false},\"type\":\"response\",\"request_seq\":12,\"command\":\"continue\"}",
                "{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"breakpoint\",\"description\":\"Paused on breakpoint\"},\"type\":\"event\"}"
        );
        tester.sendMessage("{\"command\":\"threads\",\"type\":\"request\",\"seq\":13}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"threads\":[{\"name\":\"testRunner\",\"id\":1}]},\"type\":\"response\",\"request_seq\":13,\"command\":\"threads\",\"seq\":21}");
        tester.sendMessage("{\"command\":\"stackTrace\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":14}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"stackFrames\":[{\"line\":7,\"name\":\"testLocations\",\"column\":3,\"id\":1,\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}},{\"line\":3,\"name\":\"main\",\"column\":3,\"id\":2,\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}}],\"totalFrames\":2},\"type\":\"response\",\"request_seq\":14,\"command\":\"stackTrace\",\"seq\":22}");
        // Continue to finish:
        tester.sendMessage("{\"command\":\"continue\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":15}");
        tester.compareReceivedMessages(
                "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                "{\"success\":true,\"body\":{\"allThreadsContinued\":false},\"type\":\"response\",\"request_seq\":15,\"command\":\"continue\"}"
        );
        tester.finish();
    }

    @Test
    public void testBreakpointLocations() throws Exception {
        tester = DAPTester.start(true);
        Source source = Source.newBuilder("sl", CODE3, "SLTest.sl").uri(testURI).build();
        tester.sendMessage("{\"command\":\"initialize\",\"arguments\":{\"clientID\":\"DAPTester\",\"clientName\":\"DAP Tester\",\"adapterID\":\"graalvm\",\"pathFormat\":\"path\",\"linesStartAt1\":true,\"columnsStartAt1\":true,\"supportsVariableType\":true,\"supportsVariablePaging\":true,\"supportsRunInTerminalRequest\":true,\"locale\":\"en-us\",\"supportsProgressReporting\":true},\"type\":\"request\",\"seq\":1}");
        tester.compareReceivedMessages(
                "{\"event\":\"initialized\",\"type\":\"event\"}",
                "{\"success\":true,\"type\":\"response\",\"body\":{\"supportsConditionalBreakpoints\":true,\"supportsLoadedSourcesRequest\":true,\"supportsFunctionBreakpoints\":true,\"supportsExceptionInfoRequest\":true,\"supportsBreakpointLocationsRequest\":true,\"supportsHitConditionalBreakpoints\":true,\"supportsLogPoints\":true,\"supportsSetVariable\":true,\"supportsConfigurationDoneRequest\":true,\"exceptionBreakpointFilters\":[{\"filter\":\"all\",\"label\":\"All Exceptions\"},{\"filter\":\"uncaught\",\"label\":\"Uncaught Exceptions\"}]},\"request_seq\":1,\"command\":\"initialize\"}"
        );
        tester.sendMessage("{\"command\":\"attach\",\"arguments\":{\"type\":\"graalvm\",\"request\":\"attach\",\"name\":\"Attach\",\"port\":9229,\"protocol\":\"chromeDevTools\"},\"type\":\"request\",\"seq\":2}");
        tester.compareReceivedMessages("{\"event\":\"output\",\"body\":{\"output\":\"Debugger attached.\",\"category\":\"stderr\"},\"type\":\"event\"}", "{\"success\":true,\"type\":\"response\",\"request_seq\":2,\"command\":\"attach\"}");
        tester.sendMessage("{\"command\":\"loadedSources\",\"type\":\"request\",\"seq\":3}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"sources\":[]},\"type\":\"response\",\"request_seq\":3,\"command\":\"loadedSources\",\"seq\":5}");
        tester.sendMessage("{\"command\":\"configurationDone\",\"type\":\"request\",\"seq\":4}");
        tester.compareReceivedMessages("{\"success\":true,\"type\":\"response\",\"request_seq\":4,\"command\":\"configurationDone\",\"seq\":6}");
        tester.eval(source);
        tester.compareReceivedMessages("{\"event\":\"thread\",\"body\":{\"threadId\":1,\"reason\":\"started\"},\"type\":\"event\",\"seq\":7}");
        tester.compareReceivedMessages(
                "{\"event\":\"loadedSource\",\"body\":{\"reason\":\"new\",\"source\":{\"sourceReference\":1,\"name\":\"SL builtin\"}},\"type\":\"event\",\"seq\":8}",
                "{\"event\":\"loadedSource\",\"body\":{\"reason\":\"new\",\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}},\"type\":\"event\",\"seq\":9}"
        );
        // Suspend at the beginning of the script:
        tester.compareReceivedMessages("{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"debugger_statement\",\"description\":\"Paused on debugger statement\"},\"type\":\"event\",\"seq\":10}");
        tester.sendMessage("{\"command\":\"threads\",\"type\":\"request\",\"seq\":5}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"threads\":[{\"name\":\"testRunner\",\"id\":1}]},\"type\":\"response\",\"request_seq\":5,\"command\":\"threads\",\"seq\":11}");
        tester.sendMessage("{\"command\":\"stackTrace\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":6}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"stackFrames\":[{\"line\":2,\"name\":\"main\",\"column\":3,\"id\":1,\"source\":{\"sourceReference\":2,\"path\":\"" + testFilePath + "\",\"name\":\"SLTest.sl\"}}],\"totalFrames\":1},\"type\":\"response\",\"request_seq\":6,\"command\":\"stackTrace\",\"seq\":12}");
        // Moves from an empty line to a statement location
        tester.sendMessage("{\"command\":\"breakpointLocations\",\"arguments\":{\"source\":{\"name\":\"SLTest.sl\",\"sourceReference\":2},\"line\":6,\"column\":1,\"endLine\":6,\"endColumn\":3},\"type\":\"request\",\"seq\":7}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"breakpoints\":[{\"line\":7,\"column\":3}]},\"type\":\"response\",\"request_seq\":7,\"command\":\"breakpointLocations\",\"seq\":13}");
        // Provides statement location when only beginning is included
        tester.sendMessage("{\"command\":\"breakpointLocations\",\"arguments\":{\"source\":{\"name\":\"SLTest.sl\",\"sourceReference\":2},\"line\":9,\"column\":1,\"endLine\":9,\"endColumn\":6},\"type\":\"request\",\"seq\":8}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"breakpoints\":[{\"line\":9,\"column\":3}]},\"type\":\"response\",\"request_seq\":8,\"command\":\"breakpointLocations\",\"seq\":14}");
        // Provides statement location when only end is included
        tester.sendMessage("{\"command\":\"breakpointLocations\",\"arguments\":{\"source\":{\"name\":\"SLTest.sl\",\"sourceReference\":2},\"line\":10,\"column\":1,\"endLine\":10,\"endColumn\":11},\"type\":\"request\",\"seq\":9}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"breakpoints\":[{\"line\":9,\"column\":3}]},\"type\":\"response\",\"request_seq\":9,\"command\":\"breakpointLocations\",\"seq\":15}");
        // Provides all statement locations on a line
        tester.sendMessage("{\"command\":\"breakpointLocations\",\"arguments\":{\"source\":{\"name\":\"SLTest.sl\",\"sourceReference\":2},\"line\":12,\"column\":1,\"endLine\":12,\"endColumn\":38},\"type\":\"request\",\"seq\":10}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"breakpoints\":[{\"line\":12,\"column\":3},{\"line\":12,\"column\":14},{\"line\":12,\"column\":25}]},\"type\":\"response\",\"request_seq\":10,\"command\":\"breakpointLocations\",\"seq\":16}");
        // When only start location is provided:
        tester.sendMessage("{\"command\":\"breakpointLocations\",\"arguments\":{\"source\":{\"name\":\"SLTest.sl\",\"sourceReference\":2},\"line\":6,\"column\":1},\"type\":\"request\",\"seq\":11}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"breakpoints\":[{\"line\":7,\"column\":3}]},\"type\":\"response\",\"request_seq\":11,\"command\":\"breakpointLocations\",\"seq\":17}");
        // Provides statement location when only beginning is included
        tester.sendMessage("{\"command\":\"breakpointLocations\",\"arguments\":{\"source\":{\"name\":\"SLTest.sl\",\"sourceReference\":2},\"line\":9,\"column\":3},\"type\":\"request\",\"seq\":12}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"breakpoints\":[{\"line\":9,\"column\":3}]},\"type\":\"response\",\"request_seq\":12,\"command\":\"breakpointLocations\",\"seq\":18}");
        // Provides statement location when only end is included
        tester.sendMessage("{\"command\":\"breakpointLocations\",\"arguments\":{\"source\":{\"name\":\"SLTest.sl\",\"sourceReference\":2},\"line\":10,\"column\":9},\"type\":\"request\",\"seq\":13}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"breakpoints\":[{\"line\":9,\"column\":3}]},\"type\":\"response\",\"request_seq\":13,\"command\":\"breakpointLocations\",\"seq\":19}");
        // Provides all statement locations on a line
        tester.sendMessage("{\"command\":\"breakpointLocations\",\"arguments\":{\"source\":{\"name\":\"SLTest.sl\",\"sourceReference\":2},\"line\":12,\"column\":1},\"type\":\"request\",\"seq\":14}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"breakpoints\":[{\"line\":12,\"column\":3},{\"line\":12,\"column\":14},{\"line\":12,\"column\":25}]},\"type\":\"response\",\"request_seq\":14,\"command\":\"breakpointLocations\",\"seq\":20}");
        // Test location after file length:
        tester.sendMessage("{\"command\":\"breakpointLocations\",\"arguments\":{\"source\":{\"name\":\"SLTest.sl\",\"sourceReference\":2},\"line\":15,\"column\":1},\"type\":\"request\",\"seq\":15}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"breakpoints\":[{\"line\":12,\"column\":25}]},\"type\":\"response\",\"request_seq\":15,\"command\":\"breakpointLocations\",\"seq\":21}");
        // Continue to finish:
        tester.sendMessage("{\"command\":\"continue\",\"arguments\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"request\",\"seq\":16}");
        tester.compareReceivedMessages(
                "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                "{\"success\":true,\"body\":{\"allThreadsContinued\":false},\"type\":\"response\",\"request_seq\":16,\"command\":\"continue\"}"
        );
        tester.finish();
    }

    @Test
    public void testExceptionBreakpoints() throws Exception {
        tester = DAPTester.start(false);
        URI uri = new URI("file:///test/SLThrow.sl");
        Source source = Source.newBuilder("sl", CODE_THROW, "SLThrow.sl").uri(uri).build();
        String path = getFilePath(new File(uri));
        tester.sendMessage("{\"command\":\"initialize\",\"arguments\":{\"clientID\":\"DAPTester\",\"clientName\":\"DAP Tester\",\"adapterID\":\"graalvm\",\"pathFormat\":\"path\",\"linesStartAt1\":true,\"columnsStartAt1\":true,\"supportsVariableType\":true,\"supportsVariablePaging\":true,\"supportsRunInTerminalRequest\":true,\"locale\":\"en-us\",\"supportsProgressReporting\":true},\"type\":\"request\",\"seq\":1}");
        tester.compareReceivedMessages(
                "{\"event\":\"initialized\",\"type\":\"event\"}",
                "{\"success\":true,\"type\":\"response\",\"body\":{\"supportsConditionalBreakpoints\":true,\"supportsLoadedSourcesRequest\":true,\"supportsFunctionBreakpoints\":true,\"supportsExceptionInfoRequest\":true,\"supportsBreakpointLocationsRequest\":true,\"supportsHitConditionalBreakpoints\":true,\"supportsLogPoints\":true,\"supportsSetVariable\":true,\"supportsConfigurationDoneRequest\":true,\"exceptionBreakpointFilters\":[{\"filter\":\"all\",\"label\":\"All Exceptions\"},{\"filter\":\"uncaught\",\"label\":\"Uncaught Exceptions\"}]},\"request_seq\":1,\"command\":\"initialize\"}"
        );
        tester.sendMessage("{\"command\":\"attach\",\"arguments\":{\"type\":\"graalvm\",\"request\":\"attach\",\"name\":\"Attach\",\"port\":9229,\"protocol\":\"chromeDevTools\"},\"type\":\"request\",\"seq\":2}");
        tester.compareReceivedMessages("{\"event\":\"output\",\"body\":{\"output\":\"Debugger attached.\",\"category\":\"stderr\"},\"type\":\"event\"}", "{\"success\":true,\"type\":\"response\",\"request_seq\":2,\"command\":\"attach\"}");
        tester.sendMessage("{\"command\":\"loadedSources\",\"type\":\"request\",\"seq\":3}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"sources\":[]},\"type\":\"response\",\"request_seq\":3,\"command\":\"loadedSources\",\"seq\":5}");
        tester.sendMessage("{\"command\":\"setExceptionBreakpoints\",\"arguments\":{\"filters\":[\"uncaught\"]},\"type\":\"request\",\"seq\":4}");
        tester.compareReceivedMessages("{\"success\":true,\"type\":\"response\",\"request_seq\":4,\"command\":\"setExceptionBreakpoints\",\"seq\":6}");
        tester.sendMessage("{\"command\":\"configurationDone\",\"type\":\"request\",\"seq\":5}");
        tester.compareReceivedMessages("{\"success\":true,\"type\":\"response\",\"request_seq\":5,\"command\":\"configurationDone\",\"seq\":7}");
        tester.eval(source);
        tester.compareReceivedMessages("{\"event\":\"thread\",\"body\":{\"threadId\":1,\"reason\":\"started\"},\"type\":\"event\",\"seq\":8}");
        tester.compareReceivedMessages(
                "{\"event\":\"loadedSource\",\"body\":{\"reason\":\"new\",\"source\":{\"sourceReference\":1,\"name\":\"SL builtin\"}},\"type\":\"event\",\"seq\":9}",
                "{\"event\":\"loadedSource\",\"body\":{\"reason\":\"new\",\"source\":{\"sourceReference\":2,\"path\":\"" + path + "\",\"name\":\"SLThrow.sl\"}},\"type\":\"event\",\"seq\":10}"
        );
        // Suspend at the breakpoint:
        tester.compareReceivedMessages("{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"exception\",\"description\":\"Paused on uncaught exception\"},\"type\":\"event\",\"seq\":11}");
        tester.sendMessage("{\"command\":\"threads\",\"type\":\"request\",\"seq\":6}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"threads\":[{\"name\":\"testRunner\",\"id\":1}]},\"type\":\"response\",\"request_seq\":6,\"command\":\"threads\",\"seq\":12}");
        tester.sendMessage("{\"command\":\"stackTrace\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":7}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"stackFrames\":[{\"line\":6,\"name\":\"invert\",\"column\":13,\"id\":1,\"source\":{\"sourceReference\":2,\"path\":\"" + path + "\",\"name\":\"SLThrow.sl\"}},{\"line\":3,\"name\":\"main\",\"column\":10,\"id\":2,\"source\":{\"sourceReference\":2,\"path\":\"" + path + "\",\"name\":\"SLThrow.sl\"}}],\"totalFrames\":2},\"type\":\"response\",\"request_seq\":7,\"command\":\"stackTrace\",\"seq\":13}");
        // Ask for the exception info:
        tester.sendMessage("{\"command\":\"exceptionInfo\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":8}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"exceptionId\":\"Error\",\"description\":\"Unsupported\",\"breakMode\":\"unhandled\"},\"type\":\"response\",\"request_seq\":8,\"command\":\"exceptionInfo\",\"seq\":14}");
        // Continue to finish:
        tester.sendMessage("{\"command\":\"continue\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":9}");
        tester.compareReceivedMessages(
                "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                "{\"success\":true,\"body\":{\"allThreadsContinued\":false},\"type\":\"response\",\"request_seq\":9,\"command\":\"continue\"}"
        );
        tester.finish(false);
    }

    @Test
    public void testSetVariableValue() throws Exception {
        tester = DAPTester.start(false);
        File sourceFile = createTemporaryFile(CODE_VARS);
        Source source = Source.newBuilder("sl", sourceFile).build();
        String sourceJson = "{\"name\":\"" + sourceFile.getName() + "\",\"path\":\"" + getFilePath(sourceFile) + "\"}";
        tester.sendMessage("{\"command\":\"initialize\",\"arguments\":{\"clientID\":\"DAPTester\",\"clientName\":\"DAP Tester\",\"adapterID\":\"graalvm\",\"pathFormat\":\"path\",\"linesStartAt1\":true,\"columnsStartAt1\":true,\"supportsVariableType\":true,\"supportsVariablePaging\":true,\"supportsRunInTerminalRequest\":true,\"locale\":\"en-us\",\"supportsProgressReporting\":true},\"type\":\"request\",\"seq\":1}");
        tester.compareReceivedMessages(
                "{\"event\":\"initialized\",\"type\":\"event\"}",
                "{\"success\":true,\"type\":\"response\",\"body\":{\"supportsConditionalBreakpoints\":true,\"supportsLoadedSourcesRequest\":true,\"supportsFunctionBreakpoints\":true,\"supportsExceptionInfoRequest\":true,\"supportsBreakpointLocationsRequest\":true,\"supportsHitConditionalBreakpoints\":true,\"supportsLogPoints\":true,\"supportsSetVariable\":true,\"supportsConfigurationDoneRequest\":true,\"exceptionBreakpointFilters\":[{\"filter\":\"all\",\"label\":\"All Exceptions\"},{\"filter\":\"uncaught\",\"label\":\"Uncaught Exceptions\"}]},\"request_seq\":1,\"command\":\"initialize\"}"
        );
        tester.sendMessage("{\"command\":\"attach\",\"arguments\":{\"type\":\"graalvm\",\"request\":\"attach\",\"name\":\"Attach\",\"port\":9229,\"protocol\":\"chromeDevTools\"},\"type\":\"request\",\"seq\":2}");
        tester.compareReceivedMessages("{\"event\":\"output\",\"body\":{\"output\":\"Debugger attached.\",\"category\":\"stderr\"},\"type\":\"event\"}", "{\"success\":true,\"type\":\"response\",\"request_seq\":2,\"command\":\"attach\"}");
        tester.sendMessage("{\"command\":\"loadedSources\",\"type\":\"request\",\"seq\":3}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"sources\":[]},\"type\":\"response\",\"request_seq\":3,\"command\":\"loadedSources\",\"seq\":5}");
        tester.sendMessage("{\"command\":\"setBreakpoints\",\"arguments\":{\"source\":" + sourceJson + ",\"lines\":[16],\"breakpoints\":[{\"line\":16}],\"sourceModified\":false},\"type\":\"request\",\"seq\":4}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"breakpoints\":[{\"line\":16,\"verified\":false,\"id\":1}]},\"type\":\"response\",\"request_seq\":4,\"command\":\"setBreakpoints\",\"seq\":6}");
        tester.sendMessage("{\"command\":\"configurationDone\",\"type\":\"request\",\"seq\":5}");
        tester.compareReceivedMessages("{\"success\":true,\"type\":\"response\",\"request_seq\":5,\"command\":\"configurationDone\",\"seq\":7}");
        tester.eval(source);
        tester.compareReceivedMessages("{\"event\":\"thread\",\"body\":{\"threadId\":1,\"reason\":\"started\"},\"type\":\"event\",\"seq\":8}");
        tester.compareReceivedMessages(
                "{\"event\":\"loadedSource\",\"body\":{\"reason\":\"new\",\"source\":{\"sourceReference\":1,\"name\":\"SL builtin\"}},\"type\":\"event\",\"seq\":9}",
                "{\"event\":\"loadedSource\",\"body\":{\"reason\":\"new\",\"source\":" + sourceJson + "},\"type\":\"event\",\"seq\":10}"
        );
        tester.compareReceivedMessages("{\"event\":\"breakpoint\",\"body\":{\"reason\":\"changed\",\"breakpoint\":{\"endLine\":16,\"endColumn\":5,\"line\":16,\"verified\":true,\"column\":5,\"id\":1}},\"type\":\"event\",\"seq\":11}");
        // Suspend at the breakpoint:
        tester.compareReceivedMessages("{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"breakpoint\",\"description\":\"Paused on breakpoint\"},\"type\":\"event\",\"seq\":12}");
        tester.sendMessage("{\"command\":\"threads\",\"type\":\"request\",\"seq\":6}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"threads\":[{\"name\":\"testRunner\",\"id\":1}]},\"type\":\"response\",\"request_seq\":6,\"command\":\"threads\",\"seq\":13}");
        tester.sendMessage("{\"command\":\"stackTrace\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":7}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"stackFrames\":[{\"line\":16,\"name\":\"main\",\"column\":5,\"id\":1,\"source\":" + sourceJson + "}],\"totalFrames\":1},\"type\":\"response\",\"request_seq\":7,\"command\":\"stackTrace\",\"seq\":14}");
        tester.sendMessage("{\"command\":\"scopes\",\"arguments\":{\"frameId\":1},\"type\":\"request\",\"seq\":8}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"scopes\":[{\"name\":\"Local\",\"variablesReference\":2,\"expensive\":false},{\"name\":\"Global\",\"variablesReference\":3,\"expensive\":true}]},\"type\":\"response\",\"request_seq\":8,\"command\":\"scopes\",\"seq\":15}");
        tester.sendMessage("{\"command\":\"variables\",\"arguments\":{\"variablesReference\":2},\"type\":\"request\",\"seq\":9}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"variables\":[{\"name\":\"n\",\"variablesReference\":0,\"type\":\"Number\",\"value\":\"1\"},"
                + "{\"name\":\"m\",\"variablesReference\":0,\"type\":\"Number\",\"value\":\"4\"},"
                + "{\"name\":\"b\",\"variablesReference\":0,\"type\":\"Boolean\",\"value\":\"true\"},"
                + "{\"name\":\"bb\",\"variablesReference\":0,\"type\":\"Boolean\",\"value\":\"true\"},"
                + "{\"name\":\"big\",\"variablesReference\":0,\"type\":\"Number\",\"value\":\"152415787532388367501905199875019052100\"},"
                + "{\"name\":\"str\",\"variablesReference\":0,\"type\":\"String\",\"value\":\"A String\"},"
                + "{\"name\":\"f\",\"variablesReference\":0,\"type\":\"Function\",\"value\":\"fn\"},"
                + "{\"name\":\"f2\",\"variablesReference\":0,\"type\":\"Number\",\"value\":\"0\"}]},\"type\":\"response\",\"request_seq\":9,\"command\":\"variables\",\"seq\":16}");
        tester.sendMessage("{\"command\":\"setVariable\",\"arguments\":{\"variablesReference\":2,\"name\":\"m\",\"value\":\"1000\"},\"type\":\"request\",\"seq\":10}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"type\":\"Number\",\"variablesReference\":0,\"value\":\"1000\"},\"type\":\"response\",\"request_seq\":10,\"command\":\"setVariable\",\"seq\":17}");
        tester.sendMessage("{\"command\":\"setVariable\",\"arguments\":{\"variablesReference\":2,\"name\":\"bb\",\"value\":\"false\"},\"type\":\"request\",\"seq\":11}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"type\":\"Boolean\",\"variablesReference\":0,\"value\":\"false\"},\"type\":\"response\",\"request_seq\":11,\"command\":\"setVariable\",\"seq\":18}");
        tester.sendMessage("{\"command\":\"setVariable\",\"arguments\":{\"variablesReference\":2,\"name\":\"str\",\"value\":\"\\\"A Different String\\\"\"},\"type\":\"request\",\"seq\":12}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"type\":\"String\",\"variablesReference\":0,\"value\":\"A Different String\"},\"type\":\"response\",\"request_seq\":12,\"command\":\"setVariable\",\"seq\":19}");
        tester.sendMessage("{\"command\":\"setVariable\",\"arguments\":{\"variablesReference\":2,\"name\":\"f2\",\"value\":\"f\"},\"type\":\"request\",\"seq\":13}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"type\":\"Function\",\"variablesReference\":0,\"value\":\"fn\"},\"type\":\"response\",\"request_seq\":13,\"command\":\"setVariable\",\"seq\":20}");
        // Continue and suspend at the breakpoint:
        tester.sendMessage("{\"command\":\"continue\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":14}");
        tester.compareReceivedMessages(
                "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                "{\"success\":true,\"body\":{\"allThreadsContinued\":false},\"type\":\"response\",\"request_seq\":14,\"command\":\"continue\"}",
                "{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"breakpoint\",\"description\":\"Paused on breakpoint\"},\"type\":\"event\"}"
        );
        tester.sendMessage("{\"command\":\"threads\",\"type\":\"request\",\"seq\":15}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"threads\":[{\"name\":\"testRunner\",\"id\":1}]},\"type\":\"response\",\"request_seq\":15,\"command\":\"threads\",\"seq\":24}");
        tester.sendMessage("{\"command\":\"stackTrace\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":16}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"stackFrames\":[{\"line\":16,\"name\":\"main\",\"column\":5,\"id\":1,\"source\":" + sourceJson + "}],\"totalFrames\":1},\"type\":\"response\",\"request_seq\":16,\"command\":\"stackTrace\",\"seq\":25}");
        tester.sendMessage("{\"command\":\"scopes\",\"arguments\":{\"frameId\":1},\"type\":\"request\",\"seq\":17}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"scopes\":[{\"name\":\"Local\",\"variablesReference\":2,\"expensive\":false},{\"name\":\"Global\",\"variablesReference\":3,\"expensive\":true}]},\"type\":\"response\",\"request_seq\":17,\"command\":\"scopes\",\"seq\":26}");
        tester.sendMessage("{\"command\":\"variables\",\"arguments\":{\"variablesReference\":2},\"type\":\"request\",\"seq\":18}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"variables\":[{\"name\":\"n\",\"variablesReference\":0,\"type\":\"Number\",\"value\":\"0\"},"
                + "{\"name\":\"m\",\"variablesReference\":0,\"type\":\"Number\",\"value\":\"1000\"},"
                + "{\"name\":\"b\",\"variablesReference\":0,\"type\":\"Boolean\",\"value\":\"false\"},"
                + "{\"name\":\"bb\",\"variablesReference\":0,\"type\":\"Boolean\",\"value\":\"false\"},"
                + "{\"name\":\"big\",\"variablesReference\":0,\"type\":\"Number\",\"value\":\"23230572289118153328333583928030329684079829544396666111742077337982514410000\"},"
                + "{\"name\":\"str\",\"variablesReference\":0,\"type\":\"String\",\"value\":\"A Different String\"},"
                + "{\"name\":\"f\",\"variablesReference\":0,\"type\":\"Function\",\"value\":\"fn\"},"
                + "{\"name\":\"f2\",\"variablesReference\":0,\"type\":\"Function\",\"value\":\"fn\"}]},\"type\":\"response\",\"request_seq\":18,\"command\":\"variables\",\"seq\":27}");
        // Continue to finish:
        tester.sendMessage("{\"command\":\"continue\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":19}");
        tester.compareReceivedMessages(
                "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                "{\"success\":true,\"body\":{\"allThreadsContinued\":false},\"type\":\"response\",\"request_seq\":19,\"command\":\"continue\"}"
        );
        tester.finish();
    }

    @Test
    public void testEval() throws Exception {
        tester = DAPTester.start(false);
        File sourceFile = createTemporaryFile(CODE1);
        Source source = Source.newBuilder("sl", sourceFile).build();
        String sourceJson = "{\"name\":\"" + sourceFile.getName() + "\",\"path\":\"" + getFilePath(sourceFile) + "\"}";
        tester.sendMessage("{\"command\":\"initialize\",\"arguments\":{\"clientID\":\"DAPTester\",\"clientName\":\"DAP Tester\",\"adapterID\":\"graalvm\",\"pathFormat\":\"path\",\"linesStartAt1\":true,\"columnsStartAt1\":true,\"supportsVariableType\":true,\"supportsVariablePaging\":true,\"supportsRunInTerminalRequest\":true,\"locale\":\"en-us\",\"supportsProgressReporting\":true},\"type\":\"request\",\"seq\":1}");
        tester.compareReceivedMessages(
                "{\"event\":\"initialized\",\"type\":\"event\"}",
                "{\"success\":true,\"type\":\"response\",\"body\":{\"supportsConditionalBreakpoints\":true,\"supportsLoadedSourcesRequest\":true,\"supportsFunctionBreakpoints\":true,\"supportsExceptionInfoRequest\":true,\"supportsBreakpointLocationsRequest\":true,\"supportsHitConditionalBreakpoints\":true,\"supportsLogPoints\":true,\"supportsSetVariable\":true,\"supportsConfigurationDoneRequest\":true,\"exceptionBreakpointFilters\":[{\"filter\":\"all\",\"label\":\"All Exceptions\"},{\"filter\":\"uncaught\",\"label\":\"Uncaught Exceptions\"}]},\"request_seq\":1,\"command\":\"initialize\"}"
        );
        tester.sendMessage("{\"command\":\"attach\",\"arguments\":{\"type\":\"graalvm\",\"request\":\"attach\",\"name\":\"Attach\",\"port\":9229,\"protocol\":\"chromeDevTools\"},\"type\":\"request\",\"seq\":2}");
        tester.compareReceivedMessages("{\"event\":\"output\",\"body\":{\"output\":\"Debugger attached.\",\"category\":\"stderr\"},\"type\":\"event\"}", "{\"success\":true,\"type\":\"response\",\"request_seq\":2,\"command\":\"attach\"}");
        tester.sendMessage("{\"command\":\"loadedSources\",\"type\":\"request\",\"seq\":3}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"sources\":[]},\"type\":\"response\",\"request_seq\":3,\"command\":\"loadedSources\",\"seq\":5}");
        tester.sendMessage("{\"command\":\"setBreakpoints\",\"arguments\":{\"source\":" + sourceJson + ",\"lines\":[6],\"breakpoints\":[{\"line\":6}],\"sourceModified\":false},\"type\":\"request\",\"seq\":4}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"breakpoints\":[{\"line\":6,\"verified\":false,\"id\":1}]},\"type\":\"response\",\"request_seq\":4,\"command\":\"setBreakpoints\",\"seq\":6}");
        tester.sendMessage("{\"command\":\"configurationDone\",\"type\":\"request\",\"seq\":5}");
        tester.compareReceivedMessages("{\"success\":true,\"type\":\"response\",\"request_seq\":5,\"command\":\"configurationDone\",\"seq\":7}");
        tester.eval(source);
        tester.compareReceivedMessages("{\"event\":\"thread\",\"body\":{\"threadId\":1,\"reason\":\"started\"},\"type\":\"event\",\"seq\":8}");
        tester.compareReceivedMessages(
                "{\"event\":\"loadedSource\",\"body\":{\"reason\":\"new\",\"source\":{\"sourceReference\":1,\"name\":\"SL builtin\"}},\"type\":\"event\",\"seq\":9}",
                "{\"event\":\"loadedSource\",\"body\":{\"reason\":\"new\",\"source\":" + sourceJson + "},\"type\":\"event\",\"seq\":10}"
        );
        tester.compareReceivedMessages("{\"event\":\"breakpoint\",\"body\":{\"reason\":\"changed\",\"breakpoint\":{\"endLine\":6,\"endColumn\":16,\"line\":6,\"verified\":true,\"column\":5,\"id\":1}},\"type\":\"event\",\"seq\":11}");
        // Suspend at the breakpoint:
        tester.compareReceivedMessages("{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"breakpoint\",\"description\":\"Paused on breakpoint\"},\"type\":\"event\",\"seq\":12}");
        tester.sendMessage("{\"command\":\"threads\",\"type\":\"request\",\"seq\":6}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"threads\":[{\"name\":\"testRunner\",\"id\":1}]},\"type\":\"response\",\"request_seq\":6,\"command\":\"threads\",\"seq\":13}");
        tester.sendMessage("{\"command\":\"stackTrace\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":7}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"stackFrames\":[{\"line\":6,\"name\":\"main\",\"column\":5,\"id\":1,\"source\":" + sourceJson + "}],\"totalFrames\":1},\"type\":\"response\",\"request_seq\":7,\"command\":\"stackTrace\",\"seq\":14}");
        // Evaluate:
        tester.sendMessage("{\"command\":\"evaluate\",\"arguments\":{\"expression\":\"a\",\"frameId\":1},\"type\":\"request\",\"seq\":8}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"result\":\"10\",\"variablesReference\":0,\"type\":\"Number\"},\"type\":\"response\",\"request_seq\":8,\"command\":\"evaluate\",\"seq\":15}");
        tester.sendMessage("{\"command\":\"evaluate\",\"arguments\":{\"expression\":\"b\",\"frameId\":1},\"type\":\"request\",\"seq\":9}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"result\":\"2\",\"variablesReference\":0,\"type\":\"Number\"},\"type\":\"response\",\"request_seq\":9,\"command\":\"evaluate\",\"seq\":16}");
        tester.sendMessage("{\"command\":\"evaluate\",\"arguments\":{\"expression\":\"unknown\",\"frameId\":1},\"type\":\"request\",\"seq\":10}");
        tester.compareReceivedMessages("{\"success\":false,\"body\":{\"error\":{\"format\":\"Error(s) parsing script:" + NL + "-- line 1 col 1: missing 'function' at 'unknown'\",\"id\":2025}},\"type\":\"response\",\"message\":\"Error(s) parsing script:" + NL + "-- line 1 col 1: missing 'function' at 'unknown'\",\"request_seq\":10,\"command\":\"evaluate\",\"seq\":17}");
        // Continue to finish:
        tester.sendMessage("{\"command\":\"setBreakpoints\",\"arguments\":{\"source\":" + sourceJson + ",\"lines\":[],\"breakpoints\":[],\"sourceModified\":false},\"type\":\"request\",\"seq\":18}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"breakpoints\":[]},\"type\":\"response\",\"request_seq\":18,\"command\":\"setBreakpoints\"}");
        tester.sendMessage("{\"command\":\"continue\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":19}");
        tester.compareReceivedMessages(
                "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                "{\"success\":true,\"body\":{\"allThreadsContinued\":false},\"type\":\"response\",\"request_seq\":19,\"command\":\"continue\"}"
        );
        tester.finish();
    }

    @Test
    public void testSourcePath() throws Exception {
        tester = DAPTester.start(true);
        File sourceFile = createTemporaryFile(CODE1);
        Source source = Source.newBuilder("sl", sourceFile).build();
        tester.sendMessage("{\"command\":\"initialize\",\"arguments\":{\"clientID\":\"DAPTester\",\"clientName\":\"DAP Tester\",\"adapterID\":\"graalvm\",\"pathFormat\":\"path\",\"linesStartAt1\":true,\"columnsStartAt1\":true,\"supportsVariableType\":true,\"supportsVariablePaging\":true,\"supportsRunInTerminalRequest\":true,\"locale\":\"en-us\",\"supportsProgressReporting\":true},\"type\":\"request\",\"seq\":1}");
        tester.compareReceivedMessages(
                "{\"event\":\"initialized\",\"type\":\"event\"}",
                "{\"success\":true,\"type\":\"response\",\"body\":{\"supportsConditionalBreakpoints\":true,\"supportsLoadedSourcesRequest\":true,\"supportsFunctionBreakpoints\":true,\"supportsExceptionInfoRequest\":true,\"supportsBreakpointLocationsRequest\":true,\"supportsHitConditionalBreakpoints\":true,\"supportsLogPoints\":true,\"supportsSetVariable\":true,\"supportsConfigurationDoneRequest\":true,\"exceptionBreakpointFilters\":[{\"filter\":\"all\",\"label\":\"All Exceptions\"},{\"filter\":\"uncaught\",\"label\":\"Uncaught Exceptions\"}]},\"request_seq\":1,\"command\":\"initialize\"}"
        );
        tester.sendMessage("{\"command\":\"attach\",\"arguments\":{\"type\":\"graalvm\",\"request\":\"attach\",\"name\":\"Attach\",\"port\":9229,\"protocol\":\"chromeDevTools\"},\"type\":\"request\",\"seq\":2}");
        tester.compareReceivedMessages("{\"event\":\"output\",\"body\":{\"output\":\"Debugger attached.\",\"category\":\"stderr\"},\"type\":\"event\"}", "{\"success\":true,\"type\":\"response\",\"request_seq\":2,\"command\":\"attach\"}");
        tester.sendMessage("{\"command\":\"loadedSources\",\"type\":\"request\",\"seq\":3}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"sources\":[]},\"type\":\"response\",\"request_seq\":3,\"command\":\"loadedSources\",\"seq\":5}");
        tester.sendMessage("{\"command\":\"configurationDone\",\"type\":\"request\",\"seq\":4}");
        tester.compareReceivedMessages("{\"success\":true,\"type\":\"response\",\"request_seq\":4,\"command\":\"configurationDone\",\"seq\":6}");
        tester.eval(source);
        tester.compareReceivedMessages("{\"event\":\"thread\",\"body\":{\"threadId\":1,\"reason\":\"started\"},\"type\":\"event\",\"seq\":7}");
        tester.compareReceivedMessages(
                "{\"event\":\"loadedSource\",\"body\":{\"reason\":\"new\",\"source\":{\"sourceReference\":1,\"name\":\"SL builtin\"}},\"type\":\"event\",\"seq\":8}",
                "{\"event\":\"loadedSource\",\"body\":{\"reason\":\"new\",\"source\":{\"path\":\"" + getFilePath(sourceFile) + "\",\"name\":\"" + sourceFile.getName() + "\"}},\"type\":\"event\",\"seq\":9}"
        );
        tester.compareReceivedMessages("{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"debugger_statement\",\"description\":\"Paused on debugger statement\"},\"type\":\"event\",\"seq\":10}");
        tester.sendMessage("{\"command\":\"continue\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":9}");
        tester.compareReceivedMessages(
                "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                "{\"success\":true,\"body\":{\"allThreadsContinued\":false},\"type\":\"response\",\"request_seq\":9,\"command\":\"continue\"}"
        );
        tester.finish();
    }

    // @formatter:on
    // CheckStyle: resume line length check

    private static File createTemporaryFile(String content) throws IOException {
        File file = File.createTempFile("test", ".sl");
        file.deleteOnExit();
        Files.writeString(file.toPath(), content, StandardOpenOption.TRUNCATE_EXISTING);
        return file;
    }

    private static String replaceNewLines(String nl) {
        return nl.replace("\n", "\\n").replace("\r", "\\r");
    }
}
