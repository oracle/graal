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
package com.oracle.max.graal.compiler.nodes.spi;

import com.oracle.max.graal.compiler.nodes.base.*;
import com.oracle.max.graal.compiler.nodes.calc.*;
import com.oracle.max.graal.compiler.nodes.extended.*;
import com.oracle.max.graal.compiler.nodes.java.*;

/**
 * The {@link ValueVisitor} implements one half of the visitor
 * pattern for {@linkplain ValueNode IR values}, allowing clients to implement functionality
 * depending on the type of an value without doing type tests.
 */
public abstract class ValueVisitor {
    // Checkstyle: stop
    public abstract void visitArithmetic(ArithmeticNode i);
    public abstract void visitArrayLength(ArrayLengthNode i);
    public abstract void visitMerge(MergeNode i);
    public abstract void visitCheckCast(CheckCastNode i);
    public abstract void visitNormalizeCompare(NormalizeCompareNode i);
    public abstract void visitConstant(ConstantNode i);
    public abstract void visitConvert(ConvertNode i);
    public abstract void visitConditional(ConditionalNode i);
    public abstract void visitExceptionObject(ExceptionObjectNode i);
    public abstract void visitEndNode(EndNode i);
    public abstract void visitFrameState(FrameState i);
    public abstract void visitAnchor(AnchorNode i);
    public abstract void visitIf(IfNode i);
    public abstract void visitInvoke(InvokeNode i);
    public abstract void visitLoadField(LoadFieldNode i);
    public abstract void visitLoadIndexed(LoadIndexedNode i);
    public abstract void visitLocal(LocalNode i);
    public abstract void visitLogic(LogicNode i);
    public abstract void visitLookupSwitch(LookupSwitchNode i);
    public abstract void visitMemoryRead(ReadNode i);
    public abstract void visitMemoryWrite(WriteNode i);
    public abstract void visitMonitorAddress(MonitorAddressNode monitorAddress);
    public abstract void visitMonitorEnter(MonitorEnterNode i);
    public abstract void visitMonitorExit(MonitorExitNode i);
    public abstract void visitNegate(NegateNode i);
    public abstract void visitNewInstance(NewInstanceNode i);
    public abstract void visitNewMultiArray(NewMultiArrayNode i);
    public abstract void visitNewObjectArray(NewObjectArrayNode i);
    public abstract void visitNewTypeArray(NewTypeArrayNode i);
    public abstract void visitFixedGuard(FixedGuardNode fixedGuard);
    public abstract void visitPhi(PhiNode i);
    public abstract void visitRegisterFinalizer(RegisterFinalizerNode i);
    public abstract void visitReturn(ReturnNode i);
    public abstract void visitShift(ShiftNode i);
    public abstract void visitStoreField(StoreFieldNode i);
    public abstract void visitStoreIndexed(StoreIndexedNode i);
    public abstract void visitTableSwitch(TableSwitchNode i);
    public abstract void visitDeoptimize(DeoptimizeNode deoptimize);
    public abstract void visitUnwind(UnwindNode unwind);
    public abstract void visitLoopBegin(LoopBeginNode loopBegin);
    public abstract void visitLoopEnd(LoopEndNode loopEnd);
    public abstract void visitValueAnchor(ValueAnchorNode valueAnchor);
    public abstract void visitGuardNode(GuardNode guardNode);
    public abstract void visitMathIntrinsic(MathIntrinsicNode node);
}
