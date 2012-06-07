/*
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir;

import com.oracle.graal.api.meta.*;
import com.oracle.max.cri.ci.*;

/**
 * Represents a value that is yet to be bound to a machine location (such as
 * a {@link CiRegisterValue} or {@link CiStackSlot}) by a register allocator.
 */
public final class Variable extends RiValue {
    private static final long serialVersionUID = 4507578431686109809L;

    /**
     * The identifier of the variable. This is a non-zero index in a contiguous 0-based name space.
     */
    public final int index;

    /**
     * The type of register that this variable needs to get assigned.
     */
    public final CiRegister.RegisterFlag flag;

    /**
     * Creates a new variable.
     * @param kind
     * @param index
     */
    public Variable(RiKind kind, int index, CiRegister.RegisterFlag flag) {
        super(kind);
        assert kind == kind.stackKind() : "Variables can be only created for stack kinds";
        assert index >= 0;
        this.index = index;
        this.flag = flag;
    }

    @Override
    public int hashCode() {
        return (index << 4) | kind.ordinal();
    }

    @Override
    public String toString() {
        return "v" + index + kindSuffix();
    }
}
