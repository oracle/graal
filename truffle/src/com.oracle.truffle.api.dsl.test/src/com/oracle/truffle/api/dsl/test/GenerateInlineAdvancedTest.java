/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Test;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.GenerateInlineAdvancedTestFactory.UseInlinableNodeGen;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;

@SuppressWarnings({"truffle-sharing"})
public class GenerateInlineAdvancedTest extends AbstractPolyglotTest {

    @Test
    public void test01() {
        UseInlinableNode node = UseInlinableNodeGen.create();
        int sum = 0;
        for (int i = 0; i < UseInlinableNode.SPECIALIZATIONS; i++) {
            sum += node.execute(i);
        }
        assertEquals(301, sum);
    }

    @GenerateInline(false)
    abstract static class UseInlinableNode extends Node {

        static final int SPECIALIZATIONS = 2;

        abstract int execute(Object parameter);

        @Specialization(guards = "value == 0")
        int s0(int value, @Cached(inline = true) InlinableNode inlining) {
            int result = value;
            for (int i = 0; i < InlinableNode.SPECIALIZATIONS; i++) {
                result += inlining.execute(this, i);
            }
            return result;
        }

        @Specialization(guards = "value == 1")
        int s1(int value, @Cached(inline = false) InlinableNode inlining) {
            int result = value;
            for (int i = 0; i < InlinableNode.SPECIALIZATIONS; i++) {
                result += inlining.execute(this, i);
            }
            return result;
        }

    }

    @GenerateInline
    @SuppressWarnings("unused")
    abstract static class InlinableNode extends Node {

        static final int SPECIALIZATIONS = 10;

        abstract int execute(Node node, int value);

        @Specialization(guards = "value == 0")
        int s0(int value) {
            return value;
        }

        @Specialization(guards = "value == 1")
        int s1(@NeverDefault int value, @Cached("value") int cachedValue) {
            return cachedValue;
        }

        @Specialization(guards = "value == 2")
        int s2(int value, @Cached("value") Object cachedValue) {
            return value;
        }

        @Specialization(guards = "value == 3")
        static int s3(Node node, int value, @Cached TransitiveInlinableNode inlining) {
            int result = value;
            for (int i = 0; i < TransitiveInlinableNode.SPECIALIZATIONS; i++) {
                result += inlining.execute(node, i);
            }
            return result;
        }

        @Specialization(guards = {"value <= 6", "value == cachedValue"}, limit = "3")
        static int s4(Node node, int value, @Cached("value") int cachedValue, @Cached TransitiveInlinableNode inlining) {
            int result = value;
            for (int i = 0; i < TransitiveInlinableNode.SPECIALIZATIONS; i++) {
                result += inlining.execute(node, i);
            }
            return result;
        }

        @Specialization(guards = {"value <= 9", "value == cachedValue"}, limit = "3", unroll = 3)
        static int s5(Node node, int value, @Cached("value") int cachedValue, @Cached TransitiveInlinableNode inlining) {
            int result = value;
            for (int i = 0; i < TransitiveInlinableNode.SPECIALIZATIONS; i++) {
                result += inlining.execute(node, i);
            }
            return result;
        }

    }

    @GenerateInline
    @SuppressWarnings("unused")
    abstract static class TransitiveInlinableNode extends Node {

        static final int SPECIALIZATIONS = 6;

        abstract int execute(Node node, int value);

        @Specialization(guards = "value == 0")
        int s0(int value) {
            return value;
        }

        @Specialization(guards = "value == 1")
        int s1(@NeverDefault int value, @Cached("value") int cachedValue) {
            return cachedValue;
        }

        @Specialization(guards = "value == 2")
        int s2(int value, @Cached("value") Object cachedValue) {
            return value;
        }

        @Specialization(guards = {"value <= 5", "value == cachedValue"}, limit = "3")
        static int s3(Node node, int value, @Cached("value") int cachedValue) {
            return value;
        }

    }
}
