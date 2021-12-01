/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.debug.test.TestReceiverLanguage;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import org.graalvm.polyglot.Source;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Test of receiver representation, using {@link TestReceiverLanguage}.
 */
@SuppressWarnings("static-method")
public final class InspectorReceiverTest {

    // @formatter:off   The default formatting makes unnecessarily big indents and illogical line breaks
    // CheckStyle: stop line length check
    /**
     * Test one scope with "this" receiver.
     */
    @Test
    public void testThisReceiver1() throws Exception {
        ProxyLanguage.setDelegate(new TestReceiverLanguage());
        Source source = Source.create(ProxyLanguage.ID, "this\n" +
                        "a 1 b 2 this 3");
        String sourceURI = InspectorTester.getStringURI(source.getURI());
        InspectorTester tester = InspectorTester.start(true);
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
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":1,\"scriptId\":\"0\",\"endColumn\":14,\"startColumn\":0,\"startLine\":0,\"length\":" + source.getLength() + "," +
                                "\"executionContextId\":" + id + ",\"url\":\"" + sourceURI + "\",\"hash\":\"f64d15f3ffda6a1affffffffffffffffffffffff\"}}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"\"," +
                                                 "\"scopeChain\":[{\"name\":\"0\",\"type\":\"block\",\"object\":{\"description\":\"0\",\"type\":\"object\",\"objectId\":\"1\"}}]," +
                                                 "\"this\":{\"description\":\"3\",\"type\":\"object\",\"value\":\"3\"}," +
                                                 "\"location\":{\"scriptId\":\"0\",\"columnNumber\":0,\"lineNumber\":0}," +
                                                 "\"url\":\"" + sourceURI + "\"}]}}\n"));
        // Scope's variables do not contain "this":
        tester.sendMessage("{\"id\":4,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"1\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":[{\"isOwn\":true,\"enumerable\":true,\"name\":\"a\",\"value\":{\"description\":\"1\",\"type\":\"object\",\"value\":\"1\"},\"configurable\":true,\"writable\":false}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"b\",\"value\":{\"description\":\"2\",\"type\":\"object\",\"value\":\"2\"},\"configurable\":true,\"writable\":false}],\"internalProperties\":[]},\"id\":4}\n"));

        tester.sendMessage("{\"id\":5,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":5}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        // Reset the delegate so that we can GC the tested Engine
        ProxyLanguage.setDelegate(new ProxyLanguage());
        tester.finish();
    }

    /**
     * Test multiple scopes, just one with "this" receiver.
     */
    @Test
    public void testThisReceiver2() throws Exception {
        ProxyLanguage.setDelegate(new TestReceiverLanguage());
        Source source = Source.create(ProxyLanguage.ID, "this\n" +
                        "a 1 b 2\n" +
                        "c 4 d 5 this 3\n" +
                        "a 6 b 7");
        String sourceURI = InspectorTester.getStringURI(source.getURI());
        InspectorTester tester = InspectorTester.start(true);
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
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":3,\"scriptId\":\"0\",\"endColumn\":7,\"startColumn\":0,\"startLine\":0,\"length\":" + source.getLength() + "," +
                                "\"executionContextId\":" + id + ",\"url\":\"" + sourceURI + "\",\"hash\":\"f64d15f3ef714d0af24593dbffffffffffffffff\"}}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"\"," +
                                                 "\"scopeChain\":[{\"name\":\"0\",\"type\":\"block\",\"object\":{\"description\":\"0\",\"type\":\"object\",\"objectId\":\"1\"}}," +
                                                                 "{\"name\":\"1\",\"type\":\"block\",\"object\":{\"description\":\"1\",\"type\":\"object\",\"objectId\":\"2\"}}," +
                                                                 "{\"name\":\"2\",\"type\":\"block\",\"object\":{\"description\":\"2\",\"type\":\"object\",\"objectId\":\"3\"}}]," +
                                                 "\"this\":{\"description\":\"3\",\"type\":\"object\",\"value\":\"3\"}," +
                                                 "\"location\":{\"scriptId\":\"0\",\"columnNumber\":0,\"lineNumber\":0}," +
                                                 "\"url\":\"" + sourceURI + "\"}]}}\n"));
        // Scope's variables do not contain "this":
        tester.sendMessage("{\"id\":4,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"1\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":[{\"isOwn\":true,\"enumerable\":true,\"name\":\"a\",\"value\":{\"description\":\"1\",\"type\":\"object\",\"value\":\"1\"},\"configurable\":true,\"writable\":false}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"b\",\"value\":{\"description\":\"2\",\"type\":\"object\",\"value\":\"2\"},\"configurable\":true,\"writable\":false}],\"internalProperties\":[]},\"id\":4}\n"));
        tester.sendMessage("{\"id\":5,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"2\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":[{\"isOwn\":true,\"enumerable\":true,\"name\":\"c\",\"value\":{\"description\":\"4\",\"type\":\"object\",\"value\":\"4\"},\"configurable\":true,\"writable\":false}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"d\",\"value\":{\"description\":\"5\",\"type\":\"object\",\"value\":\"5\"},\"configurable\":true,\"writable\":false}],\"internalProperties\":[]},\"id\":5}\n"));

        tester.sendMessage("{\"id\":10,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":10}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        // Reset the delegate so that we can GC the tested Engine
        ProxyLanguage.setDelegate(new ProxyLanguage());
        tester.finish();
    }

    /**
     * Test multiple scopes, with multiple "this" receivers.
     */
    @Test
    public void testThisReceiver3() throws Exception {
        checkMultipleReceivers("this", "f64d15f3ef714d0af047f02ff7dd1a32ffffffff");
    }

    private static void checkMultipleReceivers(String receiver, String sourceHash) throws Exception {
        ProxyLanguage.setDelegate(new TestReceiverLanguage());
        Source source = Source.create(ProxyLanguage.ID, receiver + "\n" +
                        "a 1 b 2 " + receiver + " 3\n" +
                        "c 4 d 5\n" +
                        "a 6 b 7 " + receiver + " 8");
        String sourceURI = InspectorTester.getStringURI(source.getURI());
        InspectorTester tester = InspectorTester.start(true);
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        assertEquals("{\"result\":{},\"id\":1}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":2,\"method\":\"Debugger.enable\"}");
        assertEquals("{\"result\":{},\"id\":2}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":3,\"method\":\"Runtime.runIfWaitingForDebugger\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":3}\n" +
                        "{\"method\":\"Runtime.executionContextCreated\",\"params\":{\"context\":{\"origin\":\"\",\"name\":\"test\",\"id\":1}}}\n"));
        tester.eval(source);
        int endColumn = source.getLength() - source.getCharacters().toString().lastIndexOf('\n') - 1;
        long id = tester.getContextId();
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":3,\"scriptId\":\"0\",\"endColumn\":" + endColumn + ",\"startColumn\":0,\"startLine\":0,\"length\":" + source.getLength() + "," +
                                "\"executionContextId\":" + id + ",\"url\":\"" + sourceURI + "\",\"hash\":\"" + sourceHash + "\"}}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"\"," +
                                                 "\"scopeChain\":[{\"name\":\"0\",\"type\":\"block\",\"object\":{\"description\":\"0\",\"type\":\"object\",\"objectId\":\"1\"}}," +
                                                                 "{\"name\":\"1\",\"type\":\"block\",\"object\":{\"description\":\"1\",\"type\":\"object\",\"objectId\":\"2\"}}," +
                                                                 "{\"name\":\"2\",\"type\":\"block\",\"object\":{\"description\":\"2\",\"type\":\"object\",\"objectId\":\"3\"}}]," +
                                                 "\"this\":null," +
                                                 "\"location\":{\"scriptId\":\"0\",\"columnNumber\":0,\"lineNumber\":0}," +
                                                 "\"url\":\"" + sourceURI + "\"}]}}\n"));
        // Scope's variables contain "this":
        tester.sendMessage("{\"id\":4,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"1\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":[{\"isOwn\":true,\"enumerable\":true,\"name\":\"" + receiver + "\",\"value\":{\"description\":\"3\",\"type\":\"object\",\"value\":\"3\"},\"configurable\":true,\"writable\":false}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"a\",\"value\":{\"description\":\"1\",\"type\":\"object\",\"value\":\"1\"},\"configurable\":true,\"writable\":false}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"b\",\"value\":{\"description\":\"2\",\"type\":\"object\",\"value\":\"2\"},\"configurable\":true,\"writable\":false}],\"internalProperties\":[]},\"id\":4}\n"));
        tester.sendMessage("{\"id\":5,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"2\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":[{\"isOwn\":true,\"enumerable\":true,\"name\":\"c\",\"value\":{\"description\":\"4\",\"type\":\"object\",\"value\":\"4\"},\"configurable\":true,\"writable\":false}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"d\",\"value\":{\"description\":\"5\",\"type\":\"object\",\"value\":\"5\"},\"configurable\":true,\"writable\":false}],\"internalProperties\":[]},\"id\":5}\n"));
        tester.sendMessage("{\"id\":6,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"3\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":[{\"isOwn\":true,\"enumerable\":true,\"name\":\"" + receiver + "\",\"value\":{\"description\":\"8\",\"type\":\"object\",\"value\":\"8\"},\"configurable\":true,\"writable\":false}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"a\",\"value\":{\"description\":\"6\",\"type\":\"object\",\"value\":\"6\"},\"configurable\":true,\"writable\":false}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"b\",\"value\":{\"description\":\"7\",\"type\":\"object\",\"value\":\"7\"},\"configurable\":true,\"writable\":false}],\"internalProperties\":[]},\"id\":6}\n"));

        tester.sendMessage("{\"id\":10,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":10}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        // Reset the delegate so that we can GC the tested Engine
        ProxyLanguage.setDelegate(new ProxyLanguage());
        tester.finish();
    }

    /**
     * Test one scope with "other" receiver.
     */
    @Test
    public void testOtherReceiver1() throws Exception {
        ProxyLanguage.setDelegate(new TestReceiverLanguage());
        Source source = Source.create(ProxyLanguage.ID, "other\n" +
                        "a 1 b 2 other 3");
        String sourceURI = InspectorTester.getStringURI(source.getURI());
        InspectorTester tester = InspectorTester.start(true);
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
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":1,\"scriptId\":\"0\",\"endColumn\":15,\"startColumn\":0,\"startLine\":0,\"length\":" + source.getLength() + "," +
                                "\"executionContextId\":" + id + ",\"url\":\"" + sourceURI + "\",\"hash\":\"e2e9dccce61f3a14f6c3f3abffffffffffffffff\"}}\n" +
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"\"," +
                                                 "\"scopeChain\":[{\"name\":\"0\",\"type\":\"block\",\"object\":{\"description\":\"0\",\"type\":\"object\",\"objectId\":\"1\"}}]," +
                                                 "\"this\":null," +
                                                 "\"location\":{\"scriptId\":\"0\",\"columnNumber\":0,\"lineNumber\":0}," +
                                                 "\"url\":\"" + sourceURI + "\"}]}}\n"));
        // Scope's variables contain the "other" receiver:
        tester.sendMessage("{\"id\":4,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"1\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":[{\"isOwn\":true,\"enumerable\":true,\"name\":\"other\",\"value\":{\"description\":\"3\",\"type\":\"object\",\"value\":\"3\"},\"configurable\":true,\"writable\":false}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"a\",\"value\":{\"description\":\"1\",\"type\":\"object\",\"value\":\"1\"},\"configurable\":true,\"writable\":false}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"b\",\"value\":{\"description\":\"2\",\"type\":\"object\",\"value\":\"2\"},\"configurable\":true,\"writable\":false}],\"internalProperties\":[]},\"id\":4}\n"));

        tester.sendMessage("{\"id\":5,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":5}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        // Reset the delegate so that we can GC the tested Engine
        ProxyLanguage.setDelegate(new ProxyLanguage());
        tester.finish();
    }

    /**
     * Test multiple scopes, with multiple "other" receivers.
     */
    @Test
    public void testOtherReceiver3() throws Exception {
        checkMultipleReceivers("other", "e2e9dccce61f3a14f4fc4ab1f8cb26daffffffff");
    }

}
