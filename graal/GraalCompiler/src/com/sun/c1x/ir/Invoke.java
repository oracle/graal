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
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * The {@code Invoke} instruction represents all kinds of method calls.
 *
 * @author Ben L. Titzer
 */
public final class Invoke extends StateSplit {

    public final int opcode;
    public final Value[] arguments;
    public final RiMethod target;
    public final RiType returnType;

    /**
     * Constructs a new Invoke instruction.
     *
     * @param opcode the opcode of the invoke
     * @param result the result type
     * @param args the list of instructions producing arguments to the invocation, including the receiver object
     * @param isStatic {@code true} if this call is static (no receiver object)
     * @param target the target method being called
     * @param stateBefore the state before executing the invocation
     */
    public Invoke(int opcode, CiKind result, Value[] args, boolean isStatic, RiMethod target, RiType returnType, FrameState stateBefore) {
        super(result, stateBefore);
        this.opcode = opcode;
        this.arguments = args;
        this.target = target;
        this.returnType = returnType;
        if (isStatic) {
            setFlag(Flag.IsStatic);
            eliminateNullCheck();
        } else if (args[0].isNonNull() || args[0].kind.isWord()) {
            eliminateNullCheck();
        }
    }

    /**
     * Gets the opcode of this invoke instruction.
     * @return the opcode
     */
    public int opcode() {
        return opcode;
    }

    /**
     * Checks whether this is an invocation of a static method.
     * @return {@code true} if the invocation is a static invocation
     */
    public boolean isStatic() {
        return checkFlag(Flag.IsStatic);
    }

    @Override
    public RiType declaredType() {
        return returnType;
    }

    /**
     * Gets the instruction that produces the receiver object for this invocation, if any.
     * @return the instruction that produces the receiver object for this invocation if any, {@code null} if this
     *         invocation does not take a receiver object
     */
    public Value receiver() {
        assert !isStatic();
        return arguments[0];
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

    /**
     * Checks whether this instruction can trap.
     * @return {@code true}, conservatively assuming the called method may throw an exception
     */
    @Override
    public boolean canTrap() {
        return true;
    }

    /**
     * Checks whether this invocation has a receiver object.
     * @return {@code true} if this invocation has a receiver object; {@code false} otherwise, if this is a
     *         static call
     */
    public boolean hasReceiver() {
        return !isStatic();
    }

    @Override
    public void inputValuesDo(ValueClosure closure) {
        for (int i = 0; i < arguments.length; i++) {
            Value arg = arguments[i];
            if (arg != null) {
                arguments[i] = closure.apply(arg);
                assert arguments[i] != null;
            }
        }
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitInvoke(this);
    }

    public CiKind[] signature() {
        CiKind receiver = isStatic() ? null : target.holder().kind();
        return Util.signatureToKinds(target.signature(), receiver);
    }

    @Override
    public void print(LogStream out) {
        int argStart = 0;
        if (hasReceiver()) {
            out.print(receiver()).print('.');
            argStart = 1;
        }

        RiMethod target = target();
        out.print(target.name()).print('(');
        Value[] arguments = arguments();
        for (int i = argStart; i < arguments.length; i++) {
            if (i > argStart) {
                out.print(", ");
            }
            out.print(arguments[i]);
        }
        out.print(CiUtil.format(") [method: %H.%n(%p):%r]", target, false));
    }
}
