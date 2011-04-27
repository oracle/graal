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
package com.sun.c1x.opt;

import com.sun.c1x.ir.*;
import com.sun.c1x.value.*;
import com.sun.c1x.graph.IR;

/**
 * This class allows instructions to be substituted within an IR graph. It allows
 * registering substitutions and iterates over the instructions of a program and replaces
 * the occurrence of each instruction with its substitution, if it has one.
 *
 * @author Ben L. Titzer
 */
public final class InstructionSubstituter implements BlockClosure, ValueClosure {

    final IR ir;
    boolean hasSubstitution;

    public InstructionSubstituter(IR ir) {
        this.ir = ir;
    }

    public void apply(BlockBegin block) {
        Instruction last = null;
        if (block.exceptionHandlerStates() != null) {
            for (FrameState s : block.exceptionHandlerStates()) {
                s.valuesDo(this);
            }
        }
        for (Instruction n = block; n != null; n = last.next()) {
            n.allValuesDo(this);
            if (n.subst != null && last != null) {
                // this instruction has a substitution, skip it
                last.resetNext(n.next());
            } else {
                last = n;
            }
        }
    }

    public void finish() {
        if (hasSubstitution) {
            ir.startBlock.iterateAnyOrder(this, false);
        }
    }

    public boolean hasSubst(Value i) {
        return i.subst != null;
    }

    public void setSubst(Value i, Value n) {
        if (i == n) {
            i.subst = null;
        } else {
            hasSubstitution = true;
            i.subst = n;
        }
    }

    public Value getSubst(Value i) {
        Value p = i;
        while (true) {
            if (p.subst == null) {
                break;
            }
            p = p.subst;
        }
        return p;
    }

    public Value apply(Value i) {
        if (i != null) {
            return getSubst(i);
        }
        return i;
    }
}
