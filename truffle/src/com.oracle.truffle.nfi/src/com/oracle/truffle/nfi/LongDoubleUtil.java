/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.nfi;

import java.nio.ByteOrder;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidBufferOffsetException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.nfi.api.SerializableLibrary;

final class LongDoubleUtil {

    static Object interopToFP80(Object number) {
        assert InteropLibrary.getUncached().isNumber(number);
        return new FP80Number(number);
    }

    static Object fp80ToNumber(Object buffer) {
        assert InteropLibrary.getUncached().hasBufferElements(buffer);
        return new FP80Buffer(buffer);
    }

    private static final class DoubleHelper {

        private static final int FRACTION_BITS = 52;
        private static final int EXPONENT_BITS = 11;

        private static final long EXPONENT_MASK = ((1L << EXPONENT_BITS) - 1) << FRACTION_BITS;
        private static final long FRACTION_MASK = (1L << FRACTION_BITS) - 1;

        private static final int EXPONENT_BIAS = 1023;
    }

    @ExportLibrary(value = SerializableLibrary.class, useForAOT = false)
    static final class FP80Number implements TruffleObject {

        private static final int FRACTION_BITS = 64;

        private static final int SIGN_MASK = 1 << 15;
        private static final int EXPONENT_MASK = SIGN_MASK - 1;
        private static final int EXPONENT_BIAS = 16383;

        private static final long INF_FRACTION = 0x8000_0000_0000_0000L;
        private static final long NAN_FRACTION = 0xc000_0000_0000_0000L;

        final Object number;

        private FP80Number(Object number) {
            this.number = number;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isSerializable() {
            return true;
        }

        @ExportMessage
        static class Serialize {

            @Specialization(limit = "1", guards = "numberInterop.fitsInLong(self.number)")
            static void doLong(FP80Number self, Object buffer,
                            @CachedLibrary("self.number") InteropLibrary numberInterop,
                            @CachedLibrary("buffer") InteropLibrary bufferInterop) {
                try {
                    long number = numberInterop.asLong(self.number);
                    if (number == 0) {
                        bufferInterop.writeBufferLong(buffer, ByteOrder.nativeOrder(), 0, 0);
                        bufferInterop.writeBufferShort(buffer, ByteOrder.nativeOrder(), 8, (short) 0);
                        return;
                    }

                    int sign = number < 0 ? SIGN_MASK : 0;
                    long val = Math.abs(number);

                    int leadingOnePosition = Long.SIZE - Long.numberOfLeadingZeros(val);
                    int exponent = FP80Number.EXPONENT_BIAS + (leadingOnePosition - 1);
                    assert (exponent & FP80Number.EXPONENT_MASK) == exponent : "exponent out of range";

                    long fractionMask;
                    if (leadingOnePosition == Long.SIZE || leadingOnePosition == Long.SIZE - 1) {
                        fractionMask = 0xffffffff;
                    } else {
                        fractionMask = (1L << leadingOnePosition + 1) - 1;
                    }
                    long maskedFractionValue = val & fractionMask;
                    long fraction = maskedFractionValue << (Long.SIZE - leadingOnePosition);

                    bufferInterop.writeBufferLong(buffer, ByteOrder.nativeOrder(), 0, fraction);
                    bufferInterop.writeBufferShort(buffer, ByteOrder.nativeOrder(), 8, (short) (sign | exponent));
                } catch (UnsupportedMessageException | InvalidBufferOffsetException ex) {
                    throw CompilerDirectives.shouldNotReachHere(ex);
                }
            }

            @Specialization(limit = "1", guards = "numberInterop.fitsInDouble(self.number)")
            static void doDouble(FP80Number self, Object buffer,
                            @CachedLibrary("self.number") InteropLibrary numberInterop,
                            @CachedLibrary("buffer") InteropLibrary bufferInterop) {
                try {
                    double number = numberInterop.asDouble(self.number);
                    long rawValue = Double.doubleToRawLongBits(number);
                    int sign = rawValue < 0 ? SIGN_MASK : 0;

                    long absRaw = Math.abs(rawValue);
                    if (absRaw == 0) {
                        // positive or negative zero
                        bufferInterop.writeBufferLong(buffer, ByteOrder.nativeOrder(), 0, 0);
                        bufferInterop.writeBufferShort(buffer, ByteOrder.nativeOrder(), 8, (short) sign);
                        return;
                    }

                    if ((absRaw & DoubleHelper.EXPONENT_MASK) == DoubleHelper.EXPONENT_MASK) {
                        if ((absRaw & DoubleHelper.FRACTION_MASK) == 0) {
                            // infinity
                            bufferInterop.writeBufferLong(buffer, ByteOrder.nativeOrder(), 0, FP80Number.INF_FRACTION);
                        } else {
                            // NaN
                            bufferInterop.writeBufferLong(buffer, ByteOrder.nativeOrder(), 0, FP80Number.NAN_FRACTION);
                        }
                        bufferInterop.writeBufferShort(buffer, ByteOrder.nativeOrder(), 8, (short) (sign | FP80Number.EXPONENT_MASK));
                    }

                    long doubleExponent = (absRaw & DoubleHelper.EXPONENT_MASK) >> DoubleHelper.FRACTION_BITS;
                    int fp80Exponent = (int) doubleExponent - DoubleHelper.EXPONENT_BIAS + FP80Number.EXPONENT_BIAS;

                    long doubleFraction = rawValue & DoubleHelper.FRACTION_MASK;
                    long shiftedDoubleFraction = doubleFraction << (63 - DoubleHelper.FRACTION_BITS);
                    long leadingOne = 1L << 63;
                    long fp80Fraction = leadingOne | shiftedDoubleFraction;

                    bufferInterop.writeBufferLong(buffer, ByteOrder.nativeOrder(), 0, fp80Fraction);
                    bufferInterop.writeBufferShort(buffer, ByteOrder.nativeOrder(), 8, (short) (sign | fp80Exponent));
                } catch (UnsupportedMessageException | InvalidBufferOffsetException ex) {
                    throw CompilerDirectives.shouldNotReachHere(ex);
                }
            }
        }
    }

    @ExportLibrary(value = InteropLibrary.class, delegateTo = "buffer")
    static final class FP80Buffer implements TruffleObject {

        final Object buffer;

        FP80Buffer(Object buffer) {
            this.buffer = buffer;
        }

        @ExportMessage
        boolean isNumber(@CachedLibrary("this.buffer") InteropLibrary interop) {
            return interop.hasBufferElements(buffer);
        }

        @ExportMessage
        boolean fitsInByte(@CachedLibrary("this.buffer") InteropLibrary interop) {
            try {
                long value = asLong(interop);
                return value == (byte) value;
            } catch (UnsupportedMessageException ex) {
                return false;
            }
        }

        @ExportMessage
        boolean fitsInShort(@CachedLibrary("this.buffer") InteropLibrary interop) {
            try {
                long value = asLong(interop);
                return value == (short) value;
            } catch (UnsupportedMessageException ex) {
                return false;
            }
        }

        @ExportMessage
        boolean fitsInInt(@CachedLibrary("this.buffer") InteropLibrary interop) {
            try {
                long value = asLong(interop);
                return value == (int) value;
            } catch (UnsupportedMessageException ex) {
                return false;
            }
        }

        @ExportMessage
        boolean fitsInLong(@CachedLibrary("this.buffer") InteropLibrary interop) {
            try {
                asLong(interop);
                return true;
            } catch (UnsupportedMessageException ex) {
                return false;
            }
        }

        @ExportMessage
        boolean fitsInFloat(@CachedLibrary("this.buffer") InteropLibrary interop) {
            if (fitsInDouble()) {
                try {
                    double value = asDouble(interop);
                    return value == (float) value;
                } catch (UnsupportedMessageException ex) {
                    return false;
                }
            } else {
                return false;
            }
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean fitsInDouble() {
            /*
             * Technically this is not correct, but there is no higher precision type available in
             * interop, so if we would return false here, there would be no way to get any number
             * out.
             */
            return true;
        }

        @ExportMessage
        byte asByte(@CachedLibrary("this.buffer") InteropLibrary interop) throws UnsupportedMessageException {
            long value = asLong(interop);
            if (value != (byte) value) {
                throw UnsupportedMessageException.create();
            }
            return (byte) value;
        }

        @ExportMessage
        short asShort(@CachedLibrary("this.buffer") InteropLibrary interop) throws UnsupportedMessageException {
            long value = asLong(interop);
            if (value != (short) value) {
                throw UnsupportedMessageException.create();
            }
            return (short) value;
        }

        @ExportMessage
        int asInt(@CachedLibrary("this.buffer") InteropLibrary interop) throws UnsupportedMessageException {
            long value = asLong(interop);
            if (value != (int) value) {
                throw UnsupportedMessageException.create();
            }
            return (int) value;
        }

        @ExportMessage
        long asLong(@CachedLibrary("this.buffer") InteropLibrary interop) throws UnsupportedMessageException {
            try {
                short exponent = interop.readBufferShort(buffer, ByteOrder.LITTLE_ENDIAN, 8);
                if ((exponent & FP80Number.EXPONENT_MASK) == FP80Number.EXPONENT_MASK) {
                    // NaN or infinity
                    throw UnsupportedMessageException.create();
                }

                int unbiasedExponent = (exponent & FP80Number.EXPONENT_MASK) - FP80Number.EXPONENT_BIAS;
                long fraction = interop.readBufferLong(buffer, ByteOrder.LITTLE_ENDIAN, 0);
                int shift = FP80Number.FRACTION_BITS - unbiasedExponent - 1;
                long ret = fraction >>> shift;
                if (ret < 0 || fraction != (ret << shift)) {
                    // overflow or not a whole number
                    throw UnsupportedMessageException.create();
                }

                if ((exponent & FP80Number.SIGN_MASK) == 0) {
                    return ret;
                } else {
                    return -ret;
                }
            } catch (InvalidBufferOffsetException ex) {
                throw UnsupportedMessageException.create();
            }
        }

        @ExportMessage
        float asFloat(@CachedLibrary("this.buffer") InteropLibrary interop) throws UnsupportedMessageException {
            return (float) asDouble(interop);
        }

        @ExportMessage
        double asDouble(@CachedLibrary("this.buffer") InteropLibrary interop) throws UnsupportedMessageException {
            try {
                long fraction = interop.readBufferLong(buffer, ByteOrder.LITTLE_ENDIAN, 0);
                short exponent = interop.readBufferShort(buffer, ByteOrder.LITTLE_ENDIAN, 8);
                if (fraction == 0) {
                    if (exponent == 0) {
                        return 0.0;
                    } else if (exponent == (short) FP80Number.SIGN_MASK) {
                        return -0.0;
                    }
                }

                if ((exponent & FP80Number.EXPONENT_MASK) == FP80Number.EXPONENT_MASK) {
                    if (fraction == 1L << 63) {
                        // infinity
                        if ((exponent & FP80Number.SIGN_MASK) == 0) {
                            return Double.POSITIVE_INFINITY;
                        } else {
                            return Double.NEGATIVE_INFINITY;
                        }
                    } else {
                        // NaN
                        return Double.NaN;
                    }
                }

                int unbiasedExponent = (exponent & FP80Number.EXPONENT_MASK) - FP80Number.EXPONENT_BIAS;
                int doubleExponent = unbiasedExponent + DoubleHelper.EXPONENT_BIAS;
                long doubleFraction = (fraction << 1) >>> (FP80Number.FRACTION_BITS - DoubleHelper.FRACTION_BITS);
                long shiftedExponent = (long) doubleExponent << DoubleHelper.FRACTION_BITS;
                long signBit = (long) (exponent & FP80Number.SIGN_MASK) << (Long.SIZE - Short.SIZE);
                return Double.longBitsToDouble(signBit | shiftedExponent | doubleFraction);
            } catch (InvalidBufferOffsetException ex) {
                throw UnsupportedMessageException.create();
            }
        }

        @TruffleBoundary
        private static String format(long fraction, short exponent) {
            return String.format("0xK%04x%016x", exponent, fraction);
        }

        @ExportMessage
        String toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects,
                        @CachedLibrary("this.buffer") InteropLibrary interop) {
            try {
                long fraction = interop.readBufferLong(buffer, ByteOrder.LITTLE_ENDIAN, 0);
                short exponent = interop.readBufferShort(buffer, ByteOrder.LITTLE_ENDIAN, 8);
                return format(fraction, exponent);
            } catch (UnsupportedMessageException | InvalidBufferOffsetException ex) {
                return "<invalid FP80>";
            }
        }
    }
}
