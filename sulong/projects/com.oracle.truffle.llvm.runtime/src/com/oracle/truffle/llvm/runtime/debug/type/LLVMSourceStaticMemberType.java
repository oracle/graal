/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.stream.Collectors;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugObject;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugObjectBuilder;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugValue;

public final class LLVMSourceStaticMemberType extends LLVMSourceType {

    public static class CollectionType extends LLVMSourceType {

        static final String MEMBERNAME = "<static>";

        private static final String TYPENAME = "";

        private final ArrayList<LLVMSourceStaticMemberType> members;

        CollectionType() {
            super(() -> TYPENAME, 0L, 0L, 0L, null);
            this.members = new ArrayList<>();
        }

        @TruffleBoundary
        void addMember(LLVMSourceStaticMemberType member) {
            CompilerAsserts.neverPartOfCompilation();
            members.add(member);
        }

        @TruffleBoundary
        public String[] getIdentifiers() {
            return members.stream().map(LLVMSourceStaticMemberType::getName).collect(Collectors.toList()).toArray(new String[members.size()]);
        }

        public LLVMDebugObjectBuilder getMemberValue(String name) {
            final LLVMSourceStaticMemberType member = getMember(name);
            return member != null ? member.getValue() : DEFAULT_VALUE;
        }

        @Override
        @TruffleBoundary
        public int getElementCount() {
            return members.size();
        }

        @TruffleBoundary
        private LLVMSourceStaticMemberType getMember(long i) {
            if (0 <= i && i < members.size()) {
                return members.get((int) i);
            }
            return null;
        }

        @TruffleBoundary
        private LLVMSourceStaticMemberType getMember(String name) {
            if (name == null) {
                return null;
            }
            for (final LLVMSourceStaticMemberType member : members) {
                if (name.equals(member.getName())) {
                    return member;
                }
            }
            return null;
        }

        @Override
        public String getElementName(long i) {
            final LLVMSourceStaticMemberType member = getMember(i);
            return member != null ? member.getName() : null;
        }

        @Override
        public LLVMSourceType getElementType(long i) {
            final LLVMSourceStaticMemberType member = getMember(i);
            return member != null ? member.getElementType() : null;
        }

        @Override
        public LLVMSourceType getElementType(String name) {
            final LLVMSourceStaticMemberType member = getMember(name);
            return member != null ? member.getElementType() : null;
        }

        @Override
        public LLVMSourceLocation getElementDeclaration(long i) {
            final LLVMSourceStaticMemberType member = getMember(i);
            return member != null ? member.getLocation() : null;
        }

        @Override
        public LLVMSourceLocation getElementDeclaration(String name) {
            final LLVMSourceStaticMemberType member = getMember(name);
            return member != null ? member.getLocation() : null;
        }

        @Override
        public LLVMSourceType getOffset(long newOffset) {
            return this;
        }
    }

    private static final LLVMDebugObjectBuilder DEFAULT_VALUE = new LLVMDebugObjectBuilder() {
        @Override
        public LLVMDebugObject getValue(LLVMSourceType type, LLVMSourceLocation declaration) {
            return LLVMDebugObject.create(type, 0L, LLVMDebugValue.UNAVAILABLE, declaration);
        }
    };

    @CompilationFinal private LLVMSourceType elementType = null;

    @CompilationFinal private LLVMDebugObjectBuilder value = DEFAULT_VALUE;

    public LLVMSourceStaticMemberType(String name, long size, long align, LLVMSourceLocation location) {
        super(() -> name, size, align, 0L, location);
    }

    public LLVMDebugObjectBuilder getValue() {
        CompilerAsserts.neverPartOfCompilation();
        return value;
    }

    public void setValue(LLVMDebugObjectBuilder value) {
        CompilerAsserts.neverPartOfCompilation();
        this.value = value;
    }

    public LLVMSourceType getElementType() {
        return elementType;
    }

    public void setElementType(LLVMSourceType elementType) {
        CompilerAsserts.neverPartOfCompilation();
        this.elementType = elementType;
    }

    @Override
    @TruffleBoundary
    public String toString() {
        return String.format("%s: %s", getName(), elementType != null ? elementType.getName() : null);
    }

    @Override
    public LLVMSourceType getOffset(long newOffset) {
        return this;
    }

    @Override
    public boolean isAggregate() {
        return true;
    }

    @Override
    public int getElementCount() {
        return 1;
    }

    @Override
    public String getElementName(long i) {
        if (i == 0) {
            return getName();
        }
        return null;
    }

    @Override
    public LLVMSourceType getElementType(long i) {
        if (i == 0) {
            return getElementType();
        }
        return null;
    }

    @Override
    public LLVMSourceType getElementType(String name) {
        if (name != null && name.equals(getName())) {
            return getElementType();
        }
        return null;
    }
}
