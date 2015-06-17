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

        public ConstantRootNode(int value) {
            super(null);
            this.value = value;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return value;
        }
    }
}
