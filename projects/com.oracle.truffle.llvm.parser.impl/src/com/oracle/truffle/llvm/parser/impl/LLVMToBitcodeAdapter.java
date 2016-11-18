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

import java.util.ArrayList;
import java.util.List;

import com.intel.llvm.ireditor.lLVM_IR.FunctionDef;
import com.intel.llvm.ireditor.lLVM_IR.FunctionHeader;
import com.intel.llvm.ireditor.lLVM_IR.Parameter;
import com.oracle.truffle.llvm.parser.base.model.enums.Linkage;
import com.oracle.truffle.llvm.parser.base.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.base.model.globals.GlobalVariable;
import com.oracle.truffle.llvm.parser.base.model.types.FunctionType;
import com.oracle.truffle.llvm.parser.base.model.types.Type;

public final class LLVMToBitcodeAdapter {

    private LLVMToBitcodeAdapter() {
    }

    public static FunctionDefinition resolveFunctionDef(LLVMParserRuntimeTextual runtime, FunctionDef def) {
        FunctionDefinition funcDef = new FunctionDefinition(resolveFunctionHeader(runtime, def.getHeader()), null);
        funcDef.setName(def.getHeader().getName().substring(1));
        return funcDef;
    }

    public static FunctionType resolveFunctionHeader(LLVMParserRuntimeTextual runtime, FunctionHeader header) {
        Type returnType = TextToBCConverter.convert(runtime.resolve(header.getRettype()));
        List<Type> args = new ArrayList<>();
        boolean hasVararg = false;
        for (Parameter arg : header.getParameters().getParameters()) {
            assert !hasVararg; // should be the last element of the parameterlist
            if (runtime.resolve(arg.getType().getType()).isVararg()) {
                hasVararg = true;
            } else {
                args.add(TextToBCConverter.convert(runtime.resolve(arg.getType().getType())));
            }
        }
        FunctionType funcType = new FunctionType(returnType, args.toArray(new Type[args.size()]), hasVararg);
        funcType.setName(header.getName().substring(1));
        return funcType;
    }

    private static Linkage resolveLinkage(String linkage) {
        for (final Linkage linkageEnumVal : Linkage.values()) {
            if (linkageEnumVal.getIrString().equalsIgnoreCase(linkage)) {
                return linkageEnumVal;
            }
        }
        return Linkage.UNKNOWN;
    }

    public static GlobalVariable resolveGlobalVariable(LLVMParserRuntimeTextual runtime, com.intel.llvm.ireditor.lLVM_IR.GlobalVariable globalVariable) {
        Type type = TextToBCConverter.convert(runtime.resolve(globalVariable.getType()));

        String alignString = globalVariable.getAlign();
        int align = alignString != null ? Integer.valueOf(alignString.replaceAll("align ", "")) : 0;

        Linkage linkage = resolveLinkage(globalVariable.getLinkage());

        GlobalVariable glob = GlobalVariable.create(type, 0, align, linkage != null ? linkage.ordinal() : 0);
        glob.setName(globalVariable.getName().substring(1));

        return glob;
    }
}
