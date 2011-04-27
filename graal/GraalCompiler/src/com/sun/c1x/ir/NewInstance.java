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
import com.sun.cri.ri.*;

/**
 * The {@code NewInstance} instruction represents the allocation of an instance class object.
 *
 * @author Ben L. Titzer
 */
public final class NewInstance extends StateSplit {

    final RiType instanceClass;
    public final int cpi;
    public final RiConstantPool constantPool;

    /**
     * Constructs a NewInstance instruction.
     * @param type the class being allocated
     * @param cpi the constant pool index
     * @param stateBefore the state before executing this instruction
     */
    public NewInstance(RiType type, int cpi, RiConstantPool constantPool, FrameState stateBefore) {
        super(CiKind.Object, stateBefore);
        this.instanceClass = type;
        this.cpi = cpi;
        this.constantPool = constantPool;
        setFlag(Flag.NonNull);
        setFlag(Flag.ResultIsUnique);
    }

    /**
     * Gets the instance class being allocated by this instruction.
     * @return the instance class allocated
     */
    public RiType instanceClass() {
        return instanceClass;
    }

    /**
     * Checks whether this instruction can trap.
     * @return {@code true}, assuming that allocation can cause OutOfMemory or other exceptions
     */
    @Override
    public boolean canTrap() {
        return true;
    }

    /**
     * Gets the exact type produced by this instruction. For allocations of instance classes, this is
     * always the class allocated.
     * @return the exact type produced by this instruction
     */
    @Override
    public RiType exactType() {
        return instanceClass;
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitNewInstance(this);
    }

    @Override
    public void print(LogStream out) {
        out.print("new instance ").print(CiUtil.toJavaName(instanceClass()));
    }
}
