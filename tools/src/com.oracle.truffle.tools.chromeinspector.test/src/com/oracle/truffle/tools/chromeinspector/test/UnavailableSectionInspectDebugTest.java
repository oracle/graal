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
package com.oracle.truffle.tools.chromeinspector.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import org.graalvm.polyglot.Source;

import com.oracle.truffle.api.debug.test.SuspensionFilterTest;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

/**
 * Test that we do not suspend on unavailable source sections.
 */
public class UnavailableSectionInspectDebugTest {

    @Test
    public void testNoStopInUnavailable() throws InterruptedException {
        ProxyLanguage.setDelegate(SuspensionFilterTest.createSectionAvailabilityTestLanguage());
        InspectorTester tester = InspectorTester.start(true, false, false);

        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        assertEquals("{\"result\":{},\"id\":1}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":2,\"method\":\"Debugger.enable\"}");
        tester.receiveMessages(
                        "{\"result\":{\"debuggerId\":\"UniqueDebuggerId.", "},\"id\":2}\n");
        tester.sendMessage("{\"id\":3,\"method\":\"Runtime.runIfWaitingForDebugger\"}");
        // @formatter:off   The default formatting makes unnecessarily big indents and illogical line breaks
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":3}\n" +
                        "{\"method\":\"Runtime.executionContextCreated\",\"params\":{\"context\":{\"origin\":\"\",\"name\":\"test\",\"id\":1}}}\n"));
        // @formatter:on

        long cid = tester.getContextId();
        int cmdId = 1;
        String[] hashes = new String[]{"fc4f434affffffffffffffffffffffffffffffff", "daaab888ffffffffffffffffffffffffffffffff"};
        for (int i = 0; i < 3; i++) {
            String sourceName = i + ".test";
            // Create RootWithUnavailableSection.sectionAvailability = i:
            Source source = Source.newBuilder(ProxyLanguage.ID, Integer.toString(i), sourceName).buildLiteral();
            tester.eval(source);
            // @formatter:off
            if (i < 2) { // No Source when SourceSection is null
                assertTrue(tester.compareReceivedMessages(
                                "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":0,\"scriptId\":\"" + i + "\",\"endColumn\":1,\"startColumn\":0,\"startLine\":0,\"length\":1," +
                                                                                  "\"executionContextId\":" + cid + ",\"url\":\"" + sourceName + "\",\"hash\":\"" + hashes[i] + "\"}}\n"));
            }
            if (i == 0) {
                assertTrue(tester.compareReceivedMessages(
                                "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                        "\"callFrames\":[{\"callFrameId\":\"0\"," +
                                                         "\"returnValue\":{\"description\":\"42\",\"type\":\"number\",\"value\":42}," +
                                                         "\"functionName\":\"\"," +
                                                         "\"scopeChain\":[]," +
                                                         "\"this\":null," +
                                                         "\"location\":{\"scriptId\":\"" + i + "\",\"columnNumber\":1,\"lineNumber\":0}," +
                                                         "\"url\":\"" + sourceName + "\"}]}}\n"));
                cmdId++;
                tester.sendMessage("{\"id\":" + cmdId + ",\"method\":\"Debugger.stepOver\"}");
                assertTrue(tester.compareReceivedMessages(
                                "{\"result\":{},\"id\":" + cmdId + "}\n" +
                                "{\"method\":\"Debugger.resumed\"}\n"));
            }
            // @formatter:on
        }
        // Wait for evals to finish, since there may be no protocol communication from some of them.
        tester.finishNoGC();
        // Reset the delegate so that we can GC the tested Engine
        ProxyLanguage.setDelegate(new ProxyLanguage());
        tester.finish();
    }
}
