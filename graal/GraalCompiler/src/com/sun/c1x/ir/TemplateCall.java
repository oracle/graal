/*
 * Copyright (c) 2010, 2011, Oracle and/or its affiliates. All rights reserved.
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
import com.sun.cri.bytecode.*;
import com.sun.cri.ci.*;

/**
 * Represents a {@linkplain Bytecodes#TEMPLATE_CALL template call}.
 *
 * @author Doug Simon
 */
public final class TemplateCall extends Instruction {

    /**
     * The address to call (null implies a direct call that will be patched).
     */
    private Value address;

    private Value receiver;

    public TemplateCall(CiKind returnKind, Value address, Value receiver) {
        super(returnKind.stackKind());
        this.address = address;
        this.receiver = receiver;
        setFlag(Flag.LiveSideEffect); // ensure this instruction is not eliminated
    }

    @Override
    public boolean canTrap() {
        return true;
    }

    public Value address() {
        return address;
    }

    public Value receiver() {
        return receiver;
    }

    @Override
    public void inputValuesDo(ValueClosure closure) {
        if (address != null) {
            address = closure.apply(address);
        }
        if (receiver != null) {
            receiver = closure.apply(receiver);
        }
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitTemplateCall(this);
    }

    @Override
    public void print(LogStream out) {
        out.print("template_call").print('(');
        if (address != null) {
            out.print(address);
            if (receiver != null) {
                out.print(", ").print(receiver);
            }
        } else if (receiver != null) {
            out.print(receiver);
            out.print(')');
        }
    }
}
