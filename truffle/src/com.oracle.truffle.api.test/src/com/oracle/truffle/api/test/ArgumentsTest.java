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
package com.oracle.truffle.api.test;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * <h3>Passing Arguments</h3>
 *
 * <p>
 * When invoking a call target with {@link CallTarget#call(Object[])}, arguments can be passed. A
 * Truffle node can access the arguments passed into the Truffle method by using
 * {@link VirtualFrame#getArguments}.
 * </p>
 *
 * <p>
 * The arguments class should only contain fields that are declared as final. This allows the
 * Truffle runtime to improve optimizations around guest language method calls. Also, the arguments
 * object array must never be stored into a field. It should be created immediately before invoking
 * {@link CallTarget#call(Object[])} and no longer be accessed afterwards.
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
        Object result = target.call(new Object[]{20, 22});
        Assert.assertEquals(42, result);
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
            this.index = index;
        }

        int execute(VirtualFrame frame) {
            return (Integer) frame.getArguments()[index];
        }
    }
}
