/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.util.Collections;

import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import org.graalvm.polyglot.Source;

import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;

public class TimeoutTest {

    @After
    public void tearDown() {
        InstrumentationTestLanguage.envConfig = null;
    }

    // @formatter:off   The default formatting makes unnecessarily big indents and illogical line breaks
    @Test
    public void testSuspensionTimeout() throws Exception {
        String code = "ROOT(STATEMENT)";
        Source source = Source.newBuilder(InstrumentationTestLanguage.ID, code, "TestFile").build();
        String sourceURI = InspectorTester.getStringURI(source.getURI());
        int codeLength = code.length();
        InspectorTester tester = InspectorTester.start(new InspectorTester.Options(true).setSuspensionTimeout(1000L));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        tester.setErr(output);
        tester.eval(source);
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        tester.sendMessage("{\"id\":2,\"method\":\"Debugger.enable\"}");
        tester.sendMessage("{\"id\":3,\"method\":\"Runtime.runIfWaitingForDebugger\"}");
        tester.receiveMessages(
                        "{\"result\":{},\"id\":1}\n" +
                        "{\"result\":{\"debuggerId\":\"UniqueDebuggerId.", "},\"id\":2}\n");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":3}\n" +
                        "{\"method\":\"Runtime.executionContextCreated\",\"params\":{\"context\":{\"origin\":\"\",\"name\":\"test\",\"id\":1}}}\n"));
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":0,\"scriptId\":\"0\",\"endColumn\":15,\"startColumn\":0,\"startLine\":0,\"length\":" + codeLength + "," +
                                "\"executionContextId\":1,\"url\":\"TestFile\",\"hash\":\"f4399823fb312268ffffffffffffffffffffffff\"}}\n"));
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"\"," +
                                                 "\"scopeChain\":[{\"name\":\"\",\"type\":\"local\",\"object\":{\"description\":\"\",\"type\":\"object\",\"objectId\":\"1\"}}]," +
                                                 "\"this\":null," +
                                                 "\"functionLocation\":{\"scriptId\":\"0\",\"columnNumber\":0,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"0\",\"columnNumber\":5,\"lineNumber\":0}," +
                                                 "\"url\":\"" + sourceURI + "\"}]}}\n"));
        // Do not resume, the suspension timeout breaks it
        tester.finish();
        String errMessage = new String(output.toByteArray());
        assertEquals("Timeout of 1000ms as specified via '--inspect.SuspensionTimeout' was reached. The debugger session is disconnected." + System.lineSeparator(), errMessage);
    }

    @Test
    public void testSuspensionTimeoutInInitializer() throws Exception {
        String code = "ROOT(STATEMENT)";
        Source source = Source.newBuilder(InstrumentationTestLanguage.ID, code, "TestFile").build();
        InstrumentationTestLanguage.envConfig = Collections.singletonMap("initSource", source);
        String sourceURI = InspectorTester.getStringURI(source.getURI());
        int codeLength = code.length();
        InspectorTester tester = InspectorTester.start(new InspectorTester.Options(true, false, true).setSuspensionTimeout(1000L));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        tester.setErr(output);
        tester.eval(source);
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        tester.sendMessage("{\"id\":2,\"method\":\"Debugger.enable\"}");
        tester.sendMessage("{\"id\":3,\"method\":\"Runtime.runIfWaitingForDebugger\"}");
        tester.receiveMessages(
                        "{\"result\":{},\"id\":1}\n" +
                        "{\"result\":{\"debuggerId\":\"UniqueDebuggerId.", "},\"id\":2}\n");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":3}\n" +
                        "{\"method\":\"Runtime.executionContextCreated\",\"params\":{\"context\":{\"origin\":\"\",\"name\":\"test\",\"id\":1}}}\n"));
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":0,\"scriptId\":\"0\",\"endColumn\":15,\"startColumn\":0,\"startLine\":0,\"length\":" + codeLength + "," +
                                "\"executionContextId\":1,\"url\":\"TestFile\",\"hash\":\"f4399823fb312268ffffffffffffffffffffffff\"}}\n"));
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"\"," +
                                                 "\"scopeChain\":[{\"name\":\"\",\"type\":\"local\",\"object\":{\"description\":\"\",\"type\":\"object\",\"objectId\":\"1\"}}]," +
                                                 "\"this\":null," +
                                                 "\"functionLocation\":{\"scriptId\":\"0\",\"columnNumber\":0,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"0\",\"columnNumber\":5,\"lineNumber\":0}," +
                                                 "\"url\":\"" + sourceURI + "\"}]}}\n"));
        // Do not resume, the suspension timeout breaks it
        tester.finish();
        String errMessage = new String(output.toByteArray());
        assertEquals("Timeout of 1000ms as specified via '--inspect.SuspensionTimeout' was reached. The debugger session is disconnected." + System.lineSeparator(), errMessage);
    }
    // @formatter:on
}
