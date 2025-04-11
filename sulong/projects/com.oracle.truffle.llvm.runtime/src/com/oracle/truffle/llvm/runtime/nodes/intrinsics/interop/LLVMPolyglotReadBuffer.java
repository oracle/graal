/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop;

import java.nio.ByteOrder;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InvalidBufferOffsetException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMAsForeignLibrary;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI16LoadNode.LLVMI16OffsetLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI32LoadNode.LLVMI32OffsetLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI64LoadNode.LLVMI64OffsetLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI8LoadNode.LLVMI8OffsetLoadNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

public abstract class LLVMPolyglotReadBuffer extends LLVMNode {

    protected boolean isBufferPointer(LLVMPointer receiver) {
        return receiver.getExportType() instanceof LLVMInteropType.Buffer;
    }

    protected boolean inBounds(LLVMPointer receiver, long byteOffset, int length) {
        return length >= 0 && byteOffset >= 0 && byteOffset + length <= receiver.getExportType().getSize();
    }

    protected boolean isNativeOrder(ByteOrder order) {
        return order == ByteOrder.nativeOrder();
    }

    protected void throwUnsupported(@SuppressWarnings("unused") LLVMPointer receiver, @SuppressWarnings("unused") long byteOffset) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    protected void throwUnsupported(@SuppressWarnings("unused") LLVMPointer receiver, @SuppressWarnings("unused") long byteOffset, @SuppressWarnings("unused") byte[] destination,
                    @SuppressWarnings("unused") int destinationOffset, @SuppressWarnings("unused") int length) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @GenerateUncached
    public abstract static class LLVMPolyglotReadBufferByteNode extends LLVMPolyglotReadBuffer {
        public abstract byte execute(LLVMPointer receiver, long byteOffset) throws UnsupportedMessageException, InvalidBufferOffsetException;

        @Specialization(guards = "isBufferPointer(receiver)")
        public byte doNative(LLVMNativePointer receiver, long byteOffset,
                        @Cached BranchProfile exception,
                        @Cached LLVMI8OffsetLoadNode loadOffset) throws InvalidBufferOffsetException {
            if (inBounds(receiver, byteOffset, Byte.BYTES)) {
                return loadOffset.executeWithTarget(receiver, byteOffset);
            } else {
                exception.enter();
                throw InvalidBufferOffsetException.create(byteOffset, Byte.BYTES);
            }
        }

        @GenerateAOT.Exclude
        @Specialization(guards = {"isBufferPointer(pointer)", "!foreignsLib.isForeign(pointer)"}, limit = "3")
        public byte nonForeignRead(LLVMManagedPointer pointer, long byteOffset,
                        @Cached LLVMI8OffsetLoadNode loadOffset,
                        @Cached BranchProfile exception,
                        @SuppressWarnings("unused") @CachedLibrary("pointer") LLVMAsForeignLibrary foreignsLib) throws InvalidBufferOffsetException {
            if (inBounds(pointer, byteOffset, Byte.BYTES)) {
                return loadOffset.executeWithTarget(pointer, byteOffset);
            } else {
                exception.enter();
                throw InvalidBufferOffsetException.create(byteOffset, Byte.BYTES);
            }
        }

        @Fallback
        public byte unsupported(LLVMPointer receiver, long byteOffset) throws UnsupportedMessageException {
            throwUnsupported(receiver, byteOffset);
            return 0;
        }
    }

    @GenerateUncached
    public abstract static class LLVMPolyglotReadBufferNode extends LLVMPolyglotReadBuffer {
        public abstract void execute(LLVMPointer receiver, long byteOffset, byte[] destination, int destinationOffset, int length) throws UnsupportedMessageException, InvalidBufferOffsetException;

        @Specialization(guards = "isBufferPointer(receiver)")
        public void doNative(LLVMNativePointer receiver, long byteOffset, byte[] destination, int destinationOffset, int length,
                        @Cached BranchProfile exception,
                        @Cached LLVMI8OffsetLoadNode loadOffset) throws InvalidBufferOffsetException {
            if (inBounds(receiver, byteOffset, length)) {
                for (long offset = byteOffset; offset < byteOffset + length; offset++) {
                    destination[destinationOffset + (int) (offset - byteOffset)] = loadOffset.executeWithTarget(receiver, offset);
                }
            } else {
                exception.enter();
                throw InvalidBufferOffsetException.create(byteOffset, length);
            }
        }

        @GenerateAOT.Exclude
        @Specialization(guards = {"isBufferPointer(pointer)", "!foreignsLib.isForeign(pointer)"}, limit = "3")
        public void nonForeignRead(LLVMManagedPointer pointer, long byteOffset, byte[] destination, int destinationOffset, int length,
                        @Cached LLVMI8OffsetLoadNode loadOffset,
                        @Cached BranchProfile exception,
                        @SuppressWarnings("unused") @CachedLibrary("pointer") LLVMAsForeignLibrary foreignsLib) throws InvalidBufferOffsetException {
            if (inBounds(pointer, byteOffset, length)) {
                for (long offset = byteOffset; offset < byteOffset + length; offset++) {
                    destination[destinationOffset + (int) (offset - byteOffset)] = loadOffset.executeWithTarget(pointer, offset);
                }
            } else {
                exception.enter();
                throw InvalidBufferOffsetException.create(byteOffset, length);
            }
        }

        @Fallback
        public void unsupported(LLVMPointer receiver, long byteOffset, byte[] destination, int destinationOffset, int length) throws UnsupportedMessageException {
            throwUnsupported(receiver, byteOffset, destination, destinationOffset, length);
        }
    }

    @GenerateUncached
    public abstract static class LLVMPolyglotReadBufferShortNode extends LLVMPolyglotReadBuffer {
        public abstract short execute(LLVMPointer receiver, ByteOrder order, long byteOffset) throws UnsupportedMessageException, InvalidBufferOffsetException;

        @Specialization(guards = "isBufferPointer(receiver)")
        public short doNative(LLVMNativePointer receiver, ByteOrder order, long byteOffset,
                        @Cached BranchProfile exception,
                        @Cached LLVMI16OffsetLoadNode loadOffset) throws InvalidBufferOffsetException {
            if (inBounds(receiver, byteOffset, Short.BYTES)) {
                short v = loadOffset.executeWithTarget(receiver, byteOffset);
                return isNativeOrder(order) ? v : Short.reverseBytes(v);
            } else {
                exception.enter();
                throw InvalidBufferOffsetException.create(byteOffset, Short.BYTES);
            }
        }

        @GenerateAOT.Exclude
        @Specialization(guards = {"isBufferPointer(pointer)", "!foreignsLib.isForeign(pointer)"}, limit = "3")
        public short nonForeignRead(LLVMManagedPointer pointer, ByteOrder order, long byteOffset,
                        @Cached LLVMI16OffsetLoadNode loadOffset,
                        @Cached BranchProfile exception,
                        @SuppressWarnings("unused") @CachedLibrary("pointer") LLVMAsForeignLibrary foreignsLib) throws InvalidBufferOffsetException {
            if (inBounds(pointer, byteOffset, Short.BYTES)) {
                short v = loadOffset.executeWithTarget(pointer, byteOffset);
                return isNativeOrder(order) ? v : Short.reverseBytes(v);
            } else {
                exception.enter();
                throw InvalidBufferOffsetException.create(byteOffset, Short.BYTES);
            }
        }

        @Fallback
        public short unsupported(LLVMPointer receiver, @SuppressWarnings("unused") ByteOrder order, long byteOffset) throws UnsupportedMessageException {
            throwUnsupported(receiver, byteOffset);
            return 0;
        }
    }

    @GenerateUncached
    public abstract static class LLVMPolyglotReadBufferIntNode extends LLVMPolyglotReadBuffer {
        public abstract int execute(LLVMPointer receiver, ByteOrder order, long byteOffset) throws UnsupportedMessageException, InvalidBufferOffsetException;

        @Specialization(guards = "isBufferPointer(receiver)")
        public int doNative(LLVMNativePointer receiver, ByteOrder order, long byteOffset,
                        @Cached BranchProfile exception,
                        @Cached LLVMI32OffsetLoadNode loadOffset) throws InvalidBufferOffsetException {
            if (inBounds(receiver, byteOffset, Integer.BYTES)) {
                int v = loadOffset.executeWithTarget(receiver, byteOffset);
                return isNativeOrder(order) ? v : Integer.reverseBytes(v);
            } else {
                exception.enter();
                throw InvalidBufferOffsetException.create(byteOffset, Integer.BYTES);
            }
        }

        @GenerateAOT.Exclude
        @Specialization(guards = {"isBufferPointer(pointer)", "!foreignsLib.isForeign(pointer)"}, limit = "3")
        public int nonForeignRead(LLVMManagedPointer pointer, ByteOrder order, long byteOffset,
                        @Cached LLVMI32OffsetLoadNode loadOffset,
                        @Cached BranchProfile exception,
                        @SuppressWarnings("unused") @CachedLibrary("pointer") LLVMAsForeignLibrary foreignsLib) throws InvalidBufferOffsetException {
            if (inBounds(pointer, byteOffset, Integer.BYTES)) {
                int v = loadOffset.executeWithTarget(pointer, byteOffset);
                return isNativeOrder(order) ? v : Integer.reverseBytes(v);
            } else {
                exception.enter();
                throw InvalidBufferOffsetException.create(byteOffset, Integer.BYTES);
            }
        }

        @Fallback
        public int unsupported(LLVMPointer receiver, @SuppressWarnings("unused") ByteOrder order, long byteOffset) throws UnsupportedMessageException {
            throwUnsupported(receiver, byteOffset);
            return 0;
        }
    }

    @GenerateUncached
    public abstract static class LLVMPolyglotReadBufferLongNode extends LLVMPolyglotReadBuffer {
        public abstract long execute(LLVMPointer receiver, ByteOrder order, long byteOffset) throws UnsupportedMessageException, InvalidBufferOffsetException;

        @Specialization(guards = "isBufferPointer(receiver)")
        public long doNative(LLVMNativePointer receiver, ByteOrder order, long byteOffset,
                        @Cached BranchProfile exception,
                        @Cached LLVMI64OffsetLoadNode loadOffset) throws InvalidBufferOffsetException, UnsupportedMessageException {
            if (inBounds(receiver, byteOffset, Long.BYTES)) {
                try {
                    long v = loadOffset.executeWithTarget(receiver, byteOffset);
                    return isNativeOrder(order) ? v : Long.reverseBytes(v);
                } catch (UnexpectedResultException ex) {
                    throwUnsupported(receiver, byteOffset);
                    return 0;
                }
            } else {
                exception.enter();
                throw InvalidBufferOffsetException.create(byteOffset, Long.BYTES);
            }
        }

        @GenerateAOT.Exclude
        @Specialization(guards = {"isBufferPointer(pointer)", "!foreignsLib.isForeign(pointer)"}, limit = "3")
        public long nonForeignRead(LLVMManagedPointer pointer, ByteOrder order, long byteOffset,
                        @Cached LLVMI64OffsetLoadNode loadOffset,
                        @Cached BranchProfile exception,
                        @SuppressWarnings("unused") @CachedLibrary("pointer") LLVMAsForeignLibrary foreignsLib) throws InvalidBufferOffsetException, UnsupportedMessageException {
            if (inBounds(pointer, byteOffset, Long.BYTES)) {
                try {
                    long v = loadOffset.executeWithTarget(pointer, byteOffset);
                    return isNativeOrder(order) ? v : Long.reverseBytes(v);
                } catch (UnexpectedResultException ex) {
                    throwUnsupported(pointer, byteOffset);
                    return 0;
                }
            } else {
                exception.enter();
                throw InvalidBufferOffsetException.create(byteOffset, Long.BYTES);
            }
        }

        @Fallback
        public long unsupported(LLVMPointer receiver, @SuppressWarnings("unused") ByteOrder order, long byteOffset) throws UnsupportedMessageException {
            throwUnsupported(receiver, byteOffset);
            return 0;
        }
    }

    @GenerateUncached
    public abstract static class LLVMPolyglotReadBufferFloatNode extends LLVMPolyglotReadBuffer {
        public abstract float execute(LLVMPointer receiver, ByteOrder order, long byteOffset) throws UnsupportedMessageException, InvalidBufferOffsetException;

        @Specialization(guards = "isBufferPointer(receiver)")
        public float doNative(LLVMNativePointer receiver, ByteOrder order, long byteOffset,
                        @Cached BranchProfile exception,
                        @Cached LLVMI32OffsetLoadNode loadOffset) throws InvalidBufferOffsetException {
            if (inBounds(receiver, byteOffset, Float.BYTES)) {
                int bits = loadOffset.executeWithTarget(receiver, byteOffset);
                return Float.intBitsToFloat(isNativeOrder(order) ? bits : Integer.reverseBytes(bits));
            } else {
                exception.enter();
                throw InvalidBufferOffsetException.create(byteOffset, Float.BYTES);
            }
        }

        @GenerateAOT.Exclude
        @Specialization(guards = {"isBufferPointer(pointer)", "!foreignsLib.isForeign(pointer)"}, limit = "3")
        public float nonForeignRead(LLVMManagedPointer pointer, ByteOrder order, long byteOffset,
                        @Cached LLVMI32OffsetLoadNode loadOffset,
                        @Cached BranchProfile exception,
                        @SuppressWarnings("unused") @CachedLibrary("pointer") LLVMAsForeignLibrary foreignsLib) throws InvalidBufferOffsetException {
            if (inBounds(pointer, byteOffset, Float.BYTES)) {
                int bits = loadOffset.executeWithTarget(pointer, byteOffset);
                return Float.intBitsToFloat(isNativeOrder(order) ? bits : Integer.reverseBytes(bits));
            } else {
                exception.enter();
                throw InvalidBufferOffsetException.create(byteOffset, Float.BYTES);
            }
        }

        @Fallback
        public float unsupported(LLVMPointer receiver, @SuppressWarnings("unused") ByteOrder order, long byteOffset) throws UnsupportedMessageException {
            throwUnsupported(receiver, byteOffset);
            return 0;
        }
    }

    @GenerateUncached
    public abstract static class LLVMPolyglotReadBufferDoubleNode extends LLVMPolyglotReadBuffer {
        public abstract double execute(LLVMPointer receiver, ByteOrder order, long byteOffset) throws UnsupportedMessageException, InvalidBufferOffsetException;

        @Specialization(guards = "isBufferPointer(receiver)")
        public double doNative(LLVMNativePointer receiver, ByteOrder order, long byteOffset,
                        @Cached BranchProfile exception,
                        @Cached LLVMI64OffsetLoadNode loadOffset) throws InvalidBufferOffsetException, UnsupportedMessageException {
            if (inBounds(receiver, byteOffset, Double.BYTES)) {
                try {
                    long bits = loadOffset.executeWithTarget(receiver, byteOffset);
                    return Double.longBitsToDouble(isNativeOrder(order) ? bits : Long.reverseBytes(bits));
                } catch (UnexpectedResultException ex) {
                    throwUnsupported(receiver, byteOffset);
                    return 0;
                }
            } else {
                exception.enter();
                throw InvalidBufferOffsetException.create(byteOffset, Double.BYTES);
            }
        }

        @GenerateAOT.Exclude
        @Specialization(guards = {"isBufferPointer(pointer)", "!foreignsLib.isForeign(pointer)"}, limit = "3")
        public double nonForeignRead(LLVMManagedPointer pointer, ByteOrder order, long byteOffset,
                        @Cached LLVMI64OffsetLoadNode loadOffset,
                        @Cached BranchProfile exception,
                        @SuppressWarnings("unused") @CachedLibrary("pointer") LLVMAsForeignLibrary foreignsLib) throws InvalidBufferOffsetException, UnsupportedMessageException {
            if (inBounds(pointer, byteOffset, Double.BYTES)) {
                try {
                    long bits = loadOffset.executeWithTarget(pointer, byteOffset);
                    return Double.longBitsToDouble(isNativeOrder(order) ? bits : Long.reverseBytes(bits));
                } catch (UnexpectedResultException ex) {
                    throwUnsupported(pointer, byteOffset);
                    return 0;
                }
            } else {
                exception.enter();
                throw InvalidBufferOffsetException.create(byteOffset, Double.BYTES);
            }
        }

        @Fallback
        public double unsupported(LLVMPointer receiver, @SuppressWarnings("unused") ByteOrder order, long byteOffset) throws UnsupportedMessageException {
            throwUnsupported(receiver, byteOffset);
            return 0;
        }
    }
}
