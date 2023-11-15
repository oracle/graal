/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test.examples;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.examples.GuidelinesExamples.MyNodeHandleOther.Handler1;
import com.oracle.truffle.api.dsl.test.examples.GuidelinesExamples.MyNodeHandleOther.Handler2;
import com.oracle.truffle.api.dsl.test.examples.GuidelinesExamplesFactory.MyNodeV2NodeGen;
import com.oracle.truffle.api.dsl.test.examples.GuidelinesExamplesFactory.Node1NodeGen;
import com.oracle.truffle.api.dsl.test.examples.GuidelinesExamplesFactory.Node1V3NodeGen;
import com.oracle.truffle.api.dsl.test.examples.GuidelinesExamplesFactory.Node2NodeGen;
import com.oracle.truffle.api.dsl.test.examples.GuidelinesExamplesFactory.NodeWithDuplicatedSpecializationsNodeGen;
import com.oracle.truffle.api.dsl.test.examples.GuidelinesExamplesFactory.NodeWithoutDuplicatedSpecializationsNodeGen;
import com.oracle.truffle.api.nodes.Node;

public class GuidelinesExamples {
    // Examples described in DSLGuidelines.md

    // ----------------------
    // Inheritance

    @GenerateInline(false)
    abstract static class MyBaseNode extends Node {
        abstract int execute(Object o);

        @Specialization(guards = "arg == 0")
        int doZero(@SuppressWarnings("unused") int arg) {
            return 1;
        }

        @Specialization(guards = "arg != 0")
        int doInt(@SuppressWarnings("unused") int arg) {
            return 2;
        }

        @Specialization
        int doOther(@SuppressWarnings("unused") Object o) {
            throw new AbstractMethodError();
        }
    }

    @GenerateInline(false)
    abstract static class Node1 extends MyBaseNode {
        @Specialization
        @Override
        final int doOther(Object o) {
            return 42;
        }
    }

    @GenerateInline(false)
    abstract static class Node2 extends MyBaseNode {
        @Specialization
        @Override
        final int doOther(Object o) {
            return -1;
        }
    }

    abstract static class MyNodeHandleOther extends Node {
        abstract int execute(Object o);

        static final class Handler1 extends MyNodeHandleOther {
            @Override
            int execute(Object o) {
                return 42;
            }
        }

        static final class Handler2 extends MyNodeHandleOther {
            @Override
            int execute(Object o) {
                return -1;
            }
        }
    }

    abstract static class MyNodeV2 extends Node {
        @Child MyNodeHandleOther otherHandler;

        MyNodeV2(MyNodeHandleOther otherHandler) {
            this.otherHandler = otherHandler;
        }

        abstract int execute(Object o);

        @Specialization(guards = "arg == 0")
        int doZero(@SuppressWarnings("unused") int arg) {
            return 1;
        }

        @Specialization(guards = "arg != 0")
        int doInt(@SuppressWarnings("unused") int arg) {
            return 2;
        }

        @Specialization
        int doOther(Object o) {
            return otherHandler.execute(o);
        }
    }

    @GenerateCached(false)
    @GenerateInline
    abstract static class MyCommonNode extends Node {
        abstract int execute(Node node, Object o);

        @Specialization(guards = "arg == 0")
        int doZero(@SuppressWarnings("unused") int arg) {
            return 1;
        }

        @Specialization(guards = "arg != 0")
        int doInt(@SuppressWarnings("unused") int arg) {
            return 2;
        }
    }

    @GenerateInline(false)
    abstract static class Node1V3 extends Node {
        abstract int execute(Object o);

        @Specialization
        int doInts(int o,
                        @Cached MyCommonNode node) {
            return node.execute(this, o);
        }

        @Specialization
        int doOther(@SuppressWarnings("unused") Object o) {
            return 42;
        }
    }

    @Test
    public void testInheritance() {
        Node1 node1 = Node1NodeGen.create();
        Node2 node2 = Node2NodeGen.create();
        assertEquals(1, node1.execute(0));
        assertEquals(1, node2.execute(0));
        assertEquals(42, node1.execute("object"));
        assertEquals(-1, node2.execute("object"));

        MyNodeV2 node1v2 = MyNodeV2NodeGen.create(new Handler1());
        MyNodeV2 node2v2 = MyNodeV2NodeGen.create(new Handler2());
        assertEquals(1, node1v2.execute(0));
        assertEquals(1, node2v2.execute(0));
        assertEquals(42, node1v2.execute("object"));
        assertEquals(-1, node2v2.execute("object"));

        Node1V3 node1v3 = Node1V3NodeGen.create();
        assertEquals(1, node1v3.execute(0));
        assertEquals(42, node1v3.execute("object"));
    }

    // ----------------------
    // Duplicated specializations

    public static final class MyObject1 {
    }

    public static final class MyObject2 {
    }

    @GenerateInline(false)
    abstract static class NodeWithDuplicatedSpecializations extends Node {
        abstract int execute(Object o);

        @Specialization
        int doObj1(MyObject1 o) {
            return helper(o);
        }

        @Specialization
        int doObj2(MyObject2 o) {
            return helper(o);
        }

        @Fallback
        int doFallback(@SuppressWarnings("unused") Object o) {
            return 3;
        }

        int helper(Object o) {
            return o instanceof MyObject1 ? 1 : 2;
        }

        // ... more @Specializations
    }

    // solution:
    @GenerateInline
    @GenerateCached(false)
    abstract static class GuardNode extends Node {
        abstract boolean execute(Node inliningTarget, Object o);

        @Specialization
        static boolean doObj1(@SuppressWarnings("unused") MyObject1 o) {
            return true;
        }

        @Specialization
        static boolean doObj2(@SuppressWarnings("unused") MyObject2 o) {
            return true;
        }

        @Specialization
        static boolean doString(@SuppressWarnings("unused") String o) {
            return false;
        }
    }

    @GenerateInline(false)
    abstract static class NodeWithoutDuplicatedSpecializations extends Node {
        abstract int execute(Object o);

        @Specialization(guards = "guardNode.execute(this, o)", limit = "1")
        int doObj(Object o,
                        @SuppressWarnings("unused") @Cached GuardNode guardNode) {
            return helper(o);
        }

        @Specialization
        int doString(@SuppressWarnings("unused") String o) {
            return 3;
        }

        int helper(Object o) {
            return o instanceof MyObject1 ? 1 : 2;
        }

        // ...other @Specializations
    }

    @Test
    public void testSpecializationsDuplication() {
        var withDups = NodeWithDuplicatedSpecializationsNodeGen.create();
        var woDups = NodeWithoutDuplicatedSpecializationsNodeGen.create();

        assertEquals(1, withDups.execute(new MyObject1()));
        assertEquals(1, woDups.execute(new MyObject1()));

        assertEquals(2, withDups.execute(new MyObject2()));
        assertEquals(2, woDups.execute(new MyObject2()));

        assertEquals(3, withDups.execute("other"));
        assertEquals(3, woDups.execute("other"));
    }
}
