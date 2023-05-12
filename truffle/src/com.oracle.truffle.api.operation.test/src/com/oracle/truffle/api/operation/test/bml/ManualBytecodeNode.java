/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.operation.test.bml;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterSwitch;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.dsl.GeneratedBy;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.FastAccess;
import com.oracle.truffle.api.nodes.BytecodeOSRNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

public class ManualBytecodeNode extends BaseBytecodeNode {
    protected ManualBytecodeNode(TruffleLanguage<?> language, FrameDescriptor frameDescriptor, short[] bc) {
        super(language, frameDescriptor, bc);
    }

    @Override
    @BytecodeInterpreterSwitch
    @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
    protected Object executeAt(VirtualFrame frame, int startBci, int startSp) {
        short[] localBc = bc;
        int bci = startBci;
        int sp = startSp;

        Counter loopCounter = new Counter();

        loop: while (true) {
            short opcode = localBc[bci];
            CompilerAsserts.partialEvaluationConstant(opcode);
            switch (opcode) {
                // ( -- )
                case OP_JUMP: {
                    int nextBci = localBc[bci + 1];
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
                // (i1 i2 -- i3)
                case OP_ADD: {
                    int lhs = frame.getInt(sp - 2);
                    int rhs = frame.getInt(sp - 1);
                    frame.setInt(sp - 2, lhs + rhs);
                    sp -= 1;
                    bci += 1;
                    continue loop;
                }
                // (i1 i2 -- i3)
                case OP_MOD: {
                    int lhs = frame.getInt(sp - 2);
                    int rhs = frame.getInt(sp - 1);
                    frame.setInt(sp - 2, lhs % rhs);
                    sp -= 1;
                    bci += 1;
                    continue loop;
                }
                // ( -- i)
                case OP_CONST: {
                    frame.setInt(sp, (localBc[bci + 1] << 16) | (localBc[bci + 2] & 0xffff));
                    sp += 1;
                    bci += 3;
                    continue loop;
                }
                // (b -- )
                case OP_JUMP_FALSE: {
                    boolean cond = frame.getBoolean(sp - 1);
                    sp -= 1;
                    if (!cond) {
                        bci = localBc[bci + 1];
                        continue loop;
                    } else {
                        bci += 2;
                        continue loop;
                    }
                }
                // (i1 i2 -- b)
                case OP_LESS: {
                    int lhs = frame.getInt(sp - 2);
                    int rhs = frame.getInt(sp - 1);
                    frame.setBoolean(sp - 2, lhs < rhs);
                    sp -= 1;
                    bci += 1;
                    continue loop;
                }
                // (i -- )
                case OP_RETURN: {
                    return frame.getInt(sp - 1);
                }
                // (i -- )
                case OP_ST_LOC: {
                    frame.copy(sp - 1, localBc[bci + 1]);
                    sp -= 1;
                    bci += 2;
                    continue loop;
                }
                // ( -- i)
                case OP_LD_LOC: {
                    frame.copy(localBc[bci + 1], sp);
                    sp += 1;
                    bci += 2;
                    continue loop;
                }
                default:
                    CompilerDirectives.shouldNotReachHere();
            }
        }
    }
}

class ManualUnsafeBytecodeNode extends BaseBytecodeNode {
    protected ManualUnsafeBytecodeNode(TruffleLanguage<?> language, FrameDescriptor frameDescriptor, short[] bc) {
        super(language, frameDescriptor, bc);
    }

    private static final FastAccess UFA = FastAccess.UNSAFE;

    @Override
    @BytecodeInterpreterSwitch
    @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
    protected Object executeAt(VirtualFrame frame, int startBci, int startSp) {
        short[] localBc = bc;
        int bci = startBci;
        int sp = startSp;

        Counter loopCounter = new Counter();

        frame.getArguments();

        loop: while (true) {
            short opcode = UFA.shortArrayRead(localBc, bci);
            CompilerAsserts.partialEvaluationConstant(opcode);
            switch (opcode) {
                // ( -- )
                case OP_JUMP: {
                    int nextBci = UFA.shortArrayRead(localBc, bci + 1);
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
                // (i1 i2 -- i3)
                case OP_ADD: {
                    int lhs = UFA.getInt(frame, sp - 2);
                    int rhs = UFA.getInt(frame, sp - 1);
                    UFA.setInt(frame, sp - 2, lhs + rhs);
                    sp -= 1;
                    bci += 1;
                    continue loop;
                }
                // (i1 i2 -- i3)
                case OP_MOD: {
                    int lhs = UFA.getInt(frame, sp - 2);
                    int rhs = UFA.getInt(frame, sp - 1);
                    UFA.setInt(frame, sp - 2, lhs % rhs);
                    sp -= 1;
                    bci += 1;
                    continue loop;
                }
                // ( -- i)
                case OP_CONST: {
                    UFA.setInt(frame, sp, (UFA.shortArrayRead(localBc, bci + 2) << 16) | (UFA.shortArrayRead(localBc, bci + 1) & 0xffff));
                    sp += 1;
                    bci += 3;
                    continue loop;
                }
                // (b -- )
                case OP_JUMP_FALSE: {
                    boolean cond = UFA.getBoolean(frame, sp - 1);
                    sp -= 1;
                    if (!cond) {
                        bci = UFA.shortArrayRead(localBc, bci + 1);
                        continue loop;
                    } else {
                        bci += 2;
                        continue loop;
                    }
                }
                // (i1 i2 -- b)
                case OP_LESS: {
                    int lhs = UFA.getInt(frame, sp - 2);
                    int rhs = UFA.getInt(frame, sp - 1);
                    UFA.setBoolean(frame, sp - 2, lhs < rhs);
                    sp -= 1;
                    bci += 1;
                    continue loop;
                }
                // (i -- )
                case OP_RETURN: {
                    return UFA.getInt(frame, sp - 1);
                }
                // (i -- )
                case OP_ST_LOC: {
                    UFA.copyPrimitive(frame, sp - 1, UFA.shortArrayRead(localBc, bci + 1));
                    sp -= 1;
                    bci += 2;
                    continue loop;
                }
                // ( -- i)
                case OP_LD_LOC: {
                    UFA.copyPrimitive(frame, UFA.shortArrayRead(localBc, bci + 1), sp);
                    sp += 1;
                    bci += 2;
                    continue loop;
                }
                default:
                    CompilerDirectives.shouldNotReachHere();
            }
        }
    }
}

abstract class BaseBytecodeNode extends RootNode implements BytecodeOSRNode {

    protected BaseBytecodeNode(TruffleLanguage<?> language, FrameDescriptor frameDescriptor, short[] bc) {
        super(language, frameDescriptor);
        this.bc = bc;
    }

    @CompilationFinal(dimensions = 1) protected short[] bc;

    static final short OP_JUMP = 1;
    static final short OP_CONST = 2;
    static final short OP_ADD = 3;
    static final short OP_JUMP_FALSE = 4;
    static final short OP_LESS = 5;
    static final short OP_RETURN = 6;
    static final short OP_ST_LOC = 7;
    static final short OP_LD_LOC = 8;
    static final short OP_MOD = 9;

    @CompilerDirectives.ValueType
    protected static class Counter {
        int count;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return executeAt(frame, 0, 0);
    }

    protected abstract Object executeAt(VirtualFrame osrFrame, int startBci, int startSp);

    protected final Object backwardsJumpCheck(VirtualFrame frame, int sp, Counter loopCounter, int nextBci) {
        if (CompilerDirectives.hasNextTier() && ++loopCounter.count >= 256) {
            TruffleSafepoint.poll(this);
            LoopNode.reportLoopCount(this, 256);
            loopCounter.count = 0;
        }

        if (CompilerDirectives.inInterpreter() && BytecodeOSRNode.pollOSRBackEdge(this)) {
            Object osrResult = BytecodeOSRNode.tryOSR(this, (sp << 16) | nextBci, null, null, frame);
            if (osrResult != null) {
                return osrResult;
            }
        }

        return null;
    }

    public Object executeOSR(VirtualFrame osrFrame, int target, Object interpreterState) {
        return executeAt(osrFrame, target & 0xffff, target >> 16);
    }

    @CompilationFinal private Object osrMetadata;

    public Object getOSRMetadata() {
        return osrMetadata;
    }

    public void setOSRMetadata(Object osrMetadata) {
        this.osrMetadata = osrMetadata;
    }
}

class ManualBytecodeNodeNBE extends BaseBytecodeNode {

    protected ManualBytecodeNodeNBE(TruffleLanguage<?> language, FrameDescriptor frameDescriptor, short[] bc) {
        super(language, frameDescriptor, bc);
    }

    @Override
    @BytecodeInterpreterSwitch
    @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
    protected Object executeAt(VirtualFrame frame, int startBci, int startSp) {
        short[] localBc = bc;
        int bci = startBci;
        int sp = startSp;

        Counter counter = new Counter();

        loop: while (true) {
            short opcode = localBc[bci];
            CompilerAsserts.partialEvaluationConstant(opcode);
            switch (opcode) {
                // ( -- )
                case OP_JUMP: {
                    int nextBci = localBc[bci + 1];
                    CompilerAsserts.partialEvaluationConstant(nextBci);
                    if (nextBci <= bci) {
                        Object result = backwardsJumpCheck(frame, sp, counter, nextBci);
                        if (result != null) {
                            return result;
                        }
                    }
                    bci = nextBci;
                    continue loop;
                }
                // (i1 i2 -- i3)
                case OP_ADD: {
                    int lhs = (int) frame.getObject(sp - 2);
                    int rhs = (int) frame.getObject(sp - 1);
                    frame.setObject(sp - 2, lhs + rhs);
                    frame.clear(sp - 1);
                    sp -= 1;
                    bci += 1;
                    continue loop;
                }
                // (i1 i2 -- i3)
                case OP_MOD: {
                    int lhs = (int) frame.getObject(sp - 2);
                    int rhs = (int) frame.getObject(sp - 1);
                    frame.setObject(sp - 2, lhs % rhs);
                    frame.clear(sp - 1);
                    sp -= 1;
                    bci += 1;
                    continue loop;
                }
                // ( -- i)
                case OP_CONST: {
                    frame.setObject(sp, (localBc[bci + 1] << 16) | (localBc[bci + 2] & 0xffff));
                    sp += 1;
                    bci += 3;
                    continue loop;
                }
                // (b -- )
                case OP_JUMP_FALSE: {
                    boolean cond = frame.getObject(sp - 1) == Boolean.TRUE;
                    frame.clear(sp - 1);
                    sp -= 1;
                    if (!cond) {
                        bci = localBc[bci + 1];
                        continue loop;
                    } else {
                        bci += 2;
                        continue loop;
                    }
                }
                // (i1 i2 -- b)
                case OP_LESS: {
                    int lhs = (int) frame.getObject(sp - 2);
                    int rhs = (int) frame.getObject(sp - 1);
                    frame.setObject(sp - 2, lhs < rhs);
                    frame.clear(sp - 1);
                    sp -= 1;
                    bci += 1;
                    continue loop;
                }
                // (i -- )
                case OP_RETURN: {
                    return frame.getObject(sp - 1);
                }
                // (i -- )
                case OP_ST_LOC: {
                    frame.copy(sp - 1, localBc[bci + 1]);
                    frame.clear(sp - 1);
                    sp -= 1;
                    bci += 2;
                    continue loop;
                }
                // ( -- i)
                case OP_LD_LOC: {
                    frame.copy(localBc[bci + 1], sp);
                    sp += 1;
                    bci += 2;
                    continue loop;
                }
                default:
                    CompilerDirectives.shouldNotReachHere();
            }
        }
    }
}

@GeneratedBy(ManualUnsafeBytecodeNode.class) // needed for UFA
class ManualBytecodeNodedNode extends BaseBytecodeNode {

    private final Object[] objs;

    protected ManualBytecodeNodedNode(TruffleLanguage<?> language, FrameDescriptor frameDescriptor, short[] bc, Object[] objs) {
        super(language, frameDescriptor, bc);
        this.objs = objs;
    }

    private static final FastAccess UFA = FastAccess.UNSAFE;

    public abstract static class AddNode extends Node {
        public abstract int execute(int lhs, int rhs);

        @Specialization
        public static int doInt(int lhs, int rhs) {
            return lhs + rhs;
        }

        public static AddNode create() {
            return ManualBytecodeNodedNodeFactory.AddNodeGen.create();
        }
    }

    public abstract static class ModNode extends Node {
        public abstract int execute(int lhs, int rhs);

        @Specialization
        public static int doInt(int lhs, int rhs) {
            return lhs % rhs;
        }

        public static ModNode create() {
            return ManualBytecodeNodedNodeFactory.ModNodeGen.create();
        }
    }

    @Override
    @BytecodeInterpreterSwitch
    @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
    protected Object executeAt(VirtualFrame frame, int startBci, int startSp) {
        short[] localBc = bc;
        Object[] localObjs = objs;
        int bci = startBci;
        int sp = startSp;

        Counter loopCounter = new Counter();

        frame.getArguments();

        loop: while (true) {
            short opcode = UFA.shortArrayRead(localBc, bci);
            Object obj = UFA.objectArrayRead(localObjs, bci);
            CompilerAsserts.partialEvaluationConstant(opcode);
            switch (opcode) {
                // ( -- )
                case OP_JUMP: {
                    int nextBci = UFA.cast(obj, Integer.class);
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
                // (i1 i2 -- i3)
                case OP_ADD: {
                    int lhs = UFA.getInt(frame, sp - 2);
                    int rhs = UFA.getInt(frame, sp - 1);
                    UFA.setInt(frame, sp - 2, UFA.cast(obj, AddNode.class).execute(lhs, rhs));
                    sp -= 1;
                    bci += 1;
                    continue loop;
                }
                // (i1 i2 -- i3)
                case OP_MOD: {
                    int lhs = UFA.getInt(frame, sp - 2);
                    int rhs = UFA.getInt(frame, sp - 1);
                    UFA.setInt(frame, sp - 2, UFA.cast(obj, ModNode.class).execute(lhs, rhs));
                    sp -= 1;
                    bci += 1;
                    continue loop;
                }
                // ( -- i)
                case OP_CONST: {
                    UFA.setInt(frame, sp, UFA.cast(obj, Integer.class));
                    sp += 1;
                    bci += 1;
                    continue loop;
                }
                // (b -- )
                case OP_JUMP_FALSE: {
                    boolean cond = UFA.getBoolean(frame, sp - 1);
                    sp -= 1;
                    if (!cond) {
                        bci = UFA.cast(obj, Integer.class);
                        continue loop;
                    } else {
                        bci += 1;
                        continue loop;
                    }
                }
                // (i1 i2 -- b)
                case OP_LESS: {
                    int lhs = UFA.getInt(frame, sp - 2);
                    int rhs = UFA.getInt(frame, sp - 1);
                    UFA.setBoolean(frame, sp - 2, lhs < rhs);
                    sp -= 1;
                    bci += 1;
                    continue loop;
                }
                // (i -- )
                case OP_RETURN: {
                    return UFA.getInt(frame, sp - 1);
                }
                // (i -- )
                case OP_ST_LOC: {
                    UFA.copyPrimitive(frame, sp - 1, UFA.cast(obj, Integer.class));
                    sp -= 1;
                    bci += 1;
                    continue loop;
                }
                // ( -- i)
                case OP_LD_LOC: {
                    UFA.copyPrimitive(frame, UFA.cast(obj, Integer.class), sp);
                    sp += 1;
                    bci += 1;
                    continue loop;
                }
                default:
                    CompilerDirectives.shouldNotReachHere();
            }
        }
    }
}
