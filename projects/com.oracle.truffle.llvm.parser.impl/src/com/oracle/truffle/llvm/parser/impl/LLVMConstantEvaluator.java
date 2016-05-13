/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.parser.impl;

import com.intel.llvm.ireditor.lLVM_IR.Constant;
import com.intel.llvm.ireditor.lLVM_IR.ConstantExpression_binary;
import com.intel.llvm.ireditor.lLVM_IR.ConstantExpression_convert;
import com.intel.llvm.ireditor.lLVM_IR.GlobalValueRef;
import com.intel.llvm.ireditor.lLVM_IR.GlobalVariable;
import com.intel.llvm.ireditor.lLVM_IR.SimpleConstant;
import com.intel.llvm.ireditor.lLVM_IR.ValueRef;
import com.intel.llvm.ireditor.types.ResolvedAnyIntegerType;
import com.intel.llvm.ireditor.types.ResolvedType;
import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException.UnsupportedReason;
import com.oracle.truffle.llvm.types.LLVMAddress;

/*
 * http://llvm.org/docs/LangRef.html#constant-expressions
 */
public final class LLVMConstantEvaluator {

    private static LLVMParserRuntime runtime;

    public static Object evaluateConstant(LLVMParserRuntime curRuntime, Constant index) {
        LLVMConstantEvaluator.runtime = curRuntime;
        ResolvedType type = curRuntime.resolve(index);
        if (!(type instanceof ResolvedAnyIntegerType)) {
            throw new LLVMUnsupportedException(UnsupportedReason.CONSTANT_EXPRESSION);
        }
        Object evaluatedConst = evaluateConstant(index);
        return evaluatedConst;
    }

    private static Object evaluateConstant(Constant index) throws AssertionError {
        if (index instanceof SimpleConstant) {
            return evaluateSimpleConstant((SimpleConstant) index);
        } else if (index instanceof ConstantExpression_binary) {
            return evaluateBinary((ConstantExpression_binary) index);
        } else if (index instanceof ConstantExpression_convert) {
            return evaluateConvert((ConstantExpression_convert) index);
        } else if (index instanceof GlobalValueRef) {
            return evaluateGlobalValueRef((GlobalValueRef) index);
        } else if (index.getRef() instanceof GlobalVariable) {
            return evaluateGlobalVariable((GlobalVariable) index.getRef());
        } else {
            throw new LLVMUnsupportedException(UnsupportedReason.CONSTANT_EXPRESSION);
        }
    }

    private static Object evaluateGlobalVariable(GlobalVariable ref) {
        Object addr = runtime.getGlobalAddress(ref);
        assert addr != null;
        return addr;
    }

    private static int evaluateGlobalValueRef(@SuppressWarnings("unused") GlobalValueRef index) {
        // TODO: we have to know the global address already here
        throw new LLVMUnsupportedException(UnsupportedReason.CONSTANT_EXPRESSION);
    }

    private static Object evaluateConvert(ConstantExpression_convert index) {
        return evaluateConstant(index.getConstant().getConstant());
    }

    private static Object evaluateBinary(ConstantExpression_binary index) {
        Object left = evaluateValueRef(index.getOp1().getRef());
        Object right = evaluateValueRef(index.getOp2().getRef());
        switch (index.getOpcode()) {
            case "add":
                return add(left, right);
            case "sub":
                return sub(left, right);
            case "and":
                return and(left, right);
            default:
                throw new LLVMUnsupportedException(UnsupportedReason.CONSTANT_EXPRESSION);
        }
    }

    private static Object sub(Object left, Object right) {
        if (isNumber(left) && isNumber(right)) {
            return asNumber(left) - asNumber(right);
        } else if (isAddress(left) && isNumber(right)) {
            return asLongAddress(left) - asNumber(right);
        } else if (isNumber(left) && isAddress(right)) {
            return asNumber(left) - asLongAddress(right);
        } else {
            throw new AssertionError(left + " " + right);
        }
    }

    private static boolean isAddress(Object obj) {
        return obj instanceof LLVMAddress;
    }

    private static long asLongAddress(Object obj) {
        return ((LLVMAddress) obj).getVal();
    }

    private static boolean isNumber(Object obj) {
        return obj instanceof Number;
    }

    private static long asNumber(Object number) {
        return ((Number) number).longValue();
    }

    private static Object add(Object left, Object right) {
        if (isNumber(left) && isNumber(right)) {
            return asNumber(left) + asNumber(right);
        } else if (isAddress(left) && isNumber(right)) {
            return asLongAddress(left) + asNumber(right);
        } else if (isNumber(left) && isAddress(right)) {
            return asNumber(left) + asLongAddress(right);
        } else {
            throw new AssertionError(left + " " + right);
        }
    }

    private static Object and(Object left, Object right) {
        if (isNumber(left) && isNumber(right)) {
            return asNumber(left) & asNumber(right);
        } else if (isAddress(left) && isNumber(right)) {
            return asLongAddress(left) & asNumber(right);
        } else if (isNumber(left) && isAddress(right)) {
            return asNumber(left) & asLongAddress(right);
        } else {
            throw new AssertionError(left + " " + right);
        }
    }

    private static Object evaluateValueRef(ValueRef ref) {
        if (ref instanceof GlobalValueRef) {
            return evaluateConstant(((GlobalValueRef) ref).getConstant());
        }
        return 0;
    }

    private static Object evaluateSimpleConstant(SimpleConstant simpleConstant) {
        String value = simpleConstant.getValue();
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return Long.parseLong(value);
        }
    }

}
