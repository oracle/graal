/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter;

import com.oracle.svm.interpreter.metadata.InterpreterUnresolvedSignature;
import com.oracle.svm.shared.AlwaysInline;

import jdk.graal.compiler.api.directives.GraalDirectives;

/**
 * Operand-stack overlay used by bytecode handlers. Up to two primitive slots are kept in the
 * threaded-handler state instead of being written to the frame. Object values are always
 * materialized so that reference-map and stack-walking semantics remain unchanged.
 */
final class CachedInterpreterOperandStack extends InterpreterOperandStack {
    private long tosPrimitive0;
    private long tosPrimitive1;
    int tosLevel;

    CachedInterpreterOperandStack(long top) {
        super(top);
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    long topForFrameStackOperation() {
        return top + tosLevel;
    }

    @AlwaysInline("Materialize cached primitives before direct frame-stack access")
    long materializeForFrameStackOperation(InterpreterFrame frame) {
        materialize(frame);
        return top;
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void applyFrameStackOperationDelta(int slotDelta) {
        assert tosLevel == 0;
        top += slotDelta;
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack access in the caller")
    int slotDeltaFrom(long initialTop) {
        return (int) (top + tosLevel - initialTop);
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void materialize(InterpreterFrame frame) {
        if (tosLevel == 1) {
            frame.setPrimitive(top, 0, tosPrimitive0);
            top++;
            tosLevel = 0;
        } else if (tosLevel == 2) {
            frame.setPrimitive(top, 0, tosPrimitive0);
            frame.setPrimitive(top, 1, tosPrimitive1);
            top += 2;
            tosLevel = 0;
        }
        killUnusedFields();
    }

    @AlwaysInline("Kill dependencies on unused cached primitive values")
    void killUnusedFields() {
        if (tosLevel == 0) {
            tosPrimitive0 = GraalDirectives.arbitraryValue(tosPrimitive0);
            tosPrimitive1 = GraalDirectives.arbitraryValue(tosPrimitive1);
        } else if (tosLevel == 1) {
            tosPrimitive1 = GraalDirectives.arbitraryValue(tosPrimitive1);
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void pushInt(InterpreterFrame frame, int value) {
        if (tosLevel == 0) {
            tosPrimitive0 = value;
            tosLevel = 1;
        } else if (tosLevel == 1) {
            tosPrimitive1 = value;
            tosLevel = 2;
        } else {
            frame.setPrimitive(top, 0, tosPrimitive0);
            tosPrimitive0 = tosPrimitive1;
            tosPrimitive1 = value;
            top++;
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void pushFloat(InterpreterFrame frame, float value) {
        pushInt(frame, Float.floatToRawIntBits(value));
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void pushLong(InterpreterFrame frame, long value) {
        if (tosLevel == 0) {
            tosPrimitive0 = GraalDirectives.arbitraryValue(tosPrimitive0);
            tosPrimitive1 = value;
            tosLevel = 2;
        } else if (tosLevel == 1) {
            frame.setPrimitive(top, 0, tosPrimitive0);
            tosPrimitive1 = value;
            top++;
            tosLevel = 2;
        } else {
            frame.setPrimitive(top, 0, tosPrimitive0);
            frame.setPrimitive(top, 1, tosPrimitive1);
            tosPrimitive1 = value;
            top += 2;
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void pushDouble(InterpreterFrame frame, double value) {
        pushLong(frame, Double.doubleToRawLongBits(value));
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void pushObject(InterpreterFrame frame, Object value) {
        materialize(frame);
        super.pushObject(frame, value);
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void pushReturnAddress(InterpreterFrame frame, int targetBCI) {
        materialize(frame);
        super.pushReturnAddress(frame, targetBCI);
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    int popInt(InterpreterFrame frame) {
        if (tosLevel == 0) {
            int value = (int) frame.getPrimitive(top, -1);
            top--;
            return value;
        } else if (tosLevel == 1) {
            tosLevel = 0;
            return GraalDirectives.assumeInt(tosPrimitive0);
        } else {
            tosLevel = 1;
            return GraalDirectives.assumeInt(tosPrimitive1);
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    float popFloat(InterpreterFrame frame) {
        if (tosLevel == 0) {
            float value = Float.intBitsToFloat((int) frame.getPrimitive(top, -1));
            top--;
            return value;
        } else if (tosLevel == 1) {
            tosLevel = 0;
            return GraalDirectives.assumeFloat(tosPrimitive0);
        } else {
            tosLevel = 1;
            return GraalDirectives.assumeFloat(tosPrimitive1);
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    long popLong(InterpreterFrame frame) {
        if (tosLevel == 0) {
            long value = frame.getPrimitive(top, -1);
            top -= 2;
            return value;
        } else if (tosLevel == 1) {
            top--;
            tosLevel = 0;
            return tosPrimitive0;
        } else {
            tosLevel = 0;
            return tosPrimitive1;
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    double popDouble(InterpreterFrame frame) {
        return Double.longBitsToDouble(popLong(frame));
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    Object popObject(InterpreterFrame frame) {
        if (tosLevel != 0) {
            throw InterpreterUtil.shouldNotReachHereAtRuntime();
        }
        return super.popObject(frame);
    }

    @Override
    @AlwaysInline("Keep invocation argument stack transitions in bytecode-handler stubs")
    Object[] popArguments(InterpreterFrame frame, byte[] argumentKinds, int argumentCount, Object appendix) {
        Object[] arguments = super.popArguments(frame, argumentKinds, argumentCount, appendix);
        materialize(frame);
        return arguments;
    }

    @Override
    @AlwaysInline("Keep materialized invocation argument stack transitions together")
    Object[] popArgumentsWithAppendix(InterpreterFrame frame, boolean hasReceiver, InterpreterUnresolvedSignature signature, Object appendix) {
        Object[] arguments = super.popArgumentsWithAppendix(frame, hasReceiver, signature, appendix);
        materialize(frame);
        return arguments;
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void pop1(InterpreterFrame frame, boolean clear) {
        if (tosLevel == 0) {
            if (clear) {
                frame.setReference(top, -1, null);
            }
            top--;
        } else if (tosLevel == 1) {
            tosLevel = 0;
        } else {
            tosLevel = 1;
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void pop2(InterpreterFrame frame, boolean clear) {
        if (tosLevel == 0) {
            if (clear) {
                frame.setReference(top, -1, null);
                frame.setReference(top, -2, null);
            }
            top -= 2;
        } else if (tosLevel == 1) {
            if (clear) {
                frame.setReference(top, -1, null);
            }
            top--;
            tosLevel = 0;
        } else {
            tosLevel = 0;
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    int peekInt(InterpreterFrame frame, long offset) {
        if (tosLevel == 2) {
            if (offset == -1) {
                return GraalDirectives.assumeInt(tosPrimitive1);
            }
            if (offset == -2) {
                return GraalDirectives.assumeInt(tosPrimitive0);
            }
        } else if (tosLevel == 1 && offset == -1) {
            return GraalDirectives.assumeInt(tosPrimitive0);
        }
        assert offset < -tosLevel;
        return (int) frame.getPrimitive(top, offset + tosLevel);
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    float peekFloat(InterpreterFrame frame, long offset) {
        if (tosLevel == 2) {
            if (offset == -1) {
                return GraalDirectives.assumeFloat(tosPrimitive1);
            }
            if (offset == -2) {
                return GraalDirectives.assumeFloat(tosPrimitive0);
            }
        } else if (tosLevel == 1 && offset == -1) {
            return GraalDirectives.assumeFloat(tosPrimitive0);
        }
        assert offset < -tosLevel;
        return Float.intBitsToFloat((int) frame.getPrimitive(top, offset + tosLevel));
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    long peekLong(InterpreterFrame frame, long offset) {
        if (tosLevel == 2) {
            if (offset == -1) {
                return tosPrimitive1;
            }
            if (offset == -2) {
                return tosPrimitive0;
            }
        } else if (tosLevel == 1 && offset == -1) {
            return tosPrimitive0;
        }
        if (offset >= -tosLevel) {
            throw InterpreterUtil.shouldNotReachHereAtRuntime();
        }
        return frame.getPrimitive(top, offset + tosLevel);
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    double peekDouble(InterpreterFrame frame, long offset) {
        return Double.longBitsToDouble(peekLong(frame, offset));
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    Object peekObject(InterpreterFrame frame, long offset) {
        if (offset >= -tosLevel) {
            throw InterpreterUtil.shouldNotReachHereAtRuntime();
        }
        return frame.getReference(top, offset + tosLevel);
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void dup1(InterpreterFrame frame) {
        if (tosLevel == 0) {
            copySlot(frame, -1, 0);
            top++;
        } else if (tosLevel == 1) {
            tosPrimitive1 = tosPrimitive0;
            tosLevel = 2;
        } else {
            frame.setPrimitive(top, 0, tosPrimitive0);
            tosPrimitive0 = tosPrimitive1;
            top++;
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void dupx1(InterpreterFrame frame) {
        if (tosLevel == 0) {
            super.dupx1(frame);
        } else if (tosLevel == 1) {
            copySlot(frame, -1, 0);
            overwriteSlot(frame, -1, tosPrimitive0);
            top++;
        } else {
            frame.setPrimitive(top, 0, tosPrimitive1);
            top++;
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void dupx2(InterpreterFrame frame) {
        if (tosLevel == 0) {
            super.dupx2(frame);
        } else if (tosLevel == 1) {
            copySlot(frame, -1, 0);
            copySlot(frame, -2, -1);
            overwriteSlot(frame, -2, tosPrimitive0);
            top++;
        } else {
            copySlot(frame, -1, 0);
            overwriteSlot(frame, -1, tosPrimitive1);
            top++;
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void dup2(InterpreterFrame frame) {
        if (tosLevel == 0) {
            super.dup2(frame);
        } else if (tosLevel == 1) {
            frame.setPrimitive(top, 0, tosPrimitive0);
            copySlot(frame, -1, 1);
            top += 2;
        } else {
            frame.setPrimitive(top, 0, tosPrimitive0);
            frame.setPrimitive(top, 1, tosPrimitive1);
            top += 2;
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void dup2x1(InterpreterFrame frame) {
        if (tosLevel == 0) {
            super.dup2x1(frame);
        } else if (tosLevel == 1) {
            copySlot(frame, -1, 1);
            copySlot(frame, -2, 0);
            copySlot(frame, -1, -2);
            overwriteSlot(frame, -1, tosPrimitive0);
            top += 2;
        } else {
            copySlot(frame, -1, 1);
            overwriteSlot(frame, -1, tosPrimitive0);
            overwriteSlot(frame, 0, tosPrimitive1);
            top += 2;
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void dup2x2(InterpreterFrame frame) {
        if (tosLevel == 0) {
            super.dup2x2(frame);
        } else if (tosLevel == 1) {
            copySlot(frame, -1, 1);
            copySlot(frame, -2, 0);
            copySlot(frame, -3, -1);
            copySlot(frame, 1, -3);
            overwriteSlot(frame, -2, tosPrimitive0);
            top += 2;
        } else {
            copySlot(frame, -1, 1);
            copySlot(frame, -2, 0);
            overwriteSlot(frame, -2, tosPrimitive0);
            overwriteSlot(frame, -1, tosPrimitive1);
            top += 2;
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void swap(InterpreterFrame frame) {
        if (tosLevel == 0) {
            super.swap(frame);
        } else if (tosLevel == 1) {
            copySlot(frame, -1, 0);
            overwriteSlot(frame, -1, tosPrimitive0);
            top++;
            tosLevel = 0;
        } else {
            long value = tosPrimitive0;
            tosPrimitive0 = tosPrimitive1;
            tosPrimitive1 = value;
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void clearOperandStack(InterpreterFrame frame) {
        materialize(frame);
        super.clearOperandStack(frame);
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    private void copySlot(InterpreterFrame frame, long srcOffset, long dstOffset) {
        frame.setPrimitive(top, dstOffset, frame.getPrimitive(top, srcOffset));
        frame.setReference(top, dstOffset, frame.getReference(top, srcOffset));
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    private void overwriteSlot(InterpreterFrame frame, long offset, long value) {
        frame.setReference(top, offset, null);
        frame.setPrimitive(top, offset, value);
    }
}
