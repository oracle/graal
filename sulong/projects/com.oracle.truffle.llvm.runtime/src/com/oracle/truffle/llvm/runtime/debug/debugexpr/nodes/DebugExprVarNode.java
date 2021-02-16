/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.NodeLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebuggerValue;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.DebugExprException;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.DebugExprType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import org.graalvm.collections.Pair;

import java.util.List;

public abstract class DebugExprVarNode extends LLVMExpressionNode implements MemberAccessible {

    private final String name;
    private final Node location;

    DebugExprVarNode(String name, Node location) {
        this.name = name;
        this.location = location;
    }

    private Pair<Object, DebugExprType> findMemberAndType(VirtualFrame frameValue) {
        NodeLibrary nodeLibrary = NodeLibrary.getUncached();
        InteropLibrary interopLibrary = InteropLibrary.getUncached();
        try {
            LLVMDebuggerValue entries = (LLVMDebuggerValue) nodeLibrary.getScope(location, frameValue, true);
            if (interopLibrary.isMemberReadable(entries, name)) {
                Object member = interopLibrary.readMember(entries, name);
                LLVMDebuggerValue ldv = (LLVMDebuggerValue) member;
                Object metaObj = ldv.resolveMetaObject();
                DebugExprType type = DebugExprType.getTypeFromSymbolTableMetaObject(metaObj);
                return Pair.create(member, type);
            }
        } catch (ClassCastException e) {
            // member has no value, e.g. if the compiler has eliminated unused symbols
            // OR metaObj is no primitive type
            throw DebugExprException.create(this, "\"%s\" cannot be casted to a LLVMDebuggerValue", name);
        } catch (UnsupportedMessageException e) {
            // should only happen if hasMembers == false
            throw DebugExprException.symbolNotFound(this, name, null);
        } catch (UnknownIdentifierException e) {
            throw DebugExprException.symbolNotFound(this, e.getUnknownIdentifier(), null);
        }
        // not found: no exception is thrown as this node might be a function name
        return Pair.create(null, DebugExprType.getVoidType());
    }

    @Override
    public DebugExprType getType(VirtualFrame frame) {
        return findMemberAndType(frame).getRight();
    }

    @Override
    public Object getMember(VirtualFrame frame) {
        return findMemberAndType(frame).getLeft();
    }

    public String getName() {
        return name;
    }

    @Specialization
    public Object doVariable(VirtualFrame frame) {
        Pair<Object, DebugExprType> pair = findMemberAndType(frame);
        if (pair.getLeft() == null) {
            throw DebugExprException.symbolNotFound(this, name, null);
        }
        Object member = pair.getLeft();
        DebugExprType type = pair.getRight();
        if (type != null && member != null) {
            Object value = type.parse(member);
            if (value != null) {
                return value;
            }
        }
        throw DebugExprException.symbolNotFound(this, name, null);
    }

    public DebugExprFunctionCallNode createFunctionCall(List<DebugExpressionPair> arguments, Object globalScope) {
        return DebugExprFunctionCallNodeGen.create(name, arguments, globalScope);
    }
}
