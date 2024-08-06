/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
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
@ExportLibrary(value = LLVMNativeLibrary.class, useForAOT = false)
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
    public long asPointer(@Cached @Exclusive BranchProfile exception) throws UnsupportedMessageException {
        if (isPointer()) {
            return nativePointer;
        }
        exception.enter();
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    public boolean isPointer() {
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
        try {
            return asNativePointer(exceptionProfile);
        } catch (UnsupportedMessageException e) {
            exceptionProfile.enter();
            throw new LLVMPolyglotException(self, "Cannot convert %s to native pointer.", this);
        }
    }

    public LLVMNativePointer asNativePointer(BranchProfile exceptionProfile) throws UnsupportedMessageException {
        if (!isPointer()) {
            toNative();
        }
        return LLVMNativePointer.create(asPointer(exceptionProfile));
    }

    @ExportMessage
    boolean isExecutable() {
        return true;
    }

    @ExportMessage
    @ImportStatic(LLVMLanguage.class)
    static class Execute {

        @Specialization(limit = "5", guards = {"self == cachedSelf", "isSingleContext($node)"})
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

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    static final String CREATE_NATIVE_CLOSURE = "createNativeClosure";

    static ContextExtension.Key<NativeContextExtension> getCtxExtKey() {
        return LLVMLanguage.get(null).lookupContextExtension(NativeContextExtension.class);
    }

    @ExportMessage(name = "isMemberReadable")
    @ExportMessage(name = "isMemberInvocable")
    static class IsMemberReadable {

        @Specialization(guards = {"ident == CREATE_NATIVE_CLOSURE", "ctxExtKey != null"})
        static boolean doCreate(@SuppressWarnings("unused") LLVMFunctionDescriptor function, @SuppressWarnings("unused") String ident,
                        @Bind("$node") Node node,
                        @Cached(value = "getCtxExtKey()", allowUncached = true) ContextExtension.Key<NativeContextExtension> ctxExtKey) {
            return ctxExtKey.get(LLVMContext.get(node)) != null;
        }

        @Specialization(guards = {"CREATE_NATIVE_CLOSURE.equals(ident)", "ctxExtKey != null"}, replaces = "doCreate")
        static boolean doEqualsCheck(@SuppressWarnings("unused") LLVMFunctionDescriptor function, @SuppressWarnings("unused") String ident,
                        @Bind("$node") Node node,
                        @Cached(value = "getCtxExtKey()", allowUncached = true) ContextExtension.Key<NativeContextExtension> ctxExtKey) {
            return doCreate(function, ident, node, ctxExtKey);
        }

        @Fallback
        static boolean doOther(@SuppressWarnings("unused") LLVMFunctionDescriptor function, @SuppressWarnings("unused") String ident) {
            return false;
        }
    }

    @GenerateInline
    @GenerateUncached
    abstract static class AsStringHelperNode extends Node {

        abstract String execute(Node node, Object arg) throws UnsupportedTypeException;

        @Specialization(limit = "3", guards = "interop.isString(arg)")
        static String doString(Object arg,
                        @CachedLibrary("arg") InteropLibrary interop) {
            try {
                return interop.asString(arg);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @Fallback
        static String doOther(Object arg) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{arg}, "string");
        }
    }

    @GenerateInline
    @GenerateUncached
    abstract static class GetWrapperFactoryNode extends Node {

        abstract CallTarget execute(Node node, NativeContextExtension ctxExt, LLVMFunctionCode function, Object[] args) throws ArityException, UnsupportedTypeException;

        @TruffleBoundary
        static CallTarget getDefault(NativeContextExtension ctxExt, LLVMFunctionCode function) {
            return function.getNativeWrapperFactory(ctxExt);
        }

        @TruffleBoundary
        static CallTarget getAltBackend(NativeContextExtension ctxExt, LLVMFunctionCode function, String backend) {
            return function.getAltBackendNativeWrapperFactory(ctxExt, backend);
        }

        @Specialization(guards = "args.length == 0")
        static CallTarget doNoArgs(NativeContextExtension ctxExt, LLVMFunctionCode function, Object[] args) {
            assert args.length == 0;
            return getDefault(ctxExt, function);
        }

        @Specialization(guards = {"args.length == 1"})
        static CallTarget doSingleArg(Node node, NativeContextExtension ctxExt, LLVMFunctionCode function, Object[] args,
                        @Cached AsStringHelperNode asString) throws UnsupportedTypeException {
            return getAltBackend(ctxExt, function, asString.execute(node, args[0]));
        }

        @Fallback
        static CallTarget doWrongArity(@SuppressWarnings("unused") NativeContextExtension ctxExt, @SuppressWarnings("unused") LLVMFunctionCode function, Object[] args) throws ArityException {
            throw ArityException.create(1, 1, args.length);
        }
    }

    @GenerateInline
    @GenerateUncached
    abstract static class InlineCacheHelperNode extends Node {

        abstract Object execute(Node node, CallTarget target);

        @Specialization(limit = "5", guards = "call.getCallTarget() == target")
        static Object doCached(CallTarget target,
                        @Cached("create(target)") DirectCallNode call) {
            assert call.getCallTarget() == target;
            return call.call();
        }

        @Specialization(replaces = "doCached")
        static Object doGeneric(CallTarget target,
                        @Cached IndirectCallNode call) {
            return call.call(target);
        }
    }

    @GenerateInline
    @GenerateUncached
    @ImportStatic(LLVMFunctionDescriptor.class)
    abstract static class CreateNativeClosureNode extends Node {

        abstract Object execute(Node node, LLVMFunctionCode function, Object[] args) throws ArityException, UnsupportedMessageException, UnsupportedTypeException;

        @Specialization(guards = "ctxExtKey != null")
        static Object doCreate(Node node, LLVMFunctionCode function, Object[] args,
                        @Cached(value = "getCtxExtKey()", allowUncached = true) ContextExtension.Key<NativeContextExtension> ctxExtKey,
                        @Cached GetWrapperFactoryNode getWrapperFactory,
                        @Cached InlineCacheHelperNode inlineCache,
                        @Cached InlinedBranchProfile exception) throws ArityException, UnsupportedMessageException, UnsupportedTypeException {
            NativeContextExtension ctxExt = ctxExtKey.get(LLVMContext.get(node));
            if (ctxExt == null) {
                exception.enter(node);
                throw UnsupportedMessageException.create();
            }
            CallTarget wrapperFactory = getWrapperFactory.execute(node, ctxExt, function, args);
            return inlineCache.execute(node, wrapperFactory);
        }

        @Fallback
        static Object doFail(@SuppressWarnings("unused") LLVMFunctionCode function, @SuppressWarnings("unused") Object[] args) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    static class InvokeMember {

        @Specialization(guards = "ident == CREATE_NATIVE_CLOSURE")
        static Object doCreate(LLVMFunctionDescriptor function, String ident, Object[] args,
                        @Bind("$node") Node node,
                        @Cached CreateNativeClosureNode createNativeClosure) throws ArityException, UnknownIdentifierException, UnsupportedTypeException {
            assert CREATE_NATIVE_CLOSURE.equals(ident);
            try {
                return createNativeClosure.execute(node, function.getFunctionCode(), args);
            } catch (UnsupportedMessageException e) {
                throw UnknownIdentifierException.create(ident);
            }
        }

        @Specialization(guards = "CREATE_NATIVE_CLOSURE.equals(ident)", replaces = "doCreate")
        static Object doEqualsCheck(LLVMFunctionDescriptor function, String ident, Object[] args,
                        @Bind("$node") Node node,
                        @Cached CreateNativeClosureNode createNativeClosure) throws ArityException, UnknownIdentifierException, UnsupportedTypeException {
            return doCreate(function, ident, args, node, createNativeClosure);
        }

        @Fallback
        static Object doOther(@SuppressWarnings("unused") LLVMFunctionDescriptor function, String ident, @SuppressWarnings("unused") Object[] arg) throws UnknownIdentifierException {
            throw UnknownIdentifierException.create(ident);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class CreateNativeClosureExecutable implements TruffleObject {

        private final LLVMFunctionCode function;

        private CreateNativeClosureExecutable(LLVMFunctionCode function) {
            this.function = function;
        }

        @ExportMessage
        boolean isExecutable() {
            assert function != null;
            return true;
        }

        @ExportMessage
        Object execute(Object[] args,
                        @Bind("$node") Node node,
                        @Cached CreateNativeClosureNode createNativeClosure) throws ArityException, UnsupportedMessageException, UnsupportedTypeException {
            return createNativeClosure.execute(node, function, args);
        }
    }

    @ExportMessage
    static class ReadMember {

        @Specialization(guards = {"ident == CREATE_NATIVE_CLOSURE", "ctxExtKey != null"})
        static Object doCreate(LLVMFunctionDescriptor function, String ident,
                        @Bind("$node") Node node,
                        @Cached(value = "getCtxExtKey()", allowUncached = true) ContextExtension.Key<NativeContextExtension> ctxExtKey,
                        @Cached InlinedBranchProfile exception) throws UnknownIdentifierException {
            assert CREATE_NATIVE_CLOSURE.equals(ident);
            if (ctxExtKey.get(LLVMContext.get(node)) == null) {
                exception.enter(node);
                throw UnknownIdentifierException.create(ident);
            }
            return new CreateNativeClosureExecutable(function.getFunctionCode());
        }

        @Specialization(guards = {"CREATE_NATIVE_CLOSURE.equals(ident)", "ctxExtKey != null"}, replaces = "doCreate")
        static Object doEqualsCheck(LLVMFunctionDescriptor function, String ident,
                        @Bind("$node") Node node,
                        @Cached(value = "getCtxExtKey()", allowUncached = true) ContextExtension.Key<NativeContextExtension> ctxExtKey,
                        @Cached InlinedBranchProfile exception) throws UnknownIdentifierException {
            return doCreate(function, ident, node, ctxExtKey, exception);
        }

        @Fallback
        static Object doOther(@SuppressWarnings("unused") LLVMFunctionDescriptor function, String ident) throws UnknownIdentifierException {
            throw UnknownIdentifierException.create(ident);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class MembersList implements TruffleObject {

        private final boolean hasCreateNativeClosure;

        MembersList(boolean hasCreateNativeClosure) {
            this.hasCreateNativeClosure = hasCreateNativeClosure;
        }

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return hasCreateNativeClosure ? 1 : 0;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return hasCreateNativeClosure && index == 0;
        }

        @ExportMessage
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            if (hasCreateNativeClosure && index == 0) {
                return CREATE_NATIVE_CLOSURE;
            } else {
                throw InvalidArrayIndexException.create(index);
            }
        }
    }

    @ExportMessage
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal,
                    @Bind("$node") Node node,
                    @Cached(value = "getCtxExtKey()", allowUncached = true) ContextExtension.Key<NativeContextExtension> ctxExtKey) {
        boolean hasCreateNativeClosure = ctxExtKey != null && ctxExtKey.get(LLVMContext.get(node)) != null;
        return new MembersList(hasCreateNativeClosure);
    }
}
