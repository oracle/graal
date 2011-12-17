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

import com.sun.max.lang.*;

/**
 * Represents a position assembled as an absolute address.
 */
public class AddressLiteral extends MutableAssembledObject {

    protected AddressLiteral(Assembler assembler, int startPosition, int endPosition, Label label) {
        super(assembler, startPosition, endPosition);
        assembler.addFixedSizeAssembledObject(this);
        this.label = label;
    }

    private final Label label;

    @Override
    protected final void assemble() throws AssemblyException {
        final Assembler assembler = assembler();
        final WordWidth wordWidth = assembler.wordWidth();
        if (wordWidth == WordWidth.BITS_64) {
            assembler.emitLong(assembler.baseAddress() + label.position());
        } else if  (wordWidth == WordWidth.BITS_32) {
            assembler.emitInt((int) (assembler.baseAddress() + label.position()));
        } else if  (wordWidth == WordWidth.BITS_16) {
            assembler.emitShort((short) (assembler.baseAddress() + label.position()));
        } else {
            assert wordWidth == WordWidth.BITS_8;
            assembler.emitByte((byte) (assembler.baseAddress() + label.position()));
        }
    }

    public final boolean isCode() {
        return false;
    }
}
