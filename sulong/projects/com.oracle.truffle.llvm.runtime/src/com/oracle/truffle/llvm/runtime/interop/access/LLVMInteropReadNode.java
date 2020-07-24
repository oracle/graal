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
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropAccessNode.AccessLocation;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.interop.convert.ToLLVM;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

@GenerateUncached
public abstract class LLVMInteropReadNode extends LLVMNode {

    public static LLVMInteropReadNode create() {
        return LLVMInteropReadNodeGen.create();
    }

    public abstract Object execute(LLVMInteropType.Structured type, Object foreign, long offset, ForeignToLLVMType accessType);

    @Specialization(guards = "type != null")
    Object doKnownType(LLVMInteropType.Structured type, Object foreign, long offset, ForeignToLLVMType accessType,
                    @Cached LLVMInteropAccessNode access,
                    @Cached ReadLocationNode read) {
        AccessLocation location = access.execute(type, foreign, offset);
        return read.execute(location.identifier, location, accessType);
    }

    @Specialization(guards = "type == null")
    Object doUnknownType(@SuppressWarnings("unused") LLVMInteropType.Structured type, Object foreign, long offset, ForeignToLLVMType accessType,
                    @Cached ReadLocationNode read) {
        // type unknown: fall back to "array of unknown value type"
        AccessLocation location = new AccessLocation(foreign, Long.divideUnsigned(offset, accessType.getSizeInBytes()), null);
        return read.execute(location.identifier, location, accessType);
    }

    @GenerateUncached
    abstract static class ReadLocationNode extends LLVMNode {

        abstract Object execute(Object identifier, AccessLocation location, ForeignToLLVMType accessType);

        @Specialization(limit = "3")
        Object readMember(String name, AccessLocation location, ForeignToLLVMType accessType,
                        @CachedLibrary("location.base") InteropLibrary interop,
                        @Cached ToLLVM toLLVM,
                        @Cached BranchProfile exception) {
            assert name == location.identifier;
            try {
                Object ret = interop.readMember(location.base, name);
                return toLLVM.executeWithType(ret, location.type, accessType);
            } catch (UnsupportedMessageException ex) {
                exception.enter();
                throw new LLVMPolyglotException(this, "Member '%s' not found", name);
            } catch (UnknownIdentifierException ex) {
                exception.enter();
                throw new LLVMPolyglotException(this, "Can not read member '%s'", name);
            }
        }

        @Specialization(guards = "isLocationTypeNullOrSameSize(location, accessType)", limit = "3")
        Object readArrayElementTypeMatch(long identifier, AccessLocation location, ForeignToLLVMType accessType,
                        @CachedLibrary("location.base") InteropLibrary interop,
                        @Cached ToLLVM toLLVM,
                        @Cached BranchProfile exception) {
            assert identifier == (Long) location.identifier;
            long idx = identifier;
            try {
                Object ret = interop.readArrayElement(location.base, idx);
                return toLLVM.executeWithType(ret, location.type, accessType);
            } catch (InvalidArrayIndexException ex) {
                exception.enter();
                throw new LLVMPolyglotException(this, "Invalid array index %d", idx);
            } catch (UnsupportedMessageException ex) {
                exception.enter();
                throw new LLVMPolyglotException(this, "Cannot read array element %d", idx);
            }
        }

        @Specialization(guards = {"!isLocationTypeNullOrSameSize(location, accessType)", "locationType.isI8()", "accessTypeSizeInBytes > 1"}, limit = "3")
        Object readArrayElementFromI8(long identifier, AccessLocation location, ForeignToLLVMType accessType,
                        @CachedLibrary("location.base") InteropLibrary interop,
                        @Cached ToLLVM toLLVM,
                        @Cached ReinterpretLongAsLLVM fromLongToLLVM,
                        @Cached BranchProfile exception,
                        @Cached BranchProfile outOfBounds,
                        @Bind("location.type.getKind().foreignToLLVMType") ForeignToLLVMType locationType,
                        @Bind("accessType.getSizeInBytes()") int accessTypeSizeInBytes) {
            assert identifier == (Long) location.identifier;
            long idx = identifier;
            assert locationType == ForeignToLLVMType.I8;
            long res = 0;
            try {
                // TODO (je) this currently assumes little endian (GR-24919)
                for (int i = 0; i < accessTypeSizeInBytes; i++, idx++) {
                    Object ret = interop.readArrayElement(location.base, idx);
                    Object toLLVMValue = toLLVM.executeWithType(ret, LLVMInteropType.ValueKind.I8.type, LLVMInteropType.ValueKind.I8.foreignToLLVMType);
                    res |= Byte.toUnsignedLong((byte) toLLVMValue) << (8 * i);
                }
                return fromLongToLLVM.executeWithAccessType(res, accessType);
            } catch (InvalidArrayIndexException ex) {
                if (idx != identifier) {
                    outOfBounds.enter();
                    /*
                     * Reading out of bounds but we read at least one byte. Simulate native
                     * allocation alignment, i.e., ignore the out-of-bounds reads.
                     */
                    return fromLongToLLVM.executeWithAccessType(res, accessType);
                }
                exception.enter();
                throw new LLVMPolyglotException(this, "Invalid array index %d", idx);
            } catch (UnsupportedMessageException ex) {
                exception.enter();
                throw new LLVMPolyglotException(this, "Cannot read array element %d", idx);
            }
        }

        static boolean isLocationTypeNullOrSameSize(AccessLocation location, ForeignToLLVMType accessType) {
            return location.type == null || location.type.getKind().foreignToLLVMType.getSizeInBytes() == accessType.getSizeInBytes();
        }

        @Fallback
        Object fallback(@SuppressWarnings("unused") Object identifier, AccessLocation location, ForeignToLLVMType accessType) {
            assert location.type != null;
            throw new LLVMPolyglotException(this, "Cannot read %d byte(s) from foreign object of element size %d", accessType.getSizeInBytes(),
                            location.type.getKind().foreignToLLVMType.getSizeInBytes());
        }
    }

    @GenerateUncached
    abstract static class ReinterpretLongAsLLVM extends LLVMNode {
        abstract Object executeWithAccessType(long value, ForeignToLLVMType accessType);

        @Specialization(guards = "accessType.isI16()")
        short toI16(long value, @SuppressWarnings("unused") ForeignToLLVMType accessType) {
            return (short) value;
        }

        @Specialization(guards = "accessType.isI32()")
        int toI32(long value, @SuppressWarnings("unused") ForeignToLLVMType accessType) {
            return (int) value;
        }

        @Specialization(guards = "accessType.isI64()")
        long toI64(long value, @SuppressWarnings("unused") ForeignToLLVMType accessType) {
            return value;
        }

        @Specialization(guards = "accessType.isFloat()")
        float toFloat(long value, @SuppressWarnings("unused") ForeignToLLVMType accessType) {
            return Float.intBitsToFloat((int) value);
        }

        @Specialization(guards = "accessType.isDouble()")
        double toDouble(long value, @SuppressWarnings("unused") ForeignToLLVMType accessType) {
            return Double.longBitsToDouble(value);
        }

        @Specialization(guards = "accessType.isPointer()")
        Object toPointer(long value, @SuppressWarnings("unused") ForeignToLLVMType accessType) {
            return LLVMNativePointer.create(value);
        }

        @Fallback
        Object fallback(@SuppressWarnings("unused") long value, ForeignToLLVMType accessType) {
            CompilerDirectives.transferToInterpreter();
            throw new LLVMPolyglotException(this, "Unexpected access type %s", accessType);
        }

    }

}
