/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

public final class TestDebugObjectLanguage extends ProxyLanguage {

    @Override
    protected CallTarget parse(ParsingRequest request) throws Exception {
        return new TestDebugObjectRootNode(languageInstance, request.getSource()).getCallTarget();
    }

    @Override
    protected Object getLanguageView(LanguageContext context, Object value) {
        return new ProxyLanguageView<>(value);
    }

    private static final class TestDebugObjectRootNode extends RootNode {

        @Node.Child private TestDebugObjectStatementNode statement;
        private final SourceSection sourceSection;
        private final int slotA;

        private TestDebugObjectRootNode(ProxyLanguage languageInstance, Source source) {
            super(languageInstance);
            this.sourceSection = source.createSection(0, source.getLength());
            this.statement = new TestDebugObjectStatementNode(sourceSection);
            insert(this.statement);
            this.slotA = getFrameDescriptor().findOrAddAuxiliarySlot("a");
        }

        @Override
        public SourceSection getSourceSection() {
            return sourceSection;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object[] arguments = frame.getArguments();
            Object a = arguments.length > 0 ? arguments[0] : null;
            frame.setAuxiliarySlot(slotA, a);
            return statement.execute(frame);
        }
    }

    @GenerateWrapper
    static class TestDebugObjectStatementNode extends Node implements InstrumentableNode {

        private final SourceSection sourceSection;

        TestDebugObjectStatementNode(SourceSection sourceSection) {
            this.sourceSection = sourceSection;
        }

        @Override
        public boolean isInstrumentable() {
            return true;
        }

        @Override
        public InstrumentableNode.WrapperNode createWrapper(ProbeNode probe) {
            return new TestDebugObjectStatementNodeWrapper(sourceSection, this, probe);
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

    @ExportLibrary(value = InteropLibrary.class, delegateTo = "delegate")
    @SuppressWarnings("static-method")
    static final class ProxyLanguageView<C> implements TruffleObject {

        protected final Object delegate;

        ProxyLanguageView(Object delegate) {
            this.delegate = delegate;
        }

        @ExportMessage
        boolean hasLanguage() {
            return true;
        }

        @SuppressWarnings("unchecked")
        @ExportMessage
        Class<? extends TruffleLanguage<?>> getLanguage() {
            return ProxyLanguage.class;
        }

        @ExportMessage
        Object toDisplayString(boolean allowSideEffects, @CachedLibrary("this.delegate") InteropLibrary interopLibrary) {
            return interopLibrary.toDisplayString(delegate, allowSideEffects);
        }

    }
}
