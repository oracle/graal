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

import com.sun.c1x.value.*;

/**
 * The {@link ValueVisitor} implements one half of the visitor
 * pattern for {@linkplain Value IR values}, allowing clients to implement functionality
 * depending on the type of an value without doing type tests.
 */
public abstract class ValueVisitor {
    // Checkstyle: stop
    public abstract void visitArithmeticOp(ArithmeticOp i);
    public abstract void visitArrayLength(ArrayLength i);
    public abstract void visitBlockBegin(BlockBegin i);
    public abstract void visitCheckCast(CheckCast i);
    public abstract void visitCompareOp(CompareOp i);
    public abstract void visitConstant(Constant i);
    public abstract void visitConvert(Convert i);
    public abstract void visitExceptionObject(ExceptionObject i);
    public abstract void visitFrameState(FrameState i);
    public abstract void visitGoto(Goto i);
    public abstract void visitIf(If i);
    public abstract void visitIfOp(IfOp i);
    public abstract void visitInstanceOf(InstanceOf i);
    public abstract void visitInvoke(Invoke i);
    public abstract void visitLoadField(LoadField i);
    public abstract void visitLoadIndexed(LoadIndexed i);
    public abstract void visitLocal(Local i);
    public abstract void visitLogicOp(LogicOp i);
    public abstract void visitLookupSwitch(LookupSwitch i);
    public abstract void visitMonitorAddress(MonitorAddress monitorAddress);
    public abstract void visitMonitorEnter(MonitorEnter i);
    public abstract void visitMonitorExit(MonitorExit i);
    public abstract void visitNegateOp(NegateOp i);
    public abstract void visitNewInstance(NewInstance i);
    public abstract void visitNewMultiArray(NewMultiArray i);
    public abstract void visitNewObjectArray(NewObjectArray i);
    public abstract void visitNewTypeArray(NewTypeArray i);
    public abstract void visitNullCheck(NullCheck i);
    public abstract void visitPhi(Phi i);
    public abstract void visitRegisterFinalizer(RegisterFinalizer i);
    public abstract void visitReturn(Return i);
    public abstract void visitShiftOp(ShiftOp i);
    public abstract void visitStoreField(StoreField i);
    public abstract void visitStoreIndexed(StoreIndexed i);
    public abstract void visitTableSwitch(TableSwitch i);
    public abstract void visitThrow(Throw i);
    public abstract void visitDeoptimize(Deoptimize deoptimize);
    public abstract void visitExceptionDispatch(ExceptionDispatch exceptionDispatch);
    public abstract void visitUnwind(Unwind unwind);
}
