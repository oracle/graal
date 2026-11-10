/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.nodes;

import org.graalvm.wasm.WasmCodeEntry;
import org.graalvm.wasm.WasmConstant;
import org.graalvm.wasm.WasmType;
import org.graalvm.wasm.vector.Vector128;
import org.graalvm.wasm.vector.Vector128Ops;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

public abstract class WasmFrame {

    private WasmFrame() {
        // no instances
    }

    public static void dropPrimitive(VirtualFrame frame, int slot) {
        if (CompilerDirectives.inCompiledCode()) {
            // Needed to avoid keeping track of popped slots in FrameStates.
            frame.clearPrimitiveStatic(slot);
        }
    }

    public static void dropObject(VirtualFrame frame, int slot) {
        frame.clearObjectStatic(slot);
    }

    public static void drop(VirtualFrame frame, int slot) {
        frame.clearStatic(slot);
    }

    public static void copyPrimitive(VirtualFrame frame, int sourceSlot, int targetSlot) {
        frame.copyPrimitiveStatic(sourceSlot, targetSlot);
    }

    public static void copyObject(VirtualFrame frame, int sourceSlot, int targetSlot) {
        frame.copyObjectStatic(sourceSlot, targetSlot);
    }

    public static void copy(VirtualFrame frame, int sourceSlot, int targetSlot) {
        frame.copyStatic(sourceSlot, targetSlot);
    }

    public static boolean popBoolean(VirtualFrame frame, int slot) {
        boolean result = frame.getIntStatic(slot) != 0;
        if (CompilerDirectives.inCompiledCode()) {
            // Needed to avoid keeping track of popped slots in FrameStates.
            frame.clearPrimitiveStatic(slot);
        }
        return result;
    }

    public static int popInt(VirtualFrame frame, int slot) {
        int result = frame.getIntStatic(slot);
        if (CompilerDirectives.inCompiledCode()) {
            // Needed to avoid keeping track of popped slots in FrameStates.
            frame.clearPrimitiveStatic(slot);
        }
        return result;
    }

    public static void pushInt(VirtualFrame frame, int slot, int value) {
        frame.setIntStatic(slot, value);
    }

    public static long popLong(VirtualFrame frame, int slot) {
        long result = frame.getLongStatic(slot);
        if (CompilerDirectives.inCompiledCode()) {
            // Needed to avoid keeping track of popped slots in FrameStates.
            frame.clearPrimitiveStatic(slot);
        }
        return result;
    }

    public static void pushLong(VirtualFrame frame, int slot, long value) {
        frame.setLongStatic(slot, value);
    }

    public static float popFloat(VirtualFrame frame, int slot) {
        float result = frame.getFloatStatic(slot);
        if (CompilerDirectives.inCompiledCode()) {
            // Needed to avoid keeping track of popped slots in FrameStates.
            frame.clearPrimitiveStatic(slot);
        }
        return result;
    }

    public static void pushFloat(VirtualFrame frame, int slot, float value) {
        frame.setFloatStatic(slot, value);
    }

    public static double popDouble(VirtualFrame frame, int slot) {
        double result = frame.getDoubleStatic(slot);
        if (CompilerDirectives.inCompiledCode()) {
            // Needed to avoid keeping track of popped slots in FrameStates.
            frame.clearPrimitiveStatic(slot);
        }
        return result;
    }

    public static void pushDouble(VirtualFrame frame, int slot, double value) {
        frame.setDoubleStatic(slot, value);
    }

    public static <V128> V128 popVector128(VirtualFrame frame, int slot) {
        V128 result = Vector128Ops.cast(frame.getObjectStatic(slot));
        frame.clearObjectStatic(slot);
        return result;
    }

    public static <T> void pushVector128(VirtualFrame frame, int slot, T value) {
        frame.setObjectStatic(slot, value);
    }

    public static Object popReference(VirtualFrame frame, int slot) {
        Object result = frame.getObjectStatic(slot);
        frame.clearObjectStatic(slot);
        return result;
    }

    public static void pushReference(VirtualFrame frame, int slot, Object value) {
        frame.setObjectStatic(slot, value);
    }

    @ExplodeLoop
    public static void initializeLocals(VirtualFrame frame, int paramCount, WasmCodeEntry codeEntry) {
        final int localCount = codeEntry.localCount();
        for (int i = paramCount; i != localCount; ++i) {
            int type = codeEntry.localType(i);
            switch (type) {
                case WasmType.I32_TYPE -> pushInt(frame, i, 0);
                case WasmType.I64_TYPE -> pushLong(frame, i, 0L);
                case WasmType.F32_TYPE -> pushFloat(frame, i, 0F);
                case WasmType.F64_TYPE -> pushDouble(frame, i, 0D);
                case WasmType.V128_TYPE -> pushVector128(frame, i, Vector128Ops.SINGLETON_IMPLEMENTATION.fromVector128(Vector128.ZERO));
                default -> {
                    WasmType.isReferenceType(type);
                    pushReference(frame, i, WasmConstant.NULL);
                }
            }
        }
    }

    /**
     * Canonicalizes local slots, including parameters, at OSR entry without changing their
     * values. The default OSR transfer copies raw static slots, including stale values in their
     * unused primitive or object storage. Each local is read using its validated type before the
     * slot is cleared and rewritten, restoring its typed representation and clearing unused storage.
     * This avoids redundant frame-state phis when the imported state merges with typed writes in
     * compiled loops.
     *
     * Unlike operand stack slots, local slots have fixed types supplied by the code entry. Only
     * locals are normalized here; operand stack and reserved catch slots are left untouched.
     *
     * @param frame the frame containing the OSR entry state
     * @param codeEntry the validated code entry defining the local count and types
     */
    @ExplodeLoop
    public static void normalizeOSRLocals(VirtualFrame frame, WasmCodeEntry codeEntry) {
        int localCount = codeEntry.localCount();
        CompilerAsserts.partialEvaluationConstant(localCount);
        for (int i = 0; i < localCount; i++) {
            int type = codeEntry.localType(i);
            switch (type) {
                case WasmType.I32_TYPE -> {
                    int value = frame.getIntStatic(i);
                    frame.clearStatic(i);
                    pushInt(frame, i, value);
                }
                case WasmType.I64_TYPE -> {
                    long value = frame.getLongStatic(i);
                    frame.clearStatic(i);
                    pushLong(frame, i, value);
                }
                case WasmType.F32_TYPE -> {
                    float value = frame.getFloatStatic(i);
                    frame.clearStatic(i);
                    pushFloat(frame, i, value);
                }
                case WasmType.F64_TYPE -> {
                    double value = frame.getDoubleStatic(i);
                    frame.clearStatic(i);
                    pushDouble(frame, i, value);
                }
                case WasmType.V128_TYPE -> {
                    Object value = frame.getObjectStatic(i);
                    frame.clearStatic(i);
                    pushVector128(frame, i, value);
                }
                default -> {
                    assert WasmType.isReferenceType(type);
                    Object value = frame.getObjectStatic(i);
                    frame.clearStatic(i);
                    pushReference(frame, i, value);
                }
            }
        }
    }

    /**
     * Clears the unused indexed-frame tail at OSR entry. The default OSR transfer imports
     * the entire frame, while interpreter pops can leave stale primitive values in dead stack slots.
     * Clearing slots from {@code stackPointer} to the end of the frame makes their representation
     * consistent with compiled pops and avoids tracking those stale values in loop frame states.
     * Locals, reserved catch slots, and live operands below {@code stackPointer} are preserved.
     *
     * The entry stack pointer and frame slot count must be partial-evaluation constants so that the
     * clearing loop can be fully exploded.
     *
     * @param frame the frame containing the OSR entry state
     * @param stackPointer the first unused operand stack slot, expressed as an indexed-frame slot
     */
    @ExplodeLoop
    public static void clearDeadOSRStackSlots(VirtualFrame frame, int stackPointer) {
        CompilerAsserts.partialEvaluationConstant(stackPointer);
        int slotCount = frame.getFrameDescriptor().getNumberOfSlots();
        CompilerAsserts.partialEvaluationConstant(slotCount);
        for (int slot = stackPointer; slot < slotCount; slot++) {
            drop(frame, slot);
        }
    }
}
