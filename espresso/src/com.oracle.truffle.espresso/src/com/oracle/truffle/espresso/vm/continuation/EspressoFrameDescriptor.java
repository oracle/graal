/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.vm.continuation;

import static com.oracle.truffle.espresso.meta.EspressoError.cat;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

/**
 * Provides a description of an Espresso frame, used in bytecode execution.
 * <p>
 * Such a descriptor is associated to a BCI, and provides a way to know statically the state of a
 * given {@link com.oracle.truffle.api.frame.VirtualFrame frame} at that BCI.
 * <p>
 * This information can then be used to access the {@link com.oracle.truffle.api.frame.VirtualFrame
 * frame} with the correct static accessors.
 * <p>
 * Note that this is a raw description of the frame, there is no notion of stack or locals.
 */
public class EspressoFrameDescriptor {
    private static final long INT_MASK = 0xFFFFFFFFL;
    private static final boolean CHECK_ILLEGAL = false;

    @CompilerDirectives.CompilationFinal(dimensions = 1) private final JavaKind[] kinds;

    public EspressoFrameDescriptor(JavaKind[] stackKinds, JavaKind[] localsKind) {
        int stack = stackKinds.length;
        int locals = localsKind.length;
        this.kinds = new JavaKind[1 + locals + stack];
        kinds[0] = JavaKind.Int;
        System.arraycopy(localsKind, 0, kinds, 1, locals);
        System.arraycopy(stackKinds, 0, kinds, 1 + locals, stack);
    }

    public void importFromFrame(Frame frame, Object[] objects, long[] primitives) {
        assert kinds.length == frame.getFrameDescriptor().getNumberOfSlots();
        assert verifyConsistent(frame);
        assert objects != null && primitives != null;
        assert kinds.length == objects.length && kinds.length == primitives.length;
        Arrays.fill(objects, StaticObject.NULL);
        for (int slot = 0; slot < kinds.length; slot++) {
            importSlot(frame, slot, objects, primitives);
        }
    }

    public void exportToFrame(Frame frame, Object[] objects, long[] primitives) {
        assert kinds.length == frame.getFrameDescriptor().getNumberOfSlots();
        assert objects != null && objects.length == kinds.length;
        assert primitives != null && primitives.length == kinds.length;
        for (int slot = 0; slot < kinds.length; slot++) {
            exportSlot(frame, slot, objects, primitives);
        }
    }

    public int size() {
        return kinds.length;
    }

    public byte[] rawTags() {
        byte[] tags = new byte[kinds.length];
        for (int i = 0; i < kinds.length; i++) {
            tags[i] = kinds[i].toOrdinal();
        }
        return tags;
    }

    private static JavaKind[] fromTags(byte[] tags) {
        JavaKind[] kinds = new JavaKind[tags.length];
        for (int i = 0; i < tags.length; i++) {
            kinds[i] = JavaKind.fromOrdinalByte(tags[i]);
        }
        return kinds;
    }

    private void importSlot(Frame frame, int slot, Object[] objects, long[] primitives) {
        switch (kinds[slot]) {
            case Int:
                primitives[slot] = extend(frame.getIntStatic(slot));
                break;
            case Float:
                primitives[slot] = extend(Float.floatToRawIntBits(frame.getFloatStatic(slot)));
                break;
            case Long:
                primitives[slot] = frame.getLongStatic(slot);
                break;
            case Double:
                primitives[slot] = Double.doubleToRawLongBits(frame.getDoubleStatic(slot));
                break;
            case Object:
                objects[slot] = (StaticObject) frame.getObjectStatic(slot);
                break;
            case Illegal:
                break;
            default:
                // No sub-word kind in frames.
                throw EspressoError.shouldNotReachHere();
        }
    }

    private void exportSlot(Frame frame, int slot, Object[] objects, long[] primitives) {
        switch (kinds[slot]) {
            case Int:
                frame.setIntStatic(slot, narrow(primitives[slot]));
                break;
            case Float:
                frame.setFloatStatic(slot, Float.intBitsToFloat(narrow(primitives[slot])));
                break;
            case Long:
                frame.setLongStatic(slot, primitives[slot]);
                break;
            case Double:
                frame.setDoubleStatic(slot, Double.longBitsToDouble(primitives[slot]));
                break;
            case Object:
                frame.setObjectStatic(slot, objects[slot]);
                break;
            case Illegal:
                frame.clearStatic(slot);
                break;
            default:
                // No sub-word kind in frames.
                throw EspressoError.shouldNotReachHere();
        }
    }

    static long extend(int value) {
        return value & INT_MASK;
    }

    static int narrow(long value) {
        return (int) value;
    }

    private EspressoFrameDescriptor(JavaKind[] kinds) {
        this.kinds = kinds.clone();
    }

    private boolean verifyConsistent(Frame frame) {
        for (int slot = 0; slot < kinds.length; slot++) {
            assert verifyConsistentSlot(frame, slot);
        }
        return true;
    }

    private boolean verifyConsistentSlot(Frame frame, int slot) {
        switch (kinds[slot]) {
            case Int:
                frame.getIntStatic(slot);
                break;
            case Float:
                frame.getFloatStatic(slot);
                break;
            case Long:
                frame.getLongStatic(slot);
                break;
            case Double:
                frame.getDoubleStatic(slot);
                break;
            case Object:
                frame.getObjectStatic(slot);
                break;
            case Illegal: {
                if (!CHECK_ILLEGAL) {
                    break;
                }
                illegalCheck(frame, slot);
                break;
            }
            default:
                // No sub-word kind in frames.
                throw EspressoError.shouldNotReachHere();
        }
        return true;
    }

    private static void illegalCheck(Frame frame, int slot) {
        if (frame.getTag(slot) == 0) {
            // uninitialized slot, it's fine.
            return;
        }
        // Try all possible slot kinds to check for a failure in each.
        boolean fail = false;
        try {
            frame.getIntStatic(slot);
            fail = true;
        } catch (AssertionError e) {
            /* nop */
        }
        try {
            frame.getFloatStatic(slot);
            fail = true;
        } catch (AssertionError e) {
            /* nop */
        }
        try {
            frame.getLongStatic(slot);
            fail = true;
        } catch (AssertionError e) {
            /* nop */
        }
        try {
            frame.getDoubleStatic(slot);
            fail = true;
        } catch (AssertionError e) {
            /* nop */
        }
        try {
            frame.getObjectStatic(slot);
            fail = true;
        } catch (AssertionError e) {
            /* nop */
        }
        if (fail) {
            throw new AssertionError();
        }
    }

    public void validateImport(StaticObject[] pointers, long[] primitives, Meta meta) {
        guarantee(pointers.length == kinds.length, cat("Invalid pointers array length: ", pointers.length), meta);
        guarantee(primitives.length == kinds.length, cat("Invalid primitives array length: ", pointers.length), meta);
        for (int i = 0; i < kinds.length; i++) {
            JavaKind k = kinds[i];
            boolean checkNullObject = (k == JavaKind.Illegal) || (k.isPrimitive());
            boolean checkZeroPrim = (k == JavaKind.Illegal) || (!k.isPrimitive());
            if (checkNullObject) {
                guarantee(StaticObject.isNull(pointers[i]), cat("Non-null object in pointers array at slot: ", i, ", but expected a ", k.toString()), meta);
            }
            if (checkZeroPrim) {
                guarantee(primitives[i] == 0, cat("Non-zero primitive in primitives array at slot: ", i, ", but expected a ", k.toString()), meta);
            }
        }
    }

    static void guarantee(boolean condition, String message, Meta meta) {
        if (!condition) {
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalStateException, message);
        }
    }

    public static class Builder {
        int bci = -1;

        final JavaKind[] kinds;
        final int maxLocals;
        int top = 0;

        public Builder(int maxLocals, int maxStack) {
            kinds = new JavaKind[1 + maxLocals + maxStack];
            Arrays.fill(kinds, JavaKind.Illegal);
            kinds[0] = JavaKind.Int;
            this.maxLocals = maxLocals;
        }

        private Builder(JavaKind[] kinds, int maxLocals, int top) {
            this.kinds = kinds;
            this.maxLocals = maxLocals;
            this.top = top;
        }

        public void push(JavaKind k) {
            push(k, true);
        }

        public void push(JavaKind k, boolean handle2Slots) {
            if (k == JavaKind.Void) {
                return;
            }
            JavaKind stackKind = k.getStackKind();
            // Quirk of the espresso frame: Long and Doubles are set closest to the top.
            if (handle2Slots && stackKind.needsTwoSlots()) {
                kinds[stackIdx(top)] = JavaKind.Illegal;
                top++;
            }
            kinds[stackIdx(top)] = stackKind;
            top++;
        }

        public JavaKind pop() {
            int head = stackIdx(top - 1);
            JavaKind k = kinds[head];
            kinds[head] = JavaKind.Illegal;
            top--;
            return k;
        }

        public void pop2() {
            pop();
            pop();
        }

        public void setBci(int bci) {
            this.bci = bci;
        }

        public void putLocal(int slot, JavaKind k) {
            int idx = localIdx(slot);
            kinds[idx] = k;
            if (k.needsTwoSlots()) {
                kinds[idx + 1] = JavaKind.Illegal;
            }
        }

        public void clear(int slot) {
            putLocal(slot, JavaKind.Illegal);
        }

        public boolean isWorking() {
            return bci < 0;
        }

        public boolean isRecord() {
            return bci >= 0;
        }

        public Builder copy() {
            return new Builder(kinds.clone(), maxLocals, top);
        }

        public EspressoFrameDescriptor build() {
            return new EspressoFrameDescriptor(kinds);
        }

        public void clearStack() {
            Arrays.fill(kinds, stackIdx(0), stackIdx(top), JavaKind.Illegal);
            top = 0;
        }

        public boolean sameTop(Builder that) {
            return (this.kinds.length == that.kinds.length) && (this.top == that.top);
        }

        public Builder mergeInto(Builder that, int mergeBci) {
            assert mergeBci == that.bci;
            assert this.sameTop(that);
            Builder merged = null;
            for (int i = 0; i < kinds.length; i++) {
                if (that.kinds[i] == JavaKind.Illegal) {
                    /* nop */
                } else if (this.kinds[i] != that.kinds[i]) {
                    if (merged == null) {
                        merged = that.copy();
                    }
                    merged.kinds[i] = JavaKind.Illegal;
                }
            }
            return merged == null ? that : merged;
        }

        private int stackIdx(int slot) {
            return 1 + maxLocals + slot;
        }

        private static int localIdx(int slot) {
            return 1 + slot;
        }
    }
}
