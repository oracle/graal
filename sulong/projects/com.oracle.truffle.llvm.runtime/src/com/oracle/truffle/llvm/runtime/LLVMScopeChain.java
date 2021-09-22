/*
 * Copyright (c) 2021, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.runtime.IDGenerater.BitcodeID;
import com.oracle.truffle.llvm.runtime.except.LLVMIllegalSymbolIndexException;
import com.oracle.truffle.llvm.runtime.except.LLVMLinkerException;
import com.oracle.truffle.llvm.runtime.interop.LLVMDataEscapeNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@ExportLibrary(InteropLibrary.class)
@SuppressWarnings("static-method")
public class LLVMScopeChain implements TruffleObject {

    private LLVMScopeChain next;
    private LLVMScopeChain prev;
    private final BitcodeID id;
    private final LLVMScope scope;

    public LLVMScopeChain() {
        this.id = IDGenerater.INVALID_ID;
        this.scope = null;
    }

    public LLVMScopeChain(BitcodeID id, LLVMScope scope) {
        this.id = id;
        this.scope = scope;
    }

    public LLVMScopeChain getNext() {
        return next;
    }

    public void setNext(LLVMScopeChain next) {
        this.next = next;
    }

    public void setPrev(LLVMScopeChain prev) {
        this.prev = prev;
    }

    public LLVMScopeChain getPrev() {
        return prev;
    }

    public BitcodeID getId() {
        return id;
    }

    public LLVMScope getScope() {
        return scope;
    }

    @TruffleBoundary
    public LLVMSymbol get(String name) {
        assert scope != null;
        LLVMSymbol symbol = scope.get(name);
        if (symbol == null && next != null) {
            symbol = next.get(name);
        }
        return symbol;
    }

    @TruffleBoundary
    public boolean contains(String name) {
        assert scope != null;
        boolean contain = scope.contains(name);
        if (!contain && next != null) {
            contain = next.contains(name);
        }
        return contain;
    }

    @TruffleBoundary
    public LLVMFunction getFunction(String name) {
        assert scope != null;
        LLVMFunction function = scope.getFunction(name);
        if (function == null && next != null) {
            function = next.getFunction(name);
        }
        return function;
    }

    @TruffleBoundary
    public synchronized void concatNextChain(LLVMScopeChain scopeChain) {
        assert scopeChain.getPrev() == null && this.getNext() == null;
        this.setNext(scopeChain);
        scopeChain.setPrev(this);
    }

    @ExportMessage
    final boolean hasLanguage() {
        return true;
    }

    @ExportMessage
    final Class<? extends TruffleLanguage<?>> getLanguage() {
        return LLVMLanguage.class;
    }

    @ExportMessage
    final boolean isScope() {
        return scope != null && id != IDGenerater.INVALID_ID;
    }

    @ExportMessage
    public Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return "llvm-scopechain";
    }

    @ExportMessage
    boolean hasMembers() {
        return isScope();
    }

    @ExportMessage
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return getKeys();
    }

    @ExportMessage
    boolean isMemberReadable(@SuppressWarnings("unused") String name) {
        return this.contains(name);
    }

    // only need to create chain keys for the head, the rest can just be traversed.
    public Object getKeys() {
        return new ChainKeys(this);
    }

    @ExportMessage
    Object readMember(String symbolName,
                    @Cached BranchProfile exception,
                    @Cached LLVMDataEscapeNode.LLVMPointerDataEscapeNode escape,
                    @CachedLibrary("this") InteropLibrary self) throws UnknownIdentifierException {

        if (contains(symbolName)) {
            LLVMSymbol symbol = get(symbolName);
            if (symbol != null) {
                try {
                    LLVMPointer value = LLVMContext.get(self).getSymbol(symbol, exception);
                    if (value != null) {
                        return escape.executeWithTarget(value);
                    }
                } catch (LLVMLinkerException | LLVMIllegalSymbolIndexException e) {
                    // fallthrough
                }
                exception.enter();
                throw UnknownIdentifierException.create(symbolName);
            }
        }
        exception.enter();
        throw UnknownIdentifierException.create(symbolName);
    }

    @ExportLibrary(InteropLibrary.class)
    static final class ChainKeys implements TruffleObject {

        private LLVMScopeChain firstChain;

        private ChainKeys(LLVMScopeChain firstChain) {
            this.firstChain = firstChain;
        }

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            assert firstChain.getScope() != null;
            long size = 0;
            LLVMScopeChain current = firstChain;
            while (current != null) {
                size += current.getScope().getFunctionSize();
                current = current.getNext();
            }
            return size;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return 0 <= index && index < getArraySize();
        }

        @ExportMessage
        Object readArrayElement(long index,
                        @Cached BranchProfile exception) throws InvalidArrayIndexException {
            if (isArrayElementReadable(index)) {
                assert firstChain.getScope() != null;
                LLVMScopeChain current = firstChain;

                long currentSize = current.getScope().getFunctionSize();
                long newIndex = index;

                while (currentSize <= newIndex) {
                    newIndex = newIndex - currentSize;
                    current = current.getNext();
                    if (current == null) {
                        exception.enter();
                        throw new IllegalStateException();
                    }
                    currentSize = current.getScope().getFunctionSize();
                }
                return current.getScope().getKey((int) newIndex);
            } else {
                exception.enter();
                throw InvalidArrayIndexException.create(index);
            }
        }
    }

}
