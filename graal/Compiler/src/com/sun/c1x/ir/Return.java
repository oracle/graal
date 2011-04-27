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
 * The {@code Return} class definition.
 *
 * @author Ben L. Titzer
 */
public final class Return extends BlockEnd {

    Value result;

    /**
     * Constructs a new Return instruction.
     * @param result the instruction producing the result for this return; {@code null} if this
     * is a void return
     * @param isSafepoint {@code true} if this instruction is a safepoint instruction
     */
    public Return(Value result, boolean isSafepoint) {
        super(result == null ? CiKind.Void : result.kind, null, isSafepoint);
        this.result = result;
    }

    /**
     * Gets the instruction that produces the result for the return.
     * @return the instruction producing the result
     */
    public Value result() {
        return result;
    }

    @Override
    public void inputValuesDo(ValueClosure closure) {
        if (result != null) {
            result = closure.apply(result);
        }
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitReturn(this);
    }

    @Override
    public void print(LogStream out) {
        if (result == null) {
            out.print("return");
        } else {
            out.print(kind.typeChar).print("return ").print(result);
        }
    }
}
