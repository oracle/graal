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

import com.sun.c1x.value.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * The {@code TypeCheck} instruction is the base class of casts and instanceof tests.
 *
 * @author Ben L. Titzer
 */
public abstract class TypeCheck extends StateSplit {

    final RiType targetClass;
    public Value targetClassInstruction;
    Value object;

    /**
     * Creates a new TypeCheck instruction.
     * @param targetClass the class which is being casted to or checked against
     * @param object the instruction which produces the object
     * @param kind the result type of this instruction
     * @param stateBefore the state before this instruction is executed
     */
    public TypeCheck(RiType targetClass, Value targetClassInstruction, Value object, CiKind kind, FrameState stateBefore) {
        super(kind, stateBefore);
        this.targetClass = targetClass;
        this.targetClassInstruction = targetClassInstruction;
        this.object = object;
    }

    /**
     * Gets the instruction that loads the target class object that is used by this checkcast.
     * @return the target class instruction
     */
    public Value targetClassInstruction() {
        return targetClassInstruction;
    }

    /**
     * Gets the target class, i.e. the class being cast to, or the class being tested against.
     * @return the target class
     */
    public RiType targetClass() {
        return targetClass;
    }

    /**
     * Gets the instruction which produces the object input.
     * @return the instruction producing the object
     */
    public Value object() {
        return object;
    }

    /**
     * Checks whether the target class of this instruction is loaded.
     * @return {@code true} if the target class is loaded
     */
    public boolean isLoaded() {
        return targetClass != null;
    }

    /**
     * Checks whether this instruction can trap.
     * @return {@code true}, conservatively assuming the cast may fail
     */
    @Override
    public boolean canTrap() {
        return true;
    }

    /**
     * Iterates over the input values to this instruction.
     * @param closure the closure to apply
     */
    @Override
    public void inputValuesDo(ValueClosure closure) {
        object = closure.apply(object);
        targetClassInstruction = closure.apply(targetClassInstruction);
    }

    /**
     * Sets this type check operation to be a direct compare.
     */
    public void setDirectCompare() {
        setFlag(Flag.DirectCompare);
    }

    /**
     * Checks where this comparison is a direct compare, because the class compared to is a leaf class.
     * @return {@code true} if this typecheck is a direct compare
     */
    public boolean isDirectCompare() {
        return checkFlag(Flag.DirectCompare);
    }
}
