/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.DebugExprType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

public class DebugExprArrayElementNode extends LLVMExpressionNode {

    private LLVMExpressionNode indexNode;
    private DebugExprType type;
    private Object member;
    private Object baseMember;

    public DebugExprArrayElementNode(Object baseMember, LLVMExpressionNode indexNode, DebugExprType type) {
        this.indexNode = indexNode;
        this.baseMember = baseMember;
        this.type = type;
    }

    public DebugExprType getType() {
        return type;
    }

    public Object getMember() {
        return member;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        int idx;
        try {
            idx = indexNode.executeI32(frame);
        } catch (UnexpectedResultException e) {
            try {
                // in case of a complex expression as index (e.g. outerArray[innerArray[2]]), the
                // index (=e.getResult()) is no Integer but a LLVMDebugObject$Primitive instead
                idx = Integer.parseInt(e.getResult().toString());
            } catch (NumberFormatException e1) {
                return DebugExprNodeFactory.errorObjNode.executeGeneric(frame);
            }
        }
        InteropLibrary library = InteropLibrary.getFactory().getUncached();
        if (library.hasMembers(baseMember)) {
            Object getmembers;
            try {
                getmembers = library.getMembers(baseMember);
                if (library.isArrayElementReadable(getmembers, idx)) {
                    Object arrayElement = library.readArrayElement(getmembers, idx);
                    if (library.isMemberReadable(baseMember, arrayElement.toString())) {
                        return library.readMember(baseMember, arrayElement.toString());
                    }
                }
            } catch (UnsupportedMessageException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (InvalidArrayIndexException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (UnknownIdentifierException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        return DebugExprNodeFactory.errorObjNode.executeGeneric(frame);
    }
}
