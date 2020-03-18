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

import static org.junit.Assert.assertNotNull;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

public class InstrumentationCompilerTest extends PartialEvaluationTest {

    static final SourceSection DUMMY_SECTION = com.oracle.truffle.api.source.Source.newBuilder(ProxyLanguage.ID, "", "").name("").build().createSection(0, 0);

    private ProxyLanguage language;

    @Test
    public void testSaveChildValues() {
        setup(SavingInstrument.NAME_AND_ID);

        testEquals(new IntLiteral(42), new IntLiteral(42), 1);
        testEquals(new IntLiteral(42), new AddNode(new IntLiteral(21), new IntLiteral(21)), 3);
        testEquals(new IntLiteral(42), new AddNode(new IntLiteral(21), new AddNode(new IntLiteral(10), new IntLiteral(11))), 5);
    }

    private void setup(String enabledInstrument) {
        setupContext(Context.newBuilder().option(enabledInstrument, "true"));
        getContext().initialize(ProxyLanguage.ID);
        this.language = ProxyLanguage.getCurrentLanguage();
    }

    private void testEquals(InstrumentationCompilerTestBaseNode expected, InstrumentationCompilerTestBaseNode baseNode, int expectedInstrumentation) {
        RootNode expectedRootNode = createRoot(expected, false);
        RootNode instrumentedRootNode = createRoot(baseNode, true);
        assertPartialEvalEquals(expectedRootNode, instrumentedRootNode, new Object[0]);

        assertNotNull(instrumentedRootNode.getCallTarget());
        assertNotNull(expectedRootNode.getCallTarget());
        Assert.assertEquals(0, NodeUtil.countNodes(expectedRootNode, (n) -> n instanceof InstrumentationCompilerTestBaseNodeWrapper));
        Assert.assertEquals(expectedInstrumentation, NodeUtil.countNodes(instrumentedRootNode, (n) -> n instanceof InstrumentationCompilerTestBaseNodeWrapper));

    }

    private RootNode createRoot(InstrumentationCompilerTestBaseNode node, boolean allowInstrumentation) {
        return new RootNode(language) {
            @Child InstrumentationCompilerTestBaseNode child = node;

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
                return child.execute(frame);
            }
        };
    }

    static class AddNode extends InstrumentationCompilerTestBaseNode {

        @Child InstrumentationCompilerTestBaseNode child0;
        @Child InstrumentationCompilerTestBaseNode child1;

        AddNode(InstrumentationCompilerTestBaseNode child0, InstrumentationCompilerTestBaseNode child1) {
            this.child0 = child0;
            this.child1 = child1;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return (int) child0.execute(frame) + (int) child1.execute(frame);
        }

    }

    static class IntLiteral extends InstrumentationCompilerTestBaseNode {

        private final int value;

        IntLiteral(int value) {
            this.value = value;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return value;
        }

    }

    @GenerateWrapper
    abstract static class InstrumentationCompilerTestBaseNode extends Node implements InstrumentableNode {

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
        public WrapperNode createWrapper(ProbeNode probe) {
            return new InstrumentationCompilerTestBaseNodeWrapper(this, probe);
        }

        @Override
        public boolean hasTag(Class<? extends Tag> tag) {
            return tag == ExpressionTag.class;
        }

    }

    @Registration(id = SavingInstrument.NAME_AND_ID, name = SavingInstrument.NAME_AND_ID)
    public static final class SavingInstrument extends TruffleInstrument {

        public static final String NAME_AND_ID = "InstrumentationCompilerTest_SavingInstrument";

        @Option(name = "", category = OptionCategory.EXPERT, help = "Enable the instrument.") //
        static final OptionKey<Boolean> EnableInstrument = new OptionKey<>(false);

        @Override
        protected void onCreate(Env env) {
            if (env.getOptions().get(EnableInstrument)) {
                env.getInstrumenter().attachExecutionEventFactory(
                                SourceSectionFilter.newBuilder().tagIs(StandardTags.ExpressionTag.class).build(),
                                SourceSectionFilter.newBuilder().tagIs(StandardTags.ExpressionTag.class).build(),
                                context -> new SavingNode());
            }
        }

        private static final class SavingNode extends ExecutionEventNode {
            @Override
            protected void onInputValue(VirtualFrame frame, EventContext inputContext, int inputIndex, Object inputValue) {
                saveInputValue(frame, inputIndex, inputValue);
            }

            @Override
            @ExplodeLoop
            protected void onReturnValue(VirtualFrame frame, Object result) {
                final Object[] savedInputs = getSavedInputValues(frame);
                for (int i = 0; i < getInputCount(); i++) {
                    // this check should get eliminated
                    if (!(savedInputs[i] instanceof Integer)) {
                        CompilerDirectives.transferToInterpreter();
                    }
                }
            }
        }

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new SavingInstrumentOptionDescriptors();
        }

    }

}
