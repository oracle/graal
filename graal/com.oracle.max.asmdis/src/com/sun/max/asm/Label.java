/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.asm;

import com.sun.max.program.*;

/**
 * Assembler labels for symbolic addressing.
 *
 * This class provides combined functionality for both 32-bit and 64-bit address spaces.
 * The respective assembler narrows the usable interface to either.
 *
 * @see Assembler32
 * @see Assembler64
 */
public final class Label implements Argument {

    public enum State {
        UNASSIGNED, BOUND, FIXED_32, FIXED_64;
    }

    protected State state = State.UNASSIGNED;

    public static Label createBoundLabel(int position) {
        final Label label = new Label();
        label.bind(position);
        return label;
    }

    public Label() {
    }

    public State state() {
        return state;
    }

    private int position;

    /**
     * Must only be called when the label is bound!
     */
    public int position() throws AssemblyException {
        if (state != State.BOUND) {
            throw new AssemblyException("unassigned or unbound label");
        }
        return position;
    }

    /**
     * Binds this label to a position in the assembler's instruction stream that represents the start of an instruction.
     * The assembler may update the position if any emitted instructions change lengths, so that this label keeps
     * denoting the same logical instruction.
     *
     * Only to be called by {@link Assembler#bindLabel(Label)}.
     *
     * @param pos
     *            an instruction's position in the assembler's instruction stream
     *
     * @see Assembler#bindLabel(Label)
     */
    void bind(int pos) {
        this.position = pos;
        state = State.BOUND;
    }

    void adjust(int delta) {
        assert state == State.BOUND;
        position += delta;
    }

    private int address32;
    private long address64;

    /**
     * Assigns a fixed, absolute 32-bit address to this label.
     * If used in a 64-bit assembler,
     * the effective address value would be unsigned-extended.
     *
     * @param address an absolute memory location
     *
     * @see Assembler#bindLabel(Label)
     */
    void fix32(int addr32) {
        this.address32 = addr32;
        state = State.FIXED_32;
    }

    /**
     * Assigns a fixed, absolute 64-bit address to this label.
     *
     * @param address an absolute memory location
     *
     * @see Assembler#bindLabel(Label)
     */
    void fix64(long addr64) {
        this.address64 = addr64;
        state = State.FIXED_64;
    }

    /**
     * Must only be called if this label has been {@link #fix32 fixed}.
     */
    public int address32() throws AssemblyException {
        switch (state) {
            case FIXED_32: {
                return address32;
            }
            case FIXED_64: {
                throw ProgramError.unexpected("64-bit address in 32-bit assembler");
            }
            default: {
                throw new AssemblyException("unassigned or unfixed label");
            }
        }
    }

    /**
     * Must only be called if this label has been {@link #fix64 fixed}.
     */
    public long address64() throws AssemblyException {
        switch (state) {
            case FIXED_64: {
                return address64;
            }
            case FIXED_32: {
                throw ProgramError.unexpected("32-bit address in 64-bit assembler");
            }
            default: {
                throw new AssemblyException("unassigned or unfixed label");
            }
        }
    }

    public String externalValue() {
        throw ProgramError.unexpected();
    }

    public String disassembledValue() {
        throw ProgramError.unexpected();
    }

    public long asLong() {
        throw ProgramError.unexpected("unimplemented");
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof Label) {
            final Label label = (Label) other;
            if (state != label.state) {
                return false;
            }
            switch (state) {
                case UNASSIGNED:
                    return this == label;
                case BOUND:
                    return position == label.position;
                case FIXED_32:
                    return address32 == label.address32;
                case FIXED_64:
                    return address64 == label.address64;
                default:
                    throw ProgramError.unexpected();
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        switch (state) {
            case BOUND:
                return position;
            case FIXED_32:
                return address32;
            case FIXED_64:
                return (int) (address64 ^ (address64 >> 32));
            default:
                return super.hashCode();
        }
    }

    @Override
    public String toString() {
        switch (state) {
            case UNASSIGNED:
                return "<unassigned>";
            case BOUND:
                return position >= 0 ? "+" + position : String.valueOf(position);
            case FIXED_32:
                return String.valueOf(address32);
            case FIXED_64:
                return String.valueOf(address64);
            default:
                throw ProgramError.unexpected();
        }
    }
}
