/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package com.oracle.truffle.espresso.classfile;

import com.oracle.truffle.espresso.meta.EspressoError;

import static com.oracle.truffle.espresso.classfile.Constants.CHOP_BOUND;
import static com.oracle.truffle.espresso.classfile.Constants.SAME_FRAME_BOUND;

public abstract class StackMapFrame {
    protected final int frameType;

    public int getFrameType() {
        return frameType;
    }

    StackMapFrame(int frameType) {
        this.frameType = frameType;
    }

    public int getChopped() {
        throw EspressoError.shouldNotReachHere("Asking for chopped value of non chopped frame");
    }

    public VerificationTypeInfo getStackItem() {
        throw EspressoError.shouldNotReachHere("Asking for stack item of incompatible stackMap frame");
    }

    public VerificationTypeInfo[] getStack() {
        throw EspressoError.shouldNotReachHere("Asking for stack of incompatible stackMap frame");
    }

    public VerificationTypeInfo[] getLocals() {
        throw EspressoError.shouldNotReachHere("Asking for stack of incompatible stackMap frame");
    }

    public abstract int getOffset();
}

class SameFrame extends StackMapFrame {

    public SameFrame(int frameType) {
        super(frameType);
    }

    @Override
    public int getOffset() {
        return frameType;
    }
}

class SameLocals1StackItemFrame extends StackMapFrame {
    private final VerificationTypeInfo stackItem;

    public SameLocals1StackItemFrame(int frameType, VerificationTypeInfo stackItem) {
        super(frameType);
        this.stackItem = stackItem;
    }

    @Override
    public int getOffset() {
        return frameType - SAME_FRAME_BOUND;
    }

    @Override
    public VerificationTypeInfo getStackItem() {
        return stackItem;
    }

}

class SameLocals1StackItemFrameExtended extends StackMapFrame {
    private final int offsetDelta;
    private final VerificationTypeInfo stackItem;

    public SameLocals1StackItemFrameExtended(int frameType, int offsetDelta, VerificationTypeInfo stackItem) {
        super(frameType);
        this.offsetDelta = offsetDelta;
        this.stackItem = stackItem;
    }

    @Override
    public int getOffset() {
        return offsetDelta;
    }

    @Override
    public VerificationTypeInfo getStackItem() {
        return stackItem;
    }
}

class ChopFrame extends StackMapFrame {
    private final int offsetDelta;

    public ChopFrame(int frameType, int offsetDelta) {
        super(frameType);
        this.offsetDelta = offsetDelta;
    }

    @Override
    public int getOffset() {
        return offsetDelta;
    }

    @Override
    public int getChopped() {
        return CHOP_BOUND - frameType;
    }
}

class SameFrameExtended extends StackMapFrame {
    private final int offsetDelta;

    public SameFrameExtended(int frameType, int offsetDelta) {
        super(frameType);
        this.offsetDelta = offsetDelta;
    }

    @Override
    public int getOffset() {
        return offsetDelta;
    }
}

class AppendFrame extends StackMapFrame {
    private final int offsetDelta;
    private final VerificationTypeInfo[] newLocals;

    public AppendFrame(int frameType, int offsetDelta, VerificationTypeInfo[] newLocals) {
        super(frameType);
        this.offsetDelta = offsetDelta;
        this.newLocals = newLocals;
    }

    @Override
    public int getOffset() {
        return offsetDelta;
    }

    @Override
    public VerificationTypeInfo[] getLocals() {
        return newLocals;
    }

}

class FullFrame extends StackMapFrame {
    private final int offsetDelta;
    private final VerificationTypeInfo[] locals;
    private final VerificationTypeInfo[] stack;

    public FullFrame(int frameType, int offsetDelta, VerificationTypeInfo[] locals, VerificationTypeInfo[] stack) {
        super(frameType);
        this.offsetDelta = offsetDelta;
        this.locals = locals;
        this.stack = stack;
    }

    @Override
    public int getOffset() {
        return offsetDelta;
    }

    @Override
    public VerificationTypeInfo[] getLocals() {
        return locals;
    }

    @Override
    public VerificationTypeInfo[] getStack() {
        return stack;
    }
}