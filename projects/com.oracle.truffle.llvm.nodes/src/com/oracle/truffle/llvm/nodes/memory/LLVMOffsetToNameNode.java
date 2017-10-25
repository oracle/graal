/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.memory;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.nodes.memory.LLVMOffsetToNameNodeGen.FindMemberNodeGen;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceArrayLikeType;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceBasicType;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceDecoratorType;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourcePointerType;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceStructLikeType;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceType;

public abstract class LLVMOffsetToNameNode extends Node {

    private final int elementAccessSize;

    protected LLVMOffsetToNameNode(int elementAccessSize) {
        this.elementAccessSize = elementAccessSize;
    }

    public abstract Object execute(LLVMSourceType type, long offset);

    @Specialization(guards = "type == cachedType")
    Object doCached(@SuppressWarnings("unused") LLVMSourceType type, long offset,
                    @Cached("type") LLVMSourceType cachedType,
                    @Cached("createFindMember()") FindMemberNode findMember) {
        return findMember.execute(cachedType, offset, elementAccessSize);
    }

    @Specialization(replaces = "doCached")
    Object doGeneric(LLVMSourceType type, long offset,
                    @Cached("createFindMember()") FindMemberNode findMember) {
        return findMember.execute(type, offset, elementAccessSize);
    }

    static FindMemberNode createFindMember() {
        return FindMemberNodeGen.create(false);
    }

    abstract static class FindMemberNode extends Node {

        public static final String UNKNOWN_MEMBER = "<UNKNOWN>";
        final boolean dereferencedPointer;

        FindMemberNode(boolean dereferencedPointer) {
            this.dereferencedPointer = dereferencedPointer;
        }

        abstract Object execute(LLVMSourceType type, long offset, int elementSize);

        FindMemberNode create() {
            return FindMemberNodeGen.create(dereferencedPointer);
        }

        static FindMemberNode createDereferencedPointer() {
            return FindMemberNodeGen.create(true);
        }

        static boolean noTypeInfo(LLVMSourceType type) {
            return type == null || type == LLVMSourceType.UNKNOWN_TYPE;
        }

        @Specialization(guards = "noTypeInfo(type)")
        int doNull(@SuppressWarnings("unused") LLVMSourceType type, long offset, int elementSize) {
            // if we have no type info, the best we can do is assume it's an indexed access
            return doArray(null, offset, elementSize);
        }

        @Specialization(guards = "!dereferencedPointer")
        Object doPointer(LLVMSourcePointerType type, long offset, int elementSize,
                        @Cached("createDereferencedPointer()") FindMemberNode findMember) {
            return findMember.execute(type.getBaseType(), offset, elementSize);
        }

        @Specialization
        Object doDecorator(LLVMSourceDecoratorType type, long offset, int elementSize,
                        @Cached("create()") FindMemberNode findMember) {
            return findMember.execute(type.getBaseType(), offset, elementSize);
        }

        @Specialization(guards = "!dereferencedPointer")
        int doArray(@SuppressWarnings("unused") LLVMSourceArrayLikeType type, long offset, int elementSize) {
            return (int) (offset / elementSize);
        }

        @Specialization(guards = "dereferencedPointer || offset == 0")
        int doBasic(@SuppressWarnings("unused") LLVMSourceBasicType type, long offset, int elementSize) {
            // pointer to basic type: this is the same as an array access
            return doArray(null, offset, elementSize);
        }

        @Specialization(guards = "dereferencedPointer")
        int doPointerToPointer(@SuppressWarnings("unused") LLVMSourcePointerType type, long offset, int elementSize) {
            // pointer to pointer is same as array of pointer
            return doArray(null, offset, elementSize);
        }

        @Specialization(guards = {"type == cachedType", "offset == cachedOffset"})
        @SuppressWarnings("unused")
        String doStructCached(LLVMSourceStructLikeType type, long offset, int elementSize,
                        @Cached("type") LLVMSourceStructLikeType cachedType,
                        @Cached("offset") long cachedOffset,
                        @Cached("doStruct(cachedType, cachedOffset, elementSize)") String cachedResult) {
            return cachedResult;
        }

        @Specialization(replaces = "doStructCached")
        String doStruct(LLVMSourceStructLikeType type, long offset, @SuppressWarnings("unused") int elementSize) {
            return type.getElementNameByOffset(offset * Byte.SIZE);
        }

        @Fallback
        @SuppressWarnings("unused")
        Object doFallback(LLVMSourceType type, long offset, int elementSize) {
            return UNKNOWN_MEMBER;
        }
    }
}
