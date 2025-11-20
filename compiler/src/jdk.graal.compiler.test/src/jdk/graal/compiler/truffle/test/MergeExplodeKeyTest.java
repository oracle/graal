/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Test;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.RootNode;

import jdk.vm.ci.code.BailoutException;

/**
 * Tests the `CompilerDirectives.mergeExplodeKey` system for explicit merge explode keys.
 */
@SuppressWarnings("deprecation")
public class MergeExplodeKeyTest extends PartialEvaluationTest {

    public static class Bytecode {
        public static final byte CONST = 0;
        public static final byte ARGUMENT = 1;
        public static final byte ADD = 2;
        public static final byte SUB = 3;
        public static final byte DUP = 4;
        public static final byte POP = 5;
        public static final byte JMP = 6;
        public static final byte IFZERO = 7;
        public static final byte RETURN = 8;
    }

    public static class Program extends RootNode {
        protected final String name;
        @CompilationFinal(dimensions = 1) protected final byte[] bytecodes;
        protected final int stackOffset;
        protected final boolean markTopAsKey;

        static Program create(String name, byte[] bytecodes, int maxStack, boolean markTopAsKey) {
            var builder = FrameDescriptor.newBuilder();
            int stackOffset = builder.addSlots(maxStack, FrameSlotKind.Int);
            return new Program(name, bytecodes, builder.build(), stackOffset, markTopAsKey);
        }

        Program(String name, byte[] bytecodes, FrameDescriptor descriptor, int stackOffset, boolean markTopAsKey) {
            super(null, descriptor);
            this.name = name;
            this.bytecodes = bytecodes;
            this.stackOffset = stackOffset;
            this.markTopAsKey = markTopAsKey;
        }

        protected void setInt(VirtualFrame frame, int stackIndex, int value) {
            frame.setInt(stackOffset + stackIndex, value);
        }

        protected int getInt(VirtualFrame frame, int stackIndex) {
            return frame.getInt(stackOffset + stackIndex);
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
        public Object execute(VirtualFrame frame) {
            int top = -1;
            int bci = 0;
            bci = CompilerDirectives.mergeExplodeKey(bci);
            if (markTopAsKey) {
                /* Testing error on multiple key variables. */
                top = CompilerDirectives.mergeExplodeKey(top);
            }
            while (true) {
                CompilerAsserts.partialEvaluationConstant(bci);
                switch (bytecodes[bci]) {
                    case Bytecode.CONST: {
                        byte value = bytecodes[bci + 1];
                        top++;
                        setInt(frame, top, value);
                        bci = bci + 2;
                        continue;
                    }
                    case Bytecode.ARGUMENT: {
                        int value = (int) frame.getArguments()[bytecodes[bci + 1]];
                        top++;
                        setInt(frame, top, value);
                        bci = bci + 2;
                        continue;
                    }

                    case Bytecode.ADD: {
                        int left = getInt(frame, top);
                        int right = getInt(frame, top - 1);
                        top--;
                        setInt(frame, top, left + right);
                        bci = bci + 1;
                        continue;
                    }
                    case Bytecode.SUB: {
                        int left = getInt(frame, top);
                        int right = getInt(frame, top - 1);
                        top--;
                        setInt(frame, top, left - right);
                        bci = bci + 1;
                        continue;
                    }
                    case Bytecode.DUP: {
                        int dupValue = getInt(frame, top);
                        top++;
                        setInt(frame, top, dupValue);
                        bci++;
                        continue;
                    }
                    case Bytecode.POP: {
                        top--;
                        bci++;
                        continue;
                    }

                    case Bytecode.JMP: {
                        byte newBci = bytecodes[bci + 1];
                        bci = newBci;
                        continue;
                    }
                    case Bytecode.IFZERO: {
                        int value = getInt(frame, top);
                        top--;
                        if (value == 0) {
                            bci = bytecodes[bci + 1];
                            continue;
                        } else {
                            bci = bci + 2;
                            continue;
                        }
                    }
                    case Bytecode.RETURN: {
                        int value = getInt(frame, top);
                        return value;
                    }

                    default: {
                        throw new IllegalStateException();
                    }
                }
            }
        }
    }

    @Test
    public void constReturnProgram() {
        byte[] bytecodes = new byte[]{
                        /* 0: */Bytecode.CONST,
                        /* 1: */42,
                        /* 2: */Bytecode.RETURN};
        partialEval(Program.create("constReturnProgram", bytecodes, 2, false));
    }

    @Test
    public void constAddProgram() {
        byte[] bytecodes = new byte[]{
                        /* 0: */Bytecode.CONST,
                        /* 1: */40,
                        /* 2: */Bytecode.CONST,
                        /* 3: */2,
                        /* 4: */Bytecode.ADD,
                        /* 5: */Bytecode.RETURN};
        partialEval(Program.create("constAddProgram", bytecodes, 2, false));
    }

    @Test(expected = BailoutException.class)
    public void multipleKeyVariables() {
        byte[] bytecodes = new byte[]{
                        /* 0: */Bytecode.CONST,
                        /* 1: */42,
                        /* 2: */Bytecode.RETURN};
        partialEval(Program.create("multipleKeyVariables", bytecodes, 2, true));
    }

    @Test(expected = BailoutException.class)
    public void variableStackSize() {
        byte[] bytecodes = new byte[]{
                        /* 0: */Bytecode.ARGUMENT,
                        /* 1: */0,
                        /* 2: */Bytecode.IFZERO,
                        /* 3: */6,
                        /* 4: */Bytecode.CONST,
                        /* 5: */40,
                        /* 6: */Bytecode.CONST,
                        /* 7: */42,
                        /* 8: */Bytecode.RETURN};
        partialEval(Program.create("variableStackSize", bytecodes, 3, false), 0);
    }
}
