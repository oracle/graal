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
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebuggerValue;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.DebugExprType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

public class DebugExprObjectMemberNode extends LLVMExpressionNode {

    private final String fieldName;
    private DebugExprType type;
    private Object value;
    private LLVMExpressionNode baseNode;
    private Object member;
    private Object metaObj;

    public DebugExprObjectMemberNode(String fieldName, LLVMExpressionNode baseNode) {
        this.fieldName = fieldName;
        this.type = null;
        this.value = null;
        this.baseNode = baseNode;
        findMemberAndType();
    }

    private Object getBaseMember() {
        if (baseNode instanceof DebugExprVarNode) {
            return ((DebugExprVarNode) baseNode).getMember();
        } else if (baseNode instanceof DebugExprObjectMemberNode) {
            return ((DebugExprObjectMemberNode) baseNode).getMember();
        }
        return DebugExprNodeFactory.noObjNode;
    }

    private void findMemberAndType() {
        InteropLibrary library = InteropLibrary.getFactory().getUncached();
        // TODO fetch member and metaObj from baseMember/name
        Object baseMember = getBaseMember();
        if (library.isMemberExisting(baseMember, fieldName)) {
            System.out.println("member " + fieldName + " exists!");
            try {
                member = library.readMember(baseMember, fieldName);
                LLVMDebuggerValue ldv = (LLVMDebuggerValue) member;
                metaObj = ldv.getMetaObject();
                type = DebugExprType.getTypeFromSymbolTableMetaObject(metaObj);
            } catch (UnsupportedMessageException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (UnknownIdentifierException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        } else {
            System.out.println("member " + fieldName + "does not exist!");
        }
    }

    public DebugExprType getType() {
        return type;
    }

    private void findValue() {
        if (type != null) {
            value = type.parseString(member.toString());
        }
    }

    public Object getMember() {
        return member;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        findValue();
        if (value == null)
            return DebugExprNodeFactory.noObjNode.executeGeneric(frame);
        return value;
    }
}
