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
    @Override public void visitArrayCopy(ArrayCopy i) { visit(i); }
    @Override public void visitArrayLength(ArrayLength i) { visit(i); }
    @Override public void visitBase(Base i) { visit(i); }
    @Override public void visitBlockBegin(BlockBegin i) { visit(i); }
    @Override public void visitBoundsCheck(BoundsCheck i) { visit(i); }
    @Override public void visitBreakpointTrap(BreakpointTrap i) {visit(i); }
    @Override public void visitCheckCast(CheckCast i) { visit(i); }
    @Override public void visitCompareOp(CompareOp i) { visit(i); }
    @Override public void visitCompareAndSwap(CompareAndSwap i) { visit(i); }
    @Override public void visitConstant(Constant i) { visit(i); }
    @Override public void visitConvert(Convert i) { visit(i); }
    @Override public void visitExceptionObject(ExceptionObject i) { visit(i); }
    @Override public void visitGoto(Goto i) { visit(i); }
    @Override public void visitIncrementRegister(IncrementRegister i) { visit(i); }
    @Override public void visitIf(If i) { visit(i); }
    @Override public void visitIfOp(IfOp i) { visit(i); }
    @Override public void visitInfopoint(Infopoint i) { visit(i); }
    @Override public void visitInstanceOf(InstanceOf i) { visit(i); }
    @Override public void visitIntrinsic(Intrinsic i) { visit(i); }
    @Override public void visitInvoke(Invoke i) { visit(i); }
    @Override public void visitLoadField(LoadField i) { visit(i); }
    @Override public void visitLoadIndexed(LoadIndexed i) { visit(i); }
    @Override public void visitLoadPointer(LoadPointer i) { visit(i); }
    @Override public void visitLoadRegister(LoadRegister i) { visit(i); }
    @Override public void visitAllocateStackHandle(StackHandle i) { visit(i); }
    @Override public void visitLocal(Local i) { visit(i); }
    @Override public void visitLogicOp(LogicOp i) { visit(i); }
    @Override public void visitLookupSwitch(LookupSwitch i) { visit(i); }
    @Override public void visitMemoryBarrier(MemoryBarrier i) { visit(i); }
    @Override public void visitMonitorAddress(MonitorAddress i) { visit(i); }
    @Override public void visitMonitorEnter(MonitorEnter i) { visit(i); }
    @Override public void visitMonitorExit(MonitorExit i) { visit(i); }
    @Override public void visitNativeCall(NativeCall i) { visit(i); }
    @Override public void visitNegateOp(NegateOp i) { visit(i); }
    @Override public void visitNewInstance(NewInstance i) { visit(i); }
    @Override public void visitNewMultiArray(NewMultiArray i) { visit(i); }
    @Override public void visitNewObjectArray(NewObjectArray i) { visit(i); }
    @Override public void visitNewObjectArrayClone(NewObjectArrayClone i) { visit(i); }
    @Override public void visitNewTypeArray(NewTypeArray i) { visit(i); }
    @Override public void visitNullCheck(NullCheck i) { visit(i); }
    @Override public void visitOsrEntry(OsrEntry i) { visit(i); }
    @Override public void visitPause(Pause i) { visit(i); }
    @Override public void visitPhi(Phi i) { visit(i); }
    @Override public void visitResolveClass(ResolveClass i) { visit(i); }
    @Override public void visitReturn(Return i) { visit(i); }
    @Override public void visitShiftOp(ShiftOp i) { visit(i); }
    @Override public void visitSignificantBit(SignificantBitOp i) { visit(i); }
    @Override public void visitStackAllocate(StackAllocate i) { visit(i); }
    @Override public void visitStoreField(StoreField i) { visit(i); }
    @Override public void visitStoreIndexed(StoreIndexed i) { visit(i); }
    @Override public void visitStorePointer(StorePointer i) { visit(i); }
    @Override public void visitStoreRegister(StoreRegister i) { visit(i); }
    @Override public void visitTableSwitch(TableSwitch i) { visit(i); }
    @Override public void visitTemplateCall(TemplateCall i) { visit(i); }
    @Override public void visitTypeEqualityCheck(TypeEqualityCheck i) { visit(i); }
    @Override public void visitThrow(Throw i) { visit(i); }
    @Override public void visitUnsafeCast(UnsafeCast i) { visit(i); }
    @Override public void visitUnsafeGetObject(UnsafeGetObject i) { visit(i); }
    @Override public void visitUnsafeGetRaw(UnsafeGetRaw i) { visit(i); }
    @Override public void visitUnsafePrefetchRead(UnsafePrefetchRead i) { visit(i); }
    @Override public void visitUnsafePrefetchWrite(UnsafePrefetchWrite i) { visit(i); }
    @Override public void visitUnsafePutObject(UnsafePutObject i) { visit(i); }
    @Override public void visitUnsafePutRaw(UnsafePutRaw i) { visit(i); }
    @Override public void visitUnsignedCompareOp(UnsignedCompareOp i) { visit(i); }
}
