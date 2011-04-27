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
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * The {@code CheckCast} instruction represents a {@link Bytecodes#CHECKCAST}.
 *
 * @author Ben L. Titzer
 */
public final class CheckCast extends TypeCheck {

    /**
     * Creates a new CheckCast instruction.
     * @param targetClass the class being cast to
     * @param object the instruction producing the object
     * @param stateBefore the state before the cast
     */
    public CheckCast(RiType targetClass, Value targetClassInstruction, Value object, FrameState stateBefore) {
        super(targetClass, targetClassInstruction, object, CiKind.Object, stateBefore);
        initFlag(Flag.NonNull, object.isNonNull());
    }

    /**
     * Gets the declared type of the result of this instruction.
     * @return the declared type of the result
     */
    @Override
    public RiType declaredType() {
        return targetClass;
    }

    /**
     * Gets the exact type of the result of this instruction.
     * @return the exact type of the result
     */
    @Override
    public RiType exactType() {
        return targetClass.isResolved() ? targetClass.exactType() : null;
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitCheckCast(this);
    }

    @Override
    public int valueNumber() {
        return targetClass.isResolved() ? Util.hash1(Bytecodes.CHECKCAST, object) : 0;
    }

    @Override
    public boolean valueEqual(Instruction i) {
        if (i instanceof CheckCast) {
            CheckCast o = (CheckCast) i;
            return targetClass == o.targetClass && object == o.object;
        }
        return false;
    }

    @Override
    public void print(LogStream out) {
        out.print("checkcast(").
        print(object()).
        print(",").
        print(targetClassInstruction()).
        print(") ").
        print(CiUtil.toJavaName(targetClass()));
    }
}
