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

import org.eclipse.emf.common.util.EList;

import com.intel.llvm.ireditor.lLVM_IR.AttributeGroup;
import com.intel.llvm.ireditor.lLVM_IR.FunctionAttribute;
import com.intel.llvm.ireditor.lLVM_IR.FunctionAttributes;
import com.intel.llvm.ireditor.lLVM_IR.FunctionHeader;
import com.intel.llvm.ireditor.lLVM_IR.TargetSpecificAttribute;
import com.oracle.truffle.llvm.runtime.LLVMLogger;

public final class LLVMAttributeVisitor {

    public static void visitFunctionHeader(FunctionHeader header) {
        FunctionAttributes allAttributes = header.getAttrs();
        if (allAttributes == null) {
            return;
        }
        EList<AttributeGroup> groupRefs = allAttributes.getFunctionAttributeGroupRefs();
        EList<FunctionAttribute> functionAttributes = allAttributes.getFunctionAttributes();
        for (FunctionAttribute functionAttribute : functionAttributes) {
            visitFunctionAttribute(functionAttribute);
        }
        for (AttributeGroup attributeGroup : groupRefs) {
            visitAttributeGroup(attributeGroup);
        }
    }

    private static void visitFunctionAttribute(FunctionAttribute functionAttribute) {
        String attribute = functionAttribute.getAttribute();
        switch (attribute) {
            case "unnamed_addr":
            case "alwaysinline":
            case "noinline":
            case "nounwind":
            case "inlinehint":
            case "readnone":
                // ignore
                break;
            case "noreturn":
                // we could emit an exception after a call to a noreturn exception to help compiler
                // optimizations
            case "uwtable":
            case "readonly":
                // ignore for the moment, investigate later
                break;
            default:
                LLVMLogger.info(attribute + " not supported!");
        }
    }

    private static void visitAttributeGroup(AttributeGroup attributeGroup) {
        EList<TargetSpecificAttribute> targetSpecificAttributes = attributeGroup.getTargetSpecificAttributes();
        if (targetSpecificAttributes.size() != 0) {
            LLVMLogger.info("target specific attributes not yet supported");
        }
        EList<FunctionAttribute> attributes = attributeGroup.getAttributes();
        if (attributes.size() != 0) {
            for (FunctionAttribute func : attributes) {
                visitFunctionAttribute(func);
            }
        }
        if (attributeGroup.getAlignstackValue().size() != 0) {
            throw new AssertionError("get align stack not yet supported!");
        }
        if (attributeGroup.getAlignstack().size() != 0) {
            throw new AssertionError("get align stack value not yet supported!");
        }
    }
}
