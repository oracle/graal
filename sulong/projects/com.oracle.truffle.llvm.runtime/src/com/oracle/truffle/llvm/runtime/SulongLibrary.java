/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.utilities.AssumedValue;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

/**
 * Object that is returned when a bitcode library is parsed.
 */
@ExportLibrary(InteropLibrary.class)
@SuppressWarnings("static-method")
public final class SulongLibrary implements TruffleObject {

    private final String name;
    private final LLVMScope scope;
    private final CallTarget main;
    private final LLVMContext context;

    public SulongLibrary(String name, LLVMScope scope, CallTarget main, LLVMContext context) {
        this.name = name;
        this.scope = scope;
        this.main = main;
        this.context = context;
    }

    /**
     * Get a function descriptor for a function called {@code symbolName}.
     *
     * @param symbolName Function name.
     * @return Function descriptor for the function called {@code symbolName} and {@code null} if
     *         the function name cannot be found.
     */
    private LLVMFunctionDescriptor lookupFunctionDescriptor(String symbolName) {
        LLVMFunction function = scope.getFunction(symbolName);
        if (function == null) {
            return null;
        }
        int index = function.getSymbolIndex(false);
        AssumedValue<LLVMPointer>[] symbols = context.findSymbolTable(function.getBitcodeID(false));
        LLVMPointer pointer = symbols[index].get();
        LLVMFunctionDescriptor functionDescriptor = (LLVMFunctionDescriptor) LLVMManagedPointer.cast(pointer).getObject();
        return functionDescriptor;
    }

    public String getName() {
        return name;
    }

    @GenerateUncached
    abstract static class LookupNode extends LLVMNode {

        abstract LLVMFunctionDescriptor execute(SulongLibrary library, String name);

        @Specialization(guards = {"library == cachedLibrary", "name.equals(cachedName)"})
        @SuppressWarnings("unused")
        LLVMFunctionDescriptor doCached(SulongLibrary library, String name,
                        @Cached("library") SulongLibrary cachedLibrary,
                        @Cached("name") String cachedName,
                        @Cached("lookupFunctionDescriptor(cachedLibrary, cachedName)") LLVMFunctionDescriptor cachedDescriptor) {
            return cachedDescriptor;
        }

        @Specialization(replaces = "doCached")
        @TruffleBoundary
        static LLVMFunctionDescriptor doGeneric(SulongLibrary library, String name) {
            return lookupFunctionDescriptor(library, name);
        }

        protected static LLVMFunctionDescriptor lookupFunctionDescriptor(SulongLibrary library, String name) {
            return library.lookupFunctionDescriptor(name);
        }
    }

    @ExportMessage
    Object readMember(String member,
                    @Shared("lookup") @Cached LookupNode lookup) throws UnknownIdentifierException {
        Object ret = lookup.execute(this, member);
        if (ret == null) {
            CompilerDirectives.transferToInterpreter();
            throw UnknownIdentifierException.create(member);
        }
        return ret;
    }

    @ExportMessage
    Object invokeMember(String member, Object[] arguments,
                    @Shared("lookup") @Cached LookupNode lookup,
                    @CachedLibrary(limit = "1") InteropLibrary interop) throws ArityException, UnknownIdentifierException, UnsupportedTypeException, UnsupportedMessageException {
        LLVMFunctionDescriptor fn = lookup.execute(this, member);
        if (fn == null) {
            CompilerDirectives.transferToInterpreter();
            throw UnknownIdentifierException.create(member);
        }

        return interop.execute(fn, arguments);
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return scope.getKeys();
    }

    @ExportMessage(name = "isMemberReadable")
    @ExportMessage(name = "isMemberInvocable")
    boolean memberExists(String member,
                    @Shared("lookup") @Cached LookupNode lookup) {
        return lookup.execute(this, member) != null;
    }

    @ExportMessage
    boolean isExecutable() {
        return main != null;
    }

    @ExportMessage
    abstract static class Execute {

        @Specialization(guards = "library == cachedLibrary")
        @SuppressWarnings("unused")
        static Object doCached(SulongLibrary library, Object[] args,
                        @Cached("library") SulongLibrary cachedLibrary,
                        @Cached("createMainCall(cachedLibrary)") DirectCallNode call) {
            return call.call(args);
        }

        static DirectCallNode createMainCall(SulongLibrary library) {
            return DirectCallNode.create(library.main);
        }

        @Specialization(replaces = "doCached")
        static Object doGeneric(SulongLibrary library, Object[] args,
                        @Cached("create()") IndirectCallNode call) {
            return call.call(library.main, args);
        }
    }

    @ExportMessage
    @SuppressWarnings({"unused", "static-method"})
    boolean hasLanguage() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings({"static-method"})
    Class<? extends TruffleLanguage<?>> getLanguage() {
        return LLVMLanguage.class;
    }

    @ExportMessage
    @TruffleBoundary
    String toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return "LLVMLibrary:" + getName();
    }
}
