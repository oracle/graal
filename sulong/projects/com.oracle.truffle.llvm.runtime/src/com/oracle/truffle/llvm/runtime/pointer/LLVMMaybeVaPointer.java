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
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.except.LLVMMemoryException;
import com.oracle.truffle.llvm.runtime.interop.LLVMDataEscapeNode;
import com.oracle.truffle.llvm.runtime.interop.LLVMInternalTruffleObject;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMAsForeignLibrary;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedReadLibrary;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedWriteLibrary;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMNativeLibrary;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.aarch64.darwin.LLVMDarwinAarch64VaListStorage;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVAListNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVaListLibrary;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVaListStorage;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVaListStorage.StackAllocationNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86_win.LLVMX86_64_WinVaListStorage;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMPointerLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMPointerStoreNode.LLVMPointerOffsetStoreNode;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.Type;

import static com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVaListStorage.*;

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
@ImportStatic(LLVMVaListStorage.class)
public final class LLVMMaybeVaPointer extends LLVMInternalTruffleObject {
    private final Assumption allocVAPointerAssumption;
    private final LLVMVAListNode allocaNode;
    private boolean wasVAListPointer = false;
    private LLVMPointer address;
    protected LLVMManagedPointer vaList;

    public LLVMMaybeVaPointer(LLVMVAListNode allocaNode, LLVMPointer address) {
        this.allocaNode = allocaNode;
        this.allocVAPointerAssumption = allocaNode.getAssumption();
        this.address = address;
    }

    public LLVMManagedPointer getVaList() {
        return vaList;
    }

    @ExportMessage
    public boolean isPointer() {
        return vaList == null;
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
        // this address should only be accessed if we are not dealing with a va_list
        assert isPointer();
        return LLVMNativePointer.cast(address).asNative();
    }

    protected static ArgumentListExpander createArgListExpander(boolean unpack32) {
        return ArgumentListExpander.create(unpack32);
    }

    @ExportMessage
    void initialize(Object[] realArguments, int numberOfExplicitArguments, Frame frame,
                        @Cached(parameters = "KEEP_32BIT_PRIMITIVES_IN_STRUCTS") ArgumentListExpander argsExpander,
                        @Cached StackAllocationNode stackAllocationNode) {
        // LLVMX86_64_WinVaListStorage vaListInstance = new LLVMX86_64_WinVaListStorage();
        // vaListInstance.initialize(realArgs, numOfExpArgs, frame, stackAllocationNode);

        assert numberOfExplicitArguments <= realArguments.length;
        Object[][][] expansionsOutArg = new Object[1][][];
        Object[] expandedArguments = argsExpander.expand(realArguments, expansionsOutArg);
        LLVMDarwinAarch64VaListStorage vaListInstance = new LLVMDarwinAarch64VaListStorage();
        LLVMDarwinAarch64VaListStorage.Initialize.initializeManaged(vaListInstance, expandedArguments, numberOfExplicitArguments, frame, stackAllocationNode);

        vaList = LLVMManagedPointer.create(vaListInstance);
        wasVAListPointer = true;
    }

    /**
     * Whenever an incompatible native access is performed on this object, this method is called to
     * prevent a VA list pointer from being allocated by the same alloca node next time. Limited
     * native access is permitted after calling the cleanup method.
     */
    protected void nativeObjectAccess() {
        if (!wasVAListPointer && allocVAPointerAssumption.isValid()) {
            allocVAPointerAssumption.invalidate();
        }
    }

    @ExportMessage
    static class Shift {
        static protected Object createStorage(LLVMMaybeVaPointer self,
                LLVMPointerLoadNode.LLVMPointerOffsetLoadNode loadPtr,
                VAListPointerWrapperFactoryDelegate wrapperFactory) {
            if (!self.isPointer()) {
                return self.vaList.getObject();
            }
            LLVMPointer vaListPtr = loadPtr.executeWithTarget(self.address, 0);
            Object vaListInstance = wrapperFactory.execute(vaListPtr);
            self.vaList = LLVMManagedPointer.create(vaListInstance);
            self.wasVAListPointer = true;
            return vaListInstance;
        }

        @Specialization(limit = "3")
        @GenerateAOT.Exclude
        static protected Object shift(LLVMMaybeVaPointer self, Type type, Frame frame,
                                      @Cached LLVMPointerLoadNode.LLVMPointerOffsetLoadNode loadPtr,
                                      @Cached VAListPointerWrapperFactoryDelegate wrapperFactory,
                                      @Bind(value = "createStorage(self, loadPtr, wrapperFactory)") Object vaListStorage,
                                      @CachedLibrary(value = "vaListStorage") LLVMManagedReadLibrary readLibrary) {
            assert self.wasVAListPointer;

            Object ret = null;
            long offset = self.vaList.getOffset();
            if (PrimitiveType.DOUBLE.equals(type)) {
                ret = readLibrary.readDouble(vaListStorage, offset);
            } else if (PrimitiveType.I32.equals(type)) {
                ret = readLibrary.readI32(vaListStorage, offset);
            } else if (PrimitiveType.I64.equals(type)) {
                try {
                    ret = readLibrary.readI64(vaListStorage, offset);
                } catch (UnexpectedResultException e) {
                    e.printStackTrace();
                }
            } else if (type instanceof PointerType) {
                ret = readLibrary.readPointer(vaListStorage, offset);
            } else {
                CompilerDirectives.shouldNotReachHere("MaybeVaPointer.shift: not implemented: " + type);
            }
            // TODO: should be ABI specific? Only needed for darwin-aarch64
            DataLayout dataLayout = LLVMLanguage.get(null).getDefaultDataLayout();
            self.vaList = self.vaList.increment(8 /*type.getSize(dataLayout)*/);
            return ret;
        }
    }

    @ExportMessage
    static class Copy {
        @Specialization(guards = "!self.isPointer()")
        static void copyGeneric(LLVMMaybeVaPointer self, Object other, Frame frame,
                @CachedLibrary(limit = "3") LLVMVaListLibrary vaListLibrary) {
            vaListLibrary.copy(self.vaList.getObject(), other, frame);
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
    public long getArraySize() {
        // TODO: add specialization for native case?
        return ((LLVMVaListStorage) vaList.getObject()).getArraySize();
    }

    @ExportMessage
    public boolean isArrayElementReadable(long index) {
        // TODO: add specialization for native case?
        return ((LLVMVaListStorage) vaList.getObject()).isArrayElementReadable(index);
    }

    @ExportMessage
    public Object readArrayElement(long index, @Cached LLVMDataEscapeNode.LLVMPointerDataEscapeNode pointerEscapeNode) {
        // TODO: add specialization for native case?
        // TODO: use CachedLibrary?
        return ((LLVMVaListStorage) vaList.getObject()).readArrayElement(index, pointerEscapeNode);
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
    public Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        // TODO: add specialization for native case?
        // TODO: use CachedLibrary?
        return ((LLVMVaListStorage) vaList.getObject()).getMembers(includeInternal);
    }

    @ExportMessage
    public boolean isMemberInvocable(String member) {
        return ((LLVMVaListStorage) vaList.getObject()).isMemberInvocable(member);
    }

    @ExportMessage
    public Object invokeMember(String member, Object[] arguments,
               @Cached LLVMDataEscapeNode.LLVMPointerDataEscapeNode pointerEscapeNode) throws ArityException, UnknownIdentifierException, UnsupportedTypeException {
        return ((LLVMVaListStorage) vaList.getObject()).invokeMember(member, arguments, pointerEscapeNode);
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

        static boolean isVaListStorage(LLVMManagedPointer p) {
            assert p.getOffset() == 0;
            Object o = p.getObject();
            return o instanceof LLVMVaListStorage || o instanceof LLVMX86_64_WinVaListStorage;
        }

        static boolean helperGuard(LLVMManagedPointer value) {
            return LLVMManagedPointer.isInstance(value);
        }

        // TODO: win_x86_64 class is not a subclass of LLVMVaListStorage
        @Specialization(limit = "1", guards = {"self.isPointer()", "offset == 0"})
        @GenerateAOT.Exclude // TODO: why is this needed? DSL bug?
        static void writeVaListStorageManagedObject(LLVMMaybeVaPointer self, long offset, LLVMVaListStorage storage,
                    @CachedLibrary("storage") LLVMVaListLibrary vaListLibrary) {
            assert offset == 0;
            self.vaList = LLVMManagedPointer.create(storage);
            self.wasVAListPointer = true;
        }

        @Specialization(limit = "1", guards = {"self.isPointer()", "offset == 0", "helperGuard(value)"})
        @GenerateAOT.Exclude // TODO: why is this needed? DSL bug?
        static void writeVaListStorageManaged(LLVMMaybeVaPointer self, long offset, LLVMManagedPointer value,
                         @CachedLibrary("value") LLVMNativeLibrary toNative,
                         @Cached LLVMPointerOffsetStoreNode pointerOffsetStoreNode,
                         @CachedLibrary("value.getObject()") LLVMVaListLibrary vaListLibrary) {
            if (helperGuard(value) && isVaListStorage(value)) {
                assert offset == 0;
                self.vaList = LLVMManagedPointer.create(value.getObject());
                self.wasVAListPointer = true;
            } else {
                // TODO: Make this work with proper guards.
                self.nativeObjectAccess();
                long ptr = toNative.toNativePointer(value).asNative();
                pointerOffsetStoreNode.executeWithTarget(self.address, 0, ptr);
            }
        }

        @Specialization(limit = "3", guards = {"self.isPointer()"})
        static void writeNative(LLVMMaybeVaPointer self, long offset, Object value,
                                @CachedLibrary("value") LLVMNativeLibrary toNative) {
            // Note: No `self.nativeObjectAccess()` needed here, this kind of write can happen on darwin-aarch64.
            long ptr = toNative.toNativePointer(value).asNative();
            LLVMLanguage.get(toNative).getLLVMMemory().putI64(toNative, self.getAddress() + offset, ptr);
        }

        @Specialization(limit = "3", guards = {"!self.isPointer()", "offset == 0"})
        @GenerateAOT.Exclude // TODO: why is this needed? DSL bug?
        static void writeVAList(LLVMMaybeVaPointer self, long offset, LLVMManagedPointer value,
                         @Cached LLVMPointerOffsetStoreNode pointerOffsetStoreNode,
                         @CachedLibrary("value.getObject()") LLVMVaListLibrary vaListLibrary) {
            /* writeback of va_list pointer */
            assert offset == 0;
            assert value.getObject() == self.vaList.getObject();
            assert value.getOffset() != 0;

            self.vaList = value;
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
            return String.format("LLVMMaybeVAPointer (address = 0x%x, contents = %s)", interop.asPointer(address) , vaList);
        } catch (UnsupportedMessageException e) {
            e.printStackTrace();
            return "no string";
        }
    }
}
