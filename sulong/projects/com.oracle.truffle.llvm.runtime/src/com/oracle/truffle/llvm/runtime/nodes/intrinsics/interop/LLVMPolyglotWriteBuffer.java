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
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMAsForeignLibrary;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI16StoreNode.LLVMI16OffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI32StoreNode.LLVMI32OffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI64StoreNode.LLVMI64OffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI8StoreNode.LLVMI8OffsetStoreNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

public abstract class LLVMPolyglotWriteBuffer extends LLVMNode {

    protected boolean isWritableBufferPointer(LLVMPointer receiver) {
        LLVMInteropType exportType = receiver.getExportType();
        return (exportType instanceof LLVMInteropType.Buffer) && ((LLVMInteropType.Buffer) exportType).isWritable();
    }

    protected boolean inBounds(LLVMPointer receiver, long byteOffset, int length) {
        return byteOffset >= 0 && byteOffset + length <= receiver.getExportType().getSize();
    }

    protected boolean isNativeOrder(ByteOrder order) {
        return order == ByteOrder.nativeOrder();
    }

    protected void throwUnsupported(@SuppressWarnings("unused") LLVMPointer receiver, @SuppressWarnings("unused") long byteOffset) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @GenerateUncached
    public abstract static class LLVMPolyglotWriteBufferByteNode extends LLVMPolyglotWriteBuffer {
        public abstract void execute(LLVMPointer receiver, long byteOffset, byte value) throws UnsupportedMessageException, InvalidBufferOffsetException;

        @Specialization(guards = "isWritableBufferPointer(receiver)")
        public void doNative(LLVMNativePointer receiver, long byteOffset, byte value,
                        @Cached BranchProfile exception,
                        @Cached LLVMI8OffsetStoreNode storeOffset) throws InvalidBufferOffsetException {
            if (inBounds(receiver, byteOffset, Byte.BYTES)) {
                storeOffset.executeWithTarget(receiver, byteOffset, value);
            } else {
                exception.enter();
                throw InvalidBufferOffsetException.create(byteOffset, Long.BYTES);
            }
        }

        @GenerateAOT.Exclude
        @Specialization(guards = {"isWritableBufferPointer(pointer)", "!foreignsLib.isForeign(pointer)"}, limit = "3")
        public void doNonForeign(LLVMManagedPointer pointer, long byteOffset, byte value,
                        @Cached LLVMI8OffsetStoreNode storeOffset,
                        @Cached BranchProfile exception,
                        @SuppressWarnings("unused") @CachedLibrary("pointer") LLVMAsForeignLibrary foreignsLib) throws InvalidBufferOffsetException {
            if (inBounds(pointer, byteOffset, Byte.BYTES)) {
                storeOffset.executeWithTarget(pointer, byteOffset, value);
            } else {
                exception.enter();
                throw InvalidBufferOffsetException.create(byteOffset, Long.BYTES);
            }
        }

        @Fallback
        public void unsupported(LLVMPointer receiver, long byteOffset, @SuppressWarnings("unused") byte value) throws UnsupportedMessageException {
            throwUnsupported(receiver, byteOffset);
        }
    }

    @GenerateUncached
    public abstract static class LLVMPolyglotWriteBufferShortNode extends LLVMPolyglotWriteBuffer {
        public abstract void execute(LLVMPointer receiver, ByteOrder order, long byteOffset, short value) throws UnsupportedMessageException, InvalidBufferOffsetException;

        @Specialization(guards = "isWritableBufferPointer(receiver)")
        public void doNative(LLVMNativePointer receiver, ByteOrder order, long byteOffset, short value,
                        @Cached BranchProfile exception,
                        @Cached LLVMI16OffsetStoreNode storeOffset) throws InvalidBufferOffsetException {
            if (inBounds(receiver, byteOffset, Short.BYTES)) {
                storeOffset.executeWithTarget(receiver, byteOffset, isNativeOrder(order) ? value : Short.reverseBytes(value));
            } else {
                exception.enter();
                throw InvalidBufferOffsetException.create(byteOffset, Long.BYTES);
            }
        }

        @GenerateAOT.Exclude
        @Specialization(guards = {"isWritableBufferPointer(pointer)", "!foreignsLib.isForeign(pointer)"}, limit = "3")
        public void doNonForeign(LLVMManagedPointer pointer, ByteOrder order, long byteOffset, short value,
                        @Cached LLVMI16OffsetStoreNode storeOffset,
                        @Cached BranchProfile exception,
                        @SuppressWarnings("unused") @CachedLibrary("pointer") LLVMAsForeignLibrary foreignsLib) throws InvalidBufferOffsetException {
            if (inBounds(pointer, byteOffset, Short.BYTES)) {
                storeOffset.executeWithTarget(pointer, byteOffset, isNativeOrder(order) ? value : Short.reverseBytes(value));
            } else {
                exception.enter();
                throw InvalidBufferOffsetException.create(byteOffset, Long.BYTES);
            }
        }

        @Fallback
        public void unsupported(LLVMPointer receiver, @SuppressWarnings("unused") ByteOrder order, long byteOffset, @SuppressWarnings("unused") short value) throws UnsupportedMessageException {
            throwUnsupported(receiver, byteOffset);
        }
    }

    @GenerateUncached
    public abstract static class LLVMPolyglotWriteBufferIntNode extends LLVMPolyglotWriteBuffer {
        public abstract void execute(LLVMPointer receiver, ByteOrder order, long byteOffset, int value) throws UnsupportedMessageException, InvalidBufferOffsetException;

        @Specialization(guards = "isWritableBufferPointer(receiver)")
        public void doNative(LLVMNativePointer receiver, ByteOrder order, long byteOffset, int value,
                        @Cached BranchProfile exception,
                        @Cached LLVMI32OffsetStoreNode storeOffset) throws InvalidBufferOffsetException {
            if (inBounds(receiver, byteOffset, Integer.BYTES)) {
                storeOffset.executeWithTarget(receiver, byteOffset, isNativeOrder(order) ? value : Integer.reverseBytes(value));
            } else {
                exception.enter();
                throw InvalidBufferOffsetException.create(byteOffset, Long.BYTES);
            }
        }

        @GenerateAOT.Exclude
        @Specialization(guards = {"isWritableBufferPointer(pointer)", "!foreignsLib.isForeign(pointer)"}, limit = "3")
        public void doNonForeign(LLVMManagedPointer pointer, ByteOrder order, long byteOffset, int value,
                        @Cached LLVMI32OffsetStoreNode storeOffset,
                        @Cached BranchProfile exception,
                        @SuppressWarnings("unused") @CachedLibrary("pointer") LLVMAsForeignLibrary foreignsLib) throws InvalidBufferOffsetException {
            if (inBounds(pointer, byteOffset, Integer.BYTES)) {
                storeOffset.executeWithTarget(pointer, byteOffset, isNativeOrder(order) ? value : Integer.reverseBytes(value));
            } else {
                exception.enter();
                throw InvalidBufferOffsetException.create(byteOffset, Long.BYTES);
            }
        }

        @Fallback
        public void unsupported(LLVMPointer receiver, @SuppressWarnings("unused") ByteOrder order, long byteOffset, @SuppressWarnings("unused") int value) throws UnsupportedMessageException {
            throwUnsupported(receiver, byteOffset);
        }
    }

    @GenerateUncached
    public abstract static class LLVMPolyglotWriteBufferLongNode extends LLVMPolyglotWriteBuffer {
        public abstract void execute(LLVMPointer receiver, ByteOrder order, long byteOffset, long value) throws UnsupportedMessageException, InvalidBufferOffsetException;

        @Specialization(guards = "isWritableBufferPointer(receiver)")
        public void doNative(LLVMNativePointer receiver, ByteOrder order, long byteOffset, long value,
                        @Cached BranchProfile exception,
                        @Cached LLVMI64OffsetStoreNode storeOffset) throws InvalidBufferOffsetException {
            if (inBounds(receiver, byteOffset, Long.BYTES)) {
                storeOffset.executeWithTarget(receiver, byteOffset, isNativeOrder(order) ? value : Long.reverseBytes(value));
            } else {
                exception.enter();
                throw InvalidBufferOffsetException.create(byteOffset, Long.BYTES);
            }
        }

        @GenerateAOT.Exclude
        @Specialization(guards = {"isWritableBufferPointer(pointer)", "!foreignsLib.isForeign(pointer)"}, limit = "3")
        public void doNonForeign(LLVMManagedPointer pointer, ByteOrder order, long byteOffset, long value,
                        @Cached LLVMI64OffsetStoreNode storeOffset,
                        @Cached BranchProfile exception,
                        @SuppressWarnings("unused") @CachedLibrary("pointer") LLVMAsForeignLibrary foreignsLib) throws InvalidBufferOffsetException {
            if (inBounds(pointer, byteOffset, Long.BYTES)) {
                storeOffset.executeWithTarget(pointer, byteOffset, isNativeOrder(order) ? value : Long.reverseBytes(value));
            } else {
                exception.enter();
                throw InvalidBufferOffsetException.create(byteOffset, Long.BYTES);
            }
        }

        @Fallback
        public void unsupported(LLVMPointer receiver, @SuppressWarnings("unused") ByteOrder order, long byteOffset, @SuppressWarnings("unused") long value) throws UnsupportedMessageException {
            throwUnsupported(receiver, byteOffset);
        }
    }

    @GenerateUncached
    public abstract static class LLVMPolyglotWriteBufferFloatNode extends LLVMPolyglotWriteBuffer {
        public abstract void execute(LLVMPointer receiver, ByteOrder order, long byteOffset, float value) throws UnsupportedMessageException, InvalidBufferOffsetException;

        @Specialization(guards = "isWritableBufferPointer(receiver)")
        public void doNative(LLVMNativePointer receiver, ByteOrder order, long byteOffset, float value,
                        @Cached BranchProfile exception,
                        @Cached LLVMI32OffsetStoreNode storeOffset) throws InvalidBufferOffsetException {
            if (inBounds(receiver, byteOffset, Float.BYTES)) {
                int bits = Float.floatToRawIntBits(value);
                storeOffset.executeWithTarget(receiver, byteOffset, isNativeOrder(order) ? bits : Integer.reverseBytes(bits));
            } else {
                exception.enter();
                throw InvalidBufferOffsetException.create(byteOffset, Long.BYTES);
            }
        }

        @GenerateAOT.Exclude
        @Specialization(guards = {"isWritableBufferPointer(pointer)", "!foreignsLib.isForeign(pointer)"}, limit = "3")
        public void doNonForeign(LLVMManagedPointer pointer, ByteOrder order, long byteOffset, float value,
                        @Cached LLVMI32OffsetStoreNode storeOffset,
                        @Cached BranchProfile exception,
                        @SuppressWarnings("unused") @CachedLibrary("pointer") LLVMAsForeignLibrary foreignsLib) throws InvalidBufferOffsetException {
            if (inBounds(pointer, byteOffset, Float.BYTES)) {
                int bits = Float.floatToRawIntBits(value);
                storeOffset.executeWithTarget(pointer, byteOffset, isNativeOrder(order) ? bits : Integer.reverseBytes(bits));
            } else {
                exception.enter();
                throw InvalidBufferOffsetException.create(byteOffset, Long.BYTES);
            }
        }

        @Fallback
        public void unsupported(LLVMPointer receiver, @SuppressWarnings("unused") ByteOrder order, long byteOffset, @SuppressWarnings("unused") float value) throws UnsupportedMessageException {
            throwUnsupported(receiver, byteOffset);
        }
    }

    @GenerateUncached
    public abstract static class LLVMPolyglotWriteBufferDoubleNode extends LLVMPolyglotWriteBuffer {
        public abstract void execute(LLVMPointer receiver, ByteOrder order, long byteOffset, double value) throws UnsupportedMessageException, InvalidBufferOffsetException;

        @Specialization(guards = "isWritableBufferPointer(receiver)")
        public void doNative(LLVMNativePointer receiver, ByteOrder order, long byteOffset, double value,
                        @Cached BranchProfile exception,
                        @Cached LLVMI64OffsetStoreNode storeOffset) throws InvalidBufferOffsetException {
            if (inBounds(receiver, byteOffset, Double.BYTES)) {
                long bits = Double.doubleToRawLongBits(value);
                storeOffset.executeWithTarget(receiver, byteOffset, isNativeOrder(order) ? bits : Long.reverseBytes(bits));
            } else {
                exception.enter();
                throw InvalidBufferOffsetException.create(byteOffset, Long.BYTES);
            }
        }

        @GenerateAOT.Exclude
        @Specialization(guards = {"isWritableBufferPointer(pointer)", "!foreignsLib.isForeign(pointer)"}, limit = "3")
        public void doNonForeign(LLVMManagedPointer pointer, ByteOrder order, long byteOffset, double value,
                        @Cached LLVMI64OffsetStoreNode storeOffset,
                        @Cached BranchProfile exception,
                        @SuppressWarnings("unused") @CachedLibrary("pointer") LLVMAsForeignLibrary foreignsLib) throws InvalidBufferOffsetException {
            if (inBounds(pointer, byteOffset, Double.BYTES)) {
                long bits = Double.doubleToRawLongBits(value);
                storeOffset.executeWithTarget(pointer, byteOffset, isNativeOrder(order) ? bits : Long.reverseBytes(bits));
            } else {
                exception.enter();
                throw InvalidBufferOffsetException.create(byteOffset, Long.BYTES);
            }
        }

        @Fallback
        public void unsupported(LLVMPointer receiver, @SuppressWarnings("unused") ByteOrder order, long byteOffset, @SuppressWarnings("unused") double value) throws UnsupportedMessageException {
            throwUnsupported(receiver, byteOffset);
        }
    }

}
