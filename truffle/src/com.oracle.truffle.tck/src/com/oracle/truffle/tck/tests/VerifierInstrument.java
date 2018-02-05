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
package com.oracle.truffle.tck.tests;

import java.util.function.Predicate;

import org.junit.Assert;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags.CallTag;
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;

import org.graalvm.polyglot.SourceSection;
import org.graalvm.polyglot.tck.InlineSnippet;

/**
 * Verify constraints of Truffle languages.
 */
@TruffleInstrument.Registration(name = VerifierInstrument.ID, id = VerifierInstrument.ID, services = VerifierInstrument.class)
public class VerifierInstrument extends TruffleInstrument {

    static final String ID = "TckVerifierInstrument";

    private Env env;
    private InlineScriptsRunner inlineScriptsRunner;
    private EventBinding<InlineScriptsRunner> inlineBinding;

    @Override
    protected void onCreate(Env instrumentEnv) {
        this.env = instrumentEnv;
        instrumentEnv.registerService(this);
        instrumentEnv.getInstrumenter().attachListener(
                        SourceSectionFilter.newBuilder().tagIs(RootTag.class).build(),
                        new RootFrameChecker(instrumentEnv.getInstrumenter()));
    }

    void setInlineSnippet(String languageId, InlineSnippet inlineSnippet, InlineResultVerifier verifier) {
        if (inlineSnippet != null) {
            inlineScriptsRunner = new InlineScriptsRunner();
            inlineBinding = env.getInstrumenter().attachListener(
                            SourceSectionFilter.newBuilder().tagIs(StatementTag.class, CallTag.class).build(),
                            inlineScriptsRunner);
            inlineScriptsRunner.setSnippet(languageId, inlineSnippet, verifier);
        } else if (inlineBinding != null) {
            inlineBinding.dispose();
            inlineBinding = null;
            inlineScriptsRunner = null;
        }
    }

    private class InlineScriptsRunner implements ExecutionEventListener {

        private volatile Source snippet;
        @CompilationFinal private volatile Predicate<SourceSection> predicate;
        @CompilationFinal private volatile ExecutableNode inlineNode;
        @CompilationFinal private volatile FrameDescriptor inlineDescriptor;
        @CompilationFinal private InlineResultVerifier resultVerifier;
        @CompilationFinal private Assumption inlineSnippetChanged = Truffle.getRuntime().createAssumption("Inline Snippet Changed");

        InlineScriptsRunner() {
        }

        @Override
        public void onEnter(EventContext context, VirtualFrame frame) {
            executeSnippet(context, frame);
        }

        @Override
        public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
            executeSnippet(context, frame);
        }

        @Override
        public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
            executeSnippet(context, frame);
        }

        private void executeSnippet(EventContext context, VirtualFrame frame) {
            if (!inlineSnippetChanged.isValid()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            if (snippet != null) {
                if (predicate == null || canRunAt(context.getInstrumentedSourceSection())) {
                    if (inlineNode == null || inlineDescriptor != frame.getFrameDescriptor()) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        try {
                            inlineNode = env.parseInline(snippet, context.getInstrumentedNode(), frame.materialize());
                        } catch (ThreadDeath t) {
                            throw t;
                        } catch (Throwable t) {
                            resultVerifier.verify(t);
                            throw t;
                        }
                        inlineDescriptor = frame.getFrameDescriptor();
                    }
                    try {
                        Object ret = inlineNode.execute(frame);
                        if (resultVerifier != null) {
                            resultVerifier.verify(ret);
                        }
                    } catch (ThreadDeath t) {
                        throw t;
                    } catch (Throwable t) {
                        CompilerDirectives.transferToInterpreter();
                        resultVerifier.verify(t);
                        throw t;
                    }
                }
            }
        }

        @TruffleBoundary
        private boolean canRunAt(com.oracle.truffle.api.source.SourceSection ss) {
            SourceSection section = TruffleTCKAccessor.instrumentAccess().createSourceSection(env, null, ss);
            return predicate.test(section);
        }

        private void setSnippet(String languageId, InlineSnippet inlineSnippet, InlineResultVerifier verifier) {
            if (inlineSnippet != null) {
                CharSequence code = inlineSnippet.getCode();
                this.snippet = Source.newBuilder(code).language(languageId).name("inline_source").build();
                this.predicate = inlineSnippet.getLocationPredicate();
                this.resultVerifier = verifier;
            } else {
                this.snippet = null;
                this.predicate = null;
                this.resultVerifier = null;
            }
            this.inlineNode = null;
            Assumption old = this.inlineSnippetChanged;
            inlineSnippetChanged = Truffle.getRuntime().createAssumption("Inline Snippet Changed");
            old.invalidate();
        }

    }

    private static class RootFrameChecker implements ExecutionEventListener {

        private final Instrumenter instrumenter;

        RootFrameChecker(Instrumenter instrumenter) {
            this.instrumenter = instrumenter;
        }

        @Override
        public void onEnter(EventContext context, VirtualFrame frame) {
            checkFrameIsEmpty(context, frame.materialize());
        }

        @TruffleBoundary
        private void checkFrameIsEmpty(EventContext context, MaterializedFrame frame) {
            if (!hasParentRootTag(context.getInstrumentedNode())) {
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
            if (instrumenter.queryTags(parent).contains(RootTag.class)) {
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

        static Accessor.InstrumentSupport instrumentAccess() {
            return ACCESSOR.instrumentSupport();
        }
    }
}
