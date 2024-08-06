/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.debug.test;

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.NodeLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.polyglot.ProxyInteropObject;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

/**
 * Language for scope receiver testing. Expects sources in the form:<br>
 * <code>receiver_name\n</code><br>
 * <code>space-separated scope variables in the form of &lt;name value&gt;\n</code><br>
 * <code>further scopes on next lines\n</code><br>
 */
public final class TestReceiverLanguage extends ProxyLanguage {

    public TestReceiverLanguage() {
    }

    @Override
    protected CallTarget parse(ParsingRequest request) throws Exception {
        Source source = request.getSource();
        return new TestReceiverRootNode(languageInstance, source).getCallTarget();
    }

    private static class TestReceiverRootNode extends RootNode {

        @Node.Child private TestReceiverStatementNode statement;

        TestReceiverRootNode(ProxyLanguage languageInstance, Source source) {
            super(languageInstance);
            this.statement = new TestReceiverStatementNode(source.createSection(0, source.getLength()));
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return statement.execute(frame);
        }
    }

    @GenerateWrapper
    @ExportLibrary(NodeLibrary.class)
    @SuppressWarnings("static-method")
    static class TestReceiverStatementNode extends Node implements InstrumentableNode {

        private final SourceSection sourceSection;

        TestReceiverStatementNode(SourceSection sourceSection) {
            this.sourceSection = sourceSection;
        }

        @Override
        public boolean isInstrumentable() {
            return true;
        }

        @Override
        public InstrumentableNode.WrapperNode createWrapper(ProbeNode probe) {
            return new TestReceiverStatementNodeWrapper(sourceSection, this, probe);
        }

        public Object execute(VirtualFrame frame) {
            assert frame != null;
            return 42;
        }

        @Override
        public SourceSection getSourceSection() {
            return sourceSection;
        }

        @Override
        public boolean hasTag(Class<? extends Tag> tag) {
            return StandardTags.StatementTag.class.equals(tag);
        }

        @ExportMessage
        boolean hasScope(@SuppressWarnings("unused") Frame frame) {
            return true;
        }

        @ExportMessage
        final Object getScope(@SuppressWarnings("unused") Frame frame, @SuppressWarnings("unused") boolean nodeEnter) {
            return getScopeSlowPath();
        }

        @TruffleBoundary
        private Object getScopeSlowPath() {
            String members = sourceSection.getCharacters().toString();
            int end = members.indexOf('\n');
            return new TestReceiverScope(members.substring(end + 1).trim(), 0);
        }

        @ExportMessage
        final boolean hasReceiverMember(@SuppressWarnings("unused") Frame frame) {
            return true;
        }

        @ExportMessage
        final Object getReceiverMember(@SuppressWarnings("unused") Frame frame) {
            return getReceiverMemberSlowPath();
        }

        @TruffleBoundary
        private Object getReceiverMemberSlowPath() {
            String receivers = sourceSection.getCharacters().toString();
            int end = receivers.indexOf('\n');
            return receivers.substring(0, end);
        }
    }

    static final class TestReceiverScope extends ProxyInteropObject {

        private final String members;
        private final String parentMembers;
        private final Map<String, String> membersMap;
        private final int ord;

        TestReceiverScope(String members, int ord) {
            int end = members.indexOf('\n');
            if (end < 0) {
                end = members.length();
            }
            this.members = members;
            this.parentMembers = (end < members.length()) ? members.substring(end + 1) : null;
            this.membersMap = createMembersMap(members);
            this.ord = ord;
        }

        private static Map<String, String> createMembersMap(String members) {
            Map<String, String> map = new HashMap<>();
            String[] membersArray = members.split("\\s+");
            for (int i = 0; i < membersArray.length; i += 2) {
                map.putIfAbsent(membersArray[i], membersArray[i + 1]);
            }
            return map;
        }

        @Override
        protected boolean isScope() {
            return true;
        }

        @Override
        protected boolean hasScopeParent() {
            return parentMembers != null;
        }

        @Override
        @TruffleBoundary
        protected Object getScopeParent() throws UnsupportedMessageException {
            if (parentMembers == null) {
                throw UnsupportedMessageException.create();
            }
            return new TestReceiverScope(parentMembers, ord + 1);
        }

        @Override
        protected boolean hasMembers() {
            return true;
        }

        @Override
        @TruffleBoundary
        protected Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
            return new ReceiverScopeMembers(members.split("\\s+"));
        }

        @Override
        @TruffleBoundary
        protected boolean isMemberReadable(String member) {
            return membersMap.containsKey(member);
        }

        @Override
        @TruffleBoundary
        protected Object readMember(String member) throws UnknownIdentifierException {
            if (!isMemberReadable(member)) {
                throw UnknownIdentifierException.create(member);
            }
            return membersMap.get(member);
        }

        @Override
        protected boolean hasLanguage() {
            return true;
        }

        @Override
        protected Class<? extends TruffleLanguage<?>> getLanguage() {
            return TestReceiverLanguage.class;
        }

        @Override
        protected Object toDisplayString(boolean allowSideEffects) {
            return Integer.toString(ord);
        }
    }

    static final class ReceiverScopeMembers extends ProxyInteropObject {

        private final String[] members;

        ReceiverScopeMembers(String[] members) {
            this.members = members;
        }

        @Override
        protected boolean hasArrayElements() {
            return true;
        }

        @Override
        protected long getArraySize() throws UnsupportedMessageException {
            return members.length / 2;
        }

        @Override
        protected Object readArrayElement(long index) throws InvalidArrayIndexException {
            if (!isArrayElementReadable(index)) {
                throw InvalidArrayIndexException.create(index);
            }
            return members[(int) index * 2];
        }

        @Override
        protected boolean isArrayElementReadable(long index) {
            return 0 <= index && index < members.length / 2;
        }

    }

}
