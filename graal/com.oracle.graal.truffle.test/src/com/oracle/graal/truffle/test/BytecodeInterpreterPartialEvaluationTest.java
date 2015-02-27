/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle.test;

import org.junit.*;

import com.oracle.graal.truffle.test.nodes.*;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;

public class BytecodeInterpreterPartialEvaluationTest extends PartialEvaluationTest {

    public static class Bytecode {
        public static final byte CONST = 0;
        public static final byte RETURN = 1;
    }

    public static class Program extends RootNode {
        @CompilationFinal final byte[] bytecodes;
        private final int maxStack;
        private final int maxLocals;
        @CompilationFinal private final FrameSlot[] locals;
        @CompilationFinal private final FrameSlot[] stack;

        public Program(byte[] bytecodes, int maxLocals, int maxStack) {
            this.bytecodes = bytecodes;
            this.maxLocals = maxLocals;
            this.maxStack = maxStack;
            locals = new FrameSlot[maxLocals];
            stack = new FrameSlot[maxStack];
            for (int i = 0; i < maxLocals; ++i) {
                locals[i] = this.getFrameDescriptor().addFrameSlot("local" + i);
                locals[i].setKind(FrameSlotKind.Int);
            }
            for (int i = 0; i < maxStack; ++i) {
                stack[i] = this.getFrameDescriptor().addFrameSlot("stack" + i);
                stack[i].setKind(FrameSlotKind.Int);
            }
        }

        @Override
        @ExplodeLoop
        public Object execute(VirtualFrame frame) {
            int topOfStack = -1;
            int bci = 0;

            while (true) {
                byte bc = bytecodes[bci];
                byte value = 0;
                switch (bc) {
                    case Bytecode.CONST:
                        value = bytecodes[bci + 1];
                        topOfStack = topOfStack + 1;
                        frame.setInt(stack[topOfStack], value);
                        bci = bci + 2;
                        break;
                    case Bytecode.RETURN:
                        return frame.getValue(stack[topOfStack]);
                }
            }
        }
    }

    public static Object constant42() {
        return 42;
    }

    @Test
    public void simpleProgram() {
        FrameDescriptor fd = new FrameDescriptor();
        byte[] bytecodes = new byte[]{Bytecode.CONST, 42, Bytecode.RETURN};
        assertPartialEvalEquals("constant42", new Program(bytecodes, 0, 2));
    }
}
