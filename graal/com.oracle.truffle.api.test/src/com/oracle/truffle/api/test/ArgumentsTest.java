/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test;

import org.junit.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;

/**
 * <h3>Passing Arguments</h3>
 * 
 * <p>
 * A guest language can pass its own custom arguments when invoking a Truffle method by creating a
 * subclass of {@link Arguments}. When invoking a call target with
 * {@link CallTarget#call(Arguments)}, the arguments can be passed. A Truffle node can access the
 * arguments passed into the Truffle method by using {@link VirtualFrame#getArguments}.
 * </p>
 * 
 * <p>
 * The arguments class should only contain fields that are declared as final. This allows the
 * Truffle runtime to improve optimizations around guest language method calls. Also, the arguments
 * object must never be stored into a field. It should be created immediately before invoking
 * {@link CallTarget#call(Arguments)} and no longer be accessed afterwards.
 * </p>
 * 
 * <p>
 * The next part of the Truffle API introduction is at {@link com.oracle.truffle.api.test.FrameTest}
 * .
 * </p>
 */
public class ArgumentsTest {

    @Test
    public void test() {
        TruffleRuntime runtime = Truffle.getRuntime();
        TestRootNode rootNode = new TestRootNode(new TestArgumentNode[]{new TestArgumentNode(0), new TestArgumentNode(1)});
        CallTarget target = runtime.createCallTarget(rootNode);
        Object result = target.call(new TestArguments(20, 22));
        Assert.assertEquals(42, result);
    }

    private static class TestArguments extends Arguments {

        final int[] values;

        TestArguments(int... values) {
            this.values = values;
        }
    }

    private static class TestRootNode extends RootNode {

        @Children private final TestArgumentNode[] children;

        TestRootNode(TestArgumentNode[] children) {
            super(null);
            this.children = children;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            int sum = 0;
            for (int i = 0; i < children.length; ++i) {
                sum += children[i].execute(frame);
            }
            return sum;
        }
    }

    private static class TestArgumentNode extends Node {

        private final int index;

        TestArgumentNode(int index) {
            super(null);
            this.index = index;
        }

        int execute(VirtualFrame frame) {
            return frame.getArguments(TestArguments.class).values[index];
        }
    }
}
