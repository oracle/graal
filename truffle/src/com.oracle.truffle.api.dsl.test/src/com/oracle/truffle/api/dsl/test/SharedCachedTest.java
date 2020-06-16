/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertSame;

import org.junit.Test;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.SharedCachedTestFactory.UnboundExclusiveObjectNodeGen;
import com.oracle.truffle.api.dsl.test.SharedCachedTestFactory.UnboundSharedObjectNodeGen;
import com.oracle.truffle.api.nodes.Node;

@SuppressWarnings("unused")
public class SharedCachedTest {

    // TODO how to share primitive caches? maybe through a specialization class?
    abstract static class UnboundCachedPrimitiveNode extends Node {

        abstract Object execute(Object arg);

        @Specialization(guards = "arg == 42")
        Object s0(int arg, @Shared("shared") @Cached("arg") int primitive) {
            return arg;
        }

        @Specialization(guards = "arg == 43")
        Object s1(int arg, @Shared("shared") @Cached("arg") int primitive) {
            return arg;
        }
    }

    abstract static class UnboundSharedObjectNode extends Node {

        static final Object ARG0 = new Object();
        static final Object ARG1 = new Object();

        abstract Object execute(Object arg);

        @Specialization(guards = "arg == ARG0")
        Object s0(Object arg, @Shared("shared") @Cached("arg") Object cachedArg) {
            return cachedArg;
        }

        @Specialization(guards = "arg == ARG1")
        Object s1(Object arg, @Shared("shared") @Cached("arg") Object cachedArg) {
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

    @SuppressWarnings("unused")
    abstract static class UnboundExclusiveObjectNode extends Node {

        static final Object ARG0 = new Object();
        static final Object ARG1 = new Object();

        abstract Object execute(Object arg);

        @Specialization(guards = "arg == ARG0")
        Object s0(Object arg, @Exclusive @Cached("arg") Object cachedArg) {
            return cachedArg;
        }

        @Specialization(guards = "arg == ARG1")
        Object s1(Object arg, @Exclusive @Cached("arg") Object cachedArg) {
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

    abstract static class UnboundCachedNodeNode extends Node {

        abstract Object execute(Object arg);

        @Specialization(guards = "arg == 42")
        Object s0(int arg, @Shared("group") @Cached TestNode node) {
            return arg;
        }

        @Specialization(guards = "arg == 43")
        Object s1(int arg, @Shared("group") @Cached TestNode node) {
            return arg;
        }
    }

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

    abstract static class ErrorUnboundCachedNode2 extends Node {

        abstract Object execute(Object arg);

        @Specialization(guards = "arg == 42")
        Object s0(int arg,
                        @Cached TestNode node) {
            return arg;
        }

        @Specialization(guards = "arg == 43")
        Object s1(int arg,
                        @Cached TestNode node) {
            return arg;
        }
    }

    abstract static class ErrorUnboundCachedNode3 extends Node {

        abstract Object execute(Object arg);

        @Specialization(guards = "arg == 42")
        Object s0(int arg,
                        @Cached TestNode node) {
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

        @Specialization(guards = "node == arg")
        Object s0(int arg,
                        @ExpectError("Could not share some of the cached parameters in group 'shared': %n" +
                                        "  - s1(..., @Cached(...) int node) : The specialization 's0(int, int)' has multiple instances.%n" +
                                        "Remove the @Shared annotation or resolve the described issues to allow sharing.") //
                        @Shared("shared") @Cached("arg") int node) {
            return arg;
        }

        @Specialization(guards = "arg == node")
        Object s1(int arg,
                        @ExpectError("Could not share some of the cached parameters in group 'shared': %n" +
                                        "  - s0(..., @Cached(...) int node) : The specialization 's1(int, int)' has multiple instances.%n" +
                                        "Remove the @Shared annotation or resolve the described issues to allow sharing.") //
                        @Shared("shared") @Cached("arg") int node) {
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

}
