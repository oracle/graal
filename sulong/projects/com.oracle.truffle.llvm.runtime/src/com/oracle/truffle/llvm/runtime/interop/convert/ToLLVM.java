/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.interop.convert;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMTypesGen;

/**
 * Converts value to the target type. For fast path code, targetType will typically be constant in
 * which case this node has zero overhead.
 */
@GenerateUncached
public abstract class ToLLVM extends LLVMNode {

    public abstract Object executeWithType(Object value, LLVMInteropType.Structured type, ForeignToLLVMType targetType);

    @Specialization(guards = "isI1(targetType)")
    static boolean toI1(Object value, LLVMInteropType.Structured type, ForeignToLLVMType targetType,
                    @Cached("createToI1()") ForeignToLLVM toI1) {
        return LLVMTypesGen.asBoolean(toI1.executeWithForeignToLLVMType(value, type, targetType));
    }

    @Specialization(guards = "isI8(targetType)")
    static byte toI8(Object value, LLVMInteropType.Structured type, @SuppressWarnings("unused") ForeignToLLVMType targetType,
                    @Cached("createToI8()") ForeignToLLVM toI8) {
        return LLVMTypesGen.asByte(toI8.executeWithForeignToLLVMType(value, type, targetType));
    }

    @Specialization(guards = "isI16(targetType)")
    static short toI16(Object value, LLVMInteropType.Structured type, @SuppressWarnings("unused") ForeignToLLVMType targetType,
                    @Cached("createToI16()") ForeignToLLVM toI16) {
        return LLVMTypesGen.asShort(toI16.executeWithForeignToLLVMType(value, type, targetType));
    }

    @Specialization(guards = "isI32(targetType)")
    static int toI32(Object value, LLVMInteropType.Structured type, @SuppressWarnings("unused") ForeignToLLVMType targetType,
                    @Cached("createToI32()") ForeignToLLVM toI32) {
        return LLVMTypesGen.asInteger(toI32.executeWithForeignToLLVMType(value, type, targetType));
    }

    @Specialization(guards = "isI64(targetType)")
    static Object toI64(Object value, LLVMInteropType.Structured type, @SuppressWarnings("unused") ForeignToLLVMType targetType,
                    @Cached("createToI64()") ForeignToLLVM toI64) {
        return toI64.executeWithForeignToLLVMType(value, type, targetType);
    }

    @Specialization(guards = "isFloat(targetType)")
    static float toFloat(Object value, LLVMInteropType.Structured type, @SuppressWarnings("unused") ForeignToLLVMType targetType,
                    @Cached("createToFloat()") ForeignToLLVM toFloat) {
        return LLVMTypesGen.asFloat(toFloat.executeWithForeignToLLVMType(value, type, targetType));
    }

    @Specialization(guards = "isDouble(targetType)")
    static double toDouble(Object value, LLVMInteropType.Structured type, @SuppressWarnings("unused") ForeignToLLVMType targetType,
                    @Cached("createToDouble()") ForeignToLLVM toDouble) {
        return LLVMTypesGen.asDouble(toDouble.executeWithForeignToLLVMType(value, type, targetType));
    }

    @Specialization(guards = "isPointer(targetType)")
    static Object toPointer(Object value, LLVMInteropType.Structured type, @SuppressWarnings("unused") ForeignToLLVMType targetType,
                    @Cached("createToPointer()") ForeignToLLVM toPointer) {
        return toPointer.executeWithForeignToLLVMType(value, type, targetType);
    }

    static boolean isI1(ForeignToLLVMType targetType) {
        return targetType == ForeignToLLVMType.I1;
    }

    static boolean isI8(ForeignToLLVMType targetType) {
        return targetType == ForeignToLLVMType.I8;
    }

    static boolean isI16(ForeignToLLVMType targetType) {
        return targetType == ForeignToLLVMType.I16;
    }

    static boolean isI32(ForeignToLLVMType targetType) {
        return targetType == ForeignToLLVMType.I32;
    }

    static boolean isI64(ForeignToLLVMType targetType) {
        return targetType == ForeignToLLVMType.I64;
    }

    static boolean isFloat(ForeignToLLVMType targetType) {
        return targetType == ForeignToLLVMType.FLOAT;
    }

    static boolean isDouble(ForeignToLLVMType targetType) {
        return targetType == ForeignToLLVMType.DOUBLE;
    }

    static boolean isPointer(ForeignToLLVMType targetType) {
        return targetType == ForeignToLLVMType.POINTER;
    }

    protected ForeignToLLVM createToI1() {
        return getNodeFactory().createForeignToLLVM(ForeignToLLVMType.I1);
    }

    protected ForeignToLLVM createToI8() {
        return getNodeFactory().createForeignToLLVM(ForeignToLLVMType.I8);
    }

    protected ForeignToLLVM createToI16() {
        return getNodeFactory().createForeignToLLVM(ForeignToLLVMType.I16);
    }

    protected ForeignToLLVM createToI32() {
        return getNodeFactory().createForeignToLLVM(ForeignToLLVMType.I32);
    }

    protected ForeignToLLVM createToI64() {
        return getNodeFactory().createForeignToLLVM(ForeignToLLVMType.I64);
    }

    protected ForeignToLLVM createToFloat() {
        return getNodeFactory().createForeignToLLVM(ForeignToLLVMType.FLOAT);
    }

    protected ForeignToLLVM createToDouble() {
        return getNodeFactory().createForeignToLLVM(ForeignToLLVMType.DOUBLE);
    }

    protected ForeignToLLVM createToPointer() {
        return getNodeFactory().createForeignToLLVM(ForeignToLLVMType.POINTER);
    }
}
