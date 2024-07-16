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

package com.oracle.truffle.espresso.analysis.frame;

import static com.oracle.truffle.espresso.meta.EspressoError.cat;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.verifier.StackMapFrameParser;
import com.oracle.truffle.espresso.verifier.StackMapFrameParser.FrameAndLocalEffect;
import com.oracle.truffle.espresso.verifier.VerificationTypeInfo;

/**
 * Provides a description of an Espresso frame, used in bytecode execution.
 * <p>
 * Such a descriptor is associated to a BCI, and provides a way to know statically the state of a
 * given {@link com.oracle.truffle.api.frame.VirtualFrame frame} at that BCI.
 * <p>
 * This information can then be used to access the {@link com.oracle.truffle.api.frame.VirtualFrame
 * frame} with the correct static accessors.
 * <p>
 */
public class EspressoFrameDescriptor {
    private static final long INT_MASK = 0xFFFF_FFFFL;

    @CompilationFinal(dimensions = 1) //
    private final FrameType[] slotTypes;
    private final int top;

    public EspressoFrameDescriptor(FrameType[] stackTypes, FrameType[] localsTypes, int top) {
        int stack = stackTypes.length;
        int locals = localsTypes.length;
        this.slotTypes = new FrameType[1 + locals + stack];
        slotTypes[0] = FrameType.INT;
        System.arraycopy(localsTypes, 0, slotTypes, 1, locals);
        System.arraycopy(stackTypes, 0, slotTypes, 1 + locals, stack);
        this.top = top;
    }

    private EspressoFrameDescriptor(FrameType[] slotTypes, int top) {
        this.slotTypes = slotTypes.clone();
        this.top = top;
    }

    public static long zeroExtend(int value) {
        return value & INT_MASK;
    }

    public static int narrow(long value) {
        return (int) value;
    }

    @ExplodeLoop
    public void importFromFrame(VirtualFrame frame, StaticObject[] objects, long[] primitives) {
        assert slotTypes.length == frame.getFrameDescriptor().getNumberOfSlots();
        assert verifyConsistent(frame);
        assert objects != null && primitives != null;
        assert slotTypes.length == objects.length && slotTypes.length == primitives.length;
        Arrays.fill(objects, StaticObject.NULL);
        for (int slot = 0; slot < slotTypes.length; slot++) {
            importSlot(frame, slot, objects, primitives);
        }
    }

    @ExplodeLoop
    public void exportToFrame(VirtualFrame frame, StaticObject[] objects, long[] primitives) {
        assert slotTypes.length == frame.getFrameDescriptor().getNumberOfSlots();
        assert objects != null && objects.length == slotTypes.length;
        assert primitives != null && primitives.length == slotTypes.length;
        for (int slot = 0; slot < slotTypes.length; slot++) {
            exportSlot(frame, slot, objects, primitives);
        }
    }

    public int size() {
        return slotTypes.length;
    }

    public int top() {
        return top;
    }

    private void importSlot(Frame frame, int slot, StaticObject[] objects, long[] primitives) {
        switch (slotTypes[slot].kind()) {
            case Int:
                primitives[slot] = zeroExtend(frame.getIntStatic(slot));
                break;
            case Float:
                primitives[slot] = zeroExtend(Float.floatToRawIntBits(frame.getFloatStatic(slot)));
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
                // Skip slots marked as illegal.
                break;
            default:
                // No sub-word kind in frames.
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere();
        }
    }

    private void exportSlot(Frame frame, int slot, StaticObject[] objects, long[] primitives) {
        switch (slotTypes[slot].kind()) {
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
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere();
        }
    }

    private boolean verifyConsistent(Frame frame) {
        for (int slot = 0; slot < slotTypes.length; slot++) {
            assert verifyConsistentSlot(frame, slot);
        }
        return true;
    }

    private boolean verifyConsistentSlot(Frame frame, int slot) {
        switch (slotTypes[slot].kind()) {
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
                break;
            }
            default:
                // No sub-word kind in frames.
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere();
        }
        return true;
    }

    public void validateImport(StaticObject[] pointers, long[] primitives, ObjectKlass accessingKlass, Meta meta) {
        guarantee(pointers.length == slotTypes.length, cat("Invalid pointers array length: ", pointers.length), meta);
        guarantee(primitives.length == slotTypes.length, cat("Invalid primitives array length: ", pointers.length), meta);
        for (int i = 0; i < slotTypes.length; i++) {
            FrameType ft = slotTypes[i];
            boolean checkNullObject = ft.isPrimitive();
            boolean checkZeroPrim = ft.isReference();
            if (checkNullObject) {
                guarantee(StaticObject.isNull(pointers[i]), cat("Non-null object in pointers array at slot: ", i, ", but expected a ", ft.toString()), meta);
            }
            if (checkZeroPrim) {
                guarantee(primitives[i] == 0, cat("Non-zero primitive in primitives array at slot: ", i, ", but expected a ", ft.toString()), meta);
            }
            // Ensures imported objects have the correct typing w.r.t. verification.
            if (ft.isReference()) {
                if (!StaticObject.isNull(pointers[i])) {
                    Klass targetKlass;
                    Klass objKlass = pointers[i].getKlass();
                    if (ft.type() == accessingKlass.getType()) {
                        // Handles hidden class cases.
                        targetKlass = accessingKlass;
                    } else {
                        if (ft.type() == objKlass.getType() && accessingKlass.getDefiningClassLoader() == objKlass.getDefiningClassLoader()) {
                            // same type and classloader, will resolve to same Klass.
                            continue;
                        }
                        targetKlass = meta.resolveSymbolOrFail(ft.type(), accessingKlass.getDefiningClassLoader(), accessingKlass.protectionDomain());
                    }
                    if (!targetKlass.isInterface()) { // Interfaces are erased.
                        guarantee(targetKlass.isAssignableFrom(objKlass),
                                        cat("Failed guarantee: Attempting to resume a continuation with invalid object class.\n",
                                                        "Expected: ", targetKlass.getExternalName(), "\n",
                                                        "But got: ", objKlass.getExternalName()),
                                        meta);
                    }
                }
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof EspressoFrameDescriptor that) {
            if (this.top() == that.top() && this.size() == that.size()) {
                for (int i = 0; i < this.size(); i++) {
                    if (!(this.slotTypes[i].equals(that.slotTypes[i]))) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(top(), Arrays.hashCode(slotTypes));
    }

    public static void guarantee(boolean condition, String message, Meta meta) {
        if (!condition) {
            throw meta.throwExceptionWithMessage(meta.continuum.org_graalvm_continuations_IllegalMaterializedRecordException, message);
        }
    }

    public static class Builder implements StackMapFrameParser.FrameState {
        int bci = -1;

        final FrameType[] types;
        final int maxLocals;
        int top = 0;

        public Builder(int maxLocals, int maxStack) {
            types = new FrameType[1 + maxLocals + maxStack];
            Arrays.fill(types, FrameType.ILLEGAL);
            types[0] = FrameType.INT;
            this.maxLocals = maxLocals;
        }

        private Builder(FrameType[] types, int maxLocals, int top) {
            this.types = types;
            this.maxLocals = maxLocals;
            this.top = top;
        }

        public void push(FrameType ft) {
            push(ft, true);
        }

        public void push(FrameType ft, boolean handle2Slots) {
            JavaKind k = ft.kind();
            assert k != JavaKind.Illegal || !handle2Slots;
            if (k == JavaKind.Void) {
                return;
            }
            JavaKind stackKind = k.getStackKind();
            // Quirk of the espresso frame: Long and Doubles are set closest to the top.
            if (handle2Slots && stackKind.needsTwoSlots()) {
                types[stackIdx(top)] = FrameType.ILLEGAL;
                top++;
            }
            types[stackIdx(top)] = ft;
            top++;
        }

        public FrameType pop(FrameType k) {
            return pop(k.kind());
        }

        public FrameType pop(JavaKind k) {
            FrameType popped = pop();
            assert popped.kind() == k;
            if (k.needsTwoSlots()) {
                FrameType dummy = pop();
                assert dummy == FrameType.ILLEGAL;
            }
            return popped;
        }

        public FrameType pop() {
            int head = stackIdx(top - 1);
            FrameType k = types[head];
            types[head] = FrameType.ILLEGAL;
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

        public void putLocal(int slot, FrameType k) {
            int idx = localIdx(slot);
            types[idx] = k;
            if (k.kind().needsTwoSlots()) {
                types[idx + 1] = FrameType.ILLEGAL;
            }
        }

        public void clear(int slot) {
            putLocal(slot, FrameType.ILLEGAL);
        }

        public FrameType getLocal(int slot) {
            return types[localIdx(slot)];
        }

        public boolean isWorking() {
            return bci < 0;
        }

        public boolean isRecord() {
            return bci >= 0;
        }

        public Builder copy() {
            return new Builder(types.clone(), maxLocals, top);
        }

        public EspressoFrameDescriptor build() {
            return new EspressoFrameDescriptor(types, top);
        }

        public int top() {
            return top;
        }

        public EspressoFrameDescriptor build(int topOverride) {
            return new EspressoFrameDescriptor(types, topOverride);
        }

        public Builder clearStack() {
            Arrays.fill(types, stackIdx(0), stackIdx(top), FrameType.ILLEGAL);
            top = 0;
            return this;
        }

        public boolean sameTop(Builder that) {
            return (this.types.length == that.types.length) && (this.top == that.top);
        }

        public Builder mergeInto(Builder that, int mergeBci, boolean trustThat, Function<Symbol<Type>, Klass> klassResolver) {
            assert mergeBci == that.bci;
            assert this.sameTop(that);
            Builder merged = null;
            for (int i = 0; i < types.length; i++) {
                FrameType thisFT = this.types[i];
                FrameType thatFT = that.types[i];
                // Illegal in 'that' is a trivial merge success.
                if (thatFT != FrameType.ILLEGAL) {
                    if (thisFT == FrameType.ILLEGAL) {
                        // Happens with stack maps and liveness analysis:
                        // update registered state to reflect cleared local.
                        that.types[i] = FrameType.ILLEGAL;
                    } else {
                        if (thisFT.isPrimitive() || thatFT.isPrimitive()) {
                            if (thisFT != thatFT) {
                                merged = updateMerged(merged, i, FrameType.ILLEGAL, that);
                            }
                        } else if (!trustThat && // With stack maps, precision is guaranteed.
                                        !thisFT.isNull() && // null merges trivially
                                        (thisFT.type() != thatFT.type())) {
                            Klass thisKlass = klassResolver.apply(thisFT.type());
                            Klass thatKlass = klassResolver.apply(thatFT.type());
                            Klass lca = thisKlass.findLeastCommonAncestor(thatKlass);
                            assert lca != null;
                            if (lca.getType() != thatFT.type()) {
                                // The least common ancestor is a new type, record it.
                                FrameType ft = FrameType.forType(lca.getType());
                                merged = updateMerged(merged, i, ft, that);
                            }
                        }
                    }
                }
                // slot merge success
            }
            return merged == null ? that : merged;
        }

        private static Builder updateMerged(Builder merged, int idx, FrameType ft, Builder that) {
            Builder result = merged;
            if (result == null) {
                result = that.copy();
            }
            result.types[idx] = ft;
            return result;
        }

        private int stackIdx(int slot) {
            assert slot >= 0;
            return 1 + maxLocals + slot;
        }

        private static int localIdx(int slot) {
            return 1 + slot;
        }

        @Override
        public Builder sameNoStack() {
            return copy().clearStack();
        }

        @Override
        public Builder sameLocalsWith1Stack(VerificationTypeInfo vfi, StackMapFrameParser.FrameBuilder<?> builder) {
            if (builder instanceof FrameAnalysis analysis) {
                Builder newFrame = copy().clearStack();
                newFrame.clearStack();
                FrameType k = fromTypeInfo(vfi, analysis);
                newFrame.push(k);
                return newFrame;
            }
            throw EspressoError.shouldNotReachHere();
        }

        @Override
        public FrameAndLocalEffect chop(int chop, int lastLocal) {
            Builder newFrame = copy().clearStack();
            int pos = lastLocal;
            for (int i = 0; i < chop; i++) {
                newFrame.clear(pos);
                pos--;
                if ((pos >= 0) && newFrame.getLocal(pos).kind().needsTwoSlots()) {
                    newFrame.clear(pos);
                    pos--;
                }
            }
            return new FrameAndLocalEffect(newFrame, pos - lastLocal);
        }

        @Override
        public FrameAndLocalEffect append(VerificationTypeInfo[] vtis, StackMapFrameParser.FrameBuilder<?> builder, int lastLocal) {
            if (builder instanceof FrameAnalysis analysis) {
                Builder newFrame = copy().clearStack();
                int pos = lastLocal;
                for (VerificationTypeInfo vti : vtis) {
                    FrameType k = fromTypeInfo(vti, analysis);
                    newFrame.putLocal(++pos, k);
                    if (k.kind().needsTwoSlots()) {
                        pos++;
                    }
                }
                return new FrameAndLocalEffect(newFrame, pos - lastLocal);
            }
            throw EspressoError.shouldNotReachHere();
        }
    }

    public static FrameType fromTypeInfo(VerificationTypeInfo vfi, FrameAnalysis analysis) {
        FrameType k;
        if (vfi.isIllegal()) {
            k = FrameType.ILLEGAL;
        } else if (vfi.isNull()) {
            k = FrameType.NULL;
        } else {
            k = FrameType.forType(vfi.getType(analysis.pool(), analysis.targetKlass(), analysis.stream()));
        }
        return k;
    }
}
