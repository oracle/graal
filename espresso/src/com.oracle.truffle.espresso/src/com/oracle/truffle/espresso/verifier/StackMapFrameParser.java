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

final class StackMapFrameParser {
    private final MethodVerifier verifier;
    private final ClassfileStream stream;

    private StackMapFrameParser(MethodVerifier verifier, StackMapTableAttribute stackMapTable) {
        this.verifier = verifier;
        this.stream = new ClassfileStream(stackMapTable.getData(), null);
    }

    public static void parse(MethodVerifier verifier, StackMapTableAttribute stackMapTable, StackFrame firstFrame) {
        new StackMapFrameParser(verifier, stackMapTable).parseStackMapTableAttribute(firstFrame);
    }

    private void parseStackMapTableAttribute(StackFrame firstFrame) {
        StackFrame previous = firstFrame;
        int bci = 0;
        boolean first = true;
        int entryCount = stream.readU2();
        for (int i = 0; i < entryCount; i++) {
            StackMapFrame entry = parseStackMapFrame();
            StackFrame frame = verifier.getStackFrame(entry, previous);
            bci = bci + entry.getOffset() + (first ? 0 : 1);
            verifier.registerStackMapFrame(bci, frame);
            first = false;
            previous = frame;
        }
        // GR-19627 HotSpot's ad-hoc behavior: Truncated StackMapTable attributes throws
        // either VerifyError or ClassFormatError only if verified. Here the
        // attribute is marked for the verifier.
        if (!stream.isAtEndOfFile()) {
            throw failFormatNoFallback("Truncated StackMap attribute in " + verifier.getThisKlass().getExternalName() + "." + verifier.getMethodName());
        }
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
