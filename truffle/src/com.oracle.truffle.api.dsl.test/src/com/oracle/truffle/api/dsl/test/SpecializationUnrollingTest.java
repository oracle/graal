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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.dsl.test.GenerateInlineTest.SimpleNode;
import com.oracle.truffle.api.dsl.test.SpecializationUnrollingTestFactory.IdentityCacheNodeGen;
import com.oracle.truffle.api.dsl.test.SpecializationUnrollingTestFactory.InlineIdentityCacheNodeGen;
import com.oracle.truffle.api.dsl.test.SpecializationUnrollingTestFactory.InlineIdentityCacheSharedNodeGen;
import com.oracle.truffle.api.dsl.test.SpecializationUnrollingTestFactory.NoUnrollLibraryNodeGen;
import com.oracle.truffle.api.dsl.test.SpecializationUnrollingTestFactory.RemoveSpecializationNodeGen;
import com.oracle.truffle.api.dsl.test.SpecializationUnrollingTestFactory.ReplaceSpecializationNodeGen;
import com.oracle.truffle.api.dsl.test.SpecializationUnrollingTestFactory.UnrollAllNodeGen;
import com.oracle.truffle.api.dsl.test.SpecializationUnrollingTestFactory.UnrollHigherThanLimitNodeGen;
import com.oracle.truffle.api.dsl.test.SpecializationUnrollingTestFactory.UnrollLibraryNodeGen;
import com.oracle.truffle.api.dsl.test.SpecializationUnrollingTestFactory.UnrollNoneNodeGen;
import com.oracle.truffle.api.dsl.test.SpecializationUnrollingTestFactory.UnrollTwoNodeGen;
import com.oracle.truffle.api.dsl.test.SpecializationUnrollingTestFactory.UnrollWithCachedLibraryNodeGen;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;

@SuppressWarnings({"truffle-inlining", "truffle-neverdefault", "truffle-sharing", "unused"})
public class SpecializationUnrollingTest extends AbstractPolyglotTest {

    @GenerateInline
    public abstract static class InnerNode extends Node {

        abstract long execute(Node node, long value);

        @Specialization(guards = "v == 0")
        static long s0(long v) {
            return 0;
        }

        @Specialization(guards = "v == 1")
        static long s1(long v) {
            return 1;
        }

        @Specialization(guards = "v == 2")
        static long s2(long v) {
            return 2;
        }

        @Specialization(guards = "v == 3")
        static long s3(long v) {
            return 3;
        }

    }

    public abstract static class UnrollNoneNode extends Node {

        abstract long execute(long v0);

        @Specialization(guards = "cachedV0 == v0", limit = "2", unroll = 0)
        static long doInt(long v0,
                        @Bind Node node,
                        @Cached(inline = true) InnerNode test,
                        @Cached("v0") long cachedV0) {
            return test.execute(node, cachedV0);
        }

    }

    @Test
    public void testUnrollNone() {
        UnrollNoneNode node = adoptNode(UnrollNoneNodeGen.create()).get();
        assertEquals(0, node.execute(0));
        assertEquals(1, node.execute(1));
        assertFails(() -> node.execute(3), UnsupportedSpecializationException.class);
    }

    public abstract static class UnrollTwoNode extends Node {

        static int limit = 2;

        abstract long execute(long v0);

        @Specialization(guards = "cachedV0 == v0", limit = "limit", unroll = 2)
        static long doInt(long v0,
                        @Bind Node node,
                        @Cached(inline = true) InnerNode test,
                        @Cached("v0") long cachedV0) {
            return test.execute(node, cachedV0);
        }

    }

    @Test
    public void testUnrollTwo() {
        UnrollTwoNode node = adoptNode(UnrollTwoNodeGen.create()).get();
        assertEquals(0, node.execute(0));
        assertEquals(1, node.execute(1));
        assertFails(() -> node.execute(2), UnsupportedSpecializationException.class);
    }

    public abstract static class UnrollAllNode extends Node {

        static int limit = 2;

        abstract long execute(long v0);

        @Specialization(guards = "cachedV0 == v0", limit = "2", unroll = 2)
        static long doInt(long v0,
                        @Bind Node node,
                        @Cached(inline = true) InnerNode abs,
                        @Cached("v0") long cachedV0) {
            return abs.execute(node, cachedV0);
        }
    }

    @Test
    public void testUnrollAll() {
        UnrollAllNode node = adoptNode(UnrollAllNodeGen.create()).get();
        assertEquals(0, node.execute(0));
        assertEquals(1, node.execute(1));
        assertFails(() -> node.execute(3), UnsupportedSpecializationException.class);
    }

    public abstract static class UnrollHigherThanLimitNode extends Node {

        static int limit = 2;

        abstract long execute(long v0);

        @Specialization(guards = "cachedV0 == v0", limit = "limit", unroll = 3)
        static long doInt(long v0,
                        @Bind Node node,
                        @Cached(inline = true) InnerNode test,
                        @Cached("v0") long cachedV0) {
            return test.execute(node, cachedV0);
        }

    }

    @Test
    public void testUnrollHigherThanLimit() {
        UnrollHigherThanLimitNode node = adoptNode(UnrollHigherThanLimitNodeGen.create()).get();
        assertEquals(0, node.execute(0));
        assertEquals(1, node.execute(1));
        assertFails(() -> node.execute(3), UnsupportedSpecializationException.class);
    }

    public abstract static class UnrollWithCachedLibraryNode extends Node {

        static int limit = 1;

        abstract long execute(Object v0);

        @Specialization(guards = "guard(cachedV0,v0)", limit = "limit", unroll = 1)
        static long doInt(Object v0,
                        @CachedLibrary("v0") InteropLibrary lib,
                        @Cached("v0") Object cachedV0) {
            return 42;
        }

        static boolean guard(Object o, Object o2) {
            return o == o2;
        }

    }

    @Test
    public void testUnrollWithCachedLibrary() {
        UnrollWithCachedLibraryNode node = adoptNode(UnrollWithCachedLibraryNodeGen.create()).get();
        node.execute(0);
        node.execute(1);
        assertFails(() -> node.execute(2), UnsupportedSpecializationException.class);
    }

    public abstract static class RemoveSpecializationNode extends Node {

        static int limit = 3;

        abstract long execute(Assumption v0);

        @Specialization(guards = "v0 == assumption", limit = "limit", unroll = 2, assumptions = "assumption")
        static long doInt(Assumption v0,
                        @Cached("v0") Assumption assumption) {
            return 42;
        }

        static boolean guard(Object o, Object o2) {
            return o == o2;
        }
    }

    @Test
    public void testRemoveSpecialization() {
        RemoveSpecializationNode node = adoptNode(RemoveSpecializationNodeGen.create()).get();
        Assumption a0 = Truffle.getRuntime().createAssumption();
        Assumption a1 = Truffle.getRuntime().createAssumption();
        Assumption a2 = Truffle.getRuntime().createAssumption();

        node.execute(a0);
        node.execute(a1);
        node.execute(a2);

        // remove first unrolled
        a0.invalidate();
        a0 = Truffle.getRuntime().createAssumption();
        node.execute(a0);

        // remove second unrolled
        a1.invalidate();
        a1 = Truffle.getRuntime().createAssumption();
        node.execute(a1);

        // remove regular specialization
        a2.invalidate();
        a2 = Truffle.getRuntime().createAssumption();
        node.execute(a2);

        // test limit == 3
        assertFails(() -> node.execute(Truffle.getRuntime().createAssumption()), UnsupportedSpecializationException.class);
    }

    public abstract static class ReplaceSpecializationNode extends Node {

        static int limit = 2;

        abstract String execute(long v0);

        @Specialization(guards = "cachedV0 == v0", limit = "limit", unroll = 1)
        static String doInt(long v0,
                        @Bind Node node,
                        @Cached(inline = true) InnerNode test,
                        @Cached("v0") long cachedV0) {
            return "cached";
        }

        @Specialization(replaces = "doInt")
        static String doGeneric(long v0) {
            return "generic";
        }

    }

    @Test
    public void testReplaceSpecialization() {
        ReplaceSpecializationNode node = adoptNode(ReplaceSpecializationNodeGen.create()).get();
        assertEquals("cached", node.execute(0));
        assertEquals("cached", node.execute(1));
        assertEquals("generic", node.execute(2));
        assertEquals("generic", node.execute(0));
        assertEquals("generic", node.execute(1));
    }

    public abstract static class ReplaceAllUnrolledNode extends Node {

        abstract String execute(long v0);

        @Specialization(guards = "cachedV0 == v0", limit = "2", unroll = 2)
        static String doInt(long v0,
                        @Bind Node node,
                        @Cached(inline = true) InnerNode test,
                        @Cached("v0") long cachedV0) {
            return "cached";
        }

        @Specialization(replaces = "doInt")
        static String doGeneric(long v0) {
            return "generic";
        }

    }

    @Test
    public void testReplaceAllUnrolled() {
        ReplaceSpecializationNode node = adoptNode(ReplaceSpecializationNodeGen.create()).get();
        assertEquals("cached", node.execute(0));
        assertEquals("cached", node.execute(1));
        assertEquals("generic", node.execute(2));
        assertEquals("generic", node.execute(0));
        assertEquals("generic", node.execute(1));
    }

    @SuppressWarnings("unused")
    @GenerateInline
    @GenerateCached(false)
    public abstract static class InlineWithUnrollNode extends Node {

        abstract Object execute(Node node, Object arg);

        @Specialization(guards = "arg == cachedArg", limit = "3", unroll = 3)
        static Object doInt(Node node, int arg,
                        @Cached SimpleNode simpleNode,
                        @Cached SimpleNode simpleNode2,
                        @Cached("arg") int cachedArg) {
            return simpleNode.execute(node, cachedArg);
        }
    }

    @GenerateInline
    public abstract static class UnrollInlineWithUnrollNode extends Node {

        abstract Object execute(Node node, Object arg);

        @Specialization(guards = "arg == cachedArg", limit = "3", unroll = 3)
        static Object doInt(Node node, int arg,
                        @Cached("arg") int cachedArg,
                        @Cached InlineWithUnrollNode simpleNode) {
            return simpleNode.execute(node, cachedArg);
        }
    }

    public abstract static class UseInlineWithUnrollNode extends Node {

        abstract Object execute(Object arg);

        @Specialization()
        Object doInt(int arg, @Cached InlineWithUnrollNode simpleNode) {
            return simpleNode.execute(this, arg);
        }
    }

    @Test
    public void testIdentityCacheNode() {
        IdentityCacheNode node = adoptNode(IdentityCacheNodeGen.create()).get();
        Object o = new Object();
        String s = "a";

        assertEquals("cached", node.execute(node, o, s, (byte) 42, 42, 42L, 42f, 42d, true, (short) 42, (char) 42));
        assertEquals("cached", node.execute(node, o, s, (byte) 41, 42, 42L, 42f, 42d, true, (short) 42, (char) 42));

        assertFails(() -> {
            node.execute(node, o, s, (byte) 40, 42, 42L, 42f, 42d, true, (short) 42, (char) 42);
        }, UnsupportedSpecializationException.class);
    }

    @GenerateInline
    public abstract static class IdentityCacheNode extends Node {
        public abstract String execute(Node node, Object arg0, String arg1,
                        byte arg2, int arg3, long arg4, float arg5, double arg6,
                        boolean arg7, short arg8, char arg9);

        @Specialization(guards = {
                        "arg0 == cachedArg0",
                        "arg1 == cachedArg1",
                        "arg2 == cachedArg2",
                        "arg3 == cachedArg3",
                        "arg4 == cachedArg4",
                        "arg5 == cachedArg5",
                        "arg6 == cachedArg6",
                        "arg7 == cachedArg7",
                        "arg8 == cachedArg8",
                        "arg9 == cachedArg9",

        }, limit = "2", unroll = 2)
        public String doCached(Node node, Object arg0, String arg1,
                        byte arg2, int arg3, long arg4, float arg5, double arg6,
                        boolean arg7, short arg8, char arg9,
                        @Cached("arg0") Object cachedArg0,
                        @Cached("arg1") String cachedArg1,
                        @Cached("arg2") byte cachedArg2,
                        @Cached("arg3") int cachedArg3,
                        @Cached("arg4") long cachedArg4,
                        @Cached("arg5") float cachedArg5,
                        @Cached("arg6") double cachedArg6,
                        @Cached("arg7") boolean cachedArg7,
                        @Cached("arg8") short cachedArg8,
                        @Cached("arg9") char cachedArg9) {
            return "cached";
        }
    }

    @Test
    public void testInlinedIdentityCacheNode() {
        InlineIdentityCacheNode node = adoptNode(InlineIdentityCacheNodeGen.create()).get();
        Object o = new Object();
        String s = "a";

        assertEquals("cached", node.execute(node, o, s, (byte) 42, 42, 42L, 42f, 42d, true, (short) 42, (char) 42));
        assertEquals("cached", node.execute(node, o, s, (byte) 41, 42, 42L, 42f, 42d, true, (short) 42, (char) 42));

        assertFails(() -> {
            node.execute(node, o, s, (byte) 40, 42, 42L, 42f, 42d, true, (short) 42, (char) 42);
        }, UnsupportedSpecializationException.class);
    }

    public abstract static class InlineIdentityCacheNode extends Node {

        public abstract String execute(Node node, Object arg0, String arg1,
                        byte arg2, int arg3, long arg4, float arg5, double arg6,
                        boolean arg7, short arg8, char arg9);

        @Specialization
        public String doCached(Node node, Object arg0, String arg1,
                        byte arg2, int arg3, long arg4, float arg5, double arg6,
                        boolean arg7, short arg8, char arg9,
                        @Cached(inline = true) IdentityCacheNode unrolledIdentity) {
            return unrolledIdentity.execute(node, arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9);
        }

    }

    @Test
    public void testInlinedIdentityCacheSharedNode() {
        InlineIdentityCacheSharedNode node = adoptNode(InlineIdentityCacheSharedNodeGen.create()).get();
        Object o = "b";
        String s = "a";

        assertEquals("cached", node.execute(node, o, s, (byte) 42, 42, 42L, 42f, 42d, true, (short) 42, (char) 42));
        assertEquals("cached", node.execute(node, o, s, (byte) 41, 42, 42L, 42f, 42d, true, (short) 42, (char) 42));

        assertFails(() -> {
            node.execute(node, o, s, (byte) 40, 42, 42L, 42f, 42d, true, (short) 42, (char) 42);
        }, UnsupportedSpecializationException.class);
    }

    public abstract static class InlineIdentityCacheSharedNode extends Node {

        public abstract String execute(Node node, Object arg0, String arg1,
                        byte arg2, int arg3, long arg4, float arg5, double arg6,
                        boolean arg7, short arg8, char arg9);

        @Specialization
        public String doCached1(Node node, String arg0, String arg1,
                        byte arg2, int arg3, long arg4, float arg5, double arg6,
                        boolean arg7, short arg8, char arg9,
                        @Shared("unrolledIdentity") @Cached(inline = true) IdentityCacheNode unrolledIdentity) {
            return unrolledIdentity.execute(node, arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9);
        }

        @Specialization
        public String doCached2(Node node, Object arg0, String arg1,
                        byte arg2, int arg3, long arg4, float arg5, double arg6,
                        boolean arg7, short arg8, char arg9,
                        @Shared("unrolledIdentity") @Cached(inline = true) IdentityCacheNode unrolledIdentity) {
            return unrolledIdentity.execute(node, arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9);
        }

    }

    @Test
    public void testUnrollLibrary() {
        UnrollLibraryNode node = adoptNode(UnrollLibraryNodeGen.create()).get();
        String s = "b";
        int i = 42;
        TruffleObject o = new TruffleObject() {
        };

        assertSame(node, node.execute(s).getParent().getParent());
        assertSame(node, node.execute(i).getParent().getParent());
        // switch to uncached -> replaces doDefault.
        assertNull(node.execute(o).getParent());
        assertNull(node.execute(i).getParent());
        assertNull(node.execute(s).getParent());
    }

    public abstract static class UnrollLibraryNode extends Node {

        abstract InteropLibrary execute(Object arg);

        @Specialization(limit = "2", unroll = 2)
        InteropLibrary doDefault(Object arg,
                        @CachedLibrary("arg") InteropLibrary lib) {
            return lib;
        }

    }

    @Test
    public void testNoUnrollLibrary() {
        NoUnrollLibraryNode node = adoptNode(NoUnrollLibraryNodeGen.create()).get();
        String s = "b";
        int i = 42;
        TruffleObject o = new TruffleObject() {
        };

        assertSame(node, node.execute(s).getParent().getParent());
        assertSame(node, node.execute(i).getParent().getParent());
        // switch to uncached -> replaces doDefault.
        assertNull(node.execute(o).getParent());
        assertNull(node.execute(s).getParent());
        assertNull(node.execute(i).getParent());
    }

    public abstract static class NoUnrollLibraryNode extends Node {

        abstract InteropLibrary execute(Object arg);

        @Specialization(limit = "2")
        InteropLibrary doDefault(Object arg,
                        @CachedLibrary("arg") InteropLibrary lib) {
            return lib;
        }

    }

    public abstract static class UnrollLibraryWithCustomCacheNode extends Node {

        abstract Object execute(Object arg);

        @Specialization(guards = "arg == cachedArg", limit = "3", unroll = 3)
        int doDefault(Object arg,
                        @CachedLibrary("arg") InteropLibrary lib,
                        @Cached("arg") Object cachedArg) {
            return 42;
        }

    }

    @GenerateInline
    public abstract static class UnrollLibraryInlineNode extends Node {

        abstract Object execute(Node node, Object arg);

        @Specialization(limit = "3", unroll = 3)
        int doDefault(Object arg,
                        @CachedLibrary("arg") InteropLibrary lib) {
            return 42;
        }

    }

//
    @GenerateInline
    public abstract static class UnrollLibraryInlineCustomCacheNode extends Node {

        abstract Object execute(Node node, Object arg);

        @Specialization(guards = "arg == cachedArg", limit = "3")
        int doDefault(Object arg,
                        @Cached("arg") Object cachedArg) {
            return 42;
        }

    }

}
