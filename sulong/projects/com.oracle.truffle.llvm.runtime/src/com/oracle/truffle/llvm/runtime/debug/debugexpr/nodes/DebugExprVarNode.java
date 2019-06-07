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

import java.util.Map.Entry;

import org.graalvm.collections.Pair;

import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebuggerValue;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.DebugExprType;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.Parser;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

public class DebugExprVarNode extends DebugExprNode {

    private final String name;
    private Iterable<Scope> scopes;

    public DebugExprVarNode(String name, Iterable<Scope> scopes) {
        this.name = name;
        this.scopes = scopes;
    }

    @Override
    public Pair<Object, DebugExprType> executeGenericWithType(VirtualFrame frame) {
        LLVMLanguage.getLLVMContextReference().get();
        for (Scope scope : scopes) {
            Object vars = scope.getVariables();
            InteropLibrary library = InteropLibrary.getFactory().getUncached();
            library.hasMembers(vars);
            try {
                final Object memberKeys = library.getMembers(vars);
                library.hasArrayElements(memberKeys);
                for (long i = 0; i < library.getArraySize(memberKeys); i++) {
                    final String memberKey = (String) library.readArrayElement(memberKeys, i);
                    if (!memberKey.equals(name))
                        continue;
                    Object member = library.readMember(vars, memberKey);
                    LLVMDebuggerValue ldv = (LLVMDebuggerValue) member;
                    Object metaObj = ldv.getMetaObject();
                    if (metaObj == null) {
                        return Pair.create(member, DebugExprType.getVoidType());
                    }
                    // System.out.println(name + "|metaObj.toString() = " + metaObj.toString());
                    // System.out.println(name + "|ldv.toString() = " + ldv.toString());
                    if (metaObj.toString().contentEquals("short") || metaObj.toString().contentEquals("signed short")) {
                        return Pair.create(Integer.parseInt(member.toString()), DebugExprType.getIntType(16, true));
                    } else if (metaObj.toString().contentEquals("unsigned short")) {
                        return Pair.create(Integer.parseInt(member.toString()), DebugExprType.getIntType(16, false));
                    } else if (metaObj.toString().contentEquals("int") || metaObj.toString().contentEquals("signed int")) {
                        return Pair.create(Integer.parseInt(member.toString()), DebugExprType.getIntType(32, true));
                    } else if (metaObj.toString().contentEquals("unsigned int")) {
                        return Pair.create(Integer.parseInt(member.toString()), DebugExprType.getIntType(32, false));
                    } else if (metaObj.toString().contentEquals("long") || metaObj.toString().contentEquals("signed long")) {
                        return Pair.create(Integer.parseInt(member.toString()), DebugExprType.getIntType(64, true));
                    } else if (metaObj.toString().contentEquals("unsigned long")) {
                        return Pair.create(Integer.parseInt(member.toString()), DebugExprType.getIntType(64, false));
                    } else if (metaObj.toString().contentEquals("float")) {
                        return Pair.create(Float.parseFloat(member.toString()), DebugExprType.getFloatType(32));
                    } else if (metaObj.toString().contentEquals("double")) {
                        return Pair.create(Double.parseDouble(member.toString()), DebugExprType.getFloatType(64));
                    } else if (metaObj.toString().contentEquals("long double")) {
                        return Pair.create(Double.parseDouble(member.toString()), DebugExprType.getFloatType(128));
                    }
                    System.out.println("String representation: " + metaObj.toString());
                    return Pair.create(member.toString(), DebugExprType.getVoidType());
                }
            } catch (UnsupportedMessageException e) {
                // should only happen if hasMembers == false
            } catch (InvalidArrayIndexException e) {
                // should only happen if memberKeysHasKeys == false
            } catch (UnknownIdentifierException e) {
                // should not happen
            }
        }
        return Pair.create(Parser.noObjNode.executeGeneric(frame), DebugExprType.getVoidType());
    }
}
