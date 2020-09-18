/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.NodeLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.polyglot.ProxyInstrument;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import static org.graalvm.compiler.truffle.test.InstrumentationCompilerTest.DUMMY_SECTION;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;

/**
 * Test that implementation of a NodeLibrary efficiently partially evaluates.
 */
public class NodeLibraryCompilerTest extends PartialEvaluationTest {

    private static final String VAR = "var";

    private ProxyLanguage language;

    @Before
    public void setup() {
        setupContext(Context.newBuilder());
        getContext().initialize(ProxyLanguage.ID);
        doInstrument(getContext().getEngine());
        this.language = ProxyLanguage.getCurrentLanguage();
    }

    @Test
    public void testScopeRead() {
        FrameDescriptor frameDescriptor = new FrameDescriptor();
        FrameSlot varSlot = frameDescriptor.addFrameSlot(VAR);
        frameDescriptor.setFrameSlotKind(varSlot, FrameSlotKind.Long);
        RootNode expectedRootNode = createRoot(frameDescriptor, new ReadVarNode(varSlot), varSlot, false);
        RootNode instrumentedRootNode = createRoot(frameDescriptor, new DummyInstrumentableNode(varSlot), varSlot, true);
        assertPartialEvalEquals(expectedRootNode, instrumentedRootNode, new Object[0]);
    }

    private RootNode createRoot(FrameDescriptor frameDescriptor, InstrumentationCompilerTestScopeNode node, FrameSlot varSlot, boolean allowInstrumentation) {
        return new RootNode(language, frameDescriptor) {
            @Child InstrumentationCompilerTestScopeNode child = node;

            @Override
            public SourceSection getSourceSection() {
                return DUMMY_SECTION;
            }

            @Override
            protected boolean isInstrumentable() {
                return allowInstrumentation;
            }

            @Override
            public Object execute(VirtualFrame frame) {
                frame.setLong(varSlot, 0);
                return child.execute(frame);
            }
        };
    }

    @GenerateWrapper
    @ExportLibrary(value = NodeLibrary.class)
    abstract static class InstrumentationCompilerTestScopeNode extends Node implements InstrumentableNode {

        public abstract Object execute(VirtualFrame frame);

        @Override
        public SourceSection getSourceSection() {
            return DUMMY_SECTION;
        }

        @Override
        public boolean isInstrumentable() {
            return true;
        }

        @Override
        public InstrumentableNode.WrapperNode createWrapper(ProbeNode probe) {
            return new InstrumentationCompilerTestScopeNodeWrapper(this, probe);
        }

        @Override
        public boolean hasTag(Class<? extends Tag> tag) {
            return tag == StandardTags.ExpressionTag.class;
        }

        @ExportMessage
        public boolean hasScope(@SuppressWarnings("unused") Frame frame) {
            return true;
        }

        @ExportMessage
        abstract Object getScope(Frame frame, boolean nodeEnter) throws UnsupportedMessageException;
    }

    @ExportLibrary(InteropLibrary.class)
    static final class ScopeVariables implements TruffleObject {

        private final Frame frame;
        private final ReadVarNode readNode;

        ScopeVariables(Frame frame, ReadVarNode readNode) {
            this.frame = frame;
            this.readNode = readNode;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasLanguage() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        Class<? extends TruffleLanguage<?>> getLanguage() {
            return ProxyLanguage.class;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isScope() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
            return "Local";
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isMemberReadable(String name) {
            return name.equals(VAR);
        }

        @ExportMessage
        Object readMember(String name) throws UnknownIdentifierException {
            if (name.equals(VAR)) {
                return readNode.execute((VirtualFrame) frame);
            } else {
                CompilerDirectives.transferToInterpreter();
                throw UnknownIdentifierException.create(name);
            }
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        Object getMembers(@SuppressWarnings("unused") boolean includeInternal) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }
    }

    static final class ReadVarNode extends InstrumentationCompilerTestScopeNode {

        private final FrameSlot varSlot;

        ReadVarNode(FrameSlot varSlot) {
            this.varSlot = varSlot;
        }

        @Override
        public boolean isInstrumentable() {
            return false;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            try {
                return frame.getLong(varSlot);
            } catch (FrameSlotTypeException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @Override
        Object getScope(Frame frame, boolean nodeEnter) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }
    }

    static final class DummyInstrumentableNode extends InstrumentationCompilerTestScopeNode {

        private final ReadVarNode readVar;

        DummyInstrumentableNode(FrameSlot varSlot) {
            this.readVar = new ReadVarNode(varSlot);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return Boolean.TRUE;
        }

        @Override
        @ExportMessage
        Object getScope(Frame frame, boolean nodeEnter) throws UnsupportedMessageException {
            return new ScopeVariables(frame, readVar);
        }
    }

    private static void doInstrument(Engine engine) {
        ProxyInstrument instrument = new ProxyInstrument();
        ProxyInstrument.setDelegate(instrument);
        instrument.setOnCreate((env) -> {
            env.getInstrumenter().attachExecutionEventFactory(
                            SourceSectionFilter.newBuilder().tagIs(StandardTags.ExpressionTag.class).build(),
                            context -> new ReadVarExecNode(context));
        });
        engine.getInstruments().get(ProxyInstrument.ID).lookup(ProxyInstrument.Initialize.class);
    }

    private static final class ReadVarExecNode extends ExecutionEventNode {

        private final EventContext context;
        private final Node node;
        @Child private NodeLibrary nodeLibrary = NodeLibrary.getFactory().createDispatched(1);
        @Child private InteropLibrary interop = InteropLibrary.getFactory().createDispatched(1);

        private ReadVarExecNode(EventContext context) {
            this.context = context;
            this.node = context.getInstrumentedNode();
            assert nodeLibrary.hasScope(node, null) : node;
        }

        @Override
        protected void onReturnValue(VirtualFrame frame, Object result) {
            try {
                Object scope = nodeLibrary.getScope(node, frame, false);
                Object val = interop.readMember(scope, VAR);
                throw context.createUnwind(val);
            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @Override
        protected Object onUnwind(VirtualFrame frame, Object info) {
            return info;
        }
    }
}
