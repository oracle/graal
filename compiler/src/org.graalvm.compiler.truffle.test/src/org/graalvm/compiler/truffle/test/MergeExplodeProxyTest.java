/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.nodes.ProxyNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.util.GraphOrder;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Collection of tests that penetrate the partial evaluation logic to produce {@linkplain ProxyNode}
 * nodes.
 */
public class MergeExplodeProxyTest extends PartialEvaluationTest {
    public static class Bytecode {
        public static final byte CONST = 0;
        public static final byte RETURN = 1;
        public static final byte ADD = 2;
        public static final byte IFZERO = 3;
        public static final byte POP = 4;
        public static final byte JMP = 5;
        public static final byte DUP = 6;
    }

    private void partialEval(OptimizedCallTarget compilable) {
        StructuredGraph g = partialEval(compilable, new Object[0]);
        Assert.assertTrue(GraphOrder.assertSchedulableGraph(g));
    }

    public static class LoopControlVariableProxy extends RootNode {
        private final String name;
        @CompilationFinal(dimensions = 1) private final byte[] bytecodes;
        @CompilationFinal(dimensions = 1) private final FrameSlot[] locals;
        @CompilationFinal(dimensions = 1) private final FrameSlot[] stack;

        public LoopControlVariableProxy(String name, byte[] bytecodes, int maxLocals, int maxStack) {
            super(null);
            this.name = name;
            this.bytecodes = bytecodes;
            locals = new FrameSlot[maxLocals];
            stack = new FrameSlot[maxStack];
            for (int i = 0; i < maxLocals; ++i) {
                locals[i] = this.getFrameDescriptor().addFrameSlot("local" + i);
                this.getFrameDescriptor().setFrameSlotKind(locals[i], FrameSlotKind.Int);
            }
            for (int i = 0; i < maxStack; ++i) {
                stack[i] = this.getFrameDescriptor().addFrameSlot("stack" + i);
                this.getFrameDescriptor().setFrameSlotKind(stack[i], FrameSlotKind.Int);
            }
        }

        protected void setInt(VirtualFrame frame, int stackIndex, int value) {
            frame.setInt(stack[stackIndex], value);
        }

        protected void setBoolean(VirtualFrame frame, boolean value) {
            frame.setBoolean(locals[0], value);
        }

        protected boolean getBoolean(VirtualFrame frame) {
            try {
                return frame.getBoolean(locals[0]);
            } catch (FrameSlotTypeException e) {
                return false;
            }
        }

        protected int getInt(VirtualFrame frame, int stackIndex) {
            try {
                return frame.getInt(stack[stackIndex]);
            } catch (FrameSlotTypeException e) {
                throw new IllegalStateException("Error accessing stack slot " + stackIndex);
            }
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
        public Object execute(VirtualFrame frame) {
            int topOfStack = -1;
            int bci = 0;
            boolean result = false;
            boolean running = true;
            outer: while (running) {
                CompilerAsserts.partialEvaluationConstant(bci);
                switch (bytecodes[bci]) {
                    case Bytecode.CONST: {
                        byte value = bytecodes[bci + 1];
                        topOfStack++;
                        setInt(frame, topOfStack, value);
                        bci = bci + 2;
                        continue;
                    }
                    case Bytecode.RETURN: {
                        setBoolean(frame, result);
                        if (result) {
                            // bci = Integer.MAX_VALUE;
                            running = false;
                            continue outer;
                        }
                        break;
                    }
                    case Bytecode.ADD: {
                        int left = getInt(frame, topOfStack);
                        int right = getInt(frame, topOfStack - 1);
                        topOfStack--;
                        setInt(frame, topOfStack, left + right);
                        bci = bci + 1;
                        continue;
                    }
                    case Bytecode.IFZERO: {
                        int value = getInt(frame, topOfStack);
                        byte trueBci = bytecodes[bci + 1];
                        topOfStack--;
                        if (value == 0) {
                            bci = trueBci;
                            result = value == 0;
                        } else {
                            bci = bci + 2;
                        }
                        continue;
                    }
                    case Bytecode.POP: {
                        getInt(frame, topOfStack);
                        topOfStack--;
                        bci++;
                        continue;
                    }
                    case Bytecode.JMP: {
                        byte newBci = bytecodes[bci + 1];
                        bci = newBci;
                        continue;
                    }
                    case Bytecode.DUP: {
                        int dupValue = getInt(frame, topOfStack);
                        topOfStack++;
                        setInt(frame, topOfStack, dupValue);
                        bci++;
                        continue;
                    }
                }
            }
            return getBoolean(frame);
        }
    }

    @Test
    public void testLoopControlVariableProxy() {
        byte[] bytecodes = new byte[]{
                        /* 0: */Bytecode.CONST,
                        /* 1: */42,
                        /* 2: */Bytecode.CONST,
                        /* 3: */-12,

                        // loop
                        /* 4: */Bytecode.CONST,
                        /* 5: */1,
                        /* 6: */Bytecode.ADD,
                        /* 7: */Bytecode.DUP,
                        /* 8: */Bytecode.IFZERO,
                        /* 9: */12,
                        // backedge
                        /* 10: */Bytecode.JMP,
                        /* 11: */4,

                        // loop exit
                        /* 12: */Bytecode.POP,
                        /* 13: */Bytecode.RETURN};

        CallTarget callee = Truffle.getRuntime().createCallTarget(new LoopControlVariableProxy("simpleLoopProgram", bytecodes, 1, 3));
        callee.call();
        callee.call();
        callee.call();
        callee.call();

        partialEval((OptimizedCallTarget) callee);
    }
}
