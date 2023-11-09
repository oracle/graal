/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.net.URL;
import java.util.regex.Pattern;

import org.graalvm.polyglot.Source;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;

/**
 * {@link InstrumentationTestLanguage} DAP debugging test.
 */
public class ITLDAPTest {

    private DAPTester tester;

    @After
    public void tearDown() {
        tester = null;
    }

    @Test
    public void testOutput() throws Exception {
        Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "ROOT(\n" +
                        "  PRINT(OUT, CONSTANT(\"one\ntwo\n\")),\n" +
                        "  STATEMENT(),\n" +
                        "  PRINT(OUT, CONSTANT(\"three,\")),\n" +
                        "  STATEMENT(),\n" +
                        "  PRINT(OUT, CONSTANT(\"four\rfive\")),\n" +
                        "  STATEMENT(),\n" +
                        "  PRINT(OUT, CONSTANT(\"\r\n\")),\n" +
                        "  PRINT(OUT, CONSTANT(\"\r\nsix,\")),\n" +
                        "  PRINT(OUT, CONSTANT(\"seven\n\neight\")),\n" +
                        "  STATEMENT(),\n" +
                        "  PRINT(ERR, CONSTANT(\"1err\n2err\r\n\")),\n" +
                        "  STATEMENT(),\n" +
                        "  PRINT(OUT, CONSTANT(\"\r\nnine\rten\r\n\"))\n" +
                        ")\n", "TestOutput.itl").build();
        tester = DAPTester.start(true);
        tester.sendMessage(
                        "{\"command\":\"initialize\",\"arguments\":{\"clientID\":\"DAPTester\",\"clientName\":\"DAP Tester\",\"adapterID\":\"graalvm\",\"pathFormat\":\"path\",\"linesStartAt1\":true,\"columnsStartAt1\":true," +
                                        "\"supportsVariableType\":true,\"supportsVariablePaging\":true,\"supportsRunInTerminalRequest\":true,\"locale\":\"en-us\",\"supportsProgressReporting\":true},\"type\":\"request\",\"seq\":1}");
        tester.compareReceivedMessages(
                        "{\"event\":\"initialized\",\"type\":\"event\"}",
                        "{\"success\":true,\"type\":\"response\",\"body\":{\"supportsConditionalBreakpoints\":true,\"supportsLoadedSourcesRequest\":true,\"supportsFunctionBreakpoints\":true,\"supportsExceptionInfoRequest\":true," +
                                        "\"supportsBreakpointLocationsRequest\":true,\"supportsHitConditionalBreakpoints\":true,\"supportsLogPoints\":true,\"supportsSetVariable\":true,\"supportsConfigurationDoneRequest\":true," +
                                        "\"exceptionBreakpointFilters\":[{\"filter\":\"all\",\"label\":\"All Exceptions\"},{\"filter\":\"uncaught\",\"label\":\"Uncaught Exceptions\"}]},\"request_seq\":1,\"command\":\"initialize\"}");
        tester.sendMessage(
                        "{\"command\":\"attach\",\"arguments\":{\"type\":\"graalvm\",\"request\":\"attach\",\"name\":\"Attach\",\"port\":9229,\"protocol\":\"debugAdapter\"},\"type\":\"request\",\"seq\":2}");
        tester.compareReceivedMessages("{\"event\":\"output\",\"body\":{\"output\":\"Debugger attached.\",\"category\":\"stderr\"},\"type\":\"event\"}",
                        "{\"success\":true,\"type\":\"response\",\"request_seq\":2,\"command\":\"attach\"}");
        tester.sendMessage("{\"command\":\"loadedSources\",\"type\":\"request\",\"seq\":3}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"sources\":[]},\"type\":\"response\",\"request_seq\":3,\"command\":\"loadedSources\",\"seq\":5}");
        tester.sendMessage("{\"command\":\"configurationDone\",\"type\":\"request\",\"seq\":4}");
        tester.compareReceivedMessages("{\"success\":true,\"type\":\"response\",\"request_seq\":4,\"command\":\"configurationDone\",\"seq\":6}");
        tester.eval(source);
        tester.compareReceivedMessages("{\"event\":\"thread\",\"body\":{\"threadId\":1,\"reason\":\"started\"},\"type\":\"event\",\"seq\":7}");
        tester.compareReceivedMessages(
                        "{\"event\":\"loadedSource\",\"body\":{\"reason\":\"new\",\"source\":{\"sourceReference\":1,\"name\":\"TestOutput.itl\"}},\"type\":\"event\",\"seq\":8}");
        // Suspend at the beginning of the script:
        tester.compareReceivedMessages(
                        "{\"event\":\"output\",\"body\":{\"output\":\"one\\ntwo\\n\",\"category\":\"stdout\"},\"type\":\"event\",\"seq\":9}",
                        "{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"debugger_statement\",\"description\":\"Paused on debugger statement\"},\"type\":\"event\",\"seq\":10}");
        tester.sendMessage("{\"command\":\"threads\",\"type\":\"request\",\"seq\":5}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"threads\":[{\"name\":\"testRunner\",\"id\":1}]},\"type\":\"response\",\"request_seq\":5,\"command\":\"threads\",\"seq\":11}");
        // Next:
        tester.sendMessage("{\"command\":\"next\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":7}");
        tester.compareReceivedMessages(
                        "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                        "{\"success\":true,\"type\":\"response\",\"request_seq\":7,\"command\":\"next\"}",
                        "{\"event\":\"output\",\"body\":{\"output\":\"three,\",\"category\":\"stdout\"},\"type\":\"event\",\"seq\":14}",
                        "{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"debugger_statement\",\"description\":\"Paused on debugger statement\"},\"type\":\"event\"}");
        // Next:
        tester.sendMessage("{\"command\":\"next\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":7}");
        tester.compareReceivedMessages(
                        "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                        "{\"success\":true,\"type\":\"response\",\"request_seq\":7,\"command\":\"next\"}",
                        "{\"event\":\"output\",\"body\":{\"output\":\"four\\rfive\",\"category\":\"stdout\"},\"type\":\"event\",\"seq\":18}",
                        "{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"debugger_statement\",\"description\":\"Paused on debugger statement\"},\"type\":\"event\"}");
        // Next:
        tester.sendMessage("{\"command\":\"next\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":7}");
        tester.compareReceivedMessages(
                        "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                        "{\"success\":true,\"type\":\"response\",\"request_seq\":7,\"command\":\"next\"}",
                        "{\"event\":\"output\",\"body\":{\"output\":\"\\r\\n\",\"category\":\"stdout\"},\"type\":\"event\",\"seq\":22}",
                        "{\"event\":\"output\",\"body\":{\"output\":\"\\r\\nsix,\",\"category\":\"stdout\"},\"type\":\"event\",\"seq\":23}",
                        "{\"event\":\"output\",\"body\":{\"output\":\"seven\\n\\neight\",\"category\":\"stdout\"},\"type\":\"event\",\"seq\":24}",
                        "{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"debugger_statement\",\"description\":\"Paused on debugger statement\"},\"type\":\"event\"}");
        // Next:
        tester.sendMessage("{\"command\":\"next\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":7}");
        tester.compareReceivedMessages(
                        "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                        "{\"success\":true,\"type\":\"response\",\"request_seq\":7,\"command\":\"next\"}",
                        "{\"event\":\"output\",\"body\":{\"output\":\"1err\\n2err\\r\\n\",\"category\":\"stderr\"},\"type\":\"event\",\"seq\":28}",
                        "{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"debugger_statement\",\"description\":\"Paused on debugger statement\"},\"type\":\"event\"}");
        // Next:
        tester.sendMessage("{\"command\":\"next\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":7}");
        tester.compareReceivedMessages(
                        "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                        "{\"success\":true,\"type\":\"response\",\"request_seq\":7,\"command\":\"next\"}",
                        "{\"event\":\"output\",\"body\":{\"output\":\"\\r\\nnine\\rten\\r\\n\",\"category\":\"stdout\"},\"type\":\"event\",\"seq\":32}",
                        "{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"debugger_statement\",\"description\":\"Paused on debugger statement\"},\"type\":\"event\"}");
        // Next:
        tester.sendMessage("{\"command\":\"next\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":7}");
        tester.compareReceivedMessages(
                        "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                        "{\"success\":true,\"type\":\"response\",\"request_seq\":7,\"command\":\"next\"}");
        tester.finish();
    }

    @Test
    public void testOutputEarly() throws Exception {
        Source source1 = Source.newBuilder(InstrumentationTestLanguage.ID, "ROOT(\n" +
                        "  PRINT(OUT, CONSTANT(\"Prologue to stdout\n\")),\n" +
                        "  PRINT(ERR, CONSTANT(\"Prologue to stderr\n\"))" +
                        ")\n", "TestOutput1.itl").build();
        Source source2 = Source.newBuilder(InstrumentationTestLanguage.ID, "ROOT(\n" +
                        "  PRINT(OUT, CONSTANT(\"Text to stdout\n\")),\n" +
                        "  PRINT(ERR, CONSTANT(\"Text to stderr\n\"))" +
                        ")\n", "TestOutput2.itl").build();
        Source source3 = Source.newBuilder(InstrumentationTestLanguage.ID, "ROOT(\n" +
                        "  PRINT(OUT, CONSTANT(\"Epilogue to stdout\n\")),\n" +
                        "  PRINT(ERR, CONSTANT(\"Epilogue to stderr\n\"))" +
                        ")\n", "TestOutput3.itl").build();
        tester = DAPTester.start(false, context -> context.eval(source1));
        tester.sendMessage(
                        "{\"command\":\"initialize\",\"arguments\":{\"clientID\":\"DAPTester\",\"clientName\":\"DAP Tester\",\"adapterID\":\"graalvm\",\"pathFormat\":\"path\",\"linesStartAt1\":true,\"columnsStartAt1\":true," +
                                        "\"supportsVariableType\":true,\"supportsVariablePaging\":true,\"supportsRunInTerminalRequest\":true,\"locale\":\"en-us\",\"supportsProgressReporting\":true},\"type\":\"request\",\"seq\":1}");
        tester.compareReceivedMessages(
                        "{\"event\":\"initialized\",\"type\":\"event\"}",
                        "{\"success\":true,\"type\":\"response\",\"body\":{\"supportsConditionalBreakpoints\":true,\"supportsLoadedSourcesRequest\":true,\"supportsFunctionBreakpoints\":true,\"supportsExceptionInfoRequest\":true," +
                                        "\"supportsBreakpointLocationsRequest\":true,\"supportsHitConditionalBreakpoints\":true,\"supportsLogPoints\":true,\"supportsSetVariable\":true,\"supportsConfigurationDoneRequest\":true," +
                                        "\"exceptionBreakpointFilters\":[{\"filter\":\"all\",\"label\":\"All Exceptions\"},{\"filter\":\"uncaught\",\"label\":\"Uncaught Exceptions\"}]},\"request_seq\":1,\"command\":\"initialize\"}");
        tester.sendMessage(
                        "{\"command\":\"attach\",\"arguments\":{\"type\":\"graalvm\",\"request\":\"attach\",\"name\":\"Attach\",\"port\":9229,\"protocol\":\"debugAdapter\"},\"type\":\"request\",\"seq\":2}");
        tester.compareReceivedMessages("{\"event\":\"output\",\"body\":{\"output\":\"Debugger attached.\",\"category\":\"stderr\"},\"type\":\"event\"}",
                        "{\"success\":true,\"type\":\"response\",\"request_seq\":2,\"command\":\"attach\"}");
        tester.sendMessage("{\"command\":\"loadedSources\",\"type\":\"request\",\"seq\":3}");
        tester.compareReceivedMessages(
                        "{\"success\":true,\"body\":{\"sources\":[{\"sourceReference\":1,\"name\":\"TestOutput1.itl\"}]},\"type\":\"response\",\"request_seq\":3,\"command\":\"loadedSources\",\"seq\":5}");
        tester.sendMessage("{\"command\":\"configurationDone\",\"type\":\"request\",\"seq\":4}");
        tester.compareReceivedMessages("{\"success\":true,\"type\":\"response\",\"request_seq\":4,\"command\":\"configurationDone\",\"seq\":6}");
        tester.eval(source2);
        tester.compareReceivedMessages("{\"event\":\"thread\",\"body\":{\"threadId\":2,\"reason\":\"started\"},\"type\":\"event\",\"seq\":7}");
        tester.compareReceivedMessages(
                        "{\"event\":\"loadedSource\",\"body\":{\"reason\":\"new\",\"source\":{\"sourceReference\":2,\"name\":\"TestOutput2.itl\"}},\"type\":\"event\",\"seq\":8}");
        tester.compareReceivedMessages(
                        "{\"event\":\"output\",\"body\":{\"output\":\"Text to stdout\\n\",\"category\":\"stdout\"},\"type\":\"event\",\"seq\":9}");
        tester.compareReceivedMessages(
                        "{\"event\":\"output\",\"body\":{\"output\":\"Text to stderr\\n\",\"category\":\"stderr\"},\"type\":\"event\",\"seq\":10}");
        tester.sendMessage("{\"command\":\"disconnect\",\"arguments\":{\"restart\":false},\"type\":\"request\",\"seq\":11}");
        tester.compareReceivedMessages(
                        "{\"success\":true,\"type\":\"response\",\"request_seq\":11,\"command\":\"disconnect\",\"seq\":11}");
        tester.finish();
        tester.getContext().eval(source3);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testMultiThreading() throws Exception {
        Source source = Source.newBuilder(InstrumentationTestLanguage.ID, new URL("file:///path/TestThreads.itl")).content("ROOT(\n" +
                        "DEFINE(f,\n" +
                        "  STATEMENT(),\n" +
                        "  STATEMENT(EXPRESSION()),\n" +
                        "  STATEMENT()\n" +
                        "),\n" +
                        "DEFINE(g,\n" +
                        "  STATEMENT(),\n" +
                        "  STATEMENT(EXPRESSION()),\n" +
                        "  STATEMENT()\n" +
                        "),\n" +
                        "STATEMENT(),\n" +
                        "SPAWN(f),\n" +
                        "SPAWN(g),\n" +
                        "JOIN(),\n" +
                        "STATEMENT()\n" +
                        ")\n").build();
        String path = source.getPath();
        String sourceJson = "{\"sourceReference\":1,\"path\":\"/path/TestThreads.itl\",\"name\":\"TestThreads.itl\"}";
        tester = DAPTester.start(false);
        tester.sendMessage(
                        "{\"command\":\"initialize\",\"arguments\":{\"clientID\":\"DAPTester\",\"clientName\":\"DAP Tester\",\"adapterID\":\"graalvm\",\"pathFormat\":\"path\",\"linesStartAt1\":true,\"columnsStartAt1\":true," +
                                        "\"supportsVariableType\":true,\"supportsVariablePaging\":true,\"supportsRunInTerminalRequest\":true,\"locale\":\"en-us\",\"supportsProgressReporting\":true},\"type\":\"request\",\"seq\":1}");
        tester.compareReceivedMessages(
                        "{\"event\":\"initialized\",\"type\":\"event\"}",
                        "{\"success\":true,\"type\":\"response\",\"body\":{\"supportsConditionalBreakpoints\":true,\"supportsLoadedSourcesRequest\":true,\"supportsFunctionBreakpoints\":true,\"supportsExceptionInfoRequest\":true," +
                                        "\"supportsBreakpointLocationsRequest\":true,\"supportsHitConditionalBreakpoints\":true,\"supportsLogPoints\":true,\"supportsSetVariable\":true,\"supportsConfigurationDoneRequest\":true," +
                                        "\"exceptionBreakpointFilters\":[{\"filter\":\"all\",\"label\":\"All Exceptions\"},{\"filter\":\"uncaught\",\"label\":\"Uncaught Exceptions\"}]},\"request_seq\":1,\"command\":\"initialize\"}");
        tester.sendMessage(
                        "{\"command\":\"attach\",\"arguments\":{\"type\":\"graalvm\",\"request\":\"attach\",\"name\":\"Attach\",\"port\":9229,\"protocol\":\"debugAdapter\"},\"type\":\"request\",\"seq\":2}");
        tester.compareReceivedMessages("{\"event\":\"output\",\"body\":{\"output\":\"Debugger attached.\",\"category\":\"stderr\"},\"type\":\"event\"}",
                        "{\"success\":true,\"type\":\"response\",\"request_seq\":2,\"command\":\"attach\"}");
        tester.sendMessage("{\"command\":\"setBreakpoints\",\"arguments\":{\"source\":{\"name\":\"TestThreads.itl\",\"path\":\"" + path +
                        "\"},\"lines\":[3,8],\"breakpoints\":[{\"line\":3},{\"line\":8}],\"sourceModified\":false},\"type\":\"request\",\"seq\":4}");
        tester.compareReceivedMessages(
                        "{\"success\":true,\"body\":{\"breakpoints\":[{\"line\":3,\"verified\":false,\"id\":1},{\"line\":8,\"verified\":false,\"id\":2}]},\"type\":\"response\",\"request_seq\":4,\"command\":\"setBreakpoints\",\"seq\":5}");
        tester.sendMessage("{\"command\":\"configurationDone\",\"type\":\"request\",\"seq\":5}");
        tester.compareReceivedMessages("{\"success\":true,\"type\":\"response\",\"request_seq\":5,\"command\":\"configurationDone\",\"seq\":6}");
        tester.eval(source);
        tester.compareReceivedMessages("{\"event\":\"thread\",\"body\":{\"threadId\":1,\"reason\":\"started\"},\"type\":\"event\",\"seq\":7}");
        tester.compareReceivedMessages(
                        "{\"event\":\"loadedSource\",\"body\":{\"reason\":\"new\",\"source\":" + sourceJson + "},\"type\":\"event\",\"seq\":8}");
        tester.compareReceivedMessages(
                        "{\"event\":\"breakpoint\",\"body\":{\"reason\":\"changed\",\"breakpoint\":{\"endLine\":3,\"endColumn\":13,\"line\":3,\"verified\":true,\"column\":3,\"id\":1}},\"type\":\"event\",\"seq\":9}");
        tester.compareReceivedMessages(
                        "{\"event\":\"breakpoint\",\"body\":{\"reason\":\"changed\",\"breakpoint\":{\"endLine\":8,\"endColumn\":13,\"line\":8,\"verified\":true,\"column\":3,\"id\":2}},\"type\":\"event\",\"seq\":10}");

        // Threads started:
        Pattern threadStarted = Pattern.compile("\\{\"event\":\"thread\",\"body\":\\{\"threadId\":\\d,\"reason\":\"started\"\\},\"type\":\"event\",\"seq\":\\d+\\}");
        // Suspend at the breakpoint:
        Pattern stopped = Pattern.compile(
                        "\\{\"event\":\"stopped\",\"body\":\\{\"threadId\":\\d,\"reason\":\"breakpoint\",\"description\":\"Paused on breakpoint\"\\},\"type\":\"event\",\"seq\":\\d+\\}");

        // The first thread started
        String message = tester.getMessage();
        Assert.assertTrue(message, threadStarted.matcher(message).matches());

        // Either the second thread started, or the first one hit the breakpoint:
        message = tester.getMessage();
        if (threadStarted.matcher(message).matches()) {
            // The second thread started
            Assert.assertTrue(message, threadStarted.matcher(message).matches());
            // The first breakpoint is hit
            message = tester.getMessage();
            Assert.assertTrue(message, stopped.matcher(message).matches());
            // The second breakpoint is hit
            message = tester.getMessage();
            Assert.assertTrue(message, stopped.matcher(message).matches());
        } else {
            // The first breakpoint is hit
            Assert.assertTrue(message, stopped.matcher(message).matches());
            // Then the second thread started
            message = tester.getMessage();
            Assert.assertTrue(message, threadStarted.matcher(message).matches());
            // The second breakpoint is hit
            message = tester.getMessage();
            Assert.assertTrue(message, stopped.matcher(message).matches());
        }

        // Step on thread 2:
        tester.sendMessage("{\"command\":\"next\",\"arguments\":{\"threadId\":2},\"type\":\"request\",\"seq\":6}");
        tester.compareReceivedMessages(
                        "{\"event\":\"continued\",\"body\":{\"threadId\":2,\"allThreadsContinued\":false},\"type\":\"event\"}",
                        "{\"success\":true,\"type\":\"response\",\"request_seq\":6,\"command\":\"next\"}",
                        "{\"event\":\"stopped\",\"body\":{\"threadId\":2,\"reason\":\"debugger_statement\",\"description\":\"Paused on debugger statement\"},\"type\":\"event\"}");

        // Step on thread 3:
        tester.sendMessage("{\"command\":\"next\",\"arguments\":{\"threadId\":3},\"type\":\"request\",\"seq\":7}");
        tester.compareReceivedMessages(
                        "{\"event\":\"continued\",\"body\":{\"threadId\":3,\"allThreadsContinued\":false},\"type\":\"event\"}",
                        "{\"success\":true,\"type\":\"response\",\"request_seq\":7,\"command\":\"next\"}",
                        "{\"event\":\"stopped\",\"body\":{\"threadId\":3,\"reason\":\"debugger_statement\",\"description\":\"Paused on debugger statement\"},\"type\":\"event\"}");

        // Verify all threads:
        tester.sendMessage("{\"command\":\"threads\",\"arguments\":{},\"type\":\"request\",\"seq\":8}");
        Pattern threads = Pattern.compile(
                        "\\{\"success\":true,\"body\":\\{\"threads\":\\[\\{\"name\":\"testRunner\",\"id\":1\\},\\{\"name\":\"Polyglot-instrumentation-test-language-\\d+\",\"id\":\\d\\}," +
                                        "\\{\"name\":\"Polyglot-instrumentation-test-language-\\d+\",\"id\":\\d\\}\\]\\},\"type\":\"response\",\"request_seq\":8,\"command\":\"threads\",\"seq\":21\\}");
        message = tester.getMessage();
        Assert.assertTrue(message, threads.matcher(message).matches());

        // Continue thread 2:
        tester.sendMessage("{\"command\":\"continue\",\"arguments\":{\"threadId\":2},\"type\":\"request\",\"seq\":11}");
        // Continue thread 3:
        tester.sendMessage("{\"command\":\"continue\",\"arguments\":{\"threadId\":3},\"type\":\"request\",\"seq\":12}");
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testBadSourceReference() throws Exception {
        Source source = Source.newBuilder(InstrumentationTestLanguage.ID, new URL("file:///path/TestSrcRef.itl")).content("ROOT(\n" +
                        "  STATEMENT(),\n" +
                        "  STATEMENT()\n" +
                        ")\n").build();
        String path = source.getPath();
        String sourceJson = "{\"sourceReference\":1,\"path\":\"/path/TestSrcRef.itl\",\"name\":\"TestSrcRef.itl\"}";
        tester = DAPTester.start(false);
        tester.sendMessage(
                        "{\"command\":\"initialize\",\"arguments\":{\"clientID\":\"DAPTester\",\"clientName\":\"DAP Tester\",\"adapterID\":\"graalvm\",\"pathFormat\":\"path\",\"linesStartAt1\":true,\"columnsStartAt1\":true," +
                                        "\"supportsVariableType\":true,\"supportsVariablePaging\":true,\"supportsRunInTerminalRequest\":true,\"locale\":\"en-us\",\"supportsProgressReporting\":true},\"type\":\"request\",\"seq\":1}");
        tester.compareReceivedMessages(
                        "{\"event\":\"initialized\",\"type\":\"event\"}",
                        "{\"success\":true,\"type\":\"response\",\"body\":{\"supportsConditionalBreakpoints\":true,\"supportsLoadedSourcesRequest\":true,\"supportsFunctionBreakpoints\":true,\"supportsExceptionInfoRequest\":true," +
                                        "\"supportsBreakpointLocationsRequest\":true,\"supportsHitConditionalBreakpoints\":true,\"supportsLogPoints\":true,\"supportsSetVariable\":true,\"supportsConfigurationDoneRequest\":true," +
                                        "\"exceptionBreakpointFilters\":[{\"filter\":\"all\",\"label\":\"All Exceptions\"},{\"filter\":\"uncaught\",\"label\":\"Uncaught Exceptions\"}]},\"request_seq\":1,\"command\":\"initialize\"}");
        // Non-existing sourceReference:30 and existing path.
        tester.sendMessage(
                        "{\"command\":\"setBreakpoints\",\"arguments\":{\"source\":{\"sourceReference\":30,\"name\":\"TestSrcRef.itl\",\"path\":\"" + path +
                                        "\"},\"breakpoints\":[{\"line\":3}],\"sourceModified\":false},\"type\":\"request\",\"seq\":4}");
        tester.compareReceivedMessages(
                        "{\"success\":true,\"body\":{\"breakpoints\":[{\"line\":3,\"verified\":false,\"id\":1}]},\"type\":\"response\",\"request_seq\":4,\"command\":\"setBreakpoints\",\"seq\":3}");
        tester.sendMessage(
                        "{\"command\":\"attach\",\"arguments\":{\"type\":\"graalvm\",\"request\":\"attach\",\"name\":\"Attach\",\"port\":9229,\"protocol\":\"debugAdapter\"},\"type\":\"request\",\"seq\":2}");
        tester.compareReceivedMessages("{\"event\":\"output\",\"body\":{\"output\":\"Debugger attached.\",\"category\":\"stderr\"},\"type\":\"event\"}",
                        "{\"success\":true,\"type\":\"response\",\"request_seq\":2,\"command\":\"attach\"}");
        tester.sendMessage("{\"command\":\"configurationDone\",\"type\":\"request\",\"seq\":4}");
        tester.compareReceivedMessages("{\"success\":true,\"type\":\"response\",\"request_seq\":4,\"command\":\"configurationDone\",\"seq\":6}");
        tester.eval(source);
        tester.compareReceivedMessages("{\"event\":\"thread\",\"body\":{\"threadId\":1,\"reason\":\"started\"},\"type\":\"event\",\"seq\":7}");
        tester.compareReceivedMessages(
                        "{\"event\":\"loadedSource\",\"body\":{\"reason\":\"new\",\"source\":{\"sourceReference\":1,\"path\":\"/path/TestSrcRef.itl\",\"name\":\"TestSrcRef.itl\"}},\"type\":\"event\",\"seq\":8}");
        tester.compareReceivedMessages(
                        "{\"event\":\"breakpoint\",\"body\":{\"reason\":\"changed\",\"breakpoint\":{\"endLine\":3,\"endColumn\":13,\"line\":3,\"verified\":true,\"column\":3,\"id\":1}},\"type\":\"event\",\"seq\":9}");
        // Suspend at the breakpoint:
        tester.compareReceivedMessages(
                        "{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"breakpoint\",\"description\":\"Paused on breakpoint\"},\"type\":\"event\",\"seq\":10}");
        tester.sendMessage("{\"command\":\"stackTrace\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":11}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"stackFrames\":[{\"line\":3,\"name\":\"\",\"column\":3,\"id\":1,\"source\":" + sourceJson +
                        "}],\"totalFrames\":1},\"type\":\"response\",\"request_seq\":11,\"command\":\"stackTrace\",\"seq\":11}");
        // Continue to finish:
        tester.sendMessage("{\"command\":\"continue\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":12}");
        tester.compareReceivedMessages(
                        "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\"}",
                        "{\"success\":true,\"body\":{\"allThreadsContinued\":false},\"type\":\"response\",\"request_seq\":12,\"command\":\"continue\"}");
        tester.finish();
    }

    @Test
    public void testEagerSourceLoad() throws Exception {
        Source source1 = Source.newBuilder(InstrumentationTestLanguage.ID, "ROOT(\n" +
                        "  EXPRESSION(),\n" +
                        "  EXPRESSION()" +
                        ")\n", "TestEagerSource1.itl").build();
        Source source2 = Source.newBuilder(InstrumentationTestLanguage.ID, "ROOT(\n" +
                        "  STATEMENT()\n" +
                        ")\n", "TestEagerSource2.itl").build();
        tester = DAPTester.start(false, context -> context.eval(source1));
        tester.sendMessage(
                        "{\"command\":\"initialize\",\"arguments\":{\"clientID\":\"DAPTester\",\"clientName\":\"DAP Tester\",\"adapterID\":\"graalvm\",\"pathFormat\":\"path\",\"linesStartAt1\":true,\"columnsStartAt1\":true," +
                                        "\"supportsVariableType\":true,\"supportsVariablePaging\":true,\"supportsRunInTerminalRequest\":true,\"locale\":\"en-us\",\"supportsProgressReporting\":true},\"type\":\"request\",\"seq\":1}");
        tester.compareReceivedMessages(
                        "{\"event\":\"initialized\",\"type\":\"event\"}",
                        "{\"success\":true,\"type\":\"response\",\"body\":{\"supportsConditionalBreakpoints\":true,\"supportsLoadedSourcesRequest\":true,\"supportsFunctionBreakpoints\":true,\"supportsExceptionInfoRequest\":true," +
                                        "\"supportsBreakpointLocationsRequest\":true,\"supportsHitConditionalBreakpoints\":true,\"supportsLogPoints\":true,\"supportsSetVariable\":true,\"supportsConfigurationDoneRequest\":true," +
                                        "\"exceptionBreakpointFilters\":[{\"filter\":\"all\",\"label\":\"All Exceptions\"},{\"filter\":\"uncaught\",\"label\":\"Uncaught Exceptions\"}]},\"request_seq\":1,\"command\":\"initialize\"}");
        tester.sendMessage(
                        "{\"command\":\"attach\",\"arguments\":{\"type\":\"graalvm\",\"request\":\"attach\",\"name\":\"Attach\",\"port\":9229,\"protocol\":\"debugAdapter\"},\"type\":\"request\",\"seq\":2}");
        tester.compareReceivedMessages("{\"event\":\"output\",\"body\":{\"output\":\"Debugger attached.\",\"category\":\"stderr\"},\"type\":\"event\"}",
                        "{\"success\":true,\"type\":\"response\",\"request_seq\":2,\"command\":\"attach\"}");
        tester.sendMessage("{\"command\":\"loadedSources\",\"type\":\"request\",\"seq\":3}");
        tester.compareReceivedMessages(
                        "{\"success\":true,\"body\":{\"sources\":[{\"sourceReference\":1,\"name\":\"TestEagerSource1.itl\"}]},\"type\":\"response\",\"request_seq\":3,\"command\":\"loadedSources\",\"seq\":5}");
        tester.sendMessage("{\"command\":\"configurationDone\",\"type\":\"request\",\"seq\":4}");
        tester.compareReceivedMessages("{\"success\":true,\"type\":\"response\",\"request_seq\":4,\"command\":\"configurationDone\",\"seq\":6}");
        tester.eval(source2);
        tester.compareReceivedMessages("{\"event\":\"thread\",\"body\":{\"threadId\":2,\"reason\":\"started\"},\"type\":\"event\",\"seq\":7}");
        tester.compareReceivedMessages(
                        "{\"event\":\"loadedSource\",\"body\":{\"reason\":\"new\",\"source\":{\"sourceReference\":2,\"name\":\"TestEagerSource2.itl\"}},\"type\":\"event\",\"seq\":8}");
        tester.sendMessage("{\"command\":\"disconnect\",\"arguments\":{\"restart\":false},\"type\":\"request\",\"seq\":11}");
        tester.compareReceivedMessages(
                        "{\"success\":true,\"type\":\"response\",\"request_seq\":11,\"command\":\"disconnect\",\"seq\":9}");
        tester.finish();
    }
}
