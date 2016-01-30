/*
Copyright (c) 2013, Intel Corporation

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice,
      this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright notice,
      this list of conditions and the following disclaimer in the documentation
      and/or other materials provided with the distribution.
    * Neither the name of Intel Corporation nor the names of its contributors
      may be used to endorse or promote products derived from this software
      without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.intel.llvm.ireditor.constants;

import org.eclipse.emf.ecore.EObject;

import com.intel.llvm.ireditor.lLVM_IR.Constant;
import com.intel.llvm.ireditor.lLVM_IR.ConstantExpression_binary;
import com.intel.llvm.ireditor.lLVM_IR.ConstantExpression_compare;
import com.intel.llvm.ireditor.lLVM_IR.ConstantExpression_convert;
import com.intel.llvm.ireditor.lLVM_IR.ConstantExpression_extractelement;
import com.intel.llvm.ireditor.lLVM_IR.ConstantExpression_select;
import com.intel.llvm.ireditor.lLVM_IR.GlobalValueRef;
import com.intel.llvm.ireditor.lLVM_IR.SimpleConstant;
import com.intel.llvm.ireditor.lLVM_IR.TypedConstant;
import com.intel.llvm.ireditor.lLVM_IR.TypedValue;
import com.intel.llvm.ireditor.lLVM_IR.ValueRef;
import com.intel.llvm.ireditor.lLVM_IR.VectorConstant;
import com.intel.llvm.ireditor.lLVM_IR.util.LLVM_IRSwitch;

public class ConstantResolver extends LLVM_IRSwitch<Integer> {
    public Integer getInteger(EObject object) {
        return doSwitch(object);
    }

// @Override
// public Integer caseValueRef(ValueRef object) {
// if (object instanceof GlobalValueRef == false) return null;
// Constant c = ((GlobalValueRef)object).getConstant();
// if (c == null) return null;
// return doSwitch(c);
// }

    @Override
    public Integer caseGlobalValueRef(GlobalValueRef object) {
        Constant c = object.getConstant();
        if (c == null) {
            return null;
        }
        return doSwitch(c);
    }

    @Override
    public Integer caseTypedValue(TypedValue object) {
        return doSwitch(object.getRef());
    }

    @Override
    public Integer caseTypedConstant(TypedConstant object) {
        return doSwitch(object.getValue());
    }

    @Override
    public Integer caseSimpleConstant(SimpleConstant object) {
        try {
            return Integer.parseInt(object.getValue());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public Integer caseConstantExpression_binary(ConstantExpression_binary object) {
        Integer op1 = doSwitch(object.getOp1());
        Integer op2 = doSwitch(object.getOp2());
        if (op1 == null || op2 == null) {
            return null;
        }

        String opcode = object.getOpcode();

        if (opcode.equals("add")) {
            return op1 + op2;
        } else if (opcode.equals("sub")) {
            return op1 - op2;
        } else if (opcode.equals("mul")) {
            return op1 * op2;
        } else if (opcode.equals("udiv")) {
            return (int) ((op1 & 0xFFFFFFFFL) / (op2 & 0xFFFFFFFFL));
        } else if (opcode.equals("sdiv")) {
            return op1 / op2;
        } else if (opcode.equals("shl")) {
            return op1 << op2;
        } else if (opcode.equals("lshr")) {
            return op1 >>> op2;
        } else if (opcode.equals("ashr")) {
            return op1 >> op2;
        } else if (opcode.equals("and")) {
            return op1 & op2;
        } else if (opcode.equals("or")) {
            return op1 | op2;
        } else if (opcode.equals("xor")) {
            return op1 ^ op2;
        } else {
            return null;
        }
    }

    @Override
    public Integer caseConstantExpression_compare(ConstantExpression_compare object) {
        Integer op1 = doSwitch(object.getOp1());
        Integer op2 = doSwitch(object.getOp2());
        if (op1 == null || op2 == null) {
            return null;
        }
        if (object.getOpcode().equals("icmp")) {
            return op1.equals(op2) ? 1 : 0;
        }
        return null;
    }

    @Override
    public Integer caseConstantExpression_convert(ConstantExpression_convert object) {
        // This could be partially implemented, but:
        // 1. There's an inherent problem here that global addresses are considered constants
        // but are unknowable here.
        // 2. Supporting things like fptosi here means this analysis should track other kinds
        // of constants, not just integers.
        // So there's a TODO to implement it, but it will be partial anyway.
        return null;
    }

    @Override
    public Integer caseConstantExpression_extractelement(ConstantExpression_extractelement object) {
        Integer index = doSwitch(object.getIndex());
        if (index == null) {
            return null;
        }

        ValueRef ref = object.getVector().getRef();
        if (!(ref instanceof GlobalValueRef)) {
            return null;
        }
        GlobalValueRef gref = (GlobalValueRef) ref;
        Constant c = gref.getConstant();
        if (!(c instanceof VectorConstant)) {
            return null;
        }
        VectorConstant vector = (VectorConstant) c;

        return doSwitch(vector.getList().getTypedConstants().get(index));
    }

    @Override
    public Integer caseConstantExpression_select(ConstantExpression_select object) {
        Integer cond = doSwitch(object.getCondition());
        if (cond == null) {
            return null;
        }
        return doSwitch(cond == 1 ? object.getOp1() : object.getOp2());
    }

    // TODO implement the other constant expression types

}
