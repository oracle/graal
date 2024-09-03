/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.verifier;

import static com.oracle.truffle.espresso.classfile.Constants.APPEND_FRAME_BOUND;
import static com.oracle.truffle.espresso.classfile.Constants.CHOP_BOUND;
import static com.oracle.truffle.espresso.classfile.Constants.FULL_FRAME;
import static com.oracle.truffle.espresso.classfile.Constants.ITEM_InitObject;
import static com.oracle.truffle.espresso.classfile.Constants.ITEM_NewObject;
import static com.oracle.truffle.espresso.classfile.Constants.ITEM_Object;
import static com.oracle.truffle.espresso.classfile.Constants.SAME_FRAME_BOUND;
import static com.oracle.truffle.espresso.classfile.Constants.SAME_FRAME_EXTENDED;
import static com.oracle.truffle.espresso.classfile.Constants.SAME_LOCALS_1_STACK_ITEM_BOUND;
import static com.oracle.truffle.espresso.classfile.Constants.SAME_LOCALS_1_STACK_ITEM_EXTENDED;
import static com.oracle.truffle.espresso.verifier.MethodVerifier.failFormat;
import static com.oracle.truffle.espresso.verifier.MethodVerifier.failFormatNoFallback;

import com.oracle.truffle.espresso.classfile.ClassfileStream;
import com.oracle.truffle.espresso.classfile.attributes.StackMapTableAttribute;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;

public final class StackMapFrameParser<T extends StackMapFrameParser.FrameState> {

    public interface FrameState {
        /**
         * @return A copy of this FrameState, from which all Stack elements have been stripped.
         */
        FrameState sameNoStack();

        /**
         * @return A copy of this FrameState, with a single stack element: {@code vfi}
         */
        FrameState sameLocalsWith1Stack(VerificationTypeInfo vfi, FrameBuilder<?> builder);

        /**
         * Computes a new {@link FrameState} starting from the receiver, from which all stack
         * elements have been stripped, and {@code chop} locals have been cleared out, starting from
         * {@code lastLocal}.
         *
         * @param chop The number of locals to remove
         * @param lastLocal the position of the last local in the current FrameState.
         * @return A {@link FrameAndLocalEffect}, which contains the
         *         {@link FrameAndLocalEffect#state frame state} and the
         *         {@link FrameAndLocalEffect#effect effect on the number of local variables} (which
         *         can be different from {@code chop} if there are {@link JavaKind#needsTwoSlots()
         *         type 2} locals to chop). This effect should be computed such that the last local
         *         of the resulting state is given by {@code lastLocal + effect}.
         */
        FrameAndLocalEffect chop(int chop, int lastLocal);

        /**
         * Computes a new {@link FrameState} starting from the receiver, from which all stack
         * elements have been stripped, and {@code vfi} locals have been appended in, starting from
         * {@code lastLocal}.
         *
         * @param vfis The locals to append.
         * @param builder The corresponding {@link FrameBuilder}.
         * @param lastLocal the position of the last local in the current FrameState.
         * @return A {@link FrameAndLocalEffect}, which contains the
         *         {@link FrameAndLocalEffect#state frame state} and the
         *         {@link FrameAndLocalEffect#effect effect on the number of local variables} (which
         *         can be different from {@code vfis.length} if there are
         *         {@link JavaKind#needsTwoSlots() type 2} locals to append). This effect should be
         *         computed such that the last local of the resulting state is given by
         *         {@code lastLocal + effect}.
         */
        FrameAndLocalEffect append(VerificationTypeInfo[] vfis, FrameBuilder<?> builder, int lastLocal);
    }

    public record FrameAndLocalEffect(FrameState state, int effect) {
    }

    public interface FrameBuilder<S extends FrameState> {
        void registerStackMapFrame(int bci, S frame);

        /**
         * Returns a completely new {@link FrameState}, built from the given {@code stack} and
         * {@code locals}.
         *
         * @param stack The stack elements.
         * @param locals The local elements.
         * @param lastLocal the position of the last local in the current FrameState.
         * @return A {@link FrameAndLocalEffect}, which contains the
         *         {@link FrameAndLocalEffect#state frame state} and the
         *         {@link FrameAndLocalEffect#effect effect on the number of local variables}. This
         *         effect should be computed such that the last local of the resulting state is
         *         given by * {@code lastLocal + effect}.
         */
        FrameAndLocalEffect newFullFrame(VerificationTypeInfo[] stack, VerificationTypeInfo[] locals, int lastLocal);

        String toExternalString();
    }

    private final FrameBuilder<T> frameBuilder;
    private final ClassfileStream stream;

    private StackMapFrameParser(FrameBuilder<T> frameBuilder, StackMapTableAttribute stackMapTable) {
        this.frameBuilder = frameBuilder;
        this.stream = new ClassfileStream(stackMapTable.getData(), null);
    }

    public static <State extends FrameState> void parse(FrameBuilder<State> builder, StackMapTableAttribute stackMapTable, State firstFrame, int initialLastLocal) {
        new StackMapFrameParser<>(builder, stackMapTable).parseStackMapTableAttribute(firstFrame, initialLastLocal);
    }

    @SuppressWarnings("unchecked")
    private void parseStackMapTableAttribute(T firstFrame, int initialLastLocal) {
        T previous = firstFrame;
        int bci = 0;
        boolean first = true;
        int lastLocal = initialLastLocal;
        int entryCount = stream.readU2();
        for (int i = 0; i < entryCount; i++) {
            StackMapFrame entry = parseStackMapFrame();
            FrameAndLocalEffect res = nextFrame(entry, previous, lastLocal);

            lastLocal = lastLocal + res.effect();
            T frame = (T) res.state();

            bci = bci + entry.getOffset() + (first ? 0 : 1);
            frameBuilder.registerStackMapFrame(bci, frame);
            first = false;
            previous = frame;
        }
        // GR-19627 HotSpot's ad-hoc behavior: Truncated StackMapTable attributes throws
        // either VerifyError or ClassFormatError only if verified. Here the
        // attribute is marked for the verifier.
        if (!stream.isAtEndOfFile()) {
            throw failFormatNoFallback("Truncated StackMap attribute in " + frameBuilder.toExternalString());
        }
    }

    private FrameAndLocalEffect nextFrame(StackMapFrame entry, T previous, int lastLocal) {
        int frameType = entry.getFrameType();
        if (frameType < SAME_FRAME_BOUND || frameType == SAME_FRAME_EXTENDED) {
            return new FrameAndLocalEffect(previous.sameNoStack(), 0);
        }
        if (frameType < SAME_LOCALS_1_STACK_ITEM_BOUND || frameType == SAME_LOCALS_1_STACK_ITEM_EXTENDED) {
            return new FrameAndLocalEffect(previous.sameLocalsWith1Stack(entry.getStackItem(), frameBuilder), 0);
        }
        MethodVerifier.formatGuarantee(frameType >= SAME_LOCALS_1_STACK_ITEM_EXTENDED, "Encountered reserved StackMapFrame tag: " + frameType);
        if (frameType < CHOP_BOUND) {
            return previous.chop(entry.getChopped(), lastLocal);
        }
        if (frameType < APPEND_FRAME_BOUND) {
            return previous.append(entry.getLocals(), frameBuilder, lastLocal);
        }
        if (frameType == FULL_FRAME) {
            return frameBuilder.newFullFrame(entry.getStack(), entry.getLocals(), lastLocal);
        }
        throw EspressoError.shouldNotReachHere();
    }

    private StackMapFrame parseStackMapFrame() {
        int frameType = stream.readU1();
        if (frameType < SAME_FRAME_BOUND) {
            return new SameFrame(frameType);
        }
        if (frameType < SAME_LOCALS_1_STACK_ITEM_BOUND) {
            VerificationTypeInfo stackItem = parseVerificationTypeInfo();
            return new SameLocals1StackItemFrame(frameType, stackItem);
        }
        if (frameType < SAME_LOCALS_1_STACK_ITEM_EXTENDED) {
            // [128, 246] is reserved and still unused
            throw failFormatNoFallback("Encountered reserved StackMapFrame tag: " + frameType);
        }
        if (frameType == SAME_LOCALS_1_STACK_ITEM_EXTENDED) {
            int offsetDelta = stream.readU2();
            VerificationTypeInfo stackItem = parseVerificationTypeInfo();
            return new SameLocals1StackItemFrameExtended(frameType, offsetDelta, stackItem);
        }
        if (frameType < CHOP_BOUND) {
            int offsetDelta = stream.readU2();
            return new ChopFrame(frameType, offsetDelta);
        }
        if (frameType == SAME_FRAME_EXTENDED) {
            int offsetDelta = stream.readU2();
            return new SameFrameExtended(frameType, offsetDelta);
        }
        if (frameType < APPEND_FRAME_BOUND) {
            int offsetDelta = stream.readU2();
            int appendLength = frameType - SAME_FRAME_EXTENDED;
            VerificationTypeInfo[] locals = new VerificationTypeInfo[appendLength];
            for (int i = 0; i < appendLength; i++) {
                locals[i] = parseVerificationTypeInfo();
            }
            return new AppendFrame(frameType, offsetDelta, locals);
        }
        if (frameType == FULL_FRAME) {
            int offsetDelta = stream.readU2();
            int localsLength = stream.readU2();
            VerificationTypeInfo[] locals = new VerificationTypeInfo[localsLength];
            for (int i = 0; i < localsLength; i++) {
                locals[i] = parseVerificationTypeInfo();
            }
            int stackLength = stream.readU2();
            VerificationTypeInfo[] stack = new VerificationTypeInfo[stackLength];
            for (int i = 0; i < stackLength; i++) {
                stack[i] = parseVerificationTypeInfo();
            }
            return new FullFrame(frameType, offsetDelta, locals, stack);
        }
        throw failFormat("Unrecognized StackMapFrame tag: " + frameType);
    }

    private VerificationTypeInfo parseVerificationTypeInfo() {
        int tag = stream.readU1();
        if (tag < ITEM_InitObject) {
            return PrimitiveTypeInfo.get(tag);
        }
        switch (tag) {
            case ITEM_InitObject:
                return UninitializedThis.get();
            case ITEM_Object:
                return new ReferenceVariable(stream.readU2());
            case ITEM_NewObject:
                return new UninitializedVariable(stream.readU2());
            default:
                throw failFormatNoFallback("Unrecognized verification type info tag: " + tag);
        }
    }
}
