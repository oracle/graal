package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.BranchProfile;

@ExportLibrary(InteropLibrary.class)
public final class KeysArray implements TruffleObject {

    public static final KeysArray EMPTY = new KeysArray(new String[0]);

    @CompilationFinal(dimensions = 1) private final String[] keys;

    public KeysArray(String[] keys) {
        this.keys = keys;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean hasArrayElements() {
        return true;
    }

    @ExportMessage
    long getArraySize() {
        return keys.length;
    }

    @ExportMessage
    boolean isArrayElementReadable(long idx) {
        return 0 <= idx && idx < keys.length;
    }

    @ExportMessage
    String readArrayElement(long idx,
                    @Cached BranchProfile exception) throws InvalidArrayIndexException {
        if (!isArrayElementReadable(idx)) {
            exception.enter();
            throw InvalidArrayIndexException.create(idx);
        }
        return keys[(int) idx];
    }
}
