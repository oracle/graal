/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import org.graalvm.compiler.truffle.DefaultTruffleCompiler;
import org.graalvm.compiler.truffle.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.OptimizedCallTarget;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

public class TransferToInterpreterTest {

    private final class TestRootNode extends RootNode {

        private TestRootNode() {
            super(TruffleLanguage.class, null, null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            int x = (int) frame.getArguments()[0];
            if (x == 0) {
                CompilerDirectives.transferToInterpreter();
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            return null;
        }
    }

    @Test
    public void test() {
        RootNode rootNode = new TestRootNode();
        OptimizedCallTarget target = (OptimizedCallTarget) GraalTruffleRuntime.getRuntime().createCallTarget(rootNode);
        target.call(0);
        Assert.assertFalse(target.isValid());
        DefaultTruffleCompiler.create(GraalTruffleRuntime.getRuntime()).compileMethod(target, GraalTruffleRuntime.getRuntime());
        Assert.assertTrue(target.isValid());
        target.call(0);
        Assert.assertTrue(target.isValid());
        target.call(1);
        Assert.assertFalse(target.isValid());
    }
}
