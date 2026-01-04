/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedIntValueProfile;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;

/**
 * This tests that if sharing is disabled automatically and the this receiver is used, the shared
 * cache cannot be stored in the specialization class, otherwise the user might pass the wrong
 * inlining context.
 */
@SuppressWarnings("truffle-sharing")
public class GR71870Test extends AbstractPolyglotTest {

    @Test
    public void test() {
        UseInlinableNode node = adoptNode(GR71870TestFactory.UseInlinableNodeGen.create()).get();

        node.execute(0);
        node.execute(1);
    }

    @SuppressWarnings("unused")
    @GenerateInline(false)
    public abstract static class UseInlinableNode extends Node {

        public abstract String execute(Object arg0);

        @Specialization(guards = "arg0 == 0")
        final String s0(int arg0,
                        @ExpectError("Combining @Shared and @Exclusive for inlined caches within one @Specialization is not supported.%")//
                        @Shared @Cached InlinableNode n0,
                        @Cached InlinableNode n1) {
            n0.execute(this);
            n1.execute(this);
            return "s0";
        }

        @Specialization(guards = "arg0 == 1")
        final String s1(int arg0,
                        @ExpectError("Combining @Shared and @Exclusive for inlined caches within one @Specialization is not supported.%")//
                        @Shared @Cached InlinableNode n0,
                        @Cached InlinableNode n1) {
            n0.execute(this);
            n1.execute(this);
            return "s1";
        }

    }

    @SuppressWarnings("unused")
    @GenerateInline
    @GenerateCached(false)
    public abstract static class InlinableNode extends Node {

        public abstract String execute(Node inliningTarget);

        @Specialization
        static String s1(Node node,
                        @Cached InlinedIntValueProfile p0,
                        @Cached InlinedIntValueProfile p1,
                        @Cached InlinedIntValueProfile p2,
                        @Cached InlinedIntValueProfile p3,
                        @Cached InlinedIntValueProfile p4,
                        @Cached InlinedIntValueProfile p5) {
            return "s1";
        }

    }

}
