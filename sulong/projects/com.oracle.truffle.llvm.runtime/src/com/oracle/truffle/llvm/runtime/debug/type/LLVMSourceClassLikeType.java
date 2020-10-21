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

    private final List<LLVMSourceMethodType> methods;

    @TruffleBoundary
    public LLVMSourceClassLikeType(String name, long size, long align, long offset, LLVMSourceLocation location) {
        super(name, size, align, offset, location);
        this.methods = new ArrayList<>();
    }

    private LLVMSourceClassLikeType(Supplier<String> name, long size, long align, long offset, List<LLVMSourceMemberType> dynamicMembers, LLVMSourceStaticMemberType.CollectionType staticMembers,
                    List<LLVMSourceMethodType> methods, LLVMSourceLocation location) {
        super(name, size, align, offset, dynamicMembers, staticMembers, location);
        this.methods = methods;
    }

    public void addMethod(String name, String linkageName, LLVMSourceFunctionType function) {
        CompilerAsserts.neverPartOfCompilation();
        final LLVMSourceMethodType method = new LLVMSourceMethodType(function.getParameterTypes(), name, linkageName, this);
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

    public String getMethodName(int i) {
        return methods.get(i).getName();
    }

    public String getMethodLinkageName(int i) {
        return methods.get(i).getLinkageName();
    }

    @Override
    @TruffleBoundary
    public int getElementCount() {
        return super.getElementCount() + methods.size();
    }

    @Override
    @TruffleBoundary
    public String getElementName(long i) {
        String elementName = super.getElementName(i);
        if (elementName == null) {
            int index = (int) (i - super.getElementCount());
            if (0 <= index && index < methods.size()) {
                return methods.get(index).getName();
            }
        }
        return elementName;
    }

    @Override
    @TruffleBoundary
    public String getElementNameByOffset(long offset) {
        String elementName = super.getElementNameByOffset(offset);
        if (elementName == null) {
            for (LLVMSourceFunctionType method : methods) {
                if (method.getOffset() == offset) {
                    return method.getName();
                }
            }
        }
        return elementName;
    }

    @Override
    @TruffleBoundary
    public LLVMSourceType getElementType(long i) {
        LLVMSourceType llvmSourceType = super.getElementType(i);
        if (llvmSourceType == null) {
            int index = (int) (i - super.getElementCount());
            if (0 <= index && index < methods.size()) {
                return methods.get(index).getReturnType();
            }
        }
        return llvmSourceType;
    }

    @Override
    @TruffleBoundary
    public LLVMSourceType getElementType(String name) {
        LLVMSourceType llvmSourceType = super.getElementType(name);
        if (llvmSourceType == null) {
            int idx = getMethodIndexByName(name);
            if (idx >= 0) {
                return methods.get(idx).getReturnType();
            }
        }
        return llvmSourceType;
    }

    @Override
    @TruffleBoundary
    public LLVMSourceLocation getElementDeclaration(long i) {
        LLVMSourceLocation llvmSourceLocation = super.getElementDeclaration(i);
        if (llvmSourceLocation == null) {
            int index = (int) (i - super.getElementCount());
            if (0 <= index && index < methods.size()) {
                return methods.get(index).getLocation();
            }
        }
        return llvmSourceLocation;
    }

    private int getMethodIndexByName(String name) {
        if (name == null) {
            return -1;
        }
        for (int i = 0; i < methods.size(); i++) {
            LLVMSourceMethodType method = methods.get(i);
            if (name.contentEquals(method.getLinkageName()) || name.contentEquals(method.getName())) {
                return i;
            }
        }
        return -1;
    }

    @Override
    @TruffleBoundary
    public LLVMSourceLocation getElementDeclaration(String name) {
        LLVMSourceLocation llvmSourceLocation = super.getElementDeclaration(name);
        if (llvmSourceLocation == null) {
            int idx = getMethodIndexByName(name);
            if (idx >= 0) {
                return methods.get(idx).getLocation();
            }
        }
        return llvmSourceLocation;
    }
}
