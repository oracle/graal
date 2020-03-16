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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.DebugExprException;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.DebugExprType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

@NodeInfo(shortName = "[]")
@NodeChild(value = "base", type = LLVMExpressionNode.class)
@NodeChild(value = "index", type = LLVMExpressionNode.class)
public abstract class DebugExprArrayElementNode extends LLVMExpressionNode {

    final DebugExprType type;

    public static DebugExprArrayElementNode create(DebugExpressionPair basePair, LLVMExpressionNode indexNode) {
        DebugExprType type = basePair.getType() == null ? DebugExprType.getVoidType() : basePair.getType().getInnerType();
        return DebugExprArrayElementNodeGen.create(type, basePair.getNode(), indexNode);
    }

    DebugExprArrayElementNode(DebugExprType type) {
        this.type = type;
    }

    public DebugExprType getType() {
        return type;
    }

    @Specialization
    public Object doIntIndex(Object baseMember, int idx,
                    @CachedLibrary(limit = "3") InteropLibrary library) {
        if (library.hasMembers(baseMember)) {
            Object getmembers;
            try {
                getmembers = library.getMembers(baseMember);
                if (library.isArrayElementReadable(getmembers, idx)) {
                    Object arrayElement = library.readArrayElement(getmembers, idx);
                    String identifier = library.asString(arrayElement);
                    if (library.isMemberReadable(baseMember, identifier)) {
                        Object member = library.readMember(baseMember, identifier);
                        return type.parse(member);
                    }
                }
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw DebugExprException.create(this, "Array access of %s not possible", baseMember);
            } catch (InvalidArrayIndexException e) {
                CompilerDirectives.transferToInterpreter();
                throw DebugExprException.create(this, "Invalid array index: %d", e.getInvalidIndex());
            } catch (UnknownIdentifierException e) {
                CompilerDirectives.transferToInterpreter();
                throw DebugExprException.symbolNotFound(this, e.getUnknownIdentifier(), baseMember);
            }
        }
        CompilerDirectives.transferToInterpreter();
        throw DebugExprException.create(this, "Array access of " + baseMember + " not possible");
    }

    @TruffleBoundary
    private static int toIntIndex(Object index) {
        return Integer.parseInt(index.toString());
    }

    @Specialization
    public Object doGeneric(Object baseMember, Object index,
                    @CachedLibrary(limit = "3") InteropLibrary library) {
        // in case of a complex expression as index (e.g. outerArray[innerArray[2]]), the
        // index is no Integer but a LLVMDebugObject$Primitive instead
        return doIntIndex(baseMember, toIntIndex(index), library);
    }
}
