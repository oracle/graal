/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.GR44836_1TestFactory.OuterOuterCachedNodeGen;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

@SuppressWarnings("truffle-interpreted-performance")
public class GR44836_1Test {

    static class TestRootNode extends RootNode {

        @Child OuterOuterCachedNode node;

        protected TestRootNode() {
            super(null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return node.execute(42);
        }
    }

    @Test
    public void test() {
        TestRootNode root = new TestRootNode();
        root.node = OuterOuterCachedNodeGen.create();
        assertEquals("InnerInlinedNode.s0", root.getCallTarget().call());
    }

    @SuppressWarnings("unused")
    @GenerateInline(false)
    public abstract static class OuterOuterCachedNode extends Node {

        public abstract String execute(Object arg0);

        @Specialization
        static String s0(String arg0) {
            return "s0";
        }

        @Specialization
        static String s1(double arg0) {
            return "s1";
        }

        @Specialization
        static String s2(long arg0,
                        @Shared @Cached OuterOuterInlinedNode node) {
            return "s2";
        }

        @Specialization
        static String s3(int arg0,
                        @Bind Node inlineTarget,
                        // make sure we need a specialization data class
                        @Cached InnerCachedNode innerCached0,
                        @Cached InnerCachedNode innerCached1,
                        @Cached InnerCachedNode innerCached2,
                        // we need an inlined inner node that is not shared
                        @Cached InnerInlinedNode errorProfile,
                        // we need an inlined inner node that is shared
                        @Shared @Cached OuterOuterInlinedNode node) {
            innerCached0.execute();
            innerCached1.execute();
            innerCached2.execute();
            errorProfile.execute(inlineTarget, arg0);
            return node.execute(inlineTarget, arg0);
        }

    }

    @SuppressWarnings("unused")
    @GenerateCached(false)
    @GenerateInline(true)
    public abstract static class OuterOuterInlinedNode extends Node {

        public abstract String execute(Node node, Object arg0);

        @Specialization
        static String s0(String arg0) {
            return "s0";
        }

        @Specialization
        static String s1(double arg0) {
            return "s1";
        }

        @Specialization
        static String s2(Node inlineTarget, long arg0,
                        @Shared @Cached OuterInlinedNode node) {
            return "s2";
        }

        @Specialization
        static String s3(Node inlineTarget, int arg0,
                        // make sure we need a specialization data class
                        @Cached(inline = false) InnerCachedNode innerCached0,
                        @Cached(inline = false) InnerCachedNode innerCached1,
                        @Cached(inline = false) InnerCachedNode innerCached2,
                        // we need an inlined inner node that is not shared
                        @Cached InnerInlinedNode errorProfile,
                        // we need an inlined inner node that is shared
                        @Shared @Cached OuterInlinedNode node) {
            innerCached0.execute();
            innerCached1.execute();
            innerCached2.execute();
            errorProfile.execute(inlineTarget, arg0);
            return node.execute(inlineTarget, arg0);
        }

    }

    @SuppressWarnings("unused")
    @GenerateCached(false)
    @GenerateInline(true)
    public abstract static class OuterInlinedNode extends Node {

        public abstract String execute(Node node, Object arg0);

        @Specialization
        static String s0(String arg0) {
            return "s0";
        }

        @Specialization
        static String s1(double arg0) {
            return "s1";
        }

        @Specialization
        static String s2(Node inlineTarget, long arg0,
                        @Shared @Cached InlinableNode node) {
            return "s2";
        }

        @Specialization
        static String s3(Node inlineTarget, int arg0,
                        // make sure we need a specialization data class
                        @Cached(inline = false) InnerCachedNode innerCached0,
                        @Cached(inline = false) InnerCachedNode innerCached1,
                        @Cached(inline = false) InnerCachedNode innerCached2,
                        // we need an inlined inner node that is not shared
                        @Exclusive @Cached InnerInlinedNode errorProfile,
                        // we need an inlined inner node that is shared
                        @Shared @Cached InlinableNode node) {
            innerCached0.execute();
            innerCached1.execute();
            innerCached2.execute();
            errorProfile.execute(inlineTarget, arg0);
            return node.execute(inlineTarget, arg0);
        }

    }

    @SuppressWarnings("unused")
    @GenerateInline
    @GenerateCached(false)
    public abstract static class InlinableNode extends Node {

        public abstract String execute(Node inliningTarget, Object arg0);

        @Specialization
        static String s1(Node node, String arg0,
                        @Cached @Shared InnerInlinedNode raiseNode) {
            return "s1";
        }

        @Specialization
        static String s2(Node node, double arg1,
                        @Cached @Shared InnerInlinedNode raiseNode) {
            return "s1";
        }

        @Specialization()
        static String s3(Node inlineTarget, int arg0,
                        // make sure we need a specialization data class
                        @Cached(inline = false) InnerCachedNode innerCached0,
                        @Cached(inline = false) InnerCachedNode innerCached1,
                        @Cached(inline = false) InnerCachedNode innerCached2,
                        // we need an inlined inner node that is not shared
                        @Exclusive @Cached InnerInlinedNode errorProfile,
                        // we need an inlined inner node that is shared
                        @Shared @Cached InnerInlinedNode raiseNode) {
            innerCached0.execute();
            innerCached1.execute();
            innerCached2.execute();
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
