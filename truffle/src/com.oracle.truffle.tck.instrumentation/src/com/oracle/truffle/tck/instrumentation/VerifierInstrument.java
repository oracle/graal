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
package com.oracle.truffle.tck.instrumentation;

import java.util.function.Predicate;

import org.junit.Assert;

import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.SourceSection;
import org.graalvm.polyglot.tck.InlineSnippet;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags.CallTag;
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.tck.common.inline.InlineVerifier;

/**
 * Verify constraints of Truffle languages.
 */
@TruffleInstrument.Registration(name = VerifierInstrument.ID, id = VerifierInstrument.ID, services = InlineVerifier.class)
public class VerifierInstrument extends TruffleInstrument implements InlineVerifier {

    private Env env;
    private InlineScriptFactory inlineScriptFactory;
    private EventBinding<InlineScriptFactory> inlineBinding;

    @Override
    protected void onCreate(Env instrumentEnv) {
        this.env = instrumentEnv;
        instrumentEnv.registerService(this);
        instrumentEnv.getInstrumenter().attachExecutionEventListener(
                        SourceSectionFilter.newBuilder().tagIs(RootTag.class).build(),
                        new RootFrameChecker());
    }

    @Override
    public void setInlineSnippet(String languageId, InlineSnippet inlineSnippet, InlineVerifier.ResultVerifier verifier) {
        if (inlineSnippet != null) {
            inlineScriptFactory = new InlineScriptFactory(languageId, inlineSnippet, verifier);
            inlineBinding = env.getInstrumenter().attachExecutionEventFactory(
                            SourceSectionFilter.newBuilder().tagIs(StatementTag.class, CallTag.class).build(),
                            inlineScriptFactory);
        } else if (inlineBinding != null) {
            inlineBinding.dispose();
            inlineBinding = null;
            inlineScriptFactory = null;
        }
    }

    private class InlineScriptFactory implements ExecutionEventNodeFactory {

        private final Source snippet;
        private final Predicate<SourceSection> predicate;
        private final InlineVerifier.ResultVerifier resultVerifier;

        InlineScriptFactory(String languageId, InlineSnippet inlineSnippet, InlineVerifier.ResultVerifier verifier) {
            CharSequence code = inlineSnippet.getCode();
            snippet = Source.newBuilder(languageId, code, "inline_source").build();
            predicate = inlineSnippet.getLocationPredicate();
            resultVerifier = verifier;
        }

        @Override
        public ExecutionEventNode create(EventContext context) {
            if (predicate == null || canRunAt(context.getInstrumentedSourceSection())) {
                return new InlineScriptNode(context);
            } else {
                return null;
            }
        }

        private boolean canRunAt(com.oracle.truffle.api.source.SourceSection ss) {
            SourceSection section = TruffleTCKAccessor.instrumentAccess().createSourceSection(env, null, ss);
            return predicate.test(section);
        }

        private class InlineScriptNode extends ExecutionEventNode {

            private final Node instrumentedNode;
            @CompilationFinal private volatile ExecutableNode inlineNode;

            InlineScriptNode(EventContext context) {
                this.instrumentedNode = context.getInstrumentedNode();
            }

            @Override
            protected void onEnter(VirtualFrame frame) {
                executeSnippet(frame);
            }

            @Override
            protected void onReturnValue(VirtualFrame frame, Object result) {
                executeSnippet(frame);
            }

            @Override
            protected void onReturnExceptional(VirtualFrame frame, Throwable exception) {
                executeSnippet(frame);
            }

            private void executeSnippet(VirtualFrame frame) {
                if (inlineNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    try {
                        inlineNode = env.parseInline(snippet, instrumentedNode, frame.materialize());
                    } catch (ThreadDeath t) {
                        throw t;
                    } catch (Throwable t) {
                        verify(t);
                        throw t;
                    }
                    insert(inlineNode);
                }
                try {
                    Object ret = inlineNode.execute(frame);
                    if (resultVerifier != null) {
                        verify(ret);
                    }
                } catch (ThreadDeath t) {
                    throw t;
                } catch (Throwable t) {
                    CompilerDirectives.transferToInterpreter();
                    verify(t);
                    throw t;
                }
            }

            @TruffleBoundary
            private void verify(final Throwable exception) {
                final PolyglotException pe = VerifierInstrument.TruffleTCKAccessor.engineAccess().wrapGuestException(snippet.getLanguage(), exception);
                resultVerifier.verify(pe);
            }

            @TruffleBoundary
            private void verify(final Object result) {
                resultVerifier.verify(result);
            }
        }
    }

    private static class RootFrameChecker implements ExecutionEventListener {

        @Override
        public void onEnter(EventContext context, VirtualFrame frame) {
            checkFrameIsEmpty(context, frame.materialize());
        }

        @TruffleBoundary
        private void checkFrameIsEmpty(EventContext context, MaterializedFrame frame) {
            Node node = context.getInstrumentedNode();
            if (!hasParentRootTag(node) &&
                            node.getRootNode().getFrameDescriptor() == frame.getFrameDescriptor()) {
                // Top-most nodes tagged with RootTag should have clean frames.
                Object defaultValue = frame.getFrameDescriptor().getDefaultValue();
                for (FrameSlot slot : frame.getFrameDescriptor().getSlots()) {
                    Assert.assertEquals(defaultValue, frame.getValue(slot));
                }
            }
        }

        private boolean hasParentRootTag(Node node) {
            Node parent = node.getParent();
            if (parent == null) {
                return false;
            }
            if (TruffleTCKAccessor.nodesAccess().isTaggedWith(parent, RootTag.class)) {
                return true;
            }
            return hasParentRootTag(parent);
        }

        @Override
        public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
        }

        @Override
        public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
        }
    }

    static final TruffleTCKAccessor ACCESSOR = new TruffleTCKAccessor();

    static final class TruffleTCKAccessor extends Accessor {

        static Accessor.EngineSupport engineAccess() {
            return ACCESSOR.engineSupport();
        }

        static Accessor.Nodes nodesAccess() {
            return ACCESSOR.nodes();
        }

        static Accessor.InstrumentSupport instrumentAccess() {
            return ACCESSOR.instrumentSupport();
        }
    }
}
