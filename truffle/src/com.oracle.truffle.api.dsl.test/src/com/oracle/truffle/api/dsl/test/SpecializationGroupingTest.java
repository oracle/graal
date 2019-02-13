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
