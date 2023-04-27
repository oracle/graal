package com.oracle.truffle.espresso.runtime.dispatch.messages;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.StopIterationException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(InteropLibrary.class)
public final class ArrayIterator implements TruffleObject {

    final Object array;
    private long currentItemIndex;

    public ArrayIterator(Object array) {
        this.array = array;
        assert InteropLibrary.getUncached().hasArrayElements(array) : "Array must have array elements.";
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isIterator() {
        return true;
    }

    @ExportMessage
    boolean hasIteratorNextElement(@CachedLibrary("this.array") InteropLibrary arrays) {
        try {
            return currentItemIndex < arrays.getArraySize(array);
        } catch (UnsupportedMessageException ume) {
            throw CompilerDirectives.shouldNotReachHere(ume);
        }
    }

    @ExportMessage
    Object getIteratorNextElement(@CachedLibrary("this.array") InteropLibrary arrays) throws UnsupportedMessageException, StopIterationException {
        try {
            long size = arrays.getArraySize(array);
            if (currentItemIndex >= size) {
                throw StopIterationException.create();
            }
            Object res = arrays.readArrayElement(array, currentItemIndex);
            currentItemIndex++;
            return res;
        } catch (UnsupportedMessageException ume) {
            throw CompilerDirectives.shouldNotReachHere(ume);
        } catch (InvalidArrayIndexException iaie) {
            throw UnsupportedMessageException.create();
        }
    }
}
