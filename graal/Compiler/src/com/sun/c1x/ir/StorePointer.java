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
package com.sun.c1x.ir;

import com.sun.c1x.debug.*;
import com.sun.c1x.value.*;
import com.sun.cri.ci.*;

/**
 * The {@code StorePointer} instruction represents a write of a pointer.
 * This instruction is part of the HIR support for low-level operations, such as safepoints,
 * stack banging, etc, and does not correspond to a Java operation.
 *
 * @author Ben L. Titzer
 * @author Doug Simon
 */
public final class StorePointer extends PointerOp {

    Value value;

    /**
     * Creates an instruction for a pointer store. If {@code displacement != null}, the effective of the address of the store is
     * computed as the pointer plus a byte displacement plus a scaled index. Otherwise, the effective address is computed as the
     * pointer plus a byte offset.
     * @param dataKind the kind of value at the address accessed by the pointer operation
     * @param pointer the value producing the pointer
     * @param displacement the value producing the displacement. This may be {@code null}.
     * @param offsetOrIndex the value producing the scaled-index or the byte offset depending on whether {@code displacement} is {@code null}
     * @param value the value to write to the pointer
     * @param stateBefore the state before
     * @param isVolatile {@code true} if the access is volatile
     */
    public StorePointer(int opcode, CiKind dataKind, Value pointer, Value displacement, Value offsetOrIndex, Value value, FrameState stateBefore, boolean isVolatile) {
        super(CiKind.Void, dataKind, opcode, pointer, displacement, offsetOrIndex, stateBefore, isVolatile);
        this.value = value;
        setFlag(Flag.LiveStore);
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitStorePointer(this);
    }

    public Value value() {
        return value;
    }

    @Override
    public void inputValuesDo(ValueClosure closure) {
        super.inputValuesDo(closure);
        value = closure.apply(value);
    }

    @Override
    public void print(LogStream out) {
        out.print("*(").print(pointer);
        if (displacement() == null) {
            out.print(" + ").print(offset());
        } else {
            out.print(" + ").print(displacement()).print(" + (").print(index()).print(" * sizeOf(" + dataKind.name() + "))");
        }
        out.print(") := ").print(value());
    }

}
