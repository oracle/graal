/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test.nodes;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeInfo;

@NodeInfo
public class InliningNullCheckNode2 extends AbstractTestNode {

    static final class A {
        final int[] array = new int[]{1, 2, 3};

        @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
        private int foo() {
            CompilerAsserts.compilationConstant(this);

            int result = 0;
            for (int i = 0; i < array.length; i++) {
                result += array[i];
            }
            return result;
        }
    }

    private final boolean condition;

    public InliningNullCheckNode2() {
        this.condition = false;

        /* Make sure foo and all its callees get loaded. */
        new A().foo();
    }

    @Override
    public int execute(VirtualFrame frame) {
        A a = null;
        if (condition) {
            a = new A();
        }
        try {
            /*
             * A invokespecial where the receiver is guaranteed to be null during partial
             * evaluation.
             */
            return a.foo();
        } catch (NullPointerException ex) {
            return 42;
        }
    }
}
