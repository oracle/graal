/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.dsl.test.ExpressionOrderTestFactory.ExpressionOrderTest1NodeGen;
import com.oracle.truffle.api.dsl.test.examples.ExampleTypes;
import com.oracle.truffle.api.nodes.Node;

public class ExpressionOrderTest {

    @SuppressWarnings("unused")
    @TypeSystemReference(ExampleTypes.class)
    public abstract static class ExpressionOrderTest1 extends Node {

        abstract boolean execute(Object value);

        @Specialization(guards = {"guard1(value)", "cacheGuard1(cache1)", "guard2(value)", "cacheGuard2(cache2)"}, //
                        assumptions = {"assumptionInitializer1(cache3)", "assumptionInitializer2()"})
        boolean s0(boolean value, @Cached("cacheInitializer1(value)") int cache1,
                        @Cached("cacheInitializer2(value)") int cache2,
                        @Cached("cacheInitializer3(value)") int cache3,
                        @Cached("cacheInitializer4(value)") int cache4) {
            Assert.assertEquals(1, cache1);
            Assert.assertEquals(2, cache2);
            Assert.assertEquals(3, cache3);
            Assert.assertEquals(4, cache4);
            return value;
        }

        private boolean[] visitedFlags = new boolean[10];

        private void assertOrder(int orderIndex) {
            for (int i = 0; i < orderIndex; i++) {
                Assert.assertTrue(String.valueOf(i), visitedFlags[i]);
            }
            for (int i = orderIndex; i < visitedFlags.length; i++) {
                Assert.assertFalse(String.valueOf(i), visitedFlags[i]);
            }
            visitedFlags[orderIndex] = true;
        }

        protected boolean guard1(boolean value) {
            assertOrder(0);
            return true;
        }

        protected int cacheInitializer1(boolean value) {
            assertOrder(1);
            return 1;
        }

        protected boolean cacheGuard1(int value) {
            assertOrder(2);
            Assert.assertEquals(1, value);
            return true;
        }

        protected boolean guard2(boolean value) {
            assertOrder(3);
            return true;
        }

        protected int cacheInitializer2(boolean value) {
            assertOrder(4);
            return 2;
        }

        protected boolean cacheGuard2(int value) {
            assertOrder(5);
            Assert.assertEquals(2, value);
            return true;
        }

        protected int cacheInitializer3(boolean value) {
            assertOrder(6);
            return 3;
        }

        protected Assumption assumptionInitializer1(int boo) {
            assertOrder(7);
            Assert.assertEquals(3, boo);
            return Truffle.getRuntime().createAssumption();
        }

        protected Assumption assumptionInitializer2() {
            assertOrder(8);
            return Truffle.getRuntime().createAssumption();
        }

        protected int cacheInitializer4(boolean boo) {
            assertOrder(9);
            return 4;
        }
    }

    @Test
    public void testExpressionOrder() {
        ExpressionOrderTest1 test = ExpressionOrderTest1NodeGen.create();
        Assert.assertTrue(test.execute(true));
    }

}
