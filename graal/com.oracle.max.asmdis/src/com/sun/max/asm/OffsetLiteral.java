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

/**
 * Represents an offset between two positions (represented by given labels) in the assembled code.
 */
public class OffsetLiteral extends MutableAssembledObject {

    /**
     * Creates an offset between two positions (represented by given labels) in the assembled code.
     *
     * @param base the label whose position marks the base of the offset
     * @param target the label whose position marks the target of the offset
     */
    protected OffsetLiteral(Assembler assembler, int startPosition, int endPosition, Label target, Label base) {
        super(assembler, startPosition, endPosition);
        assembler.addFixedSizeAssembledObject(this);
        this.target = target;
        this.base = base;
    }

    private final Label target;
    private final Label base;

    @Override
    protected final void assemble() throws AssemblyException {
        final Assembler assembler = assembler();
        final long offset = offset();
        // select the correct assembler function to emit the offset.
        // The assembler knows the endianness to use
        switch(size()) {
            case 1:
                assembler.emitByte((byte) (offset & 0xFF));
                break;
            case 2:
                assembler.emitShort((short) (offset & 0xFFFF));
                break;
            case 4:
                assembler.emitInt((int) (offset & 0xFFFFFFFF));
                break;
            case 8:
                assembler.emitLong(offset);
                break;
            default:
                throw new AssemblyException("Invalid size for offset literal");
        }
    }

    public final long offset() throws AssemblyException {
        return target.position() - base.position();
    }

    public final boolean isCode() {
        return false;
    }
}
