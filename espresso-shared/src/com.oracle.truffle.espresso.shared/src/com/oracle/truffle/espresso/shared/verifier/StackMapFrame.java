/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.shared.verifier;

import static com.oracle.truffle.espresso.classfile.Constants.CHOP_BOUND;
import static com.oracle.truffle.espresso.classfile.Constants.SAME_FRAME_BOUND;
import static com.oracle.truffle.espresso.shared.verifier.VerifierError.fatal;

import java.io.PrintStream;

import com.oracle.truffle.espresso.classfile.ConstantPool;

abstract class StackMapFrame {
    protected final int frameType;

    public int getFrameType() {
        return frameType;
    }

    StackMapFrame(int frameType) {
        this.frameType = frameType;
    }

    public int getChopped() {
        throw fatal("Asking for chopped value of non chopped frame");
    }

    public VerificationTypeInfo getStackItem() {
        throw fatal("Asking for stack item of incompatible stackMap frame");
    }

    public VerificationTypeInfo[] getStack() {
        throw fatal("Asking for stack of incompatible stackMap frame");
    }

    public VerificationTypeInfo[] getLocals() {
        throw fatal("Asking for locals of incompatible stackMap frame");
    }

    public abstract int getOffset();

    @SuppressWarnings("unused") // For debug purposes
    public void print(ConstantPool pool, PrintStream out) {
        out.println("        " + this.getClass().getSimpleName() + " {");
        out.println("            Offset: " + getOffset());
    }
}

final class SameFrame extends StackMapFrame {

    SameFrame(int frameType) {
        super(frameType);
    }

    @Override
    public int getOffset() {
        return frameType;
    }

    @Override
    public void print(ConstantPool pool, PrintStream out) {
        out.println("        " + this.getClass().getSimpleName() + " {");
        out.println("            Offset: " + getOffset());
        out.println("        }");
    }
}

final class SameLocals1StackItemFrame extends StackMapFrame {
    private final VerificationTypeInfo stackItem;

    SameLocals1StackItemFrame(int frameType, VerificationTypeInfo stackItem) {
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

    @Override
    public void print(ConstantPool pool, PrintStream out) {
        super.print(pool, out);
        out.println("            Stack: " + stackItem.toString(pool));
        out.println("        }");
    }

}

final class SameLocals1StackItemFrameExtended extends StackMapFrame {
    private final int offsetDelta;
    private final VerificationTypeInfo stackItem;

    SameLocals1StackItemFrameExtended(int frameType, int offsetDelta, VerificationTypeInfo stackItem) {
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

    @Override
    public void print(ConstantPool pool, PrintStream out) {
        super.print(pool, out);
        out.println("            Stack: " + stackItem.toString(pool));
        out.println("        }");
    }
}

final class ChopFrame extends StackMapFrame {
    private final int offsetDelta;

    ChopFrame(int frameType, int offsetDelta) {
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

    @Override
    public void print(ConstantPool pool, PrintStream out) {
        super.print(pool, out);
        out.println("            cut locals: " + getChopped());
        out.println("        }");
    }
}

final class SameFrameExtended extends StackMapFrame {
    private final int offsetDelta;

    SameFrameExtended(int frameType, int offsetDelta) {
        super(frameType);
        this.offsetDelta = offsetDelta;
    }

    @Override
    public int getOffset() {
        return offsetDelta;
    }

    @Override
    public void print(ConstantPool pool, PrintStream out) {
        super.print(pool, out);
        out.println("        }");
    }
}

final class AppendFrame extends StackMapFrame {
    private final int offsetDelta;
    private final VerificationTypeInfo[] newLocals;

    AppendFrame(int frameType, int offsetDelta, VerificationTypeInfo[] newLocals) {
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

    @Override
    public void print(ConstantPool pool, PrintStream out) {
        super.print(pool, out);
        out.println("            Add Locals: [");
        for (VerificationTypeInfo vti : newLocals) {
            out.println("                " + vti.toString(pool));
        }
        out.println("            ]");
        out.println("        }");
    }

}

final class FullFrame extends StackMapFrame {
    private final int offsetDelta;
    private final VerificationTypeInfo[] locals;
    private final VerificationTypeInfo[] stack;

    FullFrame(int frameType, int offsetDelta, VerificationTypeInfo[] locals, VerificationTypeInfo[] stack) {
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

    @Override
    public void print(ConstantPool pool, PrintStream out) {
        super.print(pool, out);
        out.println("            Locals: [");
        for (VerificationTypeInfo vti : locals) {
            out.println("                " + vti.toString(pool));
        }
        out.println("            ]");
        out.println("            Stack: [");
        for (VerificationTypeInfo vti : stack) {
            out.println("                " + vti.toString(pool));
        }
        out.println("            ]");
        out.println("        }");
    }
}
