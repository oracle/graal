/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
import org.junit.Ignore;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameDescriptor;
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
@SuppressWarnings("deprecation")
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

    abstract static class BytecodeRootNode extends RootNode {

        protected final String name;
        @CompilationFinal(dimensions = 1) protected final byte[] bytecodes;

        protected final int stackOffset;
        protected final int localsOffset;

        BytecodeRootNode(String name, byte[] bytecodes, FrameDescriptor descriptor, int stackOffset, int localsOffset) {
            super(null, descriptor);
            this.name = name;
            this.bytecodes = bytecodes;
            this.stackOffset = stackOffset;
            this.localsOffset = localsOffset;
        }

        protected void setInt(VirtualFrame frame, int stackIndex, int value) {
            frame.setInt(stackOffset + stackIndex, value);
        }

        protected void setBoolean(VirtualFrame frame, boolean value) {
            frame.setBoolean(localsOffset, value);
        }

        protected boolean getBoolean(VirtualFrame frame) {
            try {
                return frame.getBoolean(localsOffset);
            } catch (FrameSlotTypeException e) {
                return false;
            }
        }

        protected int getInt(VirtualFrame frame, int stackIndex) {
            try {
                return frame.getInt(stackOffset + stackIndex);
            } catch (FrameSlotTypeException e) {
                throw new IllegalStateException("Error accessing stack slot " + stackIndex);
            }
        }

        @Override
        public String toString() {
            return name;
        }

    }

    public static class LoopControlVariableProxy extends BytecodeRootNode {

        LoopControlVariableProxy(String name, byte[] bytecodes, FrameDescriptor descriptor, int stackOffset, int localsOffset) {
            super(name, bytecodes, descriptor, stackOffset, localsOffset);
        }

        static LoopControlVariableProxy create(String name, byte[] bytecodes, int maxLocals, int maxStack) {
            var builder = FrameDescriptor.newBuilder();
            int localsOffset = builder.addSlots(maxLocals, FrameSlotKind.Int);
            int stackOffset = builder.addSlots(maxStack, FrameSlotKind.Int);
            return new LoopControlVariableProxy(name, bytecodes, builder.build(), stackOffset, localsOffset);
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

        CallTarget callee = LoopControlVariableProxy.create("simpleLoopProgram", bytecodes, 1, 3).getCallTarget();
        callee.call();
        callee.call();
        callee.call();
        callee.call();

        partialEval((OptimizedCallTarget) callee);
    }

    public static class WrongLoopExitMerge extends BytecodeRootNode {

        WrongLoopExitMerge(String name, byte[] bytecodes, FrameDescriptor descriptor, int stackOffset, int localsOffset) {
            super(name, bytecodes, descriptor, stackOffset, localsOffset);
        }

        static WrongLoopExitMerge create(String name, byte[] bytecodes, int maxLocals, int maxStack) {
            var builder = FrameDescriptor.newBuilder();
            int localsOffset = builder.addSlots(maxLocals, FrameSlotKind.Int);
            int stackOffset = builder.addSlots(maxStack, FrameSlotKind.Int);
            return new WrongLoopExitMerge(name, bytecodes, builder.build(), stackOffset, localsOffset);
        }

        public static int SideEffect;

        @CompilationFinal int iterations = 2;

        @Override
        @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
        public Object execute(VirtualFrame frame) {
            boolean result = false;
            int topOfStack = -1;
            int bci = 0;
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
                        running = false;
                        continue outer;
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
                            if (SideEffect == 42) {
                                GraalDirectives.sideEffect(result ? 12 : 14);
                            } else {
                                GraalDirectives.sideEffect(2);
                            }
                            // uncomment this fixes the code since we are no longer considering the
                            // merge after both branches be part of the loop explosion
                            // GraalDirectives.sideEffect(3);
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
            return result;
        }
    }

    public static class Caller extends RootNode {

        @Child DirectCallNode callee;

        protected Caller(CallTarget ct) {
            super(null);
            callee = DirectCallNode.create(ct);
            callee.forceInlining();
        }

        @Override
        @ExplodeLoop
        public Object execute(VirtualFrame frame) {
            Object o = callee.call(frame.getArguments());
            if (!(o instanceof Boolean)) {
                CompilerDirectives.transferToInterpreter();
            }
            boolean b = (boolean) o;
            return b ? 0 : 10;
        }

    }

    @Test
    public void test01() {
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

        CallTarget callee = WrongLoopExitMerge.create("mergedLoopExitProgram", bytecodes, 1, 3).getCallTarget();
        callee.call();
        callee.call();
        callee.call();
        callee.call();

        CallTarget caller = new Caller(callee).getCallTarget();
        caller.call();
        caller.call();
        caller.call();
        caller.call();

        partialEval((OptimizedCallTarget) caller);
    }

    @Test
    public void test01Caller() {
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

        CallTarget callee = WrongLoopExitMerge.create("mergedLoopExitProgram", bytecodes, 1, 3).getCallTarget();
        callee.call();
        callee.call();
        callee.call();
        callee.call();

        partialEval((OptimizedCallTarget) callee);
    }

    public static class ProxySameValueOnce extends BytecodeRootNode {

        ProxySameValueOnce(String name, byte[] bytecodes, FrameDescriptor descriptor, int stackOffset, int localsOffset) {
            super(name, bytecodes, descriptor, stackOffset, localsOffset);
        }

        static ProxySameValueOnce create(String name, byte[] bytecodes, int maxLocals, int maxStack) {
            var builder = FrameDescriptor.newBuilder();
            int localsOffset = builder.addSlots(maxLocals, FrameSlotKind.Int);
            int stackOffset = builder.addSlots(maxStack, FrameSlotKind.Int);
            return new ProxySameValueOnce(name, bytecodes, builder.build(), stackOffset, localsOffset);
        }

        public static int SideEffect;

        @CompilationFinal int iterations = 3;

        int[] field = new int[]{1, 2, 3, 4, 5};

        public int explodeFully(int it, int goThroughHere) {
            int res;
            int j = 0;
            do {
                res = goThroughHere;
            } while (++j < it);
            return res;
        }

        @Override
        @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
        public Object execute(VirtualFrame frame) {
            int topOfStack = -1;
            int bci = 0;
            boolean running = true;
            while (running) {
                int p = 0;
                b: {
                    CompilerAsserts.partialEvaluationConstant(bci);
                    switch (bytecodes[bci]) {
                        case Bytecode.CONST: {
                            byte value = bytecodes[bci + 1];
                            topOfStack++;
                            setInt(frame, topOfStack, value);
                            bci = bci + 2;
                            if (bytecodes[bci] == Bytecode.RETURN) {
                                running = false;
                                break b;
                            }
                            break b;
                        }
                        case Bytecode.ADD: {
                            int left = getInt(frame, topOfStack);
                            int right = getInt(frame, topOfStack - 1);
                            topOfStack--;
                            setInt(frame, topOfStack, left + right);
                            bci = bci + 1;
                            if (bytecodes[bci] == Bytecode.RETURN) {
                                running = false;
                                break b;
                            }
                            break b;
                        }
                        case Bytecode.IFZERO: {
                            int value = getInt(frame, topOfStack);
                            byte trueBci = bytecodes[bci + 1];
                            topOfStack--;
                            int i = 0;
                            int val = 0;
                            do {
                                val = field[i];
                            } while (++i < iterations);
                            p = val;
                            if (value == 0) {
                                if (bci == 14 && bytecodes[trueBci] == Bytecode.POP) {
                                    topOfStack--; // pop
                                    bci = trueBci + 1;
                                    int value1 = getInt(frame, topOfStack);
                                    topOfStack--;
                                    if (value1 == 0) {
                                        if (SideEffect == 0) {
                                            return val;
                                        }
                                        return p;
                                    } else {
                                        bci = bci + 2;
                                        if (SideEffect == 0) {
                                            for (int j = 0; j < iterations; j++) {
                                                if (iterations < 0) {
                                                    SideEffect = 1;
                                                }
                                            }
                                        }
                                        break b;
                                    }
                                }
                                bci = trueBci;

                                break b;
                            } else {
                                bci = bci + 2;
                                if (bytecodes[bci] == Bytecode.RETURN) {
                                    return p;
                                }
                                break b;
                            }
                        }
                        case Bytecode.POP: {
                            getInt(frame, topOfStack);
                            topOfStack--;
                            bci++;
                            if (bytecodes[bci] == Bytecode.RETURN) {
                                running = false;
                                break b;
                            }
                            break b;
                        }
                        case Bytecode.JMP: {
                            byte newBci = bytecodes[bci + 1];
                            bci = newBci;
                            if (bytecodes[bci] == Bytecode.RETURN) {
                                running = false;
                                continue;
                            }
                            break b;
                        }
                        case Bytecode.DUP: {
                            int dupValue = getInt(frame, topOfStack);
                            topOfStack++;
                            setInt(frame, topOfStack, dupValue);
                            bci++;
                            if (bytecodes[bci] == Bytecode.RETURN) {
                                running = false;
                                break b;
                            }
                            break b;
                        }
                    }

                }
            }
            return 0;
        }

    }

    @Test
    public void testSameValueProxy() {
        byte[] bytecodes = new byte[]{
                        /* 0: */Bytecode.CONST,
                        /* 1: */42,
                        /* 2: */Bytecode.CONST,
                        /* 3: */-2,
                        /* 4: */Bytecode.CONST,
                        /* 5: */1,
                        /* 6: */Bytecode.ADD,
                        /* 7: */Bytecode.DUP,
                        /* 8: */Bytecode.CONST,
                        /* 9: */-2,
                        /* 10: */Bytecode.CONST,
                        /* 11: */1,
                        /* 12: */Bytecode.ADD,
                        /* 13: */Bytecode.DUP,
                        /* 14: */Bytecode.IFZERO,
                        /* 15: */18,
                        /* 16: */Bytecode.JMP,
                        /* 17: */10,
                        /* 18: */Bytecode.POP,
                        /* 19: */Bytecode.IFZERO,
                        /* 20: */23,
                        /* 21: */Bytecode.JMP,
                        /* 22: */4,
                        /* 23: */Bytecode.POP,
                        /* 24: */Bytecode.RETURN};

        CallTarget callee = ProxySameValueOnce.create("proxyAtStateProgram", bytecodes, 0, 6).getCallTarget();
        ProxySameValueOnce.SideEffect = -1;
        callee.call();
        ProxySameValueOnce.SideEffect = 0;
        callee.call();
        ProxySameValueOnce.SideEffect = 1;
        callee.call();
        callee.call();

        partialEval((OptimizedCallTarget) callee);

    }

    public static class NoneLiveNoProxyTest extends BytecodeRootNode {

        NoneLiveNoProxyTest(String name, byte[] bytecodes, FrameDescriptor descriptor, int stackOffset, int localsOffset) {
            super(name, bytecodes, descriptor, stackOffset, localsOffset);
        }

        static NoneLiveNoProxyTest create(String name, byte[] bytecodes, int maxLocals, int maxStack) {
            var builder = FrameDescriptor.newBuilder();
            int localsOffset = builder.addSlots(maxLocals, FrameSlotKind.Int);
            int stackOffset = builder.addSlots(maxStack, FrameSlotKind.Int);
            return new NoneLiveNoProxyTest(name, bytecodes, builder.build(), stackOffset, localsOffset);
        }

        public static int SideEffect;

        @CompilationFinal int iterations = 3;

        int[] field = new int[]{1, 2, 3, 4, 5};

        public int explodeFully(int it, int goThroughHere) {
            int res;
            int j = 0;
            do {
                res = goThroughHere;
            } while (++j < it);
            return res;
        }

        @Override
        @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
        public Object execute(VirtualFrame frame) {
            int topOfStack = -1;
            int bci = 0;
            boolean running = true;
            while (running) {
                int p = 0;
                b: {
                    CompilerAsserts.partialEvaluationConstant(bci);
                    switch (bytecodes[bci]) {
                        case Bytecode.CONST: {
                            byte value = bytecodes[bci + 1];
                            topOfStack++;
                            setInt(frame, topOfStack, value);
                            bci = bci + 2;
                            if (bytecodes[bci] == Bytecode.RETURN) {
                                running = false;
                                break b;
                            }
                            break b;
                        }
                        case Bytecode.ADD: {
                            int left = getInt(frame, topOfStack);
                            int right = getInt(frame, topOfStack - 1);
                            topOfStack--;
                            setInt(frame, topOfStack, left + right);
                            bci = bci + 1;
                            if (bytecodes[bci] == Bytecode.RETURN) {
                                running = false;
                                break b;
                            }
                            break b;
                        }
                        case Bytecode.IFZERO: {
                            int value = getInt(frame, topOfStack);
                            byte trueBci = bytecodes[bci + 1];
                            topOfStack--;
                            int i = 0;
                            int val = 0;
                            do {
                                val = field[i];
                            } while (++i < iterations);
                            p = val;
                            if (value == 0) {
                                if (bci == 14 && bytecodes[trueBci] == Bytecode.POP) {
                                    topOfStack--; // pop
                                    bci = trueBci + 1;
                                    int value1 = getInt(frame, topOfStack);
                                    topOfStack--;
                                    if (value1 == 0) {
                                        if (SideEffect == 0) {
                                            return val;
                                        }
                                        return p;
                                    } else {
                                        bci = bci + 2;
                                        if (SideEffect == 0) {
                                            for (int j = 0; j < iterations; j++) {
                                                if (iterations < 0) {
                                                    SideEffect = 1;
                                                }
                                            }
                                        } else {
                                            explodeFully(0, 0);
                                            // p not in state of explode fully, thus missing proxy
                                            SideEffect = p;
                                        }
                                        break b;
                                    }
                                }
                                bci = trueBci;
                                break b;
                            } else {
                                bci = bci + 2;
                                if (bytecodes[bci] == Bytecode.RETURN) {
                                    return p;
                                }
                                break b;
                            }
                        }
                        case Bytecode.POP: {
                            getInt(frame, topOfStack);
                            topOfStack--;
                            bci++;
                            if (bytecodes[bci] == Bytecode.RETURN) {
                                running = false;
                                break b;
                            }
                            break b;
                        }
                        case Bytecode.JMP: {
                            byte newBci = bytecodes[bci + 1];
                            bci = newBci;
                            if (bytecodes[bci] == Bytecode.RETURN) {
                                running = false;
                                continue;
                            }
                            break b;
                        }
                        case Bytecode.DUP: {
                            int dupValue = getInt(frame, topOfStack);
                            topOfStack++;
                            setInt(frame, topOfStack, dupValue);
                            bci++;
                            if (bytecodes[bci] == Bytecode.RETURN) {
                                running = false;
                                break b;
                            }
                            break b;
                        }
                    }

                }
            }
            return 0;
        }

    }

    @Ignore("GR-21520: Merge explode partial evaluation cannot proxy nodes that are not alive in the framestate of inner loop begins")
    @Test
    public void testNoneLiveLoopExitProxy() {
        byte[] bytecodes = new byte[]{
                        /* 0: */Bytecode.CONST,
                        /* 1: */42,
                        /* 2: */Bytecode.CONST,
                        /* 3: */-2,
                        /* 4: */Bytecode.CONST,
                        /* 5: */1,
                        /* 6: */Bytecode.ADD,
                        /* 7: */Bytecode.DUP,
                        /* 8: */Bytecode.CONST,
                        /* 9: */-2,
                        /* 10: */Bytecode.CONST,
                        /* 11: */1,
                        /* 12: */Bytecode.ADD,
                        /* 13: */Bytecode.DUP,
                        /* 14: */Bytecode.IFZERO,
                        /* 15: */18,
                        /* 16: */Bytecode.JMP,
                        /* 17: */10,
                        /* 18: */Bytecode.POP,
                        /* 19: */Bytecode.IFZERO,
                        /* 20: */23,
                        /* 21: */Bytecode.JMP,
                        /* 22: */4,
                        /* 23: */Bytecode.POP,
                        /* 24: */Bytecode.RETURN};

        CallTarget callee = NoneLiveNoProxyTest.create("proxyAtStateProgram", bytecodes, 0, 6).getCallTarget();
        ProxySameValueOnce.SideEffect = -1;
        callee.call();
        ProxySameValueOnce.SideEffect = 0;
        callee.call();
        ProxySameValueOnce.SideEffect = 1;
        callee.call();
        callee.call();

        partialEval((OptimizedCallTarget) callee);

    }

}
