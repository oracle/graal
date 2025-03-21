/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.truffle.benchmark.bytecode.manual;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.truffle.benchmark.bytecode.BenchmarkLanguage;
import org.graalvm.truffle.benchmark.bytecode.manual.ManualNodedBytecodeInterpretersFactory.AddNodeGen;
import org.graalvm.truffle.benchmark.bytecode.manual.ManualNodedBytecodeInterpretersFactory.LtNodeGen;
import org.graalvm.truffle.benchmark.bytecode.manual.ManualNodedBytecodeInterpretersFactory.ModNodeGen;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterSwitch;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

public class ManualNodedBytecodeInterpreters {

    static final short OP_CONST = 1;
    static final short OP_ST_LOC = 2;
    static final short OP_LD_LOC = 3;
    static final short OP_ADD = 4;
    static final short OP_MOD = 5;
    static final short OP_LESS = 6;
    static final short OP_JUMP = 7;
    static final short OP_JUMP_FALSE = 8;
    static final short OP_RETURN = 9;

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder extends ManualBytecodeInterpreters.Builder {
        Map<Object, Integer> constants = new HashMap<>();
        List<Node> nodes = new ArrayList<>();

        int addNode(Node n) {
            int index = nodes.size();
            nodes.add(n);
            return index;
        }

        @Override
        public void loadConstant(Object constant) {
            Integer index = constants.get(constant);
            if (index == null) {
                index = constants.size();
                constants.put(constant, index);
            }
            writeShort(OP_CONST);
            writeInt(index);
            updateSp(1);
        }

        @Override
        public void emitAdd() {
            writeShort(OP_ADD);
            writeInt(addNode(AddNode.create()));
            updateSp(-1);
        }

        @Override
        public void emitMod() {
            writeShort(OP_MOD);
            writeInt(addNode(ModNode.create()));
            updateSp(-1);
        }

        @Override
        public void emitLessThan() {
            writeShort(OP_LESS);
            writeInt(addNode(LtNode.create()));
            updateSp(-1);
        }

        public Object[] getConstants() {
            Object[] result = new Object[constants.size()];
            for (var entry : constants.entrySet()) {
                result[entry.getValue()] = entry.getKey();
            }
            return result;
        }

        public Node[] getNodes() {
            return nodes.toArray(new Node[0]);
        }
    }

    @SuppressWarnings("truffle-inlining")
    abstract static class AddNode extends Node {
        public abstract int execute(Object lhs, Object rhs);

        @Specialization
        public static int doInt(int lhs, int rhs) {
            return lhs + rhs;
        }

        public static AddNode create() {
            return AddNodeGen.create();
        }
    }

    @SuppressWarnings("truffle-inlining")
    abstract static class ModNode extends Node {
        public abstract int execute(Object lhs, Object rhs);

        @Specialization
        public static int doInt(int lhs, int rhs) {
            return lhs % rhs;
        }

        public static ModNode create() {
            return ModNodeGen.create();
        }
    }

    @SuppressWarnings("truffle-inlining")
    abstract static class LtNode extends Node {
        public abstract boolean execute(Object lhs, Object rhs);

        @Specialization
        public static boolean doInt(int lhs, int rhs) {
            return lhs < rhs;
        }

        public static LtNode create() {
            return LtNodeGen.create();
        }
    }

    public static class ManualNodedBytecodeInterpreter extends UncheckedBytecodeInterpreter {

        @CompilationFinal(dimensions = 1) private final Object[] constants;
        @CompilationFinal(dimensions = 1) private final Node[] nodes;

        protected ManualNodedBytecodeInterpreter(TruffleLanguage<?> language, FrameDescriptor frameDescriptor, byte[] bc, int numLocals, int numConditionalBranches, Object[] constants, Node[] nodes) {
            super(language, frameDescriptor, bc, numLocals, numConditionalBranches);
            this.constants = constants;
            this.nodes = nodes;
        }

        public static ManualNodedBytecodeInterpreter create(BenchmarkLanguage language, Builder builder) {
            return new ManualNodedBytecodeInterpreter(language, builder.getFrameDescriptor(), builder.getBytecode(), builder.numLocals, builder.numConditionalBranches, builder.getConstants(),
                            builder.getNodes());
        }

        @Override
        @BytecodeInterpreterSwitch
        @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
        protected Object executeAt(VirtualFrame frame, int startBci, int startSp) throws UnexpectedResultException {
            byte[] localBc = bc;
            Object[] localConstants = constants;
            int[] localBranchProfiles = branchProfiles;
            Node[] localNodes = nodes;
            int bci = startBci;
            int sp = startSp;

            Counter loopCounter = new Counter();

            frame.getArguments();

            loop: while (true) {
                short opcode = BYTES.getShort(localBc, bci);
                CompilerAsserts.partialEvaluationConstant(opcode);
                switch (opcode) {
                    // ( -- i)
                    case OP_CONST: {
                        FRAMES.setInt(frame, sp, ACCESS.uncheckedCast(ACCESS.readObject(localConstants, BYTES.getShort(localBc, bci + 2)), Integer.class));
                        sp += 1;
                        bci += 6;
                        continue loop;
                    }
                    // (i -- )
                    case OP_ST_LOC: {
                        FRAMES.copy(frame, sp - 1, BYTES.getIntUnaligned(localBc, bci + 2));
                        FRAMES.clear(frame, sp - 1);
                        sp -= 1;
                        bci += 6;
                        continue loop;
                    }
                    // ( -- i)
                    case OP_LD_LOC: {
                        FRAMES.copy(frame, BYTES.getIntUnaligned(localBc, bci + 2), sp);
                        sp += 1;
                        bci += 6;
                        continue loop;
                    }
                    // (i1 i2 -- i3)
                    case OP_ADD: {
                        int lhs = FRAMES.expectInt(frame, sp - 2);
                        int rhs = FRAMES.expectInt(frame, sp - 1);
                        FRAMES.setInt(frame, sp - 2, ACCESS.uncheckedCast(ACCESS.readObject(localNodes, BYTES.getIntUnaligned(localBc, bci + 2)), AddNode.class).execute(lhs, rhs));
                        FRAMES.clear(frame, sp - 1);
                        sp -= 1;
                        bci += 6;
                        continue loop;
                    }
                    // (i1 i2 -- i3)
                    case OP_MOD: {
                        int lhs = FRAMES.expectInt(frame, sp - 2);
                        int rhs = FRAMES.expectInt(frame, sp - 1);
                        FRAMES.setInt(frame, sp - 2, ACCESS.uncheckedCast(ACCESS.readObject(localNodes, BYTES.getIntUnaligned(localBc, bci + 2)), ModNode.class).execute(lhs, rhs));
                        FRAMES.clear(frame, sp - 1);
                        sp -= 1;
                        bci += 6;
                        continue loop;
                    }
                    // (i1 i2 -- b)
                    case OP_LESS: {
                        int lhs = FRAMES.expectInt(frame, sp - 2);
                        int rhs = FRAMES.expectInt(frame, sp - 1);
                        FRAMES.setBoolean(frame, sp - 2, ACCESS.uncheckedCast(ACCESS.readObject(localNodes, BYTES.getIntUnaligned(localBc, bci + 2)), LtNode.class).execute(lhs, rhs));
                        FRAMES.clear(frame, sp - 1);
                        sp -= 1;
                        bci += 6;
                        continue loop;
                    }
                    // ( -- )
                    case OP_JUMP: {
                        int nextBci = BYTES.getIntUnaligned(localBc, bci + 2);
                        CompilerAsserts.partialEvaluationConstant(nextBci);
                        if (nextBci <= bci) {
                            Object result = backwardsJumpCheck(frame, sp, loopCounter, nextBci);
                            if (result != null) {
                                return result;
                            }
                        }
                        bci = nextBci;
                        continue loop;
                    }
                    // (b -- )
                    case OP_JUMP_FALSE: {
                        boolean cond = FRAMES.expectBoolean(frame, sp - 1);
                        int profileIdx = BYTES.getIntUnaligned(localBc, bci + 6);
                        FRAMES.clear(frame, sp - 1);
                        sp -= 1;
                        if (profileBranch(localBranchProfiles, profileIdx, !cond)) {
                            bci = BYTES.getIntUnaligned(localBc, bci + 2);
                            continue loop;
                        } else {
                            bci += 10;
                            continue loop;
                        }
                    }
                    // (i -- )
                    case OP_RETURN: {
                        return FRAMES.expectInt(frame, sp - 1);
                    }
                    default:
                        CompilerDirectives.shouldNotReachHere();
                }
            }
        }
    }

    public static class ManualNodedCheckedBytecodeInterpreter extends CheckedBytecodeInterpreter {

        @CompilationFinal(dimensions = 1) private final Object[] constants;
        @CompilationFinal(dimensions = 1) private final Node[] nodes;

        protected ManualNodedCheckedBytecodeInterpreter(TruffleLanguage<?> language, FrameDescriptor frameDescriptor, byte[] bc, int numLocals, int numConditionalBranches, Object[] constants,
                        Node[] nodes) {
            super(language, frameDescriptor, bc, numLocals, numConditionalBranches);
            this.constants = constants;
            this.nodes = nodes;
        }

        public static ManualNodedCheckedBytecodeInterpreter create(BenchmarkLanguage language, Builder builder) {
            return new ManualNodedCheckedBytecodeInterpreter(language, builder.getFrameDescriptor(), builder.getBytecode(), builder.numLocals, builder.numConditionalBranches, builder.getConstants(),
                            builder.getNodes());
        }

        @Override
        @BytecodeInterpreterSwitch
        @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
        protected Object executeAt(VirtualFrame frame, int startBci, int startSp) throws UnexpectedResultException {
            byte[] localBc = bc;
            Object[] localConstants = constants;
            int[] localBranchProfiles = branchProfiles;
            Node[] localNodes = nodes;
            int bci = startBci;
            int sp = startSp;

            Counter loopCounter = new Counter();

            frame.getArguments();

            loop: while (true) {
                short opcode = BYTES.getShort(localBc, bci);
                CompilerAsserts.partialEvaluationConstant(opcode);
                switch (opcode) {
                    // ( -- i)
                    case OP_CONST: {
                        FRAMES.setInt(frame, sp, ACCESS.uncheckedCast(ACCESS.readObject(localConstants, BYTES.getShort(localBc, bci + 2)), Integer.class));
                        sp += 1;
                        bci += 6;
                        continue loop;
                    }
                    // (i -- )
                    case OP_ST_LOC: {
                        FRAMES.copy(frame, sp - 1, BYTES.getIntUnaligned(localBc, bci + 2));
                        FRAMES.clear(frame, sp - 1);
                        sp -= 1;
                        bci += 6;
                        continue loop;
                    }
                    // ( -- i)
                    case OP_LD_LOC: {
                        FRAMES.copy(frame, BYTES.getIntUnaligned(localBc, bci + 2), sp);
                        sp += 1;
                        bci += 6;
                        continue loop;
                    }
                    // (i1 i2 -- i3)
                    case OP_ADD: {
                        int lhs = FRAMES.expectInt(frame, sp - 2);
                        int rhs = FRAMES.expectInt(frame, sp - 1);
                        FRAMES.setInt(frame, sp - 2, ACCESS.uncheckedCast(ACCESS.readObject(localNodes, BYTES.getIntUnaligned(localBc, bci + 2)), AddNode.class).execute(lhs, rhs));
                        FRAMES.clear(frame, sp - 1);
                        sp -= 1;
                        bci += 6;
                        continue loop;
                    }
                    // (i1 i2 -- i3)
                    case OP_MOD: {
                        int lhs = FRAMES.expectInt(frame, sp - 2);
                        int rhs = FRAMES.expectInt(frame, sp - 1);
                        FRAMES.setInt(frame, sp - 2, ACCESS.uncheckedCast(ACCESS.readObject(localNodes, BYTES.getIntUnaligned(localBc, bci + 2)), ModNode.class).execute(lhs, rhs));
                        FRAMES.clear(frame, sp - 1);
                        sp -= 1;
                        bci += 6;
                        continue loop;
                    }
                    // (i1 i2 -- b)
                    case OP_LESS: {
                        int lhs = FRAMES.expectInt(frame, sp - 2);
                        int rhs = FRAMES.expectInt(frame, sp - 1);
                        FRAMES.setBoolean(frame, sp - 2, ACCESS.uncheckedCast(ACCESS.readObject(localNodes, BYTES.getIntUnaligned(localBc, bci + 2)), LtNode.class).execute(lhs, rhs));
                        FRAMES.clear(frame, sp - 1);
                        sp -= 1;
                        bci += 6;
                        continue loop;
                    }
                    // ( -- )
                    case OP_JUMP: {
                        int nextBci = BYTES.getIntUnaligned(localBc, bci + 2);
                        CompilerAsserts.partialEvaluationConstant(nextBci);
                        if (nextBci <= bci) {
                            Object result = backwardsJumpCheck(frame, sp, loopCounter, nextBci);
                            if (result != null) {
                                return result;
                            }
                        }
                        bci = nextBci;
                        continue loop;
                    }
                    // (b -- )
                    case OP_JUMP_FALSE: {
                        boolean cond = FRAMES.expectBoolean(frame, sp - 1);
                        int profileIdx = BYTES.getIntUnaligned(localBc, bci + 6);
                        FRAMES.clear(frame, sp - 1);
                        sp -= 1;
                        if (profileBranch(localBranchProfiles, profileIdx, !cond)) {
                            bci = BYTES.getIntUnaligned(localBc, bci + 2);
                            continue loop;
                        } else {
                            bci += 10;
                            continue loop;
                        }
                    }
                    // (i -- )
                    case OP_RETURN: {
                        return FRAMES.expectInt(frame, sp - 1);
                    }
                    default:
                        CompilerDirectives.shouldNotReachHere();
                }
            }
        }
    }

    @SuppressWarnings("truffle-inlining")
    public static class ManualNodedBytecodeInterpreterWithoutBE extends UncheckedBytecodeInterpreter {

        @CompilationFinal(dimensions = 1) private final Object[] constants;
        @CompilationFinal(dimensions = 1) private final Node[] nodes;

        protected ManualNodedBytecodeInterpreterWithoutBE(TruffleLanguage<?> language, FrameDescriptor frameDescriptor, byte[] bc, int numLocals, int numConditionalBranches, Object[] constants,
                        Node[] nodes) {
            super(language, frameDescriptor, bc, numLocals, numConditionalBranches);
            this.constants = constants;
            this.nodes = nodes;
        }

        public static ManualNodedBytecodeInterpreterWithoutBE create(BenchmarkLanguage language, Builder builder) {
            return new ManualNodedBytecodeInterpreterWithoutBE(language, builder.getFrameDescriptor(), builder.getBytecode(), builder.numLocals, builder.numConditionalBranches, builder.getConstants(),
                            builder.getNodes());
        }

        @Override
        @BytecodeInterpreterSwitch
        @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
        protected Object executeAt(VirtualFrame frame, int startBci, int startSp) {
            byte[] localBc = bc;
            int[] localBranchProfiles = branchProfiles;
            Object[] localConstants = constants;
            Node[] localNodes = nodes;
            int bci = startBci;
            int sp = startSp;

            Counter loopCounter = new Counter();

            loop: while (true) {
                short opcode = BYTES.getShort(localBc, bci);
                CompilerAsserts.partialEvaluationConstant(opcode);
                switch (opcode) {
                    // ( -- i)
                    case OP_CONST: {
                        FRAMES.setObject(frame, sp, ACCESS.uncheckedCast(ACCESS.readObject(localConstants, BYTES.getShort(localBc, bci + 2)), Integer.class));
                        sp += 1;
                        bci += 6;
                        continue loop;
                    }
                    // (i -- )
                    case OP_ST_LOC: {
                        FRAMES.copy(frame, sp - 1, BYTES.getIntUnaligned(localBc, bci + 2));
                        FRAMES.clear(frame, sp - 1);
                        sp -= 1;
                        bci += 6;
                        continue loop;
                    }
                    // ( -- i)
                    case OP_LD_LOC: {
                        FRAMES.copy(frame, BYTES.getIntUnaligned(localBc, bci + 2), sp);
                        sp += 1;
                        bci += 6;
                        continue loop;
                    }
                    // (i1 i2 -- i3)
                    case OP_ADD: {
                        Object lhs = FRAMES.getObject(frame, sp - 2);
                        Object rhs = FRAMES.getObject(frame, sp - 1);
                        FRAMES.setObject(frame, sp - 2, ACCESS.uncheckedCast(ACCESS.readObject(localNodes, BYTES.getIntUnaligned(localBc, bci + 2)), AddNode.class).execute(lhs, rhs));
                        FRAMES.clear(frame, sp - 1);
                        sp -= 1;
                        bci += 6;
                        continue loop;
                    }
                    // (i1 i2 -- i3)
                    case OP_MOD: {
                        Object lhs = FRAMES.getObject(frame, sp - 2);
                        Object rhs = FRAMES.getObject(frame, sp - 1);
                        FRAMES.setObject(frame, sp - 2, ACCESS.uncheckedCast(ACCESS.readObject(localNodes, BYTES.getIntUnaligned(localBc, bci + 2)), ModNode.class).execute(lhs, rhs));
                        FRAMES.clear(frame, sp - 1);
                        sp -= 1;
                        bci += 6;
                        continue loop;
                    }
                    // (i1 i2 -- b)
                    case OP_LESS: {
                        Object lhs = FRAMES.getObject(frame, sp - 2);
                        Object rhs = FRAMES.getObject(frame, sp - 1);
                        FRAMES.setObject(frame, sp - 2, ACCESS.uncheckedCast(ACCESS.readObject(localNodes, BYTES.getIntUnaligned(localBc, bci + 2)), LtNode.class).execute(lhs, rhs));
                        FRAMES.clear(frame, sp - 1);
                        sp -= 1;
                        bci += 6;
                        continue loop;
                    }
                    // ( -- )
                    case OP_JUMP: {
                        int nextBci = BYTES.getIntUnaligned(localBc, bci + 2);
                        CompilerAsserts.partialEvaluationConstant(nextBci);
                        if (nextBci <= bci) {
                            Object result = backwardsJumpCheck(frame, sp, loopCounter, nextBci);
                            if (result != null) {
                                return result;
                            }
                        }
                        bci = nextBci;
                        continue loop;
                    }
                    // (b -- )
                    case OP_JUMP_FALSE: {
                        boolean cond = FRAMES.getObject(frame, sp - 1) == Boolean.TRUE;
                        int profileIdx = BYTES.getIntUnaligned(localBc, bci + 6);
                        FRAMES.clear(frame, sp - 1);
                        sp -= 1;
                        if (profileBranch(localBranchProfiles, profileIdx, !cond)) {
                            bci = BYTES.getIntUnaligned(localBc, bci + 2);
                            continue loop;
                        } else {
                            bci += 10;
                            continue loop;
                        }
                    }
                    // (i -- )
                    case OP_RETURN: {
                        return FRAMES.getObject(frame, sp - 1);
                    }
                    default:
                        CompilerDirectives.shouldNotReachHere();
                }
            }
        }
    }

}
