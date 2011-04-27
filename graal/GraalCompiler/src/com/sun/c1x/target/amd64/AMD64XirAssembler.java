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
package com.sun.c1x.target.amd64;

import static com.sun.cri.xir.XirTemplate.GlobalFlags.*;

import java.util.*;

import com.sun.c1x.util.*;
import com.sun.cri.ci.*;
import com.sun.cri.xir.*;

/**
 * AMD64 version of {@link CiXirAssembler}.
 *
 * @author Thomas Wuerthinger
 *
 */
public class AMD64XirAssembler extends CiXirAssembler {

    @Override
    protected XirTemplate buildTemplate(String name, boolean isStub) {
        List<XirInstruction> fastPath = new ArrayList<XirInstruction>(instructions.size());
        List<XirInstruction> slowPath = new ArrayList<XirInstruction>();
        List<XirTemplate> calleeTemplates = new ArrayList<XirTemplate>();

        int flags = 0;

        if (isStub) {
            flags |= GLOBAL_STUB.mask;
        }

        List<XirInstruction> currentList = fastPath;

        XirOperand fixedRDX = null;
        XirOperand fixedRAX = null;
        XirOperand fixedRCX = null;
        XirOperand fixedRSI = null;
        XirOperand fixedRDI = null;
        HashSet<XirLabel> boundLabels = new HashSet<XirLabel>();

        for (XirInstruction i : instructions) {
            boolean appended = false;
            switch (i.op) {
                case Mov:
                    break;

                case Add:
                case Sub:
                case Div:
                case Mul:
                case Mod:
                case Shl:
                case Shr:
                case And:
                case Or:
                case Xor:
                    // Convert to two operand form
                    XirOperand xOp = i.x();
                    if (i.op == XirOp.Div || i.op == XirOp.Mod) {
                        if (fixedRDX == null) {
                            fixedRDX = createRegisterTemp("divModTemp", CiKind.Int, AMD64.rdx);
                        }
                        // Special treatment to make sure that the left input of % and / is in RAX
                        if (fixedRAX == null) {
                            fixedRAX = createRegisterTemp("divModLeftInput", CiKind.Int, AMD64.rax);
                        }
                        currentList.add(new XirInstruction(i.x().kind, XirOp.Mov, fixedRAX, i.x()));
                        xOp = fixedRAX;
                    } else {
                        if (i.result != i.x()) {
                            currentList.add(new XirInstruction(i.result.kind, XirOp.Mov, i.result, i.x()));
                            xOp = i.result;
                        }
                    }

                    XirOperand yOp = i.y();
                    if ((i.op == XirOp.Shl || i.op == XirOp.Shr) && (!(i.y() instanceof XirConstantOperand))) {
                        // Special treatment to make sure that the shift count is always in RCX
                        if (fixedRCX == null) {
                            fixedRCX = createRegisterTemp("fixedShiftCount", i.y().kind, AMD64.rcx);
                        }
                        currentList.add(new XirInstruction(i.result.kind, XirOp.Mov, fixedRCX, i.y()));
                        yOp = fixedRCX;
                    } else if (i.op == XirOp.Mul && (i.y() instanceof XirConstantOperand)) {
                        // Cannot multiply directly with a constant, so introduce a new temporary variable
                        XirOperand tempLocation = createTemp("mulTempLocation", i.y().kind);
                        currentList.add(new XirInstruction(i.result.kind, XirOp.Mov, tempLocation, i.y()));
                        yOp = tempLocation;

                    }

                    if (xOp != i.x() || yOp != i.y()) {
                        currentList.add(new XirInstruction(i.result.kind, i.op, i.result, xOp, yOp));
                        appended = true;
                    }
                    break;

                case RepeatMoveWords:
                case RepeatMoveBytes:
                    if (fixedRSI == null) {
                        fixedRSI = createRegisterTemp("fixedRSI", CiKind.Word, AMD64.rsi);
                    }
                    if (fixedRDI == null) {
                        fixedRDI = createRegisterTemp("fixedRDI", CiKind.Word, AMD64.rdi);
                    }
                    if (fixedRCX == null) {
                        fixedRCX = createRegisterTemp("fixedRCX", CiKind.Word, AMD64.rcx);
                    }
                    currentList.add(new XirInstruction(CiKind.Word, XirOp.Mov, fixedRSI, i.x()));
                    currentList.add(new XirInstruction(CiKind.Word, XirOp.Mov, fixedRDI, i.y()));
                    currentList.add(new XirInstruction(CiKind.Word, XirOp.Mov, fixedRCX, i.z()));
                    currentList.add(new XirInstruction(CiKind.Illegal, i.op, i.result, fixedRSI, fixedRDI, fixedRCX));
                    appended = true;
                    break;

                case NullCheck:
                case PointerLoad:
                case LoadEffectiveAddress:
                case PointerStore:
                case PointerLoadDisp:
                case PointerStoreDisp:
                    break;
                case PointerCAS:
                    if (fixedRAX == null) {
                        fixedRAX = createRegisterTemp("fixedRAX", CiKind.Word, AMD64.rax);
                    }
                    // x = source of cmpxch
                    // y = new value
                    // z = old value (i.e., the one compared to). Must be in RAX (and so must the result).
                    currentList.add(new XirInstruction(CiKind.Word, XirOp.Mov, fixedRAX, i.z()));
                    currentList.add(new XirInstruction(i.kind, i.op, i.result, i.x(), i.y(), fixedRAX));
                    appended = true;
                    break;
                case CallStub:
                    flags |= HAS_STUB_CALL.mask;
                    calleeTemplates.add((XirTemplate) i.extra);
                    break;
                case CallRuntime:
                    flags |= HAS_RUNTIME_CALL.mask;
                    break;
                case Jmp:
                    // jmp can be either into the snippet or to a runtime target
                    flags |= i.extra instanceof XirLabel ? HAS_CONTROL_FLOW.mask : HAS_RUNTIME_CALL.mask;
                    break;
                case Jeq:
                case Jneq:
                case Jgt:
                case Jgteq:
                case Jugteq:
                case Jlt:
                case Jlteq:
                case DecAndJumpNotZero:
                case Jbset:
                    flags |= HAS_CONTROL_FLOW.mask;
                    break;
                case Bind:
                    XirLabel label = (XirLabel) i.extra;
                    currentList = label.inline ? fastPath : slowPath;
                    assert !boundLabels.contains(label) : "label may be bound only once";
                    boundLabels.add(label);
                    break;
                case Safepoint:
                case Align:
                case StackOverflowCheck:
                case PushFrame:
                case PopFrame:
                case Push:
                case Pop:
                case Mark:
                case Nop:
                case RawBytes:
                case ShouldNotReachHere:
                    break;
                default:
                    throw Util.unimplemented("XIR operation " + i.op);
            }
            if (!appended) {
                currentList.add(i);
            }
        }
        for (XirLabel label : labels) {
            assert boundLabels.contains(label) : "label " + label.name + " is not bound!";
        }
        XirInstruction[] fp = fastPath.toArray(new XirInstruction[fastPath.size()]);
        XirInstruction[] sp = slowPath.size() > 0 ? slowPath.toArray(new XirInstruction[slowPath.size()]) : null;
        XirLabel[] xirLabels = labels.toArray(new XirLabel[labels.size()]);
        XirParameter[] xirParameters = parameters.toArray(new XirParameter[parameters.size()]);
        XirTemp[] temporaryOperands = temps.toArray(new XirTemp[temps.size()]);
        XirConstant[] constantOperands = constants.toArray(new XirConstant[constants.size()]);
        XirTemplate[] calleeTemplateArray = calleeTemplates.toArray(new XirTemplate[calleeTemplates.size()]);
        XirMark[] marksArray = marks.toArray(new XirMark[marks.size()]);
        return new XirTemplate(name, this.variableCount, this.allocateResultOperand, resultOperand, fp, sp, xirLabels, xirParameters, temporaryOperands, constantOperands, flags, calleeTemplateArray, marksArray, outgoingStackSize);
    }

    @Override
    public CiXirAssembler copy() {
        return new AMD64XirAssembler();
    }
}
