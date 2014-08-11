/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.dsl.test.SpecializationGroupingTestFactory.TestElseConnectionBug1Factory;
import com.oracle.truffle.api.dsl.test.SpecializationGroupingTestFactory.TestElseConnectionBug2Factory;
import com.oracle.truffle.api.dsl.test.SpecializationGroupingTestFactory.TestGroupingFactory;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.SimpleTypes;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.TestRootNode;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;

/**
 * Tests execution counts of guards. While we do not make guarantees for guard invocation except for
 * their execution order our implementation reduces the calls to guards as much as possible for the
 * generic case.
 */
public class SpecializationGroupingTest {

    @Test
    public void testGrouping() {
        MockAssumption a1 = new MockAssumption(true);
        MockAssumption a2 = new MockAssumption(false);
        MockAssumption a3 = new MockAssumption(true);

        TestRootNode<TestGrouping> root = TestHelper.createRoot(TestGroupingFactory.getInstance(), a1, a2, a3);

        SimpleTypes.intCast = 0;
        SimpleTypes.intCheck = 0;
        TestGrouping.true1 = 0;
        TestGrouping.false1 = 0;
        TestGrouping.true2 = 0;
        TestGrouping.false2 = 0;
        TestGrouping.true3 = 0;

        Assert.assertEquals(42, TestHelper.executeWith(root, 21, 21));
        Assert.assertEquals(4, TestGrouping.true1);
        Assert.assertEquals(0, TestGrouping.false1);
        Assert.assertEquals(4, TestGrouping.true2);
        Assert.assertEquals(5, TestGrouping.false2);
        Assert.assertEquals(5, TestGrouping.true3);
        Assert.assertEquals(8, SimpleTypes.intCheck);
        Assert.assertEquals(8, SimpleTypes.intCast);
        Assert.assertEquals(4, a1.checked);
        Assert.assertEquals(0, a2.checked);
        Assert.assertEquals(4, a3.checked);

        Assert.assertEquals(42, TestHelper.executeWith(root, 21, 21));
        Assert.assertEquals(5, TestGrouping.true1);
        Assert.assertEquals(0, TestGrouping.false1);
        Assert.assertEquals(5, TestGrouping.true2);
        Assert.assertEquals(6, TestGrouping.false2);
        Assert.assertEquals(6, TestGrouping.true3);

        Assert.assertEquals(5, a1.checked);
        Assert.assertEquals(0, a2.checked);
        Assert.assertEquals(5, a3.checked);
        Assert.assertEquals(8, SimpleTypes.intCheck);
        Assert.assertEquals(8, SimpleTypes.intCast);

    }

    @SuppressWarnings("unused")
    @NodeChildren({@NodeChild, @NodeChild})
    @NodeAssumptions({"a1", "a2", "a3"})
    public abstract static class TestGrouping extends ValueNode {

        private static int true1;
        private static int false1;
        private static int true2;
        private static int false2;
        private static int true3;

        protected boolean true1(int value) {
            true1++;
            return true;
        }

        protected boolean false1(int value, int value2) {
            false1++;
            return false;
        }

        protected boolean true2(int value) {
            true2++;
            return true;
        }

        protected boolean false2(int value) {
            false2++;
            return false;
        }

        protected boolean true3(int value) {
            true3++;
            return true;
        }

        @Specialization
        public int fail(int value1, String value2) {
            throw new AssertionError();
        }

        @Specialization(guards = {"true1", "true2", "!false2", "true3"}, assumptions = {"a1", "a3"}, rewriteOn = RuntimeException.class)
        public int throwRewrite(int value1, int value2) {
            throw new RuntimeException();
        }

        @Specialization(guards = {"true1", "true2", "!false2", "true3"}, contains = "throwRewrite", assumptions = {"a1", "a3"})
        public int success(int value1, int value2) {
            return value1 + value2;
        }

        @Specialization(guards = {"true1", "true2", "!false2", "!true3"}, assumptions = {"a1", "a3"})
        public int fail5(int value1, int value2) {
            throw new AssertionError();
        }

        @Specialization(guards = {"true1", "true2", "false2"}, assumptions = {"a1", "a3"})
        public int fail4(int value1, int value2) {
            throw new AssertionError();
        }

        @Specialization(guards = {"true1", "true2"}, assumptions = {"a1", "a3"})
        public int fail2break(int value1, int value2) {
            throw new AssertionError();
        }

        @Specialization(guards = {"true1", "false1"})
        public int fail1(int value1, int value2) {
            throw new AssertionError();
        }

    }

    @Test
    public void testElseConnectionBug1() {
        CallTarget target = TestHelper.createCallTarget(TestElseConnectionBug1Factory.create(new GenericInt()));
        Assert.assertEquals(42, target.call());
    }

    @SuppressWarnings("unused")
    @NodeChild(value = "genericChild", type = GenericInt.class)
    public abstract static class TestElseConnectionBug1 extends ValueNode {

        @Specialization(rewriteOn = {SlowPathException.class}, guards = "isInitialized")
        public int do1(int value) throws SlowPathException {
            throw new SlowPathException();
        }

        @Specialization(contains = "do1", guards = "isInitialized")
        public int do2(int value) {
            return value == 42 ? value : 0;
        }

        @Specialization(guards = "!isInitialized")
        public Object do3(int value) {
            throw new AssertionError();
        }

        boolean isInitialized(int value) {
            return true;
        }
    }

    public static final class GenericInt extends ValueNode {

        @Override
        public Object execute(VirtualFrame frame) {
            return executeInt(frame);
        }

        @Override
        public int executeInt(VirtualFrame frame) {
            return 42;
        }

    }

    @Test
    public void testElseConnectionBug2() {
        TestHelper.assertRuns(TestElseConnectionBug2Factory.getInstance(), new Object[]{42}, new Object[]{42});
    }

    @SuppressWarnings("unused")
    @NodeChild
    public abstract static class TestElseConnectionBug2 extends ValueNode {

        @Specialization(guards = "guard0")
        public int do1(int value) {
            throw new AssertionError();
        }

        @Specialization(guards = "guard1")
        public int do2(int value) {
            throw new AssertionError();
        }

        @Specialization(guards = "!guard0")
        public int do3(int value) {
            return value;
        }

        boolean guard0(int value) {
            return false;
        }

        boolean guard1(int value) {
            return false;
        }
    }

    private static class MockAssumption implements Assumption {

        int checked;

        private final boolean valid;

        public MockAssumption(boolean valid) {
            this.valid = valid;
        }

        public void check() throws InvalidAssumptionException {
            checked++;
            if (!valid) {
                throw new InvalidAssumptionException();
            }
        }

        public boolean isValid() {
            checked++;
            return valid;
        }

        public void invalidate() {
            throw new UnsupportedOperationException();
        }

        public String getName() {
            throw new UnsupportedOperationException();
        }

    }

}
