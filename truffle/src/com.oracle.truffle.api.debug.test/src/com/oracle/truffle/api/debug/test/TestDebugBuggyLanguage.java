/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Objects;
import java.util.function.BiFunction;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.NodeLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.polyglot.ProxyInteropObject;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

/**
 * A buggy language for debugger tests. Use {@link ProxyLanguage#setDelegate(ProxyLanguage)} to
 * register instances of this class. The language produces one root node containing one statement
 * node with <code>a</code> and <code>o</code> local variables. <code>a</code> contains an integer
 * and <code>o</code> is an object containing an <code>A</code> property.
 * <p>
 * To trigger an exception, evaluate a Source containing number <code>1</code> for an
 * {@link IllegalStateException}, number <code>2</code> for a {@link AbstractTruffleException}, or
 * number <code>3</code> for an {@link AssertionError}. The number is available in <code>a</code>
 * local variable and <code>o.A</code> property, so that it can be passed to the
 * {@link #throwBug(java.lang.Object)}.
 * <p>
 * Extend this class and {@link #throwBug(java.lang.Object) throw bug} in a language method, or
 * evaluate a Source that contains one of following keywords prior the error number:
 * <ul>
 * <li><b>ROOT</b> - to get an exception from RootNode.getName()</li>
 * <li><b>KEYS</b> - to get an exception from KEYS message on the value of <code>o</code> variable
 * </li>
 * <li><b>KEY_INFO</b> - to get an exception from KEY_INFO message on the <code>A</code> property of
 * the <code>o</code> variable</li>
 * <li><b>READ</b> - to get an exception from READ message on the <code>A</code> property of the
 * <code>o</code> variable</li>
 * <li><b>WRITE</b> - to get an exception from WRITE message on the <code>A</code> property of the
 * <code>o</code> variable</li>
 * </ul>
 */
@SuppressWarnings("static-method")

public class TestDebugBuggyLanguage extends ProxyLanguage {

    protected BiFunction<Node, Frame, Object> scopeProvider() {
        return null;
    }

    protected final Object getDefaultScope(Node node, Frame frame, boolean enterNode) {
        try {
            return NodeLibrary.getUncached().getScope(((TestRootNode) node.getRootNode()).getDefaultScopeNode(), frame, enterNode);
        } catch (UnsupportedMessageException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    @Override
    protected final CallTarget parse(TruffleLanguage.ParsingRequest request) throws Exception {
        return new TestRootNode(languageInstance, request.getSource(), scopeProvider()).getCallTarget();
    }

    @SuppressWarnings("static-method")
    protected final void throwBug(Object value) {
        if (value instanceof Integer) {
            int v = (Integer) value;
            throwBug(v);
        }
    }

    @Override
    protected Object getLanguageView(LanguageContext context, Object value) {
        return new ProxyInteropObject.InteropWrapper(value) {
            @Override
            protected boolean hasLanguage() {
                return true;
            }

            @Override
            protected Class<? extends TruffleLanguage<?>> getLanguage() throws UnsupportedMessageException {
                return ProxyLanguage.get(null).getClass();
            }

            @Override
            protected boolean hasMetaObject() {
                return true;
            }

            @Override
            protected Object getMetaObject() throws UnsupportedMessageException {
                return new MetaObject(delegate);
            }

            class MetaObject extends ProxyInteropObject.InteropWrapper {

                MetaObject(Object v) {
                    super(v);
                }

                @Override
                protected boolean isMetaObject() {
                    return true;
                }

                @Override
                protected String getMetaSimpleName() throws UnsupportedMessageException {
                    return delegate.getClass().getSimpleName();
                }

                @Override
                protected String getMetaQualifiedName() throws UnsupportedMessageException {
                    return delegate.getClass().getSimpleName();
                }

                @Override
                protected Object toDisplayString(boolean allowSideEffects) {
                    return Objects.toString(delegate);
                }
            }
        };
    }

    static void throwBug(int v) {
        if (v == 1) {
            throw new IllegalStateException(Integer.toString(v));
        } else if (v == 2) {
            throw new TestTruffleException();
        } else if (v == 3) {
            throw new AssertionError(v);
        }
    }

    private static final class TestRootNode extends RootNode {

        @Node.Child private TestStatementNode statement;
        @Node.Child private TestStatementNode defaultScopeNode;
        private final SourceSection statementSection;

        TestRootNode(TruffleLanguage<?> language, com.oracle.truffle.api.source.Source source, BiFunction<Node, Frame, Object> scopeProvider) {
            super(language);
            statementSection = source.createSection(1);
            statement = scopeProvider != null ? new TestStatementScopedNode(statementSection, scopeProvider) : new TestStatementNode(statementSection);
            defaultScopeNode = new TestStatementNode(statementSection);
            insert(statement);
        }

        Node getDefaultScopeNode() {
            if (defaultScopeNode instanceof InstrumentableNode.WrapperNode) {
                return ((InstrumentableNode.WrapperNode) defaultScopeNode).getDelegateNode();
            }
            return defaultScopeNode;
        }

        @Override
        public String getName() {
            String text = statementSection.getCharacters().toString();
            if (text.startsWith("ROOT")) {
                throwBug(Integer.parseInt(text.substring(4).trim()));
            }
            return text;
        }

        @Override
        public SourceSection getSourceSection() {
            return statementSection;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            boundary(frame.materialize());
            return statement.execute(frame);
        }

        @CompilerDirectives.TruffleBoundary
        private void boundary(MaterializedFrame frame) {
            int slot = frame.getFrameDescriptor().findOrAddAuxiliarySlot("a");
            String text = statementSection.getCharacters().toString();
            int index = 0;
            while (!Character.isDigit(text.charAt(index))) {
                index++;
            }
            int errNum = Integer.parseInt(text.substring(index));
            frame.setAuxiliarySlot(slot, errNum);
            TruffleObject obj = new ErrorObject(text.substring(0, index).trim(), errNum);
            slot = frame.getFrameDescriptor().findOrAddAuxiliarySlot("o");
            frame.setAuxiliarySlot(slot, obj);
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
            String text = sourceSection.getCharacters().toString();
            if (text.startsWith("Location Ex")) {
                throwBug(Integer.parseInt(text.substring(8)));
            }
            return sourceSection;
        }

        @Override
        public boolean hasTag(Class<? extends Tag> tag) {
            return StandardTags.StatementTag.class.equals(tag);
        }

    }

    @ExportLibrary(NodeLibrary.class)
    static class TestStatementScopedNode extends TestStatementNode {

        private final BiFunction<Node, Frame, Object> scopeProvider;

        TestStatementScopedNode(SourceSection sourceSection, BiFunction<Node, Frame, Object> scopeProvider) {
            super(sourceSection);
            this.scopeProvider = scopeProvider;
        }

        @ExportMessage
        boolean hasScope(@SuppressWarnings("unused") Frame frame) {
            return scopeProvider != null;
        }

        @ExportMessage
        final Object getScope(Frame frame, @SuppressWarnings("unused") boolean nodeEnter) throws UnsupportedMessageException {
            if (scopeProvider != null) {
                return scopeProvider.apply(this, frame);
            } else {
                throw UnsupportedMessageException.create();
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class ErrorObject implements TruffleObject {

        private final String error;
        private final int errNum;

        ErrorObject(String error, int errNum) {
            this.error = error;
            this.errNum = errNum;
        }

        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        boolean isMemberInsertable(@SuppressWarnings("unused") String member) {
            return false;
        }

        @ExportMessage
        public Object getMembers(@SuppressWarnings("unused") boolean internal) {
            if ("KEYS".equals(error)) {
                throwBug(errNum);
            }
            return new Keys();
        }

        @ExportMessage
        boolean isMemberModifiable(String member) {
            if ("KEY_INFO".equals(error) && "A".equals(member)) {
                throwBug(errNum);
            }
            return "A".equals(member) || "B".equals(member);
        }

        @ExportMessage
        boolean isMemberReadable(String member) {
            if ("KEY_INFO".equals(error) && "A".equals(member)) {
                throwBug(errNum);
            }
            return "A".equals(member) || "B".equals(member);
        }

        @ExportMessage
        public Object readMember(String key) throws UnknownIdentifierException {
            if ("READ".equals(error) && "A".equals(key)) {
                throwBug(errNum);
            }
            if ("A".equals(key)) {
                return errNum;
            } else if ("B".equals(key)) {
                return 42;
            } else {
                throw UnknownIdentifierException.create(key);
            }
        }

        @ExportMessage
        public Object writeMember(String key, Object value) throws UnknownIdentifierException {
            if ("WRITE".equals(error) && "A".equals(key)) {
                throwBug(errNum);
            }
            if ("A".equals(key) || "B".equals(key)) {
                return value;
            } else {
                throw UnknownIdentifierException.create(key);
            }
        }

        @ExportMessage
        public boolean isExecutable() {
            if ("CAN_EXECUTE".equals(error)) {
                throwBug(errNum);
            }
            return true;
        }

        @ExportMessage
        public Object execute(@SuppressWarnings("unused") Object[] args) {
            if ("EXECUTE".equals(error)) {
                throwBug(errNum);
            }
            return 10;
        }

        @Override
        public String toString() {
            return "ErrorObject " + errNum + (error.isEmpty() ? "" : " " + error);
        }

        @ExportLibrary(InteropLibrary.class)
        static class Keys implements TruffleObject {

            @ExportMessage
            public boolean hasArrayElements() {
                return true;
            }

            @ExportMessage
            public long getArraySize() {
                return 2L;
            }

            @ExportMessage
            public boolean isArrayElementReadable(long key) {
                return key == 0 || key == 1;
            }

            @ExportMessage
            public Object readArrayElement(long key) throws InvalidArrayIndexException {
                if (key == 0) {
                    return "A";
                } else if (key == 1) {
                    return "B";
                } else {
                    throw InvalidArrayIndexException.create(key);
                }
            }

        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class TestTruffleException extends AbstractTruffleException {

        private static final long serialVersionUID = 7653875618655878235L;

        TestTruffleException() {
            super("A TruffleException");
        }

        @Override
        public String toString() {
            return getMessage();
        }

        @ExportMessage
        public boolean hasMetaObject() {
            return true;
        }

        @ExportMessage
        public Object getMetaObject() {
            return new MetaObject();
        }

        @ExportMessage
        Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
            return getMessage();
        }

        @ExportLibrary(InteropLibrary.class)
        static final class MetaObject implements TruffleObject {

            @ExportMessage
            boolean isMetaObject() {
                return true;
            }

            @ExportMessage
            String getMetaQualifiedName() {
                return TestTruffleException.class.getName();
            }

            @ExportMessage
            String getMetaSimpleName() {
                return TestTruffleException.class.getSimpleName();
            }

            @ExportMessage
            @SuppressWarnings("unused")
            String toDisplayString(boolean allowSideEffects) {
                return getMetaSimpleName();
            }

            @ExportMessage
            boolean isMetaInstance(Object instance) {
                return instance instanceof TestTruffleException;
            }
        }
    }

}
