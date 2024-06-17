/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Field;

import org.junit.Test;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.StatePackingTestFactory.Test64NodeGen;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;

@SuppressWarnings({"truffle-inlining", "truffle-neverdefault", "truffle-sharing"})
@DisableStateBitWidthModfication
public class StatePackingTest extends AbstractPolyglotTest {

    @GenerateInline
    @SuppressWarnings("unused")
    @GenerateCached(false)
    abstract static class OneNode extends Node {

        abstract int execute(Node node, int v);

        @Specialization(guards = "v == 1")
        static int s0(Node node, int v) {
            return v;
        }

    }

    @GenerateInline
    @SuppressWarnings("unused")
    @GenerateCached(false)
    abstract static class TwoNode extends Node {

        abstract int execute(Node node, int v);

        @Specialization(guards = "v == 0")
        static int s0(Node node, int v) {
            return v;
        }

        @Specialization(guards = "v == 1")
        static int s1(Node node, int v) {
            return v;
        }

    }

    @GenerateInline
    @GenerateCached(false)
    @SuppressWarnings("unused")
    abstract static class ThreeNode extends Node {

        abstract int execute(Node node, int v);

        @Specialization(guards = "v == 0")
        static int s0(Node node, int v) {
            return 1;
        }

        @Specialization(guards = "v == 1")
        static int s1(Node node, int v) {
            return 2;
        }

        @Specialization(guards = "v == 2")
        static int s2(Node node, int v) {
            return 3;
        }

    }

    @GenerateInline
    @GenerateCached(false)
    @SuppressWarnings("unused")
    abstract static class TwelveNode extends Node {

        abstract int execute(Node node, int v);

        @Specialization(guards = "v == 0")
        static int s0(Node node, int v, @Cached ThreeNode three) {
            three.execute(node, v);
            return 1;
        }

        @Specialization(guards = "v == 1")
        static int s1(Node node, int v, @Cached ThreeNode three) {
            three.execute(node, v);
            return 2;
        }

        @Specialization(guards = "v == 2")
        static int s2(Node node, int v, @Cached ThreeNode three) {
            three.execute(node, v);
            return 3;
        }

    }

    @SuppressWarnings("unused")
    abstract static class Test64Node extends Node {

        abstract int execute(int v);

        @Specialization
        int s0(int v,
                        @Cached TwelveNode c0,
                        @Cached OneNode c4,
                        @Cached OneNode c5,
                        @Cached TwelveNode c2,
                        @Cached OneNode c6,
                        @Cached TwelveNode c1,
                        @Cached OneNode c7,
                        @Cached OneNode c8,
                        @Cached ThreeNode c9,
                        @Cached OneNode c10,
                        @Cached OneNode c11,
                        @Cached TwelveNode c3,
                        @Cached OneNode c12,
                        @Cached TwoNode c13,
                        @Cached ThreeNode c14) {
            return 1;
        }

    }

    /*
     * Tests that a node can pack all these cached values in exactly two ints of 32 bits.
     */
    @Test
    public void testPacking() {
        int fieldCount = 0;
        for (Field field : Test64NodeGen.class.getDeclaredFields()) {
            if (field.getName().startsWith("state")) {
                fieldCount++;
            }
        }
        assertEquals(2, fieldCount);
    }

}
