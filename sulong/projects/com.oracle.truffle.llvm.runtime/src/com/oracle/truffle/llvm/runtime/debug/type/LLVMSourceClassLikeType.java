/*
 * Copyright (c) 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.debug.type;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class LLVMSourceClassLikeType extends LLVMSourceStructLikeType {

    private final List<LLVMSourceFunctionType> methods;

    @TruffleBoundary
    public LLVMSourceClassLikeType(String name, long size, long align, long offset, LLVMSourceLocation location) {
        super(name, size, align, offset, location);
        this.methods = new ArrayList<>();
    }

    private LLVMSourceClassLikeType(Supplier<String> name, long size, long align, long offset, List<LLVMSourceMemberType> dynamicMembers, LLVMSourceStaticMemberType.CollectionType staticMembers,
                    List<LLVMSourceFunctionType> methods, LLVMSourceLocation location) {
        super(name, size, align, offset, dynamicMembers, staticMembers, location);
        this.methods = methods;
    }

    @TruffleBoundary
    public void addMethod(LLVMSourceFunctionType method) {
        CompilerAsserts.neverPartOfCompilation();
        methods.add(method);
    }

    @Override
    public LLVMSourceType getOffset(long newOffset) {
        return new LLVMSourceClassLikeType(this::getName, getSize(), getAlign(), newOffset, dynamicMembers, staticMembers, methods, getLocation());
    }

    @Override
    public boolean isAggregate() {
        return true;
    }

    public int getMethodCount() {
        return methods.size();
    }

    public LLVMSourceFunctionType getMethod(int i) {
        return methods.get(i);
    }

    @Override
    @TruffleBoundary
    public int getElementCount() {
        int elementCount = dynamicMembers.size() + methods.size();
        if (staticMembers.getElementCount() != 0) {
            elementCount++;
        }
        return elementCount;
    }

    @Override
    @TruffleBoundary
    public String getElementName(long i) {
        int index = (int) i;
        if (staticMembers.getElementCount() != 0) {
            if (index == 0) {
                return LLVMSourceStaticMemberType.CollectionType.MEMBERNAME;
            } else {
                index--;
            }
        }
        if (0 <= index && index < dynamicMembers.size()) {
            return dynamicMembers.get(index).getName();
        } else {
            index -= dynamicMembers.size();
        }
        if (0 <= index && index < methods.size()) {
            return methods.get(index).getName();
        }
        return null;
    }

    @Override
    @TruffleBoundary
    public String getElementNameByOffset(long offset) {
        for (LLVMSourceMemberType member : dynamicMembers) {
            if (member.getOffset() == offset) {
                return member.getName();
            }
        }
        for (LLVMSourceFunctionType method : methods) {
            if (method.getOffset() == offset) {
                return method.getName();
            }
        }
        return null;
    }

    @Override
    @TruffleBoundary
    public LLVMSourceType getElementType(long i) {
        int index = (int) i;
        if (staticMembers.getElementCount() != 0) {
            if (index == 0) {
                return staticMembers;
            } else {
                index--;
            }
        }
        if (0 <= index && index < dynamicMembers.size()) {
            return dynamicMembers.get(index).getOffsetElementType();
        } else {
            index -= dynamicMembers.size();
        }
        if (0 <= index && index < methods.size()) {
            // TODO (pichristoph)
        }
        return null;
    }

    @Override
    @TruffleBoundary
    public LLVMSourceType getElementType(String name) {
        if (name == null) {
            return null;
        }
        for (final LLVMSourceMemberType member : dynamicMembers) {
            if (name.equals(member.getName())) {
                return member.getOffsetElementType();
            }
        }
        if (LLVMSourceStaticMemberType.CollectionType.MEMBERNAME.equals(name)) {
            return staticMembers;
        }
        return null;
    }

    @Override
    @TruffleBoundary
    public LLVMSourceLocation getElementDeclaration(long i) {
        int index = (int) i;
        if (staticMembers.getElementCount() != 0) {
            if (index == 0) {
                return staticMembers.getLocation();
            } else {
                index--;
            }
        }
        if (0 <= index && index < dynamicMembers.size()) {
            return dynamicMembers.get(index).getLocation();
        } else {
            index -= dynamicMembers.size();
        }
        if (0 <= index && index < methods.size()) {
            return methods.get(index).getLocation();
        }
        return null;
    }

    @Override
    @TruffleBoundary
    public LLVMSourceLocation getElementDeclaration(String name) {
        if (name == null) {
            return null;
        }
        for (final LLVMSourceMemberType member : dynamicMembers) {
            if (name.equals(member.getName())) {
                return member.getLocation();
            }
        }
        for (final LLVMSourceFunctionType method : methods) {
            if (name.equals(method.getName())) {
                return method.getLocation();
            }
        }
        if (LLVMSourceStaticMemberType.CollectionType.MEMBERNAME.equals(name)) {
            return staticMembers.getLocation();
        }
        return null;
    }
}
