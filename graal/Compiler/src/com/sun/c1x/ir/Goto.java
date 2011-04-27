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

/**
 * The {@code Goto} instruction represents the end of a block with an unconditional jump to another block.
 *
 * @author Ben L. Titzer
 */
public final class Goto extends BlockEnd {

    /**
     * Constructs a new Goto instruction.
     * @param succ the successor block of the goto
     * @param stateAfter the frame state at the end of this block
     * @param isSafepoint {@code true} if the goto should be considered a safepoint (e.g. backward branch)
     */
    public Goto(BlockBegin succ, FrameState stateAfter, boolean isSafepoint) {
        super(CiKind.Illegal, stateAfter, isSafepoint);
        successors.add(succ);
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitGoto(this);
    }

    @Override
    public void print(LogStream out) {
        out.print("goto B").print(defaultSuccessor().blockID);
        if (isSafepoint()) {
            out.print(" (safepoint)");
        }
    }
}
