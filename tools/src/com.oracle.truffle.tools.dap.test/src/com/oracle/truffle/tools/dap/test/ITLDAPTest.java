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

import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;
import org.graalvm.polyglot.Source;
import org.junit.After;
import org.junit.Test;

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
                        "  PRINT(OUT, \"one\ntwo\n\"),\n" +
                        "  STATEMENT(),\n" +
                        "  PRINT(OUT, \"three,\"),\n" +
                        "  STATEMENT(),\n" +
                        "  PRINT(OUT, \"four\rfive\"),\n" +
                        "  STATEMENT(),\n" +
                        "  PRINT(OUT, \"\r\n\"),\n" +
                        "  PRINT(OUT, \"\r\nsix,\"),\n" +
                        "  PRINT(OUT, \"seven\n\neight\"),\n" +
                        "  STATEMENT(),\n" +
                        "  PRINT(ERR, \"1err\n2err\r\n\"),\n" +
                        "  STATEMENT(),\n" +
                        "  PRINT(OUT, \"\r\nnine\rten\r\n\")\n" +
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
                        "{\"event\":\"continued\",\"body\":{\"threadId\":1},\"type\":\"event\"}",
                        "{\"success\":true,\"type\":\"response\",\"request_seq\":7,\"command\":\"next\"}",
                        "{\"event\":\"output\",\"body\":{\"output\":\"three,\",\"category\":\"stdout\"},\"type\":\"event\",\"seq\":14}",
                        "{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"debugger_statement\",\"description\":\"Paused on debugger statement\"},\"type\":\"event\"}");
        // Next:
        tester.sendMessage("{\"command\":\"next\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":7}");
        tester.compareReceivedMessages(
                        "{\"event\":\"continued\",\"body\":{\"threadId\":1},\"type\":\"event\"}",
                        "{\"success\":true,\"type\":\"response\",\"request_seq\":7,\"command\":\"next\"}",
                        "{\"event\":\"output\",\"body\":{\"output\":\"four\\rfive\",\"category\":\"stdout\"},\"type\":\"event\",\"seq\":18}",
                        "{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"debugger_statement\",\"description\":\"Paused on debugger statement\"},\"type\":\"event\"}");
        // Next:
        tester.sendMessage("{\"command\":\"next\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":7}");
        tester.compareReceivedMessages(
                        "{\"event\":\"continued\",\"body\":{\"threadId\":1},\"type\":\"event\"}",
                        "{\"success\":true,\"type\":\"response\",\"request_seq\":7,\"command\":\"next\"}",
                        "{\"event\":\"output\",\"body\":{\"output\":\"\\r\\n\",\"category\":\"stdout\"},\"type\":\"event\",\"seq\":22}",
                        "{\"event\":\"output\",\"body\":{\"output\":\"\\r\\nsix,\",\"category\":\"stdout\"},\"type\":\"event\",\"seq\":23}",
                        "{\"event\":\"output\",\"body\":{\"output\":\"seven\\n\\neight\",\"category\":\"stdout\"},\"type\":\"event\",\"seq\":24}",
                        "{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"debugger_statement\",\"description\":\"Paused on debugger statement\"},\"type\":\"event\"}");
        // Next:
        tester.sendMessage("{\"command\":\"next\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":7}");
        tester.compareReceivedMessages(
                        "{\"event\":\"continued\",\"body\":{\"threadId\":1},\"type\":\"event\"}",
                        "{\"success\":true,\"type\":\"response\",\"request_seq\":7,\"command\":\"next\"}",
                        "{\"event\":\"output\",\"body\":{\"output\":\"1err\\n2err\\r\\n\",\"category\":\"stderr\"},\"type\":\"event\",\"seq\":28}",
                        "{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"debugger_statement\",\"description\":\"Paused on debugger statement\"},\"type\":\"event\"}");
        // Next:
        tester.sendMessage("{\"command\":\"next\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":7}");
        tester.compareReceivedMessages(
                        "{\"event\":\"continued\",\"body\":{\"threadId\":1},\"type\":\"event\"}",
                        "{\"success\":true,\"type\":\"response\",\"request_seq\":7,\"command\":\"next\"}",
                        "{\"event\":\"output\",\"body\":{\"output\":\"\\r\\nnine\\rten\\r\\n\",\"category\":\"stdout\"},\"type\":\"event\",\"seq\":32}",
                        "{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"debugger_statement\",\"description\":\"Paused on debugger statement\"},\"type\":\"event\"}");
        // Next:
        tester.sendMessage("{\"command\":\"next\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":7}");
        tester.compareReceivedMessages(
                        "{\"event\":\"continued\",\"body\":{\"threadId\":1},\"type\":\"event\"}",
                        "{\"success\":true,\"type\":\"response\",\"request_seq\":7,\"command\":\"next\"}");
        tester.finish();
    }

}
