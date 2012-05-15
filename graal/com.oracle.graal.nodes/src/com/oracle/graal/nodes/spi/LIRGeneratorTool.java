/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.spi;

import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;

public abstract class LIRGeneratorTool {
    public abstract CiTarget target();

    /**
     * Checks whether the supplied constant can be used without loading it into a register
     * for most operations, i.e., for commonly used arithmetic, logical, and comparison operations.
     * @param c The constant to check.
     * @return True if the constant can be used directly, false if the constant needs to be in a register.
     */
    public abstract boolean canInlineConstant(CiConstant c);

    /**
     * Checks whether the supplied constant can be used without loading it into a register
     * for store operations, i.e., on the right hand side of a memory access.
     * @param c The constant to check.
     * @return True if the constant can be used directly, false if the constant needs to be in a register.
     */
    public abstract boolean canStoreConstant(CiConstant c);

    public abstract CiValue operand(ValueNode object);
    public abstract CiValue newVariable(CiKind kind);
    public abstract CiValue setResult(ValueNode x, CiValue operand);

    public abstract CiAddress makeAddress(LocationNode location, ValueNode object);

    public abstract CiValue emitMove(CiValue input);
    public abstract void emitMove(CiValue src, CiValue dst);
    public abstract CiValue emitLoad(CiValue loadAddress, boolean canTrap);
    public abstract void emitStore(CiValue storeAddress, CiValue input, boolean canTrap);
    public abstract CiValue emitLea(CiValue address);

    public abstract CiValue emitNegate(CiValue input);
    public abstract CiValue emitAdd(CiValue a, CiValue b);
    public abstract CiValue emitSub(CiValue a, CiValue b);
    public abstract CiValue emitMul(CiValue a, CiValue b);
    public abstract CiValue emitDiv(CiValue a, CiValue b);
    public abstract CiValue emitRem(CiValue a, CiValue b);
    public abstract CiValue emitUDiv(CiValue a, CiValue b);
    public abstract CiValue emitURem(CiValue a, CiValue b);

    public abstract CiValue emitAnd(CiValue a, CiValue b);
    public abstract CiValue emitOr(CiValue a, CiValue b);
    public abstract CiValue emitXor(CiValue a, CiValue b);

    public abstract CiValue emitShl(CiValue a, CiValue b);
    public abstract CiValue emitShr(CiValue a, CiValue b);
    public abstract CiValue emitUShr(CiValue a, CiValue b);

    public abstract CiValue emitConvert(ConvertNode.Op opcode, CiValue inputVal);
    public abstract void emitMembar(int barriers);
    public abstract void emitDeoptimizeOnOverflow(RiDeoptAction action, RiDeoptReason reason, Object deoptInfo);
    public abstract void emitDeoptimize(RiDeoptAction action, RiDeoptReason reason, Object deoptInfo, long leafGraphId);
    public abstract CiValue emitCall(Object target, CiKind result, CiKind[] arguments, boolean canTrap, CiValue... args);
    public final CiValue emitCall(CiRuntimeCall runtimeCall, boolean canTrap, CiValue... args) {
        return emitCall(runtimeCall, runtimeCall.resultKind, runtimeCall.arguments, canTrap, args);
    }

    public abstract void emitIf(IfNode i);
    public abstract void emitConditional(ConditionalNode i);
    public abstract void emitGuardCheck(BooleanNode comp, RiDeoptReason deoptReason, RiDeoptAction deoptAction, long leafGraphId);

    public abstract void emitLookupSwitch(LookupSwitchNode i);
    public abstract void emitTableSwitch(TableSwitchNode i);

    public abstract void emitInvoke(Invoke i);
    public abstract void emitRuntimeCall(RuntimeCallNode i);

    // Handling of block-end nodes still needs to be unified in the LIRGenerator.
    public abstract void visitMerge(MergeNode i);
    public abstract void visitEndNode(EndNode i);
    public abstract void visitLoopEnd(LoopEndNode i);
    public abstract void visitSafepointNode(SafepointNode i);

    public abstract void visitCompareAndSwap(CompareAndSwapNode i);

    // Functionality that is currently implemented in XIR.
    // Some of these methods will go away when lowering is done via snippets in the front end.
    // The remainder will define the contract a runtime specific backend must provide.
    public abstract void visitCheckCast(CheckCastNode i);
    public abstract void visitMonitorEnter(MonitorEnterNode i);
    public abstract void visitMonitorExit(MonitorExitNode i);
    public abstract void visitNewInstance(NewInstanceNode i);
    public abstract void visitNewTypeArray(NewTypeArrayNode i);
    public abstract void visitNewObjectArray(NewObjectArrayNode i);
    public abstract void visitNewMultiArray(NewMultiArrayNode i);
    public abstract void visitExceptionObject(ExceptionObjectNode i);
    public abstract void visitReturn(ReturnNode i);
}
