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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

public class MaterializationAssertionsViolationTest extends AbstractPolyglotTest {
    private static final Pattern NODE_PATTERN = Pattern.compile("^S(\\d+)E(\\d+)\\((.*)\\)$");

    @Rule public ExpectedException expectedException = ExpectedException.none();

    @GenerateWrapper
    static class CustomMaterializeNode extends Node implements InstrumentableNode {

        private final ProxyLanguage language;
        private final SourceSection sourceSection;
        private final int sCount;
        private final int eCount;
        @Node.Children protected CustomMaterializeNode[] children;

        CustomMaterializeNode(ProxyLanguage language, com.oracle.truffle.api.source.Source source, int sCount, int eCount, CustomMaterializeNode[] children) {
            this.language = language;
            this.sCount = sCount;
            this.eCount = eCount;
            this.sourceSection = source.createSection(1);
            this.children = children;
        }

        CustomMaterializeNode(CustomMaterializeNode copy) {
            this(copy, copy.children);
        }

        CustomMaterializeNode(CustomMaterializeNode copy, CustomMaterializeNode[] children) {
            this.language = copy.language;
            this.sCount = copy.sCount;
            this.eCount = copy.eCount;
            this.sourceSection = copy.sourceSection;
            this.children = children;
        }

        @Override
        public boolean isInstrumentable() {
            return true;
        }

        @Override
        public WrapperNode createWrapper(ProbeNode probe) {
            return new CustomMaterializeNodeWrapper(this, this, probe);
        }

        @Override
        public boolean hasTag(Class<? extends Tag> tag) {
            return tag.equals(StandardTags.StatementTag.class);
        }

        @Override
        public SourceSection getSourceSection() {
            return sourceSection;
        }

        @ExplodeLoop
        public int execute(@SuppressWarnings("unused") VirtualFrame frame) {
            int sum = 0;
            for (CustomMaterializeNode node : children) {
                sum += node.execute(frame);
            }
            return sum + 1;
        }

        @Override
        public InstrumentableNode materializeInstrumentableNodes(Set<Class<? extends Tag>> materializedTags) {
            int newSCount = sCount;
            int newECount = eCount;
            if (materializedTags.contains(StandardTags.StatementTag.class)) {
                newSCount--;
            }
            if (materializedTags.contains(StandardTags.ExpressionTag.class)) {
                newECount--;
            }
            if (newSCount < 0 || newECount < 0) {
                return this;
            }
            return new CustomMaterializeNode(this.language, this.sourceSection.getSource(), Math.max(0, newSCount), Math.max(0, newECount), children);
        }
    }

    @Before
    public void setup() {
        setupEnv(Context.create(), new ProxyLanguage() {

            private final List<CallTarget> targets = new LinkedList<>(); // To prevent from GC

            CustomMaterializeNode[] parseNodes(String code, com.oracle.truffle.api.source.Source completeSource) {
                int depth = 0;
                List<CustomMaterializeNode> ret = new ArrayList<>();
                StringBuilder current = new StringBuilder();
                for (int i = 0; i < code.length(); i++) {
                    switch (code.charAt(i)) {
                        case ' ':
                        case '\t':
                        case '\r':
                        case '\n':
                            break;
                        case ',':
                            if (depth == 0) {
                                ret.add(parseNode(current.toString(), completeSource));
                                current.setLength(0);
                            } else {
                                current.append(code.charAt(i));
                            }
                            break;
                        case '(':
                            depth++;
                            current.append(code.charAt(i));
                            break;
                        case ')':
                            depth--;
                            current.append(code.charAt(i));
                            break;
                        default:
                            current.append(code.charAt(i));
                    }
                }
                if (depth != 0) {
                    throw new RuntimeException("Mismatched parentheses for " + code + "!");
                } else if (current.length() > 0) {
                    ret.add(parseNode(current.toString(), completeSource));
                }
                return ret.toArray(new CustomMaterializeNode[0]);
            }

            CustomMaterializeNode parseNode(String code, com.oracle.truffle.api.source.Source completeSource) {
                Matcher matcher = NODE_PATTERN.matcher(code);
                if (!matcher.find()) {
                    throw new RuntimeException("Node " + code + " cannot be parsed!");
                }
                return new CustomMaterializeNode(languageInstance, completeSource, Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
                                parseNodes(matcher.group(3), completeSource));
            }

            @Override
            protected CallTarget parse(ParsingRequest request) throws Exception {
                com.oracle.truffle.api.source.Source source = request.getSource();
                String code = source.getCharacters().toString();
                CallTarget target = Truffle.getRuntime().createCallTarget(new RootNode(languageInstance) {

                    @Node.Children private CustomMaterializeNode[] children = parseNodes(code, source);

                    @Override
                    @ExplodeLoop
                    public Object execute(VirtualFrame frame) {
                        int sum = 0;
                        for (CustomMaterializeNode node : children) {
                            sum += node.execute(frame);
                        }
                        return sum;
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
    public void testMultipleMaterializationAssertion() {
        expectedException.expect(PolyglotException.class);
        expectedException.expectMessage("java.lang.AssertionError: Node must not be materialized multiple times for the same set of tags!");
        Source source = Source.create(ProxyLanguage.ID, "S2E0()");
        ExecutionEventListener listener = createListener();
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class).build(), listener);
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class).build(), listener);
        context.eval(source);
    }

    /**
     * The assertion that a node must not be further materialized if it has already seen all the
     * materializeTags used for new instrumentation is not satisfied, but no error is thrown during
     * this test because on first execution the seen tags are not recorded.
     */
    @Test
    public void testGradualMaterializationAssertionUncaught() {
        Source source = Source.create(ProxyLanguage.ID, "S1E2()");
        ExecutionEventListener listener = createListener();
        EventBinding<ExecutionEventListener> binding = instrumentEnv.getInstrumenter().attachExecutionEventListener(
                        SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class, StandardTags.ExpressionTag.class).build(), listener);
        context.eval(source);
        binding.dispose();
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.ExpressionTag.class).build(), listener);
    }

    @Test
    public void testGradualMaterializationAssertionCaught() {
        expectedException.expect(AssertionError.class);
        expectedException.expectMessage("There should always be some new materialize tag!");
        Source source = Source.create(ProxyLanguage.ID, "S1E2()");
        ExecutionEventListener listener = createListener();
        /*
         * Execute first so that for the subsequent instrumentation, the materializeTags are
         * recorded in retired node reference, and during the second instrumentation, we find out
         * that the node should not materialize further, but it does.
         */
        context.eval(source);
        EventBinding<ExecutionEventListener> binding = instrumentEnv.getInstrumenter().attachExecutionEventListener(
                        SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class, StandardTags.ExpressionTag.class).build(), listener);
        binding.dispose();
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.ExpressionTag.class).build(), listener);
    }

    @Test
    public void testNewTreeMaterializationAssertion() {
        expectedException.expect(PolyglotException.class);
        expectedException.expectMessage("java.lang.AssertionError: New tree should be fully materialized!");
        Source source = Source.create(ProxyLanguage.ID, "S1E0(S1E0())");
        ExecutionEventListener listener = createListener();
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class).build(), listener);
        context.eval(source);
    }

    private static ExecutionEventListener createListener() {
        return new ExecutionEventListener() {
            @Override
            public void onEnter(EventContext c, VirtualFrame frame) {

            }

            @Override
            public void onReturnValue(EventContext c, VirtualFrame frame, Object result) {

            }

            @Override
            public void onReturnExceptional(EventContext c, VirtualFrame frame, Throwable exception) {

            }
        };
    }
}
