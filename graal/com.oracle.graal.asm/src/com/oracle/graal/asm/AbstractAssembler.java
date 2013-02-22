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
package com.oracle.graal.asm;

import java.nio.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;

/**
 * The platform-independent base class for the assembler.
 */
public abstract class AbstractAssembler {

    public final TargetDescription target;
    public final Buffer codeBuffer;

    public AbstractAssembler(TargetDescription target) {
        this.target = target;

        if (target.arch.getByteOrder() == ByteOrder.BIG_ENDIAN) {
            this.codeBuffer = new Buffer.BigEndian();
        } else {
            this.codeBuffer = new Buffer.LittleEndian();
        }
    }

    public void bind(Label l) {
        assert !l.isBound() : "can bind label only once";
        l.bind(codeBuffer.position());
        l.patchInstructions(this);
    }

    public abstract void align(int modulus);

    public abstract void jmp(Label l);

    protected abstract void patchJumpTarget(int branch, int jumpTarget);

    protected final void emitByte(int x) {
        codeBuffer.emitByte(x);
    }

    protected final void emitShort(int x) {
        codeBuffer.emitShort(x);
    }

    protected final void emitInt(int x) {
        codeBuffer.emitInt(x);
    }

    protected final void emitLong(long x) {
        codeBuffer.emitLong(x);
    }

    /**
     * Some GPU architectures have a text based encoding.
     */
    protected final void emitString(String x) {
        codeBuffer.emitString(x);
    }

    // XXX for pretty-printing
    protected final void emitString0(String x) {
        codeBuffer.emitString0(x);
    }

    /**
     * This is used by the TargetMethodAssembler to convert a {@link StackSlot} to an
     * {@link Address}.
     */
    public abstract Address makeAddress(Kind kind, Value base, int displacement);

    /**
     * Returns a target specific placeholder address that can be used for code patching.
     */
    public abstract Address getPlaceholder();
}
