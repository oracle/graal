package com.oracle.truffle.llvm.runtime.interop.convert;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.llvm.runtime.floating.LLVM128BitFloat;

@GenerateUncached
public abstract class ToFP128 extends ForeignToLLVM {

    @Specialization
    protected LLVM128BitFloat fromInt(int value) {
        return LLVM128BitFloat.fromInt(value);
    }

    @Specialization
    protected LLVM128BitFloat fromChar(char value) {
        return LLVM128BitFloat.fromInt(value);
    }

    @Specialization
    protected LLVM128BitFloat fromByte(byte value) {
        return LLVM128BitFloat.fromInt(value);
    }

    @Specialization
    protected LLVM128BitFloat fromShort(short value) {
        return LLVM128BitFloat.fromInt(value);
    }

    @Specialization
    protected LLVM128BitFloat fromFloat(float value) {
        return LLVM128BitFloat.fromFloat(value);
    }

    @Specialization
    protected LLVM128BitFloat fromLong(long value) {
        return LLVM128BitFloat.fromLong(value);
    }

    @Specialization
    protected LLVM128BitFloat fromDouble(double value) {
        return LLVM128BitFloat.fromDouble(value);
    }

    private static final InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();

    @CompilerDirectives.TruffleBoundary
    static LLVM128BitFloat slowPathPrimitiveConvert(Object value) throws UnsupportedTypeException {
        try {
            if (INTEROP.fitsInLong(value)) {
                return LLVM128BitFloat.fromLong(INTEROP.asLong(value));
            } else if (INTEROP.fitsInDouble(value)) {
                return LLVM128BitFloat.fromDouble(INTEROP.asDouble(value));
            }
        } catch (UnsupportedMessageException ex) {
        }
        throw UnsupportedTypeException.create(new Object[]{value});
    }

}
