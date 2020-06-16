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

import java.util.Collection;
import java.util.List;

import org.graalvm.collections.Pair;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebuggerValue;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.DebugExprException;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.DebugExprType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

public abstract class DebugExprVarNode extends LLVMExpressionNode implements MemberAccessible {

    static final Scope[] NO_SCOPES = {};

    private final String name;
    @CompilationFinal(dimensions = 1) private final Scope[] scopes;

    DebugExprVarNode(String name, Collection<Scope> scopes) {
        this.name = name;
        this.scopes = scopes.toArray(NO_SCOPES);
    }

    @TruffleBoundary
    private Pair<Object, DebugExprType> findMemberAndType() {
        InteropLibrary library = InteropLibrary.getFactory().getUncached();
        for (Scope scope : scopes) {
            Object vars = scope.getVariables();
            try {
                if (library.isMemberReadable(vars, name)) {
                    Object member = library.readMember(vars, name);
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
        }
        // not found: no exception is thrown as this node might be a function name
        return Pair.create(null, DebugExprType.getVoidType());
    }

    @Override
    public DebugExprType getType() {
        return findMemberAndType().getRight();
    }

    @Override
    public Object getMember() {
        return findMemberAndType().getLeft();
    }

    public String getName() {
        return name;
    }

    @Specialization
    public Object doVariable() {
        Pair<Object, DebugExprType> pair = findMemberAndType();
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

    public DebugExprFunctionCallNode createFunctionCall(List<DebugExpressionPair> arguments, Collection<Scope> globalScopes) {
        return DebugExprFunctionCallNodeGen.create(name, arguments, globalScopes.toArray(NO_SCOPES));
    }
}
