/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertTrue;

import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.polyglot.Source;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;

import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.debug.test.TestDebugBuggyLanguage;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.polyglot.ProxyInteropObject;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import com.oracle.truffle.tck.DebuggerTester;
import com.oracle.truffle.tools.chromeinspector.types.Script;

/**
 * Test of exception handling.
 */
public class BuggyLanguageInspectDebugTest {

    private InspectorTester tester;
    private final ByteArrayOutputStream errorStream = new ByteArrayOutputStream();

    @After
    public void tearDown() {
        assertTrue(errorStream.size() > 0); // Errors were printed
        tester = null;
        errorStream.reset();
    }

    @Test
    public void testBuggyToString() throws Exception {
        testBuggyCalls(langRef(new TestDebugBuggyLanguage() {
            @Override
            protected String toString(ProxyLanguage.LanguageContext c, Object value) {
                throwBug(value);
                return Objects.toString(value);
            }
        }), new LanguageCallsVerifier());
    }

    @Test
    public void testBuggyFindMetaObject() throws Exception {
        testBuggyCalls(langRef(new TestDebugBuggyLanguage() {
            @Override
            protected Object findMetaObject(ProxyLanguage.LanguageContext context, Object value) {
                throwBug(value);
                return Objects.toString(value);
            }
        }), new LanguageCallsVerifier());
    }

    @Test
    public void testBuggyMetaToString() throws Exception {
        class MetaObj extends ProxyInteropObject {

            final int id;

            MetaObj(int id) {
                this.id = id;
            }
        }
        testBuggyCalls(langRef(new TestDebugBuggyLanguage() {

            @Override
            protected boolean isObjectOfLanguage(Object object) {
                return object instanceof MetaObj;
            }

            @Override
            protected Object findMetaObject(ProxyLanguage.LanguageContext context, Object value) {
                if (value instanceof Integer) {
                    return new MetaObj((Integer) value);
                }
                return Objects.toString(value);
            }

            @Override
            protected String toString(ProxyLanguage.LanguageContext c, Object value) {
                if (value instanceof MetaObj) {
                    int id = ((MetaObj) value).id;
                    throwBug(id);
                }
                return Objects.toString(value);
            }
        }), new LanguageCallsVerifier());
    }

    @Test
    public void testBuggyScope() throws Exception {
        testBuggyCalls(langRef(new TestDebugBuggyLanguage() {
            @Override
            protected Iterable<Scope> findLocalScopes(ProxyLanguage.LanguageContext context, Node node, Frame frame) {
                String text = node.getSourceSection().getCharacters().toString();
                throwBug(Integer.parseInt(text));
                return super.findLocalScopes(context, node, frame);
            }
        }), "", false, new BugVerifier() {
            @Override
            public void verifyMessages(InspectorTester t, int errNum) throws InterruptedException {
                // No scope, verified by haveScope = false
            }
        });
    }

    @Test
    public void testBuggyRead() throws Exception {
        testBuggyCalls(langRef(new TestDebugBuggyLanguage()),
                        "READ", true, new ReadErrorVerifier("READ"));
    }

    @Test
    @Ignore
    public void testBuggyKeyInfo() throws Exception {
        testBuggyCalls(langRef(new TestDebugBuggyLanguage()),
                        "KEY_INFO", true, new ReadErrorVerifier("KEY_INFO"));
    }

    @Test
    public void testBuggyReadVar() throws Exception {
        testBuggyCalls(langRef(new TestDebugBuggyLanguage() {
            @Override
            protected Iterable<Scope> findLocalScopes(ProxyLanguage.LanguageContext context, Node node, Frame frame) {
                int errNum = Integer.parseInt(node.getSourceSection().getCharacters().toString());
                return buggyProxyScopes(super.findLocalScopes(context, node, frame), () -> throwBug(errNum), "READ");
            }
        }), new ReadVarErrorVerifier());
    }

    @Test
    public void testBuggyWriteVar() throws Exception {
        testBuggyCalls(langRef(new TestDebugBuggyLanguage() {
            @Override
            protected Iterable<Scope> findLocalScopes(ProxyLanguage.LanguageContext context, Node node, Frame frame) {
                int errNum = Integer.parseInt(node.getSourceSection().getCharacters().toString());
                return buggyProxyScopes(super.findLocalScopes(context, node, frame), () -> throwBug(errNum), "WRITE");
            }
        }), new WriteVarErrorVerifier());
    }

    @Test
    public void testBuggySourceLocation() throws Exception {
        testBuggyCalls(langRef(new TestDebugBuggyLanguage() {
            private int numCalls = 0;

            @Override
            protected SourceSection findSourceLocation(ProxyLanguage.LanguageContext context, Object value) {
                // Functions provide the location as description,
                // thus we need to prevent from firing an error from the description.
                // We ignore every first and second request, which comes from description
                // and an assert.
                numCalls++;
                if ((numCalls % 3) != 0) {
                    return null;
                }
                if (value instanceof TruffleObject) {
                    try {
                        int errNum = (int) InteropLibrary.getFactory().getUncached().readMember(value, "A");
                        throwBug(errNum);
                    } catch (UnknownIdentifierException | UnsupportedMessageException ex) {
                        throw new AssertionError(ex.getLocalizedMessage(), ex);
                    }
                }
                return null;
            }
        }), new SourceLocationVerifier());
    }

    private static AtomicReference<ProxyLanguage> langRef(ProxyLanguage language) {
        return new AtomicReference<>(language);
    }

    private void testBuggyCalls(AtomicReference<ProxyLanguage> language, BugVerifier bugVerifier) throws Exception {
        testBuggyCalls(language, "", true, bugVerifier);
    }

    // @formatter:off   The default formatting makes unnecessarily big indents and illogical line breaks
    // CheckStyle: stop line length check
    private void testBuggyCalls(AtomicReference<ProxyLanguage> language, String prefix, boolean haveScope, BugVerifier bugVerifier) throws Exception {
        tester = InspectorTester.start(true);
        tester.setErr(errorStream);
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        tester.sendMessage("{\"id\":2,\"method\":\"Debugger.enable\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":1}\n" +
                        "{\"result\":{},\"id\":2}\n"));
        tester.sendMessage("{\"id\":3,\"method\":\"Runtime.runIfWaitingForDebugger\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":3}\n" +
                        "{\"method\":\"Runtime.executionContextCreated\",\"params\":{\"context\":{\"origin\":\"\",\"name\":\"test\",\"id\":1}}}\n"));
        ProxyLanguage.setDelegate(language.getAndSet(null)); // Do not keep language reference
        Source source = Source.newBuilder(ProxyLanguage.ID, prefix + "1", "BuggyCall1.bug").build();
        String sourceURI = InspectorTester.getStringURI(source.getURI());
        String hash = new Script(0, null, DebuggerTester.getSourceImpl(source)).getHash();
        tester.eval(source);
        long id = tester.getContextId();
        int endLine = source.getLineCount() - 1;
        int endColumn = source.getLineLength(1);
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":" + endLine + ",\"scriptId\":\"0\",\"endColumn\":" + endColumn + ",\"startColumn\":0,\"startLine\":0,\"length\":" +
                                        source.getLength() + ",\"executionContextId\":" + id + ",\"url\":\"" + sourceURI + "\",\"hash\":\"" + hash + "\"}}\n"));

        skipConsoleMessages(tester);
        assertPaused(prefix + "1", haveScope, 1, sourceURI, 0);
        bugVerifier.verifyMessages(tester, 1);
        tester.sendMessage("{\"id\":100,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":100}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        tester.sendMessage("{\"id\":8,\"method\":\"Debugger.pause\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":8}\n"));

        source = Source.newBuilder(ProxyLanguage.ID, prefix + "2", "BuggyCall2.bug").build();
        sourceURI = InspectorTester.getStringURI(source.getURI());
        hash = new Script(0, null, DebuggerTester.getSourceImpl(source)).getHash();
        tester.eval(source);
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":" + endLine + ",\"scriptId\":\"1\",\"endColumn\":" + endColumn + ",\"startColumn\":0,\"startLine\":0,\"length\":" +
                                        source.getLength() + ",\"executionContextId\":" + id + ",\"url\":\"" + sourceURI + "\",\"hash\":\"" + hash + "\"}}\n"));
        skipConsoleMessages(tester);
        assertPaused(prefix + "2", haveScope, haveScope ? 4 : 2, sourceURI, 1);
        bugVerifier.verifyMessages(tester, 2);
        tester.sendMessage("{\"id\":100,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":100}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        tester.sendMessage("{\"id\":11,\"method\":\"Debugger.pause\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":11}\n"));

        source = Source.newBuilder(ProxyLanguage.ID, prefix + "3", "BuggyCall3.bug").build();
        sourceURI = InspectorTester.getStringURI(source.getURI());
        hash = new Script(0, null, DebuggerTester.getSourceImpl(source)).getHash();
        tester.eval(source);
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":" + endLine + ",\"scriptId\":\"2\",\"endColumn\":" + endColumn + ",\"startColumn\":0,\"startLine\":0,\"length\":" +
                                        source.getLength() + ",\"executionContextId\":" + id + ",\"url\":\"" + sourceURI + "\",\"hash\":\"" + hash + "\"}}\n"));
        skipConsoleMessages(tester);
        assertPaused(prefix + "3", haveScope, haveScope ? 7 : 3, sourceURI, 2);
        bugVerifier.verifyMessages(tester, 3);
        tester.sendMessage("{\"id\":100,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":100}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));

        // Reset the delegate so that we can GC the tested Engine
        ProxyLanguage.setDelegate(new ProxyLanguage());
        tester.finish();
        assertTrue(errorStream.size() > 0);
    }

    private static void skipConsoleMessages(InspectorTester tester) throws InterruptedException {
        String consoleMessage;
        do {
            consoleMessage = tester.receiveMessages(true, "{\"method\":\"Runtime.consoleAPICalled\"", "\"type\":\"error\",\"timestamp\":", "}}\n");
        } while (consoleMessage != null);
    }

    private interface BugVerifier {

        void verifyMessages(InspectorTester tester, int errNum) throws InterruptedException;
    }

    private static class LanguageCallsVerifier implements BugVerifier {

        @Override
        public void verifyMessages(InspectorTester tester, int errNum) throws InterruptedException {
            int objectId = 3 * errNum - 2;
            String description = (errNum == 2) ? "A TruffleException" : Integer.toString(errNum);
            String errObject = "ErrorObject " + errNum;
            tester.sendMessage("{\"id\":7,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"" + objectId + "\"}}");
            skipConsoleMessages(tester);
            assertTrue(tester.compareReceivedMessages(
                            "{\"result\":{\"result\":[{\"isOwn\":true,\"enumerable\":true,\"name\":\"o\",\"value\":{\"description\":\"" + errObject + "\",\"className\":\"" + errObject + "\",\"type\":\"function\",\"objectId\":\"" + (3 * errNum) + "\"},\"configurable\":true,\"writable\":true}]," +
                                         "\"internalProperties\":[]," +
                                         "\"exceptionDetails\":{\"exception\":{\"description\":\"" + description + "\",\"type\":\"string\",\"value\":\"" + description + "\"}," +
                                                               "\"exceptionId\":" + errNum + ",\"executionContextId\":1,\"text\":\"Uncaught\"," +
                                                               "\"stackTrace\":{\"callFrames\":[]}}},\"id\":7}\n"));
        }
    }

    private static class ReadErrorVerifier implements BugVerifier {

        private final String errMessage;

        ReadErrorVerifier(String errMessage) {
            this.errMessage = errMessage;
        }

        @Override
        public void verifyMessages(InspectorTester tester, int errNum) throws InterruptedException {
            int objectId = 3 * errNum - 2;
            String description = (errNum == 2) ? "A TruffleException" : Integer.toString(errNum);
            String errObject = "ErrorObject " + errNum + " " + errMessage;
            tester.sendMessage("{\"id\":7,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"" + objectId + "\"}}");
            assertTrue(tester.compareReceivedMessages(
                            "{\"result\":{\"result\":[{\"isOwn\":true,\"enumerable\":true,\"name\":\"a\",\"value\":{\"description\":\"" + errNum + "\",\"type\":\"number\",\"value\":" + errNum + "},\"configurable\":true,\"writable\":true}," +
                                                     "{\"isOwn\":true,\"enumerable\":true,\"name\":\"o\",\"value\":{\"description\":\"" + errObject + "\",\"className\":\"" + errObject + "\",\"type\":\"function\",\"objectId\":\"" + (3 * errNum) + "\"},\"configurable\":true,\"writable\":true}]," +
                                         "\"internalProperties\":[]},\"id\":7}\n"));
            tester.sendMessage("{\"id\":8,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"" + (3 * errNum) + "\"}}");
            skipConsoleMessages(tester);
            assertTrue(tester.compareReceivedMessages(
                            "{\"result\":{\"result\":[{\"isOwn\":true,\"enumerable\":true,\"name\":\"B\",\"value\":{\"description\":\"42\",\"type\":\"number\",\"value\":42},\"configurable\":true,\"writable\":true}]," +
                                         "\"internalProperties\":[]," +
                                         "\"exceptionDetails\":{\"exception\":{\"description\":\"" + description + "\",\"type\":\"string\",\"value\":\"" + description + "\"}," +
                                                               "\"exceptionId\":" + errNum + ",\"executionContextId\":1,\"text\":\"Uncaught\"," +
                                                               "\"stackTrace\":{\"callFrames\":[]}}},\"id\":8}\n"));
        }
    }

    private static class ReadVarErrorVerifier implements BugVerifier {

        @Override
        public void verifyMessages(InspectorTester tester, int errNum) throws InterruptedException {
            int objectId = 3 * errNum - 2;
            String description = (errNum == 2) ? "A TruffleException" : Integer.toString(errNum);
            String errObject = "ErrorObject " + errNum;
            tester.sendMessage("{\"id\":7,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"" + objectId + "\"}}");
            skipConsoleMessages(tester);
            assertTrue(tester.compareReceivedMessages(
                            "{\"result\":{\"result\":[{\"isOwn\":true,\"enumerable\":true,\"name\":\"o\",\"value\":{\"description\":\"" + errObject + "\",\"className\":\"" + errObject + "\",\"type\":\"function\",\"objectId\":\"" + (3 * errNum) + "\"},\"configurable\":true,\"writable\":true}]," +
                                         "\"internalProperties\":[]," +
                                         "\"exceptionDetails\":{\"exception\":{\"description\":\"" + description + "\",\"type\":\"string\",\"value\":\"" + description + "\"}," +
                                                               "\"exceptionId\":" + errNum + ",\"executionContextId\":1,\"text\":\"Uncaught\"," +
                                                               "\"stackTrace\":{\"callFrames\":[]}}},\"id\":7}\n"));
        }
    }

    private static class WriteVarErrorVerifier implements BugVerifier {

        @Override
        public void verifyMessages(InspectorTester tester, int errNum) throws InterruptedException {
            int objectId = 3 * errNum - 2;
            String errObject = "ErrorObject " + errNum;
            tester.sendMessage("{\"id\":7,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"" + objectId + "\"}}");
            assertTrue(tester.compareReceivedMessages(
                            "{\"result\":{\"result\":[{\"isOwn\":true,\"enumerable\":true,\"name\":\"a\",\"value\":{\"description\":\"" + errNum + "\",\"type\":\"number\",\"value\":" + errNum + "},\"configurable\":true,\"writable\":true}," +
                                                     "{\"isOwn\":true,\"enumerable\":true,\"name\":\"o\",\"value\":{\"description\":\"" + errObject + "\",\"className\":\"" + errObject + "\",\"type\":\"function\",\"objectId\":\"" + (3 * errNum) + "\"},\"configurable\":true,\"writable\":true}]," +
                                         "\"internalProperties\":[]},\"id\":7}\n"));
            tester.sendMessage("{\"id\":8,\"method\":\"Debugger.setVariableValue\",\"params\":{\"scopeNumber\":0,\"variableName\":\"a\",\"newValue\":{\"value\":1000},\"callFrameId\":\"0\"}}");
            // The protocol does not allow to provide an exception. It's consumed
            skipConsoleMessages(tester);
            assertTrue(tester.compareReceivedMessages(
                            "{\"result\":{},\"id\":8}\n"));
        }
    }

    private static class SourceLocationVerifier implements BugVerifier {

        @Override
        public void verifyMessages(InspectorTester tester, int errNum) throws InterruptedException {
            int objectId = 3 * errNum - 2;
            String description = (errNum == 2) ? "A TruffleException" : Integer.toString(errNum);
            String errObject = "ErrorObject " + errNum;
            tester.sendMessage("{\"id\":7,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"" + objectId + "\"}}");
            assertTrue(tester.compareReceivedMessages(
                            "{\"result\":{\"result\":[{\"isOwn\":true,\"enumerable\":true,\"name\":\"a\",\"value\":{\"description\":\"" + errNum + "\",\"type\":\"number\",\"value\":" + errNum + "},\"configurable\":true,\"writable\":true}," +
                                                     "{\"isOwn\":true,\"enumerable\":true,\"name\":\"o\",\"value\":{\"description\":\"" + errObject + "\",\"className\":\"" + errObject + "\",\"type\":\"function\",\"objectId\":\"" + (3 * errNum) + "\"},\"configurable\":true,\"writable\":true}]," +
                                         "\"internalProperties\":[]},\"id\":7}\n"));
            tester.sendMessage("{\"id\":8,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"" + (3 * errNum) + "\"}}");
            skipConsoleMessages(tester);
            assertTrue(tester.compareReceivedMessages(
                            "{\"result\":{\"result\":[{\"isOwn\":true,\"enumerable\":true,\"name\":\"A\",\"value\":{\"description\":\"" + errNum + "\",\"type\":\"number\",\"value\":" + errNum + "},\"configurable\":true,\"writable\":true}," +
                                                     "{\"isOwn\":true,\"enumerable\":true,\"name\":\"B\",\"value\":{\"description\":\"42\",\"type\":\"number\",\"value\":42},\"configurable\":true,\"writable\":true}]," +
                                         "\"internalProperties\":[]," +
                                         "\"exceptionDetails\":{\"exception\":{\"description\":\"" + description + "\",\"type\":\"string\",\"value\":\"" + description + "\"}," +
                                                               "\"exceptionId\":" + errNum + ",\"executionContextId\":1,\"text\":\"Uncaught\"," +
                                                               "\"stackTrace\":{\"callFrames\":[]}}},\"id\":8}\n"));
        }
    }

    private void assertPaused(String functionName, boolean haveScope, int objectId, String sourceURI, int scriptId) throws InterruptedException {
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"" + functionName + "\"," +
                                                 (haveScope ?
                                                  "\"scopeChain\":[{\"name\":\"" + functionName + "\",\"type\":\"local\",\"object\":{\"description\":\"" + functionName + "\",\"type\":\"object\",\"objectId\":\"" + objectId + "\"}}]," :
                                                  "\"scopeChain\":[],") +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"null\",\"type\":\"object\",\"objectId\":\"" + (haveScope ? objectId + 1 : objectId) + "\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"" + scriptId + "\",\"columnNumber\":0,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"" + scriptId + "\",\"columnNumber\":0,\"lineNumber\":0}," +
                                                 "\"url\":\"" + sourceURI + "\"}]}}\n"));
    }
    // @formatter:on
    // CheckStyle: resume line length check

    Iterable<Scope> buggyProxyScopes(Iterable<Scope> scopes, Runnable throwErr, String errMessage) {
        return new Iterable<Scope>() {
            @Override
            public Iterator<Scope> iterator() {
                Iterator<Scope> iterator = scopes.iterator();
                return new Iterator<Scope>() {
                    @Override
                    public boolean hasNext() {
                        return iterator.hasNext();
                    }

                    @Override
                    public Scope next() {
                        return buggyProxyScope(iterator.next(), throwErr, errMessage);
                    }
                };
            }
        };
    }

    Scope buggyProxyScope(Scope scope, Runnable throwErr, String errMessage) {
        Scope.Builder builder = Scope.newBuilder(scope.getName(), new BuggyProxyVars(scope.getVariables(), throwErr, errMessage));
        builder.arguments(new BuggyProxyVars(scope.getArguments(), throwErr, errMessage));
        builder.node(scope.getNode());
        return builder.build();
    }

    private class BuggyProxyVars extends ProxyInteropObject.InteropWrapper {

        private final Runnable throwErr;
        private final String errMessage;

        BuggyProxyVars(Object vars, Runnable throwErr, String errMessage) {
            super(vars);
            this.throwErr = throwErr;
            this.errMessage = errMessage;
        }

        @Override
        protected Object readMember(String member) throws UnsupportedMessageException, UnknownIdentifierException {
            if ("READ".equals(errMessage) && "a".equals(member)) {
                throwErr.run();
            }
            return super.readMember(member);
        }

        @Override
        protected void writeMember(String member, Object value) throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException {
            if ("WRITE".equals(errMessage)) {
                throwErr.run();
            }
            super.writeMember(member, value);
        }
    }

}
