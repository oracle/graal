/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.compiler.TruffleCompilationTask;
import com.oracle.truffle.compiler.TruffleCompiler;
import com.oracle.truffle.runtime.OptimizedTruffleRuntime;
import com.oracle.truffle.runtime.OptimizedCallTarget;

public class TransferToInterpreterTest extends TestWithPolyglotOptions {

    @Before
    public void setup() {
        setupContext("engine.CompileImmediately", Boolean.FALSE.toString());
    }

    @Test
    public void test() {
        RootNode rootNode = new TestRootNode();
        OptimizedTruffleRuntime runtime = OptimizedTruffleRuntime.getRuntime();
        OptimizedCallTarget target = (OptimizedCallTarget) rootNode.getCallTarget();
        target.call(0);
        Assert.assertFalse(target.isValid());
        final OptimizedCallTarget compilable = target;
        TruffleCompiler compiler = runtime.getTruffleCompiler(compilable);
        TestTruffleCompilationTask task = new TestTruffleCompilationTask();
        compiler.doCompile(task, compilable, null);
        Assert.assertTrue(target.isValid());
        target.call(0);
        Assert.assertTrue(target.isValid());
        target.call(1);
        Assert.assertFalse(target.isValid());
    }

    private static final class TestTruffleCompilationTask implements TruffleCompilationTask {

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isLastTier() {
            return true;
        }

        @Override
        public boolean hasNextTier() {
            return false;
        }
    }

    private final class TestRootNode extends RootNode {

        private TestRootNode() {
            super(null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            int x = (int) frame.getArguments()[0];
            if (x == 0) {
                CompilerDirectives.transferToInterpreter();
            }
            if (x == 1) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            return null;
        }
    }
}
