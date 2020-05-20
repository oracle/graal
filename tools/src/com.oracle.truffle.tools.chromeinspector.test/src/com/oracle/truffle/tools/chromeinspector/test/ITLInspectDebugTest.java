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
import com.oracle.truffle.tck.DebuggerTester;
import com.oracle.truffle.tools.chromeinspector.types.Script;

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
        String hash = new Script(0, null, DebuggerTester.getSourceImpl(source3)).getHash();
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":0,\"scriptId\":\"2\",\"endColumn\":16,\"startColumn\":0,\"startLine\":0,\"length\":" + length + ",\"executionContextId\":" + id + ",\"url\":\"" + testFileURI3 + "\",\"hash\":\"" + hash + "\"}}\n"));

        tester.finish();
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
                        "  PRINT(OUT, \"\r\nnine\rten\r\n\")\n" +
                        ")\n", "code").build();
        String sourceURI = InspectorTester.getStringURI(source.getURI());
        tester = InspectorTester.start(true);
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
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":22,\"scriptId\":\"0\",\"endColumn\":1,\"startColumn\":0,\"startLine\":0,\"length\":248," +
                                "\"executionContextId\":" + id + ",\"url\":\"" + sourceURI + "\",\"hash\":\"e47e9ba0e3dc9092fc857bbaf75a5a33fe8aba69\"}}\n"));
        tester.receiveMessages(
                        "{\"method\":\"Runtime.consoleAPICalled\"", "\"value\":\"one\\ntwo\"}",
                        "}}\n");
        tester.receiveMessages("{\"method\":\"Debugger.paused\"", "\"url\":\"" + sourceURI + "\"}]}}\n");
        tester.sendMessage("{\"id\":5,\"method\":\"Debugger.stepOver\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":5}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        // no newline, no output
        tester.receiveMessages("{\"method\":\"Debugger.paused\"", "\"url\":\"" + sourceURI + "\"}]}}\n");
        tester.sendMessage("{\"id\":5,\"method\":\"Debugger.stepOver\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":5}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        tester.receiveMessages(
                        "{\"method\":\"Runtime.consoleAPICalled\"", "\"value\":\"three,four\"}",
                        "}}\n");
        tester.receiveMessages("{\"method\":\"Debugger.paused\"", "\"url\":\"" + sourceURI + "\"}]}}\n");
        tester.sendMessage("{\"id\":5,\"method\":\"Debugger.stepOver\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":5}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        tester.receiveMessages(
                        "{\"method\":\"Runtime.consoleAPICalled\"", "\"value\":\"five\"}",
                        "}}\n");
        tester.receiveMessages(
                        "{\"method\":\"Runtime.consoleAPICalled\"", "\"value\":\"\"}",
                        "}}\n");
        tester.receiveMessages(
                        "{\"method\":\"Runtime.consoleAPICalled\"", "\"value\":\"six,seven\\n\"}",
                        "}}\n");
        tester.receiveMessages("{\"method\":\"Debugger.paused\"", "\"url\":\"" + sourceURI + "\"}]}}\n");
        tester.sendMessage("{\"id\":5,\"method\":\"Debugger.stepOver\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":5}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        tester.receiveMessages(
                        "{\"method\":\"Runtime.consoleAPICalled\"", "\"value\":\"eight\\r\\nnine\\rten\"}",
                        "}}\n");
        tester.receiveMessages("{\"method\":\"Debugger.paused\"", "\"url\":\"" + sourceURI + "\"}]}}\n");
        tester.sendMessage("{\"id\":5,\"method\":\"Debugger.stepOver\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":5}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        tester.finish();
    }

    public void testAsynchronousStackTraces() throws Exception {
        String code = "ROOT(DEFINE(af11, ROOT(STATEMENT)),\n" +
                        "DEFINE(af12, ROOT(CALL(af11))),\n" +
                        "DEFINE(af21, ROOT(STATEMENT, SPAWN(af12))),\n" +
                        "DEFINE(af22, ROOT(CALL(af21))),\n" +
                        "DEFINE(f1, ROOT(SPAWN(af22))),\n" +
                        "DEFINE(f2, ROOT(CALL(f1))),\n" +
                        "CALL(f2))\n";
        Source source = Source.newBuilder(InstrumentationTestLanguage.ID, code, "TestFile").build();
        String sourceURI = InspectorTester.getStringURI(source.getURI());
        int codeLength = code.length();
        tester = InspectorTester.start(false);
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        tester.sendMessage("{\"id\":2,\"method\":\"Debugger.enable\"}");
        tester.sendMessage("{\"id\":3,\"method\":\"Debugger.setAsyncCallStackDepth\",\"params\":{\"maxDepth\":1}}");
        tester.sendMessage("{\"id\":4,\"method\":\"Runtime.runIfWaitingForDebugger\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":1}\n" +
                        "{\"result\":{},\"id\":2}\n" +
                        "{\"result\":{},\"id\":3}\n" +
                        "{\"result\":{},\"id\":4}\n" +
                        "{\"method\":\"Runtime.executionContextCreated\",\"params\":{\"context\":{\"origin\":\"\",\"name\":\"test\",\"id\":1}}}\n"));
        tester.sendMessage("{\"id\":5,\"method\":\"Debugger.setBreakpointByUrl\",\"params\":{\"lineNumber\":0,\"url\":\"" + sourceURI + "\",\"columnNumber\":23,\"condition\":\"\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"breakpointId\":\"1\",\"locations\":[]},\"id\":5}\n"));
        tester.sendMessage("{\"id\":6,\"method\":\"Debugger.setBreakpointByUrl\",\"params\":{\"lineNumber\":2,\"url\":\"" + sourceURI + "\",\"columnNumber\":20,\"condition\":\"\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"breakpointId\":\"2\",\"locations\":[]},\"id\":6}\n"));
        tester.eval(source);
        long id = tester.getContextId();
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":6,\"scriptId\":\"0\",\"endColumn\":9,\"startColumn\":0,\"startLine\":0,\"length\":" + codeLength + ",\"executionContextId\":" + id + ",\"url\":\"" + sourceURI + "\",\"hash\":\"e48ee27adbe8b3cdf3183b8afc70c18bff3e8c87\"}}\n"));
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.breakpointResolved\",\"params\":{\"breakpointId\":\"1\",\"location\":{\"scriptId\":\"0\",\"columnNumber\":23,\"lineNumber\":0}}}\n"));
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.breakpointResolved\",\"params\":{\"breakpointId\":\"2\",\"location\":{\"scriptId\":\"0\",\"columnNumber\":18,\"lineNumber\":2}}}\n"));
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[\"2\"]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"af21\"," +
                                                 "\"scopeChain\":[{\"name\":\"af21\",\"type\":\"local\",\"object\":{\"description\":\"af21\",\"type\":\"object\",\"objectId\":\"1\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"2\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"null\",\"type\":\"object\",\"objectId\":\"3\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"0\",\"columnNumber\":12,\"lineNumber\":2}," +
                                                 "\"location\":{\"scriptId\":\"0\",\"columnNumber\":18,\"lineNumber\":2}," +
                                                 "\"url\":\"" + sourceURI + "\"}," +
                                                "{\"callFrameId\":\"1\",\"functionName\":\"af22\"," +
                                                 "\"scopeChain\":[{\"name\":\"af22\",\"type\":\"local\",\"object\":{\"description\":\"af22\",\"type\":\"object\",\"objectId\":\"4\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"5\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"null\",\"type\":\"object\",\"objectId\":\"6\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"0\",\"columnNumber\":12,\"lineNumber\":3}," +
                                                 "\"location\":{\"scriptId\":\"0\",\"columnNumber\":18,\"lineNumber\":3}," +
                                                 "\"url\":\"" + sourceURI + "\"}]," +
                                "\"asyncStackTrace\":{\"callFrames\":[{\"scriptId\":\"0\",\"functionName\":\"f1\",\"columnNumber\":16,\"lineNumber\":4,\"url\":\"TestFile\"}]}}}\n"));
        tester.sendMessage("{\"id\":10,\"method\":\"Debugger.setAsyncCallStackDepth\",\"params\":{\"maxDepth\":" + Integer.MAX_VALUE + "}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":10}\n"));
        tester.sendMessage("{\"id\":11,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":11}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[\"1\"]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"af11\"," +
                                                 "\"scopeChain\":[{\"name\":\"af11\",\"type\":\"local\",\"object\":{\"description\":\"af11\",\"type\":\"object\",\"objectId\":\"7\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"8\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"null\",\"type\":\"object\",\"objectId\":\"9\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"0\",\"columnNumber\":17,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"0\",\"columnNumber\":23,\"lineNumber\":0}," +
                                                 "\"url\":\"" + sourceURI + "\"}," +
                                                "{\"callFrameId\":\"1\",\"functionName\":\"af12\"," +
                                                 "\"scopeChain\":[{\"name\":\"af12\",\"type\":\"local\",\"object\":{\"description\":\"af12\",\"type\":\"object\",\"objectId\":\"10\"}}," +
                                                                 "{\"name\":\"global\",\"type\":\"global\",\"object\":{\"description\":\"global\",\"type\":\"object\",\"objectId\":\"11\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"null\",\"type\":\"object\",\"objectId\":\"12\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"0\",\"columnNumber\":12,\"lineNumber\":1}," +
                                                 "\"location\":{\"scriptId\":\"0\",\"columnNumber\":18,\"lineNumber\":1}," +
                                                 "\"url\":\"" + sourceURI + "\"}]," +
                                "\"asyncStackTrace\":{\"parent\":{\"callFrames\":[{\"scriptId\":\"0\",\"functionName\":\"f1\",\"columnNumber\":16,\"lineNumber\":4,\"url\":\"TestFile\"}]}," +
                                                     "\"callFrames\":[{\"scriptId\":\"0\",\"functionName\":\"af21\",\"columnNumber\":29,\"lineNumber\":2,\"url\":\"TestFile\"}," +
                                                                     "{\"scriptId\":\"0\",\"functionName\":\"af22\",\"columnNumber\":18,\"lineNumber\":3,\"url\":\"TestFile\"}]}}}\n"));

        tester.sendMessage("{\"id\":20,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":20}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        Source source2 = Source.newBuilder(InstrumentationTestLanguage.ID, "JOIN()", "Join").build();
        tester.eval(source2);
        tester.finish();
    }

    // @formatter:on
    // CheckStyle: resume line length check
}
