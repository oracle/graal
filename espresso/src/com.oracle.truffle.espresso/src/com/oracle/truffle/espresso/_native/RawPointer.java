package com.oracle.truffle.espresso._native;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.espresso.jni.NativeEnv;

@ExportLibrary(InteropLibrary.class)
public final class RawPointer implements TruffleObject {
    private final long rawPtr;

    private static final RawPointer NULL = new RawPointer(0L);

    public static @Pointer TruffleObject nullInstance() {
        return NULL;
    }

    public RawPointer(long rawPtr) {
        this.rawPtr = rawPtr;
    }

    public static @Pointer TruffleObject create(long ptr) {
        if (ptr == 0L) {
            return NULL;
        }
        return new RawPointer(ptr);
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean isPointer() {
        return true;
    }

    @ExportMessage
    long asPointer() {
        return rawPtr;
    }

    @ExportMessage
    boolean isNull() {
        return rawPtr == 0L;
    }
}
