/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.ExecuteTracingSupport;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GeneratePackagePrivate;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.InlineSupport.InlineTarget;
import com.oracle.truffle.api.dsl.InlineSupport.RequiredField;
import com.oracle.truffle.api.dsl.InlineSupport.StateField;
import com.oracle.truffle.api.dsl.Introspectable;
import com.oracle.truffle.api.dsl.Introspection;
import com.oracle.truffle.api.dsl.Introspection.SpecializationInfo;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.dsl.test.GenerateInlineTestFactory.CustomInline1NodeGen;
import com.oracle.truffle.api.dsl.test.GenerateInlineTestFactory.CustomInline2NodeGen;
import com.oracle.truffle.api.dsl.test.GenerateInlineTestFactory.ErrorRuntimeUsageNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateInlineTestFactory.InlineReplaceNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateInlineTestFactory.InlineRewriteOnNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateInlineTestFactory.InlinedByDefaultCachedNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateInlineTestFactory.InlinedUsageNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateInlineTestFactory.MultiInstanceInlineWithGenericNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateInlineTestFactory.MultiInstanceInliningNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateInlineTestFactory.MultiInstanceMixedInliningNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateInlineTestFactory.PassNodeAndFrameNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateInlineTestFactory.ReplaceNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateInlineTestFactory.RewriteOnNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateInlineTestFactory.SharedAndNonSharedInlinedMultipleInstances1NodeGen;
import com.oracle.truffle.api.dsl.test.GenerateInlineTestFactory.SharedAndNonSharedInlinedMultipleInstances2NodeGen;
import com.oracle.truffle.api.dsl.test.GenerateInlineTestFactory.SharedProfileInSpecializationClassNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateInlineTestFactory.SpecializationClassAndInlinedNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateInlineTestFactory.Use128BitsNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateInlineTestFactory.Use2048BitsNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateInlineTestFactory.Use32BitsNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateInlineTestFactory.Use512BitsNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateInlineTestFactory.UseAssumptionCacheNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateInlineTestFactory.UseBindInInlinedNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateInlineTestFactory.UseCustomInlineNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateInlineTestFactory.UseDoNotInlineInlinableNodeNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateInlineTestFactory.UseFailEarlyNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateInlineTestFactory.UseInheritedInlinedByDefaultInCachedNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateInlineTestFactory.UseInlineInlineCacheNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateInlineTestFactory.UseInlineSharedWithSpecializationClassNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateInlineTestFactory.UseInlinedAdoptNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateInlineTestFactory.UseInlinedByDefaultAndForceCachedVersionNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateInlineTestFactory.UseInlinedByDefaultAndForceInlineVersionNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateInlineTestFactory.UseInlinedByDefaultInCachedNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateInlineTestFactory.UseInlinedByDefaultInCachedWithAlwaysInlineCachedAndGenerateInlineNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateInlineTestFactory.UseInlinedByDefaultInCachedWithAlwaysInlineCachedAndGenerateInlineUserNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateInlineTestFactory.UseInlinedByDefaultInCachedWithAlwaysInlineCachedNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateInlineTestFactory.UseInlinedByDefaultInInlineOnlyUserNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateInlineTestFactory.UseInlinedNodeInGuardNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateInlineTestFactory.UseIntrospectionNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateInlineTestFactory.UseMixedAndInlinedNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateInlineTestFactory.UseNoStateNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateInlineTestFactory.UseTracingNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateInlineTestFactory.UseUncachedEnculapsingNodeGen;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString.CompactionLevel;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;

@SuppressWarnings({"truffle-neverdefault", "truffle-sharing", "truffle-interpreted-performance"})
public class GenerateInlineTest extends AbstractPolyglotTest {

    @GenerateInline
    @SuppressWarnings("unused")
    public abstract static class SingleBitNode extends Node {

        abstract int execute(Node node, int v);

        @Specialization(guards = "v == 1")
        static int s0(Node node, int v) {
            return v;
        }

    }

    @GenerateInline
    @SuppressWarnings("unused")
    public abstract static class TwoBitNode extends Node {

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
    @SuppressWarnings("unused")
    @GenerateUncached
    public abstract static class FourBitNode extends Node {

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

        @Specialization(guards = "v == 3")
        static int s3(Node node, int v) {
            return 4;
        }

    }

    @GenerateInline
    @SuppressWarnings("unused")
    @GenerateUncached
    public abstract static class NotInlineFourBitNode extends Node {

        abstract int execute(Node node, int v);

        @Specialization
        static int s0(Node node, int v, @Cached(inline = false) FourBitNode bitNode) {
            return bitNode.execute(bitNode, v);
        }

    }

    @GenerateInline
    @SuppressWarnings("unused")
    @GenerateUncached
    public abstract static class InlinableInlineCacheNode extends Node {

        abstract int execute(Node node, int v);

        @Specialization(guards = "v == cachedV", limit = "3")
        static int s0(Node node, int v,
                        @Cached("v") int cachedV,
                        @Cached FourBitNode bitNode) {
            return bitNode.execute(node, v);
        }

    }

    public abstract static class UseStateNode extends Node {

        abstract int execute(Node node);

    }

    @GenerateInline
    @SuppressWarnings("unused")
    @DisableStateBitWidthModfication
    public abstract static class Use32BitsNode extends UseStateNode {

        @Specialization
        static int doInt(Node node,
                        @Cached FourBitNode p0,
                        @Cached FourBitNode p1,
                        @Cached FourBitNode p2,
                        @Cached FourBitNode p3,
                        @Cached FourBitNode p4,
                        @Cached FourBitNode p5,
                        @Cached FourBitNode p6,
                        @Cached FourBitNode p7) {
            int sum = 0;
            for (int i = 0; i < 4; i++) {
                sum += p0.execute(node, i);
                sum += p1.execute(node, i);
                sum += p2.execute(node, i);
                sum += p3.execute(node, i);
                sum += p4.execute(node, i);
                sum += p5.execute(node, i);
                sum += p6.execute(node, i);
                sum += p7.execute(node, i);
            }

            return sum;
        }

    }

    @GenerateInline
    @SuppressWarnings("unused")
    @DisableStateBitWidthModfication
    public abstract static class Use128BitsNode extends UseStateNode {

        @Specialization
        static int doInt(Node node,
                        @Cached Use32BitsNode p0,
                        @Cached Use32BitsNode p1,
                        @Cached Use32BitsNode p2,
                        @Cached Use32BitsNode p3) {
            int sum = 0;
            sum += p0.execute(node);
            sum += p1.execute(node);
            sum += p2.execute(node);
            sum += p3.execute(node);
            return sum;
        }
    }

    @GenerateInline
    @SuppressWarnings("unused")
    @DisableStateBitWidthModfication
    public abstract static class Use512BitsNode extends UseStateNode {

        @Specialization
        static int doInt(Node node,
                        @Cached Use128BitsNode p0,
                        @Cached Use128BitsNode p1,
                        @Cached Use128BitsNode p2,
                        @Cached Use128BitsNode p3) {
            int sum = 0;
            sum += p0.execute(node);
            sum += p1.execute(node);
            sum += p2.execute(node);
            sum += p3.execute(node);
            return sum;
        }
    }

    @GenerateInline
    @SuppressWarnings("unused")
    @DisableStateBitWidthModfication
    public abstract static class Use2048BitsNode extends UseStateNode {

        @Specialization
        static int doInt(Node node,
                        @Cached Use512BitsNode p0,
                        @Cached Use512BitsNode p1,
                        @Cached Use512BitsNode p2,
                        @Cached Use512BitsNode p3) {
            int sum = 0;
            sum += p0.execute(node);
            sum += p1.execute(node);
            sum += p2.execute(node);
            sum += p3.execute(node);
            return sum;
        }
    }

    @Test
    public void testUseBitsState() throws IllegalArgumentException, IllegalAccessException {
        assertUseStateFields(Use32BitsNodeGen.create(), 1);
        assertUseStateFields(Use128BitsNodeGen.create(), 4);
        assertUseStateFields(Use512BitsNodeGen.create(), 16);
        assertUseStateFields(Use2048BitsNodeGen.create(), 64);
    }

    @SuppressWarnings("unused")
    @GenerateInline(false)
    public abstract static class InlineCacheTest extends Node {

        abstract int execute(int arg);

        @Specialization(guards = "arg == cachedArg", limit = "3")
        static int doInt(int arg,
                        @Bind("this") Node node,
                        @Cached("arg") int cachedArg,
                        @Cached InlinedBranchProfile p0) {
            p0.enter(node);
            return cachedArg;
        }

    }

    private static void assertUseStateFields(UseStateNode node, final int expectedFields) throws IllegalAccessException {
        List<Field> fields = collectStateFields(node.getClass(), true);
        assertEquals(expectedFields, fields.size());

        for (Field field : fields) {
            field.setAccessible(true);
        }

        // assert no bits set
        for (Field field : fields) {
            assertEquals(0, (int) field.get(node));
        }

        assertEquals(expectedFields * 80, node.execute(node));

        // assert all bits set
        for (Field field : fields) {
            assertEquals(0xFFFFFFFF, (int) field.get(node));
        }
    }

    static List<Field> collectStateFields(Class<?> c, boolean assertNoOtherFields) {
        List<Field> fields = new ArrayList<>();
        for (Field f : c.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            if (f.getName().startsWith("state")) {
                fields.add(f);
            } else if (assertNoOtherFields) {
                throw new AssertionError("Unexpected field found " + f);
            }
        }
        return fields;
    }

    /*
     * Tests the combination of inlined branch profiles and non-inlined branch profiles. The three
     * branch profile instances also trigger a specialization data class.
     */
    @GenerateInline(false)
    public abstract static class SpecializationClassAndInlinedNode extends Node {

        abstract int execute();

        @Specialization
        int doInt(@Cached(inline = true) SingleBitNode p0,
                        @Cached(inline = true) SingleBitNode p1,
                        @Cached(inline = false) SingleBitNode p2,
                        @Cached(inline = false) SingleBitNode p3,
                        @Cached(inline = false) SingleBitNode p4) {
            int sum = 0;
            sum += p0.execute(this, 1);
            sum += p1.execute(this, 1);
            sum += p2.execute(p2, 1);
            sum += p3.execute(p3, 1);
            sum += p4.execute(p4, 1);
            return sum;
        }
    }

    @Test
    public void testSpecializationClassAndInlined() {
        assertEquals(5, SpecializationClassAndInlinedNodeGen.create().execute());
    }

    @GenerateInline
    public abstract static class ErrorRuntimeNode extends Node {

        abstract void execute(Node node);

        @Specialization
        @SuppressWarnings("unused")
        static void doInt(Node node, @Cached InnerNode simpleNode) {
            /*
             * This is wrong on purpose to test its behavior (not crashing).
             */
            simpleNode.execute(node, 42);
        }

    }

    @GenerateInline(false)
    public abstract static class ErrorRuntimeUsageNode extends Node {

        abstract void execute();

        @Specialization
        void doInt(@Cached(inline = true) ErrorRuntimeNode simpleNode) {
            simpleNode.execute(simpleNode);
        }

    }

    @Test
    public void testErrorRuntimeUsage() {
        assertFails(() -> ErrorRuntimeUsageNodeGen.create().execute(), ClassCastException.class, (e) -> {
            assertEquals("Invalid inline context node passed to an inlined field. A receiver of type 'GenerateInlineTestFactory.ErrorRuntimeUsageNodeGen' was expected but is 'GenerateInlineTestFactory.ErrorRuntimeNodeGen.Inlined'. " +
                            "Did you pass the wrong node to an execute method of an inlined cached node?",
                            e.getMessage());
        });
    }

    @Test
    public void test() {
        InlinedUsageNode node = adoptNode(InlinedUsageNodeGen.create()).get();

        assertEquals(42, node.execute(42));
        assertEquals(42L, node.execute(42L));
        assertFails(() -> node.execute(""), UnsupportedSpecializationException.class);
    }

    @GenerateUncached
    @SuppressWarnings("unused")
    @GenerateInline(false)
    public abstract static class InlinedUsageNode extends Node {

        abstract Object execute(Object arg);

        @Specialization
        Object doInt(int arg, @Cached(inline = true) SimpleNode simpleNode) {
            return simpleNode.execute(this, arg);
        }

        @Specialization
        Object doLong(long arg, @Shared("simpleNode") @Cached(inline = true) SimpleNode simpleNode) {
            return simpleNode.execute(this, arg);
        }

        @Specialization
        Object doLong(Object arg, @Shared("simpleNode") @Cached(inline = true) SimpleNode simpleNode) {
            return simpleNode.execute(this, arg);
        }

    }

    @GenerateInline
    @GenerateUncached
    @SuppressWarnings("unused")
    public abstract static class SimpleNode extends Node {

        abstract Object execute(Node node, Object arg);

        @Specialization
        static Object doInt(Node node, int arg, @Cached InnerNode innerNode) {
            return innerNode.execute(node, arg);
        }

        @Specialization
        static Object doLong(Node node, long arg, @Cached InnerNode innerNode) {
            return innerNode.execute(node, arg);
        }

        @Specialization
        static Object doObject(Node node, Object arg, @Cached InnerNode innerNode) {
            return innerNode.execute(node, arg);
        }

    }

    @GenerateInline
    @GenerateUncached
    @SuppressWarnings("unused")
    public abstract static class InnerNode extends Node {

        abstract Object execute(Node node, Object arg);

        @Specialization
        static int doInt(Node node, int arg) {
            return arg;
        }

        @Specialization
        static long doLong(Node node, long arg) {
            return arg;
        }

    }

    @Test
    public void testInvalidUsage() {
        InlinedUsageNode node = adoptNode(InlinedUsageNodeGen.create()).get();

        assertEquals(42, node.execute(42));
        assertEquals(42L, node.execute(42L));
        assertFails(() -> node.execute(""), UnsupportedSpecializationException.class);
    }

    @Test
    public void testReplace() {
        ReplaceNode node = adoptNode(ReplaceNodeGen.create()).get();
        assertEquals("s0", node.execute(node, 42));
        assertEquals("s1", node.execute(node, 43));
        assertEquals("s1", node.execute(node, 42));

        InlineReplaceNode useReplace = adoptNode(InlineReplaceNodeGen.create()).get();
        assertEquals("s0", useReplace.execute(42));
        assertEquals("s1", useReplace.execute(43));
        assertEquals("s1", useReplace.execute(42));
    }

    @SuppressWarnings("unused")
    @GenerateInline(false)
    public abstract static class InlineReplaceNode extends Node {

        abstract Object execute(Object arg);

        @Specialization
        Object s0(int arg,
                        @Cached(inline = true) ReplaceNode innerNode) {
            return innerNode.execute(this, arg);
        }

    }

    @GenerateInline
    @SuppressWarnings("unused")
    public abstract static class ReplaceNode extends Node {

        abstract Object execute(Node node, Object arg);

        @Specialization(guards = "arg == 42")
        static String s0(Node node, int arg,
                        @Cached InnerNode innerNode) {
            innerNode.execute(node, arg);
            return "s0";
        }

        @Specialization(replaces = "s0")
        static String s1(Node node, int arg, @Cached InnerNode innerNode) {
            innerNode.execute(node, arg);
            return "s1";
        }

    }

    @Test
    public void testReplaceOn() {
        RewriteOnNode node = adoptNode(RewriteOnNodeGen.create()).get();
        assertEquals("s0", node.execute(node, 42));
        assertEquals("s1", node.execute(node, 43));
        assertEquals("s1", node.execute(node, 42));

        InlineRewriteOnNode useReplace = adoptNode(InlineRewriteOnNodeGen.create()).get();
        assertEquals("s0", useReplace.execute(42));
        assertEquals("s1", useReplace.execute(43));
        assertEquals("s1", useReplace.execute(42));
    }

    @SuppressWarnings("unused")
    @GenerateInline(false)
    public abstract static class InlineRewriteOnNode extends Node {

        abstract Object execute(Object arg);

        @Specialization
        Object s0(int arg,
                        @Cached(inline = true) RewriteOnNode innerNode) {
            return innerNode.execute(this, arg);
        }

    }

    @GenerateInline
    @SuppressWarnings("unused")
    public abstract static class RewriteOnNode extends Node {

        abstract Object execute(Node node, Object arg);

        @Specialization(rewriteOn = ArithmeticException.class)
        static String s0(Node node, int arg,
                        @Cached InnerNode innerNode) {
            if (arg != 42) {
                throw new ArithmeticException();
            }
            innerNode.execute(node, arg);
            return "s0";
        }

        @Specialization(replaces = "s0")
        static String s1(Node node, int arg, @Cached InnerNode innerNode) {
            innerNode.execute(node, arg);
            return "s1";
        }

    }

    @GenerateInline
    @GeneratePackagePrivate
    @DisableStateBitWidthModfication
    public abstract static class CustomInline1Node extends Node {

        abstract int execute(Node node, int value);

        @Specialization(guards = "v >= 0")
        static int doInt(int v) {
            return v;
        }

        @Specialization(guards = "v < 0")
        static int doLong(int v) {
            return -v;
        }

        public static CustomInline1Node inline(@RequiredField(value = StateField.class, bits = 2) InlineTarget updater) {
            return CustomInline1NodeGen.inline(updater);
        }

    }

    @GenerateInline
    @GeneratePackagePrivate
    @DisableStateBitWidthModfication
    public abstract static class CustomInline2Node extends Node {

        abstract int execute(Node node, int value);

        @Specialization(guards = "v >= 0")
        static int doInt(int v) {
            return v;
        }

        @Specialization(guards = "v < 0")
        static int doLong(int v) {
            return -v;
        }

        public static CustomInline2Node inline(@RequiredField(value = StateField.class, bits = 16) InlineTarget updater) {
            return CustomInline2NodeGen.inline(updater);
        }

    }

    @SuppressWarnings({"truffle", "unused"})
    @DisableStateBitWidthModfication
    public abstract static class UseCustomInlineNode extends Node {

        abstract Object execute(int value);

        @Specialization
        int s1(int value,
                        @Cached(inline = true) CustomInline1Node node1,
                        @Cached(inline = true) CustomInline2Node node2) {
            int v;
            v = node1.execute(this, value);
            v = node2.execute(this, v);
            return v;
        }
    }

    @Test
    public void testCustomInlineNode() {
        UseCustomInlineNode node = adoptNode(UseCustomInlineNodeGen.create()).get();

        assertEquals(42, node.execute(42));
        assertEquals(42, node.execute(-42));

    }

    @GenerateUncached
    @SuppressWarnings("unused")
    @GenerateInline(false)
    public abstract static class InvalidUsageNode extends Node {

        abstract Object execute(Object arg);

        @Specialization
        Object doInt(int arg, @Cached(inline = true) SimpleNode simpleNode) {
            return simpleNode.execute(this, arg);
        }

        @Specialization
        Object doLong(long arg, @Shared("shared") @Cached(inline = true) SimpleNode simpleNode) {
            return simpleNode.execute(this, arg);
        }

        @Specialization
        Object doLong(Object arg, @Shared("shared") @Cached(inline = true) SimpleNode simpleNode) {
            return simpleNode.execute(this, arg);
        }

    }

    @Test
    public void testMultiInstanceInline() {
        MultiInstanceInliningNode node = adoptNode(MultiInstanceInliningNodeGen.create()).get();

        assertEquals(42, node.execute(42));
        assertEquals(41, node.execute(41));
        assertEquals(40, node.execute(40));
        assertEquals(42, node.execute(42));
        assertEquals(41, node.execute(41));
        assertEquals(40, node.execute(40));
    }

    @GenerateUncached
    @SuppressWarnings("unused")
    @GenerateInline(false)
    public abstract static class MultiInstanceInliningNode extends Node {

        abstract Object execute(Object arg);

        @Specialization(guards = "arg == cachedArg", limit = "3")
        static Object doInt(int arg,
                        @Bind("this") Node node,
                        @Cached(inline = true) SimpleNode simpleNode,
                        @Cached(inline = true) SimpleNode simpleNode2,
                        @Cached("arg") int cachedArg) {
            return simpleNode.execute(node, cachedArg);
        }

    }

    @Test
    public void testMultiInstanceInlineWithGeneric() {
        MultiInstanceInlineWithGenericNode node = adoptNode(MultiInstanceInlineWithGenericNodeGen.create()).get();

        assertEquals("doCached", node.execute(42));
        assertEquals("doCached", node.execute(41));
        assertEquals("doCached", node.execute(40));
        assertEquals("doGeneric", node.execute(43));
        assertEquals("doCached", node.execute(42));
        assertEquals("doCached", node.execute(41));
        assertEquals("doCached", node.execute(40));
        assertEquals("doGeneric", node.execute(43));
    }

    @GenerateUncached
    @SuppressWarnings("unused")
    @GenerateInline(false)
    public abstract static class MultiInstanceInlineWithGenericNode extends Node {

        abstract Object execute(Object arg);

        @Specialization(guards = "arg == cachedArg", limit = "3")
        static Object doCached(int arg,
                        @Bind("this") Node node,
                        @Cached(inline = true) SimpleNode simpleNode,
                        @Cached(inline = true) SimpleNode simpleNode2,
                        @Cached("arg") int cachedArg) {
            simpleNode.execute(node, cachedArg);
            simpleNode2.execute(node, cachedArg);
            return "doCached";
        }

        @Specialization
        static Object doOther(int arg,
                        @Bind("this") Node node,
                        @Cached(inline = true) SimpleNode simpleNode,
                        @Cached(inline = true) SimpleNode simpleNode2,
                        @Cached(value = "arg", neverDefault = true) int cachedArg) {
            simpleNode.execute(node, cachedArg);
            simpleNode2.execute(node, cachedArg);
            return "doGeneric";
        }
    }

    @Test
    public void testMultiInstanceMixedInliningNode() {
        MultiInstanceMixedInliningNode node = adoptNode(MultiInstanceMixedInliningNodeGen.create()).get();

        assertEquals(42, node.execute(42));
        assertEquals(41, node.execute(41));
        assertEquals(40, node.execute(40));
        assertEquals(42, node.execute(42));
        assertEquals(41, node.execute(41));
        assertEquals(40, node.execute(40));
    }

    @GenerateUncached
    @SuppressWarnings("unused")
    @GenerateInline(false)
    public abstract static class MultiInstanceMixedInliningNode extends Node {

        abstract Object execute(Object arg);

        @Specialization(guards = "arg == cachedArg", limit = "3")
        static Object doInt(int arg,
                        @Bind("this") Node node,
                        @Cached(inline = true) SimpleNode simpleNode,
                        @Cached(inline = false) SimpleNode simpleNode2,
                        @Cached("arg") int cachedArg) {
            return simpleNode.execute(node, cachedArg);
        }
    }

    @GenerateInline
    @SuppressWarnings("unused")
    public abstract static class BindInInlinedNode extends Node {

        abstract int execute(Node node);

        @Specialization
        static int doDefault(Node node,
                        @Cached SingleBitNode cachedNode,
                        @Bind("cachedNode.execute(node, 1)") int result) {
            return result;
        }

    }

    @SuppressWarnings({"unused", "truffle"})
    public abstract static class UseBindInInlinedNode extends Node {

        abstract Object execute();

        @Specialization
        int doDefault(@Cached(inline = true) BindInInlinedNode cachedNode) {
            return cachedNode.execute(this);
        }

    }

    @Test
    public void testBindInInlined() {
        UseBindInInlinedNode node = adoptNode(UseBindInInlinedNodeGen.create()).get();
        assertEquals(1, node.execute());
    }

    @GenerateInline(false)
    public abstract static class UseMixedAndInlinedNode extends Node {

        abstract Object execute();

        @Specialization
        int doDefault(@Cached(inline = true) TwoBitNode inlinedNode,
                        @Cached(inline = false) TwoBitNode dispatchedNode) {
            inlinedNode.execute(this, 0);
            return dispatchedNode.execute(dispatchedNode, 1);
        }

    }

    @Test
    public void testUseMixedAndInlinedNode() {
        UseMixedAndInlinedNode node = adoptNode(UseMixedAndInlinedNodeGen.create()).get();
        assertEquals(1, node.execute());
    }

    @GenerateInline
    public abstract static class AssumptionCacheNode extends Node {

        abstract int execute(Node node, Assumption[] assumption, int value);

        @Specialization(guards = "value == cachedValue", limit = "2", assumptions = "get(cachedAssumptions, cachedValue)")
        @SuppressWarnings("unused")
        int doDefault(Assumption[] assumptions, int value,
                        @Cached("value") int cachedValue,
                        @Cached(value = "assumptions", dimensions = 1) Assumption[] cachedAssumptions) {
            return cachedValue;
        }

        static Assumption get(Assumption[] assumptions, int index) {
            return assumptions[index];
        }

    }

    @GenerateInline(false)
    public abstract static class UseAssumptionCacheNode extends Node {

        abstract int execute(Assumption[] assumption, int value);

        @Specialization
        int doDefault(Assumption[] assumptions, int value,
                        @Cached(inline = true) AssumptionCacheNode node) {
            return node.execute(this, assumptions, value);
        }
    }

    @Test
    public void testAssumptionCacheNode() {
        UseAssumptionCacheNode node = adoptNode(UseAssumptionCacheNodeGen.create()).get();

        Assumption[] assumptions = new Assumption[5];
        for (int i = 0; i < assumptions.length; i++) {
            assumptions[i] = Truffle.getRuntime().createAssumption();
        }
        assertEquals(0, node.execute(assumptions, 0));
        assertEquals(1, node.execute(assumptions, 1));
        assumptions[0].invalidate();
        assertEquals(2, node.execute(assumptions, 2));
        assumptions[1].invalidate();
        assumptions[2].invalidate();

        // assumption invalidated
        assertFails(() -> {
            node.execute(assumptions, 2);
        }, UnsupportedSpecializationException.class);

        // recreate assumptions
        assumptions[2] = Truffle.getRuntime().createAssumption();

        assertEquals(2, node.execute(assumptions, 2));
        assertEquals(3, node.execute(assumptions, 3));

        // out of limit
        assertFails(() -> {
            node.execute(assumptions, 4);
        }, UnsupportedSpecializationException.class);

    }

    @GenerateInline(true)
    @GenerateCached(false)
    public abstract static class DoNotInlineInlinableNodeNode extends Node {

        abstract int execute(Node node, int value);

        @Specialization
        static int doDefault(Node node, int value,
                        @Cached(inline = false) SingleBitNode cached) {
            return cached.execute(node, value);
        }
    }

    @GenerateInline(false)
    public abstract static class UseDoNotInlineInlinableNodeNode extends Node {

        abstract int execute(int value);

        @Specialization
        int doDefault(int value,
                        @Cached DoNotInlineInlinableNodeNode cached) {
            return cached.execute(this, value);
        }
    }

    @Test
    public void testDoNotInlineInlinableNodeNode() {
        UseDoNotInlineInlinableNodeNode node = adoptNode(UseDoNotInlineInlinableNodeNodeGen.create()).get();
        assertEquals(1, node.execute(1));

    }

    static int enterCount = 0;
    static int exceptionCount = 0;
    static int returnCount = 0;

    @GenerateInline(true)
    @GenerateCached(false)
    public abstract static class TracingNode extends Node implements ExecuteTracingSupport {

        abstract int execute(Node node, int value);

        @Specialization
        int doDefault(int value) {
            return value;
        }

        public void traceOnEnter(Object[] arguments) {
            enterCount++;
        }

        public void traceOnException(Throwable t) {
            exceptionCount++;
        }

        public void traceOnReturn(Object returnValue) {
            returnCount++;
        }

        public boolean isTracingEnabled() {
            return true;
        }
    }

    @GenerateInline(false)
    public abstract static class UseTracingNode extends Node {

        abstract int execute(int value);

        @Specialization
        int doDefault(int value,
                        @Cached TracingNode cached) {
            return cached.execute(this, value);
        }
    }

    @Test
    public void testTracingNode() {
        enterCount = 0;
        exceptionCount = 0;
        returnCount = 0;
        UseTracingNode node = adoptNode(UseTracingNodeGen.create()).get();

        assertEquals(42, node.execute(42));
        assertEquals(1, enterCount);
        assertEquals(0, exceptionCount);
        assertEquals(1, returnCount);

        assertEquals(42, node.execute(42));
        assertEquals(2, enterCount);
        assertEquals(0, exceptionCount);
        assertEquals(2, returnCount);
    }

    /*
     * Compile correctly with report polymorphism.
     */
    @GenerateInline(true)
    @GenerateCached(false)
    @ReportPolymorphism
    @SuppressWarnings("unused")
    public abstract static class ReportPolymorphismTest extends Node {

        abstract Object execute(Node node, Object value);

        @Specialization(guards = "value == cachedValue", limit = "3")
        int doDefault(int value, @Cached("value") int cachedValue) {
            return value;
        }

        @Specialization
        long doDefault(long value) {
            return value;
        }
    }

    /*
     * Compile correctly with report polymorphism.
     */
    @GenerateInline(true)
    @GenerateCached(false)
    @Introspectable
    @SuppressWarnings("unused")
    public abstract static class IntrospectionNode extends Node {

        abstract Object execute(Node node, Object value);

        @Specialization(guards = "value == cachedValue", limit = "3")
        int doDefault(int value, @Cached("value") int cachedValue) {
            return value;
        }

        @Specialization
        long doDefault(long value) {
            return value;
        }
    }

    @GenerateInline(false)
    @Introspectable
    public abstract static class UseIntrospectionNode extends Node {

        abstract Object execute(Object value);

        @Specialization
        Object doDefault(Object value,
                        @Cached IntrospectionNode cached) {
            return cached.execute(this, value);
        }
    }

    @Test
    public void testIntrospection() {
        UseIntrospectionNode node = adoptNode(UseIntrospectionNodeGen.create()).get();

        node.execute(42);
        node.execute(43);
        node.execute(42L);

        SpecializationInfo info = Introspection.getSpecialization(node, node, "doDefault");
        IntrospectionNode introspectionNode = (IntrospectionNode) info.getCachedData(0).get(0);

        List<SpecializationInfo> inlinedInfos = Introspection.getSpecializations(node, introspectionNode);
        assertEquals(2, inlinedInfos.size());

        assertTrue(inlinedInfos.get(0).isActive());
        assertFalse(inlinedInfos.get(0).isExcluded());
        assertEquals(2, inlinedInfos.get(0).getInstances());
        assertEquals(43, inlinedInfos.get(0).getCachedData(0).get(0));
        assertEquals(42, inlinedInfos.get(0).getCachedData(1).get(0));

        assertTrue(inlinedInfos.get(1).isActive());
        assertFalse(inlinedInfos.get(1).isExcluded());
        assertEquals(1, inlinedInfos.get(1).getInstances());
    }

    @GenerateUncached
    @SuppressWarnings("unused")
    @GenerateInline
    public abstract static class InlineInlineCache extends Node {

        abstract Object execute(Node node, Object arg);

        @Specialization(guards = "arg == cachedArg", limit = "3")
        static Object doInt(Node node, int arg, @Cached("arg") int cachedArg,
                        @Cached FourBitNode fourBit,
                        @Cached NotInlineFourBitNode notInlined,
                        @Cached InlinableInlineCacheNode inlineCacheInlined) {
            inlineCacheInlined.execute(node, arg);
            return fourBit.execute(node, cachedArg);
        }

        @Specialization
        static Object doGeneric(Node node, int arg, @Cached FourBitNode fourBit, //
                        @Cached InlinableInlineCacheNode inlineCacheInlined) {
            return inlineCacheInlined.execute(node, arg);
        }

    }

    @GenerateInline(false)
    public abstract static class UseInlineInlineCache extends Node {

        abstract Object execute(Object arg);

        @Specialization
        @SuppressWarnings("truffle-inlining")
        Object doInt(int arg, @Cached InlineInlineCache inlined) {
            return inlined.execute(this, arg);
        }

    }

    @Test
    public void testUseInlineInlineCache() {
        UseInlineInlineCache node = adoptNode(UseInlineInlineCacheNodeGen.create()).get();

        node.execute(0);
        node.execute(1);
        node.execute(2);
        node.execute(3);
    }

    @GenerateInline(true)
    @GenerateCached(false)
    public abstract static class FailEarlyNode extends Node {

        abstract Object execute(Node node);

        @Specialization
        @SuppressWarnings("unused")
        static Object doInt(Node node, @Cached InlinedBranchProfile branchProfile) {
            // we do not call the branchProfile, but we still want to validate the node passed.
            // this is useful to validate branch profiles that are rarely executed, so wouldn't fail
            throw new AssertionError("should not reach here");
        }

    }

    @GenerateInline(false)
    public abstract static class UseFailEarlyNode extends Node {

        abstract Object execute();

        @Specialization
        static Object doInt(@Cached FailEarlyNode node) {
            assertFails(() -> node.execute(node), ClassCastException.class, (e) -> {
                assertTrue(e.getMessage(), e.getMessage().contains("Invalid inline context node passed to an inlined field"));
            });
            return "doInt";
        }

    }

    @Test
    public void testFailEarly() {
        UseFailEarlyNode node = adoptNode(UseFailEarlyNodeGen.create()).get();
        assertEquals("doInt", node.execute());
    }

    @SuppressWarnings("unused")
    @GenerateCached(false)
    @GenerateInline
    public abstract static class InlineSharedNode extends Node {

        abstract Object execute(Node node, Object arg);

        @Specialization(guards = "arg == 0")
        static Object s0(Node node, int arg,
                        @Shared("bits") @Cached Use128BitsNode bits) {
            return bits.execute(node);
        }

        @Specialization(guards = "arg == 2")
        static Object s1(Node node, int arg,
                        @Shared("bits") @Cached Use128BitsNode bits) {
            return bits.execute(node);
        }

        @Specialization(guards = "arg == 3")
        static Object s2(Node node, int arg,
                        @Shared("bits") @Cached Use128BitsNode bits) {
            return bits.execute(node);
        }

        @Specialization(guards = "arg == 4")
        static Object s3(Node node, int arg,
                        @Shared("bits") @Cached Use128BitsNode bits) {
            return bits.execute(node);
        }

    }

    @SuppressWarnings("unused")
    @GenerateCached(false)
    @GenerateInline
    public abstract static class InlineSharedWithSpecializationClassNode extends Node {

        abstract Object execute(Node node, Object arg);

        @Specialization(guards = "arg == 0")
        static Object s0(Node node, int arg,
                        @Shared("innerShared") @Cached InlineInlineCache innerShared) {
            return innerShared.execute(node, arg);
        }

        @Specialization(guards = "arg == 1")
        static Object s1(Node node, int arg,
                        @Shared("innerShared") @Cached InlineInlineCache innerShared,
                        @Cached(inline = false) InlineInlineCache innerNotInlined0,
                        @Cached(inline = false) InlineInlineCache innerNotInlined1,
                        @Cached(inline = false) InlineInlineCache innerNotInlined2,
                        @Cached(inline = false) InlineInlineCache innerNotInlined3,
                        @Cached InlineInlineCache innerRegular) {
            innerRegular.execute(node, arg);
            innerNotInlined0.execute(node, arg);
            innerNotInlined1.execute(node, arg);
            innerNotInlined2.execute(node, arg);
            innerNotInlined3.execute(node, arg);
            return innerShared.execute(node, arg);
        }
    }

    @SuppressWarnings("unused")
    @GenerateInline
    public abstract static class SharedNoneInlinedWithSpecializationClassNode extends Node {

        abstract Object execute(Node node, Object arg);

        @Specialization(guards = "arg == 1")
        static Object s0(Node node, int arg,
                        @Cached(inline = false) InlineInlineCache innerNotInlined0,
                        @Cached(inline = false) InlineInlineCache innerNotInlined1,
                        @Cached(inline = false) InlineInlineCache innerNotInlined2,
                        @Cached(inline = false) InlineInlineCache innerNotInlined3,
                        @Cached(inline = false) InlineInlineCache innerNotInlined4,
                        @Cached(inline = false) InlineInlineCache innerNotInlined5,
                        @Cached(inline = false) InlineInlineCache innerNotInlined6,
                        @Cached(inline = false) InlineInlineCache innerNotInlined7,
                        @Cached(inline = false) InlineInlineCache innerNotInlined8,
                        @Cached(inline = false) InlineInlineCache innerNotInlined9,
                        @Cached(inline = false) InlineInlineCache innerNotInlined10,
                        @Shared("innerShared") @Cached InlineInlineCache innerShared) {
            innerShared.execute(node, 0);
            return innerShared.execute(node, 1);
        }

        @Specialization(guards = "arg == 2")
        static Object s1(Node node, int arg,
                        @Cached(inline = false) InlineInlineCache innerNotInlined0,
                        @Cached(inline = false) InlineInlineCache innerNotInlined1,
                        @Cached(inline = false) InlineInlineCache innerNotInlined2,
                        @Cached(inline = false) InlineInlineCache innerNotInlined3,
                        @Shared("innerShared") @Cached InlineInlineCache innerShared) {
            innerShared.execute(node, 2);
            return innerShared.execute(node, 3);
        }

    }

    @SuppressWarnings("unused")
    @GenerateInline
    public abstract static class SharedAllInlinedWithSpecializationClassNode extends Node {

        abstract Object execute(Node node, Object arg);

        @Specialization(guards = "arg == 1")
        static Object s0(Node node, int arg,
                        @Cached Use512BitsNode node0,
                        @Shared("innerShared") @Cached InlineInlineCache innerShared,
                        @Shared("innerSharedPrimitive") @Cached("arg") int innerSharedPrimitive,
                        @Shared("innerSharedNotInlined") @Cached(inline = false) InlineInlineCache innerSharedNotInlined) {
            innerShared.execute(node, 0);
            return innerShared.execute(node, 1);
        }

        @Specialization(guards = "arg == 2")
        static Object s1(Node node, int arg,
                        @Cached Use512BitsNode node0,
                        @Shared("innerShared") @Cached InlineInlineCache innerShared,
                        @Shared("innerSharedPrimitive") @Cached("arg") int innerSharedPrimitive,
                        @Shared("innerSharedNotInlined") @Cached(inline = false) InlineInlineCache innerSharedNotInlined) {
            innerShared.execute(node, 2);
            return innerShared.execute(node, 3);
        }

    }

    @SuppressWarnings("unused")
    @GenerateInline
    public abstract static class SharedMixedInlinedWithSpecializationClassNode extends Node {

        abstract Object execute(Node node, Object arg);

        @Specialization(guards = "arg == 1")
        static Object s0(Node node, int arg,
                        @Cached Use512BitsNode node0,
                        @Shared("innerShared") @Cached InlineInlineCache innerShared,
                        @Shared("innerSharedPrimitive") @Cached("arg") int innerSharedPrimitive,
                        @Shared("innerSharedNotInlined") @Cached(inline = false) InlineInlineCache innerSharedNotInlined) {
            innerShared.execute(node, 0);
            return innerShared.execute(node, 1);
        }

        @SuppressWarnings("truffle-neverdefault")
        @Specialization(guards = "arg == 2")
        static Object s1(Node node, int arg,
                        @Shared("innerShared") @Cached InlineInlineCache innerShared,
                        @Shared("innerSharedPrimitive") @Cached("arg") int innerSharedPrimitive,
                        @Shared("innerSharedNotInlined") @Cached(inline = false) InlineInlineCache innerSharedNotInlined) {
            innerShared.execute(node, 2);
            return innerShared.execute(node, 3);
        }

    }

    @SuppressWarnings("unused")
    @GenerateInline(false)
    public abstract static class UseInlineSharedWithSpecializationClassNode extends Node {

        abstract Object execute(Object arg);

        @Specialization(guards = "arg == 1")
        Object s0(int arg,
                        @Cached InlineSharedWithSpecializationClassNode bits,
                        @Shared("innerShared") @Cached(inline = true) InlineInlineCache innerShared,
                        @Shared("innerSharedPrimitive") @Cached("arg") int innerSharedPrimitive,
                        @Shared("innerSharedNotInlined") @Cached(inline = false) InlineInlineCache innerSharedNotInlined) {
            bits.execute(this, 0);
            return bits.execute(this, 1);
        }

        @Specialization(guards = "arg == 2")
        static Object s1(int arg,
                        @Bind("this") Node node,
                        @Shared("innerShared") @Cached(inline = true) InlineInlineCache innerShared,
                        @Shared("innerSharedPrimitive") @Cached("arg") int innerSharedPrimitive,
                        @Shared("innerSharedNotInlined") @Cached(inline = false) InlineInlineCache innerSharedNotInlined,
                        @Cached(inline = false) InlineInlineCache innerNotInlined0,
                        @Cached(inline = false) InlineInlineCache innerNotInlined1,
                        @Cached(inline = false) InlineInlineCache innerNotInlined2,
                        @Cached(inline = false) InlineInlineCache innerNotInlined3,
                        @Cached(inline = true) InlineInlineCache innerRegular) {

            innerRegular.execute(node, arg);
            innerNotInlined0.execute(node, arg);
            innerNotInlined1.execute(node, arg);
            innerNotInlined2.execute(node, arg);
            innerNotInlined3.execute(node, arg);
            return innerShared.execute(node, arg);
        }

        @Specialization(guards = "arg == 3")
        static Object s2(int arg,
                        @Bind("this") Node node,
                        @Cached(inline = true) SharedAllInlinedWithSpecializationClassNode bits) {

            bits.execute(node, 1);
            return bits.execute(node, 2);
        }

        @Specialization(guards = "arg == 4")
        static Object s3(int arg,
                        @Bind("this") Node node,
                        @Cached(inline = true) SharedNoneInlinedWithSpecializationClassNode bits) {

            bits.execute(node, 1);
            return bits.execute(node, 2);
        }

        @Specialization(guards = "arg == 5")
        static Object s4(int arg,
                        @Bind("this") Node node,
                        @Cached(inline = true) SharedMixedInlinedWithSpecializationClassNode bits) {

            bits.execute(node, 1);
            return bits.execute(node, 2);
        }

    }

    @Test
    public void testInlineSharedWithSpecializationClass() {
        UseInlineSharedWithSpecializationClassNode node = adoptNode(UseInlineSharedWithSpecializationClassNodeGen.create()).get();
        node.execute(1);
        node.execute(2);
        node.execute(3);
        node.execute(4);
        node.execute(5);
    }

    @SuppressWarnings("unused")
    @GenerateInline
    @GenerateCached(false)
    public abstract static class InlinedAdoptNode extends Node {

        abstract Object execute(Node node, Object arg);

        @Specialization(guards = "arg == 1")
        static int s0(Node node, int arg,
                        @Cached Use512BitsNode node0,
                        @Cached(inline = false, adopt = false) InlineInlineCache innerShared) {
            assertNull(innerShared.getParent());
            return arg;
        }

    }

    @SuppressWarnings("unused")
    @GenerateInline(false)
    public abstract static class UseInlinedAdoptNode extends Node {

        abstract Object execute(Object arg);

        @Specialization(guards = "arg == 1")
        Object s0(int arg,
                        @Cached InlinedAdoptNode bits) {
            return bits.execute(this, 1);
        }

    }

    @Test
    public void testAdopt() {
        UseInlinedAdoptNode node = adoptNode(UseInlinedAdoptNodeGen.create()).get();
        node.execute(1);
        node.execute(1);
        node.execute(1);
    }

    public static class InlinedInGuard extends Node {

        final StateField field;

        InlinedInGuard(InlineTarget target) {
            this.field = target.getState(0, 1);
        }

        boolean execute(Node node, int arg) {
            field.set(node, arg);
            return true;
        }

        public static InlinedInGuard inline(
                        @RequiredField(value = StateField.class, bits = 1) InlineTarget target) {
            return new InlinedInGuard(target);
        }

    }

    @SuppressWarnings("unused")
    @GenerateInline(false)
    @Introspectable
    public abstract static class UseInlinedNodeInGuard extends Node {

        abstract Object execute(Object arg);

        @Specialization(guards = "guard.execute(this, arg)", limit = "1")
        Object s0(int arg, @Cached InlinedInGuard guard) {
            /*
             * Inlined caches that are bound in guards must not be in the same state bitset as the
             * dependent specialization bits. At the end of slow-path specialization we set the
             * state bits of the specialization. If an inlined node in the guard changes the state
             * bits we would override when we set the specialization bits.
             */
            assertEquals(1, guard.field.get(this));
            return arg;
        }
    }

    @Test
    public void testInlinedNodeInGuard() {
        UseInlinedNodeInGuard node = adoptNode(UseInlinedNodeInGuardNodeGen.create()).get();
        node.execute(1);
    }

    @SuppressWarnings("unused")
    @GenerateInline(true)
    @GenerateCached(false)
    @Introspectable
    public abstract static class UncachedEnculapsingNode extends Node {

        abstract boolean execute(Node node, Object arg);

        @Specialization(limit = "1")
        boolean s0(Node node, Object arg, @CachedLibrary("arg") InteropLibrary interop) {
            if (arg instanceof String) {
                // ensure target node is pushed instead of inline singleton
                assertSame(node, EncapsulatingNodeReference.getCurrent().get());
            }
            return interop.isString(arg);
        }
    }

    @SuppressWarnings("unused")
    @GenerateInline(false)
    @Introspectable
    public abstract static class UseUncachedEnculapsingNode extends Node {

        abstract boolean execute(Object arg);

        @Specialization
        boolean s0(Object arg, @Cached UncachedEnculapsingNode node) {
            return node.execute(this, arg);
        }
    }

    @Test
    public void testUncachedEnculapsingNode() {
        UseUncachedEnculapsingNode node = adoptNode(UseUncachedEnculapsingNodeGen.create()).get();
        assertFalse(node.execute(1));

        // pushes an encapsulating node in uncached
        assertTrue(node.execute(""));
    }

    @SuppressWarnings("unused")
    @GenerateInline(true)
    @GenerateCached(false)
    @Introspectable
    public abstract static class NoStateNode extends Node {

        abstract String execute(Node node, Object arg);

        @Specialization
        String s0(Node node, Object arg) {
            return "s0";
        }
    }

    @SuppressWarnings("unused")
    @GenerateInline(false)
    @Introspectable
    public abstract static class UseNoStateNode extends Node {

        abstract String execute(Object arg);

        @Specialization
        String s0(Object arg, @Cached NoStateNode node) {
            return node.execute(node, arg);
        }
    }

    @Test
    public void testNoState() {
        UseNoStateNode node = adoptNode(UseNoStateNodeGen.create()).get();
        assertEquals("s0", node.execute(1));
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @SuppressWarnings("unused")
    public abstract static class InlinedIdentityNode extends Node {

        abstract boolean execute(Node node, Object arg0);

        @Specialization(guards = "arg0 == cachedArg0", limit = "1")
        static boolean s0(Node node, Object arg0,
                        @Cached("arg0") Object cachedArg0) {
            return true;
        }

        @Fallback
        static boolean fallback(Object obj) {
            return false;
        }
    }

    @Test
    public void testSharedAndNonSharedInlinedMultipleInstances1Node() {
        SharedAndNonSharedInlinedMultipleInstances1Node node = SharedAndNonSharedInlinedMultipleInstances1NodeGen.create();
        Object o1 = 1;
        Object o2 = 2;

        assertEquals("s0", node.execute(o1));
        assertEquals("s1", node.execute(o2));

        assertEquals("s0", node.execute(o1));
        assertEquals("s1", node.execute(o2));
    }

    @SuppressWarnings("truffle-inlining")
    abstract static class SharedAndNonSharedInlinedMultipleInstances1Node extends Node {

        public abstract Object execute(Object arg0);

        @Specialization(guards = "sharedNode.execute(this, arg0)")
        @SuppressWarnings("unused")
        static String s0(Object arg0,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedIdentityNode sharedNode,
                        @Cached InlinedIdentityNode exclusiveNode) {
            assertTrue(sharedNode.execute(inliningTarget, arg0));
            assertTrue(exclusiveNode.execute(inliningTarget, arg0));
            return "s0";
        }

        @Specialization
        String s1(Object arg0,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedIdentityNode sharedNode,
                        @Exclusive @Cached InlinedIdentityNode exclusiveNode) {
            assertFalse(sharedNode.execute(inliningTarget, arg0));
            assertTrue(exclusiveNode.execute(inliningTarget, arg0));
            return "s1";
        }

    }

    @Test
    public void testSharedAndNonSharedInlinedMultipleInstances2Node() {
        SharedAndNonSharedInlinedMultipleInstances2Node node = SharedAndNonSharedInlinedMultipleInstances2NodeGen.create();
        Object o1 = 1;
        Object o2 = 2;
        Object o3 = 3;
        Object o4 = 4;

        assertEquals("s0", node.execute(o1));
        assertEquals("s0", node.execute(o2));
        assertEquals("s0", node.execute(o3));
        assertEquals("s1", node.execute(o4));

        assertEquals("s0", node.execute(o1));
        assertEquals("s0", node.execute(o2));
        assertEquals("s0", node.execute(o3));
        assertEquals("s1", node.execute(o4));
    }

    @SuppressWarnings("truffle-inlining")
    abstract static class SharedAndNonSharedInlinedMultipleInstances2Node extends Node {

        public abstract Object execute(Object arg0);

        @Specialization(guards = "exclusiveNode.execute(this, arg0)", limit = "3")
        @SuppressWarnings("unused")
        static String s0(Object arg0,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedIdentityNode sharedNode,
                        @Cached InlinedIdentityNode exclusiveNode) {
            assertTrue(exclusiveNode.execute(inliningTarget, arg0));
            return "s0";
        }

        @Specialization
        String s1(Object arg0,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedIdentityNode sharedNode,
                        @Exclusive @Cached InlinedIdentityNode exclusiveNode) {
            assertTrue(sharedNode.execute(inliningTarget, arg0));
            assertTrue(exclusiveNode.execute(inliningTarget, arg0));
            return "s1";
        }

    }

    enum EnumValue {

        S0,
        S1,
        S2;

        static EnumValue fromInt(int value) {
            return EnumValue.values()[value];
        }

    }

    @GenerateUncached
    @SuppressWarnings("unused")
    @GenerateInline(false)
    public abstract static class WarningMultiInstanceInliningNeedsStaticNode extends Node {

        abstract Object execute(Object arg);

        @ExpectError("For this specialization with inlined cache parameters it is recommended to use the static modifier.%")
        @Specialization(guards = "arg == cachedArg", limit = "3")
        Object doInt(int arg,
                        @Bind("this") Node node,
                        @Cached(inline = true) SimpleNode simpleNode,
                        @Cached("arg") int cachedArg) {
            return simpleNode.execute(node, cachedArg);
        }
    }

    @GenerateUncached
    @SuppressWarnings("unused")
    @GenerateInline(false)
    public abstract static class ErrorMultiInstanceInliningNeedsNode extends Node {

        abstract Object execute(Object arg);

        @ExpectError("For this specialization with inlined cache parameters a '@Bind(\"this\") Node node' parameter must be declared. %")
        @Specialization(guards = "arg == cachedArg", limit = "3")
        static Object doInt(int arg,
                        @Cached(inline = true) SimpleNode simpleNode,
                        @Cached("arg") int cachedArg) {
            return 42;
        }
    }

    @GenerateInline
    @SuppressWarnings("unused")
    public abstract static class ErrorGenerateInlineNeedsStaticNode extends Node {

        abstract Object execute(Node node, Object arg);

        @ExpectError("For @GenerateInline annotated nodes all specialization methods with inlined cached values must be static%")
        @Specialization
        Object doInt(Node node, int arg,
                        @Cached SimpleNode simpleNode) {
            return simpleNode.execute(node, arg);
        }
    }

    @GenerateInline
    @SuppressWarnings("unused")
    public abstract static class ErrorGenerateInlineNeedsNeedsParameterNode extends Node {

        abstract Object execute(Node node, Object arg);

        @ExpectError("For @GenerateInline annotated nodes all specialization methods with inlined cached values must declare 'Node node' dynamic parameter.%")
        @Specialization
        static Object doInt(int arg,
                        @Cached SimpleNode simpleNode) {
            return simpleNode.execute(null, arg);
        }
    }

    static class NoInliningNode extends Node {
        static NoInliningNode create() {
            return null;
        }
    }

    @GenerateUncached
    @SuppressWarnings("unused")
    public abstract static class ErrorNotInlinableNode extends Node {

        abstract Object execute(Object arg);

        @Specialization()
        Object doInt(int arg,
                        @ExpectError("The cached node type does not support object inlining. Add @GenerateInline on the node type or disable inline using @Cached(inline=false) to resolve this.") @Cached(inline = true) NoInliningNode simpleNode) {
            return "";
        }

    }

    public abstract static class BaseNode extends Node {

        abstract Object execute();

    }

    @GenerateInline
    @NodeChild
    @ExpectError("Error generating code for @GenerateInline: Inlinable nodes cannot use @NodeChild. Disable inlining generation or remove @NodeChild to resolve this.")
    public abstract static class ErrorUsedNodeChildNode extends BaseNode {

        @Specialization
        int doDefault(int arg) {
            return arg;
        }

    }

    @GenerateInline
    @ExpectError("Error generating code for @GenerateInline: Found non-final execute method without a node parameter execute(). " +
                    "Inlinable nodes must use the Node type as the first parameter after the optional frame for all non-final execute methods. " +
                    "A valid signature for an inlinable node is execute([VirtualFrame frame, ] Node node, ...).")
    public abstract static class ErrorNoNodeNode extends Node {

        abstract Object execute();

        abstract Object execute(Node node);

        @Specialization
        int doDefault() {
            return 42;
        }

    }

    @GenerateInline
    @SuppressWarnings("unused")
    public abstract static class ErrorDirectRecursionNode extends Node {

        abstract Object execute(Node node);

        @Specialization
        int doDefault(Node node,
                        @ExpectError("Detected recursive inlined cache with type 'ErrorDirectRecursionNode'. " +
                                        "Recursive inlining cannot be supported. Remove the recursive declaration or disable inlining with @Cached(..., inline=false) to resolve this.") //
                        @Cached ErrorDirectRecursionNode cachedNode) {
            return 42;
        }

    }

    @GenerateInline
    @SuppressWarnings("unused")
    public abstract static class ErrorDirectRecursionDisableNode extends Node {

        abstract Object execute(Node node);

        @Specialization
        static int doDefault(Node node,
                        // test no recursion error here if inlining is disabled.
                        @ExpectError("Message redirected from element GenerateInlineTest.ErrorDirectRecursionNode.doDefault(..., ErrorDirectRecursionNode cachedNode):%") //
                        @Cached(inline = true) ErrorDirectRecursionNode cachedNode) {
            return 42;
        }

    }

    @GenerateInline
    @SuppressWarnings("unused")
    public abstract static class ErrorIndirectRecursionNode1 extends Node {

        abstract Object execute(Node node);

        @Specialization
        static int doDefault(Node node,
                        @ExpectError("%")//
                        @Cached ErrorIndirectRecursionNode2 cachedNode) {
            return 42;
        }

    }

    @GenerateInline
    @SuppressWarnings("unused")
    public abstract static class ErrorIndirectRecursionNode2 extends Node {

        abstract Object execute(Node node);

        @Specialization
        static int doDefault(Node node,
                        @ExpectError("%") //
                        @Cached ErrorIndirectRecursionNode1 cachedNode) {
            return 42;
        }

    }

    @ExpectError("Failed to generate code for @GenerateInline: The node must not declare any instance variables. Found instance variable ErrorInstanceFieldsNode.foobar. Remove instance variable to resolve this.")
    @GenerateInline
    @SuppressWarnings("unused")
    public abstract static class ErrorInstanceFieldsNode extends Node {

        private String foobar;

        abstract Object execute(Node node);

        @Specialization
        static int doDefault(Node node) {
            return 42;
        }
    }

    @GenerateInline
    @SuppressWarnings("unused")
    public abstract static class ErrorRedundantInliningNode extends Node {

        abstract Object execute(Node node, Object arg);

        @Specialization
        static Object doInt(Node node, int arg,
                        @ExpectError("Redundant specification of @GenerateInline(... inline=true). Cached values of nodes with @Cached are implicitely inlined.") //
                        @Cached(inline = true) InnerNode innerNode) {
            return innerNode.execute(node, arg);
        }

    }

    @SuppressWarnings("unused")
    @ExpectError("This node is a candidate for node object inlining.%")
    public abstract static class WarningPossibleInliningNode extends Node {

        abstract Object execute(Object arg);

        @Specialization
        Object doInt(int arg,
                        @Cached(inline = true) InnerNode innerNode) {
            return innerNode.execute(this, arg);
        }

    }

    @SuppressWarnings("unused")
    public abstract static class InvalidInlineMethodParameterType extends Node {

        public static Object inline() {
            return null;
        }

        public static InvalidInlineMethodParameterType create() {
            return null;
        }

    }

    @SuppressWarnings("unused")
    public abstract static class InvalidInlineMethodParameterTypeNode extends Node {

        abstract void execute();

        @Specialization
        void doInt(@ExpectError("Inline method inline() is invalid. The method must have exactly one parameter of type 'InlineTarget'.") //
        @Cached(inline = true) InvalidInlineMethodParameterType innerNode) {
        }

    }

    @SuppressWarnings("unused")
    public abstract static class InvalidInlineMethodReturnType extends Node {

        public static InvalidInlineMethodReturnType inline(Object o) {
            return null;
        }

        public static InvalidInlineMethodReturnType create() {
            return null;
        }

    }

    @SuppressWarnings("unused")
    public abstract static class InvalidInlineMethodReturnTypeNode extends Node {

        abstract void execute();

        @Specialization
        void doInt(
                        @ExpectError("Inline method inline(Object) is invalid. The method must have exactly one parameter of type 'InlineTarget'.") //
                        @Cached(inline = true) InvalidInlineMethodReturnType innerNode) {
        }

    }

    @ExpectError("Failed to generate code for @GenerateInline: The node must not declare any instance variables. Found instance variable ErrorNodeFieldsNode.foo. Remove instance variable to resolve this.")
    @GenerateInline
    public abstract static class ErrorNodeFieldsNode extends Node {

        String foo;

        abstract void execute(Node node);

        @Specialization
        static void doInt() {
        }

    }

    public abstract static class SuperNodeWithFields extends Node {

        String foo;

    }

    @ExpectError("Failed to generate code for @GenerateInline: The node must not declare any instance variables. Found instance variable SuperNodeWithFields.foo. Remove instance variable to resolve this.")
    @GenerateInline
    public abstract static class ErrorNodeFieldsInSuperNode extends SuperNodeWithFields {

        abstract void execute(Node node);

        @Specialization
        static void doInt() {
        }

    }

    @Test
    public void testNodeAndFrame() {
        VirtualFrame f = Truffle.getRuntime().createVirtualFrame(new Object[]{"42"}, new FrameDescriptor());
        PassNodeAndFrameNode inlinedNode = PassNodeAndFrameNodeGen.inline(InlineTarget.create(PassNodeAndFrameNode.class));
        inlinedNode.execute(inlinedNode, f);
    }

    @GenerateInline
    public abstract static class PassNodeAndFrameNode extends Node {

        abstract void execute(Node node, VirtualFrame frame);

        @Specialization
        static void doInt(@SuppressWarnings("unused") Node node, @SuppressWarnings("unused") VirtualFrame frame) {
            assertSame(node, node);
            assertEquals("42", frame.getArguments()[0]);

        }

    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class OnlyInlineNode extends UseStateNode {

        @Specialization
        static int s0() {
            return 0;
        }
    }

    @SuppressWarnings("unused")
    @GenerateInline(false)
    abstract static class SharedProfileInSpecializationClassNode extends Node {

        public abstract void execute(CompactionLevel s);

        @Specialization(guards = {"cachedCompaction ==compaction"}, limit = "2")
        static void doCached(CompactionLevel compaction,
                        @Bind("this") Node node,
                        @Cached("compaction") CompactionLevel cachedCompaction,
                        @Shared("error") @Cached InlinedBranchProfile errorProfile) {
            errorProfile.enter(node);
        }

        @Specialization(replaces = "doCached")
        void doUncached(CompactionLevel s,
                        @Shared("error") @Cached InlinedBranchProfile errorProfile) {
            errorProfile.enter(this);
        }

    }

    @Test
    public void testSharedProfileInSpecializationClass() {
        SharedProfileInSpecializationClassNode node = SharedProfileInSpecializationClassNodeGen.create();

        node.execute(CompactionLevel.S1);
        node.execute(CompactionLevel.S2);
        node.execute(CompactionLevel.S4);

    }

    @SuppressWarnings("unused")
    @GenerateInline(false)
    public abstract static class ErrorOnlyInlineMessage1 extends UseStateNode {

        @Specialization
        // automatically inlines
        static int s0(@Cached OnlyInlineNode p0) {
            return 0;
        }
    }

    @SuppressWarnings("unused")
    public abstract static class ErrorOnlyInlineMessage2 extends UseStateNode {

        @Specialization
        int s0(@ExpectError("Error parsing expression 'create()': The method create is undefined for the enclosing scope.") //
        @Cached(inline = false) OnlyInlineNode p0) {
            return 0;
        }
    }

    @SuppressWarnings("unused")
    @GenerateInline(false)
    public abstract static class ErrorOnlyInlineMessage3 extends UseStateNode {

        @Specialization
        int s0(@ExpectError("Redundant specification of @Cached(... inline=true)%") //
        @Cached(inline = true) OnlyInlineNode p0) {
            return 0;
        }
    }

    /*
     * Test that it does not produce the recommendation for inlining when a NodeChild is used.
     */
    @SuppressWarnings("unused")
    @NodeChild(type = ExecutableNode.class)
    public abstract static class ErrorChildUsage extends Node {

        abstract Object execute(VirtualFrame frame);

        @Specialization
        int s0(Object arg) {
            return 0;
        }
    }

    @GenerateInline
    @SuppressWarnings("unused")
    public abstract static class TestFrameAndNodeCombinationsNode extends Node {

        abstract Object execute(VirtualFrame frame, Node node, int value);

        @Specialization(guards = "value == 1")
        static Object s0(VirtualFrame frame, Node node, int value) {
            return "s0";
        }

        @Specialization(guards = "value == 2")
        static Object s1(Node node, int value) {
            return "s1";
        }

        @Specialization(guards = "value == 3")
        static Object s2(VirtualFrame frame, int value) {
            return "s2";
        }

        @Specialization(guards = "value == 4")
        static Object s3(int value) {
            return "s3";
        }
    }

    @GenerateInline
    @SuppressWarnings("unused")
    public abstract static class TestOmitNodeInOneSpecialization extends Node {

        abstract Object execute(Node node, int value);

        @Specialization(guards = "value == 1")
        static Object s0(int value) {
            return "s0";
        }

        @Specialization
        static Object s1(Node node, int value) {
            return "s0";
        }
    }

    @GenerateInline
    @SuppressWarnings("unused")
    public abstract static class TestOmitNodeInAllSpecializations extends Node {

        abstract Object execute(Node node, int value);

        @Specialization(guards = "value == 1")
        static Object s0(int value) {
            return "s0";
        }

        @Specialization
        static Object s1(int value) {
            return "s1";
        }
    }

    @GenerateInline
    @GeneratePackagePrivate
    @DisableStateBitWidthModfication
    public abstract static class ErrorNodeWithCustomInlineNode extends Node {

        abstract long execute(Node node, long value);

        @Specialization(guards = "v >= 0")
        static long doInt(long v) {
            return v;
        }

        @Specialization(guards = "v < 0")
        static long doLong(long v) {
            return -v;
        }

        // too little bit space
        @SuppressWarnings("unused")
        @ExpectError("The custom inline method does not specify enough bit space%")
        public static ErrorNodeWithCustomInlineNode inline(@RequiredField(value = StateField.class, bits = 1) InlineTarget target) {
            return null;
        }

    }

    @GenerateInline
    @GeneratePackagePrivate
    @DisableStateBitWidthModfication
    public abstract static class ErrorNodeWithCustomInline2Node extends Node {

        abstract long execute(Node node, long value);

        @Specialization(guards = "v >= 0")
        static long doInt(long v) {
            return v;
        }

        @Specialization(guards = "v < 0")
        static long doLong(long v) {
            return -v;
        }

        // too many parameters
        @SuppressWarnings("unused")
        @ExpectError("The custom inline method does not specify enough bit space%")
        public static ErrorNodeWithCustomInlineNode inline(@RequiredField(value = StateField.class, bits = 2) @RequiredField(value = StateField.class, bits = 1) InlineTarget target) {
            return null;
        }

    }

    @GenerateInline
    @NodeField(name = "field", type = int.class)
    @ExpectError("Error generating code for @GenerateInline: Inlinable nodes cannot use @NodeField. Disable inlining generation or remove @NodeField to resolve this.")
    public abstract static class ErrorNodeNodeFieldNode extends Node {

        abstract long execute(Node node, long value);

        @SuppressWarnings("unused")
        @Specialization(guards = "v >= 0")
        static long doInt(long v, @Bind("field") int nodeField) {
            return v;
        }

    }

    @GenerateInline
    @GenerateCached(false)
    @SuppressWarnings("unused")
    public abstract static class ErrorInvalidInlineMethod1Node extends Node {

        abstract String execute(Node node, int value);

        @Specialization
        static String s0(Node node, int value,
                        @ExpectError("Inline method customInline() is invalid. The method must have exactly one parameter of type 'InlineTarget'.") //
                        @Cached(inlineMethod = "customInline") Node inlinedNnode) {
            return "s0";
        }

        static Node customInline() {
            return null;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @SuppressWarnings("unused")
    public abstract static class ErrorInvalidInlineMethod2Node extends Node {

        abstract String execute(Node node, int value);

        @Specialization
        static String s0(Node node, int value,
                        @ExpectError("Invalid return type java.lang.Object found but expected com.oracle.truffle.api.nodes.Node. %") //
                        @Cached(inlineMethod = "customInline") Node inlinedNnode) {
            return "s0";
        }

        static Object customInline(InlineTarget target) {
            return null;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @SuppressWarnings("unused")
    public abstract static class ErrorInvalidInlineMethod3Node extends Node {

        abstract String execute(Node node, int value);

        @Specialization
        static String s0(Node node, int value,
                        @ExpectError("Static inline method with name 'doesNotExist' and parameter type 'InlineTarget' could not be resolved. %") //
                        @Cached(inlineMethod = "doesNotExist") Node inlinedNnode) {
            return "s0";
        }

    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings({"static-method", "unused"})
    static class ErrorUseBindParamterInLibraryExport1 implements TruffleObject {

        @ExportMessage
        final boolean isPointer() {
            return false;
        }

        @ExpectError("For this specialization with inlined cache parameters a '@Bind(\"$node\") Node node' parameter must be declared.%")
        @ExportMessage
        long asPointer(@Cached InlinedBranchProfile profile) {
            return 0L;
        }

    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings({"static-method", "unused"})
    static class ErrorUseBindParamterInLibraryExport2 implements TruffleObject {

        @ExportMessage
        final boolean isPointer() {
            return false;
        }

        @ExportMessage
        static class AsPointer {
            @ExpectError("For this specialization with inlined cache parameters a '@Bind(\"$node\") Node node' parameter must be declared.%")
            @Specialization
            static long asPointer(ErrorUseBindParamterInLibraryExport2 receiver, @Cached InlinedBranchProfile profile) {
                return 0L;
            }
        }

    }

    /*
     * Test this compiles correctly. The lookup field in the specialization data class may cause a
     * code generation problem.
     */
    @SuppressWarnings({"truffle-inlining", "unused"})
    public abstract static class MultiInstanceWithAssumptionNode extends Node {

        public abstract Object execute(Object object);

        @Specialization(guards = {"object.getClass() == cachedLayout"},
                        // this test needs an assumption
                        assumptions = "createAssumption()", //
                        limit = "3")
        protected static Object s0(final Object object,
                        @Bind("this") Node node,
                        @Cached("object.getClass()") final Class<?> cachedLayout,
                        // need to have one node inlined
                        @Cached InlinedConditionProfile weakRefProfile) {
            return null;
        }

        static Assumption createAssumption() {
            return Truffle.getRuntime().createAssumption();
        }

    }

    // caller Cached + Callee Inlinable, but not inlined -> Warning
    @GenerateInline(false)
    public abstract static class CachedWarningTest1 extends Node {

        public abstract Object execute(Object object);

        @Specialization
        @SuppressWarnings("unused")
        static String s0(int value,
                        @ExpectError("The cached type 'SingleBitNode' supports object-inlining.%") //
                        @Cached SingleBitNode inlinedNnode) {
            return "s0";
        }

    }

    // caller Cached + Callee Inlinable, alwaysInlineCached -> Auto-inline
    @GenerateInline(false)
    @GenerateCached(alwaysInlineCached = true)
    public abstract static class CachedWarningTest2 extends Node {

        public abstract Object execute(Object object);

        @Specialization
        @SuppressWarnings("unused")
        static String s0(int value,
                        @Cached SingleBitNode inlinedNnode) {
            return "s0";
        }

    }

    // caller Cached + Callee Not Inlinable , alwaysInlineCached -> do nothing
    @GenerateInline(false)
    @GenerateCached(alwaysInlineCached = true)
    public abstract static class CachedWarningTest3 extends Node {

        public abstract Object execute(Object object);

        @Specialization
        @SuppressWarnings("unused")
        static String s0(int value,
                        @Cached CachedWarningTest1 inlinedNnode) {
            return "s0";
        }

    }

    // caller Inlined + Callee Not Inlinable -> Warnings
    @GenerateInline(true)
    public abstract static class InlinedWarningTest1 extends Node {

        public abstract Object execute(Node node, Object object);

        @Specialization
        @SuppressWarnings("unused")
        static String s0(int value,
                        @ExpectError("The cached node type does not support object inlining.%") //
                        @Cached CachedWarningTest1 inlinedNnode) {
            return "s0";
        }

    }

    // caller Inlined + Callee Not Inlinable + inline=false -> No warning
    @GenerateInline(true)
    public abstract static class InlinedWarningTest2 extends Node {

        public abstract Object execute(Node node, Object object);

        @Specialization
        @SuppressWarnings("unused")
        static String s0(int value,
                        @Cached(inline = false) CachedWarningTest1 inlinedNnode) {
            return "s0";
        }

    }

    // caller Inlined + Callee Inlinable -> Auto Inline
    @GenerateInline(true)
    public abstract static class InlinedWarningTest3 extends Node {

        public abstract Object execute(Node node, Object object);

        @Specialization
        @SuppressWarnings("unused")
        static String s0(Node node, int value,
                        @Cached SingleBitNode inlinedNnode) {
            return "s0";
        }

    }

    @GenerateCached(inherit = true)
    @GenerateInline(inlineByDefault = true, inherit = true)
    public abstract static class InlinedByDefaultCachedNode extends Node {
        abstract String execute(Node n, Object arg);

        @Specialization
        String doInt(@SuppressWarnings("unused") int i) {
            return "int";
        }

        @Specialization
        String doDouble(@SuppressWarnings("unused") double i) {
            return "double";
        }
    }

    public abstract static class UseInlinedByDefaultInlineUser extends UseInlinedByDefaultUser {
        abstract String execute(Node inliningTarget, Object arg, boolean useCorrectNode);

        @Override
        final String execute(Object arg, boolean useCorrectNode) {
            // Shortcut for when the node is not inlined, but invalid operation if the node is
            // inlined!
            return execute(null, arg, useCorrectNode);
        }
    }

    public abstract static class UseInlinedByDefaultUser extends Node {
        abstract String execute(Object arg, boolean useCorrectNode);
    }

    @GenerateCached
    @GenerateInline(false)
    public abstract static class UseInlinedByDefaultInCached extends UseInlinedByDefaultUser {
        @Specialization
        String doInt(Object arg, boolean useCorrectNode,
                        @Cached InlinedByDefaultCachedNode node) {
            return node.execute(useCorrectNode ? this : node, arg);
        }
    }

    @GenerateCached(alwaysInlineCached = true)
    @GenerateInline(false)
    public abstract static class UseInlinedByDefaultInCachedWithAlwaysInlineCached extends UseInlinedByDefaultUser {
        @Specialization
        String doInt(Object arg, boolean useCorrectNode,
                        @Cached InlinedByDefaultCachedNode node) {
            return node.execute(useCorrectNode ? this : node, arg);
        }
    }

    @GenerateInline
    @GenerateCached(alwaysInlineCached = true)
    public abstract static class UseInlinedByDefaultInCachedWithAlwaysInlineCachedAndGenerateInline extends UseInlinedByDefaultInlineUser {
        @Specialization
        static String doInt(Node inlineTarget, Object arg, boolean useCorrectNode,
                        @Cached InlinedByDefaultCachedNode node) {
            return node.execute(useCorrectNode ? inlineTarget : node, arg);
        }
    }

    @GenerateInline(false)
    public abstract static class UseInlinedByDefaultInCachedWithAlwaysInlineCachedAndGenerateInlineUser extends UseInlinedByDefaultUser {
        @Specialization
        String doInt(Object arg, boolean useCorrectNode,
                        @Cached(inline = true) UseInlinedByDefaultInCachedWithAlwaysInlineCachedAndGenerateInline node) {
            return node.execute(this, arg, useCorrectNode);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class UseInlinedByDefaultInInlineOnly extends UseInlinedByDefaultInlineUser {
        @Specialization
        static String doInt(Node inlineTarget, Object arg, boolean useCorrectNode,
                        @Cached InlinedByDefaultCachedNode node) {
            return node.execute(useCorrectNode ? inlineTarget : node, arg);
        }
    }

    @GenerateInline(false)
    public abstract static class UseInlinedByDefaultInInlineOnlyUser extends UseInlinedByDefaultUser {
        @Specialization
        String doInt(Object arg, boolean useCorrectNode,
                        @Cached UseInlinedByDefaultInInlineOnly node) {
            return node.execute(this, arg, useCorrectNode);
        }
    }

    @GenerateInline(false)
    public abstract static class UseInlinedByDefaultAndForceInlineVersion extends UseInlinedByDefaultUser {
        @Specialization
        String doInt(Object arg, boolean useCorrectNode,
                        @SuppressWarnings("truffle-unused") // forcing inline is redundant
                        @Cached(inline = true) InlinedByDefaultCachedNode node) {
            return node.execute(useCorrectNode ? this : node, arg);
        }
    }

    @GenerateInline(false)
    public abstract static class UseInlinedByDefaultAndForceCachedVersion extends Node {
        abstract String execute(Object arg);

        @Specialization
        String doInt(Object arg,
                        @Cached(inline = false) InlinedByDefaultCachedNode node) {
            // If 'node' were wrongly inlined, this would have to fail,
            // because it does not get any inlining target argument
            return node.execute(null, arg);
        }
    }

    public abstract static class InheritedInlinedByDefaultCachedNode extends InlinedByDefaultCachedNode {
        @Specialization
        String doString(@SuppressWarnings("unused") String s) {
            return "string";
        }
    }

    @GenerateCached
    @GenerateInline(false)
    public abstract static class UseInheritedInlinedByDefaultInCached extends UseInlinedByDefaultUser {
        @Specialization
        String doInt(Object arg, boolean useCorrectNode,
                        @Cached InheritedInlinedByDefaultCachedNode node) {
            return node.execute(useCorrectNode ? this : node, arg);
        }
    }

    @Test
    public void testInlineByDefaultInCached() {
        testInlineByDefaultCachedUser(UseInlinedByDefaultInCachedNodeGen.create());
        testInlineByDefaultCachedUser(UseInlinedByDefaultInCachedWithAlwaysInlineCachedNodeGen.create());
        testInlineByDefaultCachedUser(UseInlinedByDefaultInCachedWithAlwaysInlineCachedAndGenerateInlineNodeGen.create());
        testInlineByDefaultCachedUser(UseInlinedByDefaultAndForceInlineVersionNodeGen.create());
        testInlineByDefaultCachedUser(UseInheritedInlinedByDefaultInCachedNodeGen.create());
        // inline users are tested through another cached entry point:
        testInlineByDefaultCachedUser(UseInlinedByDefaultInCachedWithAlwaysInlineCachedAndGenerateInlineUserNodeGen.create());
        testInlineByDefaultCachedUser(UseInlinedByDefaultInInlineOnlyUserNodeGen.create());

        var forceCached = UseInlinedByDefaultAndForceCachedVersionNodeGen.create();
        assertEquals("int", forceCached.execute(42));
        assertEquals("double", forceCached.execute(3.14));

        var manuallyCreatedCachedVersion = InlinedByDefaultCachedNodeGen.create();
        assertEquals("int", manuallyCreatedCachedVersion.execute(null, 42));
        assertEquals("double", manuallyCreatedCachedVersion.execute(null, 3.14));
    }

    private static void testInlineByDefaultCachedUser(UseInlinedByDefaultUser userNode) {
        String testCaseName = userNode.getClass().getSimpleName();
        assertEquals(testCaseName, "int", userNode.execute(42, true));
        assertEquals(testCaseName, "double", userNode.execute(3.14, true));
        boolean thrown = false;
        try {
            userNode.execute(1, false);
        } catch (ClassCastException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Invalid inline context node passed to an inlined field"));
            thrown = true;
        }
        assertTrue(String.format("Node %s did not throw when it used wrong inlineTarget. Is the UseInlinedByDefault really inlined?", testCaseName), thrown);
    }
}
