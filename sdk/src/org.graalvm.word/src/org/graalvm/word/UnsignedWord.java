/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.word;

/**
 * Represents an unsigned word-sized value.
 *
 * @since 19.0
 */
public interface UnsignedWord extends ComparableWord {

    /**
     * Returns a Unsigned whose value is {@code (this + val)}.
     *
     * @param val value to be added to this Unsigned.
     * @return {@code this + val}
     *
     * @since 19.0
     */
    UnsignedWord add(UnsignedWord val);

    /**
     * Returns a Unsigned whose value is {@code (this - val)}.
     *
     * @param val value to be subtracted from this Unsigned.
     * @return {@code this - val}
     *
     * @since 19.0
     */
    UnsignedWord subtract(UnsignedWord val);

    /**
     * Returns a Unsigned whose value is {@code (this * val)}.
     *
     * @param val value to be multiplied by this Unsigned.
     * @return {@code this * val}
     *
     * @since 19.0
     */
    UnsignedWord multiply(UnsignedWord val);

    /**
     * Returns a Unsigned whose value is {@code (this / val)}.
     *
     * @param val value by which this Unsigned is to be divided.
     * @return {@code this / val}
     *
     * @since 19.0
     */
    UnsignedWord unsignedDivide(UnsignedWord val);

    /**
     * Returns a Unsigned whose value is {@code (this % val)}.
     *
     * @param val value by which this Unsigned is to be divided, and the remainder computed.
     * @return {@code this % val}
     *
     * @since 19.0
     */
    UnsignedWord unsignedRemainder(UnsignedWord val);

    /**
     * Returns a Unsigned whose value is {@code (this << n)}.
     *
     * @param n shift distance, in bits.
     * @return {@code this << n}
     *
     * @since 19.0
     */
    UnsignedWord shiftLeft(UnsignedWord n);

    /**
     * Returns a Unsigned whose value is {@code (this >>> n)}. No sign extension is performed.
     *
     * @param n shift distance, in bits.
     * @return {@code this >> n}
     *
     * @since 19.0
     */
    UnsignedWord unsignedShiftRight(UnsignedWord n);

    /**
     * Returns a Unsigned whose value is {@code (this & val)}.
     *
     * @param val value to be AND'ed with this Unsigned.
     * @return {@code this & val}
     *
     * @since 19.0
     */
    UnsignedWord and(UnsignedWord val);

    /**
     * Returns a Unsigned whose value is {@code (this | val)}.
     *
     * @param val value to be OR'ed with this Unsigned.
     * @return {@code this | val}
     *
     * @since 19.0
     */
    UnsignedWord or(UnsignedWord val);

    /**
     * Returns a Unsigned whose value is {@code (this ^ val)}.
     *
     * @param val value to be XOR'ed with this Unsigned.
     * @return {@code this ^ val}
     *
     * @since 19.0
     */
    UnsignedWord xor(UnsignedWord val);

    /**
     * Returns a Unsigned whose value is {@code (~this)}.
     *
     * @return {@code ~this}
     *
     * @since 19.0
     */
    UnsignedWord not();

    /**
     * Compares this Unsigned with the specified value.
     *
     * @param val value to which this Unsigned is to be compared.
     * @return {@code this == val}
     *
     * @since 19.0
     */
    boolean equal(UnsignedWord val);

    /**
     * Compares this Unsigned with the specified value.
     *
     * @param val value to which this Unsigned is to be compared.
     * @return {@code this != val}
     *
     * @since 19.0
     */
    boolean notEqual(UnsignedWord val);

    /**
     * Compares this Unsigned with the specified value.
     *
     * @param val value to which this Unsigned is to be compared.
     * @return {@code this < val}
     *
     * @since 19.0
     */
    boolean belowThan(UnsignedWord val);

    /**
     * Compares this Unsigned with the specified value.
     *
     * @param val value to which this Unsigned is to be compared.
     * @return {@code this <= val}
     *
     * @since 19.0
     */
    boolean belowOrEqual(UnsignedWord val);

    /**
     * Compares this Unsigned with the specified value.
     *
     * @param val value to which this Unsigned is to be compared.
     * @return {@code this > val}
     *
     * @since 19.0
     */
    boolean aboveThan(UnsignedWord val);

    /**
     * Compares this Unsigned with the specified value.
     *
     * @param val value to which this Unsigned is to be compared.
     * @return {@code this >= val}
     *
     * @since 19.0
     */
    boolean aboveOrEqual(UnsignedWord val);

    /**
     * Returns a Unsigned whose value is {@code (this + val)}.
     * <p>
     * Note that the right operand is a signed value, while the operation is performed unsigned.
     * Therefore, the result is only well-defined for positive right operands.
     *
     * @param val value to be added to this Unsigned.
     * @return {@code this + val}
     *
     * @since 19.0
     */
    UnsignedWord add(int val);

    /**
     * Returns a Unsigned whose value is {@code (this - val)}.
     * <p>
     * Note that the right operand is a signed value, while the operation is performed unsigned.
     * Therefore, the result is only well-defined for positive right operands.
     *
     * @param val value to be subtracted from this Unsigned.
     * @return {@code this - val}
     *
     * @since 19.0
     */
    UnsignedWord subtract(int val);

    /**
     * Returns a Unsigned whose value is {@code (this * val)}.
     * <p>
     * Note that the right operand is a signed value, while the operation is performed unsigned.
     * Therefore, the result is only well-defined for positive right operands.
     *
     * @param val value to be multiplied by this Unsigned.
     * @return {@code this * val}
     *
     * @since 19.0
     */
    UnsignedWord multiply(int val);

    /**
     * Returns a Unsigned whose value is {@code (this / val)}.
     * <p>
     * Note that the right operand is a signed value, while the operation is performed unsigned.
     * Therefore, the result is only well-defined for positive right operands.
     *
     * @param val value by which this Unsigned is to be divided.
     * @return {@code this / val}
     *
     * @since 19.0
     */
    UnsignedWord unsignedDivide(int val);

    /**
     * Returns a Unsigned whose value is {@code (this % val)}.
     * <p>
     * Note that the right operand is a signed value, while the operation is performed unsigned.
     * Therefore, the result is only well-defined for positive right operands.
     *
     * @param val value by which this Unsigned is to be divided, and the remainder computed.
     * @return {@code this % val}
     *
     * @since 19.0
     */
    UnsignedWord unsignedRemainder(int val);

    /**
     * Returns a Unsigned whose value is {@code (this << n)}.
     * <p>
     * Note that the right operand is a signed value, while the operation is performed unsigned.
     * Therefore, the result is only well-defined for positive right operands.
     *
     * @param n shift distance, in bits.
     * @return {@code this << n}
     *
     * @since 19.0
     */
    UnsignedWord shiftLeft(int n);

    /**
     * Returns a Unsigned whose value is {@code (this >>> n)}. No sign extension is performed.
     * <p>
     * Note that the right operand is a signed value, while the operation is performed unsigned.
     * Therefore, the result is only well-defined for positive right operands.
     *
     * @param n shift distance, in bits.
     * @return {@code this >> n}
     *
     * @since 19.0
     */
    UnsignedWord unsignedShiftRight(int n);

    /**
     * Returns a Unsigned whose value is {@code (this & val)}.
     * <p>
     * Note that the right operand is a signed value, while the operation is performed unsigned.
     * Therefore, the result is only well-defined for positive right operands.
     *
     * @param val value to be AND'ed with this Unsigned.
     * @return {@code this & val}
     *
     * @since 19.0
     */
    UnsignedWord and(int val);

    /**
     * Returns a Unsigned whose value is {@code (this | val)}.
     * <p>
     * Note that the right operand is a signed value, while the operation is performed unsigned.
     * Therefore, the result is only well-defined for positive right operands.
     *
     * @param val value to be OR'ed with this Unsigned.
     * @return {@code this | val}
     *
     * @since 19.0
     */
    UnsignedWord or(int val);

    /**
     * Returns a Unsigned whose value is {@code (this ^ val)}.
     * <p>
     * Note that the right operand is a signed value, while the operation is performed unsigned.
     * Therefore, the result is only well-defined for positive right operands.
     *
     * @param val value to be XOR'ed with this Unsigned.
     * @return {@code this ^ val}
     *
     * @since 19.0
     */
    UnsignedWord xor(int val);

    /**
     * Compares this Unsigned with the specified value.
     * <p>
     * Note that the right operand is a signed value, while the operation is performed unsigned.
     * Therefore, the result is only well-defined for positive right operands.
     *
     * @param val value to which this Unsigned is to be compared.
     * @return {@code this == val}
     *
     * @since 19.0
     */
    boolean equal(int val);

    /**
     * Compares this Unsigned with the specified value.
     * <p>
     * Note that the right operand is a signed value, while the operation is performed unsigned.
     * Therefore, the result is only well-defined for positive right operands.
     *
     * @param val value to which this Unsigned is to be compared.
     * @return {@code this != val}
     *
     * @since 19.0
     */
    boolean notEqual(int val);

    /**
     * Compares this Unsigned with the specified value.
     * <p>
     * Note that the right operand is a signed value, while the operation is performed unsigned.
     * Therefore, the result is only well-defined for positive right operands.
     *
     * @param val value to which this Unsigned is to be compared.
     * @return {@code this < val}
     *
     * @since 19.0
     */
    boolean belowThan(int val);

    /**
     * Compares this Unsigned with the specified value.
     * <p>
     * Note that the right operand is a signed value, while the operation is performed unsigned.
     * Therefore, the result is only well-defined for positive right operands.
     *
     * @param val value to which this Unsigned is to be compared.
     * @return {@code this <= val}
     *
     * @since 19.0
     */
    boolean belowOrEqual(int val);

    /**
     * Compares this Unsigned with the specified value.
     * <p>
     * Note that the right operand is a signed value, while the operation is performed unsigned.
     * Therefore, the result is only well-defined for positive right operands.
     *
     * @param val value to which this Unsigned is to be compared.
     * @return {@code this > val}
     *
     * @since 19.0
     */
    boolean aboveThan(int val);

    /**
     * Compares this Unsigned with the specified value.
     * <p>
     * Note that the right operand is a signed value, while the operation is performed unsigned.
     * Therefore, the result is only well-defined for positive right operands.
     *
     * @param val value to which this Unsigned is to be compared.
     * @return {@code this >= val}
     *
     * @since 19.0
     */
    boolean aboveOrEqual(int val);
}
