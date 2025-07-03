/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.truffle.benchmark.bytecode_dsl.manual;

import org.graalvm.truffle.benchmark.bytecode_dsl.BenchmarkLanguage;
import org.graalvm.truffle.benchmark.bytecode_dsl.manual.nodes.AddNode;
import org.graalvm.truffle.benchmark.bytecode_dsl.manual.nodes.LtNode;
import org.graalvm.truffle.benchmark.bytecode_dsl.manual.nodes.ModNode;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterSwitch;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.bytecode.BytecodeDSLAccess;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameExtensions;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.Node;

public class BytecodeInterpreterNoOpts extends BaseBytecodeRootNode {
    protected static final BytecodeDSLAccess ACCESS = BytecodeDSLAccess.lookup(AccessToken.PUBLIC_TOKEN, false);
    protected static final ByteArraySupport BYTES = ACCESS.getByteArraySupport();
    protected static final FrameExtensions FRAMES = ACCESS.getFrameExtensions();

    protected BytecodeInterpreterNoOpts(TruffleLanguage<?> language, FrameDescriptor frameDescriptor, byte[] bc, int numLocals, int numConditionalBranches, Object[] constants,
                    Node[] nodes) {
        super(language, frameDescriptor, bc, numLocals, numConditionalBranches, constants, nodes);
    }

    public static BytecodeInterpreterNoOpts create(BenchmarkLanguage language, Builder builder) {
        return new BytecodeInterpreterNoOpts(language, builder.getFrameDescriptor(), builder.getBytecode(), builder.numLocals, builder.numConditionalBranches, builder.getConstants(),
                        builder.getNodes());
    }

    @Override
    @BytecodeInterpreterSwitch
    @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
    protected Object executeAt(VirtualFrame frame, int startBci, int startSp) {
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
                case Opcodes.OP_CONST: {
                    FRAMES.setObject(frame, sp, ACCESS.readObject(localConstants, BYTES.getShort(localBc, bci + 2)));
                    sp += 1;
                    bci += 6;
                    continue loop;
                }
                // (i -- )
                case Opcodes.OP_ST_LOC: {
                    FRAMES.copy(frame, sp - 1, BYTES.getIntUnaligned(localBc, bci + 2));
                    FRAMES.clear(frame, sp - 1);
                    sp -= 1;
                    bci += 6;
                    continue loop;
                }
                // ( -- i)
                case Opcodes.OP_LD_LOC: {
                    FRAMES.copy(frame, BYTES.getIntUnaligned(localBc, bci + 2), sp);
                    sp += 1;
                    bci += 6;
                    continue loop;
                }
                // (i1 i2 -- i3)
                case Opcodes.OP_ADD: {
                    Object lhs = FRAMES.uncheckedGetObject(frame, sp - 2);
                    Object rhs = FRAMES.uncheckedGetObject(frame, sp - 1);
                    FRAMES.setObject(frame, sp - 2, ACCESS.uncheckedCast(ACCESS.readObject(localNodes, BYTES.getIntUnaligned(localBc, bci + 2)), AddNode.class).execute(lhs, rhs));
                    FRAMES.clear(frame, sp - 1);
                    sp -= 1;
                    bci += 6;
                    continue loop;
                }
                // (i1 i2 -- i3)
                case Opcodes.OP_MOD: {
                    Object lhs = FRAMES.uncheckedGetObject(frame, sp - 2);
                    Object rhs = FRAMES.uncheckedGetObject(frame, sp - 1);
                    FRAMES.setObject(frame, sp - 2, ACCESS.uncheckedCast(ACCESS.readObject(localNodes, BYTES.getIntUnaligned(localBc, bci + 2)), ModNode.class).execute(lhs, rhs));
                    FRAMES.clear(frame, sp - 1);
                    sp -= 1;
                    bci += 6;
                    continue loop;
                }
                // (i1 i2 -- b)
                case Opcodes.OP_LESS: {
                    Object lhs = FRAMES.uncheckedGetObject(frame, sp - 2);
                    Object rhs = FRAMES.uncheckedGetObject(frame, sp - 1);
                    FRAMES.setObject(frame, sp - 2, ACCESS.uncheckedCast(ACCESS.readObject(localNodes, BYTES.getIntUnaligned(localBc, bci + 2)), LtNode.class).execute(lhs, rhs));
                    FRAMES.clear(frame, sp - 1);
                    sp -= 1;
                    bci += 6;
                    continue loop;
                }
                // ( -- )
                case Opcodes.OP_JUMP: {
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
                case Opcodes.OP_JUMP_FALSE: {
                    boolean cond = (boolean) FRAMES.uncheckedGetObject(frame, sp - 1);
                    int profileIdx = BYTES.getIntUnaligned(localBc, bci + 6);
                    FRAMES.clear(frame, sp - 1);
                    sp -= 1;
                    if (profileBranch(localBranchProfiles, profileIdx, cond)) {
                        bci += 10;
                        continue loop;
                    } else {
                        bci = BYTES.getIntUnaligned(localBc, bci + 2);
                        continue loop;
                    }
                }
                // (i -- )
                case Opcodes.OP_RETURN: {
                    return FRAMES.getObject(frame, sp - 1);
                }
                default:
                    CompilerDirectives.shouldNotReachHere();
            }
        }
    }

    // NB: this code was manually copied from the generated Bytecode DSL code.
    protected static boolean profileBranch(int[] branchProfiles, int profileIndex, boolean condition) {
        int t = ACCESS.readInt(branchProfiles, profileIndex * 2);
        int f = ACCESS.readInt(branchProfiles, profileIndex * 2 + 1);
        boolean val = condition;
        if (val) {
            if (t == 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            if (f == 0) {
                // Make this branch fold during PE
                val = true;
            }
            if (CompilerDirectives.inInterpreter()) {
                if (t < Integer.MAX_VALUE) {
                    ACCESS.writeInt(branchProfiles, profileIndex * 2, t + 1);
                }
            }
        } else {
            if (f == 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            if (t == 0) {
                // Make this branch fold during PE
                val = false;
            }
            if (CompilerDirectives.inInterpreter()) {
                if (f < Integer.MAX_VALUE) {
                    ACCESS.writeInt(branchProfiles, profileIndex * 2 + 1, f + 1);
                }
            }
        }
        if (CompilerDirectives.inInterpreter()) {
            // no branch probability calculation in the interpreter
            return val;
        } else {
            int sum = t + f;
            return CompilerDirectives.injectBranchProbability((double) t / (double) sum, val);
        }
    }
}
