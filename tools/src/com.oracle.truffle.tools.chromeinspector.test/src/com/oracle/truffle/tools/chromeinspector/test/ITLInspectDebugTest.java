/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Collections;

import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import org.graalvm.polyglot.Source;

import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;

/**
 * {@link InstrumentationTestLanguage} inspector debugging test.
 */
public class ITLInspectDebugTest {

    private InspectorTester tester;

    @After
    public void tearDown() {
        InstrumentationTestLanguage.envConfig = null;
        tester = null;
    }

    // @formatter:off   The default formatting makes unnecessarily big indents and illogical line breaks
    // CheckStyle: stop line length check
    @Test
    public void testSuspendInInitialization() throws Exception {
        Source initSource = Source.newBuilder(InstrumentationTestLanguage.ID, "STATEMENT(EXPRESSION)", "<init>").build();
        InstrumentationTestLanguage.envConfig = Collections.singletonMap("initSource", initSource);
        Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "ROOT(\n" +
                        "  STATEMENT(CONSTANT(42))\n" +
                        ")\n", "code").build();
        String initURI = InspectorTester.getStringURI(initSource.getURI());
        String sourceURI = InspectorTester.getStringURI(source.getURI());

        // Suspend after the initilization (by default):
        tester = InspectorTester.start(true, false, false);
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
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":0,\"scriptId\":\"0\",\"endColumn\":21,\"startColumn\":0,\"startLine\":0,\"length\":21," +
                                "\"executionContextId\":" + id + ",\"url\":\"" + initURI + "\",\"hash\":\"dcbe3658d9dedef2f282a058ffffffffffffffff\"}}\n" +
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":2,\"scriptId\":\"1\",\"endColumn\":1,\"startColumn\":0,\"startLine\":0,\"length\":34," +
                                "\"executionContextId\":" + id + ",\"url\":\"" + sourceURI + "\",\"hash\":\"f4399823ddd23020f6fa2ee2ffffffffffffffff\"}}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"\"," +
                                                 "\"scopeChain\":[{\"name\":\"\",\"type\":\"local\",\"object\":{\"description\":\"\",\"type\":\"object\",\"objectId\":\"1\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"null\",\"type\":\"object\",\"objectId\":\"2\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"1\",\"columnNumber\":0,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"1\",\"columnNumber\":2,\"lineNumber\":1}," +
                                                 "\"url\":\"" + sourceURI + "\"}]}}\n"));

        tester.sendMessage("{\"id\":4,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":4}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        tester.finish();

        // Suspend in the initialization source:
        tester = InspectorTester.start(true, false, true);
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        assertEquals("{\"result\":{},\"id\":1}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":2,\"method\":\"Debugger.enable\"}");
        assertEquals("{\"result\":{},\"id\":2}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":3,\"method\":\"Runtime.runIfWaitingForDebugger\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":3}\n" +
                        "{\"method\":\"Runtime.executionContextCreated\",\"params\":{\"context\":{\"origin\":\"\",\"name\":\"test\",\"id\":1}}}\n"));
        tester.eval(source);
        id = tester.getContextId();
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":0,\"scriptId\":\"0\",\"endColumn\":21,\"startColumn\":0,\"startLine\":0,\"length\":21," +
                                "\"executionContextId\":" + id + ",\"url\":\"" + initURI + "\",\"hash\":\"dcbe3658d9dedef2f282a058ffffffffffffffff\"}}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"\"," +
                                                 "\"scopeChain\":[{\"name\":\"\",\"type\":\"local\",\"object\":{\"description\":\"\",\"type\":\"object\",\"objectId\":\"1\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"null\",\"type\":\"object\",\"objectId\":\"2\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"0\",\"columnNumber\":0,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"0\",\"columnNumber\":0,\"lineNumber\":0}," +
                                                 "\"url\":\"" + initURI + "\"}]}}\n"));

        tester.sendMessage("{\"id\":4,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":4}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":2,\"scriptId\":\"1\",\"endColumn\":1,\"startColumn\":0,\"startLine\":0,\"length\":34," +
                                "\"executionContextId\":" + id + ",\"url\":\"" + sourceURI + "\",\"hash\":\"f4399823ddd23020f6fa2ee2ffffffffffffffff\"}}\n"));
        tester.finish();
    }

    @Test
    public void testSuspendInInternal() throws Exception {
        Source internSource = Source.newBuilder(InstrumentationTestLanguage.ID, "STATEMENT(EXPRESSION)", "<intern>").internal(true).build();
        Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "ROOT(\n" +
                        "  STATEMENT(CONSTANT(42))\n" +
                        ")\n", "code").build();
        String internURI = InspectorTester.getStringURI(internSource.getURI());
        String sourceURI = InspectorTester.getStringURI(source.getURI());

        // Suspend in non-internal source (by default):
        tester = InspectorTester.start(true, false, false);
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        assertEquals("{\"result\":{},\"id\":1}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":2,\"method\":\"Debugger.enable\"}");
        assertEquals("{\"result\":{},\"id\":2}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":3,\"method\":\"Runtime.runIfWaitingForDebugger\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":3}\n" +
                        "{\"method\":\"Runtime.executionContextCreated\",\"params\":{\"context\":{\"origin\":\"\",\"name\":\"test\",\"id\":1}}}\n"));
        tester.eval(internSource);
        long id = tester.getContextId();
        tester.eval(source);
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":2,\"scriptId\":\"0\",\"endColumn\":1,\"startColumn\":0,\"startLine\":0,\"length\":34," +
                                "\"executionContextId\":" + id + ",\"url\":\"" + sourceURI + "\",\"hash\":\"f4399823ddd23020f6fa2ee2ffffffffffffffff\"}}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"\"," +
                                                 "\"scopeChain\":[{\"name\":\"\",\"type\":\"local\",\"object\":{\"description\":\"\",\"type\":\"object\",\"objectId\":\"1\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"null\",\"type\":\"object\",\"objectId\":\"2\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"0\",\"columnNumber\":0,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"0\",\"columnNumber\":2,\"lineNumber\":1}," +
                                                 "\"url\":\"" + sourceURI + "\"}]}}\n"));

        tester.sendMessage("{\"id\":4,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":4}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        tester.finish();

        // Suspend in the internal source:
        tester = InspectorTester.start(true, true, false);
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        assertEquals("{\"result\":{},\"id\":1}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":2,\"method\":\"Debugger.enable\"}");
        assertEquals("{\"result\":{},\"id\":2}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":3,\"method\":\"Runtime.runIfWaitingForDebugger\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":3}\n" +
                        "{\"method\":\"Runtime.executionContextCreated\",\"params\":{\"context\":{\"origin\":\"\",\"name\":\"test\",\"id\":1}}}\n"));
        tester.eval(internSource);
        id = tester.getContextId();
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":0,\"scriptId\":\"0\",\"endColumn\":21,\"startColumn\":0,\"startLine\":0,\"length\":21," +
                                "\"executionContextId\":" + id + ",\"url\":\"" + internURI + "\",\"hash\":\"dcbe3658d9dedef2f282a058ffffffffffffffff\"}}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"\"," +
                                                 "\"scopeChain\":[{\"name\":\"\",\"type\":\"local\",\"object\":{\"description\":\"\",\"type\":\"object\",\"objectId\":\"1\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"null\",\"type\":\"object\",\"objectId\":\"2\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"0\",\"columnNumber\":0,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"0\",\"columnNumber\":0,\"lineNumber\":0}," +
                                                 "\"url\":\"" + internURI + "\"}]}}\n"));

        tester.sendMessage("{\"id\":4,\"method\":\"Debugger.resume\"}");
        tester.eval(source);
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":4}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n" +
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":2,\"scriptId\":\"1\",\"endColumn\":1,\"startColumn\":0,\"startLine\":0,\"length\":34," +
                                "\"executionContextId\":" + id + ",\"url\":\"" + sourceURI + "\",\"hash\":\"f4399823ddd23020f6fa2ee2ffffffffffffffff\"}}\n"));
        tester.finish();
    }

    @Test
    public void testThis() throws Exception {
        Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "ROOT(DEFINE(a,ROOT(\n" +
                        "  STATEMENT())\n" +
                        "),\n" +
                        "CALL_WITH(a, 42))\n", "code").build();
        String sourceURI = InspectorTester.getStringURI(source.getURI());

        // Suspend after the initilization (by default):
        tester = InspectorTester.start(true, false, false);
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
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":3,\"scriptId\":\"0\",\"endColumn\":17,\"startColumn\":0,\"startLine\":0,\"length\":56," +
                                "\"executionContextId\":" + id + ",\"url\":\"" + sourceURI + "\",\"hash\":\"f4399823ddd23020fa0ce116fd2aa5d1ffffffff\"}}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"a\"," +
                                                 "\"scopeChain\":[{\"name\":\"a\",\"type\":\"local\",\"object\":{\"description\":\"a\",\"type\":\"object\",\"objectId\":\"1\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"2\"}}]," +
                                                 "\"this\":{\"description\":\"42\",\"type\":\"number\",\"value\":42}," +
                                                 "\"functionLocation\":{\"scriptId\":\"0\",\"columnNumber\":14,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"0\",\"columnNumber\":2,\"lineNumber\":1}," +
                                                 "\"url\":\"" + sourceURI + "\"}," +
                                                "{\"callFrameId\":\"1\",\"functionName\":\"\"," +
                                                 "\"scopeChain\":[{\"name\":\"\",\"type\":\"local\",\"object\":{\"description\":\"\",\"type\":\"object\",\"objectId\":\"3\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"4\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"null\",\"type\":\"object\",\"objectId\":\"5\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"0\",\"columnNumber\":0,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"0\",\"columnNumber\":0,\"lineNumber\":3}," +
                                                 "\"url\":\"" + sourceURI + "\"}]}}\n"));

        tester.sendMessage("{\"id\":4,\"method\":\"Runtime.evaluate\",\"params\":{\"expression\":\"THIS\",\"objectGroup\":\"watch-group\",\"includeCommandLineAPI\":false,\"silent\":true,\"contextId\":" + id + "}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":{\"description\":\"42\",\"type\":\"number\",\"value\":42}},\"id\":4}\n"));

        tester.sendMessage("{\"id\":5,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":5}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        tester.finish();
    }

    @Test
    public void testShortURIs() throws Exception {
        // Test that URIs of 'truffle://' sources are shortened and 'truffle' is eliminated.
        tester = InspectorTester.start(false);
        Source source1 = Source.newBuilder(InstrumentationTestLanguage.ID, "ROOT(STATEMENT )", "TestFile").build();
        String testFileURI1 = InspectorTester.getStringURI(source1.getURI());
        Source source2 = Source.newBuilder(InstrumentationTestLanguage.ID, "ROOT( STATEMENT)", "TestFile").build();
        // source2 has the same name as source1.
        // Our URI contains the full scheme-specific part to have the URI unique.
        String testFileURI2 = "2/TestFile";

        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        tester.sendMessage("{\"id\":2,\"method\":\"Debugger.enable\"}");
        tester.sendMessage("{\"id\":3,\"method\":\"Runtime.runIfWaitingForDebugger\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":1}\n" +
                        "{\"result\":{},\"id\":2}\n" +
                        "{\"result\":{},\"id\":3}\n" +
                        "{\"method\":\"Runtime.executionContextCreated\",\"params\":{\"context\":{\"origin\":\"\",\"name\":\"test\",\"id\":1}}}\n"));
        tester.eval(source1);
        long id = tester.getContextId();
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":0,\"scriptId\":\"0\",\"endColumn\":16,\"startColumn\":0,\"startLine\":0,\"length\":" + 16 + ",\"executionContextId\":" + id + ",\"url\":\"" + testFileURI1 + "\",\"hash\":\"f4399823ffffffffffffffffffffffffffffffff\"}}\n"));
        tester.eval(source2);
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":0,\"scriptId\":\"1\",\"endColumn\":16,\"startColumn\":0,\"startLine\":0,\"length\":" + 16 + ",\"executionContextId\":" + id + ",\"url\":\"" + testFileURI2 + "\",\"hash\":\"f4399823ffffffffffffffffffffffffffffffff\"}}\n"));

        // Assure that file sources start with 'file://'
        // and therefore can not clash with literal sources
        File file = File.createTempFile("TestFile", "");
        file.deleteOnExit();
        Files.write(file.toPath(), Collections.singleton("ROOT(STATEMENT) "), StandardOpenOption.CREATE);
        long length = file.length();
        Source source3 = Source.newBuilder(InstrumentationTestLanguage.ID, file).build();
        String testFileURI3 = InspectorTester.getStringURI(source3.getURI());
        assertTrue(testFileURI3, testFileURI3.startsWith("file://"));
        tester.eval(source3);
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":0,\"scriptId\":\"2\",\"endColumn\":16,\"startColumn\":0,\"startLine\":0,\"length\":" + length + ",\"executionContextId\":" + id + ",\"url\":\"" + testFileURI3 + "\",\"hash\":\"f4399823e456ed0affffffffffffffffffffffff\"}}\n"));

        tester.finish();
    }

    // @formatter:on
    // CheckStyle: resume line length check
}
