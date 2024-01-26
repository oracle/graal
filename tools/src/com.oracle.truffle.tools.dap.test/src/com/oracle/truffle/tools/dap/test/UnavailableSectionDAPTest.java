/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Test;

import org.graalvm.polyglot.Source;

import com.oracle.truffle.api.debug.test.SuspensionFilterTest;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

/**
 * Test that we do not suspend on unavailable source sections.
 */
public class UnavailableSectionDAPTest {

    @Test
    public void testNoStopInUnavailable() throws Exception {
        ProxyLanguage.setDelegate(SuspensionFilterTest.createSectionAvailabilityTestLanguage());
        DAPTester tester = DAPTester.start(true, null);

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
        tester.sendMessage("{\"command\":\"configurationDone\",\"type\":\"request\",\"seq\":5}");
        tester.compareReceivedMessages("{\"success\":true,\"type\":\"response\",\"request_seq\":5,\"command\":\"configurationDone\",\"seq\":5}");

        int seq = 6;
        for (int i = 0; i < 3; i++) {
            String sourceName = i + ".test";
            // Create RootWithUnavailableSection.sectionAvailability = i:
            Source source = Source.newBuilder(ProxyLanguage.ID, Integer.toString(i), sourceName).buildLiteral();
            tester.eval(source);
            if (i == 0) {
                tester.compareReceivedMessages("{\"event\":\"thread\",\"body\":{\"threadId\":1,\"reason\":\"started\"},\"type\":\"event\",\"seq\":" + (seq++) + "}");
            }
            if (i < 2) { // No Source when SourceSection is null
                String sourceJson = "{\"sourceReference\":" + (i + 1) + ",\"name\":\"" + sourceName + "\"}";
                tester.compareReceivedMessages(
                                "{\"event\":\"loadedSource\",\"body\":{\"reason\":\"new\",\"source\":" + sourceJson + "},\"type\":\"event\",\"seq\":" + (seq++) + "}");
            }
            if (i == 0) {
                tester.compareReceivedMessages(
                                "{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"debugger_statement\",\"description\":\"Paused on debugger statement\"},\"type\":\"event\",\"seq\":" +
                                                (seq++) + "}");
                tester.sendMessage("{\"command\":\"next\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":7}");
                tester.compareReceivedMessages(
                                "{\"success\":true,\"type\":\"response\",\"request_seq\":7,\"command\":\"next\",\"seq\":" + (seq++) + "}");
                tester.compareReceivedMessages(
                                "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\",\"seq\":" + (seq++) + "}");
            }
        }
        tester.finish();
    }
}
