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
import static org.junit.Assert.assertSame;

import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.dsl.test.GenerateInlineTest.SimpleNode;
import com.oracle.truffle.api.dsl.test.SharedCachedTestFactory.SharedCachedInMultiInstanceNodeGen;
import com.oracle.truffle.api.dsl.test.SharedCachedTestFactory.SharedStringInGuardNodeGen;
import com.oracle.truffle.api.dsl.test.SharedCachedTestFactory.UnboundExclusiveObjectNodeGen;
import com.oracle.truffle.api.dsl.test.SharedCachedTestFactory.UnboundSharedObjectNodeGen;
import com.oracle.truffle.api.dsl.test.SharedCachedTestFactory.UseGenerateInlineSharedNodeGen;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.Encoding;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;

@SuppressWarnings({"truffle-inlining", "truffle-neverdefault", "unused"})
public class SharedCachedTest {

    abstract static class UnboundCachedPrimitiveNode extends Node {

        abstract Object execute(Object arg);

        @Specialization(guards = "arg == 42")
        Object s0(int arg, @Shared @Cached("arg") int primitive) {
            return arg;
        }

        @Specialization(guards = "arg == 43")
        Object s1(int arg, @Shared @Cached("arg") int primitive) {
            return arg;
        }
    }

    abstract static class UnboundSharedObjectNode extends Node {

        static final Object ARG0 = new Object();
        static final Object ARG1 = new Object();

        abstract Object execute(Object arg);

        @Specialization(guards = "arg == ARG0")
        Object s0(Object arg, @Shared @Cached("arg") Object cachedArg) {
            return cachedArg;
        }

        @Specialization(guards = "arg == ARG1")
        Object s1(Object arg, @Shared @Cached("arg") Object cachedArg) {
            return cachedArg;
        }
    }

    @Test
    public void testUnboundSharedObject() {
        UnboundSharedObjectNode node;

        node = UnboundSharedObjectNodeGen.create();
        assertSame(UnboundSharedObjectNode.ARG0, node.execute(UnboundSharedObjectNode.ARG0));
        assertSame(UnboundSharedObjectNode.ARG0, node.execute(UnboundSharedObjectNode.ARG1));

        node = UnboundSharedObjectNodeGen.create();
        assertSame(UnboundSharedObjectNode.ARG1, node.execute(UnboundSharedObjectNode.ARG1));
        assertSame(UnboundSharedObjectNode.ARG1, node.execute(UnboundSharedObjectNode.ARG0));
    }

    @SuppressWarnings("truffle-inlining")
    abstract static class UnboundExclusiveObjectNode extends Node {

        static final Object ARG0 = new Object();
        static final Object ARG1 = new Object();

        abstract Object execute(Object arg);

        @Specialization(guards = "arg == ARG0")
        Object s0(Object arg, @Exclusive @Cached(value = "arg", neverDefault = true) Object cachedArg) {
            return cachedArg;
        }

        @Specialization(guards = "arg == ARG1")
        Object s1(Object arg, @Exclusive @Cached(value = "arg", neverDefault = true) Object cachedArg) {
            return cachedArg;
        }
    }

    @Test
    public void testUnboundExclusiveObject() {
        UnboundExclusiveObjectNode node;

        node = UnboundExclusiveObjectNodeGen.create();
        assertSame(UnboundExclusiveObjectNode.ARG0, node.execute(UnboundExclusiveObjectNode.ARG0));
        assertSame(UnboundExclusiveObjectNode.ARG1, node.execute(UnboundExclusiveObjectNode.ARG1));

        node = UnboundExclusiveObjectNodeGen.create();
        assertSame(UnboundExclusiveObjectNode.ARG1, node.execute(UnboundExclusiveObjectNode.ARG1));
        assertSame(UnboundExclusiveObjectNode.ARG0, node.execute(UnboundExclusiveObjectNode.ARG0));
    }

    abstract static class TestNode extends Node {

        abstract boolean execute(Object arg);

        static TestNode create() {
            return new TestNode() {
                @Override
                boolean execute(Object arg) {
                    return true;
                }
            };
        }

    }

    @GenerateInline
    @GenerateUncached
    @GenerateCached(false)
    public abstract static class GenerateInlineSharedNode extends Node {

        abstract Object execute(Node node, Object arg);

        @Specialization
        static Object doInt(Node node, int arg, @Shared @Cached SimpleNode innerNode) {
            return innerNode.execute(node, arg);
        }

        @Specialization
        static Object doLong(Node node, long arg, @Shared @Cached SimpleNode innerNode) {
            return innerNode.execute(node, arg);
        }

        @Specialization
        static Object doObject(Node node, Object arg, @Shared @Cached SimpleNode innerNode) {
            return innerNode.execute(node, arg);
        }

    }

    @GenerateUncached
    public abstract static class UseGenerateInlineSharedNode extends Node {

        abstract Object execute(Object arg);

        @Specialization
        final Object doInt(Object arg, @Cached GenerateInlineSharedNode innerNode) {
            return innerNode.execute(this, arg);
        }
    }

    @Test
    public void testUseGenerateInlineSharedNode() {
        UseGenerateInlineSharedNode node = UseGenerateInlineSharedNodeGen.create();

        assertEquals(42, node.execute(42));
        assertEquals(42L, node.execute(42L));
        AbstractPolyglotTest.assertFails(() -> node.execute(""), UnsupportedSpecializationException.class);
    }

    abstract static class UnboundCachedNodeNode extends Node {

        abstract Object execute(Object arg);

        @Specialization(guards = "arg == 42")
        Object s0(int arg, @Shared @Cached TestNode group) {
            return arg;
        }

        @Specialization(guards = "arg == 43")
        Object s1(int arg, @Shared @Cached TestNode group) {
            return arg;
        }
    }

    @SuppressWarnings("truffle-sharing")
    abstract static class BoundCachedNodeNode extends Node {

        abstract Object execute(Object arg);

        @Specialization(guards = {"node.execute(arg)", "arg == 43"}, limit = "3")
        Object s0(int arg, @Cached TestNode node) {
            return arg;
        }

        @Specialization(guards = {"node.execute(arg)", "arg == 43"}, limit = "3")
        Object s1(int arg, @Cached TestNode node) {
            return arg;
        }
    }

    abstract static class ExplicitNameTestNode extends Node {

        abstract Object execute(Object arg);

        @Specialization(guards = "arg == 42")
        Object s0(int arg, @Shared("group") @Cached TestNode value) {
            return arg;
        }

        @Specialization(guards = "arg == 43")
        Object s1(int arg, @Shared @Cached TestNode group) {
            return arg;
        }
    }

    abstract static class ErrorUnboundCachedNode2 extends Node {

        abstract Object execute(Object arg);

        @Specialization(guards = "arg == 42")
        Object s0(int arg,
                        @Exclusive @Cached TestNode node) {
            return arg;
        }

        @Specialization(guards = "arg == 43")
        Object s1(int arg,
                        @Exclusive @Cached TestNode node) {
            return arg;
        }
    }

    abstract static class ErrorUnboundCachedNode3 extends Node {

        abstract Object execute(Object arg);

        @Specialization(guards = "arg == 42")
        Object s0(int arg,
                        @Exclusive @Cached TestNode node) {
            return arg;
        }

        @Specialization(guards = "arg == 43")
        Object s1(int arg,
                        @ExpectError("No other cached parameters are specified as shared with the group 'foo'.") //
                        @Shared("foo") @Cached TestNode node) {
            return arg;
        }
    }

    // invalid name
    abstract static class ErrorInvalidGroup1 extends Node {

        abstract Object execute(Object arg);

        @Specialization
        Object s0(int arg,
                        @ExpectError("No other cached parameters are specified as shared with the group 'foobarb'. Did you mean 'foobar'?") //
                        @Shared("foobarb") @Cached TestNode node) {
            return arg;
        }

        @Specialization
        Object s1(long arg,
                        @ExpectError("No other cached parameters are specified as shared with the group 'foobar'. Did you mean 'foobarb'?") //
                        @Shared("foobar") @Cached TestNode node) {
            return arg;
        }
    }

    // invalid type
    abstract static class ErrorInvalidGroup2 extends Node {

        abstract Object execute(Object arg);

        @Specialization
        Object s0(int arg,
                        @ExpectError("Could not share some of the cached parameters in group 'shared': %n" +
                                        "  - s1(..., @Cached(...) long node) : The cache parameter type does not match. Expected 'int' but was 'long'.%n" +
                                        "Remove the @Shared annotation or resolve the described issues to allow sharing.") //
                        @Shared("shared") @Cached("arg") int node) {
            return arg;
        }

        @Specialization
        Object s1(long arg,
                        @ExpectError("Could not share some of the cached parameters in group 'shared': %n" +
                                        "  - s0(..., @Cached(...) int node) : The cache parameter type does not match. Expected 'long' but was 'int'.%n" +
                                        "Remove the @Shared annotation or resolve the described issues to allow sharing.")//
                        @Shared("shared") @Cached("arg") long node) {
            return arg;
        }
    }

    // invalid cache initializer
    abstract static class ErrorInvalidGroup3 extends Node {

        abstract Object execute(Object arg);

        @Specialization
        Object s0(int arg,
                        @ExpectError("Could not share some of the cached parameters in group 'shared': %n" +
                                        "  - s1(..., @Cached(...) int node) : The cache initializer does not match.%n" +
                                        "Remove the @Shared annotation or resolve the described issues to allow sharing.") //
                        @Shared("shared") @Cached("arg") int node) {
            return arg;
        }

        @Specialization
        Object s1(long arg,
                        @ExpectError("Could not share some of the cached parameters in group 'shared': %n" +
                                        "  - s0(..., @Cached(...) int node) : The cache initializer does not match.%n" +
                                        "Remove the @Shared annotation or resolve the described issues to allow sharing.") //
                        @Shared("shared") @Cached("42") int node) {
            return arg;
        }
    }

    // invalid multiple instances
    abstract static class ErrorInvalidGroup4 extends Node {

        abstract Object execute(Object arg);

        @Specialization(guards = "node == arg", limit = "3")
        Object s0(int arg,
                        @Cached("arg") int node,
                        @ExpectError("Could not share some of the cached parameters in group 'shared': %n" +
                                        "  - s1(..., @Cached(...) int shared) : The specialization 's0(int, int, int)' has multiple instances.%n" +
                                        "Remove the @Shared annotation or resolve the described issues to allow sharing.")//
                        @Shared("shared") @Cached("arg") int shared) {
            return arg;
        }

        @Specialization(guards = "arg == node", limit = "3")
        Object s1(int arg,
                        @Cached("arg") int node,
                        @ExpectError("Could not share some of the cached parameters in group 'shared': %n" +
                                        "  - s0(..., @Cached(...) int shared) : The specialization 's1(int, int, int)' has multiple instances.%n" +
                                        "Remove the @Shared annotation or resolve the described issues to allow sharing.")//
                        @Shared("shared") @Cached("arg") int shared) {
            return arg;
        }
    }

    // invalid sharing between the same specialization
    abstract static class ErrorInvalidGroup5 extends Node {

        abstract Object execute(Object arg);

        @Specialization
        Object s0(int arg,
                        @ExpectError("Could not share some of the cached parameters in group 'shared': %n" +
                                        "  - s0(..., @Cached(...) int cached2) : Cannot share caches within the same specialization.%n" +
                                        "Remove the @Shared annotation or resolve the described issues to allow sharing.") //
                        @Shared("shared") @Cached("arg") int cached1,
                        @ExpectError("Could not share some of the cached parameters in group 'shared': %n" +
                                        "  - s0(..., @Cached(...) int cached1,...) : Cannot share caches within the same specialization.%n" +
                                        "Remove the @Shared annotation or resolve the described issues to allow sharing.") //
                        @Shared("shared") @Cached("arg") int cached2) {
            return arg;
        }

    }

    @SuppressWarnings("unused")
    public abstract static class SharedStringInGuardNode extends Node {

        public abstract Object execute(String arg0);

        @Specialization(guards = "name == cachedName")
        public Object s0(String name,
                        @Cached(value = "name", neverDefault = true) @Shared("name") String cachedName) {
            return cachedName;
        }

        @Specialization(guards = "name == cachedName")
        public Object s1(String name,
                        @Cached(value = "name", neverDefault = true) @Shared("name") String cachedName) {
            return cachedName;
        }
    }

    abstract static class ErrorNoSharingTestNode extends Node {

        abstract Object execute(Object arg);

        @Specialization(guards = "arg == 42")
        Object s0(int arg,
                        @ExpectError("The cached parameter may be shared with: %n  - s1(..., @Cached(...) TestNode group)%") //
                        @Cached TestNode value) {
            return arg;
        }

        @Specialization(guards = "arg == 43")
        Object s1(int arg,
                        @ExpectError("No other cached parameters are specified as shared with the group 'group'.")
                        //
                        @Shared @Cached TestNode group) {
            return arg;
        }
    }

    @Test
    public void testObjectReference() {
        SharedStringInGuardNode node = SharedStringInGuardNodeGen.create();
        assertEquals("a", node.execute("a"));
        AbstractPolyglotTest.assertFails(() -> node.execute("b"), UnsupportedSpecializationException.class);
        assertEquals("a", node.execute("a"));

        SharedStringInGuardNode errorNode = SharedStringInGuardNodeGen.create();
        AbstractPolyglotTest.assertFails(() -> errorNode.execute(null), IllegalStateException.class, (e) -> {
            assertEquals("A specialization returned a default value for a cached initializer. Default values are not supported for shared cached initializers because the default value is reserved for the uninitialized state.",
                            e.getMessage());
        });
    }

    @Test
    public void testSharedCachedInMultiInstanceNode() {
        SharedCachedInMultiInstanceNode node = SharedCachedInMultiInstanceNodeGen.create();
        TruffleString a = TruffleString.fromJavaStringUncached("a", Encoding.UTF_16);
        TruffleString b = TruffleString.fromJavaStringUncached("b", Encoding.UTF_16);
        TruffleString c = TruffleString.fromJavaStringUncached("c", Encoding.UTF_16);

        assertEquals(a, node.execute(a));
        assertEquals(b, node.execute(b));
        assertEquals(c, node.execute(c));
    }

    public abstract static class SharedCachedInMultiInstanceNode extends Node {

        abstract Object execute(TruffleString name);

        @Specialization(guards = {"stringEquals(equalsNode, cachedName, name)"}, limit = "2")
        protected TruffleString doCached(TruffleString name,
                        @Cached("name") TruffleString cachedName,
                        @Cached @Shared TruffleString.EqualNode equalsNode,
                        @Cached("doGeneric(name)") TruffleString cachedResult) {
            return cachedResult;
        }

        static boolean stringEquals(TruffleString.EqualNode equalNode, TruffleString s1, TruffleString s2) {
            return equalNode.execute(s1, s2, Encoding.UTF_16);
        }

        @TruffleBoundary
        @Specialization(replaces = "doCached")
        protected TruffleString doGeneric(TruffleString name) {
            return name;
        }
    }

}
