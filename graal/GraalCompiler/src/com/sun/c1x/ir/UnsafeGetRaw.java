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
import com.sun.cri.ci.*;

/**
 * The {@code UnsafeGetRaw} instruction represents an unsafe access of raw memory where
 * the type is not an object reference.
 *
 * @author Ben L. Titzer
 */
public final class UnsafeGetRaw extends UnsafeRawOp {

    final boolean mayBeUnaligned;

    /**
     * Constructs a new UnsafeGetRaw instruction.
     * @param opKind the kind of the operation
     * @param addr the instruction generating the base address
     * @param mayBeUnaligned {@code true} if this operation may be unaligned
     */
    public UnsafeGetRaw(CiKind opKind, Value addr, boolean mayBeUnaligned) {
        super(opKind, addr, false);
        this.mayBeUnaligned = mayBeUnaligned;
    }

    /**
     * Constructs a new UnsafeGetRaw instruction.
     * @param opKind the kind of the operation
     * @param addr the instruction generating the base address
     * @param index the instruction generating the index
     * @param log2scale the log base 2 of the scaling factor
     * @param mayBeUnaligned {@code true} if this operation may be unaligned
     */
    public UnsafeGetRaw(CiKind opKind, Value addr, Value index, int log2scale, boolean mayBeUnaligned) {
        super(opKind, addr, index, log2scale, false);
        this.mayBeUnaligned = mayBeUnaligned;
    }

    /**
     * Checks whether this operation may be unaligned.
     * @return {@code true} if this operation may be unaligned
     */
    public boolean mayBeUnaligned() {
        return mayBeUnaligned;
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitUnsafeGetRaw(this);
    }

    @Override
    public void print(LogStream out) {
        out.print("UnsafeGetRaw.(base ").print(base());
        if (hasIndex()) {
            out.print(", index ").print(index()).print(", log2_scale ").print(log2Scale());
        }
        out.print(')');
    }
}
