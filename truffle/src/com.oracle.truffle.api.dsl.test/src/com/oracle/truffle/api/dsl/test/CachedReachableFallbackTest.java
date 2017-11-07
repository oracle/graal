/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.CachedReachableFallbackTestFactory.Valid1NodeGen;
import com.oracle.truffle.api.nodes.Node;

public class CachedReachableFallbackTest {

    static final String CACHED_GUARD_FALLBACK_ERROR = "A guard cannot be negated for the @Fallback because it binds @Cached parameters. " +
                    "To fix this introduce a strictly more generic specialization declared between " +
                    "this specialization and the fallback. Alternatively the use of @Fallback can be " +
                    "avoided by declaring a @Specialization with manually specified negated guards.";

    @SuppressWarnings("unused")
    static abstract class Valid1Node extends Node {

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

    static class GuardNode extends Node {
        boolean execute(int object) {
            return object == 42;
        }
    }

    @Test
    public void testValid1() {
        Valid1Node node;

        // test s1 first
        node = Valid1NodeGen.create();
        Assert.assertEquals("s1", node.execute(42));
        Assert.assertEquals("fallback", node.execute("42"));
        Assert.assertEquals("s1", node.execute(42));
        Assert.assertEquals("s2", node.execute(41));
        Assert.assertEquals("fallback", node.execute("42"));
        Assert.assertEquals("s1", node.execute(42)); // s2 does not replace s1

        // test fallback first
        node = Valid1NodeGen.create();
        Assert.assertEquals("fallback", node.execute("42"));
        Assert.assertEquals("s1", node.execute(42));
        Assert.assertEquals("s2", node.execute(41));

        // test s2 first
        node = Valid1NodeGen.create();
        Assert.assertEquals("s2", node.execute(41));
        Assert.assertEquals("fallback", node.execute("42"));
        Assert.assertEquals("s2", node.execute(42));
    }

    @SuppressWarnings("unused")
    static abstract class CachedError1Node extends Node {

        abstract Object execute(Object other);

        @ExpectError(CACHED_GUARD_FALLBACK_ERROR)
        @Specialization(guards = {"guardNode.execute(obj)"}, limit = "1")
        protected Object s1(int obj,
                        @Cached("createGuard()") GuardNode guardNode) {
            return "s1";
        }

        @Fallback
        protected Object fallback(Object x) {
            return "fallback";
        }

        static GuardNode createGuard() {
            return new GuardNode();
        }
    }

    @SuppressWarnings("unused")
    static abstract class CachedError2Node extends Node {

        abstract Object execute(Object other);

        @ExpectError(CACHED_GUARD_FALLBACK_ERROR)
        @Specialization(guards = {"guardNode.execute(obj)"})
        protected Object s1(int obj,
                        @Cached("createGuard()") GuardNode guardNode) {
            return "s1";
        }

        @Fallback
        protected Object fallback(Object x) {
            return "fallback";
        }

        static GuardNode createGuard() {
            return new GuardNode();
        }
    }

    @SuppressWarnings("unused")
    static abstract class CachedError3Node extends Node {

        abstract Object execute(Object other);

        // we don't know if fallback is reachable directly from s1 because s2 has a custom guard.
        @ExpectError(CACHED_GUARD_FALLBACK_ERROR)
        @Specialization(guards = {"guardNode.execute(obj)"})
        protected Object s1(int obj,
                        @Cached("createGuard()") GuardNode guardNode) {
            return "s1";
        }

        @Specialization(guards = "obj == 41")
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

}
