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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.SpecializationGroupingTestFactory.TestElseConnectionBug1Factory;
import com.oracle.truffle.api.dsl.test.SpecializationGroupingTestFactory.TestElseConnectionBug2Factory;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.SlowPathException;

/**
 * Tests execution counts of guards. While we do not make guarantees for guard invocation except for
 * their execution order our implementation reduces the calls to guards as much as possible for the
 * generic case.
 */
public class SpecializationGroupingTest {

    @Test
    public void testElseConnectionBug1() {
        CallTarget target = TestHelper.createCallTarget(TestElseConnectionBug1Factory.create(new GenericInt()));
        Assert.assertEquals(42, target.call());
    }

    @SuppressWarnings("unused")
    @NodeChild(value = "genericChild", type = GenericInt.class)
    public abstract static class TestElseConnectionBug1 extends ValueNode {

        @Specialization(rewriteOn = {SlowPathException.class}, guards = "isInitialized(value)")
        public int do1(int value) throws SlowPathException {
            throw new SlowPathException();
        }

        @Specialization(replaces = "do1", guards = "isInitialized(value)")
        public int do2(int value) {
            return value == 42 ? value : 0;
        }

        @Specialization(guards = "!isInitialized(value)")
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

        @Specialization(guards = "guard0(value)")
        public int do1(int value) {
            throw new AssertionError();
        }

        @Specialization(guards = "guard1(value)")
        public int do2(int value) {
            throw new AssertionError();
        }

        @Specialization(guards = "!guard0(value)")
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

}
