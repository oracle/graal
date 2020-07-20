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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.polyglot.Source;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.polyglot.ProxyInteropObject;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import com.oracle.truffle.tck.DebuggerTester;
import com.oracle.truffle.tools.chromeinspector.types.Script;

/**
 * Test that properties that have {@link InteropLibrary#hasMemberReadSideEffects(Object, String)}
 * flag are not read eagerly, but on a request only.
 */
public class LazyAccessInspectDebugTest {

    private static final String READ_READY = "readReady";
    private static final String READ_LAZY = "readLazy";

    private static final String INVOKE_GETTER1 = "\"functionDeclaration\":" +
                    "\"function invokeGetter(arrayStr){let result=this;const properties=JSON.parse(arrayStr);for(let i=0,n=properties.length;i<n;++i)\\nresult=result[properties[i]];return result;}\"";
    private static final String INVOKE_GETTER2 = "\"functionDeclaration\":" +
                    "\"function remoteFunction(propName) { return this[propName]; }\"";
    private static final String INVOKE_GETTER3 = "\"functionDeclaration\":" +
                    "\"function invokeGetter(arrayStr) {\\n      let result = this;\\n      const properties = JSON.parse(arrayStr);\\n      for (let i = 0, n = properties.length; i < n; ++i) {" +
                    "\\n        result = result[properties[i]];\\n      }\\n      return result;\\n    }\"";

    // @formatter:off   The default formatting makes unnecessarily big indents and illogical line breaks
    // CheckStyle: stop line length check
    @Test
    public void testReadWithSideEffects() throws Exception {
        final AtomicInteger readLazyCount = new AtomicInteger(0);
        InspectorTester tester = InspectorTester.start(true);
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        tester.sendMessage("{\"id\":2,\"method\":\"Debugger.enable\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":1}\n" +
                        "{\"result\":{},\"id\":2}\n"));
        tester.sendMessage("{\"id\":3,\"method\":\"Runtime.runIfWaitingForDebugger\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":3}\n" +
                        "{\"method\":\"Runtime.executionContextCreated\",\"params\":{\"context\":{\"origin\":\"\",\"name\":\"test\",\"id\":1}}}\n"));
        ProxyLanguage.setDelegate(new ReadWithSideEffectsLanguage(readLazyCount));
        Source source = Source.newBuilder(ProxyLanguage.ID, "1", "ReadWithSideEffects.test").build();
        String sourceURI = InspectorTester.getStringURI(source.getURI());
        String hash = new Script(0, null, DebuggerTester.getSourceImpl(source)).getHash();
        tester.eval(source);
        long id = tester.getContextId();
        int endLine = source.getLineCount() - 1;
        int endColumn = source.getLineLength(1);
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":" + endLine + ",\"scriptId\":\"0\",\"endColumn\":" + endColumn + ",\"startColumn\":0,\"startLine\":0,\"length\":" +
                                        source.getLength() + ",\"executionContextId\":" + id + ",\"url\":\"" + sourceURI + "\",\"hash\":\"" + hash + "\"}}\n"));
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"TestRootNode\"," +
                                                 "\"scopeChain\":[{\"name\":\"TestRootNode\",\"type\":\"local\",\"object\":{\"description\":\"TestRootNode\",\"type\":\"object\",\"objectId\":\"1\"}}," +
                                                                 "{\"name\":\"top\",\"type\":\"global\",\"object\":{\"description\":\"top\",\"type\":\"object\",\"objectId\":\"2\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"null\",\"type\":\"object\",\"objectId\":\"3\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"0\",\"columnNumber\":0,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"0\",\"columnNumber\":0,\"lineNumber\":0}," +
                                                 "\"url\":\"" + sourceURI + "\"" +
                                                 "}]}}\n"));
        // Ask for local variables
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"1\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":[{\"isOwn\":true,\"enumerable\":true," +
                                "\"name\":\"o\",\"value\":{\"description\":\"Object LazyReadObject\",\"className\":\"Object\",\"type\":\"object\",\"objectId\":\"4\"},\"configurable\":true,\"writable\":true}]," +
                                "\"internalProperties\":[]},\"id\":1}\n"));
        // Ask for properties of 'o':
        tester.sendMessage("{\"id\":2,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"4\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":[" +
                                "{\"isOwn\":true,\"enumerable\":true,\"name\":\"readReady\",\"value\":{\"description\":\"42\",\"type\":\"number\",\"value\":42},\"configurable\":true,\"writable\":false}," +
                                "{\"isOwn\":true,\"get\":{\"description\":\"\",\"className\":\"Function\",\"type\":\"function\",\"objectId\":\"6\"},\"enumerable\":true,\"name\":\"readLazy\",\"configurable\":true,\"writable\":false}]," +
                                "\"internalProperties\":[]},\"id\":2}\n"));
        // The lazy value was not read yet:
        assertEquals(0, readLazyCount.get());
        // Invoke the lazy read (getter):
        tester.sendMessage("{\"id\":10,\"method\":\"Runtime.callFunctionOn\",\"params\":" +
                        "{\"objectId\":\"4\"," +
                         INVOKE_GETTER1 + "," +
                         "\"arguments\":[{\"value\":\"[\\\"readLazy\\\"]\"}]," +
                         "\"silent\":true}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":{\"description\":\"43\",\"type\":\"number\",\"value\":43}},\"id\":10}\n"));
        // The lazy value was read now:
        assertEquals(1, readLazyCount.get());
        // Test an alternate getter function:
        tester.sendMessage("{\"id\":11,\"method\":\"Runtime.callFunctionOn\",\"params\":" +
                        "{\"objectId\":\"4\"," +
                         INVOKE_GETTER2 + "," +
                         "\"arguments\":[{\"value\":\"readLazy\"}]," +
                         "\"silent\":true}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":{\"description\":\"43\",\"type\":\"number\",\"value\":43}},\"id\":11}\n"));
        assertEquals(2, readLazyCount.get());

        // Test lazy scope property:
        tester.sendMessage("{\"id\":20,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"2\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":[{\"isOwn\":true,\"enumerable\":true,\"name\":\"readReady\",\"value\":{\"description\":\"42\",\"type\":\"number\",\"value\":42},\"configurable\":true,\"writable\":false}," +
                                                 "{\"isOwn\":true,\"get\":{\"description\":\"\",\"className\":\"Function\",\"type\":\"function\",\"objectId\":\"8\"},\"enumerable\":true,\"name\":\"readLazy\",\"configurable\":true,\"writable\":false}]," +
                                "\"internalProperties\":[]},\"id\":20}\n"));
        assertEquals(2, readLazyCount.get());
        // Invoke the lazy read (getter):
        tester.sendMessage("{\"id\":21,\"method\":\"Runtime.callFunctionOn\",\"params\":" +
                        "{\"objectId\":\"2\"," +
                         INVOKE_GETTER3 + "," +
                         "\"arguments\":[{\"value\":\"[\\\"readLazy\\\"]\"}]," +
                         "\"silent\":true}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":{\"description\":\"43\",\"type\":\"number\",\"value\":43}},\"id\":21}\n"));
        // The lazy value was read now:
        assertEquals(3, readLazyCount.get());

        tester.sendMessage("{\"id\":100,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":100}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));

        // Reset the delegate so that we can GC the tested Engine
        ProxyLanguage.setDelegate(new ProxyLanguage());
        tester.finish();
    }
    // @formatter:on
    // CheckStyle: resume line length check

    static class ReadWithSideEffectsLanguage extends ProxyLanguage {

        private final AtomicInteger readLazyCount;

        ReadWithSideEffectsLanguage(AtomicInteger readLazyCount) {
            this.readLazyCount = readLazyCount;
        }

        @Override
        protected final CallTarget parse(TruffleLanguage.ParsingRequest request) throws Exception {
            return Truffle.getRuntime().createCallTarget(new TestRootNode(languageInstance, request.getSource()));
        }

        @Override
        protected String toString(LanguageContext context, Object value) {
            if (value instanceof LazyReadObject) {
                return LazyReadObject.class.getSimpleName();
            }
            return super.toString(context, value);
        }

        @Override
        protected Object findMetaObject(LanguageContext context, Object value) {
            if (value instanceof TruffleObject) {
                return "Object";
            }
            return super.findMetaObject(context, value);
        }

        @Override
        protected Iterable<Scope> findTopScopes(LanguageContext context) {
            return Collections.singletonList(Scope.newBuilder("top", new LazyReadObject()).build());
        }

        final class TestRootNode extends RootNode {

            @Node.Child private TestStatementNode statement;
            private final SourceSection statementSection;

            TestRootNode(TruffleLanguage<?> language, com.oracle.truffle.api.source.Source source) {
                super(language);
                statementSection = source.createSection(1);
                statement = new TestStatementNode(statementSection);
                insert(statement);
            }

            @Override
            public String getName() {
                return TestRootNode.class.getSimpleName();
            }

            @Override
            public SourceSection getSourceSection() {
                return statementSection;
            }

            @Override
            public Object execute(VirtualFrame frame) {
                TruffleObject obj = new LazyReadObject();
                FrameSlot slot = frame.getFrameDescriptor().findOrAddFrameSlot("o", FrameSlotKind.Object);
                frame.setObject(slot, obj);
                return statement.execute(frame);
            }

            @Override
            protected boolean isInstrumentable() {
                return true;
            }

        }

        @GenerateWrapper
        static class TestStatementNode extends Node implements InstrumentableNode {

            private final SourceSection sourceSection;

            TestStatementNode(SourceSection sourceSection) {
                this.sourceSection = sourceSection;
            }

            @Override
            public boolean isInstrumentable() {
                return true;
            }

            @Override
            public InstrumentableNode.WrapperNode createWrapper(ProbeNode probe) {
                return new TestStatementNodeWrapper(sourceSection, this, probe);
            }

            public Object execute(VirtualFrame frame) {
                assert frame != null;
                return 10;
            }

            @Override
            public SourceSection getSourceSection() {
                return sourceSection;
            }

            @Override
            public boolean hasTag(Class<? extends Tag> tag) {
                return StandardTags.StatementTag.class.equals(tag);
            }

        }

        private class LazyReadObject extends ProxyInteropObject {

            LazyReadObject() {
            }

            @Override
            protected boolean hasMembers() {
                return true;
            }

            @Override
            protected Object getMembers(boolean includeInternal) throws UnsupportedMessageException {
                return new Keys();
            }

            @Override
            protected boolean isMemberReadable(String member) {
                return READ_READY.equals(member) || READ_LAZY.equals(member);
            }

            @Override
            protected boolean hasMemberReadSideEffects(String member) {
                return READ_LAZY.equals(member);
            }

            @Override
            protected Object readMember(String member) throws UnsupportedMessageException, UnknownIdentifierException {
                if (READ_READY.equals(member)) {
                    return 42;
                } else if (READ_LAZY.equals(member)) {
                    readLazyCount.incrementAndGet();
                    return 43;
                } else {
                    throw UnknownIdentifierException.create(member);
                }
            }
        }

        private static class Keys extends ProxyInteropObject {

            @Override
            protected boolean hasArrayElements() {
                return true;
            }

            @Override
            protected long getArraySize() throws UnsupportedMessageException {
                return 2;
            }

            @Override
            protected boolean isArrayElementReadable(long index) {
                return index >= 0 && index < 2;
            }

            @Override
            protected Object readArrayElement(long index) throws UnsupportedMessageException, InvalidArrayIndexException {
                switch ((int) index) {
                    case 0:
                        return READ_READY;
                    case 1:
                        return READ_LAZY;
                    default:
                        throw InvalidArrayIndexException.create(index);
                }
            }

        }
    }
}
