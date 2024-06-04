/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.runtime.OptimizedCallTarget;

public class CallPartialEvaluationTest extends TestWithSynchronousCompiling {

    static class ConstantTargetRootNode extends RootNode {

        // deliberately
        final CallTarget target;

        protected ConstantTargetRootNode(RootNode target) {
            super(null);
            this.target = target.getCallTarget();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object result = target.call(this);
            if (CompilerDirectives.inCompiledCode() && !CompilerDirectives.isCompilationConstant(result)) {
                throw CompilerDirectives.shouldNotReachHere();
            }
            return result;
        }

    }

    static class NonConstantTargetRootNode extends RootNode {

        // deliberately not a constant
        CallTarget target;

        protected NonConstantTargetRootNode(RootNode target) {
            super(null);
            this.target = target.getCallTarget();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object result = target.call(this);
            if (CompilerDirectives.inCompiledCode() && CompilerDirectives.isCompilationConstant(result)) {
                throw CompilerDirectives.shouldNotReachHere();
            }
            return result;
        }

    }

    @Test
    public void testInliningConstant() {
        var root0 = new ConstantTargetRootNode(RootNode.createConstantNode(42));
        var root1 = new ConstantTargetRootNode(root0);
        var root2 = new ConstantTargetRootNode(root1);

        OptimizedCallTarget target = (OptimizedCallTarget) root2.getCallTarget();
        target.call();
        target.compile(true);
        target.call();
        assertTrue(target.isValid());
    }

    static class ExpectedNonConstantRootNode extends RootNode {

        protected ExpectedNonConstantRootNode() {
            super(null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return 42;
        }

    }

    @Test
    public void testInliningNonConstant() {
        var root = new NonConstantTargetRootNode(RootNode.createConstantNode(42));
        OptimizedCallTarget target = (OptimizedCallTarget) root.getCallTarget();
        target.call();
        target.compile(true);
        target.call();
        assertTrue(target.isValid());
    }

    static class LateFoldTargetRootNode extends RootNode {

        final CallTarget target;

        protected LateFoldTargetRootNode(RootNode target) {
            super(null, createFrameDescriptor());
            this.target = target.getCallTarget();
        }

        static FrameDescriptor createFrameDescriptor() {
            var b = FrameDescriptor.newBuilder();
            b.addSlot(FrameSlotKind.Object, "test", null);
            return b.build();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            frame.setObject(0, target);
            Object result = ((CallTarget) frame.getObject(0)).call(this);
            if (CompilerDirectives.inCompiledCode() && CompilerDirectives.isCompilationConstant(result)) {
                /*
                 * Unfortunately we do not yet support inlining with indirect constants. The inliner
                 * needs to be adapted for this to work. If it ever starts to work then this test
                 * will fail. Feel free to switch the condition for compilation constant.
                 */
                throw CompilerDirectives.shouldNotReachHere();
            }

            return result;
        }

    }

    @Test
    public void testLateConstantFold() {
        var root0 = new LateFoldTargetRootNode(RootNode.createConstantNode(42));
        var root1 = new LateFoldTargetRootNode(root0);
        var root2 = new LateFoldTargetRootNode(root1);
        OptimizedCallTarget target = (OptimizedCallTarget) root2.getCallTarget();
        target.call();
        target.compile(true);
        target.call();
        assertTrue(target.isValid());
    }
}
