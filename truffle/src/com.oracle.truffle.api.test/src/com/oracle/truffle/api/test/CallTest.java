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
import com.oracle.truffle.api.nodes.RootNode;

/**
 * <h3>Calling Another Tree</h3>
 *
 * <p>
 * A guest language implementation can create multiple call targets using the
 * {@link TruffleRuntime#createCallTarget(RootNode)} method. Those call targets can be passed around
 * as normal Java objects and used for calling guest language methods.
 * </p>
 *
 * <p>
 * The next part of the Truffle API introduction is at
 * {@link com.oracle.truffle.api.test.ArgumentsTest}.
 * </p>
 */
public class CallTest {

    @Test
    public void test() {
        TruffleRuntime runtime = Truffle.getRuntime();
        CallTarget foo = runtime.createCallTarget(new ConstantRootNode(20));
        CallTarget bar = runtime.createCallTarget(new ConstantRootNode(22));
        CallTarget main = runtime.createCallTarget(new DualCallNode(foo, bar));
        Object result = main.call();
        Assert.assertEquals(42, result);
    }

    class DualCallNode extends RootNode {

        private final CallTarget firstTarget;
        private final CallTarget secondTarget;

        DualCallNode(CallTarget firstTarget, CallTarget secondTarget) {
            super(null);
            this.firstTarget = firstTarget;
            this.secondTarget = secondTarget;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return ((Integer) firstTarget.call()) + ((Integer) secondTarget.call());
        }
    }

    class ConstantRootNode extends RootNode {

        private final int value;

        ConstantRootNode(int value) {
            super(null);
            this.value = value;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return value;
        }
    }
}
