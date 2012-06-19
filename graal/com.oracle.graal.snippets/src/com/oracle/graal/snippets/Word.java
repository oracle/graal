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

import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.calc.*;

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
        L2W,
        I2W,
        W2L,
        W2I,
        PLUS,
        MINUS,
        COMPARE;
    }

    private Word(long value) {
        this.value = value;
    }

    private final long value;

    @Operation(L2W)
    public static Word fromLong(long value) {
        return new Word(value);
    }

    @Operation(I2W)
    public static Word fromInt(int value) {
        return new Word(value);
    }

    @Operation(W2I)
    public int toInt() {
        return (int) value;
    }

    @Operation(W2L)
    public long toLong() {
        return value;
    }

    @Operation(COMPARE)
    public boolean cmp(Condition condition, Word other) {
        long a = value;
        long b = other.value;
        switch (condition) {
            case AE: return (a >= b) ^ ((a < 0) != (b < 0));
            case AT: return (a > b) ^ ((a < 0) != (b < 0));
            case BE: return (a <= b) ^ ((a < 0) != (b < 0));
            case BT: return (a < b) ^ ((a < 0) != (b < 0));
            case EQ: return a == b;
            case NE: return a != b;
            default: throw new GraalInternalError("Unexpected operation on word: " + condition);
        }
    }

    @Operation(PLUS)
    public Word plus(int addend) {
        return new Word(value + addend);
    }

    @Operation(PLUS)
    public Word plus(long addend) {
        return new Word(value + addend);
    }

    @Operation(PLUS)
    public Word plus(Word addend) {
        return new Word(value + addend.value);
    }

    @Operation(MINUS)
    public Word minus(int addend) {
        return new Word(value - addend);
    }

    @Operation(MINUS)
    public Word minus(long addend) {
        return new Word(value - addend);
    }

    @Operation(MINUS)
    public Word minus(Word addend) {
        return new Word(value - addend.value);
    }
}
