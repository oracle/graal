/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.snippets;

import static com.oracle.graal.snippets.Word.Opcode.*;

import java.lang.annotation.*;

//JaCoCo Exclude

/**
 * Special type for use in snippets to represent machine word sized data.
 */
public final class Word {

    /**
     * Links a method to a canonical operation represented by an {@link Opcode} value.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Operation {
        Opcode value();
    }

    /**
     * The canonical {@link Operation} represented by a method in the {@link Word} class.
     */
    public enum Opcode {
        ZERO,
        W2A,
        A2W,
        L2W,
        I2W,
        W2L,
        W2I,
        PLUS,
        MINUS,
        OR,
        AND,
        XOR,
        BELOW,
        BELOW_EQUAL,
        ABOVE,
        ABOVE_EQUAL;
    }

    private Word(long value, Object object) {
        assert object == null || value == 0L;
        this.value = value;
        this.object = object;
    }

    private final long value;
    private final Object object;

    @Operation(ZERO)
    public static Word zero() {
        return new Word(0L, null);
    }

    @Operation(W2A)
    public Object toObject() {
        assert value == 0L;
        return object;
    }

    @Operation(A2W)
    public static Word fromObject(Object value) {
        return new Word(0L, value);
    }

    @Operation(L2W)
    public static Word fromLong(long value) {
        return new Word(value, null);
    }

    @Operation(I2W)
    public static Word fromInt(int value) {
        return new Word(value, null);
    }

    @Operation(W2I)
    public int toInt() {
        assert object == null;
        return (int) value;
    }

    @Operation(W2L)
    public long toLong() {
        assert object == null;
        return value;
    }

    @Operation(ABOVE)
    public boolean above(Word other) {
        assert object == null;
        assert other.object == null;
        long a = value;
        long b = other.value;
        return (a > b) ^ ((a < 0) != (b < 0));
    }

    @Operation(ABOVE_EQUAL)
    public boolean aboveOrEqual(Word other) {
        assert object == null;
        assert other.object == null;
        long a = value;
        long b = other.value;
        return (a >= b) ^ ((a < 0) != (b < 0));
    }

    @Operation(BELOW)
    public boolean below(Word other) {
        assert object == null;
        assert other.object == null;
        long a = value;
        long b = other.value;
        return (a < b) ^ ((a < 0) != (b < 0));
    }

    @Operation(BELOW_EQUAL)
    public boolean belowOrEqual(Word other) {
        assert object == null;
        assert other.object == null;
        long a = value;
        long b = other.value;
        return (a <= b) ^ ((a < 0) != (b < 0));
    }

    @Operation(PLUS)
    public Word plus(int addend) {
        assert object == null;
        return new Word(value + addend, null);
    }

    @Operation(PLUS)
    public Word plus(long addend) {
        assert object == null;
        return new Word(value + addend, null);
    }

    @Operation(PLUS)
    public Word plus(Word addend) {
        assert object == null;
        return new Word(value + addend.value, null);
    }

    @Operation(MINUS)
    public Word minus(int addend) {
        assert object == null;
        return new Word(value - addend, null);
    }

    @Operation(MINUS)
    public Word minus(long addend) {
        assert object == null;
        return new Word(value - addend, null);
    }

    @Operation(MINUS)
    public Word minus(Word addend) {
        assert object == null;
        return new Word(value - addend.value, null);
    }

    @Operation(OR)
    public Word or(int other) {
        assert object == null;
        return new Word(value | other, null);
    }

    @Operation(OR)
    public Word or(long other) {
        assert object == null;
        return new Word(value | other, null);
    }

    @Operation(OR)
    public Word or(Word other) {
        assert object == null;
        return new Word(value | other.value, null);
    }

    @Operation(AND)
    public Word and(int other) {
        assert object == null;
        return new Word(value & other, null);
    }

    @Operation(AND)
    public Word and(long other) {
        assert object == null;
        return new Word(value & other, null);
    }

    @Operation(AND)
    public Word and(Word other) {
        assert object == null;
        return new Word(value & other.value, null);
    }

    @Operation(XOR)
    public Word xor(int other) {
        assert object == null;
        return new Word(value | other, null);
    }

    @Operation(XOR)
    public Word xor(long other) {
        assert object == null;
        return new Word(value | other, null);
    }

    @Operation(XOR)
    public Word xor(Word other) {
        assert object == null;
        return new Word(value | other.value, null);
    }

}
