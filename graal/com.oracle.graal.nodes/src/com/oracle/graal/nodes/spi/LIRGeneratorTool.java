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

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;

public abstract class LIRGeneratorTool {

    public abstract TargetDescription target();

    public abstract CodeCacheProvider getRuntime();

    /**
     * Checks whether the supplied constant can be used without loading it into a register for most
     * operations, i.e., for commonly used arithmetic, logical, and comparison operations.
     * 
     * @param c The constant to check.
     * @return True if the constant can be used directly, false if the constant needs to be in a
     *         register.
     */
    public abstract boolean canInlineConstant(Constant c);

    /**
     * Checks whether the supplied constant can be used without loading it into a register for store
     * operations, i.e., on the right hand side of a memory access.
     * 
     * @param c The constant to check.
     * @return True if the constant can be used directly, false if the constant needs to be in a
     *         register.
     */
    public abstract boolean canStoreConstant(Constant c);

    public abstract RegisterAttributes attributes(Register register);

    public abstract Value operand(ValueNode object);

    public abstract Value newVariable(Kind kind);

    public abstract Value setResult(ValueNode x, Value operand);

    public abstract Address makeAddress(LocationNode location, ValueNode object);

    public abstract Value emitMove(Value input);

    public abstract void emitMove(Value src, Value dst);

    public abstract Value emitLoad(Value loadAddress, boolean canTrap);

    public abstract void emitStore(Value storeAddress, Value input, boolean canTrap);

    public abstract Value emitLea(Value address);

    public abstract Value emitNegate(Value input);

    public abstract Value emitAdd(Value a, Value b);

    public abstract Value emitSub(Value a, Value b);

    public abstract Value emitMul(Value a, Value b);

    public abstract Value emitDiv(Value a, Value b);

    public abstract Value emitRem(Value a, Value b);

    public abstract Value emitUDiv(Value a, Value b);

    public abstract Value emitURem(Value a, Value b);

    public abstract Value emitAnd(Value a, Value b);

    public abstract Value emitOr(Value a, Value b);

    public abstract Value emitXor(Value a, Value b);

    public abstract Value emitShl(Value a, Value b);

    public abstract Value emitShr(Value a, Value b);

    public abstract Value emitUShr(Value a, Value b);

    public abstract Value emitConvert(ConvertNode.Op opcode, Value inputVal);

    public abstract void emitMembar(int barriers);

    public abstract void emitDeoptimizeOnOverflow(DeoptimizationAction action, DeoptimizationReason reason, Object deoptInfo);

    public abstract void emitDeoptimize(DeoptimizationAction action, DeoptimizationReason reason, Object deoptInfo);

    public abstract Value emitCall(RuntimeCallTarget callTarget, CallingConvention cc, boolean canTrap, Value... args);

    public abstract void emitIf(IfNode i);

    public abstract void emitConditional(ConditionalNode i);

    public abstract void emitGuardCheck(BooleanNode comp, DeoptimizationReason deoptReason, DeoptimizationAction deoptAction, boolean negated);

    public abstract void emitSwitch(SwitchNode i);

    public abstract void emitInvoke(Invoke i);

    public abstract void visitRuntimeCall(RuntimeCallNode i);

    // Handling of block-end nodes still needs to be unified in the LIRGenerator.
    public abstract void visitMerge(MergeNode i);

    public abstract void visitEndNode(EndNode i);

    public abstract void visitLoopEnd(LoopEndNode i);

    public abstract void visitCompareAndSwap(CompareAndSwapNode i);

    // These methods define the contract a runtime specific backend must provide.
    public abstract void visitExceptionObject(ExceptionObjectNode i);

    public abstract void visitReturn(ReturnNode i);

    public abstract void visitSafepointNode(SafepointNode i);

    public abstract void visitBreakpointNode(BreakpointNode i);
}
