/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tck.instrumentation;

import java.util.function.Predicate;

import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.SourceSection;
import org.graalvm.polyglot.tck.InlineSnippet;
import org.junit.Assert;

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
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags.CallTag;
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;
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

    private static final ThreadLocal<Boolean> ENTERED = new ThreadLocal<>();

    @Override
    protected void onCreate(Env instrumentEnv) {
        this.env = instrumentEnv;
        instrumentEnv.registerService(this);
        instrumentEnv.getInstrumenter().attachExecutionEventListener(
                        SourceSectionFilter.newBuilder().tagIs(RootTag.class).build(),
                        new RootFrameChecker());
        instrumentEnv.getInstrumenter().attachExecutionEventListener(
                        SourceSectionFilter.newBuilder().tagIs(RootTag.class).build(),
                        new NodePropertyChecker());
        instrumentEnv.getInstrumenter().attachExecutionEventFactory(
                        SourceSectionFilter.newBuilder().tagIs(RootTag.class).build(),
                        new LibraryChecker());
        instrumentEnv.getInstrumenter().attachExecutionEventListener(
                        SourceSectionFilter.newBuilder().build(),
                        new EmptyExecutionEventListener());
    }

    @Override
    public void setInlineSnippet(String languageId, InlineSnippet inlineSnippet, InlineVerifier.ResultVerifier verifier) {
        if (inlineSnippet != null) {
            inlineScriptFactory = new InlineScriptFactory(languageId, inlineSnippet, verifier);
            inlineBinding = env.getInstrumenter().attachExecutionEventFactory(
                            SourceSectionFilter.newBuilder().tagIs(StatementTag.class, CallTag.class).build(),
                            inlineScriptFactory);
        } else if (inlineBinding != null) {
            if (!inlineScriptFactory.snippetExecuted) {
                Assert.fail("Inline snippet was not executed.");
            }
            inlineBinding.dispose();
            inlineBinding = null;
            inlineScriptFactory = null;
        }
    }

    @TruffleBoundary
    private static void leave() {
        ENTERED.set(Boolean.FALSE);
    }

    @TruffleBoundary
    private static void enter() {
        ENTERED.set(Boolean.TRUE);
    }

    @TruffleBoundary
    private static boolean isEntered() {
        return Boolean.TRUE == ENTERED.get();
    }

    private class InlineScriptFactory implements ExecutionEventNodeFactory {

        private final Source snippet;
        private final Predicate<SourceSection> predicate;
        private final InlineVerifier.ResultVerifier resultVerifier;
        volatile boolean snippetExecuted = false;

        InlineScriptFactory(String languageId, InlineSnippet inlineSnippet, InlineVerifier.ResultVerifier verifier) {
            CharSequence code = inlineSnippet.getCode();
            snippet = Source.newBuilder(languageId, code, "inline_source").build();
            predicate = inlineSnippet.getLocationPredicate();
            resultVerifier = verifier;
        }

        @Override
        public ExecutionEventNode create(EventContext context) {
            if (!isEntered()) {
                if (predicate == null || canRunAt(context.getInstrumentedSourceSection())) {
                    return new InlineScriptNode(context);
                }
            }
            return null;
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
                    snippetExecuted = true;
                }
                enter();
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
                } finally {
                    leave();
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

    private static class NodePropertyChecker implements ExecutionEventListener {

        public void onEnter(EventContext context, VirtualFrame frame) {
            Node instrumentedNode = context.getInstrumentedNode();
            RootNode root = instrumentedNode.getRootNode();
            checkRootNames(root);
        }

        @TruffleBoundary
        private static void checkRootNames(RootNode root) {
            Assert.assertNotNull(root);
            root.getName(); // should not crash
            root.getQualifiedName(); // should not crash
        }

        public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
        }

        public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
        }
    }

    private static class RootFrameChecker implements ExecutionEventListener {

        @Override
        public void onEnter(EventContext context, VirtualFrame frame) {
            checkFrameIsEmpty(context, frame.materialize());
        }

        @TruffleBoundary
        private static void checkFrameIsEmpty(EventContext context, MaterializedFrame frame) {
            Node node = context.getInstrumentedNode();
            if (!hasParentRootTag(node) &&
                            node.getRootNode().getFrameDescriptor() == frame.getFrameDescriptor()) {
                Object defaultValue = frame.getFrameDescriptor().getDefaultValue();
                for (FrameSlot slot : frame.getFrameDescriptor().getSlots()) {
                    Assert.assertEquals("Top-most nodes tagged with RootTag should have clean frames.", defaultValue, frame.getValue(slot));
                }
            }
        }

        private static boolean hasParentRootTag(Node node) {
            Node parent = node.getParent();
            if (parent == null) {
                return false;
            }
            if (parent instanceof InstrumentableNode && ((InstrumentableNode) parent).hasTag(RootTag.class)) {
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

    private static class LibraryChecker implements ExecutionEventNodeFactory {

        @Override
        public ExecutionEventNode create(EventContext context) {
            return new LibraryCheckerNode(context);
        }

        private static final class LibraryCheckerNode extends ExecutionEventNode {

            private final EventContext context;
            @CompilationFinal private boolean checked;

            LibraryCheckerNode(EventContext context) {
                this.context = context;
            }

            @Override
            protected void onReturnValue(VirtualFrame frame, Object result) {
                if (!checked) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    checked = true;
                    check();
                }
            }

            @Override
            protected void onReturnExceptional(VirtualFrame frame, Throwable exception) {
                if (!checked) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    checked = true;
                    check();
                }
            }

            private void check() {
                Node rootNode = context.getInstrumentedNode();
                new InstrumentableNodeInLibrary().visit(rootNode);
            }

            private static final class InstrumentableNodeInLibrary implements NodeVisitor {

                private int inLibrary;

                @Override
                public boolean visit(Node node) {
                    preEnter(node);
                    checkInstrumentable(node);
                    NodeUtil.forEachChild(node, this);
                    postEnter(node);
                    return true;
                }

                private void checkInstrumentable(Node node) {
                    if (inLibrary > 0 && node instanceof InstrumentableNode && ((InstrumentableNode) node).isInstrumentable()) {
                        Assert.assertFalse("Node \"" + node + "\" of class " + node.getClass() + " is instrumentable, but used in Library. " + node.getSourceSection() + "\n" +
                                        "Library implementation nodes ought not to be instrumentable, because library may be rewritten to uncached and therefore no longer be able to be instrumented.\n" +
                                        "InstrumentableNode.isInstrumentable() should return false in the context of a Library.", true);
                    }
                }

                private void preEnter(Node node) {
                    if (node instanceof Library) {
                        inLibrary++;
                    }
                }

                private void postEnter(Node node) {
                    if (node instanceof Library) {
                        inLibrary--;
                    }
                }
            }
        }
    }

    private static final class EmptyExecutionEventListener implements ExecutionEventListener {
        @Override
        public void onEnter(EventContext context, VirtualFrame frame) {
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

        static NodeSupport nodesAccess() {
            return ACCESSOR.nodeSupport();
        }

        static Accessor.InstrumentSupport instrumentAccess() {
            return ACCESSOR.instrumentSupport();
        }
    }
}
