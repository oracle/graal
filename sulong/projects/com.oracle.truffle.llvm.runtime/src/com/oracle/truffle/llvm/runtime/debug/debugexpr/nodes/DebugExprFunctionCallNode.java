/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes;

import java.util.List;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.DebugExprException;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.DebugExprType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.types.Type;

public abstract class DebugExprFunctionCallNode extends LLVMExpressionNode {

    private final String functionName;
    @Children private final LLVMExpressionNode[] arguments;

    private final Object scope;

    public DebugExprFunctionCallNode(String functionName, List<DebugExpressionPair> arguments, Object scope) {
        this.functionName = functionName;
        this.scope = scope;
        this.arguments = new LLVMExpressionNode[arguments.size()];
        for (int i = 0; i < this.arguments.length; i++) {
            this.arguments[i] = arguments.get(i).getNode();
        }
    }

    @TruffleBoundary
    public DebugExprType getType() {
        InteropLibrary library = InteropLibrary.getFactory().getUncached();
        if (library.isMemberReadable(scope, functionName)) {
            try {
                Object member = library.readMember(scope, functionName);
                if (LLVMManagedPointer.isInstance(member)) {
                    LLVMManagedPointer pointer = LLVMManagedPointer.cast(member);
                    if (pointer.getOffset() == 0) {
                        member = pointer.getObject();
                    }
                }
                if (member instanceof LLVMFunctionDescriptor) {
                    LLVMFunctionDescriptor ldv = (LLVMFunctionDescriptor) member;
                    Type returnType = ldv.getLLVMFunction().getType().getReturnType();
                    DebugExprType t = DebugExprType.getTypeFromLLVMType(returnType);
                    return t;
                } else {
                    throw DebugExprException.create(this, "variable %s does not point to a function", functionName);
                }
            } catch (UnsupportedMessageException e) {
                throw DebugExprException.create(this, "error while accessing function %s", functionName);
            } catch (UnknownIdentifierException e) {
                // fallthrough
            }
        }
        throw DebugExprException.symbolNotFound(this, functionName, null);
    }

    @Specialization
    Object doCall(VirtualFrame frame) {
        InteropLibrary library = InteropLibrary.getFactory().getUncached();
        if (library.isMemberExisting(scope, functionName)) {
            try {
                Object member = library.readMember(scope, functionName);
                if (library.isExecutable(member)) {
                    try {
                        Object[] argumentArr = new Object[arguments.length];
                        for (int i = 0; i < arguments.length; i++) {
                            argumentArr[i] = arguments[i].executeGeneric(frame);
                        }
                        return library.execute(member, argumentArr);
                    } catch (UnsupportedTypeException e) {
                        throw DebugExprException.create(this, "actual and formal parameters of %s do not match", functionName);
                    } catch (ArityException e) {
                        throw DebugExprException.create(this, "%s requires %d argument(s) but got %d", functionName, e.getExpectedMinArity(), e.getActualArity());
                    }
                } else {
                    throw DebugExprException.create(this, "%s is not invocable", functionName);
                }
            } catch (UnsupportedMessageException e1) {
                throw DebugExprException.create(this, "Error while accessing function %s", functionName);
            } catch (UnknownIdentifierException e1) {
                throw DebugExprException.symbolNotFound(this, e1.getUnknownIdentifier(), functionName);
            }
        }
        throw DebugExprException.symbolNotFound(this, functionName, null);
    }
}
