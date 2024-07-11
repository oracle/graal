/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Supplier;

import org.junit.Test;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Introspectable;
import com.oracle.truffle.api.dsl.Introspection;
import com.oracle.truffle.api.dsl.Introspection.SpecializationInfo;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.CachedReachableFallbackTest.GuardNode;
import com.oracle.truffle.api.dsl.test.NeverDefaultTestFactory.CacheCleanupNodeGen;
import com.oracle.truffle.api.dsl.test.NeverDefaultTestFactory.CachedGuardAndFallbackNodeGen;
import com.oracle.truffle.api.dsl.test.NeverDefaultTestFactory.FallbackManyCachesNodeGen;
import com.oracle.truffle.api.dsl.test.NeverDefaultTestFactory.FallbackWithCacheClassNodeGen;
import com.oracle.truffle.api.dsl.test.NeverDefaultTestFactory.GuardCacheNodeGen;
import com.oracle.truffle.api.dsl.test.NeverDefaultTestFactory.MultiInstanceCacheNodeGen;
import com.oracle.truffle.api.dsl.test.NeverDefaultTestFactory.MultiInstanceNodeCacheNodeGen;
import com.oracle.truffle.api.dsl.test.NeverDefaultTestFactory.NeverDefaultInt1NodeGen;
import com.oracle.truffle.api.dsl.test.NeverDefaultTestFactory.NeverDefaultInt2NodeGen;
import com.oracle.truffle.api.dsl.test.NeverDefaultTestFactory.NeverDefaultInt3NodeGen;
import com.oracle.truffle.api.dsl.test.NeverDefaultTestFactory.ObjectInitializationInCachedValueNodeGen;
import com.oracle.truffle.api.dsl.test.NeverDefaultTestFactory.ReplaceThisNoCacheNodeGen;
import com.oracle.truffle.api.dsl.test.NeverDefaultTestFactory.ReplaceThisNodeGen;
import com.oracle.truffle.api.dsl.test.NeverDefaultTestFactory.SharedDefaultInlinedNodeNodeGen;
import com.oracle.truffle.api.dsl.test.NeverDefaultTestFactory.SharedDefaultIntNodeGen;
import com.oracle.truffle.api.dsl.test.NeverDefaultTestFactory.SharedDefaultNodeNodeGen;
import com.oracle.truffle.api.dsl.test.NeverDefaultTestFactory.SharedDefaultObjectNodeGen;
import com.oracle.truffle.api.dsl.test.NeverDefaultTestFactory.SharedNeverDefaultInlinedNodeNodeGen;
import com.oracle.truffle.api.dsl.test.NeverDefaultTestFactory.SharedNeverDefaultIntNodeGen;
import com.oracle.truffle.api.dsl.test.NeverDefaultTestFactory.SharedNeverDefaultNodeNodeGen;
import com.oracle.truffle.api.dsl.test.NeverDefaultTestFactory.SharedNeverDefaultObjectNodeGen;
import com.oracle.truffle.api.dsl.test.NeverDefaultTestFactory.SingleInstanceAssumptionNodeGen;
import com.oracle.truffle.api.dsl.test.NeverDefaultTestFactory.SingleInstanceLibraryCacheNodeGen;
import com.oracle.truffle.api.dsl.test.NeverDefaultTestFactory.SingleInstanceNodeCacheNodeGen;
import com.oracle.truffle.api.dsl.test.NeverDefaultTestFactory.SingleInstancePrimitiveCacheNodeGen;
import com.oracle.truffle.api.dsl.test.NeverDefaultTestFactory.SingleInstanceSharedCacheNodeGen;
import com.oracle.truffle.api.dsl.test.NeverDefaultTestFactory.UseMultiInstanceNodeCacheNodeGen;
import com.oracle.truffle.api.dsl.test.NeverDefaultTestFactory.UseSharedDefaultInlinedNodeNodeGen;
import com.oracle.truffle.api.dsl.test.NeverDefaultTestFactory.UseSharedNeverDefaultInlinedNodeNodeGen;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.test.ReflectionUtils;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;

/**
 * Tests the never default behavior of shared caches and caches without generated specialization
 * class. In order to use lock free caching, we need to make sure we do not trigger the
 * specialization with a default value. Hence we assert for not zero or null in that case.
 */
@SuppressWarnings({"truffle-inlining", "truffle-sharing"})
public class NeverDefaultTest extends AbstractPolyglotTest {

    abstract static class TestNode extends Node {

        abstract int execute(int argument);

    }

    abstract static class SharedDefaultIntNode extends TestNode {

        @Specialization(guards = "value == cachedValue")
        int s0(int value,
                        @Shared("a") @Cached(value = "value", neverDefault = false) int cachedValue,
                        @Cached(value = "value", neverDefault = false) int notShared) {
            assertNotEquals(0, cachedValue);
            assertNotEquals(0, notShared);
            return value;
        }

        @Specialization
        int s1(int value,
                        @Shared("a") @Cached(value = "value", neverDefault = false) int cachedValue,
                        @Cached(value = "value", neverDefault = false) int notShared) {
            assertNotEquals(0, cachedValue);
            assertNotEquals(0, notShared);
            return value;
        }
    }

    abstract static class SharedNeverDefaultIntNode extends TestNode {

        @Specialization(guards = "value == cachedValue")
        int s0(int value,
                        @Shared("a") @Cached(value = "value", neverDefault = true) int cachedValue,
                        @Cached(value = "value", neverDefault = true) int notShared) {
            assertNotEquals(0, cachedValue);
            assertNotEquals(0, notShared);
            return value;
        }

        @Specialization
        int s1(int value,
                        @Shared("a") @Cached(value = "value", neverDefault = true) int cachedValue,
                        @Cached(value = "value", neverDefault = true) int notShared) {
            assertNotEquals(0, cachedValue);
            assertNotEquals(0, notShared);
            return value;
        }

    }

    @Test
    public void testSharedDefaultIntNode() throws InterruptedException {
        assertInParallel(SharedDefaultIntNodeGen::create, (node, threadIndex, objectIndex) -> {
            node.execute(threadIndex % 3 + 1);
        });

        assertInParallel(SharedNeverDefaultIntNodeGen::create, (node, threadIndex, objectIndex) -> {
            node.execute(threadIndex % 3 + 1);
        });
    }

    @SuppressWarnings("truffle-inlining")
    abstract static class SharedDefaultObjectNode extends TestNode {

        @Specialization(guards = "value == 1")
        int s0(int value, @Shared("a") @Cached(value = "fromInt(value)", neverDefault = false) Object cachedValue) {
            assertEquals("42", cachedValue);
            return value;
        }

        @Specialization
        int s1(int value, @Shared("a") @Cached(value = "fromInt(value)", neverDefault = false) Object cachedValue) {
            /*
             * The invariant is that the cached values are never 0.
             */
            assertEquals("42", cachedValue);
            return value;
        }

        @SuppressWarnings("unused")
        static Object fromInt(int intValue) {
            return "42";
        }

    }

    @SuppressWarnings("truffle-inlining")
    abstract static class SharedNeverDefaultObjectNode extends TestNode {

        @Specialization(guards = "value == 1")
        int s0(int value, @Shared("a") @Cached("fromInt(value)") Object cachedValue) {
            assertEquals("42", cachedValue);
            return value;
        }

        @Specialization
        int s1(int value, @Shared("a") @Cached("fromInt(value)") Object cachedValue) {
            /*
             * The invariant is that the cached values are never 0.
             */
            assertEquals("42", cachedValue);
            return value;
        }

        @SuppressWarnings("unused")
        @NeverDefault
        static Object fromInt(int intValue) {
            return "42";
        }

    }

    @Test
    public void testSharedDefaultObjectNode() throws InterruptedException {
        assertInParallel(SharedDefaultObjectNodeGen::create, (node, threadIndex, objectIndex) -> {
            node.execute(threadIndex % 2 + 1);
        });

        assertInParallel(SharedNeverDefaultObjectNodeGen::create, (node, threadIndex, objectIndex) -> {
            node.execute(threadIndex % 2 + 1);
        });
    }

    @SuppressWarnings("truffle-inlining")
    abstract static class SharedDefaultNodeNode extends TestNode {

        @Specialization(guards = "value == 1")
        int s0(int value, @Shared("a") @Cached(value = "fromInt(value)", neverDefault = false) Node cachedValue) {
            assertNotNull(cachedValue.getParent());
            return value;
        }

        @Specialization
        int s1(int value, @Shared("a") @Cached(value = "fromInt(value)", neverDefault = false) Node cachedValue) {
            /*
             * The invariant is that the cached values are never 0.
             */
            assertNotNull(cachedValue.getParent());
            return value;
        }

        @SuppressWarnings("unused")
        static Node fromInt(int intValue) {
            return SharedDefaultNodeNodeGen.create();
        }

    }

    @SuppressWarnings("truffle-inlining")
    abstract static class SharedNeverDefaultNodeNode extends TestNode {

        @Specialization(guards = "value == 1")
        int s0(int value, @Shared("a") @Cached("fromInt(value)") Node cachedValue) {
            assertNotNull(cachedValue.getParent());
            return value;
        }

        @Specialization
        int s1(int value, @Shared("a") @Cached("fromInt(value)") Node cachedValue) {
            /*
             * The invariant is that the cached values are never 0.
             */
            assertNotNull(cachedValue.getParent());
            return value;
        }

        @SuppressWarnings("unused")
        @NeverDefault
        static Node fromInt(int intValue) {
            return SharedNeverDefaultNodeNodeGen.create();
        }

    }

    @Test
    public void testSharedDefaultNodeNode() throws InterruptedException {
        assertInParallel(SharedNeverDefaultNodeNodeGen::create, (node, threadIndex, objectIndex) -> {
            node.execute(threadIndex % 2 + 1);
        });

        assertInParallel(SharedDefaultNodeNodeGen::create, (node, threadIndex, objectIndex) -> {
            node.execute(threadIndex % 2 + 1);
        });
    }

    @GenerateInline
    abstract static class SharedDefaultInlinedNodeNode extends Node {

        abstract Object execute(Node node, Object value);

        @Specialization(guards = "value == 1")
        int s0(int value, @Shared("a") @Cached(value = "fromInt(value)", neverDefault = false, inline = false) SharedDefaultInlinedNodeNode cachedValue) {
            assertNotNull(cachedValue.getParent());
            return value;
        }

        @Specialization
        int s1(int value, @Shared("a") @Cached(value = "fromInt(value)", neverDefault = false, inline = false) SharedDefaultInlinedNodeNode cachedValue) {
            /*
             * The invariant is that the cached values are never 0.
             */
            assertNotNull(cachedValue.getParent());
            return value;
        }

        @SuppressWarnings("unused")
        static SharedDefaultInlinedNodeNode fromInt(int intValue) {
            return SharedDefaultInlinedNodeNodeGen.create();
        }

    }

    @SuppressWarnings("truffle-inlining")
    abstract static class UseSharedDefaultInlinedNodeNode extends TestNode {

        @Specialization
        int s0(int value, @Cached SharedDefaultInlinedNodeNode cachedValue) {
            cachedValue.execute(this, value);
            return value;
        }

    }

    @GenerateInline
    abstract static class SharedNeverDefaultInlinedNodeNode extends Node {

        abstract Object execute(Node node, Object value);

        @Specialization(guards = "value == 1")
        int s0(int value, @Shared("a") @Cached(value = "fromInt(value)", neverDefault = true, inline = false) SharedNeverDefaultInlinedNodeNode cachedValue) {
            assertNotNull(cachedValue.getParent());
            return value;
        }

        @Specialization
        int s1(int value, @Shared("a") @Cached(value = "fromInt(value)", neverDefault = true, inline = false) SharedNeverDefaultInlinedNodeNode cachedValue) {
            /*
             * The invariant is that the cached values are never 0.
             */
            assertNotNull(cachedValue.getParent());
            return value;
        }

        @SuppressWarnings("unused")
        static SharedNeverDefaultInlinedNodeNode fromInt(int intValue) {
            return SharedNeverDefaultInlinedNodeNodeGen.create();
        }

    }

    @SuppressWarnings("truffle-inlining")
    abstract static class UseSharedNeverDefaultInlinedNodeNode extends TestNode {

        @Specialization
        int s0(int value, @Cached SharedNeverDefaultInlinedNodeNode cachedValue) {
            cachedValue.execute(this, value);
            return value;
        }

    }

    @Test
    public void testInlinedSharedDefaultNodeNode() throws InterruptedException {
        assertInParallel(UseSharedDefaultInlinedNodeNodeGen::create, (node, threadIndex, objectIndex) -> {
            node.execute(threadIndex % 2 + 1);
        });

        assertInParallel(UseSharedNeverDefaultInlinedNodeNodeGen::create, (node, threadIndex, objectIndex) -> {
            node.execute(threadIndex % 2 + 1);
        });
    }

    @SuppressWarnings("truffle-inlining")
    abstract static class NeverDefaultInt1Node extends Node {

        abstract int execute(Object argument);

        @Specialization
        int s0(int value, @Cached(value = "value", neverDefault = true) int cachedValue) {
            assertNotEquals(0, cachedValue);
            return value;
        }

    }

    @Test
    public void testNeverDefaultInt1Node() throws InterruptedException {
        assertInParallel(NeverDefaultInt1NodeGen::create, (node, threadIndex, objectIndex) -> {
            node.execute(threadIndex % 2 + 1);
        });
    }

    @SuppressWarnings("truffle-inlining")
    abstract static class NeverDefaultInt2Node extends Node {

        abstract int execute(Object argument);

        @Specialization
        int s0(int value, @Cached(value = "value", neverDefault = true) int cachedValue1,
                        @Cached(value = "value", neverDefault = true) int cachedValue2) {
            assertNotEquals(0, cachedValue1);
            assertNotEquals(0, cachedValue2);
            return value;
        }

    }

    @Test
    public void testNeverDefaultInt2Node() throws InterruptedException {
        assertInParallel(NeverDefaultInt2NodeGen::create, (node, threadIndex, objectIndex) -> {
            node.execute(threadIndex % 2 + 1);
        });
    }

    @SuppressWarnings("truffle-inlining")
    abstract static class NeverDefaultInt3Node extends Node {

        abstract int execute(Object argument);

        /*
         * Triggers a specialization class, so there is an implicit null check.
         */
        @Specialization
        @SuppressWarnings("truffle") // never default warning
        int s0(int value, @Cached("value") int cachedValue1,
                        @Cached("value") int cachedValue2,
                        @Cached("value") int cachedValue3) {
            assertNotEquals(0, cachedValue1);
            assertNotEquals(0, cachedValue2);
            assertNotEquals(0, cachedValue3);
            return value;
        }

    }

    @Test
    public void testNeverDefaultInt3Node() throws InterruptedException {
        assertInParallel(NeverDefaultInt3NodeGen::create, (node, threadIndex, objectIndex) -> {
            node.execute(threadIndex % 2 + 1);
        });
    }

    @SuppressWarnings("truffle-inlining")
    abstract static class NeverDefaultObject1Node extends Node {

        abstract int execute(int argument);

        @Specialization
        int s0(int value, @Cached(value = "fromInt(value)", neverDefault = true) Object cachedValue) {
            assertEquals("42", cachedValue);
            return value;
        }

        @SuppressWarnings("unused")
        static Object fromInt(int intValue) {
            return "42";
        }

    }

    @Test
    public void testNeverDefaultObject1Node() throws InterruptedException {
        assertInParallel(NeverDefaultInt1NodeGen::create, (node, threadIndex, objectIndex) -> {
            node.execute(threadIndex);
        });
    }

    @SuppressWarnings("truffle-inlining")
    abstract static class NeverDefaultObject2Node extends Node {

        abstract int execute(int argument);

        @Specialization
        int s0(int value, @Cached(value = "fromInt(value)", neverDefault = true) Object cachedValue1,
                        @Cached(value = "fromInt(value)", neverDefault = true) Object cachedValue2) {
            assertEquals("42", cachedValue1);
            assertEquals("42", cachedValue2);
            return value;
        }

        @SuppressWarnings("unused")
        static Object fromInt(int intValue) {
            return "42";
        }

    }

    @Test
    public void testNeverDefaultObject2Node() throws InterruptedException {
        assertInParallel(NeverDefaultInt2NodeGen::create, (node, threadIndex, objectIndex) -> {
            node.execute(threadIndex);
        });
    }

    @SuppressWarnings("truffle-inlining")
    abstract static class NeverDefaultObject3Node extends Node {

        abstract int execute(int argument);

        @Specialization
        @SuppressWarnings("truffle") // never default warning
        int s0(int value, @Cached("fromInt(value)") Object cachedValue1,
                        @Cached("fromInt(value)") Object cachedValue2,
                        @Cached("fromInt(value)") Object cachedValue3) {
            assertEquals("42", cachedValue1);
            assertEquals("42", cachedValue2);
            assertEquals("42", cachedValue3);
            return value;
        }

        @SuppressWarnings("unused")
        static Object fromInt(int intValue) {
            return "42";
        }

    }

    @Test
    public void testNeverDefaultObject3Node() throws InterruptedException {
        assertInParallel(NeverDefaultInt3NodeGen::create, (node, threadIndex, objectIndex) -> {
            node.execute(threadIndex);
        });
    }

    @GenerateInline(inherit = true)
    @Introspectable
    abstract static class InlinableTestNode extends Node {

        abstract int execute(Node node, int argument);

    }

    abstract static class MultipleInstanceAssumptionCacheNode extends InlinableTestNode {

        @SuppressWarnings("unused")
        @Specialization(guards = "value == cachedValue", limit = "3", assumptions = "a")
        int s0(int value, @Cached("value") int cachedValue,
                        @Cached Assumption a,
                        @Cached(inline = false) CachedNode cachedNode) {
            assertNotEquals(0, cachedValue);
            assertEquals(this, cachedNode.getParent());
            return value;
        }

    }

    @Test
    public void testMultipleInstanceAssumptionCacheNode() throws InterruptedException {
        assertInParallel(SingleInstanceAssumptionNodeGen::create, (node, threadIndex, objectIndex) -> {
            node.execute(node, threadIndex % 3 + 1);
            if (objectIndex == THREADS / 2) {
                // invalidate once in between
                SpecializationInfo info = Introspection.getSpecialization(node, "s0");
                if (info.getInstances() > 0) {
                    List<Object> cacheData = info.getCachedData(threadIndex % info.getInstances());
                    if (!cacheData.isEmpty()) {
                        ((Assumption) cacheData.get(0)).invalidate();
                    }
                }
            }
        });
    }

    @GenerateInline
    @Introspectable
    abstract static class SingleInstanceAssumptionNode extends InlinableTestNode {

        @SuppressWarnings("truffle-assumption")
        @Specialization(assumptions = "cachedAssumption")
        int s0(int value, @Cached(neverDefault = false) Assumption cachedAssumption) {
            assertNotNull(cachedAssumption);
            return value;
        }

    }

    @Test
    public void testSingleInstanceAssumptionNode() throws InterruptedException {
        assertInParallel(SingleInstanceAssumptionNodeGen::create, (node, threadIndex, objectIndex) -> {
            if (objectIndex == THREADS / 2) {
                // invalidate once in between
                SpecializationInfo info = Introspection.getSpecialization(node, "s0");
                if (info.getInstances() > 0) {
                    List<Object> cacheData = info.getCachedData(threadIndex % info.getInstances());
                    if (!cacheData.isEmpty()) {
                        ((Assumption) cacheData.get(0)).invalidate();
                    }
                }
            }
            node.execute(node, threadIndex % 3 + 1);
        });
    }

    @GenerateInline
    abstract static class MultiInstanceCacheNode extends InlinableTestNode {

        @Specialization(guards = "value == cachedValue", limit = "3")
        int s0(int value, @Cached("value") int cachedValue) {
            assertNotEquals(0, cachedValue);
            return value;
        }
    }

    @Test
    public void testMultiInstanceCacheNode() throws InterruptedException {
        assertInParallel(MultiInstanceCacheNodeGen::create, (node, threadIndex, objectIndex) -> {
            node.execute(node, threadIndex % 3 + 1);
        });
    }

    abstract static class CachedNode extends InlinableTestNode {

        @Specialization
        int s0(int value) {
            return value;
        }
    }

    @SuppressWarnings("unused")
    abstract static class MultiInstanceNodeCacheNode extends InlinableTestNode {

        @Specialization(guards = "value == cachedValue", limit = "3")
        static int s0(Node node, int value, @Cached("value") int cachedValue,
                        @Cached(inline = false) CachedNode cachedNode) {
            assertNotEquals(0, cachedValue);
            assertNotNull(node);
            assertEquals(node, cachedNode.getParent());
            cachedNode.execute(node, 42);
            return value;
        }
    }

    @Test
    public void testMultiInstanceNodeCacheNode() throws InterruptedException {
        assertInParallel(MultiInstanceNodeCacheNodeGen::create, (node, threadIndex, objectIndex) -> {
            node.execute(node, threadIndex % 3 + 1);
        });
    }

    @SuppressWarnings("unused")
    abstract static class UseMultiInstanceNodeCacheNode extends InlinableTestNode {

        @Specialization
        static int s0(Node node, int value, @Cached MultiInstanceNodeCacheNode cachedNode) {
            assertNotNull(node);
            return cachedNode.execute(node, 42);
        }
    }

    @Test
    public void testUseMultiInstanceNodeCacheNode() throws InterruptedException {
        assertInParallel(UseMultiInstanceNodeCacheNodeGen::create, (node, threadIndex, objectIndex) -> {
            node.execute(node, threadIndex % 3 + 1);
        });
    }

    @GenerateInline
    abstract static class SingleInstancePrimitiveCacheNode extends InlinableTestNode {

        @Specialization(guards = "value == cachedValue", limit = "1")
        int s0(int value, @Cached(value = "value") int cachedValue) {
            assertNotEquals(0, cachedValue);
            return value;
        }

    }

    @Test
    public void testSingleInstancePrimitiveCacheNode() throws InterruptedException {
        assertInParallel(SingleInstancePrimitiveCacheNodeGen::create, (node, threadIndex, objectIndex) -> {
            node.execute(node, 1);
        });

        SingleInstancePrimitiveCacheNode node = adoptNode(SingleInstancePrimitiveCacheNodeGen.create()).get();
        assertFails(() -> node.execute(null, 0), AssertionError.class);
    }

    @GenerateInline
    abstract static class SingleInstanceSharedCacheNode extends InlinableTestNode {

        @Specialization(guards = "guardNode.execute(value)")
        int s0(int value, @SuppressWarnings("unused") @Shared @Cached InnerGuardNode guardNode) {
            assertNotNull(guardNode);
            return value;
        }

        @Specialization
        int s1(int value, @SuppressWarnings("unused") @Shared @Cached InnerGuardNode guardNode) {
            fail("must not fallthrough");
            return value;
        }

    }

    @Test
    public void testSingleInstanceSharedCacheNode() throws InterruptedException {
        assertInParallel(SingleInstanceSharedCacheNodeGen::create, (node, threadIndex, objectIndex) -> {
            node.execute(node, 0);
        });
    }

    @GenerateInline
    abstract static class SingleInstanceLibraryCacheNode extends InlinableTestNode {

        @Specialization(guards = "value == 1", limit = "1")
        int s0(int value, @CachedLibrary("value") InteropLibrary interop,
                        @Cached RegularNode node) {
            assertNotNull(interop);
            assertNotNull(node);
            return value;
        }

        @Specialization(guards = {"value == 1 || value == 2"}, replaces = "s0")
        int s1(int value, @Cached RegularNode node) {
            assertNotNull(node);
            return value;
        }

        @Specialization
        int s1(int value) {
            fail("must not fallthrough");
            return value;
        }

    }

    @Test
    public void testSingleInstanceLibraryCacheNode() throws InterruptedException {
        assertInParallel(SingleInstanceLibraryCacheNodeGen::create, (node, threadIndex, objectIndex) -> {
            node.execute(node, threadIndex % 2 + 1);
        });
    }

    @SuppressWarnings("truffle-inlining")
    abstract static class GuardCacheNode extends Node {

        abstract boolean execute(int argument);

        @Specialization
        boolean s0(int value) {
            return value == 1;
        }

        static volatile boolean returnNull = false;

        @NeverDefault
        static GuardCacheNode create() {
            if (returnNull) {
                return null;
            }
            return GuardCacheNodeGen.create();
        }
    }

    @GenerateInline
    abstract static class SingleInstanceNodeCacheNode extends InlinableTestNode {

        @Specialization(guards = {"value == 1"})
        int s0(int value, @Cached(inline = false) GuardCacheNode cachedNode) {
            assertNotNull(cachedNode);
            assertTrue(cachedNode.execute(value));
            return value;
        }

    }

    @Test
    public void testSingleInstanceNodeCacheNode() throws InterruptedException {
        GuardCacheNode.returnNull = false;
        assertInParallel(SingleInstanceNodeCacheNodeGen::create, (node, threadIndex, objectIndex) -> {
            node.execute(node, 1);
        });

        SingleInstanceNodeCacheNode node = adoptNode(SingleInstanceNodeCacheNodeGen.create()).get();
        node.execute(node, 1);

        SingleInstanceNodeCacheNode returnNull = adoptNode(SingleInstanceNodeCacheNodeGen.create()).get();
        GuardCacheNode.returnNull = true;
        assertFails(() -> returnNull.execute(null, 1), NullPointerException.class, (e) -> {
            assertEquals("A specialization cache returned a default value. The cache initializer must never return a default value for this cache. " +
                            "Use @Cached(neverDefault=false) to allow default values for this cached value or make sure the cache initializer never returns the default value.",
                            e.getMessage());
        });
        GuardCacheNode.returnNull = false;
    }

    @GenerateInline
    @Introspectable
    abstract static class ReplaceThisNode extends InlinableTestNode {

        @Specialization(guards = "value == cachedValue", limit = "3", rewriteOn = ControlFlowException.class)
        int s0(int value, @Cached("value") int cachedValue) throws ControlFlowException {
            assertNotEquals(0, cachedValue);
            if (cachedValue == 2) {
                throw new ControlFlowException();
            }
            return value;
        }

        @Specialization
        int s1(int value) {
            return value;
        }

    }

    @Test
    public void testReplaceThisNode() throws InterruptedException {
        assertInParallel(ReplaceThisNodeGen::create, (node, threadIndex, objectIndex) -> {
            node.execute(node, threadIndex);
        });
    }

    @GenerateInline
    abstract static class ReplaceThisNoCacheNode extends InlinableTestNode {

        @Specialization(rewriteOn = ControlFlowException.class)
        int s0(int value) throws ControlFlowException {
            if (value == 2) {
                throw new ControlFlowException();
            }
            return value;
        }

        @Specialization
        int s1(int value) {
            return value;
        }

    }

    @Test
    public void testReplaceThisNoCacheNode() throws InterruptedException {
        assertInParallel(ReplaceThisNoCacheNodeGen::create, (node, threadIndex, objectIndex) -> {
            node.execute(node, threadIndex);
        });
    }

    /*
     * This test triggers multiple guards bits with the fallback
     */
    @SuppressWarnings("truffle-inlining")
    abstract static class FallbackManyCachesNode extends Node {

        abstract int execute(int argument);

        @Specialization(guards = {"guardNode1.execute(obj)", "guardNode2.execute(obj)", "guardNode3.execute(obj)"}, limit = "1")
        protected int s1(@SuppressWarnings("unused") int obj,
                        @Exclusive @Cached GuardCacheNode guardNode1,
                        @Exclusive @Cached GuardCacheNode guardNode2,
                        @Exclusive @Cached GuardCacheNode guardNode3,
                        @Exclusive @Cached GuardCacheNode unboundGuard) {
            assertNotNull(guardNode1);
            assertNotNull(guardNode2);
            assertNotNull(guardNode3);
            assertNotNull(unboundGuard);
            return 0;
        }

        @Fallback
        protected int fallback(@SuppressWarnings("unused") int x) {
            return 1;
        }

        static GuardNode createGuard() {
            return new GuardNode();
        }
    }

    @Test
    public void testFallbackManyCachesNode() throws InterruptedException {
        assertInParallel(FallbackManyCachesNodeGen::create, (node, threadIndex, objectIndex) -> {
            node.execute(threadIndex);
        });
    }

    abstract static class FallbackWithCacheClass extends Node {
        abstract boolean execute(Object obj);

        @Idempotent
        static boolean myGuard(Object obj) {
            return !(obj instanceof Integer);
        }

        @Specialization(guards = {"obj.getClass() == cachedClass", "myGuard(cachedClass)"}, limit = "1")
        static boolean doItCached(@SuppressWarnings("unused") Object obj,
                        @Cached("obj.getClass()") @SuppressWarnings("unused") Class<?> cachedClass) {
            return myGuard(cachedClass);
        }

        @Fallback
        @SuppressWarnings("unused")
        protected static boolean fromObjectGeneric(Object value) {
            return true;
        }
    }

    @Test
    public void testFallbackWithCacheClass() throws InterruptedException {
        assertInParallel(FallbackWithCacheClassNodeGen::create, (node, threadIndex, objectIndex) -> {
            Object arg;
            switch (threadIndex % 2) {
                case 0:
                    arg = "";
                    break;
                case 1:
                    arg = 42;
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
            assertTrue(node.execute(arg));
        });
    }

    @SuppressWarnings("unused")
    public abstract static class InnerGuardNode extends Node {

        abstract boolean execute(int value);

        @Specialization(guards = "value == 0")
        protected boolean s0(int value) {
            return true;
        }

        @Specialization(guards = "value == 1")
        protected boolean s1(int value) {
            return false;
        }
    }

    public abstract static class RegularNode extends Node {

        abstract int execute(int value);

        @Specialization
        protected int s1(int value) {
            return value + 1;
        }

    }

    /*
     * Tests special race discovered in JSRegExpExecIntlNode.
     */
    @SuppressWarnings("unused")
    public abstract static class CachedGuardAndFallbackNode extends Node {

        abstract String execute(Object thisObject);

        @SuppressWarnings("truffle-unused")
        @Specialization(guards = {"guardNode1.execute(arg)", "guardNode2.execute(arg)"}, limit = "1")
        protected String s0(int arg,
                        @Cached InnerGuardNode guardNode1,
                        @Cached InnerGuardNode guardNode2,
                        @Cached(neverDefault = false) RegularNode regularNode) {
            // this may be null if the cache guard is partially initialized
            assertNotNull(guardNode1);
            assertNotNull(guardNode2);
            assertNotNull(regularNode);
            return "s0";
        }

        @Fallback
        protected String fallback(Object thisNonObj) {
            return "fallback";
        }

    }

    @Test
    public void testCachedGuardAndFallbackNode() throws InterruptedException {
        assertInParallel(CachedGuardAndFallbackNodeGen::create, (node, threadIndex, objectIndex) -> {
            node.execute(threadIndex % 2);
        });
    }

    /*
     * Compilation only test.
     */
    @SuppressWarnings({"unused", "truffle-unused"})
    public abstract static class SharedWrapperWithBoundaryNode extends Node {

        public abstract Object execute(Object arg0);

        @Specialization
        protected Object oneArg(int arg0,
                        @Cached(neverDefault = false) @Shared("shared") GuardCacheNode sharedNode) {
            return arg0;
        }

        /*
         * This specialization forces a boundary method in combination with a shared wrapper.
         */
        @Specialization(limit = "3")
        protected Object twoArgs(double arg0,
                        @CachedLibrary("arg0") InteropLibrary interop,
                        @Cached(neverDefault = false) @Shared("shared") GuardCacheNode sharedNode) {
            return arg0;
        }

    }

    @SuppressWarnings({"unused", "truffle-unused"})
    public abstract static class SharedWrapperInFallbackNode extends Node {

        public abstract Object execute(Object arg0);

        @Specialization(guards = {"arg0 == 2", "guardNode.execute(arg0)"}, limit = "1")
        protected Object oneArg(int arg0,
                        @Shared("guard") @Cached(neverDefault = false) GuardCacheNode guardNode) {
            return arg0;
        }

        @Specialization(guards = {"arg0 == 1", "guardNode.execute(arg0)"}, limit = "1")
        protected Object twoArg(int arg0,
                        @Shared("guard") @Cached(neverDefault = false) GuardCacheNode guardNode) {
            return arg0;
        }

        /*
         * This specialization forces a boundary method in combination with a shared wrapper.
         */
        @Fallback
        protected Object twoArgs(Object arg0) {
            return arg0;
        }

    }

    public abstract static class ObjectInitializationInCachedValue extends Node {
        abstract boolean execute(Object obj1);

        static Cache getCache(int val) {
            Cache c = new Cache();
            c.value = val;
            return c;
        }

        @Specialization(guards = "value == 0")
        static boolean s0(int value,
                        @Cached(value = "getCache(value)", neverDefault = true) Cache cache) {
            assertNotNull(cache.value);
            return value >= 0;
        }

        @Specialization(guards = "value == 1")
        static boolean s1(int value,
                        @Cached(value = "getCache(value)", neverDefault = false) Cache cache) {
            assertNotNull(cache.value);
            return value >= 0;
        }

    }

    @Test
    public void testObjectInitializationInCachedValue() throws InterruptedException {
        assertInParallel(ObjectInitializationInCachedValueNodeGen::create, (node, threadIndex, objectIndex) -> {
            node.execute(threadIndex % 2);
        });
    }

    static final class Cache {
        public /* not final! */ Integer value;

        public boolean guard(int other) {
            return value.intValue() == other;
        }
    }

    public abstract static class CacheCleanupNode extends Node {
        abstract boolean execute(int v);

        static Cache getCache(int v) {
            Cache c = new Cache();
            c.value = v;
            return c;
        }

        @Specialization(guards = "v == 0")
        static boolean s0(int v,
                        @Cached(value = "getCache(v)", neverDefault = true) Cache cache) {
            assertNotNull(cache.value);
            return v >= 0;
        }

        @Specialization(guards = "v == 1")
        static boolean s1(int v,
                        @Cached(value = "getCache(v)", neverDefault = false) Cache cache) {
            assertNotNull(cache.value);
            return v >= 0;
        }

        @Specialization(guards = "v == 2")
        static boolean s2(int v,
                        @Shared("cacheDefault") @Cached(value = "getCache(2)", neverDefault = false) Cache cache) {
            assertNotNull(cache.value);
            return v >= 0;
        }

        @Specialization(guards = "v == 3")
        static boolean s3(int v,
                        @Shared("cacheDefault") @Cached(value = "getCache(2)", neverDefault = false) Cache cache) {
            assertNotNull(cache.value);
            return v >= 0;
        }

        @Specialization(guards = "v == 4")
        static boolean s4(int v,
                        @Shared("cacheNeverDefault") @Cached(value = "getCache(3)", neverDefault = true) Cache cache) {
            assertNotNull(cache.value);
            return v >= 0;
        }

        @Specialization(guards = "v == 5")
        static boolean s5(int v,
                        @Shared("cacheNeverDefault") @Cached(value = "getCache(3)", neverDefault = true) Cache cache) {
            assertNotNull(cache.value);
            return v >= 0;
        }

        @Specialization(guards = "v == 6")
        static boolean s6(int v,
                        @Shared("cacheSharedNonReplaced") @Cached(value = "getCache(3)", neverDefault = true) Cache cache) {
            assertNotNull(cache.value);
            return v >= 0;
        }

        @Specialization(guards = "v == 7")
        static boolean s7(int v,
                        @Shared("cacheSharedNonReplaced") @Cached(value = "getCache(3)", neverDefault = true) Cache cache) {
            assertNotNull(cache.value);
            return v >= 0;
        }

        /*
         * Deliberately not replacing s7 to test that shared are not cleaned in that case.
         */
        @Specialization(guards = "v == 8", replaces = {"s0", "s1", "s2", "s3", "s4", "s5", "s6"})
        static boolean s8(int v) {
            return v >= 0;
        }

    }

    @Test
    public void testCacheCleanupNode() throws IllegalArgumentException, IllegalAccessException {
        CacheCleanupNode node = CacheCleanupNodeGen.create();
        for (int i = 0; i < 9; i++) {
            node.execute(i);
        }

        int fieldCount = 0;
        for (Field f : node.getClass().getDeclaredFields()) {
            ReflectionUtils.setAccessible(f, true);
            if (f.getType() != int.class) {
                fieldCount++;
                if (f.getName().equals("cacheSharedNonReplaced")) {
                    assertNotNull(f.get(node));
                } else {
                    assertNull(f.get(node));
                }
            }
        }
        assertEquals(5, fieldCount);
    }

    static final int NODES = 10;
    static final int THREADS = 10;

    private <T extends Node> void assertInParallel(Supplier<T> nodeFactory, ParallelObjectConsumer<T> assertions) throws InterruptedException {
        final int threads = THREADS;
        final int threadPools = 4;
        final int iterations = 1000;
        /*
         * We create multiple nodes and run the assertions in a loop to avoid implicit
         * synchronization through the synchronization primitives when running the assertions just
         * for a single node.
         */
        final int nodesCount = NODES;
        assertNodeInParallel(nodeFactory, assertions, threadPools, threads, iterations, nodesCount);
    }

    @SuppressWarnings("truffle-inlining")
    abstract static class WarningNeverDefaultForSharedCachesNode extends TestNode {

        @Specialization(guards = "value == 1")
        int s0(int value,
                        @ExpectError("It is recommended to set the @Cached(neverDefault=true|false) property for this cache expression%") //
                        @Shared("a") @Cached(value = "value") int cachedValue) {
            assertNotEquals(0, cachedValue);
            return value;
        }

        @Specialization
        int s1(int value,
                        @ExpectError("It is recommended to set the @Cached(neverDefault=true|false) property for this cache expression%") //
                        @Shared("a") @Cached("value") int cachedValue) {
            /*
             * The invariant is that the cached values are never 0.
             */
            assertNotEquals(0, cachedValue);
            return value;
        }

    }

    @SuppressWarnings("truffle-inlining")
    abstract static class WarningNeverSharedBeneficial1Node extends Node {

        abstract long execute(Object argument);

        @Specialization(guards = "value == 1")
        @SuppressWarnings("unused")
        long s1(long value,
                        @ExpectError("It is recommended to set the @Cached(neverDefault=true|false) property for this cache expression to allow the DSL to further optimize the generated layout of this node%") //
                        @Cached("value") long cachedValue0) {
            return value;
        }

    }

    @SuppressWarnings("truffle-inlining")
    abstract static class WarningNeverSharedBeneficial2Node extends Node {

        abstract int execute(Object argument);

        @Specialization
        int s0(int value,
                        @ExpectError("It is recommended to set the @Cached(neverDefault=true|false) property for this cache expression to allow the DSL to further optimize the generated layout of this node%") //
                        @Cached("value") int cachedValue1,
                        @ExpectError("It is recommended to set the @Cached(neverDefault=true|false) property for this cache expression to allow the DSL to further optimize the generated layout of this node%") //
                        @Cached("value") int cachedValue2) {
            assertNotEquals(0, cachedValue1);
            assertNotEquals(0, cachedValue2);
            return value;
        }

    }

    @SuppressWarnings("truffle-inlining")
    abstract static class WarningNeverSharedBeneficial3Node extends Node {

        abstract int execute(Object argument);

        @SuppressWarnings("unused")
        @Specialization(guards = "value == 1")
        int s0(int value,
                        // three fields generates a specialization class, so no warning needed.
                        @Cached SharedDefaultIntNode cachedValue1,
                        @Cached SharedDefaultIntNode cachedValue2,
                        @Cached(value = "value") int cachedValue3,
                        @Cached(value = "value") int cachedValue4,
                        @Cached(value = "value") int cachedValue5,
                        @ExpectError("The @Cached(neverDefault=true|false) property is not needed to be set. %") //
                        @Cached(value = "value", neverDefault = true) int cachedValue0) {
            return value;
        }

        @Specialization(guards = "value == 2")
        int s2(int value) {
            return value;
        }

        @Specialization(guards = "value == 3")
        int s3(int value) {
            return value;
        }

    }

    @SuppressWarnings("truffle-inlining")
    abstract static class NeverDefaultGuaranteedAnnotationMethod extends Node {

        abstract int execute(Object argument);

        @SuppressWarnings("unused")
        @Specialization
        int s0(int value,
                        @ExpectError("The @Cached(neverDefault=true|false) property is guaranteed or implied by the initializer expression.%") //
                        @Cached(value = "neverDefault(value)", neverDefault = true) int cachedValue0) {
            assertNotEquals(0, cachedValue0);
            return cachedValue0;
        }

        @NeverDefault
        int neverDefault(int v) {
            return v;
        }

    }

    abstract static class NeverDefaultGuaranteedAnnotationField extends Node {

        @NeverDefault int neverDefault = 42;

        abstract int execute(Object argument);

        @SuppressWarnings("unused")
        @Specialization
        int s0(int value,
                        @ExpectError("The @Cached(neverDefault=true|false) property is guaranteed or implied by the initializer expression.%") //
                        @Cached(value = "neverDefault", neverDefault = true) int cachedValue0) {
            assertNotEquals(0, cachedValue0);
            return cachedValue0;
        }

    }

    @SuppressWarnings("truffle-inlining")
    abstract static class NeverDefaultGuaranteedConstant1 extends Node {

        abstract int execute(Object argument);

        @SuppressWarnings("unused")
        @Specialization
        int s0(int value,
                        @ExpectError("The @Cached(neverDefault=true|false) property is guaranteed or implied by the initializer expression.%") //
                        @Cached(value = "42", neverDefault = true) int cachedValue) {
            return cachedValue;
        }

    }

    @SuppressWarnings("truffle-inlining")
    abstract static class NeverDefaultGuaranteedConstant2 extends Node {

        abstract int execute(Object argument);

        @SuppressWarnings("unused")
        @Specialization
        int s0(int value,
                        // constant 0 needs a default value
                        @Cached(value = "0", neverDefault = true) int cachedValue) {
            return cachedValue;
        }

    }

    @SuppressWarnings("truffle-inlining")
    abstract static class NeverDefaultGuaranteedConstructor extends Node {

        abstract int execute(Object argument);

        @SuppressWarnings("unused")
        @Specialization
        int s0(int value,
                        @ExpectError("The @Cached(neverDefault=true|false) property is guaranteed or implied by the initializer expression.%") //
                        @Cached(value = "new()", neverDefault = true) MyObject cached) {
            return value;
        }

        static class MyObject {

        }

    }

    @SuppressWarnings("truffle-inlining")
    abstract static class NeverDefaultGuaranteedPrimitiveBoxed extends Node {

        abstract int execute(Object argument);

        @SuppressWarnings("unused")
        @Specialization
        int s0(int value,
                        @ExpectError("The @Cached(neverDefault=true|false) property is guaranteed or implied by the initializer expression.%") //
                        @Cached(value = "value", neverDefault = true) Integer cached) {
            return value;
        }

    }

    @SuppressWarnings({"truffle-inlining", "unused"})
    abstract static class ErrorSharedNeverDefaultInconsistentNode extends TestNode {

        @Specialization(guards = "value == 1")
        int s0(int value,
                        @ExpectError("Could not share some of the cached parameters in group 'a': %") //
                        @Shared("a") @Cached(value = "value", neverDefault = true) int cachedValue) {
            return value;
        }

        @Specialization
        int s1(int value,
                        @ExpectError("Could not share some of the cached parameters in group 'a': %") //
                        @Shared("a") @Cached(value = "value", neverDefault = false) int cachedValue) {
            return value;
        }
    }

    @ImportStatic(CompilerDirectives.class)
    abstract static class ProfileArgumentNode extends Node {

        abstract Object execute(Object arg);

        @Specialization(guards = "value == cachedValue", limit = "1")
        @SuppressWarnings("unused")
        protected boolean cacheBoolean(boolean value,
                        // test: does not cause a never-default warning
                        @Cached("value") boolean cachedValue) {
            return cachedValue;
        }
    }

    @SuppressWarnings({"truffle-inlining", "unused"})
    abstract static class NeverDefaultFrameDescriptorWarningNode extends Node {

        abstract Object execute(VirtualFrame frame, Object arg);

        @Specialization
        int s0(VirtualFrame frame, int value,
                        @ExpectError("The @Cached(neverDefault=true|false) property is guaranteed%") //
                        @Cached(value = "frame.getFrameDescriptor()", neverDefault = true) FrameDescriptor cachedValue) {
            return value;
        }

    }

    static final class AllocatableObject {
    }

    // tests GR-43642
    abstract static class NeverDefaultWithLibraryWarningNode extends Node {

        abstract Object execute(Object arg);

        @SuppressWarnings("unused")
        @Specialization(limit = "3")
        Object s0(Object receiver,
                        @CachedLibrary("receiver") InteropLibrary receivers,
                        @Cached(value = "new()") AllocatableObject cachedValue) {
            return receiver;
        }

    }

}
