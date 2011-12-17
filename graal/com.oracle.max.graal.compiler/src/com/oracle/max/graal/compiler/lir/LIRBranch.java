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
package com.oracle.max.graal.compiler.lir;

import com.oracle.max.graal.nodes.calc.*;
import com.sun.cri.ci.*;

public abstract class LIRBranch extends LIRInstruction {

    /**
     * The condition when this branch is taken, or {@code null} if it is an unconditional branch.
     */
    protected Condition cond;

    /**
     * For floating point branches only. True when the branch should be taken when the comparison is unordered.
     */
    protected boolean unorderedIsTrue;

    /**
     * The target of this branch.
     */
    protected LabelRef destination;


    public LIRBranch(LIROpcode code, Condition cond, boolean unorderedIsTrue, LabelRef destination, LIRDebugInfo info) {
        super(code, CiValue.IllegalValue, info, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS);
        this.cond = cond;
        this.unorderedIsTrue = unorderedIsTrue;
        this.destination = destination;
    }

    public LabelRef destination() {
        return destination;
    }

    public void negate(LabelRef newDestination) {
        destination = newDestination;
        cond = cond.negate();
        unorderedIsTrue = !unorderedIsTrue;
    }
}
