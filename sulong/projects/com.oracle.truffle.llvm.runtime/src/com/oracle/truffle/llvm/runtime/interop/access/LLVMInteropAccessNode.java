/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.interop.access;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType.StructMember;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;

@GenerateUncached
abstract class LLVMInteropAccessNode extends LLVMNode {

    @ValueType
    protected static class AccessLocation {

        final Object base;
        final Object identifier;
        final LLVMInteropType.Value type;

        AccessLocation(Object base, Object identifier, LLVMInteropType.Value type) {
            this.base = base;
            this.identifier = identifier;
            this.type = type;
        }
    }

    protected abstract AccessLocation execute(LLVMInteropType.Structured type, Object foreign, long offset);

    public static LLVMInteropAccessNode create() {
        return LLVMInteropAccessNodeGen.create();
    }

    @Specialization
    AccessLocation doArray(LLVMInteropType.Array type, Object foreign, long offset,
                    @Cached MakeAccessLocation makeAccessLocation) {
        long index = Long.divideUnsigned(offset, type.elementSize);
        long restOffset = Long.remainderUnsigned(offset, type.elementSize);
        return makeAccessLocation.execute(foreign, index, type.elementType, restOffset);
    }

    @Specialization(guards = "checkMember(type, cachedMember, offset)")
    AccessLocation doStructMember(@SuppressWarnings("unused") LLVMInteropType.Struct type, Object foreign, long offset,
                    @Cached("findMember(type, offset)") StructMember cachedMember,
                    @Cached("create()") MakeAccessLocation makeAccessLocation) {
        return makeAccessLocation.execute(foreign, cachedMember.name, cachedMember.type, offset - cachedMember.startOffset);
    }

    @Specialization(replaces = "doStructMember")
    AccessLocation doStruct(LLVMInteropType.Struct type, Object foreign, long offset,
                    @Cached MakeAccessLocation makeAccessLocation) {
        StructMember member = findMember(type, offset);
        return makeAccessLocation.execute(foreign, member.name, member.type, offset - member.startOffset);
    }

    static boolean checkMember(LLVMInteropType.Struct struct, StructMember member, long offset) {
        return struct == member.struct && member.contains(offset);
    }

    static StructMember findMember(LLVMInteropType.Struct struct, long offset) {
        for (StructMember m : struct.members) {
            if (m.contains(offset)) {
                return m;
            }
        }

        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException("invalid struct access");
    }

    @GenerateUncached
    abstract static class MakeAccessLocation extends LLVMNode {

        protected abstract AccessLocation execute(Object foreign, Object identifier, LLVMInteropType type, long restOffset);

        @Specialization
        AccessLocation doValue(Object foreign, Object identifier, LLVMInteropType.Value type, long restOffset) {
            if (restOffset != 0) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("cannot read from non-structured type with offset " + restOffset);
            }
            return new AccessLocation(foreign, identifier, type);
        }

        @Specialization(limit = "3")
        AccessLocation doRecursiveObject(Object foreign, String identifier, LLVMInteropType.Structured type, long restOffset,
                        @CachedLibrary("foreign") InteropLibrary interop,
                        @Cached("create()") LLVMInteropAccessNode recursive) {
            Object inner;
            try {
                inner = interop.readMember(foreign, identifier);
            } catch (UnknownIdentifierException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new LLVMPolyglotException(this, "Member '%s' not found", identifier);
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new LLVMPolyglotException(this, "Can not read member '%s'", identifier);
            }

            return recursive.execute(type, inner, restOffset);
        }

        @Specialization(limit = "3")
        AccessLocation doRecursiveArray(Object foreign, long index, LLVMInteropType.Structured type, long restOffset,
                        @CachedLibrary("foreign") InteropLibrary interop,
                        @Cached("create()") LLVMInteropAccessNode recursive) {
            Object inner;
            try {
                inner = interop.readArrayElement(foreign, index);
            } catch (InvalidArrayIndexException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new LLVMPolyglotException(this, "Invalid array index %d", index);
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new LLVMPolyglotException(this, "Cannot acess array element %d", index);
            }

            return recursive.execute(type, inner, restOffset);
        }
    }
}
