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
package com.oracle.truffle.api.dsl.test;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;

public class SharedAndNonSharedInlineWarningTest {
    private static final String EXPECTED_WARNING = "It is discouraged that specializations with specialization data class combine shared and exclusive%";

    @GenerateInline
    @GenerateCached(false)
    public abstract static class InlineNode extends Node {
        public abstract Object execute(Node inliningTarget, Object arg);

        @Specialization
        static Object spec1(int arg) {
            return arg;
        }

        @Specialization
        static Object spec2(Object arg,
                        @Cached(value = "arg", neverDefault = false) Object cachedArg) {
            return cachedArg == arg;
        }
    }

    @GenerateInline(false)
    @GenerateCached
    public abstract static class NonInlineNode extends Node {
        public abstract Object execute(Object arg);

        @Specialization
        static Object spec3(int arg) {
            return arg;
        }
    }

    // ------------------------------------------------------------------------
    // Mixing WITHOUT a data-class is OK:

    @GenerateCached
    @GenerateInline(false)
    public abstract static class MixProfilesWithoutDataClassNode extends Node {
        public abstract Object execute(Object arg);

        @Specialization
        static Object mixProfilesWithoutDataClass(int a,
                        @Bind("this") Node node,
                        @Shared @Cached InlinedBranchProfile sharedBranch,
                        @Exclusive @Cached InlinedBranchProfile exclusiveBranch) {
            sharedBranch.enter(node);
            exclusiveBranch.enter(node);
            return a;
        }

        @Specialization
        static Object dummy(double a,
                        @Bind("this") Node node,
                        @Shared @Cached InlinedBranchProfile sharedBranch) {
            sharedBranch.enter(node);
            return a;
        }
    }

    @GenerateCached
    @GenerateInline(false)
    public abstract static class NonMixedProfilesWithoutDataClassNode extends Node {
        public abstract Object execute(Object arg);

        @Specialization
        static Object mixProfilesWithoutDataClass(int a,
                        @Bind("this") Node node,
                        @Exclusive @Cached InlinedBranchProfile sharedBranch,
                        @Exclusive @Cached InlinedBranchProfile exclusiveBranch) {
            sharedBranch.enter(node);
            exclusiveBranch.enter(node);
            return a;
        }

        @Specialization
        static Object dummy(double a,
                        @Bind("this") Node node,
                        @Exclusive @Cached InlinedBranchProfile sharedBranch) {
            sharedBranch.enter(node);
            return a;
        }
    }

    @GenerateCached
    @GenerateInline(false)
    public abstract static class MixProfileAndNodeWithoutDataClassNode extends Node {
        public abstract Object execute(Object arg);

        @Specialization
        static Object mixProfilesWithoutDataClass(int a,
                        @Bind("this") Node node,
                        @Shared @Cached InlinedBranchProfile sharedBranch,
                        @Exclusive @Cached InlineNode exclusiveNode) {
            sharedBranch.enter(node);
            exclusiveNode.execute(node, a);
            return a;
        }

        @Specialization
        static Object dummy(double a,
                        @Bind("this") Node node,
                        @Shared @Cached InlinedBranchProfile sharedBranch) {
            sharedBranch.enter(node);
            return a;
        }
    }

    @GenerateCached
    @GenerateInline(false)
    public abstract static class MixNodesWithoutDataClassNode extends Node {
        public abstract Object execute(Object arg);

        @Specialization
        static Object mixWithoutDataClass(int a,
                        @Bind("this") Node node,
                        @Shared @Cached InlineNode sharedNode,
                        @Exclusive @Cached InlineNode exclusiveNode) {
            sharedNode.execute(node, a);
            exclusiveNode.execute(node, a);
            return a;
        }

        @Specialization
        static Object dummy(double a,
                        @Bind("this") Node node,
                        @Shared @Cached InlineNode sharedNode) {
            sharedNode.execute(node, a);
            return a;
        }
    }

    // ------------------------------------------------------------------------
    // Mixing WITH a data-class produces the warning:

    @GenerateCached
    @GenerateInline(false)
    public abstract static class MixProfilesWithDataClassNode extends Node {
        public abstract Object execute(Object arg);

        @Specialization
        static Object dummy(double a,
                        @Bind("this") Node node,
                        @Shared @Cached InlinedBranchProfile sharedBranch) {
            sharedBranch.enter(node);
            return a;
        }

        @Specialization
        @ExpectWarning(EXPECTED_WARNING)
        static Object mixWithDataClass(int a,
                        @Bind("this") Node node,
                        @Shared @Cached InlinedBranchProfile sharedBranch,
                        @Exclusive @Cached InlinedBranchProfile exclusiveBranch,
                        @SuppressWarnings("unused") @Cached IndirectCallNode cachedNode1,
                        @SuppressWarnings("unused") @Cached IndirectCallNode cachedNode2,
                        @SuppressWarnings("unused") @Cached IndirectCallNode cachedNode3,
                        @SuppressWarnings("unused") @Cached IndirectCallNode cachedNode4) {
            sharedBranch.enter(node);
            exclusiveBranch.enter(node);
            return a;
        }
    }

    @GenerateCached
    @GenerateInline(false)
    public abstract static class NonMixedProfilesWithDataClassNode extends Node {
        public abstract Object execute(Object arg);

        @Specialization
        static Object dummy(double a,
                        @Bind("this") Node node,
                        @Exclusive @Cached InlinedBranchProfile sharedBranch) {
            sharedBranch.enter(node);
            return a;
        }

        @Specialization
        static Object mixWithDataClass(int a,
                        @Bind("this") Node node,
                        @Exclusive @Cached InlinedBranchProfile sharedBranch,
                        @Exclusive @Cached InlinedBranchProfile exclusiveBranch,
                        @SuppressWarnings("unused") @Cached IndirectCallNode cachedNode1,
                        @SuppressWarnings("unused") @Cached IndirectCallNode cachedNode2,
                        @SuppressWarnings("unused") @Cached IndirectCallNode cachedNode3,
                        @SuppressWarnings("unused") @Cached IndirectCallNode cachedNode4) {
            sharedBranch.enter(node);
            exclusiveBranch.enter(node);
            return a;
        }
    }

    @GenerateCached
    @GenerateInline(false)
    public abstract static class MixProfileAndNodeWithDataClassNode extends Node {
        public abstract Object execute(Object arg);

        @Specialization
        static Object dummy(double a,
                        @Bind("this") Node node,
                        @Shared @Cached InlinedBranchProfile sharedBranch) {
            sharedBranch.enter(node);
            return a;
        }

        @Specialization
        @ExpectWarning(EXPECTED_WARNING)
        static Object mixWithDataClass(int a,
                        @Bind("this") Node node,
                        @Shared @Cached InlinedBranchProfile sharedBranch,
                        @Exclusive @Cached InlineNode exclusiveNode,
                        @SuppressWarnings("unused") @Cached IndirectCallNode cachedNode1,
                        @SuppressWarnings("unused") @Cached IndirectCallNode cachedNode2,
                        @SuppressWarnings("unused") @Cached IndirectCallNode cachedNode3,
                        @SuppressWarnings("unused") @Cached IndirectCallNode cachedNode4) {
            sharedBranch.enter(node);
            exclusiveNode.execute(node, a);
            return a;
        }
    }

    @GenerateCached
    @GenerateInline(false)
    public abstract static class MixNodesWithDataClassNode extends Node {
        public abstract Object execute(Object arg);

        @Specialization
        static Object dummy(double a,
                        @Bind("this") Node node,
                        @Shared @Cached InlineNode sharedNode) {
            sharedNode.execute(node, a);
            return a;
        }

        @Specialization
        @ExpectWarning(EXPECTED_WARNING)
        static Object mixWithDataClass(int a,
                        @Bind("this") Node node,
                        @Shared @Cached InlineNode sharedNode,
                        @Exclusive @Cached InlineNode exclusiveNode,
                        @SuppressWarnings("unused") @Cached IndirectCallNode cachedNode1,
                        @SuppressWarnings("unused") @Cached IndirectCallNode cachedNode2,
                        @SuppressWarnings("unused") @Cached IndirectCallNode cachedNode3,
                        @SuppressWarnings("unused") @Cached IndirectCallNode cachedNode4) {
            sharedNode.execute(node, a);
            exclusiveNode.execute(node, a);
            return a;
        }
    }
}
