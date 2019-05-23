/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.bytecode;

/**
 * An abstract class that provides the state and methods common to {@link Bytecodes#LOOKUPSWITCH}
 * and {@link Bytecodes#TABLESWITCH} instructions.
 */
public abstract class BytecodeSwitch {

    /**
     * The {@link BytecodeStream} containing the bytecode array.
     */
    protected final BytecodeStream stream;

    /**
     * Constructor for a {@link BytecodeStream}.
     *
     * @param stream the {@code BytecodeStream} containing the switch instruction
     */
    public BytecodeSwitch(BytecodeStream stream) {
        this.stream = stream;
    }

    protected int getAlignedBci(int bci) {
        return (bci + 4) & 0xfffffffc;
    }

    /**
     * Gets the index of the instruction denoted by the {@code i}'th switch target.
     *
     * @param i index of the switch target
     * @return the index of the instruction denoted by the {@code i}'th switch target
     */
    public int targetAt(int bci, int i) {
        return bci + offsetAt(bci, i);
    }

    /**
     * Gets the index of the instruction for the default switch target.
     *
     * @return the index of the instruction for the default switch target
     */
    public int defaultTarget(int bci) {
        return bci + defaultOffset(bci);
    }

    /**
     * Gets the offset from the start of the switch instruction to the default switch target.
     *
     * @return the offset to the default switch target
     */
    public int defaultOffset(int bci) {
        return stream.readInt(getAlignedBci(bci));
    }

    /**
     * Gets the key at {@code i}'th switch target index.
     *
     * @param i the switch target index
     * @return the key at {@code i}'th switch target index
     */
    public abstract int keyAt(int bci, int i);

    /**
     * Gets the offset from the start of the switch instruction for the {@code i}'th switch target.
     *
     * @param i the switch target index
     * @return the offset to the {@code i}'th switch target
     */
    public abstract int offsetAt(int bci, int i);

    /**
     * Gets the number of switch targets.
     *
     * @return the number of switch targets
     */
    public abstract int numberOfCases(int bci);

    /**
     * Gets the total size in bytes of the switch instruction.
     *
     * @return the total size in bytes of the switch instruction
     */
    public abstract int size(int bci);
}
