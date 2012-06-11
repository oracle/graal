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
        PLUS,
        MINUS,
        COMPARE;
    }

    private Word() {
    }

    @Operation(COMPARE)
    public native boolean cmp(Condition condition, Word other);

    @Operation(PLUS)
    public native Word plus(int addend);

    @Operation(PLUS)
    public native Word plus(long addend);

    @Operation(PLUS)
    public native Word plus(Word addend);

    @Operation(MINUS)
    public native Word minus(int addend);

    @Operation(MINUS)
    public native Word minus(long addend);

    @Operation(MINUS)
    public native Word minus(Word addend);
}
