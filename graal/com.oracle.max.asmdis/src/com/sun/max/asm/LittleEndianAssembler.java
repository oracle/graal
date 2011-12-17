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
 */
public abstract class LittleEndianAssembler extends Assembler {

    protected LittleEndianAssembler() {
        super((byte) 0x90, true);
    }

    @Override
    protected void emitShort(short shortValue) {
        emitByte((byte) (shortValue & 0xff));
        emitByte((byte) (shortValue >> 8));
    }

    @Override
    protected void emitInt(int intValue) {
        emitShort((short) (intValue & 0xffff));
        emitShort((short) (intValue >> 16));
    }

    @Override
    protected void emitLong(long longValue) {
        emitInt((int) (longValue & 0xffffffffL));
        emitInt((int) (longValue >> 32));
    }
}
