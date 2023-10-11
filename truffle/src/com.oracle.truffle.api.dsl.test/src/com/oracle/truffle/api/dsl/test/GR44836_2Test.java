/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Test;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;

@SuppressWarnings("truffle-interpreted-performance")
public class GR44836_2Test extends AbstractPolyglotTest {

    @Test
    public void test() {
        UseInlinableNode node = adoptNode(GR44836_2TestFactory.UseInlinableNodeGen.create()).get();

        node.execute(1);
        node.execute(2);
        node.execute(3);
    }

    @SuppressWarnings("unused")
    @GenerateInline(false)
    public abstract static class UseInlinableNode extends Node {

        public abstract String execute(Object arg0);

        @Specialization
        final String s0(int arg0, @Cached InlinableNode node) {
            node.execute(this, arg0);
            return "s0";
        }

    }

    @SuppressWarnings("unused")
    @GenerateInline
    @GenerateCached(false)
    public abstract static class InlinableNode extends Node {

        public abstract String execute(Node inliningTarget, Object arg0);

        @Specialization
        static String s1(Node node, String arg0,
                        @Exclusive @Cached InnerInlinedNode errorProfile,
                        @Shared @Cached InnerInnerInlinedNode raiseNode) {
            return "s1";
        }

        @Specialization(guards = "arg0 == 1 || arg0 == 3")
        static String s2(Node inlineTarget, int arg0,
                        @Shared @Cached InnerInnerInlinedNode raiseNode) {

            return raiseNode.execute(inlineTarget, arg0);
        }

        @Specialization(guards = "arg0 == 2")
        static String s3(Node inlineTarget, int arg0,
                        // make sure we need a specialization data class
                        @Exclusive @Cached(inline = false) InnerCachedNode innerCached0,
                        @Exclusive @Cached(inline = false) InnerCachedNode innerCached1,
                        @Exclusive @Cached(inline = false) InnerCachedNode innerCached2,
                        @Exclusive @Cached(inline = false) InnerCachedNode innerCached3,
                        @Exclusive @Cached(inline = false) InnerCachedNode innerCached4,
                        @Exclusive @Cached InnerInlinedNode errorProfile,
                        @Shared @Cached InnerInnerInlinedNode raiseNode) {
            errorProfile.execute(inlineTarget, arg0);
            return raiseNode.execute(inlineTarget, arg0);
        }

    }

    @SuppressWarnings("unused")
    @GenerateInline
    @GenerateCached(false)
    public abstract static class InnerInnerInlinedNode extends Node {

        abstract String execute(Node inliningTarget, Object arg);

        @Specialization
        static String s1(Node node, String arg0,
                        @Cached InnerInlinedNode errorProfile,
                        @Shared @Cached InnerInlinedNode raiseNode) {
            return "s1";
        }

        @Specialization(guards = "arg0 == 3")
        static String s2(Node inlineTarget, int arg0,
                        @Exclusive @Cached InnerInlinedNode errorProfile,
                        @Shared @Cached InnerInlinedNode raiseNode) {
            errorProfile.execute(inlineTarget, arg0);
            return raiseNode.execute(inlineTarget, arg0);
        }

        @Specialization(guards = {"arg0 == 1 || arg0 == 2"})
        static String s3(Node inlineTarget, int arg0,
                        // make sure we need a specialization data class
                        @Exclusive @Cached(inline = false) InnerCachedNode innerCached0,
                        @Exclusive @Cached(inline = false) InnerCachedNode innerCached1,
                        @Exclusive @Cached(inline = false) InnerCachedNode innerCached2,
                        @Exclusive @Cached(inline = false) InnerCachedNode innerCached3,
                        @Exclusive @Cached(inline = false) InnerCachedNode innerCached4,
                        @Exclusive @Cached InnerInlinedNode errorProfile,
                        @Shared @Cached InnerInlinedNode raiseNode) {

            errorProfile.execute(inlineTarget, arg0);
            return raiseNode.execute(inlineTarget, arg0);
        }
    }

    @SuppressWarnings("unused")
    @GenerateInline
    @GenerateCached(false)
    public abstract static class InnerInlinedNode extends Node {

        abstract String execute(Node inliningTarget, Object arg);

        @Specialization
        static String s0(int value) {
            return "InnerInlinedNode.s0";
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    public abstract static class InnerCachedNode extends Node {

        public abstract Object execute();

        @Specialization
        Object s0() {
            return null;
        }

    }

}
