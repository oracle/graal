/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.polyglot.ProxyInteropObject;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import org.graalvm.polyglot.Source;

/**
 * Test that not readable values are handled correctly. Variables, property values and array
 * elements are covered.
 */
public class NotReadableValuesTest {

    // @formatter:off   The default formatting makes unnecessarily big indents and illogical line breaks
    @Test
    public void testNotReadableVariables() throws Exception {
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
        ProxyLanguage.setDelegate(new NotReadableValuesLanguage());
        Source source = Source.newBuilder(ProxyLanguage.ID, "1", "ReadWithSideEffects.test").build();
        String sourceURI = InspectorTester.getStringURI(source.getURI());
        tester.eval(source);
        assertNotNull(tester.receiveMessages("{\"method\":\"Debugger.scriptParsed\"", "}\n"));
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
        // Ask for global variables
        tester.sendMessage("{\"id\":10,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"2\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":[{\"isOwn\":true,\"enumerable\":true,\"name\":\"object\"," +
                                                  "\"value\":{\"description\":\"Object VariablesObject\",\"className\":\"Object\",\"type\":\"object\",\"objectId\":\"4\"},\"configurable\":true,\"writable\":false}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"array\"," +
                                                  "\"value\":{\"subtype\":\"array\",\"description\":\"Object ArrayValue\",\"className\":\"Object\",\"type\":\"object\",\"objectId\":\"5\"},\"configurable\":true,\"writable\":false}]," +
                                "\"internalProperties\":[]},\"id\":10}\n"));
        // Ask for variable properties
        tester.sendMessage("{\"id\":20,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"4\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":[{\"isOwn\":true,\"enumerable\":true,\"name\":\"object\"," +
                                                  "\"value\":{\"description\":\"Object VariablesObject\",\"className\":\"Object\",\"type\":\"object\",\"objectId\":\"6\"},\"configurable\":true,\"writable\":false}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"array\"," +
                                                  "\"value\":{\"subtype\":\"array\",\"description\":\"Object ArrayValue\",\"className\":\"Object\",\"type\":\"object\",\"objectId\":\"7\"},\"configurable\":true,\"writable\":false}]," +
                                "\"internalProperties\":[]},\"id\":20}\n"));
        // Ask for array elements
        tester.sendMessage("{\"id\":30,\"method\":\"Runtime.getProperties\",\"params\":{\"objectId\":\"5\"}}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{\"result\":[{\"isOwn\":true,\"enumerable\":true,\"name\":\"0\",\"value\":{\"description\":\"6\",\"type\":\"number\",\"value\":6},\"configurable\":true,\"writable\":false}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"1\",\"value\":{\"description\":\"5\",\"type\":\"number\",\"value\":5},\"configurable\":true,\"writable\":false}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"4\",\"value\":{\"description\":\"2\",\"type\":\"number\",\"value\":2},\"configurable\":true,\"writable\":false}," +
                                                 "{\"isOwn\":true,\"enumerable\":true,\"name\":\"5\",\"value\":{\"description\":\"1\",\"type\":\"number\",\"value\":1},\"configurable\":true,\"writable\":false}]," +
                                "\"internalProperties\":[]},\"id\":30}\n"));

        tester.sendMessage("{\"id\":100,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":100}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));

        // Reset the delegate so that we can GC the tested Engine
        ProxyLanguage.setDelegate(new ProxyLanguage());
        tester.finish();
    }
    // @formatter:on

    // Not readable variable and property names start with 'nr'.
    // Not readable array elements are on indexes 2 and 3.
    static class NotReadableValuesLanguage extends ProxyLanguage {

        @Override
        protected final CallTarget parse(TruffleLanguage.ParsingRequest request) throws Exception {
            return Truffle.getRuntime().createCallTarget(new TestRootNode(languageInstance, request.getSource()));
        }

        @Override
        protected Iterable<Scope> findTopScopes(LanguageContext context) {
            return Collections.singletonList(Scope.newBuilder("top", new VariablesObject()).build());
        }

        @Override
        protected Object findMetaObject(LanguageContext context, Object value) {
            if (value instanceof TruffleObject) {
                return "Object";
            }
            return super.findMetaObject(context, value);
        }

        @Override
        protected String toString(LanguageContext context, Object value) {
            if (value instanceof ProxyInteropObject) {
                return value.getClass().getSimpleName();
            }
            return super.toString(context, value);
        }

        final class TestRootNode extends RootNode {

            @Node.Child private NrStatementNode statement;
            private final SourceSection statementSection;

            TestRootNode(TruffleLanguage<?> language, com.oracle.truffle.api.source.Source source) {
                super(language);
                statementSection = source.createSection(1);
                statement = new NrStatementNode(statementSection);
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
                return statement.execute(frame);
            }

            @Override
            protected boolean isInstrumentable() {
                return true;
            }

        }

        @GenerateWrapper
        static class NrStatementNode extends Node implements InstrumentableNode {

            private final SourceSection sourceSection;

            NrStatementNode(SourceSection sourceSection) {
                this.sourceSection = sourceSection;
            }

            @Override
            public boolean isInstrumentable() {
                return true;
            }

            @Override
            public InstrumentableNode.WrapperNode createWrapper(ProbeNode probe) {
                return new NrStatementNodeWrapper(sourceSection, this, probe);
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

        private static final class VariablesObject extends ProxyInteropObject {

            private static final String[] NAMES = new String[]{"nr_a", "object", "nr_object", "array", "nr_array"};

            @Override
            protected boolean hasMembers() {
                return true;
            }

            @Override
            @CompilerDirectives.TruffleBoundary
            protected Object getMembers(boolean includeInternal) throws UnsupportedMessageException {
                return new Names(NAMES);
            }

            @Override
            protected boolean isMemberReadable(String member) {
                return !member.startsWith("nr");
            }

            @Override
            @CompilerDirectives.TruffleBoundary
            protected Object readMember(String member) throws UnsupportedMessageException, UnknownIdentifierException {
                if (member.startsWith("nr")) {
                    throw UnsupportedMessageException.create();
                }
                switch (member) {
                    case "object":
                        return new VariablesObject();
                    case "array":
                        return new ArrayValue();
                    default:
                        throw UnsupportedMessageException.create();
                }
            }
        }

        private static class Names extends ProxyInteropObject {

            private final String[] names;

            Names(String[] names) {
                this.names = names;
            }

            @Override
            protected boolean hasArrayElements() {
                return true;
            }

            @Override
            protected long getArraySize() throws UnsupportedMessageException {
                return names.length;
            }

            @Override
            protected boolean isArrayElementReadable(long index) {
                return index >= 0 && index < names.length;
            }

            @Override
            protected Object readArrayElement(long index) throws UnsupportedMessageException, InvalidArrayIndexException {
                if (index >= 0 && index < names.length) {
                    return names[(int) index];
                } else {
                    CompilerDirectives.transferToInterpreter();
                    throw InvalidArrayIndexException.create(index);
                }
            }
        }

        private static class ArrayValue extends ProxyInteropObject {

            private static final long LENGTH = 6;
            private static final Set<Long> NOT_READABLE = new HashSet<>(Arrays.asList(new Long[]{2L, 3L}));

            @Override
            protected boolean hasArrayElements() {
                return true;
            }

            @Override
            protected long getArraySize() throws UnsupportedMessageException {
                return LENGTH;
            }

            @Override
            @CompilerDirectives.TruffleBoundary
            protected boolean isArrayElementReadable(long index) {
                return index >= 0 && index < LENGTH && !NOT_READABLE.contains(index);
            }

            @Override
            protected Object readArrayElement(long index) throws UnsupportedMessageException, InvalidArrayIndexException {
                if (isArrayElementReadable(index)) {
                    return LENGTH - index;
                } else {
                    CompilerDirectives.transferToInterpreter();
                    throw InvalidArrayIndexException.create(index);
                }
            }
        }
    }

}
