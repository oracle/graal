/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test;

import static com.oracle.truffle.api.dsl.test.TestHelper.createRoot;
import static com.oracle.truffle.api.dsl.test.TestHelper.createRootPrefix;
import static com.oracle.truffle.api.dsl.test.TestHelper.executeWith;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertThat;

import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.CreateCast;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ArgumentNode;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.TestRootNode;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

@RunWith(Theories.class)
public class SourceSectionTest {

    @DataPoints public static final int[] data = new int[]{1, 2, 3, 4};

    @Theory
    public void testSourceSections(int value0, int value1, int value2) {
        TestRootNode<MutableSourceSectionNode> root = createRoot(SourceSectionTestFactory.MutableSourceSectionNodeFactory.getInstance());
        SourceSection section = Source.newBuilder("", "", "a").build().createUnavailableSection();
        root.getNode().changeSourceSection(section);
        expectSourceSection(root.getNode(), section);
        assertThat((int) executeWith(root, value0), is(value0));
        expectSourceSection(root.getNode(), section);
        assertThat((int) executeWith(root, value1), is(value1));
        expectSourceSection(root.getNode(), section);
        assertThat((int) executeWith(root, value2), is(value2));
        expectSourceSection(root.getNode(), section);
    }

    private static void expectSourceSection(Node root, SourceSection section) {
        assertThat(root.getSourceSection(), is(sameInstance(section)));
        for (Node child : root.getChildren()) {
            if (child instanceof ArgumentNode) {
                continue;
            }
            if (child != null) {
                expectSourceSection(child, section);
            }
        }
    }

    @NodeChild("a")
    static class MutableSourceSectionNode extends ValueNode {
        // BEGIN: MutableSourceSectionNode
        @CompilerDirectives.CompilationFinal private SourceSection section;

        final void changeSourceSection(SourceSection sourceSection) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            this.section = sourceSection;
        }

        @Override
        public SourceSection getSourceSection() {
            return section;
        }

        // END: MutableSourceSectionNode

        @Specialization(guards = "a == 1")
        int do1(int a) {
            return a;
        }

        @Specialization(guards = "a == 2")
        int do2(int a) {
            return a;
        }

        @Specialization(guards = "a == 3")
        int do3(int a) {
            return a;
        }

        @Fallback
        Object do4(Object a) {
            return a; // the generic answer to all questions
        }
    }

    @Test
    public void testCreateCast() {
        SourceSection section = Source.newBuilder("", "", "a").build().createUnavailableSection();
        TestRootNode<NodeWithFixedSourceSection> root = createRootPrefix(SourceSectionTestFactory.NodeWithFixedSourceSectionFactory.getInstance(), true, section);
        expectSourceSection(root.getNode(), section);
        assertThat((int) executeWith(root, 1), is(1));
        expectSourceSection(root.getNode(), section);
    }

    @NodeChild("a")
    static class NodeWithFixedSourceSection extends ValueNode {
        // BEGIN: NodeWithFixedSourceSection
        private final SourceSection section;

        NodeWithFixedSourceSection(SourceSection section) {
            this.section = section;
        }

        @Override
        public SourceSection getSourceSection() {
            return section;
        }

        // END: NodeWithFixedSourceSection

        @CreateCast("a")
        public ValueNode cast(ValueNode node) {
            assert getSourceSection() != null;
            return node;
        }

        @Specialization
        int do0(int a) {
            return a;
        }

    }
}
