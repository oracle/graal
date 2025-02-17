/*
 * Copyright (c) 2009, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.classfile.bytecode;

import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.TABLESWITCH;

/**
 * A utility for processing {@link Bytecodes#TABLESWITCH} bytecodes.
 */
public final class BytecodeTableSwitch extends BytecodeSwitch {

    private static final int OFFSET_TO_LOW_KEY = 4;
    private static final int OFFSET_TO_HIGH_KEY = 8;
    private static final int OFFSET_TO_FIRST_JUMP_OFFSET = 12;
    private static final int JUMP_OFFSET_SIZE = 4;

    public static final BytecodeTableSwitch INSTANCE = new BytecodeTableSwitch();

    private BytecodeTableSwitch() {
        // singleton
    }

    /**
     * Gets the low key of the table switch.
     *
     * @return the low key
     */
    public int lowKey(BytecodeStream stream, int bci) {
        assert stream.opcode(bci) == getSwitchBytecode();
        return stream.readInt(getAlignedBci(bci) + OFFSET_TO_LOW_KEY);
    }

    /**
     * Gets the high key of the table switch.
     *
     * @return the high key
     */
    public int highKey(BytecodeStream stream, int bci) {
        assert stream.opcode(bci) == getSwitchBytecode();
        return stream.readInt(getAlignedBci(bci) + OFFSET_TO_HIGH_KEY);
    }

    @Override
    public int keyAt(BytecodeStream stream, int bci, int i) {
        assert stream.opcode(bci) == getSwitchBytecode();
        return lowKey(stream, bci) + i;
    }

    @Override
    public int offsetAt(BytecodeStream stream, int bci, int i) {
        assert stream.opcode(bci) == getSwitchBytecode();
        return stream.readInt(getAlignedBci(bci) + OFFSET_TO_FIRST_JUMP_OFFSET + JUMP_OFFSET_SIZE * i);
    }

    @Override
    public int numberOfCases(BytecodeStream stream, int bci) {
        assert stream.opcode(bci) == getSwitchBytecode();
        return highKey(stream, bci) - lowKey(stream, bci) + 1;
    }

    @Override
    public int size(BytecodeStream stream, int bci) {
        assert stream.opcode(bci) == getSwitchBytecode();
        return getAlignedBci(bci) + OFFSET_TO_FIRST_JUMP_OFFSET + JUMP_OFFSET_SIZE * numberOfCases(stream, bci) - bci;
    }

    @Override
    protected int getSwitchBytecode() {
        return TABLESWITCH;
    }
}
