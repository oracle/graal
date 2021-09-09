/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.runtime.IDGenerater.BitcodeID;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.interop.LLVMInternalTruffleObject;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMAsForeignLibrary;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMNativeLibrary;
import com.oracle.truffle.llvm.runtime.memory.LLVMHandleMemoryBase;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

/**
 * Our implementation assumes that there is a 1:1:1 relationship between callable functions (
 * {@link LLVMFunctionCode}), function symbols ({@link LLVMFunction}), and
 * {@link LLVMFunctionDescriptor}s.
 */
@ExportLibrary(InteropLibrary.class)
@ExportLibrary(value = LLVMNativeLibrary.class, useForAOT = true, useForAOTPriority = 1)
@ExportLibrary(value = LLVMAsForeignLibrary.class, useForAOT = true, useForAOTPriority = 2)
@SuppressWarnings("static-method")
public final class LLVMFunctionDescriptor extends LLVMInternalTruffleObject implements Comparable<LLVMFunctionDescriptor> {
    private static final long SULONG_FUNCTION_POINTER_TAG = 0xBADE_FACE_0000_0000L;

    static {
        assert LLVMHandleMemoryBase.isCommonHandleMemory(SULONG_FUNCTION_POINTER_TAG);
        assert !LLVMHandleMemoryBase.isDerefHandleMemory(SULONG_FUNCTION_POINTER_TAG);
    }

    private final LLVMFunction llvmFunction;
    private final LLVMFunctionCode functionCode;

    @CompilationFinal private Object nativeWrapper;
    @CompilationFinal private long nativePointer;

    private static long tagSulongFunctionPointer(int id) {
        return id | SULONG_FUNCTION_POINTER_TAG;
    }

    public LLVMFunction getLLVMFunction() {
        return llvmFunction;
    }

    public LLVMFunctionCode getFunctionCode() {
        return functionCode;
    }

    public long getNativePointer() {
        return nativePointer;
    }

    public LLVMFunctionDescriptor(LLVMFunction llvmFunction, LLVMFunctionCode functionCode) {
        CompilerAsserts.neverPartOfCompilation();
        this.llvmFunction = llvmFunction;
        this.functionCode = functionCode;
    }

    @Override
    public String toString() {
        return String.format("function@%d '%s'", llvmFunction.getSymbolIndexIllegalOk(), llvmFunction.getName());
    }

    @Override
    public int compareTo(LLVMFunctionDescriptor o) {
        int otherIndex = o.llvmFunction.getSymbolIndexIllegalOk();
        BitcodeID otherBitcodeID = o.llvmFunction.getBitcodeIDIllegalOk();
        int index = llvmFunction.getSymbolIndexIllegalOk();
        BitcodeID bitcodeID = llvmFunction.getBitcodeIDIllegalOk();

        if (bitcodeID == otherBitcodeID) {
            return Long.compare(index, otherIndex);
        }

        throw new IllegalStateException("Comparing functions from different bitcode files.");
    }

    @ExportMessage
    long asPointer(@Cached @Exclusive BranchProfile exception) throws UnsupportedMessageException {
        if (isPointer()) {
            return nativePointer;
        }
        exception.enter();
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    boolean isPointer() {
        return nativeWrapper != null;
    }

    /**
     * Gets a pointer to this function that can be stored in native memory.
     */
    @ExportMessage
    public LLVMFunctionDescriptor toNative() {
        if (nativeWrapper == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            nativeWrapper = functionCode.getFunction().createNativeWrapper(this);
            try {
                nativePointer = InteropLibrary.getFactory().getUncached().asPointer(nativeWrapper);
            } catch (UnsupportedMessageException ex) {
                nativePointer = tagSulongFunctionPointer(llvmFunction.getSymbolIndexIllegalOk());
            }
        }
        return this;
    }

    @ExportMessage
    public LLVMNativePointer toNativePointer(@CachedLibrary("this") LLVMNativeLibrary self,
                    @Cached @Exclusive BranchProfile exceptionProfile) {
        if (!isPointer()) {
            toNative();
        }
        try {
            return LLVMNativePointer.create(asPointer(exceptionProfile));
        } catch (UnsupportedMessageException e) {
            exceptionProfile.enter();
            throw new LLVMPolyglotException(self, "Cannot convert %s to native pointer.", this);
        }
    }

    @ExportMessage
    boolean isExecutable() {
        return true;
    }

    @ExportMessage
    static class Execute {

        @Specialization(limit = "5", guards = "self == cachedSelf", assumptions = "singleContextAssumption()")
        static Object doDescriptor(@SuppressWarnings("unused") LLVMFunctionDescriptor self, Object[] args,
                        @Cached("self") @SuppressWarnings("unused") LLVMFunctionDescriptor cachedSelf,
                        @Cached("createCall(cachedSelf)") DirectCallNode call) {
            return call.call(args);
        }

        @Specialization(replaces = "doDescriptor", limit = "5", guards = "self.getFunctionCode() == cachedFunctionCode")
        static Object doCached(@SuppressWarnings("unused") LLVMFunctionDescriptor self, Object[] args,
                        @Cached("self.getFunctionCode()") @SuppressWarnings("unused") LLVMFunctionCode cachedFunctionCode,
                        @Cached("createCall(self)") DirectCallNode call) {
            return call.call(args);
        }

        @Specialization(replaces = "doCached")
        static Object doPolymorphic(LLVMFunctionDescriptor self, Object[] args,
                        @Exclusive @Cached IndirectCallNode call) {
            return call.call(self.getFunctionCode().getForeignCallTarget(), args);
        }

        protected static DirectCallNode createCall(LLVMFunctionDescriptor self) {
            DirectCallNode callNode = DirectCallNode.create(self.getFunctionCode().getForeignCallTarget());
            callNode.forceInlining();
            return callNode;
        }

        protected static Assumption singleContextAssumption() {
            return LLVMLanguage.get(null).singleContextAssumption;
        }

    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportLibrary(InteropLibrary.class)
    static final class FunctionMembers implements TruffleObject {

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return 1;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return index == 0;
        }

        @ExportMessage
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            if (index == 0) {
                return "bind";
            } else {
                throw InvalidArrayIndexException.create(index);
            }
        }
    }

    @ExportMessage
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return new FunctionMembers();
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean isMemberInvocable(String member) {
        return "bind".equals(member);
    }

    @ExportMessage
    Object invokeMember(String member, @SuppressWarnings("unused") Object[] args) throws UnknownIdentifierException {
        if ("bind".equals(member)) {
            return this;
        } else {
            throw UnknownIdentifierException.create(member);
        }
    }

    @ExportMessage
    boolean isInstantiable() {
        return true;
    }

    @ExportMessage
    Object instantiate(Object[] arguments, @Exclusive @Cached IndirectCallNode call) {
        final Object[] newArgs = new Object[arguments.length + 1];
        for (int i = 0; i < arguments.length; i++) {
            newArgs[i + 1] = arguments[i];
        }
        return call.call(functionCode.getForeignConstructorCallTarget(), newArgs);
    }

    @ExportMessage
    public boolean hasExecutableName() {
        return llvmFunction.getSourceLocation() != null && llvmFunction.getSourceLocation().getName() != null;
    }

    @ExportMessage
    public Object getExecutableName() throws UnsupportedMessageException {
        if (hasExecutableName()) {
            return llvmFunction.getSourceLocation().getName();
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    public boolean hasSourceLocation() {
        return llvmFunction.getSourceLocation() != null;
    }

    @ExportMessage
    @CompilerDirectives.TruffleBoundary
    public SourceSection getSourceLocation() throws UnsupportedMessageException {
        if (hasSourceLocation()) {
            return llvmFunction.getSourceLocation().getSourceSection();
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    public static boolean isForeign(@SuppressWarnings("unused") LLVMFunctionDescriptor receiver) {
        return false;
    }

}
