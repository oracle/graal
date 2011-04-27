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

/**
 * A default implementation of {@link ValueVisitor} that simply
 * does nothing for each value visited. This convenience class
 * simplifies implementing a value visitor that is only interested
 * in a subset of the value types.
 *
 * @author Doug Simon
 */
public class DefaultValueVisitor extends ValueVisitor {

    /**
     * All the specific visitor methods in this class call this method by default.
     *
     * @param value the value being visited
     */
    protected  void visit(Value value) {
    }

    // Checkstyle: stop
    @Override public void visitArithmeticOp(ArithmeticOp i) { visit(i); }
    @Override public void visitArrayLength(ArrayLength i) { visit(i); }
    @Override public void visitBase(Base i) { visit(i); }
    @Override public void visitBlockBegin(BlockBegin i) { visit(i); }
    @Override public void visitCheckCast(CheckCast i) { visit(i); }
    @Override public void visitCompareOp(CompareOp i) { visit(i); }
    @Override public void visitConstant(Constant i) { visit(i); }
    @Override public void visitConvert(Convert i) { visit(i); }
    @Override public void visitExceptionObject(ExceptionObject i) { visit(i); }
    @Override public void visitGoto(Goto i) { visit(i); }
    @Override public void visitIf(If i) { visit(i); }
    @Override public void visitIfOp(IfOp i) { visit(i); }
    @Override public void visitInstanceOf(InstanceOf i) { visit(i); }
    @Override public void visitInvoke(Invoke i) { visit(i); }
    @Override public void visitLoadField(LoadField i) { visit(i); }
    @Override public void visitLoadIndexed(LoadIndexed i) { visit(i); }
    @Override public void visitLocal(Local i) { visit(i); }
    @Override public void visitLogicOp(LogicOp i) { visit(i); }
    @Override public void visitLookupSwitch(LookupSwitch i) { visit(i); }
    @Override public void visitMonitorAddress(MonitorAddress i) { visit(i); }
    @Override public void visitMonitorEnter(MonitorEnter i) { visit(i); }
    @Override public void visitMonitorExit(MonitorExit i) { visit(i); }
    @Override public void visitNegateOp(NegateOp i) { visit(i); }
    @Override public void visitNewInstance(NewInstance i) { visit(i); }
    @Override public void visitNewMultiArray(NewMultiArray i) { visit(i); }
    @Override public void visitNewObjectArray(NewObjectArray i) { visit(i); }
    @Override public void visitNewTypeArray(NewTypeArray i) { visit(i); }
    @Override public void visitNullCheck(NullCheck i) { visit(i); }
    @Override public void visitPhi(Phi i) { visit(i); }
    @Override public void visitRegisterFinalizer(RegisterFinalizer i) { visit(i); }
    @Override public void visitResolveClass(ResolveClass i) { visit(i); }
    @Override public void visitReturn(Return i) { visit(i); }
    @Override public void visitShiftOp(ShiftOp i) { visit(i); }
    @Override public void visitStoreField(StoreField i) { visit(i); }
    @Override public void visitStoreIndexed(StoreIndexed i) { visit(i); }
    @Override public void visitTableSwitch(TableSwitch i) { visit(i); }
    @Override public void visitThrow(Throw i) { visit(i); }
}
