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

import com.sun.c1x.*;
import com.sun.c1x.debug.*;
import com.sun.c1x.value.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * The {@code Intrinsic} instruction represents a call to a JDK method
 * that has been made {@linkplain C1XIntrinsic intrinsic}.
 *
 * @author Ben L. Titzer
 * @see C1XIntrinsic
 */
public final class Intrinsic extends StateSplit {

    final C1XIntrinsic intrinsic;
    final RiMethod target;
    final Value[] arguments;
    final boolean canTrap;

    /**
     * Creates a new Intrinsic instruction.
     * @param kind the result type of the instruction
     * @param intrinsic the actual intrinsic
     * @param target the method for this intrinsic
     * @param args the arguments to the call (including the receiver object)
     * @param isStatic {@code true} if this method is static
     * @param stateBefore the lock stack
     * @param preservesState {@code true} if the implementation of this intrinsic preserves register state
     * @param canTrap {@code true} if this intrinsic can cause a trap
     */
    public Intrinsic(CiKind kind, C1XIntrinsic intrinsic, RiMethod target, Value[] args, boolean isStatic,
                     FrameState stateBefore, boolean preservesState, boolean canTrap) {
        super(kind, stateBefore);
        this.intrinsic = intrinsic;
        int nonNullArgs = 0;
        for (int i = 0; i < args.length; ++i) {
            if (args[i] != null) {
                nonNullArgs++;
            }
        }
        this.arguments = new Value[nonNullArgs];
        int z = 0;
        for (int i = 0; i < args.length; ++i) {
            if (args[i] != null) {
                arguments[z++] = args[i];
            }
        }
        this.target = target;
        initFlag(Flag.IsStatic, isStatic);
        // Preserves state means that the intrinsic preserves register state across all cases,
        // including slow cases--even if it causes a trap. If so, it can still be a candidate
        // for load elimination and common subexpression elimination
        initFlag(Flag.PreservesState, preservesState);
        this.canTrap = canTrap;
        if (!isStatic && args[0].isNonNull()) {
            eliminateNullCheck();
        }
    }

    /**
     * Gets the intrinsic represented by this instruction.
     * @return the intrinsic
     */
    public C1XIntrinsic intrinsic() {
        return intrinsic;
    }

    /**
     * Gets the target method for this invocation instruction.
     * @return the target method
     */
    public RiMethod target() {
        return target;
    }

    /**
     * Gets the list of instructions that produce input for this instruction.
     * @return the list of instructions that produce input
     */
    public Value[] arguments() {
        return arguments;
    }

    public boolean isStatic() {
        return checkFlag(Flag.IsStatic);
    }

    /**
     * Checks whether this intrinsic has a receiver object.
     * @return {@code true} if this intrinsic has a receiver object
     */
    public boolean hasReceiver() {
        return !isStatic();
    }

    /**
     * Gets the instruction which produces the receiver object for this intrinsic.
     * @return the instruction producing the receiver object
     */
    public Value receiver() {
        assert !isStatic();
        return arguments[0];
    }

    /**
     * Checks whether this intrinsic preserves the state of registers across all cases.
     * @return {@code true} if this intrinsic always preserves register state
     */
    public boolean preservesState() {
        return checkFlag(Flag.PreservesState);
    }

    /**
     * Checks whether this intrinsic can cause a trap.
     * @return {@code true} if this intrinsic can cause a trap
     */
    @Override
    public boolean canTrap() {
        return canTrap;
    }

    @Override
    public void inputValuesDo(ValueClosure closure) {
        for (int i = 0; i < arguments.length; i++) {
            arguments[i] = closure.apply(arguments[i]);
        }
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitIntrinsic(this);
    }

    public Value argumentAt(int i) {
        return arguments[i];
    }

    public int numberOfArguments() {
        return arguments.length;
    }

    @Override
    public void print(LogStream out) {
        out.print(intrinsic().className).print('.').print(intrinsic().name()).print('(');
        for (int i = 0; i < arguments().length; i++) {
          if (i > 0) {
              out.print(", ");
          }
          out.print(arguments()[i]);
        }
        out.print(')');
    }
}
