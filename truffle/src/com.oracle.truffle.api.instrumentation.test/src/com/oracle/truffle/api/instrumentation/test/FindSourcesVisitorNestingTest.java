/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation.test;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.SourceFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

public class FindSourcesVisitorNestingTest extends AbstractPolyglotTest {
    @GenerateWrapper
    static class DummyInstrumentableNode extends Node implements InstrumentableNode {
        private final SourceSection sourceSection;

        DummyInstrumentableNode(SourceSection sourceSection) {
            this.sourceSection = sourceSection;
        }

        DummyInstrumentableNode(DummyInstrumentableNode copy) {
            this.sourceSection = copy.sourceSection;
        }

        @Override
        public boolean isInstrumentable() {
            return true;
        }

        @Override
        public boolean hasTag(Class<? extends Tag> tag) {
            return tag.equals(StandardTags.StatementTag.class);
        }

        @Override
        public WrapperNode createWrapper(ProbeNode probe) {
            return new DummyInstrumentableNodeWrapper(this, this, probe);
        }

        @Override
        public SourceSection getSourceSection() {
            return sourceSection;
        }

        public boolean execute(@SuppressWarnings("unused") VirtualFrame frame) {
            if (getParent() instanceof WrapperNode) {
                return true;
            } else {
                return false;
            }
        }
    }

    @GenerateWrapper
    static class CreateCallTargetOnMaterializeNode extends Node implements InstrumentableNode {
        @Child private DummyInstrumentableNode dummyInstrumentableNode;

        private final TruffleInstrument.Env env;
        private final ProxyLanguage language;
        private final SourceSection sourceSection;
        private final boolean materialized;

        CreateCallTargetOnMaterializeNode(TruffleInstrument.Env env, ProxyLanguage language, com.oracle.truffle.api.source.Source source, boolean materialized) {
            this.env = env;
            this.language = language;
            this.materialized = materialized;
            this.sourceSection = source.createSection(1);
            this.dummyInstrumentableNode = new DummyInstrumentableNode(this.sourceSection);
        }

        CreateCallTargetOnMaterializeNode(CreateCallTargetOnMaterializeNode copy) {
            this.env = copy.env;
            this.language = copy.language;
            this.materialized = copy.materialized;
            this.sourceSection = copy.sourceSection;
            this.dummyInstrumentableNode = copy.dummyInstrumentableNode;
        }

        @Override
        public boolean isInstrumentable() {
            return true;
        }

        @Override
        public WrapperNode createWrapper(ProbeNode probe) {
            return new CreateCallTargetOnMaterializeNodeWrapper(this, this, probe);
        }

        @Override
        public boolean hasTag(Class<? extends Tag> tag) {
            return tag.equals(StandardTags.StatementTag.class);
        }

        @Override
        public SourceSection getSourceSection() {
            return sourceSection;
        }

        @CompilerDirectives.TruffleBoundary
        private void attachListeners() {
            env.getInstrumenter().attachLoadSourceListener(SourceFilter.ANY, event -> {
            }, true);
            env.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.ANY, new ExecutionEventListener() {
                @Override
                public void onEnter(EventContext c, VirtualFrame frame) {

                }

                @Override
                public void onReturnValue(EventContext c, VirtualFrame frame, Object result) {

                }

                @Override
                public void onReturnExceptional(EventContext c, VirtualFrame frame, Throwable exception) {

                }
            });
        }

        public boolean execute(@SuppressWarnings("unused") VirtualFrame frame) {
            attachListeners();
            return dummyInstrumentableNode.execute(frame);
        }

        @Override
        public InstrumentableNode materializeInstrumentableNodes(Set<Class<? extends Tag>> materializedTags) {
            if (materialized) {
                return this;
            }

            Truffle.getRuntime().createCallTarget(new RootNode(this.language) {
                @Override
                public Object execute(VirtualFrame frame) {
                    return 0;
                }

                @Override
                public SourceSection getSourceSection() {
                    return CreateCallTargetOnMaterializeNode.this.sourceSection;
                }

            });
            return new CreateCallTargetOnMaterializeNode(this.env, this.language, this.sourceSection.getSource(), true);
        }
    }

    @Before
    public void setup() {
        setupEnv(Context.create(), new ProxyLanguage() {
            private final List<CallTarget> targets = new LinkedList<>(); // To prevent from GC

            @Override
            protected CallTarget parse(ParsingRequest request) throws Exception {
                com.oracle.truffle.api.source.Source source = request.getSource();
                CallTarget target = Truffle.getRuntime().createCallTarget(new RootNode(languageInstance) {

                    @Node.Child private CreateCallTargetOnMaterializeNode child = new CreateCallTargetOnMaterializeNode(instrumentEnv, languageInstance, source, false);

                    @Override
                    public Object execute(VirtualFrame frame) {
                        return child.execute(frame);
                    }

                    @Override
                    public SourceSection getSourceSection() {
                        return source.createSection(1);
                    }

                });
                targets.add(target);
                return target;
            }
        });
    }

    @Test
    public void testFindSourcesVisitorNesting() {
        Source source = Source.create(ProxyLanguage.ID, "");
        Assert.assertTrue("Retired dummy instrumentable node was not added an instrumentation wrapper!", context.eval(source).asBoolean());
    }
}
