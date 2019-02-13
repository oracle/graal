/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
