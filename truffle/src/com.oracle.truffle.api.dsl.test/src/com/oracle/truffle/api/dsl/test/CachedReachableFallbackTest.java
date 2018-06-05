/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.CachedReachableFallbackTestFactory.CacheDuplicatesNodeGen;
import com.oracle.truffle.api.dsl.test.CachedReachableFallbackTestFactory.GuardKindsNodeGen;
import com.oracle.truffle.api.dsl.test.CachedReachableFallbackTestFactory.ManyCachesNodeGen;
import com.oracle.truffle.api.dsl.test.CachedReachableFallbackTestFactory.ValidWithGenericNodeGen;
import com.oracle.truffle.api.dsl.test.CachedReachableFallbackTestFactory.ValidWithoutGenericNodeGen;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeVisitor;

public class CachedReachableFallbackTest {

    static final String CACHED_GUARD_FALLBACK_ERROR = "Some guards for the following specializations could not be negated for the @Fallback specialization: [s1]. " +
                    "Guards cannot be negated for the @Fallback when they bind @Cached parameters and the specialization may consist of multiple instances. " +
                    "To fix this limit the number of instances to '1' or introduce a more generic specialization declared between this specialization and the fallback. " +
                    "Alternatively the use of @Fallback can be avoided by declaring a @Specialization with manually specified negated guards.";

    @SuppressWarnings("unused")
    abstract static class ValidWithGenericNode extends Node {

        abstract Object execute(Object other);

        @Specialization(guards = {"guardNode.execute(obj)"})
        protected Object s1(int obj,
                        @Cached("createGuard()") GuardNode guardNode) {
            return "s1";
        }

        /*
         * s1 is not reachable after s2 is enabled therefore the fallback cannot be reached from s1
         * and must not be included in the fallback guard.
         */
        @Specialization
        protected Object s2(int obj) {
            return "s2";
        }

        @Fallback
        protected Object fallback(Object x) {
            return "fallback";
        }

        static GuardNode createGuard() {
            return new GuardNode();
        }
    }

    @Test
    public void testValidWithGeneric() {
        ValidWithGenericNode node;

        // test s1 first
        node = ValidWithGenericNodeGen.create();
        Assert.assertEquals(0, countGuardNodes(node));
        Assert.assertEquals("s1", node.execute(42));
        Assert.assertEquals(1, countGuardNodes(node));
        Assert.assertEquals("fallback", node.execute("42"));
        Assert.assertEquals("s1", node.execute(42));
        Assert.assertEquals("s2", node.execute(41));
        Assert.assertEquals("fallback", node.execute("42"));
        Assert.assertEquals("s1", node.execute(42)); // s2 does not replace s1
        Assert.assertEquals(1, countGuardNodes(node));

        // test fallback first
        node = ValidWithGenericNodeGen.create();
        Assert.assertEquals("fallback", node.execute("42"));
        Assert.assertEquals("s1", node.execute(42));
        Assert.assertEquals("s2", node.execute(41));

        // test s2 first
        node = ValidWithGenericNodeGen.create();
        Assert.assertEquals("s2", node.execute(41));
        Assert.assertEquals("fallback", node.execute("42"));
        Assert.assertEquals("s2", node.execute(42));
    }

    private static int countGuardNodes(Node searchNode) {
        AtomicInteger count = new AtomicInteger(0);
        searchNode.accept(new NodeVisitor() {
            public boolean visit(Node node) {
                if (node instanceof GuardNode) {
                    count.incrementAndGet();
                    return false;
                }
                return true;
            }
        });
        return count.get();
    }

    @SuppressWarnings("unused")
    abstract static class ValidWithoutGenericNode extends Node {

        int createGuardCalls;

        abstract Object execute(Object other);

        @Specialization(guards = {"guardNode.execute(obj)"}, limit = "1")
        protected Object s1(int obj,
                        @Cached("createGuard()") GuardNode guardNode) {
            return "s1";
        }

        @Fallback
        protected Object fallback(Object x) {
            return "fallback";
        }

        GuardNode createGuard() {
            createGuardCalls++;
            return new GuardNode();
        }
    }

    @Test
    public void testValidWithoutGeneric() {
        ValidWithoutGenericNode node;

        // test s1 first
        node = ValidWithoutGenericNodeGen.create();
        Assert.assertEquals(0, countGuardNodes(node));
        Assert.assertEquals("s1", node.execute(42));
        Assert.assertEquals(1, countGuardNodes(node));
        Assert.assertEquals("fallback", node.execute("42"));
        Assert.assertEquals("s1", node.execute(42));
        Assert.assertEquals("fallback", node.execute("42"));
        Assert.assertEquals("s1", node.execute(42)); // s2 does not replace s1
        Assert.assertEquals(1, node.createGuardCalls);
        Assert.assertEquals(1, countGuardNodes(node));

        // test fallback first
        node = ValidWithoutGenericNodeGen.create();
        Assert.assertEquals(0, countGuardNodes(node));
        Assert.assertEquals("fallback", node.execute("42"));
        Assert.assertEquals(0, countGuardNodes(node));
        Assert.assertEquals("s1", node.execute(42));
        Assert.assertEquals(1, node.createGuardCalls);
        Assert.assertEquals(1, countGuardNodes(node));
    }

    @SuppressWarnings("unused")
    abstract static class CacheDuplicatesNode extends Node {

        abstract Object execute(Object other);

        @Specialization(guards = {"obj == cachedObj"}, limit = "1")
        protected Object s1(int obj,
                        @Cached("obj") int cachedObj) {
            return cachedObj;
        }

        @Fallback
        protected Object fallback(Object x) {
            return "fallback";
        }

    }

    @Test
    public void testCacheDuplicates() {
        CacheDuplicatesNode node;

        // test s1 first
        node = CacheDuplicatesNodeGen.create();
        Assert.assertEquals(0, countGuardNodes(node));
        Assert.assertEquals(42, node.execute(42));
        Assert.assertEquals("fallback", node.execute(41));
        Assert.assertEquals(42, node.execute(42));
        Assert.assertEquals("fallback", node.execute(41));

        // test fallback with string
        node = CacheDuplicatesNodeGen.create();
        Assert.assertEquals("fallback", node.execute("41"));
        Assert.assertEquals(41, node.execute(41));
        Assert.assertEquals("fallback", node.execute(42));
        Assert.assertEquals(41, node.execute(41));
    }

    private static void assertAdopted(Node node) {
        Assert.assertNotNull(node.getParent());
        Assert.assertTrue(NodeUtil.findNodeChildren(node.getParent()).contains(node));
    }

    @SuppressWarnings("unused")
    abstract static class ManyCachesNode extends Node {

        abstract Object execute(Object other);

        @Specialization(guards = {"guardNode1.execute(obj)", "guardNode2.execute(obj)", "guardNode3.execute(obj)"}, limit = "1")
        protected Object s1(int obj,
                        @Cached("createGuard()") GuardNode guardNode1,
                        @Cached("createGuard()") GuardNode guardNode2,
                        @Cached("createGuard()") GuardNode guardNode3,
                        @Cached("createGuard()") GuardNode unboundGuard) {
            assertAdopted(guardNode1);
            assertAdopted(guardNode2);
            assertAdopted(guardNode3);
            assertAdopted(unboundGuard);
            return "s1";
        }

        @Specialization
        protected Object s2(double obj,
                        @Cached("createGuard()") GuardNode unboundGuard) {
            assertAdopted(unboundGuard);
            return "s2";
        }

        @Specialization
        protected Object s3(float obj,
                        @Cached("createGuard()") GuardNode unboundGuard1,
                        @Cached("createGuard()") GuardNode unboundGuard2,
                        @Cached("createGuard()") GuardNode unboundGuard3,
                        @Cached("createGuard()") GuardNode unboundGuard4) {
            assertAdopted(unboundGuard1);
            assertAdopted(unboundGuard2);
            assertAdopted(unboundGuard3);
            assertAdopted(unboundGuard4);
            return "s3";
        }

        @Fallback
        protected Object fallback(Object x) {
            return "fallback";
        }

        static GuardNode createGuard() {
            return new GuardNode();
        }
    }

    @Test
    public void testManyCaches() {
        ManyCachesNode node;

        // test s1 first
        node = ManyCachesNodeGen.create();
        Assert.assertEquals("s1", node.execute(42));
        Assert.assertEquals("fallback", node.execute("41"));

        // test fallback first
        node = ManyCachesNodeGen.create();
        Assert.assertEquals("fallback", node.execute(41));
        Assert.assertEquals("fallback", node.execute(41));
        Assert.assertEquals("s1", node.execute(42));
        Assert.assertEquals("fallback", node.execute(41));
        Assert.assertEquals("s1", node.execute(42));
    }

    @SuppressWarnings("unused")
    abstract static class GuardKindsNode extends Node {

        abstract Object execute(Object other);

        @Specialization(guards = {"guardNode1.execute(obj)", "notTwo(obj)"}, rewriteOn = RuntimeException.class, assumptions = "createAssumption()", limit = "1")
        protected Object s1(int obj,
                        @Cached("create(1)") NotGuardNode guardNode1) {
            assertAdopted(guardNode1);
            if (obj == 3) {
                throw new RuntimeException();
            }
            return "s1";
        }

        boolean notTwo(int obj) {
            // checked by a prior guard
            Assert.assertNotEquals(1, obj);
            return obj != 2;
        }

        @Fallback
        protected Object fallback(Object x) {
            return "fallback";
        }

        Assumption assumption;

        Assumption createAssumption() {
            if (assumption == null) {
                assumption = Truffle.getRuntime().createAssumption();
            }
            return assumption;
        }
    }

    @Test
    public void testGuardKinds() {
        GuardKindsNode node;

        // test s1 first
        node = GuardKindsNodeGen.create();
        Assert.assertEquals("s1", node.execute(0));
        Assert.assertEquals("fallback", node.execute(1));
        Assert.assertEquals("fallback", node.execute(2));
        Assert.assertEquals("s1", node.execute(0));
        Assert.assertEquals("fallback", node.execute(3));
        Assert.assertEquals("fallback", node.execute(0));

        node = GuardKindsNodeGen.create();
        Assert.assertEquals("fallback", node.execute(1));
        Assert.assertEquals("s1", node.execute(0));
        Assert.assertEquals("fallback", node.execute(2));
        Assert.assertEquals("s1", node.execute(0));

        node = GuardKindsNodeGen.create();
        Assert.assertEquals("fallback", node.execute(2));
        Assert.assertEquals("fallback", node.execute(1));
        Assert.assertEquals("s1", node.execute(0));

    }

    static class NotGuardNode extends Node {

        private final int value;

        NotGuardNode(int value) {
            this.value = value;
        }

        boolean execute(int object) {
            return object != this.value;
        }

        public static NotGuardNode create(int value) {
            return new NotGuardNode(value);
        }
    }

    static class GuardNode extends Node {

        private final int value;

        GuardNode() {
            this(42);
        }

        GuardNode(int value) {
            this.value = value;
        }

        boolean execute(int object) {
            return object == this.value;
        }
    }

    @SuppressWarnings("unused")
    abstract static class CachedError1Node extends Node {

        abstract Object execute(Object other);

        @Specialization(guards = {"guardNode.execute(obj)"}, limit = "2")
        protected Object s1(int obj,
                        @Cached("createGuard()") GuardNode guardNode) {
            return "s1";
        }

        @ExpectError(CACHED_GUARD_FALLBACK_ERROR)
        @Fallback
        protected Object fallback(Object x) {
            return "fallback";
        }

        static GuardNode createGuard() {
            return new GuardNode();
        }
    }

    @SuppressWarnings("unused")
    abstract static class CachedError2Node extends Node {

        abstract Object execute(Object other);

        @Specialization(guards = {"guardNode.execute(obj)"}, limit = "2")
        protected Object s1(int obj,
                        @Cached("createGuard()") GuardNode guardNode) {
            return "s1";
        }

        @ExpectError(CACHED_GUARD_FALLBACK_ERROR)
        @Fallback
        protected Object fallback(Object x) {
            return "fallback";
        }

        static GuardNode createGuard() {
            return new GuardNode();
        }
    }

    @SuppressWarnings("unused")
    abstract static class CachedError3Node extends Node {

        abstract Object execute(Object other);

        // we don't know if fallback is reachable directly from s1 because s2 has a custom guard.
        @Specialization(guards = {"guardNode.execute(obj)"}, limit = "2")
        protected Object s1(int obj,
                        @Cached("createGuard()") GuardNode guardNode) {
            return "s1";
        }

        @Specialization(guards = "obj == 41")
        protected Object s2(int obj) {
            return "s2";
        }

        @ExpectError(CACHED_GUARD_FALLBACK_ERROR)
        @Fallback
        protected Object fallback(Object x) {
            return "fallback";
        }

        static GuardNode createGuard() {
            return new GuardNode();
        }
    }

}
