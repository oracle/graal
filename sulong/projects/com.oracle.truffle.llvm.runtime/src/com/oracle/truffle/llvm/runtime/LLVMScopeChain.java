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
                    currentSize = current.getScope().getFunctionSize();
                }

                if (currentSize == 0) {
                    throw InvalidArrayIndexException.create(newIndex);
                }

                return current.getScope().getKey((int) newIndex);
            } else {
                exception.enter();
                throw InvalidArrayIndexException.create(index);
            }
        }
    }

}
