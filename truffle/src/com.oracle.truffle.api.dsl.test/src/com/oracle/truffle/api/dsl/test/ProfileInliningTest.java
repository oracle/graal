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

import static com.oracle.truffle.api.test.ReflectionUtils.invoke;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.ProfileInliningTestFactory.LoopConditionUsageNodeGen;
import com.oracle.truffle.api.dsl.test.ProfileInliningTestFactory.UsageNodeGen;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ByteValueProfile;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedByteValueProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedCountingConditionProfile;
import com.oracle.truffle.api.profiles.InlinedIntValueProfile;
import com.oracle.truffle.api.profiles.InlinedLongValueProfile;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;
import com.oracle.truffle.api.profiles.InlinedProfile;
import com.oracle.truffle.api.profiles.IntValueProfile;
import com.oracle.truffle.api.profiles.LongValueProfile;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;

@SuppressWarnings({"truffle-inlining", "truffle-neverdefault", "truffle-sharing", "unused"})
public class ProfileInliningTest extends AbstractPolyglotTest {

    @Test
    public void testCachedUsageNode() {
        UsageNode node = adoptNode(UsageNodeGen.create()).get();
        testUsageNode(node);
    }

    @Test
    public void testUncacheddUsageNode() {
        UsageNode node = adoptNode(UsageNodeGen.create()).get();
        testUsageNode(UsageNodeGen.getUncached());
    }

    private static void testUsageNode(UsageNode node) {
        assertEquals(true, node.execute(true));
        assertEquals(false, node.execute(false));
        assertEquals(true, node.execute(true));
        assertEquals(false, node.execute(false));
        assertEquals(true, node.execute(true));
        assertEquals(false, node.execute(false));

        assertEquals(42, node.execute(42));
        assertEquals(43, node.execute(43));
        assertEquals(44, node.execute(44));

        assertEquals((byte) 42, node.execute((byte) 42));
        assertEquals((byte) 43, node.execute((byte) 43));
        assertEquals((byte) 44, node.execute((byte) 44));

        assertEquals(Long.MAX_VALUE - 1, node.execute(Long.MAX_VALUE - 1));
        assertEquals(Long.MAX_VALUE - 2, node.execute(Long.MAX_VALUE - 2));
    }

    @GenerateCached(alwaysInlineCached = true)
    @GenerateUncached
    @SuppressWarnings({"unused"})
    public abstract static class UsageNode extends Node {

        abstract Object execute(Object arg);

        @Specialization(guards = "triggerGuards(arg, g0, g1, g2)", limit = "1")
        @TruffleBoundary
        final Object doBoolean(boolean arg,
                        @Cached InlinedBranchProfile g0,
                        @Cached InlinedConditionProfile g1,
                        @Cached InlinedCountingConditionProfile g2,
                        @Cached InlinedBranchProfile p0,
                        @Cached InlinedConditionProfile p1,
                        @Cached InlinedCountingConditionProfile p2) {
            assertFalse(isUninitialized(this, g0));
            assertFalse(isUninitialized(this, g1));
            assertFalse(isUninitialized(this, g2));
            int trueCount = (int) invokeProfileMethod(this, g2, "getTrueCount");

            triggerGuards(arg, p0, p1, p2);

            assertFalse(isUninitialized(this, p0));
            assertFalse(isUninitialized(this, p1));
            assertFalse(isUninitialized(this, p2));

            return arg;
        }

        @TruffleBoundary
        boolean triggerGuards(boolean arg, InlinedBranchProfile p0,
                        InlinedConditionProfile p1,
                        InlinedCountingConditionProfile p2) {
            p0.enter(this);
            p1.profile(this, arg);
            p2.profile(this, arg);
            return true;
        }

        @Specialization
        final Object doInt(int arg,
                        @Cached InlinedIntValueProfile p) {
            return p.profile(this, arg);
        }

        @Specialization
        Object doByte(byte arg,
                        @Cached InlinedByteValueProfile p) {
            return p.profile(this, arg);
        }

        @Specialization
        static Object doLong(long arg,
                        @Bind Node node,
                        @Cached InlinedLongValueProfile p) {
            return p.profile(node, arg);
        }

    }

    @Test
    public void testCachedLoopConditionUsageNode() {
        testLoopConditionUsageNode(adoptNode(LoopConditionUsageNodeGen.create()).get());
    }

    @Test
    public void testUncachedLoopConditionUsageNode() {
        testLoopConditionUsageNode(LoopConditionUsageNodeGen.getUncached());
    }

    private static void testLoopConditionUsageNode(LoopConditionUsageNode node) {
        assertEquals(true, node.execute(true));
        assertEquals(false, node.execute(false));
    }

    @GenerateCached(alwaysInlineCached = true)
    @GenerateUncached
    @SuppressWarnings({"unused"})
    public abstract static class LoopConditionUsageNode extends Node {

        abstract Object execute(Object arg);

        @Specialization(guards = "triggerGuards(arg, g)", limit = "1")
        @TruffleBoundary
        final Object doBoolean(boolean arg,
                        @Cached InlinedLoopConditionProfile g,
                        @Cached InlinedLoopConditionProfile p) {
            assertFalse(isUninitialized(this, g));
            triggerGuards(arg, p);

            assertFalse(isUninitialized(this, p));
            return arg;
        }

        @TruffleBoundary
        boolean triggerGuards(boolean arg, InlinedLoopConditionProfile p) {
            p.profileCounted(this, 1);
            p.inject(this, arg);
            p.profile(this, arg);
            return true;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static class UsageExport implements TruffleObject {

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("unused")
        static Object execute(UsageExport receiver, Object[] arguments,
                        @Bind Node node,
                        @Cached InlinedBranchProfile p0,
                        @Cached InlinedConditionProfile p1,
                        @Cached InlinedCountingConditionProfile p2,
                        // add regular profiles such that useSpecializationClass becomes true
                        @Cached(inline = false) ByteValueProfile byteValue,
                        @Cached(inline = false) IntValueProfile intValue,
                        @Cached(inline = false) LongValueProfile longValue) {
            p0.enter(node);
            return p1.profile(node, true) & p2.profile(node, true);
        }

    }

    @Test
    public void testExport() throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
        UsageExport export = new UsageExport();
        InteropLibrary node = adoptNode(InteropLibrary.getFactory().create(export)).get();
        node.execute(export);
    }

    public abstract static class BaseNode extends Node {

        abstract int execute(Node node);

    }

    @SuppressWarnings({"unused", "truffle"})
    public abstract static class TestInlineMessage1 extends BaseNode {

        // even though BranchProfile needs a different type we report as inlinable
        @Specialization
        int s0(@ExpectError("The cached type 'BranchProfile' supports object-inlining. %") //
        @Cached BranchProfile p0) {
            return 0;
        }
    }

    @SuppressWarnings({"unused", "truffle"})
    public abstract static class TestInlineMessage2 extends BaseNode {

        // test warning goes away
        @Specialization
        int s0(@Cached(inline = false) BranchProfile p0) {
            return 0;
        }
    }

    @SuppressWarnings({"unused", "truffle"})
    @GenerateCached(alwaysInlineCached = false)
    public abstract static class TestInlineMessage3 extends BaseNode {

        // test warning goes away
        @Specialization
        int s0(@Cached(inline = false) BranchProfile p0) {
            return 0;
        }
    }

    @SuppressWarnings("unused")
    public abstract static class TestInlineMessage4 extends BaseNode {

        // give a reasonable error that the branch profile type needs to be updated
        @Specialization
        int s0(@ExpectError("Invalid return type com.oracle.truffle.api.profiles.InlinedBranchProfile found but expected com.oracle.truffle.api.profiles.BranchProfile. " +
                        "This is a common error if a different type is required for inlining.") //
        @Cached(inline = true) BranchProfile p0) {
            return 0;
        }
    }

    @SuppressWarnings("unused")
    public abstract static class TestInlineMessage5 extends BaseNode {

        // test warning goes away
        @Specialization
        int s0(@ExpectError("Error parsing expression 'create()': The method create is undefined for the enclosing scope.") //
        @Cached(inline = false) InlinedBranchProfile p0) {
            return 0;
        }
    }

    @SuppressWarnings({"unused", "truffle"})
    public abstract static class TestInlineMessage6 extends BaseNode {

        // test no warning for inlinable
        @Specialization
        int s0(@ExpectError("Redundant specification of @Cached(... inline=true)%") //
        @Cached(inline = true) InlinedBranchProfile p0) {
            return 0;
        }
    }

    @SuppressWarnings("unused")
    @GenerateInline
    @GenerateCached(false)
    public abstract static class TestInlineMessage7 extends BaseNode {

        // test no warning for inlinable
        @Specialization
        static int s0(Node node, @Cached InlinedBranchProfile p0) {
            return 0;
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @SuppressWarnings("unused")
    public abstract static class TestInlineMessage8 extends BaseNode {

        @Specialization
        static int s0(
                        @ExpectError("Invalid return type com.oracle.truffle.api.profiles.InlinedBranchProfile found but expected com.oracle.truffle.api.profiles.BranchProfile. " +
                                        "This is a common error if a different type is required for inlining.") //
                        @Cached BranchProfile p0) {
            return 0;
        }
    }

    private static boolean isUninitialized(Node node, InlinedProfile profile) {
        return (boolean) invokeProfileMethod(node, profile, "isUninitialized");
    }

    @SuppressWarnings("rawtypes")
    private static Object invokeProfileMethod(Node node, InlinedProfile profile, String name) {
        return invoke(profile, name, new Class[]{Node.class}, node);
    }

}
