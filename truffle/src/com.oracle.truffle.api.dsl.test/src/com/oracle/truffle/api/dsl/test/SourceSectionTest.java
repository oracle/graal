/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
