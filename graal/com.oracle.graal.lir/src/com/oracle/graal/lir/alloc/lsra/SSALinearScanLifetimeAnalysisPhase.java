/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.alloc.lsra;

import java.util.*;

import jdk.internal.jvmci.debug.*;
import jdk.internal.jvmci.meta.*;

import com.oracle.graal.lir.*;
import com.oracle.graal.lir.LIRInstruction.OperandFlag;
import com.oracle.graal.lir.LIRInstruction.OperandMode;
import com.oracle.graal.lir.StandardOp.LabelOp;
import com.oracle.graal.lir.alloc.lsra.Interval.RegisterPriority;
import com.oracle.graal.lir.ssa.*;

public class SSALinearScanLifetimeAnalysisPhase extends LinearScanLifetimeAnalysisPhase {

    SSALinearScanLifetimeAnalysisPhase(LinearScan linearScan) {
        super(linearScan);
    }

    @Override
    void addRegisterHint(final LIRInstruction op, final Value targetValue, OperandMode mode, EnumSet<OperandFlag> flags, final boolean hintAtDef) {
        super.addRegisterHint(op, targetValue, mode, flags, hintAtDef);

        if (hintAtDef && op instanceof LabelOp) {
            LabelOp label = (LabelOp) op;

            Interval to = allocator.getOrCreateInterval((AllocatableValue) targetValue);

            SSAUtils.forEachPhiRegisterHint(allocator.ir, allocator.blockForId(label.id()), label, targetValue, mode, (ValueConsumer) (registerHint, valueMode, valueFlags) -> {
                if (LinearScan.isVariableOrRegister(registerHint)) {
                    Interval from = allocator.getOrCreateInterval((AllocatableValue) registerHint);

                    setHint(op, to, from);
                    setHint(op, from, to);
                }
            });
        }
    }

    private static void setHint(final LIRInstruction op, Interval target, Interval source) {
        Interval currentHint = target.locationHint(false);
        if (currentHint == null || currentHint.from() > target.from()) {
            /*
             * Update hint if there was none or if the hint interval starts after the hinted
             * interval.
             */
            target.setLocationHint(source);
            if (Debug.isLogEnabled()) {
                Debug.log("operation at opId %d: added hint from interval %d to %d", op.id(), source.operandNumber, target.operandNumber);
            }
        }
    }

    @Override
    protected RegisterPriority registerPriorityOfOutputOperand(LIRInstruction op) {
        if (op instanceof LabelOp) {
            LabelOp label = (LabelOp) op;
            if (label.isPhiIn()) {
                return RegisterPriority.None;
            }
        }
        return super.registerPriorityOfOutputOperand(op);
    }
}
