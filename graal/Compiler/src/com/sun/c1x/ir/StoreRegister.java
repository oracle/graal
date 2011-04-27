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
 * The {@code StoreRegister} instruction represents a write of a physical register.
 * This instruction is part of the HIR support for low-level operations, such as safepoints,
 * stack banging, etc, and does not correspond to a Java operation.
 *
 * @author Ben L. Titzer
 */
public final class StoreRegister extends Instruction {

    public final CiRegister register;
    Value value;

    /**
     * Creates a new StoreReigster instance.
     * @param kind the kind of value stored to the register
     * @param register the register to store
     * @param value the value to write
     */
    public StoreRegister(CiKind kind, CiRegister register, Value value) {
        super(kind);
        this.register = register;
        this.value = value;
        setFlag(Flag.LiveStore);
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitStoreRegister(this);
    }

    public Value value() {
        return value;
    }

    @Override
    public void inputValuesDo(ValueClosure closure) {
        value = closure.apply(value);
    }

    @Override
    public void print(LogStream out) {
        out.print(register.toString()).print(" := ").print(value());
    }
}
