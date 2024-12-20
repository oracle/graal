/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.metadata;

import static com.oracle.svm.interpreter.metadata.Bytecodes.LOOKUPSWITCH;

import com.oracle.svm.core.util.VMError;

/**
 * A utility for processing {@link Bytecodes#LOOKUPSWITCH} bytecodes.
 */
public final class LookupSwitch {

    private static final int OFFSET_TO_NUMBER_PAIRS = 4;
    private static final int OFFSET_TO_FIRST_PAIR_MATCH = 8;
    private static final int OFFSET_TO_FIRST_PAIR_OFFSET = 12;
    private static final int PAIR_SIZE = 8;

    private LookupSwitch() {
        throw VMError.shouldNotReachHereAtRuntime();
    }

    /**
     * Gets the offset from the start of the switch instruction for the {@code i}'th switch target.
     *
     * @param i the switch target index
     * @return the offset to the {@code i}'th switch target
     */
    public static int offsetAt(byte[] code, int bci, int i) {
        assert BytecodeStream.opcode(code, bci) == LOOKUPSWITCH;
        return BytecodeStream.readInt(code, TableSwitch.getAlignedBci(bci) + OFFSET_TO_FIRST_PAIR_OFFSET + PAIR_SIZE * i);
    }

    /**
     * Gets the key at {@code i}'th switch target index.
     *
     * @param i the switch target index
     * @return the key at {@code i}'th switch target index
     */
    public static int keyAt(byte[] code, int bci, int i) {
        assert BytecodeStream.opcode(code, bci) == LOOKUPSWITCH;
        return BytecodeStream.readInt(code, TableSwitch.getAlignedBci(bci) + OFFSET_TO_FIRST_PAIR_MATCH + PAIR_SIZE * i);
    }

    /**
     * Gets the number of switch targets.
     *
     * @return the number of switch targets
     */
    public static int numberOfCases(byte[] code, int bci) {
        assert BytecodeStream.opcode(code, bci) == LOOKUPSWITCH;
        return BytecodeStream.readInt(code, TableSwitch.getAlignedBci(bci) + OFFSET_TO_NUMBER_PAIRS);
    }

    /**
     * Gets the total size in bytes of the switch instruction.
     *
     * @return the total size in bytes of the switch instruction
     */
    public static int size(byte[] code, int bci) {
        assert BytecodeStream.opcode(code, bci) == LOOKUPSWITCH;
        return TableSwitch.getAlignedBci(bci) + OFFSET_TO_FIRST_PAIR_MATCH + PAIR_SIZE * numberOfCases(code, bci) - bci;
    }

    /**
     * Gets the index of the instruction denoted by the {@code i}'th switch target.
     *
     * @param i index of the switch target
     * @return the index of the instruction denoted by the {@code i}'th switch target
     */
    public static int targetAt(byte[] code, int bci, int i) {
        assert BytecodeStream.opcode(code, bci) == LOOKUPSWITCH;
        return bci + offsetAt(code, bci, i);
    }

    /**
     * Gets the index of the instruction for the default switch target.
     *
     * @return the index of the instruction for the default switch target
     */
    public static int defaultTarget(byte[] code, int bci) {
        assert BytecodeStream.opcode(code, bci) == LOOKUPSWITCH;
        return bci + defaultOffset(code, bci);
    }

    /**
     * Gets the offset from the start of the switch instruction to the default switch target.
     *
     * @return the offset to the default switch target
     */
    public static int defaultOffset(byte[] code, int bci) {
        assert BytecodeStream.opcode(code, bci) == LOOKUPSWITCH;
        return BytecodeStream.readInt(code, TableSwitch.getAlignedBci(bci));
    }
}
