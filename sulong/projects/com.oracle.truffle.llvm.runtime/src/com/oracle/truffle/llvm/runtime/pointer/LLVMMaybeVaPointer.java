/*
 * Copyright (c) 2022, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.pointer;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.PlatformCapability;
import com.oracle.truffle.llvm.runtime.except.LLVMMemoryException;
import com.oracle.truffle.llvm.runtime.interop.LLVMInternalTruffleObject;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMAsForeignLibrary;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedReadLibrary;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedWriteLibrary;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMNativeLibrary;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVAListNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVaListLibrary;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVaListStorage;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMPointerLoadNode.LLVMPointerOffsetLoadNode;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.Type;

import static com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMDoubleLoadNode.*;
import static com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI32LoadNode.*;
import static com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI64LoadNode.*;
import static com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMPointerStoreNode.*;

/**
 * On platforms such as on Windows VA lists objects are created by allocating a pointer sized object
 * on the stack. We would like the VA list to be a managed object so that we can store managed
 * function arguments in the va list. When allocating pointer sized objects using `alloca`, we
 * create a managed pointer to this class. If the class instance is used as a VA list (i.e. we call
 * va_start on it), we continue to treat it as a managed pointer to the VA list. Otherwise, it
 * performs the equivalent native memory operation on the underlying pointer. Any out of spec usage
 * should cause it to revert to the native pointer behaviour.
 *
 * <code>
 * %1 = alloca i8*
 * %2 = bitcast i8** %1
 * va_start(%2)
 * </code>
 *
 */
@ExportLibrary(InteropLibrary.class)
@ExportLibrary(value = LLVMVaListLibrary.class, useForAOT = true, useForAOTPriority = 0)
@ExportLibrary(value = LLVMManagedReadLibrary.class, useForAOT = true, useForAOTPriority = 1)
@ExportLibrary(value = LLVMManagedWriteLibrary.class, useForAOT = true, useForAOTPriority = 2)
@ExportLibrary(value = LLVMAsForeignLibrary.class, useForAOT = true, useForAOTPriority = 3)
public final class LLVMMaybeVaPointer extends LLVMInternalTruffleObject {
    private final Assumption allocVAPointerAssumption;
    private final LLVMVAListNode allocaNode;
    private boolean wasVAListPointer = false;
    protected LLVMPointer address;

    private long nativeOffset = 0;
    private LLVMManagedPointer vaList;

    public static LLVMMaybeVaPointer createWithAlloca(LLVMPointer address, LLVMVAListNode allocaNode) {
        return new LLVMMaybeVaPointer(address, allocaNode, null);
    }

    public static LLVMMaybeVaPointer createWithStorage(LLVMPointer address, Object vaListStorage) {
        return new LLVMMaybeVaPointer(address, null, vaListStorage);
    }

    public static LLVMMaybeVaPointer createWithHeap(LLVMPointer address) {
        return new LLVMMaybeVaPointer(address, null, null);
    }

    private LLVMMaybeVaPointer(LLVMPointer address, LLVMVAListNode allocaNode, Object vaListStorage) {
        this.allocaNode = allocaNode;
        this.allocVAPointerAssumption = allocaNode == null ? null : allocaNode.getAssumption();
        this.address = address;
        if (vaListStorage != null) {
            this.vaList = LLVMManagedPointer.create(vaListStorage);
        }
    }

    @ExportMessage
    public boolean isPointer() {
        return vaList == null;
    }

    boolean isStoredOnHeap() {
        return allocaNode == null;
    }

    @ExportMessage
    public long asPointer() throws UnsupportedMessageException {
        if (isPointer()) {
            return getAddress();
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    public long getAddress() {
        // this address should only be accessed if we are not dealing with a managed va_list
        assert isPointer();
        return LLVMNativePointer.cast(address).asNative();
    }

    private Object createVaListStorage() {
        return LLVMLanguage.getContext().getLanguage().getActiveConfiguration().getCapability(PlatformCapability.class).createActualVAListStorage();
    }

    @ExportMessage
    static class Initialize {
        static boolean isManagedPtr(Object obj) {
            return LLVMManagedPointer.isInstance(obj);
        }

        @Specialization(guards = {"self.isStoredOnHeap()", "isManagedPtr(self.address)"})
        static void initializeOnHeapManaged(LLVMMaybeVaPointer self, Object[] realArguments, int numberOfExplicitArguments, Frame frame,
                       @CachedLibrary(limit = "3") LLVMManagedWriteLibrary writeLibrary,
                       @CachedLibrary(limit = "3") LLVMVaListLibrary vaListLibrary) {
            Object vaListInstance = self.initializeBase(realArguments, numberOfExplicitArguments, frame, vaListLibrary);

            writeLibrary.writeGenericI64(((LLVMManagedPointer) self.address).getObject(), 0, vaListInstance);
        }
        @Specialization(guards = "self.isStoredOnHeap()")
        static void initializeOnHeapNative(LLVMMaybeVaPointer self, Object[] realArguments, int numberOfExplicitArguments, Frame frame,
                       @Cached LLVMPointerOffsetStoreNode storeNode,
                       @CachedLibrary(limit = "3") LLVMVaListLibrary vaListLibrary) {
            Object vaListInstance = self.initializeBase(realArguments, numberOfExplicitArguments, frame, vaListLibrary);

            /* triggers toNative transition for vaListInstance */
            storeNode.executeWithTarget(self.address, 0, vaListInstance);
        }
        @Specialization(guards = "!self.isStoredOnHeap()")
        static void initializeStack(LLVMMaybeVaPointer self, Object[] realArguments, int numberOfExplicitArguments, Frame frame,
                        @CachedLibrary(limit = "3") LLVMVaListLibrary vaListLibrary) {
            self.initializeBase(realArguments, numberOfExplicitArguments, frame, vaListLibrary);

            /*
             * storing to stack memory would trigger a toNative transitions as well, but we can
             * avoid it as we intercept reads from that stack storage
            */
        }
    }

    Object initializeBase(Object[] realArguments, int numberOfExplicitArguments, Frame frame,
                    LLVMVaListLibrary vaListLibrary) {
        assert numberOfExplicitArguments <= realArguments.length;
        Object vaListInstance = createVaListStorage();
        vaListLibrary.initialize(vaListInstance, realArguments, numberOfExplicitArguments, frame);

        vaList = LLVMManagedPointer.create(vaListInstance);
        wasVAListPointer = true;
        return vaListInstance;
    }

    /**
     * Whenever an incompatible native access is performed on this object, this method is called to
     * prevent a VA list pointer from being allocated by the same alloca node next time. Limited
     * native access is permitted after calling the cleanup method.
     */
    protected void nativeObjectAccess() {
        if (isStoredOnHeap() || wasVAListPointer) {
            return;
        }
        if (allocVAPointerAssumption.isValid()) {
            allocVAPointerAssumption.invalidate();
        }
    }

    @ExportMessage
    static class Shift {
        @Specialization(guards = "self.isPointer()")
        static protected Object shiftNative(LLVMMaybeVaPointer self, Type type, Frame frame,
                        @Cached LLVMPointerOffsetLoadNode baseAddrLoadNode,
                        @Cached LLVMPointerOffsetLoadNode pointerOffsetLoadNode,
                        @Cached LLVMI32OffsetLoadNode i32OffsetLoadNode,
                        @Cached LLVMI64OffsetLoadNode i64OffsetLoadNode,
                        @Cached LLVMDoubleOffsetLoadNode doubleOffsetLoadNode) {
            Object ret = null;
            LLVMPointer baseAddr = baseAddrLoadNode.executeWithTarget(self.address, 0);
            if (PrimitiveType.DOUBLE == type) {
                ret = doubleOffsetLoadNode.executeWithTarget(baseAddr, self.nativeOffset);
            } else if (PrimitiveType.I32 == type) {
                ret = i32OffsetLoadNode.executeWithTarget(baseAddr, self.nativeOffset);
            } else if (PrimitiveType.I64 == type) {
                try {
                    ret = i64OffsetLoadNode.executeWithTarget(baseAddr, self.nativeOffset);
                } catch (UnexpectedResultException e) {
                    CompilerDirectives.shouldNotReachHere();
                }
            } else if (type instanceof PointerType) {
                ret = pointerOffsetLoadNode.executeWithTarget(baseAddr, self.nativeOffset);
            } else {
                CompilerDirectives.transferToInterpreter();
                CompilerDirectives.shouldNotReachHere("MaybeVaPointer.shift: not implemented: " + type);
            }

            self.nativeOffset += Long.BYTES;
            return ret;
        }

        @Specialization(guards = "!self.isPointer()")
        @GenerateAOT.Exclude
        static protected Object shiftStorage(LLVMMaybeVaPointer self, Type type, Frame frame,
                        @CachedLibrary(limit = "3") LLVMManagedReadLibrary readLibrary) {
            assert self.wasVAListPointer;

            Object vaListStorage = self.vaList.getObject();
            Object ret = null;
            long offset = self.vaList.getOffset();
            if (PrimitiveType.DOUBLE == type) {
                ret = readLibrary.readDouble(vaListStorage, offset);
            } else if (PrimitiveType.I32 == type) {
                ret = readLibrary.readI32(vaListStorage, offset);
            } else if (PrimitiveType.I64 == type) {
                try {
                    ret = readLibrary.readI64(vaListStorage, offset);
                } catch (UnexpectedResultException e) {
                    CompilerDirectives.shouldNotReachHere();
                }
            } else if (type instanceof PointerType) {
                ret = readLibrary.readPointer(vaListStorage, offset);
            } else {
                CompilerDirectives.transferToInterpreter();
                CompilerDirectives.shouldNotReachHere("MaybeVaPointer.shift: not implemented: " + type);
            }
            self.vaList = self.vaList.increment(Long.BYTES);
            return ret;
        }
    }

    @ExportMessage
    static class Copy {
        @Specialization(guards = {"self.isStoredOnHeap()"})
        static void copyHeapNative(LLVMMaybeVaPointer self, LLVMMaybeVaPointer other, @SuppressWarnings("unused") Frame frame,
                        @Cached LLVMPointerOffsetLoadNode offsetLoadNode,
                        @Cached LLVMPointerOffsetStoreNode offsetStoreNode) {
            LLVMPointer vaListPtr = offsetLoadNode.executeWithTarget(self.address, 0);
            offsetStoreNode.executeWithTarget(other.address, 0, vaListPtr);
        }

        @Specialization
        static void copyStack(LLVMMaybeVaPointer self, LLVMMaybeVaPointer other, @SuppressWarnings("unused") Frame frame) {
            other.vaList = self.vaList;
        }
    }

    @ExportMessage
    void cleanup(@SuppressWarnings("unused") Frame frame) {
        // set this pointer to null
        vaList = null;
        address = null;
    }

    @SuppressWarnings("static-method")
    public int getSize() {
        return 1;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    public boolean hasArrayElements() {
        return !isPointer();
    }

    @ExportMessage
    static class GetArraySize {
        @Specialization(guards = "!self.isPointer()")
        static long getArraySizeVaList(LLVMMaybeVaPointer self) {
            return ((LLVMVaListStorage) self.vaList.getObject()).getArraySize();
        }
    }

    @ExportMessage
    static class IsArrayElementReadable {
        @Specialization(guards = "!self.isPointer()")
        static boolean isArrayElementReadableVaList(LLVMMaybeVaPointer self, long index) {
            return ((LLVMVaListStorage) self.vaList.getObject()).isArrayElementReadable(index);
        }
    }

    @ExportMessage
    static class ReadArrayElement {
        @Specialization(guards = "!self.isPointer()")
        static Object readArrayElementVaList(LLVMMaybeVaPointer self, long index,
                        @CachedLibrary(limit = "3") InteropLibrary interop) throws InvalidArrayIndexException, UnsupportedMessageException {
            return interop.readArrayElement(self.vaList.getObject(), index);
        }
    }

    @ExportMessage(name = "isReadable")
    @ExportMessage(name = "isWritable")
    @SuppressWarnings("static-method")
    boolean isAccessible() {
        return true;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    public boolean hasMembers() {
        return true;
    }

    @ExportMessage
    static class GetMembers {
        @Specialization(guards = "!self.isPointer()")
        static Object getMembers(LLVMMaybeVaPointer self, boolean includeInternal) {
            return ((LLVMVaListStorage) self.vaList.getObject()).getMembers(includeInternal);
        }
    }

    @ExportMessage
    static class IsMemberInvocable {
        @Specialization(guards = "!self.isPointer()")
        static boolean isMemberInvocable(LLVMMaybeVaPointer self, String member) {
            return ((LLVMVaListStorage) self.vaList.getObject()).isMemberInvocable(member);
        }
    }

    @ExportMessage
    static class InvokeMember {
        @Specialization(guards = "!self.isPointer()")
        static Object invokeMember(LLVMMaybeVaPointer self, String member, Object[] arguments,
                        @CachedLibrary(limit = "3") InteropLibrary interop) throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException, ArityException {
            return interop.invokeMember(self.vaList.getObject(), member, arguments);
        }
    }

    @ExportMessage
    static class ReadI8 {

        @Specialization(guards = "self.isPointer()")
        static byte readNative(LLVMMaybeVaPointer self, long offset,
                        @CachedLibrary("self") LLVMManagedReadLibrary location) {
            self.nativeObjectAccess();
            return LLVMLanguage.get(location).getLLVMMemory().getI8(location, self.getAddress() + offset);
        }

        @Specialization(guards = "!self.isPointer()")
        @GenerateAOT.Exclude
        static byte readManaged(LLVMMaybeVaPointer self, @SuppressWarnings("unused") long offset) {
            assert false;
            throw new LLVMMemoryException(self.allocaNode, "Unsupported read from VA list pointer.");
        }
    }

    @ExportMessage
    static class ReadI16 {

        @Specialization(guards = "self.isPointer()")
        static short readNative(LLVMMaybeVaPointer self, long offset,
                        @CachedLibrary("self") LLVMManagedReadLibrary location) {
            self.nativeObjectAccess();
            return LLVMLanguage.get(location).getLLVMMemory().getI16(location, self.getAddress() + offset);
        }

        @Specialization(guards = "!self.isPointer()")
        @GenerateAOT.Exclude
        static short readManaged(LLVMMaybeVaPointer self, @SuppressWarnings("unused") long offset) {
            assert false;
            throw new LLVMMemoryException(self.allocaNode, "Unsupported read from VA list pointer.");
        }
    }

    @ExportMessage
    static class ReadI32 {

        @Specialization(guards = "self.isPointer()")
        static int readNative(LLVMMaybeVaPointer self, long offset,
                        @CachedLibrary("self") LLVMManagedReadLibrary location) {
            self.nativeObjectAccess();
            return LLVMLanguage.get(location).getLLVMMemory().getI32(location, self.getAddress() + offset);
        }

        @Specialization(guards = "!self.isPointer()")
        @GenerateAOT.Exclude
        static int readManaged(LLVMMaybeVaPointer self, @SuppressWarnings("unused") long offset) {
            assert false;
            throw new LLVMMemoryException(self.allocaNode, "Unsupported read from VA list pointer.");
        }
    }

    @ExportMessage
    static class ReadFloat {

        @Specialization(guards = "self.isPointer()")
        static float readNative(LLVMMaybeVaPointer self, long offset,
                        @CachedLibrary("self") LLVMManagedReadLibrary location) {
            self.nativeObjectAccess();
            return LLVMLanguage.get(location).getLLVMMemory().getFloat(location, self.getAddress() + offset);
        }

        @Specialization(guards = "!self.isPointer()")
        @GenerateAOT.Exclude
        static float readManaged(LLVMMaybeVaPointer self, @SuppressWarnings("unused") long offset) {
            assert false;
            throw new LLVMMemoryException(self.allocaNode, "Unsupported read from VA list pointer.");
        }
    }

    @ExportMessage
    static class ReadDouble {

        @Specialization(guards = "self.isPointer()")
        static double readNative(LLVMMaybeVaPointer self, long offset,
                        @CachedLibrary("self") LLVMManagedReadLibrary location) {
            self.nativeObjectAccess();
            return LLVMLanguage.get(location).getLLVMMemory().getDouble(location, self.getAddress() + offset);
        }

        @Specialization(guards = "!self.isPointer()")
        @GenerateAOT.Exclude
        static double readManaged(LLVMMaybeVaPointer self, @SuppressWarnings("unused") long offset) {
            assert false;
            throw new LLVMMemoryException(self.allocaNode, "Unsupported read from VA list pointer.");
        }
    }

    @ExportMessage
    static class ReadGenericI64 {

        @Specialization(guards = "self.isPointer()")
        static long readNative(LLVMMaybeVaPointer self, long offset,
                        @CachedLibrary("self") LLVMManagedReadLibrary location) {
            self.nativeObjectAccess();
            return LLVMLanguage.get(location).getLLVMMemory().getI64(location, self.getAddress() + offset);
        }

        @Specialization(guards = {"!self.isPointer()", "offset == 0"})
        static Object readI64Managed(LLVMMaybeVaPointer self, long offset) {
            assert offset == 0;
            return self.vaList;
        }

        @Specialization(guards = {"!self.isPointer()", "offset != 0"})
        @GenerateAOT.Exclude
        static Object readFallback(LLVMMaybeVaPointer self, @SuppressWarnings("unused") long offset) {
            assert offset == 0;
            throw new LLVMMemoryException(self.allocaNode, "Unsupported read from VA list pointer.");
        }
    }

    @ExportMessage
    static class ReadPointer {

        @Specialization(guards = "self.isPointer()")
        static LLVMPointer readNative(LLVMMaybeVaPointer self, long offset,
                        @CachedLibrary("self") LLVMManagedReadLibrary location) {
            self.nativeObjectAccess();
            return LLVMLanguage.get(location).getLLVMMemory().getPointer(location, self.getAddress() + offset);
        }

        @Specialization(guards = {"!self.isPointer()", "offset == 0"})
        static LLVMPointer readManaged(LLVMMaybeVaPointer self, long offset) {
            assert offset == 0;
            return self.vaList;
        }

        @Specialization(guards = {"!self.isPointer()", "offset != 0"})
        @GenerateAOT.Exclude
        static LLVMPointer readFallback(LLVMMaybeVaPointer self, long offset) {
            assert offset == 0;
            throw new LLVMMemoryException(self.allocaNode, "Unsupported read from VA list pointer.");
        }
    }

    @ExportMessage
    static class WriteI8 {

        @Specialization(guards = "self.isPointer()")
        static void writeNative(LLVMMaybeVaPointer self, long offset, byte value,
                        @CachedLibrary("self") LLVMManagedWriteLibrary location) {
            self.nativeObjectAccess();
            LLVMLanguage.get(location).getLLVMMemory().putI8(location, self.getAddress() + offset, value);
        }

        @Specialization(guards = "!self.isPointer()")
        @GenerateAOT.Exclude
        static void writeManaged(LLVMMaybeVaPointer self, @SuppressWarnings("unused") long offset, @SuppressWarnings("unused") byte value) {
            assert offset == 0;
            throw new LLVMMemoryException(self.allocaNode, "Unsupported write from VA list pointer.");
        }
    }

    @ExportMessage
    static class WriteI16 {

        @Specialization(guards = "self.isPointer()")
        static void writeNative(LLVMMaybeVaPointer self, long offset, short value,
                        @CachedLibrary("self") LLVMManagedWriteLibrary location) {
            self.nativeObjectAccess();
            LLVMLanguage.get(location).getLLVMMemory().putI16(location, self.getAddress() + offset, value);
        }

        @Specialization(guards = "!self.isPointer()")
        @GenerateAOT.Exclude
        static void writeManaged(LLVMMaybeVaPointer self, @SuppressWarnings("unused") long offset, @SuppressWarnings("unused") short value) {
            assert offset == 0;
            throw new LLVMMemoryException(self.allocaNode, "Unsupported write from VA list pointer.");
        }
    }

    @ExportMessage
    static class WriteI32 {

        @Specialization(guards = "self.isPointer()")
        static void writeNative(LLVMMaybeVaPointer self, long offset, int value,
                        @CachedLibrary("self") LLVMManagedWriteLibrary location) {
            self.nativeObjectAccess();
            LLVMLanguage.get(location).getLLVMMemory().putI32(location, self.getAddress() + offset, value);
        }

        @Specialization(guards = "!self.isPointer()")
        @GenerateAOT.Exclude
        static void writeManaged(LLVMMaybeVaPointer self, @SuppressWarnings("unused") long offset, @SuppressWarnings("unused") int value) {
            assert offset == 0;
            throw new LLVMMemoryException(self.allocaNode, "Unsupported write from VA list pointer.");
        }
    }

    @ExportMessage
    static class WriteFloat {

        @Specialization(guards = "self.isPointer()")
        static void writeNative(LLVMMaybeVaPointer self, long offset, float value,
                        @CachedLibrary("self") LLVMManagedWriteLibrary location) {
            self.nativeObjectAccess();
            LLVMLanguage.get(location).getLLVMMemory().putFloat(location, self.getAddress() + offset, value);
        }

        @Specialization(guards = "!self.isPointer()")
        @GenerateAOT.Exclude
        static void writeManaged(LLVMMaybeVaPointer self, @SuppressWarnings("unused") long offset, @SuppressWarnings("unused") float value) {
            assert offset == 0;
            throw new LLVMMemoryException(self.allocaNode, "Unsupported write from VA list pointer.");
        }
    }

    @ExportMessage
    static class WriteDouble {

        @Specialization(guards = "self.isPointer()")
        static void writeNative(LLVMMaybeVaPointer self, long offset, double value,
                        @CachedLibrary("self") LLVMManagedWriteLibrary location) {
            self.nativeObjectAccess();
            LLVMLanguage.get(location).getLLVMMemory().putDouble(location, self.getAddress() + offset, value);
        }

        @Specialization(guards = "!self.isPointer()")
        @GenerateAOT.Exclude
        static void writeManaged(LLVMMaybeVaPointer self, @SuppressWarnings("unused") long offset, @SuppressWarnings("unused") double value) {
            assert offset == 0;
            throw new LLVMMemoryException(self.allocaNode, "Unsupported write from VA list pointer.");
        }
    }

    @ExportMessage
    static class WriteI64 {

        @Specialization(guards = "self.isPointer()")
        static void writeNative(LLVMMaybeVaPointer self, long offset, long value,
                        @CachedLibrary("self") LLVMManagedWriteLibrary location) {
            self.nativeObjectAccess();
            LLVMLanguage.get(location).getLLVMMemory().putI64(location, self.getAddress() + offset, value);
        }

        @Specialization(guards = {"!self.isPointer()"})
        @GenerateAOT.Exclude
        static void writeFallback(LLVMMaybeVaPointer self, @SuppressWarnings("unused") long offset, @SuppressWarnings("unused") long value) {
            throw new LLVMMemoryException(self.allocaNode, "Unsupported write from VA list pointer.");
        }
    }

    @ExportMessage
    static class WriteGenericI64 {
        static boolean isManagedPtr(Object obj) {
            return LLVMManagedPointer.isInstance(obj);
        }

        @Specialization(guards = {"isManagedPtr(value)", "offset == 0"})
        static void writeVAList(LLVMMaybeVaPointer self, long offset, LLVMManagedPointer value) {
            assert offset == 0;
            self.wasVAListPointer = true;
            self.vaList = value;
        }

        @Specialization(limit = "3", guards = "self.isPointer()")
        static void writeNative(LLVMMaybeVaPointer self, long offset, Object value,
                        @CachedLibrary("value") LLVMNativeLibrary toNative) {
            /*
             * No `self.nativeObjectAccess()` should be done here, a native va_list ptr can be
             * written here
             */
            long ptr = toNative.toNativePointer(value).asNative();
            LLVMLanguage.get(toNative).getLLVMMemory().putI64(toNative, self.getAddress() + offset, ptr);
        }

        @Specialization(guards = {"!self.isPointer()", "offset != 0"})
        @GenerateAOT.Exclude
        static void writeFallback(LLVMMaybeVaPointer self, @SuppressWarnings("unused") long offset, @SuppressWarnings("unused") Object value) {
            throw new LLVMMemoryException(self.allocaNode, "Unsupported write from VA list pointer.");
        }
    }

    @ExportMessage
    public boolean isForeign() {
        assert !isPointer();
        return true;
    }

    @ExportMessage
    public Object asForeign() {
        assert !isPointer();
        return this;
    }

    @Override
    @TruffleBoundary
    public String toString() {
        InteropLibrary interop = InteropLibrary.getUncached();
        try {
            return String.format("LLVMMaybeVAPointer (address = 0x%x, contents = %s)", interop.asPointer(address), vaList);
        } catch (UnsupportedMessageException e) {
            return "no string";
        }
    }
}
