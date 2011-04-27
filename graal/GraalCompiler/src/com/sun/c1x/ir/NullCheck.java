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
import com.sun.c1x.util.*;
import com.sun.c1x.value.*;
import com.sun.cri.bytecode.*;
import com.sun.cri.ri.*;

/**
 * The {@code NullCheck} class represents an explicit null check instruction.
 *
 * @author Ben L. Titzer
 */
public final class NullCheck extends StateSplit {

    Value object;

    /**
     * Constructs a new NullCheck instruction.
     * @param obj the instruction producing the object to check against null
     * @param stateBefore the state before executing the null check
     */
    public NullCheck(Value obj, FrameState stateBefore) {
        super(obj.kind, stateBefore);
        this.object = obj;
        setFlag(Flag.NonNull);
        if (object.isNonNull()) {
            eliminateNullCheck();
        }
    }

    /**
     * Gets the instruction that produces the object tested against null.
     * @return the instruction producing the object
     */
    public Value object() {
        return object;
    }

    /**
     * Checks whether this instruction can cause a trap.
     * @return {@code true} if this instruction can cause a trap
     */
    @Override
    public boolean canTrap() {
        return needsNullCheck();
    }

    @Override
    public void inputValuesDo(ValueClosure closure) {
        object = closure.apply(object);
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitNullCheck(this);
    }

    @Override
    public int valueNumber() {
        return Util.hash1(Bytecodes.IFNONNULL, object);
    }

    @Override
    public boolean valueEqual(Instruction i) {
        if (i instanceof NullCheck) {
            NullCheck o = (NullCheck) i;
            return object == o.object;
        }
        return false;
    }

    @Override
    public RiType declaredType() {
        // null check does not alter the type of the object
        return object.declaredType();
    }

    @Override
    public RiType exactType() {
        // null check does not alter the type of the object
        return object.exactType();
    }

    @Override
    public void runtimeCheckCleared() {
        clearState();
    }

    @Override
    public void print(LogStream out) {
        out.print("null_check(").print(object()).print(')');
        if (!canTrap()) {
          out.print(" (eliminated)");
        }
    }
}
