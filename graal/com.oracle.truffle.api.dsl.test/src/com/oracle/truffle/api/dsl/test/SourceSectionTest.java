/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import static com.oracle.truffle.api.dsl.test.TestHelper.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import org.junit.*;
import org.junit.experimental.theories.*;
import org.junit.runner.*;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.dsl.test.SourceSectionTestFactory.SourceSection0Factory;
import com.oracle.truffle.api.dsl.test.SourceSectionTestFactory.SourceSection1Factory;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ArgumentNode;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.TestRootNode;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;

@RunWith(Theories.class)
public class SourceSectionTest {

    @DataPoints public static final int[] data = new int[]{1, 2, 3, 4};

    @Theory
    public void testSourceSections(int value0, int value1, int value2) {
        TestRootNode<SourceSection0> root = createRoot(SourceSection0Factory.getInstance());
        SourceSection section = new NullSourceSection("a", "b");
        root.getNode().assignSourceSection(section);
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
    static class SourceSection0 extends ValueNode {

        boolean isOne(int a) {
            return a == 1;
        }

        boolean isTwo(int a) {
            return a == 2;
        }

        boolean isThree(int a) {
            return a == 3;
        }

        @Specialization(guards = "isOne")
        int do1(int a) {
            return a;
        }

        @Specialization(guards = "isTwo")
        int do2(int a) {
            return a;
        }

        @Specialization(guards = "isThree")
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
        SourceSection section = new NullSourceSection("a", "b");
        TestRootNode<SourceSection1> root = createRootPrefix(SourceSection1Factory.getInstance(), true, section);
        expectSourceSection(root.getNode(), section);
        assertThat((int) executeWith(root, 1), is(1));
        expectSourceSection(root.getNode(), section);
    }

    @NodeChild("a")
    static class SourceSection1 extends ValueNode {

        public SourceSection1(SourceSection section) {
            super(section);
        }

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
