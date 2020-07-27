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
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.interop.LLVMDataEscapeNode;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropAccessNode.AccessLocation;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;

@GenerateUncached
public abstract class LLVMInteropWriteNode extends LLVMNode {

    public static LLVMInteropWriteNode create() {
        return LLVMInteropWriteNodeGen.create();
    }

    public abstract void execute(LLVMInteropType.Structured baseType, Object foreign, long offset, Object value, ForeignToLLVMType writeType);

    @Specialization(guards = "type != null")
    void doKnownType(LLVMInteropType.Structured type, Object foreign, long offset, Object value, ForeignToLLVMType writeType,
                    @Cached LLVMInteropAccessNode access,
                    @Cached WriteLocationNode write) {
        AccessLocation location = access.execute(type, foreign, offset);
        write.execute(location.identifier, location, value, writeType);
    }

    @Specialization(guards = "type == null")
    void doUnknownType(@SuppressWarnings("unused") LLVMInteropType.Structured type, Object foreign, long offset, Object value, ForeignToLLVMType writeType,
                    @Cached WriteLocationNode write) {
        // type unknown: fall back to "array of unknown value type"
        AccessLocation location = new AccessLocation(foreign, Long.divideUnsigned(offset, writeType.getSizeInBytes()), null);
        write.execute(location.identifier, location, value, writeType);
    }

    @GenerateUncached
    abstract static class WriteLocationNode extends LLVMNode {

        abstract void execute(Object identifier, AccessLocation location, Object value, ForeignToLLVMType writeType);

        @Specialization(limit = "3")
        void writeMember(String identifier, AccessLocation location, Object value, @SuppressWarnings("unused") ForeignToLLVMType writeType,
                        @CachedLibrary("location.base") InteropLibrary interop,
                        @Cached ConvertOutgoingNode convertOutgoing,
                        @Cached BranchProfile exception) {
            assert identifier == location.identifier;
            try {
                interop.writeMember(location.base, identifier, convertOutgoing.execute(value, location.type, writeType));
            } catch (UnsupportedMessageException ex) {
                exception.enter();
                throw new LLVMPolyglotException(this, "Can not write member '%s'.", identifier);
            } catch (UnknownIdentifierException ex) {
                exception.enter();
                throw new LLVMPolyglotException(this, "Member '%s' not found.", identifier);
            } catch (UnsupportedTypeException ex) {
                exception.enter();
                throw new LLVMPolyglotException(this, "Wrong type writing to member '%s'.", identifier);
            }
        }

        @Specialization(guards = "isLocationTypeNullOrSameSize(location, writeType)", limit = "3")
        void writeArrayElementTypeMatch(long identifier, AccessLocation location, Object value, ForeignToLLVMType writeType,
                        @CachedLibrary("location.base") InteropLibrary interop,
                        @Cached ConvertOutgoingNode convertOutgoing,
                        @Cached BranchProfile exception) {
            assert identifier == (Long) location.identifier;
            long idx = identifier;
            try {
                interop.writeArrayElement(location.base, idx, convertOutgoing.execute(value, location.type, writeType));
            } catch (InvalidArrayIndexException ex) {
                exception.enter();
                throw new LLVMPolyglotException(this, "Invalid array index %d.", idx);
            } catch (UnsupportedMessageException ex) {
                exception.enter();
                throw new LLVMPolyglotException(this, "Can not write array element %d.", idx);
            } catch (UnsupportedTypeException ex) {
                exception.enter();
                throw new LLVMPolyglotException(this, "Wrong type writing to array element %d.", idx);
            }
        }

        @Specialization(guards = {"!isLocationTypeNullOrSameSize(location, writeType)", "locationType.isI8()", "writeTypeSizeInBytes > 1"}, limit = "3")
        void writeArrayElementToI8(long identifier, AccessLocation location, Object value, ForeignToLLVMType writeType,
                        @CachedLibrary("location.base") InteropLibrary interop,
                        @Cached ReinterpretLLVMAsLong toLong,
                        @Cached BranchProfile exception,
                        @Bind("location.type.getKind().foreignToLLVMType") @SuppressWarnings("unused") ForeignToLLVMType locationType,
                        @Bind("writeType.getSizeInBytes()") int writeTypeSizeInBytes) {
            assert identifier == (Long) location.identifier;
            long idx = identifier;
            try {
                long longValue = toLong.executeWithWriteType(value, writeType);
                // TODO (je) this currently assumes little endian (GR-24919)
                for (int i = 0; i < writeTypeSizeInBytes; i++, idx++) {
                    interop.writeArrayElement(location.base, idx, (byte) longValue);
                    longValue >>= 8;
                }
            } catch (InvalidArrayIndexException ex) {
                exception.enter();
                throw new LLVMPolyglotException(this, "Invalid array index %d.", idx);
            } catch (UnsupportedMessageException ex) {
                exception.enter();
                throw new LLVMPolyglotException(this, "Can not write array element %d.", idx);
            } catch (UnsupportedTypeException ex) {
                exception.enter();
                throw new LLVMPolyglotException(this, "Wrong type writing to array element %d.", idx);
            }
        }

        @Fallback
        void fallback(@SuppressWarnings("unused") Object identifier, AccessLocation location, Object value, ForeignToLLVMType writeType) {
            assert location.type != null;
            throw new LLVMPolyglotException(this, "Cannot write object '%s' of size %d byte(s) to foreign object of element size %d", value, writeType.getSizeInBytes(),
                            location.type.getKind().foreignToLLVMType.getSizeInBytes());
        }

        static boolean isLocationTypeNullOrSameSize(AccessLocation location, ForeignToLLVMType accessType) {
            return location.type == null || location.type.getKind().foreignToLLVMType.getSizeInBytes() == accessType.getSizeInBytes();
        }
    }

    @ImportStatic(ForeignToLLVMType.class)
    @GenerateUncached
    abstract static class ReinterpretLLVMAsLong extends LLVMNode {
        abstract long executeWithWriteType(Object value, ForeignToLLVMType writeType);

        @Specialization(guards = "writeType.getSizeInBytes() == 2")
        long doI16(Object value, @SuppressWarnings("unused") ForeignToLLVMType writeType,
                        @Cached LLVMDataEscapeNode.LLVMI16DataEscapeNode dataEscape) {
            return dataEscape.executeWithTargetI16(value);
        }

        @Specialization(guards = "writeType.getSizeInBytes() == 4")
        long doI32(Object value, @SuppressWarnings("unused") ForeignToLLVMType writeType,
                        @Cached LLVMDataEscapeNode.LLVMI32DataEscapeNode dataEscape) {
            return dataEscape.executeWithTargetI32(value);
        }

        @Specialization(guards = "writeType.getSizeInBytes() == 8")
        long doI64(Object value, @SuppressWarnings("unused") ForeignToLLVMType writeType,
                        @Cached LLVMDataEscapeNode.LLVMI64DataEscapeNode dataEscape) {
            return dataEscape.executeWithTargetI64(value);
        }

        @Fallback
        long fallback(@SuppressWarnings("unused") Object value, ForeignToLLVMType writeType) {
            CompilerDirectives.transferToInterpreter();
            throw new LLVMPolyglotException(this, "Unexpected access type %s", writeType);
        }

    }

    @GenerateUncached
    abstract static class ConvertOutgoingNode extends LLVMNode {

        abstract Object execute(Object value, LLVMInteropType.Value outgoingType, ForeignToLLVMType writeType);

        @Specialization(limit = "3", guards = {"outgoingType != null", "cachedOutgoingType == outgoingType.kind.foreignToLLVMType", "type.getSizeInBytes() == cachedOutgoingType.getSizeInBytes()"})
        Object doKnownType(Object value, LLVMInteropType.Value outgoingType, @SuppressWarnings("unused") ForeignToLLVMType type,
                        @Cached("outgoingType.kind.foreignToLLVMType") @SuppressWarnings("unused") ForeignToLLVMType cachedOutgoingType,
                        @Cached(parameters = "cachedOutgoingType") LLVMDataEscapeNode dataEscape) {
            return dataEscape.executeWithType(value, outgoingType.baseType);
        }

        static boolean typeMismatch(LLVMInteropType.Value outgoingType, ForeignToLLVMType writeType) {
            if (outgoingType == null) {
                return true;
            } else {
                return outgoingType.getSize() != writeType.getSizeInBytes();
            }
        }

        @Specialization(limit = "3", guards = {"typeMismatch(outgoingType, cachedType)", "cachedType == type"})
        @SuppressWarnings("unused")
        Object doUnknownType(Object value, LLVMInteropType.Value outgoingType, ForeignToLLVMType type,
                        @Cached("type") ForeignToLLVMType cachedType,
                        @Cached(parameters = "type") LLVMDataEscapeNode dataEscape) {
            return dataEscape.executeWithTarget(value);
        }
    }
}
